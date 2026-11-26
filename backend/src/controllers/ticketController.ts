import { Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { z } from 'zod';
import { TicketStatus, PaymentStatus, PaymentMethod, UserRole, EscrowStatus } from '@prisma/client';
import { getIo } from '../socket';
import { v4 as uuidv4 } from 'uuid';

const createTicketSchema = z.object({
  tripId: z.string().uuid(),
  boardingStopId: z.string().uuid(),
  destinationStopId: z.string().uuid(),
  paymentMethod: z.nativeEnum(PaymentMethod).optional().default(PaymentMethod.ONLINE),
});

const verifyTicketSchema = z.object({
  ticketCode: z.string().min(1),
  tripId: z.string().uuid(),
});

const onboardTicketSchema = z.object({
  tripId: z.string().uuid(),
  boardingStopId: z.string().uuid(),
  destinationStopId: z.string().uuid(),
  fare: z.number().min(0),
  paymentMethod: z.nativeEnum(PaymentMethod).default(PaymentMethod.CASH),
  clientEventId: z.string().optional(),
});

export async function createTicket(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const parseResult = createTicketSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid ticket request', details: parseResult.error.format() },
      });
      return;
    }

    const { tripId, boardingStopId, destinationStopId, paymentMethod } = parseResult.data;

    const trip = await prisma.trip.findUnique({
      where: { id: tripId },
      include: {
        route: {
          include: {
            stops: {
              orderBy: { stopOrder: 'asc' },
            },
          },
        },
      },
    });

    if (!trip) {
      res.status(404).json({ success: false, error: { code: 'TRIP_NOT_FOUND', message: 'Trip not found' } });
      return;
    }

    if (trip.status === 'COMPLETED' || trip.status === 'CANCELLED') {
      res.status(400).json({
        success: false,
        error: { code: 'TRIP_INACTIVE', message: 'Cannot book ticket for a completed or cancelled trip' },
      });
      return;
    }

    // Validate stops on this route
    const boardingStop = trip.route.stops.find((s) => s.stopId === boardingStopId);
    const destinationStop = trip.route.stops.find((s) => s.stopId === destinationStopId);

    if (!boardingStop || !destinationStop) {
      res.status(400).json({
        success: false,
        error: { code: 'INVALID_STOPS', message: 'Selected stops do not belong to the trip route' },
      });
      return;
    }

    if (boardingStop.stopOrder >= destinationStop.stopOrder) {
      res.status(400).json({
        success: false,
        error: { code: 'INVALID_STOP_ORDER', message: 'Boarding stop must be before destination stop' },
      });
      return;
    }

    // Backend Fare Calculation
    const stopCount = destinationStop.stopOrder - boardingStop.stopOrder;
    const fareDiff = (destinationStop.fareFromStart || 0) - (boardingStop.fareFromStart || 0);
    // Base fare: calculated difference or minimum ₹10 + ₹5 per stop
    const fare = fareDiff > 0 ? fareDiff : Math.max(10, stopCount * 10);

    const ticketCode = uuidv4();
    const qrData = JSON.stringify({
      code: ticketCode,
      tripId,
      t: Date.now(),
    });

    // Create ticket & pending payment in a single transaction
    const ticket = await prisma.$transaction(async (tx) => {
      const newTicket = await tx.ticket.create({
        data: {
          ticketCode,
          passengerId: user.userId,
          tripId,
          boardingStopId,
          destinationStopId,
          fare,
          status: TicketStatus.PENDING,
          paymentMethod,
          qrData,
        },
      });

      await tx.payment.create({
        data: {
          ticketId: newTicket.id,
          amount: fare,
          currency: 'INR',
          status: PaymentStatus.PENDING,
          paymentMethod,
          isSandbox: true,
          escrowStatus: EscrowStatus.HELD_IN_ESCROW,
        },
      });

      // Track frequent route
      try {
        await tx.frequentRoute.upsert({
          where: {
            passengerId_boardingStopId_destinationStopId: {
              passengerId: user.userId,
              boardingStopId,
              destinationStopId,
            },
          },
          create: {
            passengerId: user.userId,
            boardingStopId,
            destinationStopId,
            routeId: trip.routeId,
            tripCount: 1,
          },
          update: {
            tripCount: { increment: 1 },
            lastUsedAt: new Date(),
          },
        });
      } catch (_e) {
        // ignore duplicate or non-fatal frequent route tracking error
      }

      return tx.ticket.findUnique({
        where: { id: newTicket.id },
        include: {
          payment: true,
          trip: {
            include: {
              bus: { select: { busNumber: true } },
              route: { select: { name: true, code: true } },
            },
          },
        },
      });
    });

    res.status(201).json({ success: true, data: ticket });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to create ticket' } });
  }
}

