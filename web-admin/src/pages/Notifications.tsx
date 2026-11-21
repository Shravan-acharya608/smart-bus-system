import React, { useState } from 'react';
import { Bell, Check, Trash2, AlertTriangle, Info, CheckCircle2 } from 'lucide-react';
import Card from '../components/ui/Card';
import Button from '../components/ui/Button';

interface SystemNotification {
  id: string;
  title: string;
  message: string;
  type: 'INFO' | 'WARNING' | 'SUCCESS';
  timestamp: string;
  read: boolean;
}

const initialNotifications: SystemNotification[] = [
  {
    id: '1',
    title: 'New Trip Run Initiated',
    message: 'Bus M101 has commenced its scheduled run on Central to Airport Express.',
    type: 'SUCCESS',
    timestamp: 'Just now',
    read: false,
  },
  {
    id: '2',
    title: 'GPS Telemetry Active',
    message: 'Live vehicle coordinates transmitting smoothly across all active units.',
    type: 'INFO',
    timestamp: '10 minutes ago',
    read: false,
  },
  {
    id: '3',
    title: 'Fleet Advisory',
    message: 'Vehicle maintenance schedule due for MC-BUS-001 within 300 km.',
    type: 'WARNING',
    timestamp: '2 hours ago',
    read: true,
  },
];

const Notifications: React.FC = () => {
  const [notifications, setNotifications] = useState<SystemNotification[]>(initialNotifications);

  const markAllRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
  };

  const clearAll = () => {
    setNotifications([]);
  };

  const getIcon = (type: SystemNotification['type']) => {
    switch (type) {
      case 'WARNING':
        return <AlertTriangle size={18} className="text-amber-500 flex-shrink-0" />;
      case 'SUCCESS':
        return <CheckCircle2 size={18} className="text-green-500 flex-shrink-0" />;
      default:
        return <Info size={18} className="text-primary-500 flex-shrink-0" />;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-gray-900">Notifications & Alerts</h2>
          <p className="text-sm text-gray-500">Live operational events, vehicle status, and system alerts</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" onClick={markAllRead}>
            <Check size={14} className="mr-1.5" /> Mark All Read
          </Button>
          <Button variant="secondary" size="sm" onClick={clearAll}>
            <Trash2 size={14} className="mr-1.5" /> Clear All
          </Button>
        </div>
      </div>

      {notifications.length === 0 ? (
        <Card>
          <div className="text-center py-12">
            <Bell size={40} className="mx-auto text-gray-300 mb-3" />
            <p className="text-base font-semibold text-gray-700">No new notifications</p>
            <p className="text-xs text-gray-400 mt-1">You are all caught up!</p>
          </div>
        </Card>
      ) : (
        <div className="space-y-3">
          {notifications.map((n) => (
            <Card key={n.id} className={n.read ? 'opacity-70 bg-white' : 'bg-primary-50/20 border-primary-200'}>
              <div className="flex items-start gap-3.5">
                <div className="mt-0.5">{getIcon(n.type)}</div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <h4 className="text-sm font-bold text-gray-900">{n.title}</h4>
                    <span className="text-xs text-gray-400 font-medium whitespace-nowrap">{n.timestamp}</span>
                  </div>
                  <p className="text-xs text-gray-600 mt-1 leading-relaxed">{n.message}</p>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
};

export default Notifications;
