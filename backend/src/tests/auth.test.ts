import request from 'supertest';
import app from '../app';
import prisma from '../lib/prisma';
import { hashPassword } from '../utils/hash';
import { UserRole } from '@prisma/client';

// ── Mock Prisma ───────────────────────────────────────────────────────────────
jest.mock('../lib/prisma', () => ({
  __esModule: true,
  default: {
    user: {
      findFirst: jest.fn(),
      findUnique: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn(),
    },
    refreshToken: {
      create: jest.fn(),
      findUnique: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn(),
    },
    $transaction: jest.fn(),
  },
}));

const mockPrisma = prisma as jest.Mocked<typeof prisma>;

// Helper to reset all mocks between tests
beforeEach(() => {
  jest.clearAllMocks();
});

// ── POST /api/v1/auth/register ────────────────────────────────────────────────
describe('POST /api/v1/auth/register', () => {
  const validPayload = {
    email: 'newuser@example.com',
    password: 'SecurePass123!',
    firstName: 'New',
    lastName: 'User',
  };

  it('should register a new user and return tokens', async () => {
    // No existing user
    (mockPrisma.user.findFirst as jest.Mock).mockResolvedValueOnce(null);

    const createdUser = {
      id: 'user-uuid-1',
      email: validPayload.email,
      firstName: validPayload.firstName,
      lastName: validPayload.lastName,
      phone: null,
      role: UserRole.PASSENGER,
      isVerified: false,
      createdAt: new Date(),
    };

    (mockPrisma.user.create as jest.Mock).mockResolvedValueOnce(createdUser);
    (mockPrisma.refreshToken.create as jest.Mock).mockResolvedValueOnce({
      id: 'rt-uuid-1',
      token: 'refresh-token-value',
    });

    const res = await request(app)
      .post('/api/v1/auth/register')
      .send(validPayload);

    expect(res.status).toBe(201);
    expect(res.body.success).toBe(true);
    expect(res.body.data.user.email).toBe(validPayload.email);
    expect(res.body.data.user.role).toBe(UserRole.PASSENGER);
    expect(res.body.data.tokens.accessToken).toBeDefined();
    expect(res.body.data.tokens.refreshToken).toBeDefined();
    // Password must NOT be in the response
    expect(res.body.data.user.passwordHash).toBeUndefined();
    expect(res.body.data.user.password).toBeUndefined();
  });

  it('should return 409 when email already exists', async () => {
    (mockPrisma.user.findFirst as jest.Mock).mockResolvedValueOnce({
      id: 'existing-user-id',
      email: validPayload.email,
    });

    const res = await request(app)
      .post('/api/v1/auth/register')
      .send(validPayload);

    expect(res.status).toBe(409);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('CONFLICT');
  });

  it('should return 400 when password is too short', async () => {
    const res = await request(app)
      .post('/api/v1/auth/register')
      .send({ ...validPayload, password: 'short' });

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  it('should return 400 for invalid email format', async () => {
    const res = await request(app)
      .post('/api/v1/auth/register')
      .send({ ...validPayload, email: 'not-an-email' });

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  it('should return 400 when required fields are missing', async () => {
    const res = await request(app)
      .post('/api/v1/auth/register')
      .send({ email: 'a@b.com', password: 'SecurePass123!' });

    expect(res.status).toBe(400);
    expect(res.body.success).toBe(false);
  });
});

// ── POST /api/v1/auth/login ───────────────────────────────────────────────────
describe('POST /api/v1/auth/login', () => {
  const plainPassword = 'SecurePass123!';

  it('should login successfully and return tokens', async () => {
    const hash = await hashPassword(plainPassword);

    (mockPrisma.user.findUnique as jest.Mock).mockResolvedValueOnce({
      id: 'user-uuid-1',
      email: 'user@example.com',
      passwordHash: hash,
      firstName: 'Test',
      lastName: 'User',
      role: UserRole.PASSENGER,
      isActive: true,
      isVerified: true,
      organizationId: null,
    });

    (mockPrisma.$transaction as jest.Mock).mockResolvedValueOnce([
      { id: 'rt-uuid', token: 'new-rt' },
      { id: 'user-uuid-1', lastLoginAt: new Date() },
    ]);

    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'user@example.com', password: plainPassword });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.tokens.accessToken).toBeDefined();
    expect(res.body.data.tokens.refreshToken).toBeDefined();
    expect(res.body.data.user.email).toBe('user@example.com');
  });

  it('should return 401 for wrong password', async () => {
    const hash = await hashPassword(plainPassword);

    (mockPrisma.user.findUnique as jest.Mock).mockResolvedValueOnce({
      id: 'user-uuid-1',
      email: 'user@example.com',
      passwordHash: hash,
      firstName: 'Test',
      lastName: 'User',
      role: UserRole.PASSENGER,
      isActive: true,
      isVerified: true,
      organizationId: null,
    });

    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'user@example.com', password: 'WrongPassword!' });

    expect(res.status).toBe(401);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('INVALID_CREDENTIALS');
  });

  it('should return 401 when user is not found', async () => {
    (mockPrisma.user.findUnique as jest.Mock).mockResolvedValueOnce(null);

    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'ghost@example.com', password: plainPassword });

    expect(res.status).toBe(401);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('INVALID_CREDENTIALS');
  });

  it('should return 403 for disabled account', async () => {
    const hash = await hashPassword(plainPassword);

    (mockPrisma.user.findUnique as jest.Mock).mockResolvedValueOnce({
      id: 'user-uuid-1',
      email: 'user@example.com',
      passwordHash: hash,
      firstName: 'Test',
      lastName: 'User',
      role: UserRole.PASSENGER,
      isActive: false,
      isVerified: true,
      organizationId: null,
    });

    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'user@example.com', password: plainPassword });

    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('ACCOUNT_DISABLED');
  });

  it('should return 403 on role mismatch', async () => {
    const hash = await hashPassword(plainPassword);

    (mockPrisma.user.findUnique as jest.Mock).mockResolvedValueOnce({
      id: 'user-uuid-1',
      email: 'user@example.com',
      passwordHash: hash,
      firstName: 'Test',
      lastName: 'User',
      role: UserRole.PASSENGER,
      isActive: true,
      isVerified: true,
      organizationId: null,
    });

    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'user@example.com', password: plainPassword, role: UserRole.DRIVER });

    expect(res.status).toBe(403);
    expect(res.body.error.code).toBe('ROLE_MISMATCH');
  });

  it('should return 400 for invalid email', async () => {
    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'bad-email', password: plainPassword });

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });
});

