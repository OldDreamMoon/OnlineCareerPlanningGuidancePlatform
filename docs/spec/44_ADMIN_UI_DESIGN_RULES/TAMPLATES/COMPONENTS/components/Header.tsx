export default function Header() {
  return (
    <header className="w-full bg-white/70 backdrop-blur-xl sticky top-0 z-50 border-b border-outline-variant/15">
      <div className="max-w-7xl mx-auto px-8 h-20 flex justify-between items-center">
        <div className="flex items-center gap-4">
          <div className="w-10 h-10 bg-primary rounded-lg flex items-center justify-center shadow-lg shadow-primary/20">
            <span className="material-symbols-outlined text-white" style={{ fontVariationSettings: "'FILL' 1" }}>deployed_code</span>
          </div>
          <div>
            <h1 className="font-headline text-xl font-extrabold tracking-tight text-on-surface">Indigo Ether Specimen</h1>
            <p className="text-[10px] uppercase tracking-[0.2em] text-outline font-bold">UI Component Library V1.0.4</p>
          </div>
        </div>
        <div className="hidden md:flex items-center gap-8">
          <nav className="flex gap-6 text-sm font-semibold text-on-surface-variant">
            <a className="text-primary border-b-2 border-primary pb-1" href="#">Specimen</a>
            <a className="hover:text-primary transition-colors" href="#">Documentation</a>
            <a className="hover:text-primary transition-colors" href="#">Themes</a>
          </nav>
          <button className="bg-on-surface text-surface py-2 px-6 rounded-lg text-sm font-bold hover:scale-[1.02] active:scale-95 transition-all">
            GitHub Repo
          </button>
        </div>
      </div>
    </header>
  );
}
