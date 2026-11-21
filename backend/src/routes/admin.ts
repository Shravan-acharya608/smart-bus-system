import { Router } from 'express';
import { authenticateToken, requireRole } from '../middleware/auth';
import { UserRole } from '@prisma/client';
import {
  getDashboardStats,
  getReports,
  getStaff,
  createStaff,
  toggleStaffStatus,
} from '../controllers/adminController';

const router = Router();

router.use(authenticateToken);
router.use(requireRole(UserRole.ADMIN, UserRole.BUS_OWNER));

// Dashboard statistics
router.get('/dashboard', (req, res) => getDashboardStats(req as any, res));

// Reports and analytics
router.get('/reports', (req, res) => getReports(req as any, res));

// Staff management
router.get('/staff', (req, res) => getStaff(req as any, res));
router.post('/staff', (req, res) => createStaff(req as any, res));
router.patch('/staff/:id/status', (req, res) => toggleStaffStatus(req as any, res));

export default router;
