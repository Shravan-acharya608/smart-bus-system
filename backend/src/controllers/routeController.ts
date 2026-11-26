import { Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { z } from 'zod';
import { UserRole } from '@prisma/client';
import { UDUPI_SCHEDULES, buildStopsForSchedule } from '../data/udupiTimetable';

const stopInputSchema = z.object({
  name: z.string().min(2).max(100),
  latitude: z.number().min(-90).max(90),
  longitude: z.number().min(-180).max(180),
  address: z.string().optional(),
});

const routeStopSchema = z.object({
  stopId: z.string().uuid().optional(),
  name: z.string().min(2).max(100).optional(),
  latitude: z.number().min(-90).max(90).optional(),
  longitude: z.number().min(-180).max(180).optional(),
  stopOrder: z.number().int().min(1),
  scheduledArrivalTime: z.string().optional(),
  scheduledDepartureTime: z.string().optional(),
  distanceFromStart: z.number().min(0).optional(),
  fareFromStart: z.number().min(0).optional(),
});

const createRouteSchema = z.object({
  name: z.string().min(2).max(100),
  code: z.string().min(1).max(20),
  description: z.string().optional(),
  organizationId: z.string().uuid().optional(),
  stops: z.array(routeStopSchema).min(2, 'Route must have at least 2 stops'),
});

export async function getRoutes(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const { origin, destination, search, page = '1', limit = '20' } = req.query as Record<string, string>;

    const pageNum = Math.max(1, parseInt(page, 10) || 1);
    const limitNum = Math.min(200, Math.max(1, parseInt(limit, 10) || 100));
    const skip = (pageNum - 1) * limitNum;

    const where: any = { isActive: true };

    if (user.role === UserRole.BUS_OWNER) {
      if (!user.organizationId) {
        res.status(403).json({ success: false, error: { code: 'NO_ORG', message: 'No organization linked' } });
        return;
      }
      where.organizationId = user.organizationId;
    }

    if (search) {
      where.OR = [
        { name: { contains: search, mode: 'insensitive' } },
        { code: { contains: search, mode: 'insensitive' } },
        { description: { contains: search, mode: 'insensitive' } },
      ];
    }

    let routes = await prisma.route.findMany({
      where,
      skip,
      take: limitNum,
      orderBy: { name: 'asc' },
      include: {
        organization: { select: { id: true, name: true, code: true } },
        stops: {
          orderBy: { stopOrder: 'asc' },
          include: { stop: true },
        },
        trips: {
          where: { status: 'ACTIVE' },
          select: { id: true, status: true, bus: { select: { id: true, busNumber: true } } },
        },
      },
    });

    // Passenger Origin & Destination Search filtering
    if (origin && destination) {
      routes = routes.filter((route) => {
        const originIndex = route.stops.findIndex(
          (s) =>
            s.stop.name.toLowerCase().includes(origin.toLowerCase()) ||
            s.stop.id === origin
        );
        const destIndex = route.stops.findIndex(
          (s) =>
            s.stop.name.toLowerCase().includes(destination.toLowerCase()) ||
            s.stop.id === destination
        );
        return originIndex !== -1 && destIndex !== -1 && originIndex < destIndex;
      });
    }

    const total = await prisma.route.count({ where });

    res.json({
      success: true,
      data: routes,
      pagination: {
        page: pageNum,
        limit: limitNum,
        total,
        pages: Math.ceil(total / limitNum),
      },
    });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch routes' } });
  }
}

export async function getRouteById(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    const route = await prisma.route.findUnique({
      where: { id },
      include: {
        organization: { select: { id: true, name: true, code: true } },
        stops: {
          orderBy: { stopOrder: 'asc' },
          include: { stop: true },
        },
        trips: {
          where: { status: { in: ['SCHEDULED', 'ACTIVE'] } },
          include: {
            bus: { select: { id: true, busNumber: true, capacity: true } },
            assignments: {
              include: {
                driver: { select: { id: true, firstName: true, lastName: true, phone: true } },
                conductor: { select: { id: true, firstName: true, lastName: true, phone: true } },
              },
            },
          },
        },
      },
    });

    if (!route) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Route not found' } });
      return;
    }

    if (user.role === UserRole.BUS_OWNER && route.organizationId !== user.organizationId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Access denied' } });
      return;
    }

    res.json({ success: true, data: route });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch route' } });
  }
}

