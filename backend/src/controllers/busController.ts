import { Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { z } from 'zod';
import { BusStatus, UserRole } from '@prisma/client';

const createBusSchema = z.object({
  registrationNo: z.string().min(3).max(20),
  busNumber: z.string().min(1).max(20),
  capacity: z.number().int().min(1).max(150),
  status: z.nativeEnum(BusStatus).optional().default(BusStatus.ACTIVE),
  make: z.string().optional(),
  model: z.string().optional(),
  year: z.number().int().min(1990).max(new Date().getFullYear() + 1).optional(),
  organizationId: z.string().uuid().optional(),
});

const updateBusSchema = createBusSchema.partial();

export async function getBuses(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const { status, search, page = '1', limit = '20' } = req.query as Record<string, string>;

    const pageNum = Math.max(1, parseInt(page, 10) || 1);
    const limitNum = Math.min(100, Math.max(1, parseInt(limit, 10) || 20));
    const skip = (pageNum - 1) * limitNum;

    const where: any = {};

    // Organization data isolation
    if (user.role === UserRole.BUS_OWNER) {
      if (!user.organizationId) {
        res.status(403).json({ success: false, error: { code: 'NO_ORG', message: 'No organization linked' } });
        return;
      }
      where.organizationId = user.organizationId;
    }

    if (status && Object.values(BusStatus).includes(status as BusStatus)) {
      where.status = status as BusStatus;
    }

    if (search) {
      where.OR = [
        { busNumber: { contains: search, mode: 'insensitive' } },
        { registrationNo: { contains: search, mode: 'insensitive' } },
        { make: { contains: search, mode: 'insensitive' } },
        { model: { contains: search, mode: 'insensitive' } },
      ];
    }

    const [buses, total] = await Promise.all([
      prisma.bus.findMany({
        where,
        skip,
        take: limitNum,
        orderBy: { createdAt: 'desc' },
        include: {
          organization: { select: { id: true, name: true, code: true } },
          trips: {
            where: { status: 'ACTIVE' },
            take: 1,
            select: { id: true, status: true, route: { select: { id: true, name: true, code: true } } },
          },
        },
      }),
      prisma.bus.count({ where }),
    ]);

    res.json({
      success: true,
      data: buses,
      pagination: {
        page: pageNum,
        limit: limitNum,
        total,
        pages: Math.ceil(total / limitNum),
      },
    });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch buses' } });
  }
}

export async function getBusById(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    const bus = await prisma.bus.findUnique({
      where: { id },
      include: {
        organization: true,
        trips: {
          take: 5,
          orderBy: { scheduledStart: 'desc' },
          include: { route: true },
        },
      },
    });

    if (!bus) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Bus not found' } });
      return;
    }

    if (user.role === UserRole.BUS_OWNER && bus.organizationId !== user.organizationId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Access denied to this bus' } });
      return;
    }

    res.json({ success: true, data: bus });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch bus' } });
  }
}

export async function createBus(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const parseResult = createBusSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid bus data', details: parseResult.error.format() },
      });
      return;
    }

    const data = parseResult.data;
    const orgId = user.role === UserRole.BUS_OWNER ? user.organizationId : (data.organizationId || user.organizationId);

    if (!orgId) {
      res.status(400).json({
        success: false,
        error: { code: 'ORG_REQUIRED', message: 'Organization ID is required' },
      });
      return;
    }

    // Check unique constraints
    const existing = await prisma.bus.findFirst({
      where: {
        OR: [{ registrationNo: data.registrationNo }, { busNumber: data.busNumber }],
      },
    });

    if (existing) {
      res.status(409).json({
        success: false,
        error: { code: 'DUPLICATE_BUS', message: 'Bus number or registration number already exists' },
      });
      return;
    }

    const bus = await prisma.bus.create({
      data: {
        registrationNo: data.registrationNo,
        busNumber: data.busNumber,
        capacity: data.capacity,
        status: data.status,
        make: data.make,
        model: data.model,
        year: data.year,
        organizationId: orgId,
      },
      include: {
        organization: { select: { id: true, name: true, code: true } },
      },
    });

    res.status(201).json({ success: true, data: bus });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to create bus' } });
  }
}

export async function updateBus(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    const existingBus = await prisma.bus.findUnique({ where: { id } });
    if (!existingBus) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Bus not found' } });
      return;
    }

    if (user.role === UserRole.BUS_OWNER && existingBus.organizationId !== user.organizationId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Access denied' } });
      return;
    }

    const parseResult = updateBusSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid data', details: parseResult.error.format() },
      });
      return;
    }

    const bus = await prisma.bus.update({
      where: { id },
      data: parseResult.data,
      include: { organization: { select: { id: true, name: true, code: true } } },
    });

    res.json({ success: true, data: bus });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to update bus' } });
  }
}

export async function deleteBus(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    const existingBus = await prisma.bus.findUnique({
      where: { id },
      include: { trips: { where: { status: 'ACTIVE' } } },
    });

    if (!existingBus) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Bus not found' } });
      return;
    }

    if (user.role === UserRole.BUS_OWNER && existingBus.organizationId !== user.organizationId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Access denied' } });
      return;
    }

    if (existingBus.trips.length > 0) {
      res.status(400).json({
        success: false,
        error: { code: 'ACTIVE_TRIP', message: 'Cannot delete bus with an active trip' },
      });
      return;
    }

    // Soft delete / set INACTIVE
    await prisma.bus.update({
      where: { id },
      data: { status: BusStatus.INACTIVE },
    });

    res.json({ success: true, message: 'Bus marked as inactive' });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to delete bus' } });
  }
}
