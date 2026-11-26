import { Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { UserRole } from '@prisma/client';

function generatePassCode(): string {
  const num = Math.floor(1000 + Math.random() * 9000);
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const suffix = chars[Math.floor(Math.random() * chars.length)] + chars[Math.floor(Math.random() * chars.length)];
  return `KA-PASS-${num}-${suffix}`;
}

export async function getPermanentPass(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const userId = req.user.userId;
    let user = await prisma.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        firstName: true,
        lastName: true,
        email: true,
        phone: true,
        permanentPassCode: true,
        walletBalance: true,
      },
    });

    if (!user) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'User not found' } });
      return;
    }

    if (!user.permanentPassCode) {
      user = await prisma.user.update({
        where: { id: userId },
        data: { permanentPassCode: generatePassCode() },
        select: {
          id: true,
          firstName: true,
          lastName: true,
          email: true,
          phone: true,
          permanentPassCode: true,
          walletBalance: true,
        },
      });
    }

    res.json({
      success: true,
      data: {
        passCode: user.permanentPassCode,
        fullName: `${user.firstName} ${user.lastName}`,
        email: user.email,
        phone: user.phone || 'Not Set',
        walletBalance: user.walletBalance ?? 500,
        qrPayload: `SMARTBUS-PASS:${user.permanentPassCode}:${user.firstName} ${user.lastName}`,
      },
    });
  } catch (error) {
    console.error('getPermanentPass error:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to load permanent pass' } });
  }
}

export async function regeneratePermanentPass(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const userId = req.user.userId;
    const newCode = generatePassCode();

    const user = await prisma.user.update({
      where: { id: userId },
      data: { permanentPassCode: newCode },
      select: {
        id: true,
        firstName: true,
        lastName: true,
        email: true,
        phone: true,
        permanentPassCode: true,
        walletBalance: true,
      },
    });

    res.json({
      success: true,
      data: {
        passCode: user.permanentPassCode,
        fullName: `${user.firstName} ${user.lastName}`,
        email: user.email,
        phone: user.phone || 'Not Set',
        walletBalance: user.walletBalance ?? 500,
        qrPayload: `SMARTBUS-PASS:${user.permanentPassCode}:${user.firstName} ${user.lastName}`,
      },
      message: 'Permanent Transit Pass QR updated!',
    });
  } catch (error) {
    console.error('regeneratePermanentPass error:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to regenerate pass' } });
  }
}

export async function topUpWallet(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const userId = req.user.userId;
    const amount = Number(req.body?.amount || 0);
    if (!amount || amount <= 0 || amount > 10000) {
      res.status(400).json({ success: false, error: { code: 'INVALID_AMOUNT', message: 'Enter an amount between ₹1 and ₹10,000' } });
      return;
    }

    const user = await prisma.user.update({
      where: { id: userId },
      data: { walletBalance: { increment: amount } },
      select: { walletBalance: true },
    });

    res.json({
      success: true,
      data: { walletBalance: user.walletBalance },
      message: `₹${amount} added to Tap & Pay Wallet!`,
    });
  } catch (error) {
    console.error('topUpWallet error:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to top up wallet' } });
  }
}

export async function getStaffDetailsAndSalary(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const userId = req.user.userId;
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        firstName: true,
        lastName: true,
        email: true,
        phone: true,
        role: true,
        createdAt: true,
      },
    });

    if (!user) {
      res.status(404).json({ success: false, error: { code: 'NOT_FOUND', message: 'Staff member not found' } });
      return;
    }

    const isDriver = user.role === UserRole.DRIVER;
    const baseSalary = isDriver ? 18500 : 14500;
    const dailyBhatta = isDriver ? 250 : 200;
    const dutyDays = 26;
    const totalBhatta = dailyBhatta * dutyDays;
    const tripIncentive = isDriver ? 2400 : 1850;
    const deductions = 500;
    const netPayable = baseSalary + totalBhatta + tripIncentive - deductions;

    res.json({
      success: true,
      data: {
        profile: {
          fullName: `${user.firstName} ${user.lastName}`,
          email: user.email,
          phone: user.phone || '+91 98450 11223',
          role: user.role,
          licenseNo: isDriver ? 'KA-20-2018-0049102 (HMV)' : 'COND-KA20-2021-409',
          badgeNo: isDriver ? 'BDG-KA20-104' : 'BDG-KA20-218',
          assignedBus: 'KA-20-111 (KA-20-F-1234)',
          assignedRoute: 'Udupi – Katpadi – Shirva (KA-20-UKS)',
          shiftTiming: '06:30 AM – 06:30 PM',
          rating: 4.8,
          bloodGroup: isDriver ? 'O+' : 'B+',
          emergencyContact: '+91 94481 77210',
          joinedDate: user.createdAt.toISOString().split('T')[0],
        },
        salary: {
          month: 'November 2026',
          baseSalary,
          dailyBhatta,
          dutyDays,
          totalBhatta,
          tripIncentive,
          deductions,
          netPayable,
          status: 'PAID',
          lastPaidDate: '2026-11-05',
          paymentMethod: 'UPI / Bank Direct',
          history: [
            { month: 'November 2026', netAmount: netPayable, status: 'PAID', paidOn: '2026-11-05' },
            { month: 'October 2026', netAmount: netPayable - 400, status: 'PAID', paidOn: '2026-10-05' },
            { month: 'September 2026', netAmount: netPayable - 900, status: 'PAID', paidOn: '2026-09-05' },
          ],
        },
      },
    });
  } catch (error) {
    console.error('getStaffDetailsAndSalary error:', error);
    res.status(500).json({ success: false, error: { code: 'SERVER_ERROR', message: 'Failed to load staff details' } });
  }
}
