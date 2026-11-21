import { Router } from 'express';
import { authenticateToken, requireRole } from '../middleware/auth';
import { UserRole } from '@prisma/client';
import {
  getTrips,
  getTripById,
  createTrip,
  startTrip,
  endTrip,
  postLocationUpdate,
  getTripLocation,
  getPassengerCount,
} from '../controllers/tripController';

const router = Router();

router.use(authenticateToken);

// Read trips
router.get('/', (req, res) => getTrips(req as any, res));
router.get('/:id', (req, res) => getTripById(req as any, res));
router.get('/:id/location', (req, res) => getTripLocation(req as any, res));
router.get('/:id/passenger-count', (req, res) => getPassengerCount(req as any, res));

// Admin / Owner create trip
router.post('/', requireRole(UserRole.ADMIN, UserRole.BUS_OWNER), (req, res) =>
  createTrip(req as any, res)
);

// Driver trip actions
router.post(
  '/:id/start',
  requireRole(UserRole.DRIVER, UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => startTrip(req as any, res)
);
router.post(
  '/:id/end',
  requireRole(UserRole.DRIVER, UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => endTrip(req as any, res)
);
router.post(
  '/:id/location',
  requireRole(UserRole.DRIVER, UserRole.ADMIN),
  (req, res) => postLocationUpdate(req as any, res)
);

export default router;
