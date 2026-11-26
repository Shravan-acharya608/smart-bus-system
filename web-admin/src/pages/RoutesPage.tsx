import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, MapPin, Eye, Route as RouteIcon } from 'lucide-react';
import { apiClient } from '../lib/axios';
import { API_ENDPOINTS } from '../config/api';
import { Route, ApiResponse } from '../types';
import Button from '../components/ui/Button';
import Card from '../components/ui/Card';
import Table, { Column } from '../components/ui/Table';
import Modal from '../components/ui/Modal';
import Input from '../components/ui/Input';

const RoutesPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [selectedRoute, setSelectedRoute] = useState<Route | null>(null);
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isDetailsModalOpen, setIsDetailsModalOpen] = useState(false);

  // Form states for creating a route
  const [name, setName] = useState('');
  const [code, setCode] = useState('');
  const [description, setDescription] = useState('');
  const [stop1Name, setStop1Name] = useState('');
  const [stop1Lat, setStop1Lat] = useState('12.9716');
  const [stop1Lng, setStop1Lng] = useState('77.5946');
  const [stop2Name, setStop2Name] = useState('');
  const [stop2Lat, setStop2Lat] = useState('12.9816');
  const [stop2Lng, setStop2Lng] = useState('77.6046');
  const [formError, setFormError] = useState<string | null>(null);

  const { data: routes, isLoading, isError } = useQuery<Route[]>({
    queryKey: ['routes'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Route[]>>(`${API_ENDPOINTS.ROUTES}?limit=100`);
      return res.data.data || [];
    },
  });

  const createRouteMutation = useMutation({
    mutationFn: async (payload: any) => {
      const res = await apiClient.post<ApiResponse<Route>>(API_ENDPOINTS.ROUTES, payload);
      return res.data.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['routes'] });
      setIsAddModalOpen(false);
      resetForm();
    },
    onError: (err: any) => {
      setFormError(err.response?.data?.error?.message || 'Failed to create route');
    },
  });

  const resetForm = () => {
    setName('');
    setCode('');
    setDescription('');
    setStop1Name('');
    setStop2Name('');
    setFormError(null);
  };

  const handleCreateRoute = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name || !code || !stop1Name || !stop2Name) {
      setFormError('Please fill in route name, code, origin stop, and destination stop.');
      return;
    }

    createRouteMutation.mutate({
      name,
      code,
      description,
      stops: [
        {
          name: stop1Name,
          latitude: parseFloat(stop1Lat) || 12.9716,
          longitude: parseFloat(stop1Lng) || 77.5946,
          stopOrder: 1,
          distanceFromStart: 0,
          fareFromStart: 0,
        },
        {
          name: stop2Name,
          latitude: parseFloat(stop2Lat) || 12.9816,
          longitude: parseFloat(stop2Lng) || 77.6046,
          stopOrder: 2,
          distanceFromStart: 5.5,
          fareFromStart: 25,
        },
      ],
    });
  };

  const columns: Column<Route>[] = [
    {
      key: 'code',
      header: 'Route Code',
      render: (r) => <span className="font-semibold text-primary-600">{r.code}</span>,
    },
    { key: 'name', header: 'Route Name' },
    {
      key: 'stopsCount',
      header: 'Total Stops',
      render: (r) => (
        <span className="inline-flex items-center gap-1 text-sm text-gray-600">
          <MapPin size={14} className="text-primary-500" />
          {r.stops?.length ?? 0} stops
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (r) => (
        <span
          className={`px-2.5 py-0.5 rounded-full text-xs font-medium ${
            r.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-700'
          }`}
        >
          {r.isActive ? 'Active' : 'Inactive'}
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (r) => (
        <Button
          size="sm"
          variant="secondary"
          onClick={() => {
            setSelectedRoute(r);
            setIsDetailsModalOpen(true);
          }}
        >
          <Eye size={14} className="mr-1" /> View Stops
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Route & Stop Management</h2>
          <p className="text-sm text-gray-500">Configure transit routes, stop sequences, and fare stages</p>
        </div>
        <Button variant="primary" onClick={() => setIsAddModalOpen(true)}>
          <Plus size={16} className="mr-1.5" /> Add Route
        </Button>
      </div>

      <Card>
        <Table<Route>
          columns={columns}
          data={routes || []}
          loading={isLoading}
          emptyMessage={isError ? 'Failed to load routes' : 'No routes defined yet.'}
          keyExtractor={(r) => r.id}
        />
      </Card>

      {/* Add Route Modal */}
      <Modal
        isOpen={isAddModalOpen}
        onClose={() => {
          setIsAddModalOpen(false);
          resetForm();
        }}
        title="Create New Route"
      >
        <form onSubmit={handleCreateRoute} className="space-y-4">
          {formError && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 rounded-lg text-sm">
              {formError}
            </div>
          )}
          <Input label="Route Name" placeholder="e.g. Majestic to Electronic City" value={name} onChange={(e) => setName(e.target.value)} required />
          <Input label="Route Code" placeholder="e.g. 356-CW" value={code} onChange={(e) => setCode(e.target.value)} required />
          <Input label="Description (Optional)" placeholder="e.g. Express via Silk Board" value={description} onChange={(e) => setDescription(e.target.value)} />

          <div className="border-t pt-4 mt-2">
            <h4 className="text-sm font-semibold text-gray-800 mb-2 flex items-center gap-1.5">
              <MapPin size={16} className="text-primary-600" /> Origin Stop
            </h4>
            <Input label="Stop Name" placeholder="e.g. Majestic Bus Station" value={stop1Name} onChange={(e) => setStop1Name(e.target.value)} required />
            <div className="grid grid-cols-2 gap-2 mt-2">
              <Input label="Latitude" value={stop1Lat} onChange={(e) => setStop1Lat(e.target.value)} />
              <Input label="Longitude" value={stop1Lng} onChange={(e) => setStop1Lng(e.target.value)} />
            </div>
          </div>

          <div className="border-t pt-4 mt-2">
            <h4 className="text-sm font-semibold text-gray-800 mb-2 flex items-center gap-1.5">
              <MapPin size={16} className="text-red-600" /> Destination Stop
            </h4>
            <Input label="Stop Name" placeholder="e.g. Electronic City Toll Gate" value={stop2Name} onChange={(e) => setStop2Name(e.target.value)} required />
            <div className="grid grid-cols-2 gap-2 mt-2">
              <Input label="Latitude" value={stop2Lat} onChange={(e) => setStop2Lat(e.target.value)} />
              <Input label="Longitude" value={stop2Lng} onChange={(e) => setStop2Lng(e.target.value)} />
            </div>
          </div>

          <div className="flex justify-end gap-3 pt-4 border-t">
            <Button type="button" variant="secondary" onClick={() => setIsAddModalOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" variant="primary" loading={createRouteMutation.isPending}>
              Create Route
            </Button>
          </div>
        </form>
      </Modal>

      {/* Route Details / Stops Modal */}
      <Modal
        isOpen={isDetailsModalOpen}
        onClose={() => setIsDetailsModalOpen(false)}
        title={selectedRoute ? `Stops for Route ${selectedRoute.code}` : 'Route Stops'}
      >
        <div className="space-y-4">
          <div className="p-3 bg-gray-50 rounded-lg">
            <p className="font-semibold text-gray-900">{selectedRoute?.name}</p>
            <p className="text-xs text-gray-500">{selectedRoute?.description || 'No description provided'}</p>
          </div>

          <div className="space-y-3">
            <h4 className="text-sm font-semibold text-gray-700">Sequence of Stops:</h4>
            {selectedRoute?.stops && selectedRoute.stops.length > 0 ? (
              <div className="relative pl-6 border-l-2 border-primary-300 space-y-4 my-2">
                {selectedRoute.stops.map((rs, idx) => (
                  <div key={rs.id} className="relative">
                    <span className="absolute -left-[31px] top-0 w-5 h-5 rounded-full bg-primary-600 text-white text-xs flex items-center justify-center font-bold">
                      {idx + 1}
                    </span>
                    <p className="text-sm font-medium text-gray-900">{rs.stop?.name}</p>
                    <p className="text-xs text-gray-500">
                      Lat: {rs.stop?.latitude}, Lng: {rs.stop?.longitude} | Fare from start: ₹{rs.fareFromStart ?? 0}
                    </p>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-gray-400 italic">No stops recorded for this route.</p>
            )}
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default RoutesPage;
