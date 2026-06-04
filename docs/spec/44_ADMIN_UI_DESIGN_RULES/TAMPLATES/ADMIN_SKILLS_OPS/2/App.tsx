/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import SkillTree from './components/SkillTree';
import Dashboard from './components/Dashboard';

export default function App() {
  return (
    <div className="min-h-screen bg-surface text-on-surface font-sans selection:bg-primary-container selection:text-primary-content flex">
      <SkillTree />
      <Dashboard />
    </div>
  );
}


