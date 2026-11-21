import { Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { z } from 'zod';
import { TripStatus, UserRole } from '@prisma/client';
import { getIo } from '../socket';
import { logger } from '../lib/logger';
import { processMissedTripRefunds } from '../services/refundService';

const createTripSchema = z.object({
  busId: z.string().uuid(),
  routeId: z.string().uuid(),
  scheduledStart: z.string().datetime(),
  scheduledEnd: z.string().datetime().optional(),
  driverId: z.string().uuid().optional(),
  conductorId: z.string().uuid().optional(),
  notes: z.string().optional(),
});

const locationUpdateSchema = z.object({
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  speed: z.number().min(0).optional().default(0),
  heading: z.number().min(0).max(360).optional().default(0),
  accuracy: z.number().min(0).optional(),
  timestamp: z.string().datetime().optional(),
});

export async function getTrips(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const { status, busId, routeId, date, page = '1', limit = '20' } = req.query as Record<string, string>;

    const pageNum = Math.max(1, parseInt(page, 10) || 1);
    const limitNum = Math.min(100, Math.max(1, parseInt(limit, 10) || 20));
    const skip = (pageNum - 1) * limitNum;

    const where: any = {};

    // Role-specific filtering
    if (user.role === UserRole.DRIVER) {
      where.assignments = { some: { driverId: user.userId } };
    } else if (user.role === UserRole.CONDUCTOR) {
      where.assignments = { some: { conductorId: user.userId } };
    } else if (user.role === UserRole.BUS_OWNER) {
      if (!user.organizationId) {
        res.status(403).json({ success: false, error: { code: 'NO_ORG', message: 'No organization linked' } });
        return;
      }
      where.bus = { organizationId: user.organizationId };
    }

    if (status && Object.values(TripStatus).includes(status as TripStatus)) {
      where.status = status as TripStatus;
    }
    if (busId) where.busId = busId;
    if (routeId) where.routeId = routeId;
    if (date) {
      const startDate = new Date(date);
      startDate.setHours(0, 0, 0, 0);
      const endDate = new Date(date);
      endDate.setHours(23, 59, 59, 999);
      where.scheduledStart = { gte: startDate, lte: endDate };
    }

    const [trips, total] = await Promise.all([
      prisma.trip.findMany({
        where,
        skip,
        take: limitNum,
        orderBy: { scheduledStart: 'desc' },
        include: {
          bus: { select: { id: true, busNumber: true, registrationNo: true, capacity: true, status: true } },
          route: {
            include: {
              stops: {
                orderBy: { stopOrder: 'asc' },
                include: { stop: true },
              },
            },
          },
          assignments: {
            include: {
              driver: { select: { id: true, firstName: true, lastName: true, phone: true } },
              conductor: { select: { id: true, firstName: true, lastName: true, phone: true } },
            },
          },
          locationUpdates: {
            orderBy: { timestamp: 'desc' },
            take: 1,
          },
        },
      }),
      prisma.trip.count({ where }),
    ]);

    // Format response and attach freshness metadata
    const now = Date.now();
    const formattedTrips = trips.map((trip) => {
      const lastUpdate = trip.locationUpdates[0];
      let location = null;
      if (lastUpdate) {
        const updateAgeSeconds = Math.floor((now - new Date(lastUpdate.timestamp).getTime()) / 1000);
        location = {
          latitude: lastUpdate.latitude,
          longitude: lastUpdate.longitude,
          speed: lastUpdate.speed,
          heading: lastUpdate.heading,
          timestamp: lastUpdate.timestamp,
          isStale: updateAgeSeconds > 300, // Stale if older than 5 minutes
          ageSeconds: updateAgeSeconds,
        };
      }
      return {
        ...trip,
        lastLocation: location,
        locationUpdates: undefined,
      };
    });

    res.json({
      success: true,
      data: formattedTrips,
      pagination: {
        page: pageNum,
        limit: limitNum,
        total,
        pages: Math.ceil(total / limitNum),
      },
    });
  } catch (error) {
    logger.error('Error fetching trips:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch trips' } });
  }
}

export async function getTripById(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    const trip = await prisma.trip.findUnique({
      where: { id },
      include: {
        bus: true,
        route: {
          include: {
            stops: {
              orderBy: { stopOrder: 'asc' },
              include: { stop: true },
            },
          },
        },
        assignments: {
          include: {
            driver: { select: { id: true, firstName: true, lastName: true, phone: true } },
            conductor: { select: { id: true, firstName: true, lastName: true, phone: true } },
          },
        },
        locationUpdates: {
          orderBy: { timestamp: 'desc' },
          take: 1,
        },
      },
    });

    if (!trip) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Trip not found' } });
      return;
    }

    if (user.role === UserRole.BUS_OWNER && trip.bus.organizationId !== user.organizationId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Access denied to this trip' } });
      return;
    }

    // Calculate passenger count
    const [boardings, exits] = await Promise.all([
      prisma.boardingEvent.count({ where: { tripId: id } }),
      prisma.exitEvent.count({ where: { tripId: id } }),
    ]);

    const currentOnboard = Math.max(0, boardings - exits);
    const lastUpdate = trip.locationUpdates[0];
    let location = null;
    if (lastUpdate) {
      const ageSeconds = Math.floor((Date.now() - new Date(lastUpdate.timestamp).getTime()) / 1000);
      location = {
        latitude: lastUpdate.latitude,
        longitude: lastUpdate.longitude,
        speed: lastUpdate.speed,
        heading: lastUpdate.heading,
        timestamp: lastUpdate.timestamp,
        isStale: ageSeconds > 300,
        ageSeconds,
      };
    }

    res.json({
      success: true,
      data: {
        ...trip,
        lastLocation: location,
        locationUpdates: undefined,
        passengerCount: {
          currentOnboard,
          totalBoardings: boardings,
          totalExits: exits,
          capacity: trip.bus.capacity,
          availableSeats: Math.max(0, trip.bus.capacity - currentOnboard),
          hasInconsistency: boardings < exits,
        },
      },
    });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch trip' } });
  }
}

