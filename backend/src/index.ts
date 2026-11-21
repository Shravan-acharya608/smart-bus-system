import 'dotenv/config';
import { httpServer } from './app';
import { logger } from './lib/logger';
import prisma from './lib/prisma';

const PORT = parseInt(process.env.PORT || '3000', 10);

async function main(): Promise<void> {
  // Verify DB connection on startup
  await prisma.$connect();
  logger.info('Database connection established');

  httpServer.listen(PORT, '0.0.0.0', () => {
    logger.info(`
╔══════════════════════════════════════════════════════╗
║       Smart Bus Management System - Backend          ║
╠══════════════════════════════════════════════════════╣
║  Status  : Running                                   ║
║  Port    : ${String(PORT).padEnd(43)}║
║  Host    : 0.0.0.0 (All Network Interfaces)          ║
║  Env     : ${(process.env.NODE_ENV || 'development').padEnd(43)}║
║  API     : http://localhost:${PORT}/api/v1${' '.repeat(Math.max(0, 22 - String(PORT).length))}║
╚══════════════════════════════════════════════════════╝
    `);
  });
}

// ── Graceful shutdown ─────────────────────────────────────────────────────────
async function shutdown(signal: string): Promise<void> {
  logger.info(`Received ${signal}. Shutting down gracefully...`);

  httpServer.close(async () => {
    logger.info('HTTP server closed');

    try {
      await prisma.$disconnect();
      logger.info('Database disconnected');
      process.exit(0);
    } catch (err) {
      logger.error('Error during DB disconnect', err);
      process.exit(1);
    }
  });

  // Force exit after 10 seconds if graceful shutdown stalls
  setTimeout(() => {
    logger.error('Forced shutdown after timeout');
    process.exit(1);
  }, 10_000).unref();
}

process.on('SIGTERM', () => void shutdown('SIGTERM'));
process.on('SIGINT', () => void shutdown('SIGINT'));

process.on('unhandledRejection', (reason) => {
  logger.error('Unhandled Promise Rejection:', reason);
});

process.on('uncaughtException', (err) => {
  logger.error('Uncaught Exception:', err);
  process.exit(1);
});

main().catch((err) => {
  logger.error('Failed to start server:', err);
  process.exit(1);
});
