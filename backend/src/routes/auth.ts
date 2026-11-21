import { Router } from 'express';
import {
  register,
  login,
  refreshToken,
  logout,
} from '../controllers/authController';

const router = Router();

/**
 * POST /api/v1/auth/register
 * Public – create a new PASSENGER account.
 */
router.post('/register', register);

/**
 * POST /api/v1/auth/login
 * Public – authenticate and receive tokens.
 */
router.post('/login', login);

/**
 * POST /api/v1/auth/refresh
 * Public – rotate refresh token and get a new access token.
 */
router.post('/refresh', refreshToken);

/**
 * POST /api/v1/auth/logout
 * Public – revoke refresh token (idempotent).
 */
router.post('/logout', logout);

export default router;