export async function createTrip(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const parseResult = createTripSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid trip data', details: parseResult.error.format() },
      });
      return;
    }

    const { busId, routeId, scheduledStart, scheduledEnd, driverId, conductorId, notes } = parseResult.data;

    const bus = await prisma.bus.findUnique({ where: { id: busId } });
    if (!bus) {
      res.status(404).json({ success: false, error: { code: 'BUS_NOT_FOUND', message: 'Bus not found' } });
      return;
    }

    if (user.role === UserRole.BUS_OWNER && bus.organizationId !== user.organizationId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Access denied' } });
      return;
    }

    // Check conflict: Bus should not have an overlapping active trip
    const activeBusTrip = await prisma.trip.findFirst({
      where: { busId, status: TripStatus.ACTIVE },
    });
    if (activeBusTrip) {
      res.status(409).json({
        success: false,
        error: { code: 'BUS_CONFLICT', message: 'Bus is currently on an active trip' },
      });
      return;
    }

    // Conflict check for Driver
    if (driverId) {
      const activeDriverTrip = await prisma.tripAssignment.findFirst({
        where: { driverId, trip: { status: TripStatus.ACTIVE } },
      });
      if (activeDriverTrip) {
        res.status(409).json({
          success: false,
          error: { code: 'DRIVER_CONFLICT', message: 'Assigned driver is already on an active trip' },
        });
        return;
      }
    }

    // Conflict check for Conductor
    if (conductorId) {
      const activeConductorTrip = await prisma.tripAssignment.findFirst({
        where: { conductorId, trip: { status: TripStatus.ACTIVE } },
      });
      if (activeConductorTrip) {
        res.status(409).json({
          success: false,
          error: { code: 'CONDUCTOR_CONFLICT', message: 'Assigned conductor is already on an active trip' },
        });
        return;
      }
    }

    const trip = await prisma.trip.create({
      data: {
        busId,
        routeId,
        scheduledStart: new Date(scheduledStart),
        scheduledEnd: scheduledEnd ? new Date(scheduledEnd) : null,
        notes,
        assignments: {
          create: {
            driverId: driverId || null,
            conductorId: conductorId || null,
          },
        },
      },
      include: {
        bus: true,
        route: true,
        assignments: {
          include: {
            driver: { select: { id: true, firstName: true, lastName: true } },
            conductor: { select: { id: true, firstName: true, lastName: true } },
          },
        },
      },
    });

    res.status(201).json({ success: true, data: trip });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to create trip' } });
  }
}

export async function startTrip(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    const trip = await prisma.trip.findUnique({
      where: { id },
      include: { assignments: true },
    });

    if (!trip) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Trip not found' } });
      return;
    }

    // Only assigned driver, admin or owner can start trip
    if (user.role === UserRole.DRIVER && trip.assignments[0]?.driverId !== user.userId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Only the assigned driver can start this trip' } });
      return;
    }

    if (trip.status === TripStatus.ACTIVE) {
      res.status(400).json({ success: false, error: { code: 'ALREADY_ACTIVE', message: 'Trip is already active' } });
      return;
    }

    if (trip.status === TripStatus.COMPLETED || trip.status === TripStatus.CANCELLED) {
      res.status(400).json({ success: false, error: { code: 'INVALID_STATUS', message: 'Cannot start a completed or cancelled trip' } });
      return;
    }

    const updated = await prisma.trip.update({
      where: { id },
      data: {
        status: TripStatus.ACTIVE,
        actualStart: new Date(),
      },
      include: { bus: true, route: true },
    });

    // Broadcast trip start via Socket.IO
    try {
      const io = getIo();
      io.to(`trip:${id}`).emit('trip-status-changed', {
        tripId: id,
        status: TripStatus.ACTIVE,
        timestamp: new Date().toISOString(),
      });
    } catch (e) {
      // Socket might not have connected clients
    }

    res.json({ success: true, data: updated, message: 'Trip started successfully' });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to start trip' } });
  }
}

