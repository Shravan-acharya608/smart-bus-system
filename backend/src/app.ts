import express, { Request, Response, NextFunction } from 'express';
import http from 'http';
import cors from 'cors';
import helmet from 'helmet';
import morgan from 'morgan';
import rateLimit from 'express-rate-limit';
import apiRouter from './routes';
import { errorHandler } from './middleware/errorHandler';
import { logger } from './lib/logger';
import { createSocketServer } from './socket';

const app = express();

// Trust reverse proxy headers (Vercel, Render, Cloudflare)
app.set('trust proxy', 1);

// ── Security headers ─────────────────────────────────────────────────────────
app.use(helmet());

// ── CORS ─────────────────────────────────────────────────────────────────────
const allowedOrigins = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(',').map((o) => o.trim())
  : ['http://localhost:3000', 'http://localhost:5173'];

const isDev = process.env.NODE_ENV !== 'production';

app.use(
  cors({
    origin: (origin, callback) => {
      // Allow requests with no origin (e.g. mobile apps, curl, Postman)
      if (!origin) return callback(null, true);

      // In development, allow localhost, 127.0.0.1, and private LAN IPs
      if (isDev) return callback(null, true);

      // Explicitly listed origins or wildcard
      if (allowedOrigins.includes(origin) || allowedOrigins.includes('*')) {
        return callback(null, true);
      }

      // Automatically allow Vercel and Render cloud frontend domains
      try {
        const parsed = new URL(origin);
        if (parsed.hostname.endsWith('.vercel.app') || parsed.hostname.endsWith('.onrender.com')) {
          return callback(null, true);
        }
      } catch {
        // invalid URL format, proceed to checks below
      }

      const isLocal = /^https?:\/\/(localhost|127\.0\.0\.1|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+)(:\d+)?$/.test(origin);
      if (isLocal) {
        return callback(null, true);
      }

      return callback(null, false);
    },
    credentials: true,
    methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization'],
  }),
);

// ── HTTP request logging ─────────────────────────────────────────────────────
app.use(
  morgan('combined', {
    stream: { write: (msg: string) => logger.http(msg.trim()) },
  }),
);

// ── Body parsing ─────────────────────────────────────────────────────────────
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true, limit: '1mb' }));

// ── Global rate limiter ──────────────────────────────────────────────────────
const globalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: parseInt(process.env.RATE_LIMIT_MAX || '200', 10),
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_EXCEEDED',
      message: 'Too many requests. Please try again later.',
    },
  },
});

app.use(globalLimiter);

// ── Auth-specific stricter rate limiter ──────────────────────────────────────
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: parseInt(process.env.AUTH_RATE_LIMIT_MAX || '20', 10),
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    success: false,
    error: {
      code: 'RATE_LIMIT_EXCEEDED',
      message: 'Too many auth attempts. Please try again later.',
    },
  },
});

app.use('/api/v1/auth', authLimiter);

// ── API Routes ───────────────────────────────────────────────────────────────
app.get('/', (_req: Request, res: Response) => {
  res.json({
    success: true,
    data: {
      service: 'SmartBus Transit Backend Server',
      status: 'ONLINE',
      version: '1.0.0',
      apiBaseUrl: '/api/v1',
      webAdminDashboard: 'http://localhost:5173 (or http://192.168.43.196:5173)'
    }
  });
});

app.use('/api/v1', apiRouter);

// ── 404 handler ──────────────────────────────────────────────────────────────
app.use((req: Request, res: Response) => {
  res.status(404).json({
    success: false,
    error: {
      code: 'NOT_FOUND',
      message: `Route ${req.method} ${req.path} not found`,
    },
  });
});

// ── Global error handler ─────────────────────────────────────────────────────
app.use((err: Error, req: Request, res: Response, next: NextFunction) => {
  errorHandler(err, req, res, next);
});

// ── HTTP server + Socket.IO ──────────────────────────────────────────────────
export const httpServer = http.createServer(app);
if (!process.env.VERCEL) {
  createSocketServer(httpServer);
}

export default app;
