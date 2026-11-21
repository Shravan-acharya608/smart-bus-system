import { PrismaClient, TicketStatus, PaymentStatus, EscrowStatus } from '@prisma/client';

const prisma = new PrismaClient();

export async function processMissedTripRefunds(tripId: string): Promise<void> {
  try {
    // 1. Find all PAID tickets for this trip that have no boarding event
    const missedTickets = await prisma.ticket.findMany({
      where: {
        tripId,
        status: TicketStatus.PAID,
        boardingEvent: null,
      },
      include: { payment: true },
    });

    for (const ticket of missedTickets) {
      if (!ticket.payment) continue;

      try {
        await prisma.$transaction(async (tx) => {
          // Update payment to refunded in escrow
          await tx.payment.update({
            where: { id: ticket.payment!.id },
            data: {
              status: PaymentStatus.REFUNDED,
              refundAmount: ticket.payment!.amount,
              escrowStatus: EscrowStatus.REFUNDED_TO_USER,
              refundReason: 'Passenger did not board — auto-refund on trip end',
              refundedAt: new Date(),
            },
          });

          // Update ticket status to REFUNDED
          await tx.ticket.update({
            where: { id: ticket.id },
            data: { status: TicketStatus.REFUNDED },
          });

          // Create notification for passenger
          await tx.notification.create({
            data: {
              userId: ticket.passengerId,
              tripId: ticket.tripId,
              type: 'PAYMENT_SUCCESS',
              title: '💰 Auto-Refund Processed',
              body: `Your fare of ₹${ticket.payment!.amount.toFixed(2)} has been refunded to your account because you missed this trip.`,
              data: {
                ticketId: ticket.id,
                refundAmount: ticket.payment!.amount,
                reason: 'MISSED_TRIP_ESCROW_REFUND',
              },
            },
          });
        });
      } catch (refundErr) {
        console.error(`[RefundService] Failed to auto-refund ticket ${ticket.id}:`, refundErr);
      }
    }

    // 2. Settle escrow for all passengers who DID board
    const boardedPayments = await prisma.payment.findMany({
      where: {
        escrowStatus: EscrowStatus.HELD_IN_ESCROW,
        status: PaymentStatus.SUCCESS,
        ticket: {
          tripId,
          boardingEvent: { isNot: null },
        },
      },
    });

    for (const payment of boardedPayments) {
      await prisma.payment.update({
        where: { id: payment.id },
        data: { escrowStatus: EscrowStatus.SETTLED_TO_OWNER },
      });
    }
  } catch (err) {
    console.error(`[RefundService] Error processing refunds for trip ${tripId}:`, err);
  }
}
