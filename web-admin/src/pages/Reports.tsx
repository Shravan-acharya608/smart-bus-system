import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Download, Calendar, DollarSign, TrendingUp, BarChart2 } from 'lucide-react';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, Tooltip, CartesianGrid } from 'recharts';
import { apiClient } from '../lib/axios';
import { ApiResponse } from '../types';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import StatCard from '../components/ui/StatCard';

interface ReportsData {
  totalRevenue: number;
  totalTickets: number;
  dailyStats: Array<{
    date: string;
    revenue: number;
    ticketCount: number;
    cashCount: number;
    onlineCount: number;
  }>;
  routeStats: Array<{
    routeName: string;
    routeCode: string;
    revenue: number;
    ticketCount: number;
  }>;
}

const Reports: React.FC = () => {
  const [days, setDays] = useState('7');

  const { data, isLoading } = useQuery<ReportsData>({
    queryKey: ['reports-data', days],
    queryFn: async () => {
      const startDate = new Date(Date.now() - parseInt(days, 10) * 24 * 60 * 60 * 1000).toISOString();
      const res = await apiClient.get<ApiResponse<ReportsData>>(`/admin/reports?startDate=${startDate}`);
      return res.data.data!;
    },
  });

  const handleDownloadCsv = () => {
    const startDate = new Date(Date.now() - parseInt(days, 10) * 24 * 60 * 60 * 1000).toISOString();
    window.open(`${apiClient.defaults.baseURL}/admin/reports?startDate=${startDate}&format=csv`, '_blank');
  };

  const chartData = (data?.dailyStats || []).map((d) => ({
    date: d.date.slice(5), // MM-DD
    Revenue: d.revenue,
    Tickets: d.ticketCount,
  }));

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Operational & Financial Reports</h2>
          <p className="text-sm text-gray-500">Analyze route performance, ticket volume, and audited fare revenue</p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={days}
            onChange={(e) => setDays(e.target.value)}
            className="px-3 py-1.5 border border-gray-300 rounded-lg text-sm bg-white"
          >
            <option value="7">Last 7 Days</option>
            <option value="14">Last 14 Days</option>
            <option value="30">Last 30 Days</option>
          </select>
          <Button variant="secondary" onClick={handleDownloadCsv}>
            <Download size={14} className="mr-1.5" /> Export CSV
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <StatCard
          title="Period Fare Revenue"
          value={`₹${(data?.totalRevenue ?? 0).toFixed(2)}`}
          icon={<DollarSign size={20} />}
          iconBg="bg-emerald-100 text-emerald-600"
        />
        <StatCard
          title="Total Tickets Issued"
          value={data?.totalTickets ?? 0}
          icon={<TrendingUp size={20} />}
          iconBg="bg-blue-100 text-blue-600"
        />
      </div>

      {/* Daily Revenue Chart */}
      <Card title="Daily Fare Revenue (₹)">
        {isLoading ? (
          <div className="h-64 flex items-center justify-center text-gray-400">Loading chart...</div>
        ) : chartData.length === 0 ? (
          <div className="h-64 flex flex-col items-center justify-center text-gray-400">
            <BarChart2 size={36} className="text-gray-300 mb-2" />
            <p className="text-sm font-medium">No revenue recorded in this period</p>
            <p className="text-xs text-gray-400">Completed ticket purchases will appear here.</p>
          </div>
        ) : (
          <div className="h-64 mt-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chartData}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" />
                <XAxis dataKey="date" tickLine={false} axisLine={false} stroke="#64748b" fontSize={12} />
                <YAxis tickLine={false} axisLine={false} stroke="#64748b" fontSize={12} />
                <Tooltip
                  formatter={(val: number) => [`₹${val}`, 'Revenue']}
                  contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                />
                <Bar dataKey="Revenue" fill="#0284c7" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>

      {/* Route Usage Breakdown */}
      <Card title="Route Utilization Breakdown">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b border-gray-200 text-xs font-semibold text-gray-500 uppercase">
                <th className="py-3 px-4">Route</th>
                <th className="py-3 px-4">Code</th>
                <th className="py-3 px-4">Tickets Sold</th>
                <th className="py-3 px-4">Total Revenue</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {(data?.routeStats || []).map((r, idx) => (
                <tr key={idx} className="hover:bg-gray-50">
                  <td className="py-3 px-4 font-medium text-gray-900">{r.routeName}</td>
                  <td className="py-3 px-4 font-mono text-xs text-primary-600">{r.routeCode}</td>
                  <td className="py-3 px-4 text-gray-700">{r.ticketCount}</td>
                  <td className="py-3 px-4 font-bold text-gray-900">₹{r.revenue.toFixed(2)}</td>
                </tr>
              ))}
              {(!data?.routeStats || data.routeStats.length === 0) && (
                <tr>
                  <td colSpan={4} className="py-6 text-center text-gray-400">
                    No route sales recorded yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </Card>
    </div>
  );
};

export default Reports;
