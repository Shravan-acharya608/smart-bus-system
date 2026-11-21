import React, { useState } from 'react';
import { Plus, Pencil, AlertCircle } from 'lucide-react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { apiClient } from '../lib/axios';
import { API_ENDPOINTS } from '../config/api';
import { Bus, BusStatus, ApiResponse } from '../types';
import { apiFetch } from '../hooks/useApi';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import Modal from '../components/ui/Modal';
import Table from '../components/ui/Table';
import { BusStatusBadge } from '../components/ui/Badge';
import type { Column } from '../components/ui/Table';

const busSchema = z.object({
  registrationNo: z.string().min(1, 'Registration number is required'),
  busNumber: z.string().min(1, 'Bus number is required'),
  capacity: z.coerce.number().int().min(1, 'Capacity must be at least 1'),
  make: z.string().optional(),
  model: z.string().optional(),
  year: z.coerce.number().int().min(1990).max(new Date().getFullYear() + 1).optional().or(z.literal('')),
  status: z.enum(['ACTIVE', 'INACTIVE', 'MAINTENANCE'] as const),
});

type BusFormValues = z.infer<typeof busSchema>;

interface BusListResponse {
  buses: Bus[];
  pagination: { page: number; limit: number; total: number; pages: number };
}

const Buses: React.FC = () => {
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [editingBus, setEditingBus] = useState<Bus | null>(null);
  const [toastMsg, setToastMsg] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [page, setPage] = useState(1);

  const { data, isLoading, isError } = useQuery<BusListResponse, Error>({
    queryKey: ['buses', page],
    queryFn: () => apiFetch<BusListResponse>(API_ENDPOINTS.BUSES, { page, limit: 10 }),
  });

  const buses = data?.buses ?? [];
  const totalPages = data?.pagination?.pages ?? 1;
  const totalItems = data?.pagination?.total ?? 0;

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<BusFormValues>({
    resolver: zodResolver(busSchema),
    defaultValues: { status: 'ACTIVE' },
  });

  const showToast = (type: 'success' | 'error', text: string) => {
    setToastMsg({ type, text });
    setTimeout(() => setToastMsg(null), 3500);
  };

  const createMutation = useMutation<Bus, Error, BusFormValues>({
    mutationFn: async (values) => {
      const res = await apiClient.post<ApiResponse<Bus>>(API_ENDPOINTS.BUSES, values);
      if (!res.data.success) throw new Error(res.data.error?.message || 'Failed to create bus');
      return res.data.data!;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['buses'] });
      setModalOpen(false);
      reset();
      showToast('success', 'Bus created successfully');
    },
    onError: (err) => showToast('error', err.message),
  });

  const updateMutation = useMutation<Bus, Error, BusFormValues & { id: string }>({
    mutationFn: async ({ id, ...values }) => {
      const res = await apiClient.put<ApiResponse<Bus>>(`${API_ENDPOINTS.BUSES}/${id}`, values);
      if (!res.data.success) throw new Error(res.data.error?.message || 'Failed to update bus');
      return res.data.data!;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['buses'] });
      setModalOpen(false);
      setEditingBus(null);
      reset();
      showToast('success', 'Bus updated successfully');
    },
    onError: (err) => showToast('error', err.message),
  });

  const openCreate = () => {
    setEditingBus(null);
    reset({ status: 'ACTIVE', registrationNo: '', busNumber: '', capacity: 40, make: '', model: '' });
    setModalOpen(true);
  };

  const openEdit = (bus: Bus) => {
    setEditingBus(bus);
    reset({
      registrationNo: bus.registrationNo,
      busNumber: bus.busNumber,
      capacity: bus.capacity,
      make: bus.make ?? '',
      model: bus.model ?? '',
      year: bus.year ?? '',
      status: bus.status,
    });
    setModalOpen(true);
  };

  const onSubmit = (values: BusFormValues) => {
    if (editingBus) {
      updateMutation.mutate({ ...values, id: editingBus.id });
    } else {
      createMutation.mutate(values);
    }
  };

  const columns: Column<Bus>[] = [
    { key: 'busNumber', header: 'Bus Number' },
    { key: 'registrationNo', header: 'Registration' },
    { key: 'capacity', header: 'Capacity' },
    {
      key: 'status',
      header: 'Status',
      render: (b) => <BusStatusBadge status={b.status} />,
    },
    {
      key: 'make',
      header: 'Make / Model',
      render: (b) => b.make && b.model ? `${b.make} ${b.model}` : b.make || b.model || '—',
    },
    {
      key: 'year',
      header: 'Year',
      render: (b) => b.year ?? '—',
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (b) => (
        <Button
          variant="ghost"
          size="sm"
          icon={<Pencil size={14} />}
          onClick={() => openEdit(b)}
        >
          Edit
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-5">
      {/* Toast */}
      {toastMsg && (
        <div
          className={`fixed top-4 right-4 z-50 px-4 py-3 rounded-lg shadow-lg text-sm font-medium flex items-center gap-2 ${
            toastMsg.type === 'success'
              ? 'bg-green-600 text-white'
              : 'bg-red-600 text-white'
          }`}
        >
          {toastMsg.type === 'error' && <AlertCircle size={16} />}
          {toastMsg.text}
        </div>
      )}

      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Bus Management</h2>
          <p className="text-sm text-gray-500 mt-0.5">Manage your fleet of buses</p>
        </div>
        <Button variant="primary" icon={<Plus size={16} />} onClick={openCreate}>
          Add Bus
        </Button>
      </div>

      {isError && (
        <div className="flex items-center gap-2 p-4 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm">
          <AlertCircle size={16} />
          Failed to load buses. Please refresh.
        </div>
      )}

      <Card padding="none">
        <Table<Bus>
          columns={columns}
          data={buses}
          loading={isLoading}
          emptyMessage="No buses found. Add your first bus!"
          keyExtractor={(b) => b.id}
          currentPage={page}
          totalPages={totalPages}
          totalItems={totalItems}
          pageSize={10}
          onPageChange={setPage}
        />
      </Card>

      {/* Add/Edit Modal */}
      <Modal
        isOpen={modalOpen}
        onClose={() => { setModalOpen(false); setEditingBus(null); reset(); }}
        title={editingBus ? 'Edit Bus' : 'Add New Bus'}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => { setModalOpen(false); setEditingBus(null); reset(); }}>
              Cancel
            </Button>
            <Button
              variant="primary"
              loading={isSubmitting || createMutation.isPending || updateMutation.isPending}
              onClick={handleSubmit(onSubmit)}
            >
              {editingBus ? 'Save Changes' : 'Add Bus'}
            </Button>
          </>
        }
      >
        <form className="space-y-4" onSubmit={handleSubmit(onSubmit)}>
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Bus Number"
              placeholder="e.g. BUS-001"
              error={errors.busNumber?.message}
              {...register('busNumber')}
            />
            <Input
              label="Registration No."
              placeholder="e.g. KA01AB1234"
              error={errors.registrationNo?.message}
              {...register('registrationNo')}
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Input
              label="Capacity"
              type="number"
              placeholder="40"
              error={errors.capacity?.message}
              {...register('capacity')}
            />
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
              <select
                className="block w-full rounded-lg border border-gray-300 bg-white px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                {...register('status')}
              >
                {(['ACTIVE', 'INACTIVE', 'MAINTENANCE'] as BusStatus[]).map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
              {errors.status && <p className="mt-1 text-xs text-red-600">{errors.status.message}</p>}
            </div>
          </div>
          <div className="grid grid-cols-3 gap-4">
            <Input
              label="Make"
              placeholder="e.g. Tata"
              error={errors.make?.message}
              {...register('make')}
            />
            <Input
              label="Model"
              placeholder="e.g. Starbus"
              error={errors.model?.message}
              {...register('model')}
            />
            <Input
              label="Year"
              type="number"
              placeholder="2023"
              error={errors.year?.message}
              {...register('year')}
            />
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default Buses;
