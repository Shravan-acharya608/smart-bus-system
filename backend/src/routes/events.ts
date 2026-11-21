import { Router } from 'express';
import { authenticateToken, requireRole } from '../middleware/auth';
import { UserRole } from '@prisma/client';
import { syncOfflineEvents } from '../controllers/eventController';

const router = Router();

router.use(authenticateToken);

// Conductor / Driver offline synchronization endpoint
router.post(
  '/sync',
  requireRole(UserRole.CONDUCTOR, UserRole.DRIVER, UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => syncOfflineEvents(req as any, res)
);

export default router;
