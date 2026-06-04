import { Sidebar } from './components/Sidebar';
import { TopNav } from './components/TopNav';
import { SensitiveWords } from './components/SensitiveWords';

export default function App() {
  return (
    <div className="flex min-h-screen selection:bg-primary-container">
      <Sidebar />
      <main className="flex-1 flex flex-col min-w-0 bg-surface">
        <TopNav />
        <SensitiveWords />
      </main>
    </div>
  );
}
