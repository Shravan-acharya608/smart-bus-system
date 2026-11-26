import { Request, Response } from 'express';
import { prisma } from '../lib/prisma';
import { AuthenticatedRequest } from '../middleware/auth';
import { z } from 'zod';
import { UserRole, JobApplicationStatus } from '@prisma/client';
import { hashPassword } from '../utils/hash';

const applyJobSchema = z.object({
  fullName: z.string().min(2),
  role: z.enum(['DRIVER', 'CONDUCTOR']).default('DRIVER'),
  phone: z.string().min(8),
  email: z.string().email(),
  licenseNumber: z.string().min(4),
  badgeNumber: z.string().optional(),
  experienceYears: z.coerce.number().int().min(0).max(50).default(1),
  preferredRoutes: z.string().optional().default('Udupi – Katpadi – Shirva'),
  expectedSalary: z.coerce.number().min(5000).default(22000),
});

export async function applyForJob(req: Request, res: Response): Promise<void> {
  try {
    const parsed = applyJobSchema.safeParse(req.body);
    if (!parsed.success) {
      res.status(400).json({
        success: false,
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Please fill all required job application fields accurately.',
          details: parsed.error.format(),
        },
      });
      return;
    }

    const application = await prisma.jobApplication.create({
      data: {
        fullName: parsed.data.fullName.trim(),
        role: parsed.data.role as UserRole,
        phone: parsed.data.phone.trim(),
        email: parsed.data.email.trim().toLowerCase(),
        licenseNumber: parsed.data.licenseNumber.trim(),
        badgeNumber: parsed.data.badgeNumber?.trim() || null,
        experienceYears: parsed.data.experienceYears,
        preferredRoutes: parsed.data.preferredRoutes,
        expectedSalary: parsed.data.expectedSalary,
        status: JobApplicationStatus.PENDING_REVIEW,
      },
    });

    res.status(201).json({
      success: true,
      data: { application },
      message: 'Your job application has been submitted to the Bus Owner Portal!',
    });
  } catch (error) {
    console.error('Job application error:', error);
    res.status(500).json({
      success: false,
      error: { code: 'SERVER_ERROR', message: 'Failed to submit job application' },
    });
  }
}

export async function listJobApplications(_req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const applications = await prisma.jobApplication.findMany({
      orderBy: { createdAt: 'desc' },
    });
    res.json({ success: true, data: { applications } });
  } catch (error) {
    console.error('List job applications error:', error);
    res.status(500).json({
      success: false,
      error: { code: 'SERVER_ERROR', message: 'Failed to load job applications' },
    });
  }
}

export async function updateJobApplicationStatus(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const { status } = req.body as { status?: JobApplicationStatus };

    if (!status || !Object.values(JobApplicationStatus).includes(status)) {
      res.status(400).json({
        success: false,
        error: { code: 'INVALID_STATUS', message: 'Invalid application status' },
      });
      return;
    }

    const updated = await prisma.jobApplication.update({
      where: { id },
      data: { status },
    });

    res.json({ success: true, data: { application: updated } });
  } catch (error) {
    console.error('Update job status error:', error);
    res.status(500).json({
      success: false,
      error: { code: 'SERVER_ERROR', message: 'Failed to update job application' },
    });
  }
}

export async function hireJobApplicant(req: AuthenticatedRequest, res: Response): Promise<void> {
  try {
    const { id } = req.params;
    const applicant = await prisma.jobApplication.findUnique({ where: { id } });

    if (!applicant) {
      res.status(404).json({
        success: false,
        error: { code: 'NOT_FOUND', message: 'Applicant not found' },
      });
      return;
    }

    const parts = applicant.fullName.trim().split(/\s+/);
    const firstName = parts[0] || applicant.fullName;
    const lastName = parts.slice(1).join(' ') || 'Staff';

    const result = await prisma.$transaction(async (tx) => {
      // Check if user already exists by email
      let user = await tx.user.findUnique({ where: { email: applicant.email } });
      if (!user) {
        const passwordHash = await hashPassword('Staff@123456');
        user = await tx.user.create({
          data: {
            email: applicant.email,
            phone: applicant.phone,
            firstName,
            lastName,
            passwordHash,
            role: applicant.role,
            isActive: true,
            isVerified: true,
          },
        });
      }

      const updatedApp = await tx.jobApplication.update({
        where: { id },
        data: { status: JobApplicationStatus.HIRED },
      });

      return { user, application: updatedApp };
    });

    res.json({
      success: true,
      data: result,
      message: `${applicant.fullName} hired as ${applicant.role}! Default login password: Staff@123456`,
    });
  } catch (error) {
    console.error('Hire applicant error:', error);
    res.status(500).json({
      success: false,
      error: { code: 'SERVER_ERROR', message: 'Failed to hire applicant' },
    });
  }
}
