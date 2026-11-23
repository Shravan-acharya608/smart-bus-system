import { PrismaClient, TripStatus } from '@prisma/client';

const prisma = new PrismaClient({
  datasources: {
    db: { url: process.env.DIRECT_URL || process.env.DATABASE_URL },
  },
});

async function main() {
  console.log('🚍 Seeding Karnataka Udupi - Katpadi - Shirva Route...');

  // 1. Get or create Organization
  let org = await prisma.organization.findFirst({ where: { code: 'METRO' } });
  if (!org) {
    org = await prisma.organization.create({
      data: {
        name: 'Coastal Bus Transit',
        code: 'METRO',
        address: 'Service Bus Stand, Udupi, Karnataka 576101',
        phone: '+91-820-2520001',
        email: 'info@udupismartbus.in',
        isActive: true,
      },
    });
  } else {
    await prisma.organization.update({
      where: { id: org.id },
      data: { name: 'Coastal Bus Transit (Udupi)' },
    });
  }

  // 2. Stops for Udupi - Katpadi - Shirva
  const karnatakaStops = [
    {
      name: 'Udupi Service Bus Stand',
      latitude: 13.3409,
      longitude: 74.7421,
      address: 'Service Bus Stand, Near City Bus Stand, Udupi, Karnataka 576101',
    },
    {
      name: 'Kinnimulki',
      latitude: 13.3235,
      longitude: 74.7388,
      address: 'Kinnimulki Circle, NH 66, Udupi, Karnataka 574118',
    },
    {
      name: 'Katpadi Junction',
      latitude: 13.2848,
      longitude: 74.7505,
      address: 'Katpadi Junction, NH 66, Udupi, Karnataka 574105',
    },
    {
      name: 'Shankarapura',
      latitude: 13.2603,
      longitude: 74.7712,
      address: 'Jasmine Circle, Shankarapura, Udupi, Karnataka 574115',
    },
    {
      name: 'Shirva Bus Stand',
      latitude: 13.2188,
      longitude: 74.8223,
      address: 'Shirva Market Bus Stand, Udupi, Karnataka 574116',
    },
  ];

  const createdStops = [];
  for (const s of karnatakaStops) {
    let stop = await prisma.stop.findFirst({ where: { name: s.name } });
    if (!stop) {
      stop = await prisma.stop.create({
        data: s,
      });
      console.log(`✅ Created Stop: ${stop.name} (${stop.latitude}, ${stop.longitude})`);
    } else {
      stop = await prisma.stop.update({
        where: { id: stop.id },
        data: { latitude: s.latitude, longitude: s.longitude, address: s.address },
      });
      console.log(`🔄 Updated Stop: ${stop.name} (${stop.latitude}, ${stop.longitude})`);
    }
    createdStops.push(stop);
  }

  // 3. Route: Udupi - Katpadi - Shirva
  let route = await prisma.route.findFirst({ where: { code: 'KA-20-UKS' } });
  if (!route) {
    // Also check if R-101 exists and update it or create new
    const oldRoute = await prisma.route.findFirst({ where: { code: 'R-101' } });
    if (oldRoute) {
      route = await prisma.route.update({
        where: { id: oldRoute.id },
        data: {
          name: 'Udupi - Katpadi - Shirva',
          code: 'KA-20-UKS',
          description: 'Regular express via Kinnimulki, Katpadi, and Shankarapura',
        },
      });
      console.log(`🔄 Updated existing Route to Udupi - Katpadi - Shirva: ${route.id}`);
    } else {
      route = await prisma.route.create({
        data: {
          name: 'Udupi - Katpadi - Shirva',
          code: 'KA-20-UKS',
          description: 'Regular express via Kinnimulki, Katpadi, and Shankarapura',
          organizationId: org.id,
          isActive: true,
        },
      });
      console.log(`✅ Created Route: ${route.name}`);
    }
  } else {
    console.log(`✅ Found Route: ${route.name} (${route.id})`);
  }

  // Delete existing route stops for this route and recreate
  await prisma.routeStop.deleteMany({ where: { routeId: route.id } });

  const stopTimings = [
    { order: 1, arrival: '08:00', departure: '08:05', distance: 0, fare: 0 },
    { order: 2, arrival: '08:12', departure: '08:14', distance: 2.5, fare: 10 },
    { order: 3, arrival: '08:24', departure: '08:26', distance: 7.5, fare: 20 },
    { order: 4, arrival: '08:35', departure: '08:37', distance: 12.0, fare: 30 },
    { order: 5, arrival: '08:50', departure: null, distance: 18.5, fare: 45 },
  ];

  for (let i = 0; i < createdStops.length; i++) {
    const timing = stopTimings[i];
    await prisma.routeStop.create({
      data: {
        routeId: route.id,
        stopId: createdStops[i].id,
        stopOrder: timing.order,
        scheduledArrivalTime: timing.arrival,
        scheduledDepartureTime: timing.departure,
        distanceFromStart: timing.distance,
        fareFromStart: timing.fare,
      },
    });
  }
  console.log(`✅ Associated ${createdStops.length} stops to Route ${route.name}`);

  // 4. Update Bus
  let bus = await prisma.bus.findFirst({ where: { registrationNo: 'KA-20-F-1234' } });
  if (!bus) {
    const oldBus = await prisma.bus.findFirst({ where: { registrationNo: 'MC-BUS-001' } });
    if (oldBus) {
      bus = await prisma.bus.update({
        where: { id: oldBus.id },
        data: {
          registrationNo: 'KA-20-F-1234',
          busNumber: 'KA-20-111',
          make: 'Tata / Ashok Leyland',
          model: 'Starbus Express',
        },
      });
      console.log(`🔄 Updated existing Bus to KA-20-111 (${bus.registrationNo})`);
    } else {
      bus = await prisma.bus.create({
        data: {
          registrationNo: 'KA-20-F-1234',
          busNumber: 'KA-20-111',
          capacity: 45,
          make: 'Tata',
          model: 'Starbus Express',
          year: 2023,
          organizationId: org.id,
        },
      });
      console.log(`✅ Created Bus KA-20-111`);
    }
  }

  // 5. Driver & Conductor
  const driver = await prisma.user.findFirst({ where: { role: 'DRIVER' } });
  const conductor = await prisma.user.findFirst({ where: { role: 'CONDUCTOR' } });

  // 6. Ensure an ACTIVE Trip exists
  let activeTrip = await prisma.trip.findFirst({
    where: {
      busId: bus.id,
      routeId: route.id,
      status: TripStatus.ACTIVE,
    },
  });

  if (!activeTrip) {
    // Complete any old active trips
    await prisma.trip.updateMany({
      where: { busId: bus.id, status: TripStatus.ACTIVE },
      data: { status: TripStatus.COMPLETED, actualEnd: new Date() },
    });

    activeTrip = await prisma.trip.create({
      data: {
        busId: bus.id,
        routeId: route.id,
        status: TripStatus.ACTIVE,
        actualStart: new Date(),
        scheduledStart: new Date(),
        scheduledEnd: new Date(Date.now() + 60 * 60 * 1000),
      },
    });
    console.log(`✅ Created ACTIVE Trip ${activeTrip.id}`);

    if (driver && conductor) {
      await prisma.tripAssignment.create({
        data: {
          tripId: activeTrip.id,
          driverId: driver.id,
          conductorId: conductor.id,
        },
      });
      console.log(`✅ Assigned Driver & Conductor to active trip`);
    }
  } else {
    console.log(`✅ Active trip already exists: ${activeTrip.id}`);
  }

  // 7. Add location telemetry near Katpadi Junction (13.2848, 74.7505)
  await prisma.locationUpdate.create({
    data: {
      tripId: activeTrip.id,
      latitude: 13.2848,
      longitude: 74.7505,
      speed: 38.5,
      heading: 145.0,
      accuracy: 6.0,
      timestamp: new Date(),
    },
  });
  console.log(`📍 Ingested latest GPS coordinate at Katpadi Junction (13.2848, 74.7505)`);

  console.log('🎉 Karnataka Udupi - Katpadi - Shirva route seeded successfully!');
}

main()
  .catch((e) => {
    console.error('❌ Failed to seed Karnataka route:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
