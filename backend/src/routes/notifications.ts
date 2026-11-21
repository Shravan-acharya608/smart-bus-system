import { Router } from 'express';
import { authenticateToken, requireRole } from '../middleware/auth';
import { UserRole } from '@prisma/client';
import {
  getNotifications,
  markAsRead,
  createAnnouncement,
} from '../controllers/notificationController';

const router = Router();

router.use(authenticateToken);

// All users view their notifications
router.get('/', (req, res) => getNotifications(req as any, res));
router.patch('/:id/read', (req, res) => markAsRead(req as any, res));

// Admin / Owner create announcements
router.post(
  '/announcement',
  requireRole(UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => createAnnouncement(req as any, res)
);

export default router;
