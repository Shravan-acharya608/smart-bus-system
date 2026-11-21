import { Router } from 'express';
import { authenticateToken } from '../middleware/auth';
import {
  reportIncident,
  resolveIncident,
  getIncidents,
} from '../controllers/incidentController';

const router = Router();

router.use(authenticateToken);

router.get('/', (req, res) => getIncidents(req as any, res));
router.post('/trips/:tripId/report', (req, res) => reportIncident(req as any, res));
router.patch('/:id/resolve', (req, res) => resolveIncident(req as any, res));

export default router;