export async function getMyTickets(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const { page = '1', limit = '20' } = req.query as Record<string, string>;

    const pageNum = Math.max(1, parseInt(page, 10) || 1);
    const limitNum = Math.min(100, Math.max(1, parseInt(limit, 10) || 20));
    const skip = (pageNum - 1) * limitNum;

    const [tickets, total] = await Promise.all([
      prisma.ticket.findMany({
        where: { passengerId: user.userId },
        skip,
        take: limitNum,
        orderBy: { createdAt: 'desc' },
        include: {
          trip: {
            include: {
              bus: { select: { busNumber: true } },
              route: { select: { name: true, code: true } },
            },
          },
          payment: true,
          boardingEvent: true,
          exitEvent: true,
        },
      }),
      prisma.ticket.count({ where: { passengerId: user.userId } }),
    ]);

    res.json({
      success: true,
      data: tickets,
      pagination: {
        page: pageNum,
        limit: limitNum,
        total,
        pages: Math.ceil(total / limitNum),
      },
    });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch tickets' } });
  }
}

export async function getTicketById(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const user = req.user;

    const ticket = await prisma.ticket.findFirst({
      where: {
        OR: [{ id }, { ticketCode: id }],
      },
      include: {
        passenger: { select: { id: true, firstName: true, lastName: true, phone: true } },
        trip: {
          include: {
            bus: { select: { busNumber: true, registrationNo: true } },
            route: { select: { name: true, code: true } },
          },
        },
        payment: true,
        boardingEvent: true,
        exitEvent: true,
      },
    });

    if (!ticket) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Ticket not found' } });
      return;
    }

    // Role verification: Passenger can only view their own ticket; Driver/Conductor/Admin can view
    if (user.role === UserRole.PASSENGER && ticket.passengerId !== user.userId) {
      res.status(403).json({ success: false, error: { code: 'FORBIDDEN', message: 'Access denied to this ticket' } });
      return;
    }

    res.json({ success: true, data: ticket });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to fetch ticket' } });
  }
}

