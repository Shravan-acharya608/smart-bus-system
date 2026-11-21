import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { UserPlus, UserCheck, UserX, Mail, Phone } from 'lucide-react';
import { apiClient } from '../lib/axios';
import { User, ApiResponse } from '../types';
import Button from '../components/ui/Button';
import Card from '../components/ui/Card';
import Table, { Column } from '../components/ui/Table';
import Modal from '../components/ui/Modal';
import Input from '../components/ui/Input';

const Staff: React.FC = () => {
  const queryClient = useQueryClient();
  const [roleFilter, setRoleFilter] = useState<'ALL' | 'DRIVER' | 'CONDUCTOR'>('ALL');
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [role, setRole] = useState<'DRIVER' | 'CONDUCTOR'>('DRIVER');
  const [formError, setFormError] = useState<string | null>(null);

  const { data: staffList, isLoading, isError } = useQuery<User[]>({
    queryKey: ['staff', roleFilter],
    queryFn: async () => {
      const url = roleFilter === 'ALL' ? '/admin/staff' : `/admin/staff?role=${roleFilter}`;
      const res = await apiClient.get<ApiResponse<User[]>>(url);
      return res.data.data || [];
    },
  });

  const createStaffMutation = useMutation({
    mutationFn: async (payload: any) => {
      const res = await apiClient.post<ApiResponse<User>>('/admin/staff', payload);
      return res.data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff'] });
      setIsAddModalOpen(false);
      resetForm();
    },
    onError: (err: any) => {
      setFormError(err.response?.data?.error?.message || 'Failed to create staff account');
    },
  });

  const toggleStatusMutation = useMutation({
    mutationFn: async ({ id, isActive }: { id: string; isActive: boolean }) => {
      await apiClient.patch(`/admin/staff/${id}/status`, { isActive });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['staff'] });
    },
  });

  const resetForm = () => {
    setFirstName('');
    setLastName('');
    setEmail('');
    setPassword('');
    setPhone('');
    setRole('DRIVER');
    setFormError(null);
  };

  const handleCreateStaff = (e: React.FormEvent) => {
    e.preventDefault();
    if (!firstName || !lastName || !email || !password) {
      setFormError('Please fill in all required fields (password minimum 8 characters)');
      return;
    }
    createStaffMutation.mutate({ firstName, lastName, email, password, phone, role });
  };

  const columns: Column<User>[] = [
    {
      key: 'name',
      header: 'Staff Member',
      render: (u) => (
        <div>
          <p className="font-semibold text-gray-900">{u.firstName} {u.lastName}</p>
          <p className="text-xs text-gray-500 flex items-center gap-1 mt-0.5">
            <Mail size={12} /> {u.email}
          </p>
          {u.phone && (
            <p className="text-xs text-gray-400 flex items-center gap-1">
              <Phone size={12} /> {u.phone}
            </p>
          )}
        </div>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      render: (u) => (
        <span
          className={`px-2.5 py-0.5 rounded-full text-xs font-semibold ${
            u.role === 'DRIVER' ? 'bg-purple-100 text-purple-800' : 'bg-orange-100 text-orange-800'
          }`}
        >
          {u.role}
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (u) => (
        <span
          className={`px-2 py-0.5 rounded-full text-xs font-medium ${
            u.isActive ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
          }`}
        >
          {u.isActive ? 'Active' : 'Inactive'}
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Account Access',
      render: (u) => (
        <Button
          size="sm"
          variant={u.isActive ? 'danger' : 'secondary'}
          onClick={() => toggleStatusMutation.mutate({ id: u.id, isActive: !u.isActive })}
        >
          {u.isActive ? (
            <>
              <UserX size={13} className="mr-1" /> Deactivate
            </>
          ) : (
            <>
              <UserCheck size={13} className="mr-1" /> Activate
            </>
          )}
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Staff Management</h2>
          <p className="text-sm text-gray-500">Manage transit drivers, conductors, and credentials</p>
        </div>
        <Button variant="primary" onClick={() => setIsAddModalOpen(true)}>
          <UserPlus size={16} className="mr-1.5" /> Add Staff Member
        </Button>
      </div>

      {/* Tabs */}
      <div className="flex gap-2 border-b border-gray-200">
        {(['ALL', 'DRIVER', 'CONDUCTOR'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setRoleFilter(tab)}
            className={`pb-2.5 px-3 text-sm font-medium border-b-2 transition-colors ${
              roleFilter === tab
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700'
            }`}
          >
            {tab === 'ALL' ? 'All Staff' : `${tab.charAt(0) + tab.slice(1).toLowerCase()}s`}
          </button>
        ))}
      </div>

      <Card>
        <Table<User>
          columns={columns}
          data={staffList || []}
          loading={isLoading}
          emptyMessage={isError ? 'Failed to load staff list' : 'No staff members found.'}
          keyExtractor={(u) => u.id}
        />
      </Card>

      {/* Add Staff Modal */}
      <Modal
        isOpen={isAddModalOpen}
        onClose={() => {
          setIsAddModalOpen(false);
          resetForm();
        }}
        title="Add Driver or Conductor"
      >
        <form onSubmit={handleCreateStaff} className="space-y-4">
          {formError && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
              {formError}
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <Input label="First Name" value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
            <Input label="Last Name" value={lastName} onChange={(e) => setLastName(e.target.value)} required />
          </div>

          <Input label="Email address" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <Input
            label="Initial Password"
            type="password"
            placeholder="Minimum 8 characters"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
          <Input label="Phone Number (Optional)" value={phone} onChange={(e) => setPhone(e.target.value)} />

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Assigned Role</label>
            <select
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm bg-white"
              value={role}
              onChange={(e) => setRole(e.target.value as 'DRIVER' | 'CONDUCTOR')}
            >
              <option value="DRIVER">Driver</option>
              <option value="CONDUCTOR">Conductor</option>
            </select>
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t">
            <Button type="button" variant="secondary" onClick={() => setIsAddModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={createStaffMutation.isPending}>
              Create Account
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};

export default Staff;
