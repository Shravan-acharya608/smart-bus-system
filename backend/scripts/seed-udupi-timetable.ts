import { PrismaClient, TripStatus, BusStatus } from '@prisma/client';
import { UDUPI_PLACE_COORDS, UDUPI_SCHEDULES, buildStopsForSchedule } from '../src/data/udupiTimetable';

const prisma = new PrismaClient();

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.asin(Math.sqrt(a));
}

async function main() {
  console.log('🌱 Seeding Udupi Bus Stand 139-bus timetable...');

  const org = await prisma.organization.findFirst();
  if (!org) {
    throw new Error('No Organization found in database. Seed core org first.');
  }

  // 1. Upsert all unique stops
  const stopMap = new Map<string, string>(); // stop name -> id
  const existingStops = await prisma.stop.findMany();
  for (const s of existingStops) {
    stopMap.set(s.name.toLowerCase(), s.id);
  }

  for (const place of Object.values(UDUPI_PLACE_COORDS)) {
    const key = place.name.toLowerCase();
    if (!stopMap.has(key)) {
      const created = await prisma.stop.create({
        data: {
          name: place.name,
          latitude: place.latitude,
          longitude: place.longitude,
          address: `${place.name}, Karnataka`,
        },
      });
      stopMap.set(key, created.id);
    }
  }
  console.log(`✅ Stops ready: ${stopMap.size}`);

  // 2. Group distinct corridors (from + to + via) -> create Routes & RouteStops
  const routeCache = new Map<string, { id: string; code: string; name: string }>();

  for (const row of UDUPI_SCHEDULES) {
    const corridorKey = `${row.from}|${row.to}|${row.via}`.toLowerCase();
    if (routeCache.has(corridorKey)) continue;

    const cleanCodeSlug = `${row.to}-${row.via}`
      .replace(/[^a-zA-Z0-9]+/g, '')
      .toUpperCase()
      .slice(0, 10);
    const routeCode = `KA20-${String(routeCache.size + 1).padStart(2, '0')}-${cleanCodeSlug}`.slice(0, 20);
    const routeName = `${row.from} – ${row.to} (${row.via})`;

    let route = await prisma.route.findUnique({ where: { code: routeCode } });
    if (!route) {
      const existingByName = await prisma.route.findFirst({ where: { name: routeName } });
      if (existingByName) {
        route = existingByName;
      } else {
        route = await prisma.route.create({
          data: {
            name: routeName,
            code: routeCode,
            description: `${row.serviceClass} service via ${row.via}`,
            organizationId: org.id,
            isActive: true,
          },
        });

        const stopsList = buildStopsForSchedule(row);
        let cumulativeDist = 0;
        for (let i = 0; i < stopsList.length; i++) {
          const st = stopsList[i];
          if (i > 0) {
            cumulativeDist += haversineKm(
              stopsList[i - 1].latitude,
              stopsList[i - 1].longitude,
              st.latitude,
              st.longitude
            );
          }
          const stopId = stopMap.get(st.name.toLowerCase())!;
          const fare = i === 0 ? 0 : Math.max(10, Math.round((cumulativeDist * 1.8) / 5) * 5);
          await prisma.routeStop.create({
            data: {
              routeId: route.id,
              stopId,
              stopOrder: st.order,
              distanceFromStart: Number(cumulativeDist.toFixed(1)),
              fareFromStart: fare,
            },
          });
        }
      }
    }
    routeCache.set(corridorKey, { id: route.id, code: route.code, name: route.name });
  }
  console.log(`✅ Distinct routes ready: ${routeCache.size}`);

  // 3. Ensure Buses & Scheduled Trips exist for all 139 departures
  // Check existing timetable trips so we don't duplicate
  const existingTrips = await prisma.trip.findMany({
    where: { notes: { contains: 'Sl.No #' } },
    select: { notes: true },
  });
  const seededSlNos = new Set<number>();
  for (const t of existingTrips) {
    const m = t.notes?.match(/Sl\.No #(\d+)/);
    if (m) seededSlNos.add(parseInt(m[1], 10));
  }

  let createdTripsCount = 0;
  const todayBase = new Date();
  todayBase.setSeconds(0, 0);

  // Use a compact pool of 30 real Udupi buses cycling cleanly across the 139 daily departures
  const busPool: string[] = [];
  for (let b = 1; b <= 30; b++) {
    const busNum = `KA-20-UDP-${String(b).padStart(2, '0')}`;
    const regNo = `KA-20-F-${4000 + b}`;
    let bus = await prisma.bus.findFirst({ where: { registrationNo: regNo } });
    if (!bus) {
      bus = await prisma.bus.create({
        data: {
          busNumber: busNum,
          registrationNo: regNo,
          capacity: 48,
          make: 'Tata',
          model: 'Starbus Ordinary',
          status: BusStatus.ACTIVE,
          organizationId: org.id,
        },
      });
    }
    busPool.push(bus.id);
  }

  const tripsToCreate = [];
  for (const row of UDUPI_SCHEDULES) {
    if (seededSlNos.has(row.slNo)) continue;
    const corridorKey = `${row.from}|${row.to}|${row.via}`.toLowerCase();
    const routeInfo = routeCache.get(corridorKey);
    if (!routeInfo) continue;

    const [hh, mm] = row.departureTime.split('.').map((n) => parseInt(n, 10));
    const depDate = new Date(todayBase);
    depDate.setHours(hh, mm, 0, 0);
    const endDate = new Date(depDate.getTime() + 60 * 60 * 1000);

    const busId = busPool[(row.slNo - 1) % busPool.length];
    tripsToCreate.push({
      busId,
      routeId: routeInfo.id,
      scheduledStart: depDate,
      scheduledEnd: endDate,
      status: TripStatus.SCHEDULED,
      notes: `Sl.No #${row.slNo} | ${row.serviceClass} | Via ${row.via} | Dep ${row.departureTime}`,
    });
  }

  if (tripsToCreate.length > 0) {
    await prisma.trip.createMany({ data: tripsToCreate });
    createdTripsCount = tripsToCreate.length;
  }

  console.log(`✅ Created ${createdTripsCount} scheduled Udupi Bus Stand departures (Total: 139 covered).`);
}

main()
  .catch((e) => {
    console.error('❌ Seed error:', e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