export async function verifyTicket(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const parseResult = verifyTicketSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid ticket verification data', details: parseResult.error.format() },
      });
      return;
    }

    const { ticketCode, tripId } = parseResult.data;

    const ticket = await prisma.ticket.findUnique({
      where: { ticketCode },
      include: {
        passenger: { select: { firstName: true, lastName: true, phone: true } },
        payment: true,
        boardingEvent: true,
        exitEvent: true,
        trip: { select: { id: true, status: true } },
      },
    });

    if (!ticket) {
      res.status(404).json({
        success: false,
        error: { code: 'INVALID_TICKET', message: 'Ticket code does not exist' },
      });
      return;
    }

    // Verify trip match
    if (ticket.tripId !== tripId) {
      res.status(400).json({
        success: false,
        error: { code: 'TRIP_MISMATCH', message: 'This ticket is valid for a different trip' },
      });
      return;
    }

    // Check HELD tickets
    if (ticket.status === TicketStatus.HELD) {
      res.json({
        success: true,
        data: {
          ticketId: ticket.id,
          ticketCode: ticket.ticketCode,
          passengerName: `${ticket.passenger.firstName} ${ticket.passenger.lastName}`,
          fare: ticket.fare,
          status: ticket.status,
          isHeldPaymentRequired: true,
          amountToCollect: ticket.fare,
          paymentStatus: ticket.payment?.status,
          paymentMethod: ticket.paymentMethod,
          isValid: true,
        },
        message: `Seat held. Collect ₹${ticket.fare} before confirming boarding.`,
      });
      return;
    }

    // Verify payment status
    if (ticket.status === TicketStatus.PENDING) {
      res.status(400).json({
        success: false,
        error: { code: 'PAYMENT_PENDING', message: 'Ticket payment is pending' },
      });
      return;
    }

    // Verify not already boarded
    if (ticket.boardingEvent) {
      res.status(409).json({
        success: false,
        error: {
          code: 'ALREADY_USED',
          message: `Ticket already boarded at ${new Date(ticket.boardingEvent.scannedAt).toLocaleTimeString()}`,
        },
      });
      return;
    }

    if (ticket.status === TicketStatus.CANCELLED || ticket.status === TicketStatus.EXPIRED) {
      res.status(400).json({
        success: false,
        error: { code: 'TICKET_EXPIRED', message: `Ticket is ${ticket.status.toLowerCase()}` },
      });
      return;
    }

    res.json({
      success: true,
      data: {
        ticketId: ticket.id,
        ticketCode: ticket.ticketCode,
        passengerName: `${ticket.passenger.firstName} ${ticket.passenger.lastName}`,
        fare: ticket.fare,
        status: ticket.status,
        paymentStatus: ticket.payment?.status,
        paymentMethod: ticket.paymentMethod,
        isValid: true,
      },
      message: 'Ticket verified successfully',
    });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Verification failed' } });
  }
}

export async function recordBoarding(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const { stopId, clientEventId } = req.body as { stopId?: string; clientEventId?: string };

    const ticket = await prisma.ticket.findFirst({
      where: { OR: [{ id }, { ticketCode: id }] },
      include: { boardingEvent: true, trip: { select: { id: true, bus: { select: { capacity: true } } } } },
    });

    if (!ticket) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Ticket not found' } });
      return;
    }

    if (ticket.boardingEvent) {
      res.status(409).json({
        success: false,
        error: { code: 'ALREADY_BOARDED', message: 'Boarding event already recorded for this ticket' },
      });
      return;
    }

    // Prevent duplicate sync using clientEventId
    if (clientEventId) {
      const existingEvent = await prisma.boardingEvent.findUnique({
        where: { clientEventId },
      });
      if (existingEvent) {
        res.json({ success: true, data: existingEvent, message: 'Event already recorded (idempotent)' });
        return;
      }
    }

    const boardingEvent = await prisma.$transaction(async (tx) => {
      const event = await tx.boardingEvent.create({
        data: {
          ticketId: ticket.id,
          tripId: ticket.tripId,
          stopId: stopId || ticket.boardingStopId,
          clientEventId: clientEventId || null,
        },
      });

      await tx.ticket.update({
        where: { id: ticket.id },
        data: { status: TicketStatus.USED },
      });

      if (ticket.status === TicketStatus.HELD) {
        await tx.payment.updateMany({
          where: { ticketId: ticket.id },
          data: {
            status: PaymentStatus.SUCCESS,
            escrowStatus: EscrowStatus.SETTLED_TO_OWNER,
          },
        });
      }

      return event;
    });

    // Broadcast passenger count update via Socket.IO
    try {
      const [boardings, exits] = await Promise.all([
        prisma.boardingEvent.count({ where: { tripId: ticket.tripId } }),
        prisma.exitEvent.count({ where: { tripId: ticket.tripId } }),
      ]);
      const currentOnboard = Math.max(0, boardings - exits);

      const io = getIo();
      io.to(`trip:${ticket.tripId}`).emit('passenger-count-updated', {
        tripId: ticket.tripId,
        currentOnboard,
        totalBoardings: boardings,
        totalExits: exits,
      });
    } catch (e) {}

    res.status(201).json({ success: true, data: boardingEvent, message: 'Boarding recorded successfully' });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to record boarding' } });
  }
}

