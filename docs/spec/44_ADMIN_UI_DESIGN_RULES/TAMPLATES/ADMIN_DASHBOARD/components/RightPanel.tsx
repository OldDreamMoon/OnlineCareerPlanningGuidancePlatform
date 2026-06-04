import { User, Briefcase, MoreVertical, ShieldCheck } from 'lucide-react';

export function UserReports() {
  const reports = [
    {
      type: 'User: Alex_99',
      icon: User,
      iconBg: 'bg-primary-container/20',
      iconColor: 'text-primary',
      reason: 'Policy violation (Spam)',
      time: '2 mins ago',
      status: 'URGENT',
      statusClass: 'bg-error-container/20 text-error',
    },
    {
      type: 'Project: Cloud Migration',
      icon: Briefcase,
      iconBg: 'bg-tertiary-container/20',
      iconColor: 'text-tertiary',
      reason: 'Intellectual Property',
      time: '45 mins ago',
      status: 'QUEUED',
      statusClass: 'bg-surface-container-high text-on-surface-variant',
    },
  ];

  return (
    <section className="glass-card rounded-3xl overflow-hidden shadow-sm">
      <div className="p-6 border-b border-surface-container-low flex justify-between items-center">
        <h2 className="text-xl font-bold font-headline">Pending User Reports</h2>
        <button className="text-primary text-sm font-bold hover:underline">View All</button>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left">
          <thead className="bg-surface-container-low">
            <tr>
              {['Target Type', 'Reason', 'Reported At', 'Status', 'Action'].map((header) => (
                <th key={header} className={`px-6 py-4 text-xs font-black text-on-surface-variant uppercase tracking-widest ${header === 'Action' ? 'text-right' : ''}`}>
                  {header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-surface-container-low">
            {reports.map((report, i) => (
              <tr key={i} className="hover:bg-slate-50/50 transition-colors">
                <td className="px-6 py-4">
                  <div className="flex items-center gap-3">
                    <div className={`w-8 h-8 rounded-lg flex items-center justify-center ${report.iconBg} ${report.iconColor}`}>
                      <report.icon size={18} />
                    </div>
                    <span className="text-sm font-bold">{report.type}</span>
                  </div>
                </td>
                <td className="px-6 py-4 text-sm">{report.reason}</td>
                <td className="px-6 py-4 text-sm text-on-surface-variant">{report.time}</td>
                <td className="px-6 py-4">
                  <span className={`px-3 py-1 text-[10px] font-black rounded-full ${report.statusClass}`}>
                    {report.status}
                  </span>
                </td>
                <td className="px-6 py-4 text-right">
                  <button className="p-2 hover:bg-surface-container rounded-lg text-on-surface-variant transition-colors">
                    <MoreVertical size={18} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

export function RightPanel() {
  return (
    <aside className="space-y-8">
      {/* AI Cost Models */}
      <div className="glass-card p-6 rounded-3xl shadow-sm">
        <h3 className="text-lg font-bold font-headline mb-4">AI Cost Top Models</h3>
        <div className="space-y-4">
          {[
            { id: '01', name: 'GPT-4 Omni', cost: '$4,203', bg: 'bg-primary', text: 'text-white' },
            { id: '02', name: 'Claude 3.5 Sonnet', cost: '$3,120', bg: 'bg-primary-container', text: 'text-on-primary-container' },
            { id: '03', name: 'Llama 3 70B', cost: '$1,450', bg: 'bg-surface-container-high', text: 'text-on-surface-variant' },
          ].map((model) => (
            <div key={model.id} className="flex justify-between items-center">
              <div className="flex items-center gap-3">
                <span className={`w-6 h-6 flex items-center justify-center rounded text-[10px] font-bold ${model.bg} ${model.text}`}>
                  {model.id}
                </span>
                <span className="text-sm font-semibold">{model.name}</span>
              </div>
              <span className="text-sm font-black">{model.cost}</span>
            </div>
          ))}
        </div>
        <button className="w-full mt-6 py-2 rounded-xl bg-surface-container-low text-primary text-xs font-black hover:bg-surface-container-high transition-colors">
          COST BREAKDOWN
        </button>
      </div>

      {/* Payment Health */}
      <div className="glass-card p-6 rounded-3xl border-l-4 border-secondary shadow-sm">
        <h3 className="text-lg font-bold font-headline mb-4">Payment Health</h3>
        <div className="space-y-4">
          {[
            { label: 'Refund Rate', value: '0.4%', color: 'text-secondary' },
            { label: 'After-sales Tickets', value: '24' },
            { label: 'Dispute Resolution', value: '94%' },
          ].map((item) => (
            <div key={item.label} className="flex items-center justify-between">
              <span className="text-xs font-bold text-on-surface-variant">{item.label}</span>
              <span className={`text-sm font-black ${item.color || ''}`}>{item.value}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Supply & Demand */}
      <div className="glass-card p-6 rounded-3xl shadow-sm">
        <h3 className="text-lg font-bold font-headline mb-4">Supply & Demand</h3>
        <div className="relative h-24 flex items-end justify-between gap-2 px-2">
          {[60, 45, 80, 95, 70, 50].map((h, i) => (
            <div 
              key={i} 
              className={`w-4 rounded-t-lg transition-all duration-1000 ${i === 3 ? 'bg-primary' : 'bg-primary-container'}`} 
              style={{ height: `${h}%` }}
            ></div>
          ))}
        </div>
        <div className="mt-4 flex justify-between items-center">
          <span className="text-[10px] font-bold text-on-surface-variant uppercase">Market Liquidity</span>
          <span className="text-sm font-black text-primary">EXCELLENT</span>
        </div>
      </div>

      {/* Domain Risks */}
      <div className="bg-inverse-surface p-6 rounded-3xl shadow-xl text-white">
        <div className="flex items-center gap-3 mb-4">
          <div className="text-tertiary">
            <ShieldCheck size={24} fill="currentColor" fillOpacity={0.2} />
          </div>
          <h3 className="text-lg font-bold font-headline">Domain Risks</h3>
        </div>
        <div className="space-y-3">
          {[
            { domain: 'Compliance', level: 'MODERATE', levelColor: 'bg-tertiary/20 text-tertiary', desc: 'New EU regulations effective in 12 days. Assessment required.' },
            { domain: 'Security', level: 'LOW', levelColor: 'bg-error/20 text-error', desc: 'Unauthorized API calls detected in Sandbox environment.' },
          ].map((risk) => (
            <div key={risk.domain} className="p-3 bg-white/10 rounded-xl border border-white/5">
              <div className="flex justify-between items-center mb-1">
                <span className="text-[10px] font-bold text-tertiary tracking-widest uppercase">{risk.domain}</span>
                <span className={`text-[10px] px-1 rounded font-bold ${risk.levelColor}`}>{risk.level}</span>
              </div>
              <p className="text-xs leading-relaxed text-slate-300">{risk.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </aside>
  );
}
