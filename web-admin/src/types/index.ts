export type UserRole = 'PASSENGER' | 'DRIVER' | 'CONDUCTOR' | 'BUS_OWNER' | 'ADMIN';
export type BusStatus = 'ACTIVE' | 'INACTIVE' | 'MAINTENANCE';
export type TripStatus = 'SCHEDULED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type TicketStatus = 'PENDING' | 'PAID' | 'USED' | 'CANCELLED' | 'EXPIRED' | 'HELD' | 'REFUNDED';
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED' | 'PARTIAL_REFUND';
export type EscrowStatus = 'HELD_IN_ESCROW' | 'SETTLED_TO_OWNER' | 'REFUNDED_TO_USER';

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: { code: string; message: string; details?: unknown };
  pagination?: { page: number; limit: number; total: number; pages: number };
}

export interface User {
  id: string;
  email: string;
  phone?: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  isActive: boolean;
  organizationId?: string;
  createdAt: string;
}

export interface Bus {
  id: string;
  registrationNo: string;
  busNumber: string;
  capacity: number;
  status: BusStatus;
  make?: string;
  model?: string;
  year?: number;
  organizationId: string;
}

export interface Route {
  id: string;
  name: string;
  code: string;
  description?: string;
  isActive: boolean;
  stops?: RouteStop[];
}

export interface Stop {
  id: string;
  name: string;
  latitude: number;
  longitude: number;
  address?: string;
}

export interface RouteStop {
  id: string;
  routeId: string;
  stopId: string;
  stopOrder: number;
  scheduledArrivalTime?: string;
  fareFromStart?: number;
  stop: Stop;
}

export interface Trip {
  id: string;
  busId: string;
  routeId: string;
  status: TripStatus;
  scheduledStart: string;
  scheduledEnd?: string;
  actualStart?: string;
  actualEnd?: string;
  bus?: Bus;
  route?: Route;
  assignments?: TripAssignment[];
}

export interface TripAssignment {
  id: string;
  tripId: string;
  driverId?: string;
  conductorId?: string;
  driver?: User;
  conductor?: User;
}

export interface Ticket {
  id: string;
  code?: string;
  ticketCode?: string;
  passengerId: string;
  tripId: string;
  boardingStopId: string;
  destinationStopId: string;
  fare: number;
  status: TicketStatus;
  paymentStatus: PaymentStatus;
  paymentMethod?: string;
  isOfflineIssued?: boolean;
  createdAt: string;
  passenger?: User;
  trip?: Trip;
  boardingStop?: Stop;
  destinationStop?: Stop;
}

export interface DashboardStats {
  totalBuses: number;
  activeTrips: number;
  totalDrivers: number;
  totalConductors: number;
  totalTickets: number;
  todayRevenue: number;
  totalPassengers: number;
}

export interface ReportData {
  dailySales: { date: string; tickets: number; revenue: number }[];
  passengerCounts: { date: string; count: number }[];
  routeRevenue: { routeName: string; revenue: number; tickets: number }[];
}

export interface LiveBusLocation {
  busId: string;
  busNumber: string;
  latitude: number;
  longitude: number;
  speed?: number;
  routeName?: string;
  driverName?: string;
  lastUpdated: string;
  status: BusStatus;
}
