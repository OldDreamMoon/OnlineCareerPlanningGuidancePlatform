import { LucideIcon } from 'lucide-react';

interface StatCardProps {
  icon: LucideIcon;
  label: string;
  value: string;
  trend?: string;
  trendColor?: 'primary' | 'error' | 'tertiary';
  isCritical?: boolean;
  progress?: number;
}

export default function StatCard({ 
  icon: Icon, 
  label, 
  value, 
  trend, 
  trendColor = 'primary', 
  isCritical,
  progress 
}: StatCardProps) {
  const colorClasses = {
    primary: 'text-primary bg-primary/10',
    error: 'text-error bg-error-container/20',
    tertiary: 'text-tertiary bg-tertiary-container/20',
  };

  return (
    <div className={`glass-card p-6 rounded-3xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] hover:-translate-y-1 transition-all ${isCritical ? 'border-l-4 border-error' : ''}`}>
      <div className={`w-12 h-12 rounded-2xl flex items-center justify-center mb-4 ${colorClasses[trendColor]}`}>
        <Icon size={24} fill="currentColor" fillOpacity={0.2} />
      </div>
      <h3 className="text-on-surface-variant font-semibold text-sm uppercase tracking-wider">{label}</h3>
      <div className="flex items-baseline gap-2 mt-2">
        <span className={`text-3xl font-black font-headline ${isCritical ? 'text-error' : 'text-on-surface'}`}>{value}</span>
        {trend && (
          <span className={`text-xs font-bold text-${trendColor}`}>{trend}</span>
        )}
        {progress !== undefined && (
          <div className="w-16 h-2 bg-surface-container-low rounded-full overflow-hidden ml-auto">
            <div 
              className="bg-secondary h-full transition-all duration-1000" 
              style={{ width: `${progress}%` }}
            />
          </div>
        )}
      </div>
    </div>
  );
}
