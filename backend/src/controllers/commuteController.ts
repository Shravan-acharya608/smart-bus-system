import { Request, Response } from 'express';
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

export const getFrequentCommutes = async (req: Request, res: Response) => {
  try {
    const userId = (req as any).user?.userId;
    if (!userId) {
      return res.status(401).json({ success: false, message: 'Unauthorized' });
    }

    const commutes = await prisma.frequentRoute.findMany({
      where: { passengerId: userId },
      include: {
        boardingStop: true,
        destinationStop: true,
      },
      orderBy: [{ isPinned: 'desc' }, { tripCount: 'desc' }, { lastUsedAt: 'desc' }],
      take: 10,
    });

    return res.json({ success: true, data: commutes });
  } catch (err: any) {
    console.error('[CommuteController] getFrequentCommutes error:', err);
    return res.status(500).json({ success: false, message: 'Failed to fetch frequent commutes' });
  }
};

export const pinCommute = async (req: Request, res: Response) => {
  try {
    const userId = (req as any).user?.userId;
    const { id } = req.params;

    const existing = await prisma.frequentRoute.findFirst({
      where: { id, passengerId: userId },
    });

    if (!existing) {
      return res.status(404).json({ success: false, message: 'Commute shortcut not found' });
    }

    const updated = await prisma.frequentRoute.update({
      where: { id },
      data: { isPinned: !existing.isPinned },
    });

    return res.json({ success: true, data: updated });
  } catch (err: any) {
    console.error('[CommuteController] pinCommute error:', err);
    return res.status(500).json({ success: false, message: 'Failed to update pin status' });
  }
};

export const updateCommuteLabel = async (req: Request, res: Response) => {
  try {
    const userId = (req as any).user?.userId;
    const { id } = req.params;
    const { label } = req.body;

    const existing = await prisma.frequentRoute.findFirst({
      where: { id, passengerId: userId },
    });

    if (!existing) {
      return res.status(404).json({ success: false, message: 'Commute shortcut not found' });
    }

    const updated = await prisma.frequentRoute.update({
      where: { id },
      data: { customLabel: label },
    });

    return res.json({ success: true, data: updated });
  } catch (err: any) {
    console.error('[CommuteController] updateCommuteLabel error:', err);
    return res.status(500).json({ success: false, message: 'Failed to update label' });
  }
};