export async function recordExit(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const { stopId, clientEventId } = req.body as { stopId?: string; clientEventId?: string };

    const ticket = await prisma.ticket.findFirst({
      where: { OR: [{ id }, { ticketCode: id }] },
      include: { boardingEvent: true, exitEvent: true },
    });

    if (!ticket) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Ticket not found' } });
      return;
    }

    if (!ticket.boardingEvent) {
      res.status(400).json({
        success: false,
        error: { code: 'NOT_BOARDED', message: 'Cannot record exit before passenger has boarded' },
      });
      return;
    }

    if (ticket.exitEvent) {
      res.status(409).json({
        success: false,
        error: { code: 'ALREADY_EXITED', message: 'Exit event already recorded for this ticket' },
      });
      return;
    }

    if (clientEventId) {
      const existing = await prisma.exitEvent.findUnique({ where: { clientEventId } });
      if (existing) {
        res.json({ success: true, data: existing, message: 'Event already recorded (idempotent)' });
        return;
      }
    }

    const exitEvent = await prisma.exitEvent.create({
      data: {
        ticketId: ticket.id,
        tripId: ticket.tripId,
        stopId: stopId || ticket.destinationStopId,
        clientEventId: clientEventId || null,
      },
    });

    // Broadcast passenger count update via Socket.IO
    try {
      const [boardings, exits] = await Promise.all([
        prisma.boardingEvent.count({ where: { tripId: ticket.tripId } }),
        prisma.exitEvent.count({ where: { tripId: ticket.tripId } }),
      ]);
      const currentOnboard = Math.max(0, boardings - exits);

      const io = getIo();
      io.to(`trip:${ticket.tripId}`).emit('passenger-count-updated', {
        tripId: ticket.tripId,
        currentOnboard,
        totalBoardings: boardings,
        totalExits: exits,
      });
    } catch (e) {}

    res.status(201).json({ success: true, data: exitEvent, message: 'Exit recorded successfully' });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to record exit' } });
  }
}

export async function issueOnboardTicket(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const parseResult = onboardTicketSchema.safeParse(req.body);

    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid onboard ticket data', details: parseResult.error.format() },
      });
      return;
    }

    const { tripId, boardingStopId, destinationStopId, fare, paymentMethod, clientEventId } = parseResult.data;

    // Check idempotency for offline-synced records
    if (clientEventId) {
      const existingTicket = await prisma.ticket.findUnique({
        where: { clientEventId },
        include: { boardingEvent: true, payment: true },
      });
      if (existingTicket) {
        res.json({ success: true, data: existingTicket, message: 'Onboard ticket already recorded (idempotent)' });
        return;
      }
    }

    const ticketCode = uuidv4();
    const qrData = JSON.stringify({ code: ticketCode, tripId, t: Date.now() });

    // Issue ticket, mark PAID, create payment, and record boarding event in transaction
    const ticket = await prisma.$transaction(async (tx) => {
      const newTicket = await tx.ticket.create({
        data: {
          ticketCode,
          passengerId: user.userId, // Conductor acts as creator or passenger proxy
          tripId,
          boardingStopId,
          destinationStopId,
          fare,
          status: TicketStatus.USED, // Already onboard
          paymentMethod,
          isOfflineIssued: true,
          clientEventId: clientEventId || null,
          qrData,
          issuedByConductorId: user.userId,
        },
      });

      await tx.payment.create({
        data: {
          ticketId: newTicket.id,
          amount: fare,
          currency: 'INR',
          status: PaymentStatus.SUCCESS,
          paymentMethod,
          isSandbox: true,
        },
      });

      await tx.boardingEvent.create({
        data: {
          ticketId: newTicket.id,
          tripId,
          stopId: boardingStopId,
          isOffline: !!clientEventId,
          clientEventId: clientEventId ? `board_${clientEventId}` : null,
        },
      });

      return tx.ticket.findUnique({
        where: { id: newTicket.id },
        include: { payment: true, boardingEvent: true },
      });
    });

    // Broadcast passenger count update
    try {
      const [boardings, exits] = await Promise.all([
        prisma.boardingEvent.count({ where: { tripId } }),
        prisma.exitEvent.count({ where: { tripId } }),
      ]);
      const currentOnboard = Math.max(0, boardings - exits);

      const io = getIo();
      io.to(`trip:${tripId}`).emit('passenger-count-updated', {
        tripId,
        currentOnboard,
        totalBoardings: boardings,
        totalExits: exits,
      });
    } catch (e) {}

    res.status(201).json({ success: true, data: ticket, message: 'Onboard ticket issued successfully' });
  } catch (error) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to issue onboard ticket' } });
  }
}

