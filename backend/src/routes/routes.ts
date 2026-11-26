import { Router } from 'express';
import { authenticateToken, requireRole } from '../middleware/auth';
import { UserRole } from '@prisma/client';
import {
  getRoutes,
  getRouteById,
  createRoute,
  getStops,
  createStop,
  searchBuses,
  getUdupiSchedules,
} from '../controllers/routeController';

const router = Router();

// Public Udupi Bus Stand 139-schedule timetable with stop coordinates
router.get('/udupi-schedules', (req, res) => getUdupiSchedules(req as any, res));

router.use(authenticateToken);

// Bus search by stops
router.get('/search-buses', (req, res) => searchBuses(req as any, res));

// Stops management
router.get('/stops', (req, res) => getStops(req as any, res));
router.post('/stops', requireRole(UserRole.ADMIN, UserRole.BUS_OWNER), (req, res) =>
  createStop(req as any, res)
);

// Routes
router.get('/', (req, res) => getRoutes(req as any, res));
router.get('/:id', (req, res) => getRouteById(req as any, res));
router.post('/', requireRole(UserRole.ADMIN, UserRole.BUS_OWNER), (req, res) =>
  createRoute(req as any, res)
);

export default router;
