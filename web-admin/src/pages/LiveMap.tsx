import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Bus, MapPin, AlertTriangle, Users, Compass, RefreshCw } from 'lucide-react';
import { apiClient } from '../lib/axios';
import { ApiResponse } from '../types';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';
import LeafletFleetMap from '../components/LeafletFleetMap';

interface FleetBus {
  tripId: string;
  busId: string;
  busNumber: string;
  registrationNo: string;
  capacity: number;
  currentOnboard: number;
  routeName: string;
  routeCode: string;
  driverName: string;
  conductorName: string;
  hasInconsistency: boolean;
  lastLocation: {
    latitude: number;
    longitude: number;
    speed?: number;
    heading?: number;
    timestamp: string;
    isStale: boolean;
    ageSeconds: number;
  } | null;
}

interface DashboardResponse {
  fleetStatus: FleetBus[];
}

interface IncidentItem {
  id: string;
  tripId: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  description: string;
  delayMinutes: number;
  isResolved: boolean;
  trip?: {
    bus?: { busNumber: string };
    route?: { name: string };
  };
}

const LiveMap: React.FC = () => {
  const [selectedBus, setSelectedBus] = useState<FleetBus | null>(null);

  const { data, isLoading, refetch, isFetching } = useQuery<FleetBus[]>({
    queryKey: ['live-fleet'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<DashboardResponse>>('/admin/dashboard');
      return res.data.data?.fleetStatus || [];
    },
    refetchInterval: 15000, // Poll every 15s for fresh telemetry
  });

  const { data: incidents, refetch: refetchIncidents } = useQuery<IncidentItem[]>({
    queryKey: ['active-incidents'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<IncidentItem[]>>('/incidents?isResolved=false');
      return res.data.data || [];
    },
    refetchInterval: 10000,
  });

  const fleet = data || [];

  useEffect(() => {
    if (fleet.length > 0 && !selectedBus) {
      setSelectedBus(fleet[0]);
    }
  }, [fleet, selectedBus]);

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Live Fleet Tracking & Telemetry</h2>
          <p className="text-sm text-gray-500">Real-time GPS coordinates, vehicle speeds, and onboard passenger loads</p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => { refetch(); refetchIncidents(); }} loading={isFetching}>
          <RefreshCw size={14} className="mr-1.5" /> Refresh Telemetry
        </Button>
      </div>

      {/* Emergency Incidents & Delays Banner */}
      {incidents && incidents.length > 0 && (
        <div className="space-y-2">
          {incidents.map((inc) => (
            <div
              key={inc.id}
              className={`p-4 rounded-xl border flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 shadow-sm ${
                inc.severity === 'CRITICAL' || inc.severity === 'HIGH'
                  ? 'bg-red-50 border-red-300 text-red-900'
                  : 'bg-amber-50 border-amber-300 text-amber-900'
              }`}
            >
              <div className="flex items-center gap-3">
                <AlertTriangle className={inc.severity === 'CRITICAL' ? 'text-red-600 animate-bounce' : 'text-amber-600'} size={24} />
                <div>
                  <div className="flex items-center gap-2">
                    <span className="font-bold text-sm">
                      🚨 BUS {inc.trip?.bus?.busNumber || 'ALERT'} — {inc.severity} SEVERITY
                    </span>
                    {inc.delayMinutes > 0 && (
                      <span className="text-xs bg-red-200 text-red-800 font-bold px-2 py-0.5 rounded-full">
                        +{inc.delayMinutes} min delay
                      </span>
                    )}
                  </div>
                  <p className="text-xs mt-0.5 opacity-90">{inc.description}</p>
                </div>
              </div>

              <Button
                size="sm"
                variant="secondary"
                onClick={async () => {
                  try {
                    await apiClient.patch(`/incidents/${inc.id}/resolve`, { resolvedNote: 'Resolved by Admin' });
                    refetchIncidents();
                  } catch (e) {}
                }}
              >
                Mark Resolved
              </Button>
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Active Bus List Sidebar */}
        <div className="lg:col-span-1 space-y-3">
          <h3 className="text-sm font-semibold text-gray-700 uppercase tracking-wider">
            Active Vehicles ({fleet.length})
          </h3>

          {isLoading ? (
            <div className="p-8 text-center text-gray-400">Loading active fleet...</div>
          ) : fleet.length === 0 ? (
            <Card>
              <div className="text-center py-6">
                <Bus size={32} className="mx-auto text-gray-300 mb-2" />
                <p className="text-sm font-medium text-gray-700">No active trips</p>
                <p className="text-xs text-gray-400 mt-1">Start a trip from Trips page to view live telemetry.</p>
              </div>
            </Card>
          ) : (
            fleet.map((bus) => {
              const isSelected = selectedBus?.busId === bus.busId;
              const isStale = bus.lastLocation?.isStale;

              return (
                <div
                  key={bus.tripId}
                  onClick={() => setSelectedBus(bus)}
                  className={`p-4 rounded-xl border transition-all cursor-pointer ${
                    isSelected
                      ? 'border-primary-500 bg-primary-50 shadow-sm'
                      : 'border-gray-200 bg-white hover:border-gray-300'
                  }`}
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-bold text-gray-900">{bus.busNumber}</span>
                        <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded font-mono">
                          {bus.routeCode}
                        </span>
                      </div>
                      <p className="text-xs text-gray-600 mt-1 font-medium">{bus.routeName}</p>
                    </div>

                    {isStale ? (
                      <span className="flex items-center gap-1 text-[11px] font-semibold text-amber-700 bg-amber-100 px-2 py-0.5 rounded-full">
                        <AlertTriangle size={11} /> Stale GPS
                      </span>
                    ) : bus.lastLocation ? (
                      <span className="flex items-center gap-1 text-[11px] font-semibold text-green-700 bg-green-100 px-2 py-0.5 rounded-full">
                        <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse" /> Live
                      </span>
                    ) : (
                      <span className="text-[11px] text-gray-400 bg-gray-100 px-2 py-0.5 rounded">
                        No Fix
                      </span>
                    )}
                  </div>

                  <div className="flex items-center justify-between text-xs text-gray-500 mt-3 pt-3 border-t border-gray-100">
                    <span className="flex items-center gap-1">
                      <Users size={12} className="text-primary-600" />
                      {bus.currentOnboard} / {bus.capacity} onboard
                    </span>
                    <span>Driver: {bus.driverName}</span>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Live Map Display Container */}
        <div className="lg:col-span-2">
          <Card className="h-full flex flex-col min-h-[550px]">
            {/* Header details */}
            <div className="flex flex-wrap items-center justify-between gap-2 pb-4 mb-4 border-b border-gray-100">
              <div>
                <h3 className="font-bold text-gray-900 text-lg">
                  {selectedBus ? `Bus ${selectedBus.busNumber} — ${selectedBus.routeName}` : 'Fleet Overview Map'}
                </h3>
                <p className="text-xs text-gray-500">
                  {selectedBus
                    ? `Driver: ${selectedBus.driverName} | Conductor: ${selectedBus.conductorName}`
                    : 'Showing real-time location of all active buses'}
                </p>
              </div>
              {selectedBus && (
                <div className="flex items-center gap-3">
                  <div className="text-right">
                    <p className="text-xs text-gray-400">Onboard Passengers</p>
                    <p className="font-bold text-primary-600 text-base">
                      {selectedBus.currentOnboard} / {selectedBus.capacity}
                    </p>
                  </div>
                </div>
              )}
            </div>

            {/* Stale Warning Banner */}
            {selectedBus?.lastLocation?.isStale && (
              <div className="mb-4 p-3 bg-amber-50 border border-amber-200 rounded-lg flex items-center gap-2.5 text-amber-800 text-xs">
                <AlertTriangle size={16} className="text-amber-600 flex-shrink-0" />
                <span>
                  <strong>Warning:</strong> GPS location was last updated {selectedBus.lastLocation.ageSeconds} seconds ago ({'>'} 5 minutes). Telemetry may not reflect real-time position.
                </span>
              </div>
            )}

            {/* Interactive OpenStreetMap Canvas */}
            <div className="flex-1 flex flex-col gap-4 min-h-[440px]">
              <LeafletFleetMap
                fleet={fleet}
                selectedBus={selectedBus}
                onSelectBus={(b) => setSelectedBus(b)}
              />

              {/* Telemetry info bar */}
              {selectedBus?.lastLocation && (
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-gray-50 border border-gray-200 p-3 rounded-lg text-xs">
                  <div>
                    <span className="text-gray-500">Speed:</span>
                    <p className="font-bold text-gray-900">{selectedBus.lastLocation.speed?.toFixed(1) || 0} km/h</p>
                  </div>
                  <div>
                    <span className="text-gray-500">Heading:</span>
                    <p className="font-bold text-gray-900 flex items-center gap-1">
                      <Compass size={12} /> {selectedBus.lastLocation.heading?.toFixed(0) || 0}°
                    </p>
                  </div>
                  <div>
                    <span className="text-gray-500">Coordinates:</span>
                    <p className="font-mono text-gray-800">
                      {selectedBus.lastLocation.latitude.toFixed(4)}, {selectedBus.lastLocation.longitude.toFixed(4)}
                    </p>
                  </div>
                  <div>
                    <span className="text-gray-500">Last Telemetry:</span>
                    <p className="text-gray-800">
                      {new Date(selectedBus.lastLocation.timestamp).toLocaleTimeString()}
                    </p>
                  </div>
                </div>
              )}
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};

export default LiveMap;
