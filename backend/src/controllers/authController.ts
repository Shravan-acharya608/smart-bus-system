import { Request, Response, NextFunction } from 'express';
import { UserRole } from '@prisma/client';
import { z } from 'zod';
import prisma from '../lib/prisma';
import { hashPassword, comparePassword } from '../utils/hash';
import { generateAccessToken, generateRefreshToken } from '../utils/jwt';
import { logger } from '../lib/logger';

// ─── Zod Schemas ────────────────────────────────────────────────────────────

const registerSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(8, 'Password must be at least 8 characters'),
  firstName: z.string().min(1, 'First name is required').max(100),
  lastName: z.string().min(1, 'Last name is required').max(100),
  phone: z.string().optional(),
});

const loginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
  role: z
    .nativeEnum(UserRole)
    .optional(),
});

const refreshSchema = z.object({
  refreshToken: z.string().min(1, 'Refresh token is required'),
});

const logoutSchema = z.object({
  refreshToken: z.string().min(1, 'Refresh token is required'),
});

// ─── Helpers ────────────────────────────────────────────────────────────────

const REFRESH_TOKEN_TTL_DAYS = parseInt(process.env.REFRESH_TOKEN_TTL_DAYS || '7', 10);

function refreshTokenExpiresAt(): Date {
  const d = new Date();
  d.setDate(d.getDate() + REFRESH_TOKEN_TTL_DAYS);
  return d;
}

// ─── Controllers ────────────────────────────────────────────────────────────

/**
 * POST /api/v1/auth/register
 * Registers a new PASSENGER user.
 */
export async function register(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const body = registerSchema.parse(req.body);

    // Check for existing email / phone
    const existing = await prisma.user.findFirst({
      where: {
        OR: [
          { email: body.email },
          ...(body.phone ? [{ phone: body.phone }] : []),
        ],
      },
    });

    if (existing) {
      res.status(409).json({
        success: false,
        error: {
          code: 'CONFLICT',
          message:
            existing.email === body.email
              ? 'An account with this email already exists'
              : 'An account with this phone number already exists',
        },
      });
      return;
    }

    const passwordHash = await hashPassword(body.password);

    const user = await prisma.user.create({
      data: {
        email: body.email,
        passwordHash,
        firstName: body.firstName,
        lastName: body.lastName,
        phone: body.phone,
        role: UserRole.PASSENGER,
      },
      select: {
        id: true,
        email: true,
        firstName: true,
        lastName: true,
        phone: true,
        role: true,
        isVerified: true,
        createdAt: true,
      },
    });

    // Issue tokens
    const accessToken = generateAccessToken(user.id, user.role, null);
    const refreshTokenValue = generateRefreshToken();

    await prisma.refreshToken.create({
      data: {
        token: refreshTokenValue,
        userId: user.id,
        expiresAt: refreshTokenExpiresAt(),
      },
    });

    logger.info(`User registered: ${user.email}`);

    res.status(201).json({
      success: true,
      data: {
        user,
        tokens: {
          accessToken,
          refreshToken: refreshTokenValue,
          expiresIn: process.env.JWT_ACCESS_EXPIRES_IN || '15m',
        },
      },
    });
  } catch (err) {
    next(err);
  }
}

/**
 * POST /api/v1/auth/login
 */
