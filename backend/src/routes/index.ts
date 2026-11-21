import { Router } from 'express';
import authRouter from './auth';
import busRouter from './buses';
import routeRouter from './routes';
import tripRouter from './trips';
import ticketRouter from './tickets';
import paymentRouter from './payments';
import adminRouter from './admin';
import eventsRouter from './events';
import journeyRouter from './journey';
import notificationRouter from './notifications';
import commuteRouter from './commutes';
import incidentRouter from './incidents';

const router = Router();

router.get('/', (_req, res) => {
  res.json({
    success: true,
    data: {
      service: 'SmartBus Transit API',
      status: 'ONLINE',
      version: '1.0.0',
      database: 'Neon PostgreSQL (Connected)',
      endpoints: {
        health: '/api/v1/health',
        auth: '/api/v1/auth/login',
        routes: '/api/v1/routes',
        trips: '/api/v1/trips',
        stops: '/api/v1/routes/stops',
        searchBuses: '/api/v1/routes/search-buses',
        incidents: '/api/v1/incidents',
        commutes: '/api/v1/commutes/frequent'
      }
    }
  });
});

router.get('/health', (_req, res) => {
  res.json({
    success: true,
    data: {
      status: 'ok',
      service: 'Smart Bus API Server',
      version: '1.0.0',
      timestamp: new Date().toISOString(),
    },
  });
});

router.use('/auth', authRouter);
router.use('/buses', busRouter);
router.use('/routes', routeRouter);
router.use('/trips', tripRouter);
router.use('/tickets', ticketRouter);
router.use('/payments', paymentRouter);
router.use('/admin', adminRouter);
router.use('/events', eventsRouter);
router.use('/journey-sharing', journeyRouter);
router.use('/notifications', notificationRouter);
router.use('/commutes', commuteRouter);
router.use('/incidents', incidentRouter);

export default router;
