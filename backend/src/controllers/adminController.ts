import { Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { z } from 'zod';
import { UserRole, PaymentStatus } from '@prisma/client';
import { hashPassword } from '../utils/hash';
import { logger } from '../lib/logger';

const createStaffSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  firstName: z.string().min(1),
  lastName: z.string().min(1),
  phone: z.string().optional(),
  role: z.enum([UserRole.DRIVER, UserRole.CONDUCTOR]),
  organizationId: z.string().uuid().optional(),
});

export async function getDashboardStats(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const orgId = user.role === UserRole.BUS_OWNER ? user.organizationId : undefined;

    const busWhere: any = {};
    const tripWhere: any = { status: 'ACTIVE' };
    const userWhere: any = {};

    if (orgId) {
      busWhere.organizationId = orgId;
      tripWhere.bus = { organizationId: orgId };
      userWhere.organizationId = orgId;
    }

    const todayStart = new Date();
    todayStart.setHours(0, 0, 0, 0);

    const [
      totalBuses,
      activeBuses,
      activeTrips,
      totalDrivers,
      totalConductors,
      totalPassengers,
      totalTickets,
      todayTicketsCount,
      paymentsSummary,
      activeTripsList,
    ] = await Promise.all([
      prisma.bus.count({ where: busWhere }),
      prisma.bus.count({ where: { ...busWhere, status: 'ACTIVE' } }),
      prisma.trip.count({ where: tripWhere }),
      prisma.user.count({ where: { ...userWhere, role: UserRole.DRIVER } }),
      prisma.user.count({ where: { ...userWhere, role: UserRole.CONDUCTOR } }),
      prisma.user.count({ where: { role: UserRole.PASSENGER } }),
      prisma.ticket.count({ where: orgId ? { trip: { bus: { organizationId: orgId } } } : {} }),
      prisma.ticket.count({
        where: {
          createdAt: { gte: todayStart },
          ...(orgId ? { trip: { bus: { organizationId: orgId } } } : {}),
        },
      }),
      prisma.payment.aggregate({
        where: {
          status: PaymentStatus.SUCCESS,
          createdAt: { gte: todayStart },
          ...(orgId ? { ticket: { trip: { bus: { organizationId: orgId } } } } : {}),
        },
        _sum: { amount: true },
      }),
      prisma.trip.findMany({
        where: tripWhere,
        include: {
          bus: { select: { id: true, busNumber: true, registrationNo: true, capacity: true } },
          route: { select: { id: true, name: true, code: true } },
          assignments: {
            include: {
              driver: { select: { id: true, firstName: true, lastName: true } },
              conductor: { select: { id: true, firstName: true, lastName: true } },
            },
          },
          locationUpdates: {
            orderBy: { timestamp: 'desc' },
            take: 1,
          },
        },
      }),
    ]);

    // Active fleet status with freshness check
    const now = Date.now();
    const fleetStatus = await Promise.all(
      activeTripsList.map(async (trip) => {
        const [boardings, exits] = await Promise.all([
          prisma.boardingEvent.count({ where: { tripId: trip.id } }),
          prisma.exitEvent.count({ where: { tripId: trip.id } }),
        ]);

        const currentOnboard = Math.max(0, boardings - exits);
        const lastLocation = trip.locationUpdates[0];
        let location = null;

        if (lastLocation) {
          const ageSeconds = Math.floor((now - new Date(lastLocation.timestamp).getTime()) / 1000);
          location = {
            latitude: lastLocation.latitude,
            longitude: lastLocation.longitude,
            speed: lastLocation.speed,
            heading: lastLocation.heading,
            timestamp: lastLocation.timestamp,
            isStale: ageSeconds > 300,
            ageSeconds,
          };
        }

        return {
          tripId: trip.id,
          busId: trip.bus.id,
          busNumber: trip.bus.busNumber,
          registrationNo: trip.bus.registrationNo,
          capacity: trip.bus.capacity,
          currentOnboard,
          routeName: trip.route.name,
          routeCode: trip.route.code,
          driverName: trip.assignments[0]?.driver
            ? `${trip.assignments[0].driver.firstName} ${trip.assignments[0].driver.lastName}`
            : 'Unassigned',
          conductorName: trip.assignments[0]?.conductor
            ? `${trip.assignments[0].conductor.firstName} ${trip.assignments[0].conductor.lastName}`
            : 'Unassigned',
          lastLocation,
          hasInconsistency: boardings < exits || currentOnboard > trip.bus.capacity,
        };
      })
    );

    const totalCurrentPassengers = fleetStatus.reduce((acc, curr) => acc + curr.currentOnboard, 0);

    res.json({
      success: true,
      data: {
        totalBuses,
        activeBuses,
        activeTrips,
        totalDrivers,
        totalConductors,
        totalPassengers,
        totalTickets,
        todayTickets: todayTicketsCount,
        todayRevenue: paymentsSummary._sum.amount || 0,
        totalCurrentPassengers,
        fleetStatus,
      },
    });
  } catch (error) {
    logger.error('Dashboard stats error:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch dashboard statistics' } });
  }
}

