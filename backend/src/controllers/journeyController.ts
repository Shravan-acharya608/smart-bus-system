import { Request, Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { z } from 'zod';
import { v4 as uuidv4 } from 'uuid';

const createJourneyShareSchema = z.object({
  tripId: z.string().uuid(),
  durationMinutes: z.number().int().min(15).max(720).optional().default(180), // 3 hours default
  sharedWithEmail: z.string().email().optional(),
});

export async function createJourneyShare(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const parseResult = createJourneyShareSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid journey share request', details: parseResult.error.format() },
      });
      return;
    }

    const { tripId, durationMinutes, sharedWithEmail } = parseResult.data;

    let sharedWithId: string | null = null;
    if (sharedWithEmail) {
      const recipient = await prisma.user.findUnique({ where: { email: sharedWithEmail } });
      if (recipient) sharedWithId = recipient.id;
    }

    const expiresAt = new Date(Date.now() + durationMinutes * 60 * 1000);
    const shareToken = uuidv4();

    const share = await prisma.journeyShare.create({
      data: {
        ownerId: user.userId,
        sharedWithId,
        tripId,
        shareToken,
        expiresAt,
        isActive: true,
      },
      include: {
        trip: {
          include: {
            bus: { select: { busNumber: true } },
            route: { select: { name: true, code: true } },
          },
        },
      },
    });

    res.status(201).json({
      success: true,
      data: {
        shareId: share.id,
        shareToken: share.shareToken,
        shareUrl: `/shared-journey/${share.shareToken}`,
        expiresAt: share.expiresAt,
        trip: share.trip,
      },
      message: 'Journey sharing enabled. Link generated for authorized recipient.',
    });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to create journey share' } });
  }
}

// Public or authorized link lookup with token
export async function getSharedJourney(req: Request, res: Response): Promise<void> {
  try {
    const { token } = req.params;

    const share = await prisma.journeyShare.findUnique({
      where: { shareToken: token },
      include: {
        owner: { select: { firstName: true, lastName: true } },
        trip: {
          include: {
            bus: { select: { busNumber: true } },
            route: {
              include: {
                stops: { orderBy: { stopOrder: 'asc' }, include: { stop: true } },
              },
            },
            locationUpdates: {
              orderBy: { timestamp: 'desc' },
              take: 1,
            },
          },
        },
      },
    });

    if (!share || !share.isActive || (share.expiresAt && share.expiresAt < new Date())) {
      res.status(404).json({
        success: false,
        error: { code: 'LINK_EXPIRED', message: 'This journey sharing link has expired or been revoked' },
      });
      return;
    }

    const lastLoc = share.trip?.locationUpdates[0];
    let location = null;
    if (lastLoc) {
      const ageSeconds = Math.floor((Date.now() - new Date(lastLoc.timestamp).getTime()) / 1000);
      location = {
        latitude: lastLoc.latitude,
        longitude: lastLoc.longitude,
        speed: lastLoc.speed,
        heading: lastLoc.heading,
        timestamp: lastLoc.timestamp,
        isStale: ageSeconds > 300,
      };
    }

    res.json({
      success: true,
      data: {
        passengerName: `${share.owner.firstName} ${share.owner.lastName}`,
        tripStatus: share.trip?.status,
        busNumber: share.trip?.bus.busNumber,
        routeName: share.trip?.route.name,
        stops: share.trip?.route.stops,
        lastLocation: location,
        expiresAt: share.expiresAt,
      },
    });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch shared journey' } });
  }
}

export async function revokeJourneyShare(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    const share = await prisma.journeyShare.findUnique({ where: { id } });
    if (!share) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Sharing link not found' } });
      return;
    }

    if (share.ownerId !== user.userId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Access denied' } });
      return;
    }

    await prisma.journeyShare.update({
      where: { id },
      data: {
        isActive: false,
        revokedAt: new Date(),
      },
    });

    res.json({ success: true, message: 'Journey sharing revoked successfully' });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to revoke journey share' } });
  }
}