export async function holdTicket(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const { tripId, boardingStopId, destinationStopId, clientEventId } = req.body;

    if (!tripId || !boardingStopId || !destinationStopId) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'tripId, boardingStopId, and destinationStopId are required' },
      });
      return;
    }

    const trip = await prisma.trip.findUnique({
      where: { id: tripId },
      include: {
        bus: true,
        route: { include: { stops: { orderBy: { stopOrder: 'asc' } } } },
        tickets: { where: { status: { in: [TicketStatus.PAID, TicketStatus.HELD] } }, select: { id: true } },
      },
    });

    if (!trip) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Trip not found' } });
      return;
    }

    if (!['SCHEDULED', 'ACTIVE'].includes(trip.status)) {
      res.status(400).json({ success: false, error: { code: 'TRIP_INACTIVE', message: 'Trip is not available for booking' } });
      return;
    }

    if (trip.tickets.length >= trip.bus.capacity) {
      res.status(400).json({ success: false, error: { code: 'BUS_FULL', message: 'Bus is at full capacity' } });
      return;
    }

    const boardingStop = trip.route.stops.find((s) => s.stopId === boardingStopId);
    const destinationStop = trip.route.stops.find((s) => s.stopId === destinationStopId);

    if (!boardingStop || !destinationStop || boardingStop.stopOrder >= destinationStop.stopOrder) {
      res.status(400).json({
        success: false,
        error: { code: 'INVALID_STOPS', message: 'Invalid boarding or destination stop sequence' },
      });
      return;
    }

    const stopCount = destinationStop.stopOrder - boardingStop.stopOrder;
    const fareDiff = (destinationStop.fareFromStart || 0) - (boardingStop.fareFromStart || 0);
    const fare = fareDiff > 0 ? fareDiff : Math.max(10, stopCount * 10);

    const ticketCode = uuidv4();
    const qrData = JSON.stringify({
      code: ticketCode,
      tripId,
      status: 'HELD',
      t: Date.now(),
    });

    const ticket = await prisma.$transaction(async (tx) => {
      const held = await tx.ticket.create({
        data: {
          ticketCode,
          passengerId: user.userId,
          tripId,
          boardingStopId,
          destinationStopId,
          fare,
          status: TicketStatus.HELD,
          paymentMethod: PaymentMethod.CASH,
          clientEventId: clientEventId || null,
          qrData,
        },
      });

      await tx.payment.create({
        data: {
          ticketId: held.id,
          amount: fare,
          currency: 'INR',
          status: PaymentStatus.PENDING,
          paymentMethod: PaymentMethod.CASH,
          isSandbox: true,
          escrowStatus: EscrowStatus.HELD_IN_ESCROW,
        },
      });

      // Upsert FrequentRoute
      try {
        await tx.frequentRoute.upsert({
          where: {
            passengerId_boardingStopId_destinationStopId: {
              passengerId: user.userId,
              boardingStopId,
              destinationStopId,
            },
          },
          create: {
            passengerId: user.userId,
            boardingStopId,
            destinationStopId,
            routeId: trip.routeId,
            tripCount: 1,
          },
          update: {
            tripCount: { increment: 1 },
            lastUsedAt: new Date(),
          },
        });
      } catch (_e) {}

      return tx.ticket.findUnique({
        where: { id: held.id },
        include: {
          payment: true,
          trip: {
            include: {
              bus: { select: { busNumber: true } },
              route: { select: { name: true, code: true } },
            },
          },
        },
      });
    });

    res.status(201).json({ success: true, data: ticket });
  } catch (error: any) {
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: error.message || 'Failed to hold ticket' } });
  }
}