// ── POST /api/v1/auth/refresh ─────────────────────────────────────────────────
describe('POST /api/v1/auth/refresh', () => {
  const futureDate = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);

  it('should return new tokens for a valid refresh token', async () => {
    const storedToken = {
      id: 'rt-id-1',
      token: 'valid-refresh-token',
      userId: 'user-uuid-1',
      isRevoked: false,
      expiresAt: futureDate,
      user: {
        id: 'user-uuid-1',
        role: UserRole.PASSENGER,
        organizationId: null,
        isActive: true,
      },
    };

    (mockPrisma.refreshToken.findUnique as jest.Mock).mockResolvedValueOnce(storedToken);
    (mockPrisma.$transaction as jest.Mock).mockResolvedValueOnce([
      { id: 'rt-id-1', isRevoked: true },
      { id: 'rt-id-2', token: 'new-refresh-token' },
    ]);

    const res = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: 'valid-refresh-token' });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.tokens.accessToken).toBeDefined();
    expect(res.body.data.tokens.refreshToken).toBeDefined();
  });

  it('should return 401 for an expired refresh token', async () => {
    const pastDate = new Date(Date.now() - 1000);

    (mockPrisma.refreshToken.findUnique as jest.Mock).mockResolvedValueOnce({
      id: 'rt-id-1',
      token: 'expired-token',
      userId: 'user-uuid-1',
      isRevoked: false,
      expiresAt: pastDate,
      user: {
        id: 'user-uuid-1',
        role: UserRole.PASSENGER,
        organizationId: null,
        isActive: true,
      },
    });

    const res = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: 'expired-token' });

    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('TOKEN_EXPIRED');
  });

  it('should return 401 for a revoked refresh token', async () => {
    (mockPrisma.refreshToken.findUnique as jest.Mock).mockResolvedValueOnce({
      id: 'rt-id-1',
      token: 'revoked-token',
      userId: 'user-uuid-1',
      isRevoked: true,
      expiresAt: futureDate,
      user: {
        id: 'user-uuid-1',
        role: UserRole.PASSENGER,
        organizationId: null,
        isActive: true,
      },
    });

    const res = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: 'revoked-token' });

    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('TOKEN_REVOKED');
  });

  it('should return 401 when refresh token not found', async () => {
    (mockPrisma.refreshToken.findUnique as jest.Mock).mockResolvedValueOnce(null);

    const res = await request(app)
      .post('/api/v1/auth/refresh')
      .send({ refreshToken: 'non-existent-token' });

    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('INVALID_REFRESH_TOKEN');
  });

  it('should return 400 when refreshToken field is missing', async () => {
    const res = await request(app)
      .post('/api/v1/auth/refresh')
      .send({});

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });
});

// ── POST /api/v1/auth/logout ──────────────────────────────────────────────────
describe('POST /api/v1/auth/logout', () => {
  it('should successfully logout by revoking the refresh token', async () => {
    (mockPrisma.refreshToken.updateMany as jest.Mock).mockResolvedValueOnce({ count: 1 });

    const res = await request(app)
      .post('/api/v1/auth/logout')
      .send({ refreshToken: 'some-valid-refresh-token' });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.message).toBe('Logged out successfully');
    expect(mockPrisma.refreshToken.updateMany).toHaveBeenCalledWith({
      where: { token: 'some-valid-refresh-token', isRevoked: false },
      data: { isRevoked: true },
    });
  });

  it('should succeed (idempotent) even when token does not exist', async () => {
    (mockPrisma.refreshToken.updateMany as jest.Mock).mockResolvedValueOnce({ count: 0 });

    const res = await request(app)
      .post('/api/v1/auth/logout')
      .send({ refreshToken: 'already-revoked-token' });

    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
  });

  it('should return 400 when refreshToken is missing', async () => {
    const res = await request(app)
      .post('/api/v1/auth/logout')
      .send({});

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });
});

// ── Health check ──────────────────────────────────────────────────────────────
describe('GET /api/v1/health', () => {
  it('should return ok status', async () => {
    const res = await request(app).get('/api/v1/health');
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(res.body.data.status).toBe('ok');
  });
});

// ── 404 handler ───────────────────────────────────────────────────────────────
describe('Unknown routes', () => {
  it('should return 404 for unknown routes', async () => {
    const res = await request(app).get('/api/v1/unknown-route');
    expect(res.status).toBe(404);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('NOT_FOUND');
  });
});
