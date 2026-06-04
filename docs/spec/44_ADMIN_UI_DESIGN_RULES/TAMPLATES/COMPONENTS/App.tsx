/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import Header from './components/Header';
import Intro from './components/Intro';
import ButtonsSection from './components/ButtonsSection';
import FormsSection from './components/FormsSection';
import FeedbackSection from './components/FeedbackSection';
import DataDisplaySection from './components/DataDisplaySection';
import NavigationSection from './components/NavigationSection';
import Footer from './components/Footer';

export default function App() {
  return (
    <div className="min-h-screen">
      <Header />
      <main className="max-w-7xl mx-auto px-8 py-12 space-y-24">
        <Intro />
        <ButtonsSection />
        <FormsSection />
        <FeedbackSection />
        <DataDisplaySection />
        <NavigationSection />
      </main>
      <Footer />
    </div>
  );
}