export async function getReports(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const { startDate, endDate, format = 'json' } = req.query as Record<string, string>;
    const orgId = user.role === UserRole.BUS_OWNER ? user.organizationId : undefined;

    const start = startDate ? new Date(startDate) : new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
    const end = endDate ? new Date(endDate) : new Date();

    const paymentWhere: any = {
      status: PaymentStatus.SUCCESS,
      createdAt: { gte: start, lte: end },
    };

    if (orgId) {
      paymentWhere.ticket = { trip: { bus: { organizationId: orgId } } };
    }

    const [payments, routes] = await Promise.all([
      prisma.payment.findMany({
        where: paymentWhere,
        include: {
          ticket: {
            include: {
              trip: {
                include: {
                  route: { select: { id: true, name: true, code: true } },
                  bus: { select: { busNumber: true } },
                },
              },
            },
          },
        },
      }),
      prisma.route.findMany({
        where: orgId ? { organizationId: orgId } : {},
        select: { id: true, name: true, code: true },
      }),
    ]);

    // Aggregate by Route
    const routeStatsMap = new Map<string, { routeName: string; routeCode: string; revenue: number; ticketCount: number }>();
    routes.forEach((r) => routeStatsMap.set(r.id, { routeName: r.name, routeCode: r.code, revenue: 0, ticketCount: 0 }));

    // Aggregate by Date
    const dailyStatsMap = new Map<string, { date: string; revenue: number; ticketCount: number; cashCount: number; onlineCount: number }>();

    payments.forEach((p) => {
      // By route
      const rId = p.ticket.trip.route.id;
      const rStat = routeStatsMap.get(rId) || {
        routeName: p.ticket.trip.route.name,
        routeCode: p.ticket.trip.route.code,
        revenue: 0,
        ticketCount: 0,
      };
      rStat.revenue += p.amount;
      rStat.ticketCount += 1;
      routeStatsMap.set(rId, rStat);

      // By date
      const dateKey = p.createdAt.toISOString().slice(0, 10);
      const dStat = dailyStatsMap.get(dateKey) || {
        date: dateKey,
        revenue: 0,
        ticketCount: 0,
        cashCount: 0,
        onlineCount: 0,
      };
      dStat.revenue += p.amount;
      dStat.ticketCount += 1;
      if (p.paymentMethod === 'CASH') dStat.cashCount += 1;
      else dStat.onlineCount += 1;
      dailyStatsMap.set(dateKey, dStat);
    });

    const routeStats = Array.from(routeStatsMap.values());
    const dailyStats = Array.from(dailyStatsMap.values()).sort((a, b) => a.date.localeCompare(b.date));

    if (format === 'csv') {
      let csv = 'Date,Tickets Sold,Cash Tickets,Online Tickets,Revenue (INR)\n';
      dailyStats.forEach((d) => {
        csv += `${d.date},${d.ticketCount},${d.cashCount},${d.onlineCount},${d.revenue}\n`;
      });
      res.setHeader('Content-Type', 'text/csv');
      res.setHeader('Content-Disposition', 'attachment; filename="smartbus-revenue-report.csv"');
      res.send(csv);
      return;
    }

    res.json({
      success: true,
      data: {
        totalRevenue: payments.reduce((acc, p) => acc + p.amount, 0),
        totalTickets: payments.length,
        dailyStats,
        routeStats,
      },
    });
  } catch (error) {
    logger.error('Reports generation error:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to generate reports' } });
  }
}

export async function getStaff(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const { role } = req.query as { role?: string };
    const orgId = user.role === UserRole.BUS_OWNER ? user.organizationId : undefined;

    const where: any = {
      role: role ? (role as UserRole) : { in: [UserRole.DRIVER, UserRole.CONDUCTOR] },
    };

    if (orgId) {
      where.organizationId = orgId;
    }

    const staff = await prisma.user.findMany({
      where,
      select: {
        id: true,
        email: true,
        phone: true,
        firstName: true,
        lastName: true,
        role: true,
        isActive: true,
        organizationId: true,
        createdAt: true,
        lastLoginAt: true,
      },
      orderBy: { createdAt: 'desc' },
    });

    res.json({ success: true, data: staff });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch staff' } });
  }
}

export async function createStaff(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const parseResult = createStaffSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid staff data', details: parseResult.error.format() },
      });
      return;
    }

    const { email, password, firstName, lastName, phone, role, organizationId } = parseResult.data;
    const orgId = user.role === UserRole.BUS_OWNER ? user.organizationId : (organizationId || user.organizationId);

    if (!orgId) {
      res.status(400).json({ success: false, error: { code: 'ORG_REQUIRED', message: 'Organization ID is required' } });
      return;
    }

    const existing = await prisma.user.findUnique({ where: { email } });
    if (existing) {
      res.status(409).json({ success: false, error: { code: 'EMAIL_EXISTS', message: 'Email already registered' } });
      return;
    }

    const passwordHash = await hashPassword(password);

    const newStaff = await prisma.user.create({
      data: {
        email,
        passwordHash,
        firstName,
        lastName,
        phone,
        role,
        organizationId: orgId,
        isVerified: true,
      },
      select: {
        id: true,
        email: true,
        phone: true,
        firstName: true,
        lastName: true,
        role: true,
        isActive: true,
        organizationId: true,
        createdAt: true,
      },
    });

    res.status(201).json({ success: true, data: newStaff, message: 'Staff account created successfully' });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to create staff account' } });
  }
}

export async function toggleStaffStatus(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const { isActive } = req.body as { isActive: boolean };

    const staff = await prisma.user.findUnique({ where: { id } });
    if (!staff) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Staff member not found' } });
      return;
    }

    const updated = await prisma.user.update({
      where: { id },
      data: { isActive },
      select: { id: true, email: true, isActive: true },
    });

    res.json({ success: true, data: updated });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to update staff status' } });
  }
}
