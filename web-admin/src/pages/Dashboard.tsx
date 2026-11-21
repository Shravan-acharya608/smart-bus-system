import React from 'react';
import { Bus, Navigation, Users, Ticket, DollarSign, Activity } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../hooks/useApi';
import { API_ENDPOINTS } from '../config/api';
import { DashboardStats, Trip } from '../types';
import StatCard from '../components/ui/StatCard';
import Card from '../components/ui/Card';
import Table from '../components/ui/Table';
import { TripStatusBadge } from '../components/ui/Badge';
import type { Column } from '../components/ui/Table';

interface DashboardData {
  stats: DashboardStats;
  recentTrips: Trip[];
}

const Dashboard: React.FC = () => {
  const { data, isLoading, isError, error } = useQuery<DashboardData, Error>({
    queryKey: ['dashboard'],
    queryFn: () => apiFetch<DashboardData>(API_ENDPOINTS.ADMIN.DASHBOARD),
    staleTime: 60_000,
  });

  const stats = data?.stats;
  const recentTrips = data?.recentTrips ?? [];

  const tripColumns: Column<Trip>[] = [
    {
      key: 'route',
      header: 'Route',
      render: (t) => t.route?.name ?? t.routeId,
    },
    {
      key: 'bus',
      header: 'Bus',
      render: (t) => t.bus?.busNumber ?? t.busId,
    },
    {
      key: 'status',
      header: 'Status',
      render: (t) => <TripStatusBadge status={t.status} />,
    },
    {
      key: 'scheduledStart',
      header: 'Scheduled Start',
      render: (t) =>
        new Date(t.scheduledStart).toLocaleString(undefined, {
          dateStyle: 'short',
          timeStyle: 'short',
        }),
    },
  ];

  if (isError) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-center">
          <p className="text-red-500 font-medium">Failed to load dashboard</p>
          <p className="text-gray-400 text-sm mt-1">{(error as Error)?.message}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-gray-900">Overview</h2>
        <p className="text-sm text-gray-500 mt-0.5">Real-time stats for your fleet</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
        <StatCard
          loading={isLoading}
          title="Total Buses"
          value={stats?.totalBuses ?? 0}
          icon={<Bus size={22} />}
          iconBg="bg-blue-100 text-blue-600"
        />
        <StatCard
          loading={isLoading}
          title="Active Trips"
          value={stats?.activeTrips ?? 0}
          icon={<Activity size={22} />}
          iconBg="bg-green-100 text-green-600"
        />
        <StatCard
          loading={isLoading}
          title="Total Drivers"
          value={stats?.totalDrivers ?? 0}
          icon={<Users size={22} />}
          iconBg="bg-purple-100 text-purple-600"
        />
        <StatCard
          loading={isLoading}
          title="Total Conductors"
          value={stats?.totalConductors ?? 0}
          icon={<Users size={22} />}
          iconBg="bg-orange-100 text-orange-600"
        />
        <StatCard
          loading={isLoading}
          title="Today's Revenue"
          value={stats ? `₹${stats.todayRevenue.toFixed(2)}` : '—'}
          icon={<DollarSign size={22} />}
          iconBg="bg-emerald-100 text-emerald-600"
        />
        <StatCard
          loading={isLoading}
          title="Total Tickets"
          value={stats?.totalTickets ?? 0}
          icon={<Ticket size={22} />}
          iconBg="bg-pink-100 text-pink-600"
        />
      </div>

      {/* Recent Trips */}
      <Card title="Recent Trips">
        {isError ? (
          <p className="text-red-500 text-sm py-4 text-center">Failed to load trips</p>
        ) : (
          <Table<Trip>
            columns={tripColumns}
            data={recentTrips.slice(0, 5)}
            loading={isLoading}
            emptyMessage="No recent trips found"
            keyExtractor={(t) => t.id}
          />
        )}
      </Card>

      {/* Trip count summary */}
      {!isLoading && stats && (
        <Card title="Passenger Summary" padding="md">
          <div className="flex items-center gap-4">
            <Navigation size={36} className="text-primary-400 flex-shrink-0" />
            <div>
              <p className="text-3xl font-bold text-gray-900">{stats.totalPassengers}</p>
              <p className="text-sm text-gray-500">Total passengers served</p>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
};

export default Dashboard;
