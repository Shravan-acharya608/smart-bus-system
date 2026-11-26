import { Router } from 'express';
import {
  applyForJob,
  listJobApplications,
  updateJobApplicationStatus,
  hireJobApplicant,
} from '../controllers/jobController';
import { authenticateToken, requireRole } from '../middleware/auth';
import { UserRole } from '@prisma/client';

const router = Router();

// Public driver/conductor job application endpoint (used by Android app & public web signup)
router.post('/apply', applyForJob);

// Admin / Bus Owner endpoints
router.get(
  '/',
  authenticateToken,
  requireRole(UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => listJobApplications(req as any, res)
);

router.patch(
  '/:id/status',
  authenticateToken,
  requireRole(UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => updateJobApplicationStatus(req as any, res)
);

router.post(
  '/:id/hire',
  authenticateToken,
  requireRole(UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => hireJobApplicant(req as any, res)
);

export default router;
