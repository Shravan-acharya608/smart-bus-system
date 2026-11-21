import { Request, Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { z } from 'zod';
import { PaymentStatus, TicketStatus } from '@prisma/client';
import crypto from 'crypto';
import { logger } from '../lib/logger';

const createPaymentSchema = z.object({
  ticketId: z.string().uuid(),
  idempotencyKey: z.string().optional(),
});

const verifyPaymentSchema = z.object({
  ticketId: z.string().uuid(),
  providerOrderId: z.string(),
  providerPaymentId: z.string(),
  providerSignature: z.string().optional(),
});

export async function createPayment(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const parseResult = createPaymentSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid payment data', details: parseResult.error.format() },
      });
      return;
    }

    const { ticketId, idempotencyKey } = parseResult.data;

    const ticket = await prisma.ticket.findUnique({
      where: { id: ticketId },
      include: { payment: true },
    });

    if (!ticket) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Ticket not found' } });
      return;
    }

    if (ticket.status === TicketStatus.PAID || ticket.status === TicketStatus.USED) {
      res.status(400).json({
        success: false,
        error: { code: 'ALREADY_PAID', message: 'Ticket has already been paid for' },
      });
      return;
    }

    // Idempotency check
    if (idempotencyKey && ticket.payment?.idempotencyKey === idempotencyKey && ticket.payment?.providerOrderId) {
      res.json({
        success: true,
        data: {
          orderId: ticket.payment.providerOrderId,
          amount: ticket.fare,
          currency: 'INR',
          isSandbox: ticket.payment.isSandbox,
        },
      });
      return;
    }

    const isSandbox = process.env.PAYMENT_SANDBOX !== 'false';
    const razorpayKeyId = process.env.RAZORPAY_KEY_ID || 'rzp_test_mock';
    const razorpayKeySecret = process.env.RAZORPAY_KEY_SECRET;

    let orderId = `order_mock_${Date.now()}_${Math.floor(Math.random() * 1000)}`;

    // If real Razorpay credentials provided and not in sandbox mock
    if (!isSandbox && razorpayKeyId && razorpayKeySecret && !razorpayKeyId.startsWith('rzp_test_mock')) {
      try {
        const Razorpay = require('razorpay');
        const instance = new Razorpay({ key_id: razorpayKeyId, key_secret: razorpayKeySecret });
        const order = await instance.orders.create({
          amount: Math.round(ticket.fare * 100), // in paise
          currency: 'INR',
          receipt: ticket.ticketCode,
        });
        orderId = order.id;
      } catch (err: any) {
        logger.error('Razorpay order creation failed:', err);
        res.status(502).json({
          success: false,
          error: { code: 'GATEWAY_ERROR', message: 'Payment gateway communication failed' },
        });
        return;
      }
    }

    const payment = await prisma.payment.upsert({
      where: { ticketId },
      create: {
        ticketId,
        amount: ticket.fare,
        currency: 'INR',
        status: PaymentStatus.PENDING,
        providerOrderId: orderId,
        idempotencyKey: idempotencyKey || crypto.randomUUID(),
        isSandbox,
      },
      update: {
        providerOrderId: orderId,
        status: PaymentStatus.PENDING,
      },
    });

    res.json({
      success: true,
      data: {
        orderId,
        amount: ticket.fare,
        currency: 'INR',
        keyId: razorpayKeyId,
        isSandbox,
        message: isSandbox ? 'Development Sandbox Mode: simulated gateway' : undefined,
      },
    });
  } catch (error) {
    logger.error('Error creating payment:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to initiate payment' } });
  }
}

export async function verifyPayment(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const parseResult = verifyPaymentSchema.safeParse(req.body);
    if (!parseResult.success) {
      res.status(400).json({
        success: false,
        error: { code: 'VALIDATION_ERROR', message: 'Invalid verification data', details: parseResult.error.format() },
      });
      return;
    }

    const { ticketId, providerOrderId, providerPaymentId, providerSignature } = parseResult.data;

    const payment = await prisma.payment.findUnique({
      where: { ticketId },
      include: { ticket: true },
    });

    if (!payment) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Payment record not found' } });
      return;
    }

    if (payment.status === PaymentStatus.SUCCESS) {
      res.json({ success: true, message: 'Payment already verified', data: payment });
      return;
    }

    const isSandbox = payment.isSandbox;
    const razorpayKeySecret = process.env.RAZORPAY_KEY_SECRET;

    // In production mode, verify signature strictly
    if (!isSandbox && razorpayKeySecret) {
      if (!providerSignature) {
        res.status(400).json({
          success: false,
          error: { code: 'SIGNATURE_REQUIRED', message: 'Payment signature required for verification' },
        });
        return;
      }
      const generatedSignature = crypto
        .createHmac('sha256', razorpayKeySecret)
        .update(`${providerOrderId}|${providerPaymentId}`)
        .digest('hex');

      if (generatedSignature !== providerSignature) {
        await prisma.payment.update({
          where: { id: payment.id },
          data: { status: PaymentStatus.FAILED },
        });
        res.status(400).json({
          success: false,
          error: { code: 'INVALID_SIGNATURE', message: 'Payment verification failed: signature mismatch' },
        });
        return;
      }
    }

    // Atomic update of payment and ticket status
    const result = await prisma.$transaction(async (tx) => {
      const updatedPayment = await tx.payment.update({
        where: { id: payment.id },
        data: {
          status: PaymentStatus.SUCCESS,
          providerPaymentId,
          providerSignature,
        },
      });

      const updatedTicket = await tx.ticket.update({
        where: { id: ticketId },
        data: { status: TicketStatus.PAID },
      });

      return { payment: updatedPayment, ticket: updatedTicket };
    });

    res.json({
      success: true,
      data: result,
      message: 'Payment verified and ticket confirmed successfully',
    });
  } catch (error) {
    logger.error('Error verifying payment:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to verify payment' } });
  }
}

export async function paymentWebhook(req: Request, res: Response): Promise<void> {
  try {
    const webhookSecret = process.env.RAZORPAY_WEBHOOK_SECRET;
    const signature = req.headers['x-razorpay-signature'] as string;

    if (webhookSecret && signature) {
      const shasum = crypto.createHmac('sha256', webhookSecret);
      shasum.update(JSON.stringify(req.body));
      const digest = shasum.digest('hex');

      if (digest !== signature) {
        res.status(400).json({ success: false, message: 'Invalid webhook signature' });
        return;
      }
    }

    const event = req.body.event;
    if (event === 'payment.captured') {
      const payload = req.body.payload.payment.entity;
      const orderId = payload.order_id;
      const paymentId = payload.id;

      const payment = await prisma.payment.findUnique({
        where: { providerOrderId: orderId },
      });

      if (payment && payment.status !== PaymentStatus.SUCCESS) {
        await prisma.$transaction([
          prisma.payment.update({
            where: { id: payment.id },
            data: { status: PaymentStatus.SUCCESS, providerPaymentId: paymentId },
          }),
          prisma.ticket.update({
            where: { id: payment.ticketId },
            data: { status: TicketStatus.PAID },
          }),
        ]);
      }
    }

    res.json({ status: 'ok' });
  } catch (error) {
    logger.error('Webhook processing error:', error);
    res.status(500).json({ status: 'error' });
  }
}
