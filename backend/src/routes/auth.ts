import { Router } from 'express';
import {
  register,
  login,
  refreshToken,
  logout,
} from '../controllers/authController';
import {
  getPermanentPass,
  regeneratePermanentPass,
  topUpWallet,
  getStaffDetailsAndSalary,
} from '../controllers/profileExtrasController';
import { authenticateToken } from '../middleware/auth';

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

// Authenticated user pass, wallet & staff profile endpoints
router.get('/pass', authenticateToken, (req, res) => getPermanentPass(req as any, res));
router.post('/pass/regenerate', authenticateToken, (req, res) => regeneratePermanentPass(req as any, res));
router.post('/wallet/topup', authenticateToken, (req, res) => topUpWallet(req as any, res));
router.get('/staff-details', authenticateToken, (req, res) => getStaffDetailsAndSalary(req as any, res));

export default router;
