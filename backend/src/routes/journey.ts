import { Router } from 'express';
import { authenticateToken } from '../middleware/auth';
import {
  createJourneyShare,
  getSharedJourney,
  revokeJourneyShare,
} from '../controllers/journeyController';

const router = Router();

// Public link access with unique secure token
router.get('/shared/:token', getSharedJourney);

// Authenticated passenger actions
router.use(authenticateToken);
router.post('/', (req, res) => createJourneyShare(req as any, res));
router.delete('/:id', (req, res) => revokeJourneyShare(req as any, res));

export default router;