export async function payInstantTicket(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const user = req.user;
    const { tripId, boardingStopId, destinationStopId, paymentMode = 'RAZORPAY', razorpayRef } = req.body;

    if (!tripId || !boardingStopId || !destinationStopId) {
      res.status(400).json({ success: false, error: { code: 'VALIDATION_ERROR', message: 'Missing trip or stop IDs' } });
      return;
    }

    const trip = await prisma.trip.findUnique({
      where: { id: tripId },
      include: { route: { include: { stops: { orderBy: { stopOrder: 'asc' } } } } },
    });

    if (!trip) {
      res.status(404).json({ success: false, error: { code: 'TRIP_NOT_FOUND', message: 'Trip not found' } });
      return;
    }

    const boardingStop = trip.route.stops.find((s) => s.stopId === boardingStopId);
    const destinationStop = trip.route.stops.find((s) => s.stopId === destinationStopId);

    if (!boardingStop || !destinationStop || boardingStop.stopOrder >= destinationStop.stopOrder) {
      res.status(400).json({ success: false, error: { code: 'INVALID_STOPS', message: 'Invalid boarding/destination stops' } });
      return;
    }

    const fareDiff = (destinationStop.fareFromStart || 0) - (boardingStop.fareFromStart || 0);
    const fare = fareDiff > 0 ? fareDiff : Math.max(10, (destinationStop.stopOrder - boardingStop.stopOrder) * 10);

    const dbUser = await prisma.user.findUnique({ where: { id: user.userId } });
    if (!dbUser) {
      res.status(404).json({ success: false, error: { code: 'USER_NOT_FOUND', message: 'User not found' } });
      return;
    }

    if (paymentMode === 'WALLET' && (dbUser.walletBalance ?? 0) < fare) {
      res.status(400).json({
        success: false,
        error: { code: 'INSUFFICIENT_BALANCE', message: `Insufficient wallet balance (₹${dbUser.walletBalance}). Please top up or pay via Razorpay.` },
      });
      return;
    }

    const ticketCode = uuidv4();
    const qrData = JSON.stringify({ code: ticketCode, tripId, mode: paymentMode, t: Date.now() });
    const methodEnum = paymentMode === 'WALLET' ? PaymentMethod.UPI : PaymentMethod.ONLINE;

    const result = await prisma.$transaction(async (tx) => {
      let updatedBalance = dbUser.walletBalance ?? 500;
      if (paymentMode === 'WALLET') {
        const updatedUser = await tx.user.update({
          where: { id: user.userId },
          data: { walletBalance: { decrement: fare } },
        });
        updatedBalance = updatedUser.walletBalance;
      }

      const paidTicket = await tx.ticket.create({
        data: {
          ticketCode,
          passengerId: user.userId,
          tripId,
          boardingStopId,
          destinationStopId,
          fare,
          status: TicketStatus.PAID,
          paymentMethod: methodEnum,
          qrData,
        },
      });

      await tx.payment.create({
        data: {
          ticketId: paidTicket.id,
          amount: fare,
          currency: 'INR',
          status: PaymentStatus.SUCCESS,
          paymentMethod: methodEnum,
          isSandbox: true,
          escrowStatus: EscrowStatus.HELD_IN_ESCROW,
        },
      });

      const fullTicket = await tx.ticket.findUnique({
        where: { id: paidTicket.id },
        include: {
          payment: true,
          trip: {
            include: {
              bus: { select: { busNumber: true } },
              route: { select: { name: true, code: true } },
            },
          },
        },
      });

      return { ticket: fullTicket, walletBalance: updatedBalance };
    });

    res.status(201).json({
      success: true,
      data: result,
      message: `Ticket booked & paid (₹${fare}) via ${paymentMode}!`,
    });
  } catch (error: any) {
    console.error('payInstantTicket error:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: error.message || 'Instant payment failed' } });
  }
}

