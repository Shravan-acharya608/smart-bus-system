import { Router } from 'express';
import { authenticateToken } from '../middleware/auth';
import {
  createPayment,
  verifyPayment,
  paymentWebhook,
} from '../controllers/paymentController';

const router = Router();

// Webhook endpoint (must not require Bearer token; authenticated via webhook signature)
router.post('/webhook', paymentWebhook);

// Authenticated endpoints
router.use(authenticateToken);
router.post('/create', (req, res) => createPayment(req as any, res));
router.post('/verify', (req, res) => verifyPayment(req as any, res));

export default router;
