import React from 'react';
import { Menu, Bell } from 'lucide-react';
import { useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { RoleBadge } from '../ui/Badge';
import { UserRole } from '../../types';

interface HeaderProps {
  onMenuClick: () => void;
  notificationCount?: number;
}

const routeTitles: Record<string, string> = {
  '/': 'Dashboard',
  '/buses': 'Bus Management',
  '/routes': 'Route Management',
  '/trips': 'Trip Management',
  '/staff': 'Staff Management',
  '/tickets': 'Ticket Management',
  '/live-map': 'Live Fleet Map',
  '/reports': 'Reports & Analytics',
  '/notifications': 'Notifications',
  '/settings': 'Settings',
};

const Header: React.FC<HeaderProps> = ({ onMenuClick, notificationCount = 0 }) => {
  const location = useLocation();
  const { user } = useAuthStore();
  const title = routeTitles[location.pathname] || 'SmartBus Admin';

  return (
    <header className="sticky top-0 z-30 h-16 bg-white border-b border-gray-200 flex items-center px-4 lg:px-6 gap-4">
      {/* Mobile menu toggle */}
      <button
        onClick={onMenuClick}
        className="lg:hidden p-2 rounded-lg text-gray-500 hover:bg-gray-100 transition-colors"
        aria-label="Open menu"
      >
        <Menu size={22} />
      </button>

      {/* Page title */}
      <h1 className="flex-1 text-lg font-semibold text-gray-800">{title}</h1>

      {/* Right section */}
      <div className="flex items-center gap-3">
        {/* Notification bell */}
        <button
          className="relative p-2 rounded-lg text-gray-500 hover:bg-gray-100 transition-colors"
          aria-label="Notifications"
        >
          <Bell size={20} />
          {notificationCount > 0 && (
            <span className="absolute top-1 right-1 w-4 h-4 bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center">
              {notificationCount > 9 ? '9+' : notificationCount}
            </span>
          )}
        </button>

        {/* User info */}
        <div className="flex items-center gap-2">
          {user?.role && <RoleBadge role={user.role as UserRole} />}
          <span className="hidden sm:block text-sm font-medium text-gray-700">
            {user?.firstName} {user?.lastName}
          </span>
          <div className="w-8 h-8 rounded-full bg-primary-600 flex items-center justify-center text-white font-semibold text-xs">
            {user?.firstName?.[0]}
            {user?.lastName?.[0]}
          </div>
        </div>
      </div>
    </header>
  );
};

export default Header;
