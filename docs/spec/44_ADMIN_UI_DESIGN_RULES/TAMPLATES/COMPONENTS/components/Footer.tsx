export default function Footer() {
  return (
    <footer className="w-full py-16 px-8 bg-white border-t border-outline-variant/15 mt-24">
      <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-8">
        <div className="flex items-center gap-4 opacity-50 grayscale hover:grayscale-0 transition-all">
          <div className="w-8 h-8 bg-on-surface rounded-lg flex items-center justify-center">
            <span className="material-symbols-outlined text-white text-sm">deployed_code</span>
          </div>
          <span className="font-headline font-black text-sm uppercase tracking-widest">Indigo Ether</span>
        </div>
        <p className="text-[10px] font-bold text-outline uppercase tracking-[0.2em]">© 2024 INDIGO ETHER UI SYSTEM. BUILT FOR LUMINOUS DASHBOARDS.</p>
        <div className="flex gap-6">
          <a className="text-xs font-bold text-outline uppercase hover:text-primary transition-colors" href="#">Privacy Policy</a>
          <a className="text-xs font-bold text-outline uppercase hover:text-primary transition-colors" href="#">License</a>
          <a className="text-xs font-bold text-outline uppercase hover:text-primary transition-colors" href="#">GitHub</a>
        </div>
      </div>
    </footer>
  );
}
