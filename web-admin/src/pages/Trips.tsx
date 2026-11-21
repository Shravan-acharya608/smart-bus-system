import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Play, CheckCircle2 } from 'lucide-react';
import { apiClient } from '../lib/axios';
import { API_ENDPOINTS } from '../config/api';
import { Trip, Bus, Route, User, ApiResponse } from '../types';
import Button from '../components/ui/Button';
import Card from '../components/ui/Card';
import Table, { Column } from '../components/ui/Table';
import Modal from '../components/ui/Modal';
import Input from '../components/ui/Input';
import { TripStatusBadge } from '../components/ui/Badge';

const Trips: React.FC = () => {
  const queryClient = useQueryClient();
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [busId, setBusId] = useState('');
  const [routeId, setRouteId] = useState('');
  const [driverId, setDriverId] = useState('');
  const [conductorId, setConductorId] = useState('');
  const [scheduledStart, setScheduledStart] = useState('');
  const [notes, setNotes] = useState('');
  const [formError, setFormError] = useState<string | null>(null);

  const { data: trips, isLoading, isError } = useQuery<Trip[]>({
    queryKey: ['trips'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Trip[]>>(API_ENDPOINTS.TRIPS);
      return res.data.data || [];
    },
  });

  const { data: buses } = useQuery<Bus[]>({
    queryKey: ['buses-select'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Bus[]>>(API_ENDPOINTS.BUSES);
      return res.data.data || [];
    },
  });

  const { data: routes } = useQuery<Route[]>({
    queryKey: ['routes-select'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Route[]>>(API_ENDPOINTS.ROUTES);
      return res.data.data || [];
    },
  });

  const { data: drivers } = useQuery<User[]>({
    queryKey: ['drivers-select'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<User[]>>('/admin/staff?role=DRIVER');
      return res.data.data || [];
    },
  });

  const { data: conductors } = useQuery<User[]>({
    queryKey: ['conductors-select'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<User[]>>('/admin/staff?role=CONDUCTOR');
      return res.data.data || [];
    },
  });

  const createTripMutation = useMutation({
    mutationFn: async (payload: any) => {
      const res = await apiClient.post<ApiResponse<Trip>>(API_ENDPOINTS.TRIPS, payload);
      return res.data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      setIsAddModalOpen(false);
      resetForm();
    },
    onError: (err: any) => {
      setFormError(err.response?.data?.error?.message || 'Failed to create trip');
    },
  });

  const startTripMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`${API_ENDPOINTS.TRIPS}/${id}/start`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['trips'] }),
  });

  const endTripMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`${API_ENDPOINTS.TRIPS}/${id}/end`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['trips'] }),
  });

  const resetForm = () => {
    setBusId('');
    setRouteId('');
    setDriverId('');
    setConductorId('');
    setScheduledStart('');
    setNotes('');
    setFormError(null);
  };

  const handleCreateTrip = (e: React.FormEvent) => {
    e.preventDefault();
    if (!busId || !routeId || !scheduledStart) {
      setFormError('Please select a bus, route, and scheduled start time');
      return;
    }

    createTripMutation.mutate({
      busId,
      routeId,
      driverId: driverId || undefined,
      conductorId: conductorId || undefined,
      scheduledStart: new Date(scheduledStart).toISOString(),
      notes: notes || undefined,
    });
  };

  const columns: Column<Trip>[] = [
    {
      key: 'route',
      header: 'Route',
      render: (t) => (
        <div>
          <p className="font-semibold text-gray-900">{t.route?.name}</p>
          <p className="text-xs text-primary-600 font-mono">{t.route?.code}</p>
        </div>
      ),
    },
    {
      key: 'bus',
      header: 'Assigned Bus',
      render: (t) => (
        <div>
          <p className="font-medium text-gray-900">{t.bus?.busNumber}</p>
          <p className="text-xs text-gray-400">{t.bus?.registrationNo}</p>
        </div>
      ),
    },
    {
      key: 'staff',
      header: 'Assigned Crew',
      render: (t) => {
        const d = t.assignments?.[0]?.driver;
        const c = t.assignments?.[0]?.conductor;
        return (
          <div className="text-xs space-y-0.5">
            <p className="text-gray-700">Driver: <span className="font-medium">{d ? `${d.firstName} ${d.lastName}` : 'Unassigned'}</span></p>
            <p className="text-gray-700">Conductor: <span className="font-medium">{c ? `${c.firstName} ${c.lastName}` : 'Unassigned'}</span></p>
          </div>
        );
      },
    },
    {
      key: 'status',
      header: 'Status',
      render: (t) => <TripStatusBadge status={t.status} />,
    },
    {
      key: 'scheduledStart',
      header: 'Scheduled Time',
      render: (t) => new Date(t.scheduledStart).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' }),
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (t) => (
        <div className="flex gap-2">
          {t.status === 'SCHEDULED' && (
            <Button
              size="sm"
              variant="primary"
              onClick={() => startTripMutation.mutate(t.id)}
              loading={startTripMutation.isPending}
            >
              <Play size={12} className="mr-1" /> Start
            </Button>
          )}
          {t.status === 'ACTIVE' && (
            <Button
              size="sm"
              variant="danger"
              onClick={() => endTripMutation.mutate(t.id)}
              loading={endTripMutation.isPending}
            >
              <CheckCircle2 size={12} className="mr-1" /> End
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Trip Schedules & Assignments</h2>
          <p className="text-sm text-gray-500">Plan and manage active and scheduled bus runs</p>
        </div>
        <Button variant="primary" onClick={() => setIsAddModalOpen(true)}>
          <Plus size={16} className="mr-1.5" /> Schedule Trip
        </Button>
      </div>

      <Card>
        <Table<Trip>
          columns={columns}
          data={trips || []}
          loading={isLoading}
          emptyMessage={isError ? 'Failed to load trips' : 'No trips scheduled yet.'}
          keyExtractor={(t) => t.id}
        />
      </Card>

      {/* Schedule Trip Modal */}
      <Modal
        isOpen={isAddModalOpen}
        onClose={() => {
          setIsAddModalOpen(false);
          resetForm();
        }}
        title="Schedule New Trip"
      >
        <form onSubmit={handleCreateTrip} className="space-y-4">
          {formError && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
              {formError}
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Select Route</label>
            <select
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white"
              value={routeId}
              onChange={(e) => setRouteId(e.target.value)}
              required
            >
              <option value="">-- Choose Route --</option>
              {routes?.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.code} - {r.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Select Bus</label>
            <select
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white"
              value={busId}
              onChange={(e) => setBusId(e.target.value)}
              required
            >
              <option value="">-- Choose Bus --</option>
              {buses?.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.busNumber} ({b.registrationNo}) - Cap: {b.capacity}
                </option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Assign Driver</label>
              <select
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white"
                value={driverId}
                onChange={(e) => setDriverId(e.target.value)}
              >
                <option value="">-- None (Assign Later) --</option>
                {drivers?.map((d) => (
                  <option key={d.id} value={d.id}>
                    {d.firstName} {d.lastName} ({d.email})
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Assign Conductor</label>
              <select
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white"
                value={conductorId}
                onChange={(e) => setConductorId(e.target.value)}
              >
                <option value="">-- None (Assign Later) --</option>
                {conductors?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.firstName} {c.lastName} ({c.email})
                  </option>
                ))}
              </select>
            </div>
          </div>

          <Input
            label="Scheduled Start Date & Time"
            type="datetime-local"
            value={scheduledStart}
            onChange={(e) => setScheduledStart(e.target.value)}
            required
          />

          <Input
            label="Trip Notes (Optional)"
            placeholder="e.g. Regular morning rush hour trip"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
          />

          <div className="flex justify-end gap-3 pt-4 border-t">
            <Button type="button" variant="secondary" onClick={() => setIsAddModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={createTripMutation.isPending}>
              Schedule
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default Trips;
