import { Router } from 'express';
import { authenticateToken } from '../middleware/auth';
import {
  getFrequentCommutes,
  pinCommute,
  updateCommuteLabel,
} from '../controllers/commuteController';

const router = Router();

router.use(authenticateToken);

router.get('/frequent', (req, res) => getFrequentCommutes(req as any, res));
router.patch('/:id/pin', (req, res) => pinCommute(req as any, res));
router.patch('/:id/label', (req, res) => updateCommuteLabel(req as any, res));

export default router;
