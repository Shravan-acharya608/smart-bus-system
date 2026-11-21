import { Router } from 'express';
import { authenticateToken, requireRole } from '../middleware/auth';
import { UserRole } from '@prisma/client';
import {
  getBuses,
  getBusById,
  createBus,
  updateBus,
  deleteBus,
} from '../controllers/busController';

const router = Router();

router.use(authenticateToken);

// All authenticated users can view buses
router.get('/', (req, res) => getBuses(req as any, res));
router.get('/:id', (req, res) => getBusById(req as any, res));

// Only ADMIN and BUS_OWNER can manage buses
router.post(
  '/',
  requireRole(UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => createBus(req as any, res)
);
router.put(
  '/:id',
  requireRole(UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => updateBus(req as any, res)
);
router.delete(
  '/:id',
  requireRole(UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => deleteBus(req as any, res)
);

export default router;