export async function login(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const body = loginSchema.parse(req.body);

    const user = await prisma.user.findUnique({
      where: { email: body.email },
      select: {
        id: true,
        email: true,
        passwordHash: true,
        firstName: true,
        lastName: true,
        role: true,
        isActive: true,
        isVerified: true,
        organizationId: true,
      },
    });

    if (!user) {
      res.status(401).json({
        success: false,
        error: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' },
      });
      return;
    }

    if (!user.isActive) {
      res.status(403).json({
        success: false,
        error: { code: 'ACCOUNT_DISABLED', message: 'This account has been disabled' },
      });
      return;
    }

    // Enforce role restriction if caller specifies expected role
    if (body.role && user.role !== body.role) {
      res.status(403).json({
        success: false,
        error: { code: 'ROLE_MISMATCH', message: 'Invalid credentials for this role' },
      });
      return;
    }

    const passwordValid = await comparePassword(body.password, user.passwordHash);
    if (!passwordValid) {
      res.status(401).json({
        success: false,
        error: { code: 'INVALID_CREDENTIALS', message: 'Invalid email or password' },
      });
      return;
    }

    const accessToken = generateAccessToken(user.id, user.role, user.organizationId);
    const refreshTokenValue = generateRefreshToken();

    await prisma.$transaction([
      prisma.refreshToken.create({
        data: {
          token: refreshTokenValue,
          userId: user.id,
          expiresAt: refreshTokenExpiresAt(),
        },
      }),
      prisma.user.update({
        where: { id: user.id },
        data: { lastLoginAt: new Date() },
      }),
    ]);

    logger.info(`User logged in: ${user.email}`);

    res.status(200).json({
      success: true,
      data: {
        user: {
          id: user.id,
          email: user.email,
          firstName: user.firstName,
          lastName: user.lastName,
          role: user.role,
          isVerified: user.isVerified,
          organizationId: user.organizationId,
        },
        tokens: {
          accessToken,
          refreshToken: refreshTokenValue,
          expiresIn: process.env.JWT_ACCESS_EXPIRES_IN || '15m',
        },
      },
    });
  } catch (err) {
    next(err);
  }
}

/**
 * POST /api/v1/auth/refresh
 * Rotate refresh token and issue a new access token.
 */
export async function refreshToken(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const { refreshToken: token } = refreshSchema.parse(req.body);

    const stored = await prisma.refreshToken.findUnique({
      where: { token },
      include: { user: { select: { id: true, role: true, organizationId: true, isActive: true } } },
    });

    if (!stored) {
      res.status(401).json({
        success: false,
        error: { code: 'INVALID_REFRESH_TOKEN', message: 'Refresh token not found' },
      });
      return;
    }

    if (stored.isRevoked) {
      res.status(401).json({
        success: false,
        error: { code: 'TOKEN_REVOKED', message: 'Refresh token has been revoked' },
      });
      return;
    }

    if (stored.expiresAt < new Date()) {
      res.status(401).json({
        success: false,
        error: { code: 'TOKEN_EXPIRED', message: 'Refresh token has expired' },
      });
      return;
    }

    if (!stored.user.isActive) {
      res.status(403).json({
        success: false,
        error: { code: 'ACCOUNT_DISABLED', message: 'This account has been disabled' },
      });
      return;
    }

    // Rotate: revoke old, issue new
    const newRefreshToken = generateRefreshToken();

    await prisma.$transaction([
      prisma.refreshToken.update({
        where: { id: stored.id },
        data: { isRevoked: true },
      }),
      prisma.refreshToken.create({
        data: {
          token: newRefreshToken,
          userId: stored.userId,
          expiresAt: refreshTokenExpiresAt(),
        },
      }),
    ]);

    const accessToken = generateAccessToken(
      stored.user.id,
      stored.user.role,
      stored.user.organizationId,
    );

    res.status(200).json({
      success: true,
      data: {
        tokens: {
          accessToken,
          refreshToken: newRefreshToken,
          expiresIn: process.env.JWT_ACCESS_EXPIRES_IN || '15m',
        },
      },
    });
  } catch (err) {
    next(err);
  }
}

/**
 * POST /api/v1/auth/logout
 * Revoke the refresh token.
 */
export async function logout(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  try {
    const { refreshToken: token } = logoutSchema.parse(req.body);

    // Revoke if exists – silently succeed if not found (idempotent)
    await prisma.refreshToken.updateMany({
      where: { token, isRevoked: false },
      data: { isRevoked: true },
    });

    res.status(200).json({
      success: true,
      data: { message: 'Logged out successfully' },
    });
  } catch (err) {
    next(err);
  }
}
