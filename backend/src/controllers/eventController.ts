import { Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { z } from 'zod';
import { TicketStatus, PaymentStatus, PaymentMethod } from '@prisma/client';
import { getIo } from '../socket';
import { v4 as uuidv4 } from 'uuid';
import { logger } from '../lib/logger';

const offlineSyncItemSchema = z.object({
  clientEventId: z.string().min(1),
  eventType: z.enum(['BOARDING', 'EXIT', 'OFFLINE_TICKET']),
  tripId: z.string().uuid(),
  ticketId: z.string().optional(),
  ticketCode: z.string().optional(),
  boardingStopId: z.string().uuid().optional(),
  destinationStopId: z.string().uuid().optional(),
  fare: z.number().min(0).optional(),
  paymentMethod: z.nativeEnum(PaymentMethod).optional().default(PaymentMethod.CASH),
  timestamp: z.string().datetime().optional(),
});

const syncPayloadSchema = z.object({
  events: z.array(offlineSyncItemSchema),
});

export interface SyncItemResult {
  clientEventId: string;
  eventType: string;
  status: 'PROCESSED' | 'SKIPPED_DUPLICATE' | 'ERROR';
  message?: string;
  serverEntityId?: string;
}

export async function syncOfflineEvents(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const parseResult = syncPayloadSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid sync payload', details: parseResult.error.format() },
      });
      return;
    }

    const { events } = parseResult.data;
    const results: SyncItemResult[] = [];
    const affectedTripIds = new Set<string>();

    for (const item of events) {
      try {
        affectedTripIds.add(item.tripId);

        if (item.eventType === 'OFFLINE_TICKET') {
          // Check for duplicate clientEventId
          const existing = await prisma.ticket.findUnique({
            where: { clientEventId: item.clientEventId },
          });

          if (existing) {
            results.push({
              clientEventId: item.clientEventId,
              eventType: item.eventType,
              status: 'SKIPPED_DUPLICATE',
              serverEntityId: existing.id,
              message: 'Ticket already synchronized',
            });
            continue;
          }

          if (!item.boardingStopId || !item.destinationStopId || item.fare === undefined) {
            results.push({
              clientEventId: item.clientEventId,
              eventType: item.eventType,
              status: 'ERROR',
              message: 'Missing stops or fare for offline ticket',
            });
            continue;
          }

          const ticketCode = uuidv4();
          const qrData = JSON.stringify({ code: ticketCode, tripId: item.tripId, t: Date.now() });

          const created = await prisma.$transaction(async (tx) => {
            const ticket = await tx.ticket.create({
              data: {
                ticketCode,
                passengerId: user.userId,
                tripId: item.tripId,
                boardingStopId: item.boardingStopId!,
                destinationStopId: item.destinationStopId!,
                fare: item.fare!,
                status: TicketStatus.USED,
                paymentMethod: item.paymentMethod || PaymentMethod.CASH,
                isOfflineIssued: true,
                clientEventId: item.clientEventId,
                qrData,
                issuedByConductorId: user.userId,
                createdAt: item.timestamp ? new Date(item.timestamp) : new Date(),
              },
            });

            await tx.payment.create({
              data: {
                ticketId: ticket.id,
                amount: item.fare!,
                currency: 'INR',
                status: PaymentStatus.SUCCESS,
                paymentMethod: item.paymentMethod || PaymentMethod.CASH,
                isSandbox: true,
              },
            });

            await tx.boardingEvent.create({
              data: {
                ticketId: ticket.id,
                tripId: item.tripId,
                stopId: item.boardingStopId,
                isOffline: true,
                clientEventId: `board_${item.clientEventId}`,
                scannedAt: item.timestamp ? new Date(item.timestamp) : new Date(),
                syncedAt: new Date(),
              },
            });

            return ticket;
          });

          results.push({
            clientEventId: item.clientEventId,
            eventType: item.eventType,
            status: 'PROCESSED',
            serverEntityId: created.id,
          });
        } else if (item.eventType === 'BOARDING') {
          // Check for duplicate boarding event
          const existing = await prisma.boardingEvent.findUnique({
            where: { clientEventId: item.clientEventId },
          });

          if (existing) {
            results.push({
              clientEventId: item.clientEventId,
              eventType: item.eventType,
              status: 'SKIPPED_DUPLICATE',
              serverEntityId: existing.id,
              message: 'Boarding event already processed',
            });
            continue;
          }

          // Find ticket by id or ticketCode
          let targetTicketId = item.ticketId;
          if (!targetTicketId && item.ticketCode) {
            const ticket = await prisma.ticket.findUnique({ where: { ticketCode: item.ticketCode } });
            targetTicketId = ticket?.id;
          }

          if (!targetTicketId) {
            results.push({
              clientEventId: item.clientEventId,
              eventType: item.eventType,
              status: 'ERROR',
              message: 'Ticket not found for boarding event',
            });
            continue;
          }

          // Check if boarding already recorded for this ticket
          const alreadyBoarded = await prisma.boardingEvent.findUnique({
            where: { ticketId: targetTicketId },
          });

          if (alreadyBoarded) {
            results.push({
              clientEventId: item.clientEventId,
              eventType: item.eventType,
              status: 'SKIPPED_DUPLICATE',
              serverEntityId: alreadyBoarded.id,
              message: 'Ticket already recorded as boarded',
            });
            continue;
          }

          const boarding = await prisma.$transaction(async (tx) => {
            const event = await tx.boardingEvent.create({
              data: {
                ticketId: targetTicketId!,
                tripId: item.tripId,
                stopId: item.boardingStopId,
                isOffline: true,
                clientEventId: item.clientEventId,
                scannedAt: item.timestamp ? new Date(item.timestamp) : new Date(),
                syncedAt: new Date(),
              },
            });

            await tx.ticket.update({
              where: { id: targetTicketId },
              data: { status: TicketStatus.USED },
            });

            return event;
          });

          results.push({
            clientEventId: item.clientEventId,
            eventType: item.eventType,
            status: 'PROCESSED',
            serverEntityId: boarding.id,
          });
        } else if (item.eventType === 'EXIT') {
          const existing = await prisma.exitEvent.findUnique({
            where: { clientEventId: item.clientEventId },
          });

          if (existing) {
            results.push({
              clientEventId: item.clientEventId,
              eventType: item.eventType,
              status: 'SKIPPED_DUPLICATE',
              serverEntityId: existing.id,
              message: 'Exit event already processed',
            });
            continue;
          }

          let targetTicketId = item.ticketId;
          if (!targetTicketId && item.ticketCode) {
            const ticket = await prisma.ticket.findUnique({ where: { ticketCode: item.ticketCode } });
            targetTicketId = ticket?.id;
          }

          if (!targetTicketId) {
            results.push({
              clientEventId: item.clientEventId,
              eventType: item.eventType,
              status: 'ERROR',
              message: 'Ticket not found for exit event',
            });
            continue;
          }

          const exit = await prisma.exitEvent.create({
            data: {
              ticketId: targetTicketId,
              tripId: item.tripId,
              stopId: item.destinationStopId,
              isOffline: true,
              clientEventId: item.clientEventId,
              exitedAt: item.timestamp ? new Date(item.timestamp) : new Date(),
              syncedAt: new Date(),
            },
          });

          results.push({
            clientEventId: item.clientEventId,
            eventType: item.eventType,
            status: 'PROCESSED',
            serverEntityId: exit.id,
          });
        }
      } catch (itemErr: any) {
        logger.error(`Error processing sync item ${item.clientEventId}:`, itemErr);
        results.push({
          clientEventId: item.clientEventId,
          eventType: item.eventType,
          status: 'ERROR',
          message: itemErr.message || 'Unknown processing error',
        });
      }
    }

    // Broadcast updated passenger counts for all affected trips
    try {
      const io = getIo();
      for (const tripId of affectedTripIds) {
        const [boardings, exits] = await Promise.all([
          prisma.boardingEvent.count({ where: { tripId } }),
          prisma.exitEvent.count({ where: { tripId } }),
        ]);
        const currentOnboard = Math.max(0, boardings - exits);

        io.to(`trip:${tripId}`).emit('passenger-count-updated', {
          tripId,
          currentOnboard,
          totalBoardings: boardings,
          totalExits: exits,
        });
      }
    } catch (e) {}

    const processedCount = results.filter((r) => r.status === 'PROCESSED').length;
    const skippedCount = results.filter((r) => r.status === 'SKIPPED_DUPLICATE').length;
    const errorCount = results.filter((r) => r.status === 'ERROR').length;

    res.json({
      success: true,
      data: {
        summary: {
          total: events.length,
          processed: processedCount,
          skippedDuplicates: skippedCount,
          errors: errorCount,
        },
        results,
      },
    });
  } catch (error) {
    logger.error('Error during bulk offline synchronization:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to synchronize offline records' } });
  }
}
