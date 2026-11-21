import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Ticket as TicketIcon, DollarSign, QrCode } from 'lucide-react';
import { apiClient } from '../lib/axios';
import { Ticket, ApiResponse } from '../types';
import Card from '../components/ui/Card';
import Table, { Column } from '../components/ui/Table';
import StatCard from '../components/ui/StatCard';
import { TicketStatusBadge } from '../components/ui/Badge';

const Tickets: React.FC = () => {
  const [filterMethod, setFilterMethod] = useState<'ALL' | 'ONLINE' | 'CASH' | 'UPI'>('ALL');

  const { data: tickets, isLoading, isError } = useQuery<Ticket[]>({
    queryKey: ['tickets-admin'],
    queryFn: async () => {
      // In production/stage, admin can view all tickets from /tickets endpoint
      const res = await apiClient.get<ApiResponse<Ticket[]>>('/tickets/my-tickets');
      return res.data.data || [];
    },
  });

  const filteredTickets = (tickets || []).filter((t) => {
    if (filterMethod === 'ALL') return true;
    return t.paymentMethod === filterMethod;
  });

  const totalRevenue = filteredTickets.reduce((acc, t) => acc + (t.status === 'PAID' || t.status === 'USED' ? t.fare : 0), 0);
  const onlineRevenue = filteredTickets.filter((t) => t.paymentMethod === 'ONLINE').reduce((acc, t) => acc + (t.status === 'PAID' || t.status === 'USED' ? t.fare : 0), 0);
  const cashRevenue = filteredTickets.filter((t) => t.paymentMethod === 'CASH').reduce((acc, t) => acc + (t.status === 'PAID' || t.status === 'USED' ? t.fare : 0), 0);

  const columns: Column<Ticket>[] = [
    {
      key: 'ticketCode',
      header: 'Ticket Code',
      render: (t) => (
        <div className="flex items-center gap-1.5 font-mono text-xs text-gray-800">
          <QrCode size={14} className="text-primary-600" />
          {(t.ticketCode || t.code || t.id).slice(0, 8)}...
        </div>
      ),
    },
    {
      key: 'trip',
      header: 'Trip / Bus',
      render: (t) => (
        <div>
          <p className="font-semibold text-gray-900">{t.trip?.route?.name || 'Assigned Route'}</p>
          <p className="text-xs text-gray-500">Bus: {t.trip?.bus?.busNumber || '—'}</p>
        </div>
      ),
    },
    {
      key: 'fare',
      header: 'Fare Amount',
      render: (t) => <span className="font-bold text-gray-900">₹{t.fare.toFixed(2)}</span>,
    },
    {
      key: 'method',
      header: 'Payment Method',
      render: (t) => (
        <span
          className={`px-2 py-0.5 rounded text-xs font-semibold ${
            t.paymentMethod === 'ONLINE'
              ? 'bg-blue-100 text-blue-800'
              : t.paymentMethod === 'CASH'
              ? 'bg-emerald-100 text-emerald-800'
              : 'bg-purple-100 text-purple-800'
          }`}
        >
          {t.paymentMethod}
        </span>
      ),
    },
    {
      key: 'type',
      header: 'Issuance',
      render: (t) => (
        <span className="text-xs text-gray-600">
          {t.isOfflineIssued ? 'Onboard (Conductor)' : 'Prepaid (Mobile)'}
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Ticket Status',
      render: (t) => <TicketStatusBadge status={t.status} />,
    },
    {
      key: 'date',
      header: 'Issued At',
      render: (t) => new Date(t.createdAt).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }),
    },
  ];

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-bold text-gray-900">Tickets & Fare Transactions</h2>
        <p className="text-sm text-gray-500">Track digital tickets, cash fares, and verified passenger revenue</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard
          title="Total Verified Revenue"
          value={`₹${totalRevenue.toFixed(2)}`}
          icon={<DollarSign size={20} />}
          iconBg="bg-green-100 text-green-600"
        />
        <StatCard
          title="Digital Online Revenue"
          value={`₹${onlineRevenue.toFixed(2)}`}
          icon={<DollarSign size={20} />}
          iconBg="bg-blue-100 text-blue-600"
        />
        <StatCard
          title="Cash Collection"
          value={`₹${cashRevenue.toFixed(2)}`}
          icon={<DollarSign size={20} />}
          iconBg="bg-emerald-100 text-emerald-600"
        />
      </div>

      {/* Filter Chips */}
      <div className="flex gap-2">
        {(['ALL', 'ONLINE', 'CASH', 'UPI'] as const).map((m) => (
          <button
            key={m}
            onClick={() => setFilterMethod(m)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium border ${
              filterMethod === m
                ? 'bg-primary-600 text-white border-primary-600'
                : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'
            }`}
          >
            {m === 'ALL' ? 'All Transactions' : m}
          </button>
        ))}
      </div>

      <Card>
        <Table<Ticket>
          columns={columns}
          data={filteredTickets}
          loading={isLoading}
          emptyMessage={isError ? 'Failed to load tickets' : 'No tickets matching selection.'}
          keyExtractor={(t) => t.id}
        />
      </Card>
    </div>
  );
};

export default Tickets;
