import React from 'react';

export function TopNav() {
  return (
    <header className="w-full sticky top-0 z-50 bg-white/70 backdrop-blur-xl shadow-[0px_10px_40px_rgba(44,47,49,0.06)] flex justify-between items-center px-6 py-3">
      <div className="flex items-center gap-4">
        <div className="md:hidden">
          <span className="material-symbols-outlined text-on-surface">menu</span>
        </div>
        <span className="text-xl font-bold tracking-tighter text-cyan-600">Luminous Mod</span>
      </div>
      <div className="flex-1 max-w-xl mx-8 hidden lg:block">
        <div className="relative group">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline group-focus-within:text-primary transition-colors">search</span>
          <input className="w-full bg-surface-container-low border-none rounded-full pl-10 pr-4 py-2 text-sm focus:ring-2 focus:ring-primary focus:bg-white transition-all" placeholder="Search system wide..." type="text" />
        </div>
      </div>
      <div className="flex items-center gap-4">
        <button className="w-10 h-10 flex items-center justify-center rounded-full text-slate-500 hover:bg-slate-50 active:scale-95 transition-all">
          <span className="material-symbols-outlined">notifications</span>
        </button>
        <button className="w-10 h-10 flex items-center justify-center rounded-full text-slate-500 hover:bg-slate-50 active:scale-95 transition-all">
          <span className="material-symbols-outlined">help_outline</span>
        </button>
        <div className="h-8 w-px bg-slate-200 mx-2"></div>
        <img alt="Moderator Profile Avatar" className="w-8 h-8 rounded-full border-2 border-white shadow-sm" src="https://lh3.googleusercontent.com/aida-public/AB6AXuCM--J85RINWNxcZsvKlH3tDjbT7SvsmrxsvevXFlN1Ux1qQt_00n66bgPe4YQVKrApMhn4drUfKojHRcMA9VaSAEgQiOr3L7dbHJ5DEHJ45_kEoxg0mqupUpZFirs-OpJB8jNcBnQ5_hK7CGzVxv6l4pBlMZM-48NAfrNoLsh57WINrz0MT70scl6WaIO9QRA8l3f8IupGhFqXobynw5mwJ119qdxmwz1Mt0gAAO1CIV_n8HBdSdNr6YZNkBXu2KjUIulOOBcuojM" />
      </div>
    </header>
  );
}
