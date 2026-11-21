import jwt from 'jsonwebtoken';
import { UserRole } from '@prisma/client';

export interface TokenPayload {
  userId: string;
  role: UserRole;
  organizationId: string | null;
}

/**
 * Generate a short-lived JWT access token.
 */
export function generateAccessToken(
  userId: string,
  role: UserRole,
  organizationId: string | null,
): string {
  const secret = process.env.JWT_ACCESS_SECRET;
  if (!secret) throw new Error('JWT_ACCESS_SECRET is not configured');

  const payload: TokenPayload = { userId, role, organizationId };
  return jwt.sign(payload, secret, {
    expiresIn: (process.env.JWT_ACCESS_EXPIRES_IN as jwt.SignOptions['expiresIn']) || '15m',
  });
}

/**
 * Generate a random UUID refresh token (stored in DB).
 */
export function generateRefreshToken(): string {
  const { v4: uuidv4 } = require('uuid');
  return uuidv4() as string;
}

/**
 * Verify and decode a JWT access token.
 * Throws JsonWebTokenError / TokenExpiredError on invalid / expired tokens.
 */
export function verifyAccessToken(token: string): TokenPayload {
  const secret = process.env.JWT_ACCESS_SECRET;
  if (!secret) throw new Error('JWT_ACCESS_SECRET is not configured');

  return jwt.verify(token, secret) as TokenPayload;
}
