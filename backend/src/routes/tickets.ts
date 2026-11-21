import { Router } from 'express';
import { authenticateToken, requireRole } from '../middleware/auth';
import { UserRole } from '@prisma/client';
import {
  createTicket,
  getMyTickets,
  getTicketById,
  verifyTicket,
  recordBoarding,
  recordExit,
  issueOnboardTicket,
  holdTicket,
} from '../controllers/ticketController';

const router = Router();

router.use(authenticateToken);

// Passenger
router.post('/hold', (req, res) => holdTicket(req as any, res));
router.post('/', (req, res) => createTicket(req as any, res));
router.get('/my-tickets', (req, res) => getMyTickets(req as any, res));
router.get('/:id', (req, res) => getTicketById(req as any, res));

// Conductor / Staff ticket operations
router.post(
  '/verify',
  requireRole(UserRole.CONDUCTOR, UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => verifyTicket(req as any, res)
);
router.post(
  '/:id/board',
  requireRole(UserRole.CONDUCTOR, UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => recordBoarding(req as any, res)
);
router.post(
  '/:id/exit',
  requireRole(UserRole.CONDUCTOR, UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => recordExit(req as any, res)
);
router.post(
  '/onboard',
  requireRole(UserRole.CONDUCTOR, UserRole.ADMIN, UserRole.BUS_OWNER),
  (req, res) => issueOnboardTicket(req as any, res)
);

export default router;