export async function createRoute(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const parseResult = createRouteSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid route data', details: parseResult.error.format() },
      });
      return;
    }

    const { name, code, description, stops, organizationId } = parseResult.data;
    const orgId = user.role === UserRole.BUS_OWNER ? user.organizationId : (organizationId || user.organizationId);

    if (!orgId) {
      res.status(400).json({ success: false, error: { code: 'ORG_REQUIRED', message: 'Organization ID is required' } });
      return;
    }

    // Check code uniqueness
    const existing = await prisma.route.findUnique({ where: { code } });
    if (existing) {
      res.status(409).json({ success: false, error: { code: 'DUPLICATE_CODE', message: 'Route code already exists' } });
      return;
    }

    // Use transaction to create route and stops
    const createdRoute = await prisma.$transaction(async (tx) => {
      const route = await tx.route.create({
        data: {
          name,
          code,
          description,
          organizationId: orgId,
        },
      });

      for (const stopItem of stops) {
        let stopId = stopItem.stopId;

        // If stopId is not provided, create the stop first
        if (!stopId) {
          if (!stopItem.name || stopItem.latitude === undefined || stopItem.longitude === undefined) {
            throw new Error(`Stop at order ${stopItem.stopOrder} requires either stopId or name and coordinates`);
          }
          const newStop = await tx.stop.create({
            data: {
              name: stopItem.name,
              latitude: stopItem.latitude,
              longitude: stopItem.longitude,
            },
          });
          stopId = newStop.id;
        }

        await tx.routeStop.create({
          data: {
            routeId: route.id,
            stopId: stopId,
            stopOrder: stopItem.stopOrder,
            scheduledArrivalTime: stopItem.scheduledArrivalTime,
            scheduledDepartureTime: stopItem.scheduledDepartureTime,
            distanceFromStart: stopItem.distanceFromStart || 0,
            fareFromStart: stopItem.fareFromStart || 0,
          },
        });
      }

      return tx.route.findUnique({
        where: { id: route.id },
        include: {
          stops: {
            orderBy: { stopOrder: 'asc' },
            include: { stop: true },
          },
        },
      });
    });

    res.status(201).json({ success: true, data: createdRoute });
  } catch (error: any) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: error.message || 'Failed to create route' } });
  }
}

export async function getStops(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { search } = req.query as { search?: string };
    const where: any = {};
    if (search) {
      where.name = { contains: search, mode: 'insensitive' };
    }

    const stops = await prisma.stop.findMany({
      where,
      orderBy: { name: 'asc' },
      take: 100,
    });

    res.json({ success: true, data: stops });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch stops' } });
  }
}

export async function createStop(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const parseResult = stopInputSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid stop data', details: parseResult.error.format() },
      });
      return;
    }

    const stop = await prisma.stop.create({
      data: parseResult.data,
    });

    res.status(201).json({ success: true, data: stop });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to create stop' } });
  }
}

