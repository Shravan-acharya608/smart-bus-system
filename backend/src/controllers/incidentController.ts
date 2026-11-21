import { Request, Response } from 'express';
import { PrismaClient, IncidentSeverity } from '@prisma/client';

const prisma = new PrismaClient();

export const reportIncident = async (req: Request, res: Response) => {
  try {
    const userId = (req as any).user?.userId;
    const { tripId } = req.params;
    const { severity, description, delayMinutes, latitude, longitude } = req.body;

    const trip = await prisma.trip.findUnique({
      where: { id: tripId },
      include: { bus: true, route: true },
    });
    if (!trip) {
      return res.status(404).json({ success: false, message: 'Trip not found' });
    }

    const incident = await prisma.incidentReport.create({
      data: {
        tripId,
        reportedById: userId,
        severity: (severity as IncidentSeverity) || IncidentSeverity.MEDIUM,
        description: description || 'Delay or road condition reported',
        delayMinutes: Number(delayMinutes) || 0,
        latitude: latitude ? parseFloat(latitude) : null,
        longitude: longitude ? parseFloat(longitude) : null,
      },
    });

    // Notify all active passengers booked on this trip
    const activeTickets = await prisma.ticket.findMany({
      where: { tripId, status: { in: ['PAID', 'HELD', 'USED'] } },
      select: { passengerId: true },
    });
    const uniquePassengerIds = [...new Set(activeTickets.map(t => t.passengerId))];

    const severityEmoji: Record<string, string> = {
      LOW: '⚠️',
      MEDIUM: '🔶',
      HIGH: '🔴',
      CRITICAL: '🚨',
    };

    if (uniquePassengerIds.length > 0) {
      const delayText = incident.delayMinutes && incident.delayMinutes > 0
        ? `Expected delay: ~${incident.delayMinutes} mins. Reason: ${incident.description}`
        : `Update: ${incident.description}`;

      await prisma.notification.createMany({
        data: uniquePassengerIds.map(pid => ({
          userId: pid,
          tripId,
          type: 'TRIP_UPDATE',
          title: `${severityEmoji[incident.severity] || '⚠️'} Bus ${trip.bus.busNumber} Incident/Delay`,
          body: delayText,
          data: {
            incidentId: incident.id,
            severity: incident.severity,
            delayMinutes: incident.delayMinutes,
            tripId,
            busNumber: trip.bus.busNumber,
          },
        })),
      });
    }

    // Broadcast via Socket.IO if instance attached to app
    const io = (req.app as any).get('io');
    if (io) {
      io.to(`trip:${tripId}`).emit('trip:incident', {
        tripId,
        severity: incident.severity,
        description: incident.description,
        delayMinutes: incident.delayMinutes,
        incidentId: incident.id,
        busNumber: trip.bus.busNumber,
      });
      io.emit('emergency:alert', {
        tripId,
        busId: trip.busId,
        busNumber: trip.bus.busNumber,
        routeName: trip.route.name,
        severity: incident.severity,
        description: incident.description,
        delayMinutes: incident.delayMinutes,
        incidentId: incident.id,
        timestamp: incident.createdAt,
      });
    }

    return res.status(201).json({ success: true, data: incident });
  } catch (err: any) {
    console.error('[IncidentController] reportIncident error:', err);
    return res.status(500).json({ success: false, message: 'Failed to report incident' });
  }
};

export const resolveIncident = async (req: Request, res: Response) => {
  try {
    const { id } = req.params;
    const { resolvedNote } = req.body;

    const incident = await prisma.incidentReport.update({
      where: { id },
      data: {
        isResolved: true,
        resolvedAt: new Date(),
        resolvedNote: resolvedNote || 'Resolved by operator',
      },
    });

    return res.json({ success: true, data: incident });
  } catch (err: any) {
    console.error('[IncidentController] resolveIncident error:', err);
    return res.status(500).json({ success: false, message: 'Failed to resolve incident' });
  }
};

export const getIncidents = async (req: Request, res: Response) => {
  try {
    const { tripId, isResolved } = req.query;
    const where: any = {};
    if (tripId) where.tripId = String(tripId);
    if (isResolved !== undefined) where.isResolved = isResolved === 'true';

    const incidents = await prisma.incidentReport.findMany({
      where,
      include: {
        reportedBy: {
          select: { firstName: true, lastName: true, role: true, phone: true },
        },
        trip: {
          select: {
            id: true,
            bus: { select: { busNumber: true, registrationNo: true } },
            route: { select: { name: true, code: true } },
          },
        },
      },
      orderBy: { createdAt: 'desc' },
      take: 50,
    });

    return res.json({ success: true, data: incidents });
  } catch (err: any) {
    console.error('[IncidentController] getIncidents error:', err);
    return res.status(500).json({ success: false, message: 'Failed to fetch incidents' });
  }
};
