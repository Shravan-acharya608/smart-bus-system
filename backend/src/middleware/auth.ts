import { Request, Response, NextFunction } from 'express';
import { UserRole } from '@prisma/client';
import { verifyAccessToken, TokenPayload } from '../utils/jwt';

/**
 * Extended Express Request carrying the authenticated user payload.
 */
export interface AuthenticatedRequest extends Request {
  user: TokenPayload;
}

/**
 * Middleware – verifies the Bearer JWT access token.
 * Attaches `req.user = { userId, role, organizationId }` on success.
 */
export function authenticateToken(
  req: Request,
  res: Response,
  next: NextFunction,
): void {
  const authHeader = req.headers['authorization'];
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    res.status(401).json({
      success: false,
      error: { code: 'MISSING_TOKEN', message: 'Authorization token is required' },
    });
    return;
  }

  const token = authHeader.slice(7);

  try {
    const payload = verifyAccessToken(token);
    (req as AuthenticatedRequest).user = payload;
    next();
  } catch (err: unknown) {
    const isExpired =
      err instanceof Error && err.name === 'TokenExpiredError';

    res.status(401).json({
      success: false,
      error: {
        code: isExpired ? 'TOKEN_EXPIRED' : 'INVALID_TOKEN',
        message: isExpired ? 'Access token has expired' : 'Invalid access token',
      },
    });
  }
}

/**
 * Middleware factory – restricts access to the provided roles.
 * Must be used AFTER authenticateToken.
 */
export function requireRole(...roles: UserRole[]) {
  return (req: Request, res: Response, next: NextFunction): void => {
    const user = (req as AuthenticatedRequest).user;

    if (!user) {
      res.status(401).json({
        success: false,
        error: { code: 'UNAUTHENTICATED', message: 'Authentication required' },
      });
      return;
    }

    if (!roles.includes(user.role as UserRole)) {
      res.status(403).json({
        success: false,
        error: {
          code: 'FORBIDDEN',
          message: `Access denied. Required roles: ${roles.join(', ')}`,
        },
      });
      return;
    }

    next();
  };
}