export async function endTrip(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    const trip = await prisma.trip.findUnique({
      where: { id },
      include: { assignments: true },
    });

    if (!trip) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Trip not found' } });
      return;
    }

    if (user.role === UserRole.DRIVER && trip.assignments[0]?.driverId !== user.userId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Only the assigned driver can end this trip' } });
      return;
    }

    if (trip.status !== TripStatus.ACTIVE) {
      res.status(400).json({ success: false, error: { code: 'NOT_ACTIVE', message: 'Only active trips can be ended' } });
      return;
    }

    const updated = await prisma.trip.update({
      where: { id },
      data: {
        status: TripStatus.COMPLETED,
        actualEnd: new Date(),
      },
    });

    // Auto-refund all unboarded tickets & settle escrow for boarded passengers
    try {
      await processMissedTripRefunds(id);
    } catch (refundErr) {
      logger.error(`Error auto-refunding missed tickets for trip ${id}:`, refundErr);
    }

    // Broadcast trip end via Socket.IO
    try {
      const io = getIo();
      io.to(`trip:${id}`).emit('trip-status-changed', {
        tripId: id,
        status: TripStatus.COMPLETED,
        timestamp: new Date().toISOString(),
      });
    } catch (e) {}

    res.json({ success: true, data: updated, message: 'Trip completed successfully' });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to end trip' } });
  }
}

export async function postLocationUpdate(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    // Verify driver is assigned to active trip
    const trip = await prisma.trip.findUnique({
      where: { id },
      include: { assignments: true },
    });

    if (!trip) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Trip not found' } });
      return;
    }

    if (trip.status !== TripStatus.ACTIVE) {
      res.status(400).json({
        success: false,
        error: { code: 'TRIP_NOT_ACTIVE', message: 'Cannot update location for a non-active trip' },
      });
      return;
    }

    if (user.role === UserRole.DRIVER && trip.assignments[0]?.driverId !== user.userId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Unauthorized driver' } });
      return;
    }

    const parseResult = locationUpdateSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid coordinates', details: parseResult.error.format() },
      });
      return;
    }

    const { latitude, longitude, speed, heading, accuracy, timestamp } = parseResult.data;
    const updateTime = timestamp ? new Date(timestamp) : new Date();

    const locationUpdate = await prisma.locationUpdate.create({
      data: {
        tripId: id,
        latitude,
        longitude,
        speed,
        heading,
        accuracy,
        timestamp: updateTime,
      },
    });

    // Real-time broadcast to room
    try {
      const io = getIo();
      io.to(`trip:${id}`).emit('location-update', {
        tripId: id,
        latitude,
        longitude,
        speed,
        heading,
        accuracy,
        timestamp: updateTime.toISOString(),
      });
    } catch (e) {}

    res.status(201).json({ success: true, data: locationUpdate });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to record location update' } });
  }
}

export async function getTripLocation(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;

    const lastUpdate = await prisma.locationUpdate.findFirst({
      where: { tripId: id },
      orderBy: { timestamp: 'desc' },
    });

    if (!lastUpdate) {
      res.status(404).json({
        success: false,
        error: { code: 'NO_LOCATION_DATA', message: 'No location updates recorded for this trip' },
      });
      return;
    }

    const ageSeconds = Math.floor((Date.now() - new Date(lastUpdate.timestamp).getTime()) / 1000);
    const isStale = ageSeconds > 300; // 5 minutes threshold

    res.json({
      success: true,
      data: {
        tripId: id,
        latitude: lastUpdate.latitude,
        longitude: lastUpdate.longitude,
        speed: lastUpdate.speed,
        heading: lastUpdate.heading,
        accuracy: lastUpdate.accuracy,
        timestamp: lastUpdate.timestamp,
        isStale,
        ageSeconds,
        warning: isStale ? 'Location data is stale (> 5 minutes old)' : undefined,
      },
    });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch location' } });
  }
}

export async function getPassengerCount(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;

    const trip = await prisma.trip.findUnique({
      where: { id },
      include: { bus: { select: { capacity: true } } },
    });

    if (!trip) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Trip not found' } });
      return;
    }

    const [totalBoardings, totalExits, prepaidBoardings, conductorBoardings] = await Promise.all([
      prisma.boardingEvent.count({ where: { tripId: id } }),
      prisma.exitEvent.count({ where: { tripId: id } }),
      prisma.boardingEvent.count({
        where: { tripId: id, ticket: { paymentMethod: 'ONLINE' } },
      }),
      prisma.boardingEvent.count({
        where: { tripId: id, ticket: { paymentMethod: { in: ['CASH', 'UPI'] }, isOfflineIssued: true } },
      }),
    ]);

    const currentOnboard = Math.max(0, totalBoardings - totalExits);
    const capacity = trip.bus.capacity;
    const hasInconsistency = totalBoardings < totalExits || currentOnboard > capacity;

    res.json({
      success: true,
      data: {
        tripId: id,
        currentOnboard,
        capacity,
        availableSeats: Math.max(0, capacity - currentOnboard),
        totalBoardings,
        totalExits,
        prepaidBoardings,
        conductorBoardings,
        hasInconsistency,
        warning: hasInconsistency ? 'Passenger count anomaly detected: review boarding/exit events' : undefined,
      },
    });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch passenger count' } });
  }
}
