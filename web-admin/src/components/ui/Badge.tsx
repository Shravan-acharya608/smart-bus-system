import React from 'react';
import { BusStatus, TripStatus, TicketStatus, PaymentStatus, UserRole } from '../../types';

type BadgeColor = 'green' | 'gray' | 'yellow' | 'red' | 'blue' | 'purple' | 'orange';

interface BadgeProps {
  children: React.ReactNode;
  color?: BadgeColor;
  className?: string;
}

const colorMap: Record<BadgeColor, string> = {
  green: 'bg-green-100 text-green-800',
  gray: 'bg-gray-100 text-gray-700',
  yellow: 'bg-yellow-100 text-yellow-800',
  red: 'bg-red-100 text-red-700',
  blue: 'bg-blue-100 text-blue-800',
  purple: 'bg-purple-100 text-purple-800',
  orange: 'bg-orange-100 text-orange-800',
};

export const Badge: React.FC<BadgeProps> = ({ children, color = 'gray', className = '' }) => (
  <span
    className={[
      'inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium',
      colorMap[color],
      className,
    ].join(' ')}
  >
    {children}
  </span>
);

// Convenience components for domain-specific statuses
export const BusStatusBadge: React.FC<{ status: BusStatus }> = ({ status }) => {
  const colorMap: Record<BusStatus, BadgeColor> = {
    ACTIVE: 'green',
    INACTIVE: 'gray',
    MAINTENANCE: 'yellow',
  };
  return <Badge color={colorMap[status]}>{status}</Badge>;
};

export const TripStatusBadge: React.FC<{ status: TripStatus }> = ({ status }) => {
  const colorMap: Record<TripStatus, BadgeColor> = {
    SCHEDULED: 'blue',
    ACTIVE: 'green',
    COMPLETED: 'gray',
    CANCELLED: 'red',
  };
  return <Badge color={colorMap[status]}>{status}</Badge>;
};

export const TicketStatusBadge: React.FC<{ status: TicketStatus }> = ({ status }) => {
  const colorMap: Record<TicketStatus, BadgeColor> = {
    PENDING: 'yellow',
    PAID: 'green',
    USED: 'gray',
    CANCELLED: 'red',
    EXPIRED: 'orange',
    HELD: 'orange',
    REFUNDED: 'blue',
  };
  return <Badge color={colorMap[status] || 'gray'}>{status}</Badge>;
};

export const PaymentStatusBadge: React.FC<{ status: PaymentStatus }> = ({ status }) => {
  const colorMap: Record<PaymentStatus, BadgeColor> = {
    PENDING: 'yellow',
    SUCCESS: 'green',
    FAILED: 'red',
    REFUNDED: 'blue',
    PARTIAL_REFUND: 'orange',
  };
  return <Badge color={colorMap[status] || 'gray'}>{status}</Badge>;
};

export const RoleBadge: React.FC<{ role: UserRole | string }> = ({ role }) => {
  const colorMap: Record<string, BadgeColor> = {
    ADMIN: 'purple',
    BUS_OWNER: 'blue',
    DRIVER: 'green',
    CONDUCTOR: 'orange',
    PASSENGER: 'gray',
  };
  return <Badge color={colorMap[role] || 'gray'}>{role}</Badge>;
};

export default Badge;
