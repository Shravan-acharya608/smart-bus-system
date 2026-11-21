import { PrismaClient, UserRole } from '@prisma/client';
import bcrypt from 'bcrypt';

const prisma = new PrismaClient({
  datasources: {
    db: { url: process.env.DIRECT_URL || process.env.DATABASE_URL },
  },
});

const SALT_ROUNDS = 12;

async function hashPassword(plain: string): Promise<string> {
  return bcrypt.hash(plain, SALT_ROUNDS);
}

async function main(): Promise<void> {
  console.log('🌱 Starting seed...');

  // ── Organization ─────────────────────────────────────────────────────────
  const org = await prisma.organization.upsert({
    where: { code: 'METRO' },
    update: {},
    create: {
      name: 'Metro Bus Co',
      code: 'METRO',
      address: '123 Transit Avenue, Metro City, MC 10001',
      phone: '+1-555-METRO-01',
      email: 'info@metrobus.co',
      isActive: true,
    },
  });
  console.log(`✅ Organization: ${org.name} (${org.id})`);

  // ── Users ─────────────────────────────────────────────────────────────────
  const adminHash = await hashPassword('Admin@123456');
  const admin = await prisma.user.upsert({
    where: { email: 'admin@smartbus.local' },
    update: {},
    create: {
      email: 'admin@smartbus.local',
      passwordHash: adminHash,
      firstName: 'System',
      lastName: 'Admin',
      role: UserRole.ADMIN,
      isVerified: true,
    },
  });
  console.log(`✅ Admin: ${admin.email} (${admin.id})`);

  const ownerHash = await hashPassword('Owner@123456');
  const busOwner = await prisma.user.upsert({
    where: { email: 'owner@smartbus.local' },
    update: {},
    create: {
      email: 'owner@smartbus.local',
      passwordHash: ownerHash,
      firstName: 'Bus',
      lastName: 'Owner',
      role: UserRole.BUS_OWNER,
      organizationId: org.id,
      isVerified: true,
    },
  });
  console.log(`✅ Bus Owner: ${busOwner.email} (${busOwner.id})`);

  const driverHash = await hashPassword('Driver@123456');
  const driver = await prisma.user.upsert({
    where: { email: 'driver@smartbus.local' },
    update: {},
    create: {
      email: 'driver@smartbus.local',
      passwordHash: driverHash,
      firstName: 'John',
      lastName: 'Driver',
      role: UserRole.DRIVER,
      organizationId: org.id,
      isVerified: true,
    },
  });
  console.log(`✅ Driver: ${driver.email} (${driver.id})`);

  const conductorHash = await hashPassword('Conductor@123456');
  const conductor = await prisma.user.upsert({
    where: { email: 'conductor@smartbus.local' },
    update: {},
    create: {
      email: 'conductor@smartbus.local',
      passwordHash: conductorHash,
      firstName: 'Jane',
      lastName: 'Conductor',
      role: UserRole.CONDUCTOR,
      organizationId: org.id,
      isVerified: true,
    },
  });
  console.log(`✅ Conductor: ${conductor.email} (${conductor.id})`);

  const passengerHash = await hashPassword('Passenger@123456');
  const passenger = await prisma.user.upsert({
    where: { email: 'passenger@smartbus.local' },
    update: {},
    create: {
      email: 'passenger@smartbus.local',
      passwordHash: passengerHash,
      firstName: 'Alice',
      lastName: 'Passenger',
      role: UserRole.PASSENGER,
      isVerified: true,
    },
  });
  console.log(`✅ Passenger: ${passenger.email} (${passenger.id})`);

  // ── Stops ─────────────────────────────────────────────────────────────────
  const stopData = [
    { name: 'Central Station', latitude: 40.7128, longitude: -74.006, address: '1 Central Plaza' },
    { name: 'Downtown Hub', latitude: 40.7148, longitude: -74.013, address: '45 Downtown Blvd' },
    { name: 'Midtown Square', latitude: 40.7589, longitude: -73.9851, address: '100 Midtown Ave' },
    { name: 'Uptown Terminal', latitude: 40.7831, longitude: -73.9712, address: '200 Uptown Rd' },
    { name: 'Airport Express', latitude: 40.6413, longitude: -73.7781, address: 'Terminal B, Airport' },
  ];

  const stops = await Promise.all(
    stopData.map((s) =>
      prisma.stop.upsert({
        where: {
          // latitude/longitude pair as pseudo-unique key via findFirst+create pattern
          // Prisma doesn't support composite unique on floats natively, so we use create with a check
          id: `00000000-0000-0000-0000-${Buffer.from(s.name).toString('hex').slice(0, 12)}`,
        },
        update: {},
        create: {
          id: `00000000-0000-0000-0000-${Buffer.from(s.name).toString('hex').slice(0, 12)}`,
          name: s.name,
          latitude: s.latitude,
          longitude: s.longitude,
          address: s.address,
        },
      }),
    ),
  );

  console.log(`✅ Stops created: ${stops.length}`);

  // ── Bus ───────────────────────────────────────────────────────────────────
  const bus = await prisma.bus.upsert({
    where: { registrationNo: 'MC-BUS-001' },
    update: {},
    create: {
      registrationNo: 'MC-BUS-001',
      busNumber: 'M101',
      capacity: 50,
      make: 'Volvo',
      model: '9400',
      year: 2022,
      organizationId: org.id,
    },
  });
  console.log(`✅ Bus: ${bus.busNumber} (${bus.id})`);

  // ── Route ─────────────────────────────────────────────────────────────────
  const route = await prisma.route.upsert({
    where: { code: 'R-101' },
    update: {},
    create: {
      name: 'Central to Airport Express',
      code: 'R-101',
      description: 'Direct express route from Central Station to Airport',
      organizationId: org.id,
      isActive: true,
    },
  });
  console.log(`✅ Route: ${route.name} (${route.id})`);

  // ── Route Stops ───────────────────────────────────────────────────────────
  const routeStopData = [
    { stopOrder: 1, arrival: '06:00', departure: '06:05', distance: 0, fare: 0 },
    { stopOrder: 2, arrival: '06:15', departure: '06:18', distance: 2.5, fare: 10 },
    { stopOrder: 3, arrival: '06:35', departure: '06:38', distance: 8.0, fare: 25 },
    { stopOrder: 4, arrival: '06:55', departure: '06:58', distance: 14.5, fare: 40 },
    { stopOrder: 5, arrival: '07:30', departure: null, distance: 28.0, fare: 75 },
  ];

  for (let i = 0; i < stops.length; i++) {
    const d = routeStopData[i];
    await prisma.routeStop.upsert({
      where: { routeId_stopId: { routeId: route.id, stopId: stops[i].id } },
      update: {},
      create: {
        routeId: route.id,
        stopId: stops[i].id,
        stopOrder: d.stopOrder,
        scheduledArrivalTime: d.arrival,
        scheduledDepartureTime: d.departure ?? undefined,
        distanceFromStart: d.distance,
        fareFromStart: d.fare,
      },
    });
  }
  console.log(`✅ Route stops created: ${stops.length}`);

  console.log('\n🎉 Seed completed successfully!\n');
  console.log('Seed credentials:');
  console.log('  Admin:     admin@smartbus.local / Admin@123456');
  console.log('  Bus Owner: owner@smartbus.local / Owner@123456');
  console.log('  Driver:    driver@smartbus.local / Driver@123456');
  console.log('  Conductor: conductor@smartbus.local / Conductor@123456');
  console.log('  Passenger: passenger@smartbus.local / Passenger@123456');
}

main()
  .catch((e) => {
    console.error('❌ Seed failed:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
