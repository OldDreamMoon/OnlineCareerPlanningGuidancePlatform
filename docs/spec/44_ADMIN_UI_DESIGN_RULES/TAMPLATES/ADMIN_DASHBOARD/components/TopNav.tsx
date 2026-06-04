import { Bell, Settings, HelpCircle } from 'lucide-react';

export default function TopNav() {
  return (
    <header className="sticky top-0 z-50 flex justify-between items-center w-full px-8 h-16 bg-white/70 backdrop-blur-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)]">
      <div className="flex items-center gap-8">
        <span className="text-2xl font-black tracking-tighter text-primary font-headline">Luminous Admin</span>
        <nav className="hidden lg:flex gap-6 h-full">
          {['Dashboard', 'Analytics', 'Governance', 'Compliance'].map((item, i) => (
            <a
              key={item}
              href="#"
              className={`font-semibold text-sm h-16 flex items-center transition-colors ${
                i === 0 
                  ? 'text-primary border-b-2 border-primary' 
                  : 'text-slate-500 hover:text-primary'
              }`}
            >
              {item}
            </a>
          ))}
        </nav>
      </div>

      <div className="flex items-center gap-4">
        <div className="relative group cursor-pointer">
          <Bell size={20} className="text-slate-500 group-hover:text-primary transition-colors" />
          <span className="absolute -top-1 -right-1 w-2 h-2 bg-error rounded-full"></span>
        </div>
        <Settings size={20} className="text-slate-500 cursor-pointer hover:text-primary transition-colors" />
        <HelpCircle size={20} className="text-slate-500 cursor-pointer hover:text-primary transition-colors" />
        
        <div className="h-8 w-8 rounded-full overflow-hidden border-2 border-primary-container shadow-sm">
          <img 
            src="https://lh3.googleusercontent.com/aida-public/AB6AXuCL7iUsqIBRmy2Z92mQAYqQP5O_IOih_MK-gRmDl2eUM98IiYcNakpWNyo7SsCA-RG3BhP_pIzpVleJ7DNDU81mB9rT4hlu8ENpHrCqG_ZwiA52FXsd2MQya1iuHEVic_873_9DAvc8EwTztqV48Evz6V50p4XGSo2DTOGgdQ01f14b--eSwLf6j3sCSEzqXynoHTt6BV9rH-akDkJQQDJ4WJp34rVfdxc83bLdVpJrtg2tEy-mm3ep4b7m8BTyxXVRQ_suGDeXkio" 
            alt="User profile" 
            referrerPolicy="no-referrer"
            className="w-full h-full object-cover"
          />
        </div>
      </div>
    </header>
  );
}
