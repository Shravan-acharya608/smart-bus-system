import { Server as HttpServer } from 'http';
import { Server as SocketIOServer, Socket } from 'socket.io';
import { verifyAccessToken } from '../utils/jwt';
import { logger } from '../lib/logger';

let io: SocketIOServer;

interface LocationData {
  latitude: number;
  longitude: number;
  speed?: number;
  heading?: number;
  accuracy?: number;
  timestamp?: string;
}

/**
 * Attach Socket.IO to the existing HTTP server.
 * Authenticates connections via JWT in the handshake auth object.
 */
export function createSocketServer(httpServer: HttpServer): SocketIOServer {
  const allowedOrigins = process.env.ALLOWED_ORIGINS
    ? process.env.ALLOWED_ORIGINS.split(',').map((o) => o.trim())
    : ['http://localhost:3000'];

  io = new SocketIOServer(httpServer, {
    cors: {
      origin: '*',
      methods: ['GET', 'POST'],
      credentials: true,
    },
    pingTimeout: 60000,
    pingInterval: 25000,
  });

  // ── Authentication middleware ──────────────────────────────────────────────
  io.use((socket: Socket, next) => {
    const token =
      (socket.handshake.auth as { token?: string }).token ||
      (socket.handshake.headers.authorization as string | undefined)?.split(' ')[1];

    if (!token) {
      return next(new Error('Authentication token required'));
    }

    try {
      const payload = verifyAccessToken(token);
      (socket.data as { userId: string; role: string; organizationId: string | null }) = {
        userId: payload.userId,
        role: payload.role,
        organizationId: payload.organizationId,
      };
      return next();
    } catch {
      return next(new Error('Invalid or expired token'));
    }
  });

  // ── Connection handler ─────────────────────────────────────────────────────
  io.on('connection', (socket: Socket) => {
    const { userId, role } = socket.data as { userId: string; role: string };
    logger.info(`Socket connected: userId=${userId}, role=${role}, socketId=${socket.id}`);

    /**
     * Client joins a trip room to receive real-time location updates.
     * Event: 'join-trip' { tripId: string }
     */
    socket.on('join-trip', ({ tripId }: { tripId: string }) => {
      if (!tripId) {
        socket.emit('error', { message: 'tripId is required to join a trip room' });
        return;
      }
      const room = `trip:${tripId}`;
      socket.join(room);
      logger.info(`Socket ${socket.id} (userId=${userId}) joined room ${room}`);
      socket.emit('joined-trip', { tripId, room });
    });

    /**
     * Driver/conductor broadcasts a location update to all passengers in the trip room.
     * Event: 'location-update' { tripId: string, latitude: number, longitude: number, ... }
     */
    socket.on(
      'location-update',
      (data: { tripId: string } & LocationData) => {
        if (!data.tripId) {
          socket.emit('error', { message: 'tripId is required for location updates' });
          return;
        }

        const room = `trip:${data.tripId}`;
        // Broadcast to everyone else in the room (not the sender)
        socket.to(room).emit('location-update', {
          tripId: data.tripId,
          latitude: data.latitude,
          longitude: data.longitude,
          speed: data.speed ?? 0,
          heading: data.heading ?? 0,
          accuracy: data.accuracy,
          timestamp: data.timestamp ?? new Date().toISOString(),
          sentBy: userId,
        });
      },
    );

    /**
     * Client leaves a trip room.
     * Event: 'leave-trip' { tripId: string }
     */
    socket.on('leave-trip', ({ tripId }: { tripId: string }) => {
      const room = `trip:${tripId}`;
      socket.leave(room);
      logger.info(`Socket ${socket.id} (userId=${userId}) left room ${room}`);
    });

    socket.on('disconnect', (reason) => {
      logger.info(`Socket disconnected: socketId=${socket.id}, userId=${userId}, reason=${reason}`);
    });

    socket.on('error', (err: Error) => {
      logger.error(`Socket error: socketId=${socket.id}, ${err.message}`);
    });
  });

  return io;
}

/**
 * Returns the shared Socket.IO server instance.
 * Throws if createSocketServer has not been called yet.
 */
export function getIo(): SocketIOServer {
  if (!io) throw new Error('Socket.IO server not initialized. Call createSocketServer first.');
  return io;
}
