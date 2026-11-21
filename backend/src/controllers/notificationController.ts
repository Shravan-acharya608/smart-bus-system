import { Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { z } from 'zod';
import { NotificationType, UserRole } from '@prisma/client';
import { getIo } from '../socket';

const announcementSchema = z.object({
  title: z.string().min(1),
  body: z.string().min(1),
  type: z.nativeEnum(NotificationType).default(NotificationType.SERVICE_ANNOUNCEMENT),
  tripId: z.string().uuid().optional(),
});

export async function getNotifications(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const { unreadOnly } = req.query as { unreadOnly?: string };

    const where: any = { userId: user.userId };
    if (unreadOnly === 'true') {
      where.isRead = false;
    }

    const notifications = await prisma.notification.findMany({
      where,
      orderBy: { createdAt: 'desc' },
      take: 50,
    });

    res.json({ success: true, data: notifications });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch notifications' } });
  }
}

export async function markAsRead(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    const notification = await prisma.notification.findUnique({ where: { id } });
    if (!notification || notification.userId !== user.userId) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Notification not found' } });
      return;
    }

    const updated = await prisma.notification.update({
      where: { id },
      data: { isRead: true },
    });

    res.json({ success: true, data: updated });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to update notification' } });
  }
}

export async function createAnnouncement(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const parseResult = announcementSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid announcement', details: parseResult.error.format() },
      });
      return;
    }

    const { title, body, type, tripId } = parseResult.data;

    // Find affected users: if tripId provided, all ticket holders of this trip; otherwise all passengers
    let targetUsers: { id: string }[] = [];
    if (tripId) {
      const tickets = await prisma.ticket.findMany({
        where: { tripId },
        select: { passengerId: true },
        distinct: ['passengerId'],
      });
      targetUsers = tickets.map((t) => ({ id: t.passengerId }));
    } else {
      targetUsers = await prisma.user.findMany({
        where: { role: UserRole.PASSENGER },
        select: { id: true },
        take: 200,
      });
    }

    if (targetUsers.length > 0) {
      await prisma.notification.createMany({
        data: targetUsers.map((u) => ({
          userId: u.id,
          tripId: tripId || null,
          title,
          body,
          type,
          sentAt: new Date(),
        })),
      });
    }

    // Broadcast announcement via Socket.IO
    try {
      const io = getIo();
      if (tripId) {
        io.to(`trip:${tripId}`).emit('announcement', { title, body, type, tripId });
      } else {
        io.emit('global-announcement', { title, body, type });
      }
    } catch (e) {}

    res.status(201).json({
      success: true,
      message: `Announcement created for ${targetUsers.length} passengers`,
    });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to broadcast announcement' } });
  }
}