export async function searchBuses(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { boardingStopId, destinationStopId } = req.query as Record<string, string>;
    if (!boardingStopId || !destinationStopId) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'boardingStopId and destinationStopId are required' },
      });
      return;
    }

    const routes = await prisma.route.findMany({
      where: {
        isActive: true,
        stops: {
          some: { stopId: boardingStopId },
        },
      },
      include: {
        stops: { orderBy: { stopOrder: 'asc' }, include: { stop: true } },
        trips: {
          where: {
            status: { in: ['SCHEDULED', 'ACTIVE'] },
          },
          include: {
            bus: true,
            locationUpdates: { orderBy: { timestamp: 'desc' }, take: 1 },
            tickets: { where: { status: { in: ['PAID', 'HELD'] } }, select: { id: true } },
            assignments: { include: { conductor: { select: { firstName: true, lastName: true } } } },
          },
          orderBy: { scheduledStart: 'asc' },
          take: 20,
        },
      },
    });

    const results: any[] = [];
    for (const route of routes) {
      const boardingStop = route.stops.find((s) => s.stopId === boardingStopId);
      const destinationStop = route.stops.find((s) => s.stopId === destinationStopId);
      if (!boardingStop || !destinationStop) continue;
      if (boardingStop.stopOrder >= destinationStop.stopOrder) continue;

      const fare = Math.max((destinationStop.fareFromStart ?? 0) - (boardingStop.fareFromStart ?? 0), 10);

      for (const trip of route.trips) {
        const seatsBooked = trip.tickets.length;
        const seatsAvailable = Math.max(trip.bus.capacity - seatsBooked, 0);
        const lastLoc = trip.locationUpdates[0];

        results.push({
          tripId: trip.id,
          busId: trip.bus.id,
          busNumber: trip.bus.busNumber,
          routeName: route.name,
          routeCode: route.code,
          status: trip.status,
          scheduledStart: trip.scheduledStart,
          seatsAvailable,
          seatsTotal: trip.bus.capacity,
          fare,
          lastLocation: lastLoc ? { lat: lastLoc.latitude, lng: lastLoc.longitude, timestamp: lastLoc.timestamp } : null,
          boardingStop: { id: boardingStop.stopId, name: boardingStop.stop.name, order: boardingStop.stopOrder },
          destinationStop: { id: destinationStop.stopId, name: destinationStop.stop.name, order: destinationStop.stopOrder },
        });
      }
    }

    results.sort((a, b) => new Date(a.scheduledStart).getTime() - new Date(b.scheduledStart).getTime());

    res.json({ success: true, data: results, count: results.length });
  } catch (error: any) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: error.message || 'Search failed' } });
  }
}

export async function getUdupiSchedules(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { q, to } = req.query as { q?: string; to?: string };

    // Match schedules with any DB trips/routes if seeded
    const dbTrips = await prisma.trip.findMany({
      where: { status: { in: ['ACTIVE', 'SCHEDULED'] } },
      select: {
        id: true,
        notes: true,
        status: true,
        bus: { select: { busNumber: true } },
      },
      take: 200,
    });

    const tripBySlNo = new Map<number, { tripId: string; status: string; busNumber: string }>();
    for (const t of dbTrips) {
      const m = t.notes?.match(/Sl\.No\s*#?(\d+)/i);
      if (m) {
        tripBySlNo.set(parseInt(m[1], 10), {
          tripId: t.id,
          status: t.status,
          busNumber: t.bus.busNumber,
        });
      }
    }

    let items = UDUPI_SCHEDULES.map((row) => {
      const stops = buildStopsForSchedule(row);
      const linked = tripBySlNo.get(row.slNo);
      return {
        slNo: row.slNo,
        busNumber: linked?.busNumber || `KA-20-UDP-${String(row.slNo).padStart(3, '0')}`,
        tripId: linked?.tripId || null,
        tripStatus: linked?.status || 'SCHEDULED',
        from: row.from,
        to: row.to,
        serviceClass: row.serviceClass,
        via: row.via,
        departureTime: row.departureTime,
        stops,
      };
    });

    if (to && to !== 'ALL') {
      items = items.filter((item) => item.to.toLowerCase().includes(to.toLowerCase()));
    }
    if (q) {
      const term = q.toLowerCase();
      items = items.filter(
        (item) =>
          item.to.toLowerCase().includes(term) ||
          item.via.toLowerCase().includes(term) ||
          item.departureTime.includes(term) ||
          String(item.slNo) === term
      );
    }

    res.json({ success: true, data: items, count: items.length });
  } catch (error: any) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: error.message || 'Failed to load Udupi schedules' } });
  }
}

