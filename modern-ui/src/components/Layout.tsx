import React, { useState } from 'react';
import { Activity, BookOpen, FileText, HelpCircle, PlayCircle, Table2 } from 'lucide-react';
import { cn } from '../lib/utils';
import { HelpModal } from './HelpModal';

interface LayoutProps {
    children: React.ReactNode;
    activeTab: 'sessions' | 'products' | 'tests' | 'logs' | 'dictionary';
    onTabChange: (tab: 'sessions' | 'products' | 'tests' | 'logs' | 'dictionary') => void;
}

const tabs = [
   { id: 'sessions' as const, icon: Activity, label: 'Sessions' },
   { id: 'products' as const, icon: Table2, label: 'Products' },
   { id: 'tests' as const, icon: PlayCircle, label: 'Tests' },
   { id: 'logs' as const, icon: FileText, label: 'Logs' },
   { id: 'dictionary' as const, icon: BookOpen, label: 'Dictionary' },
];

export const Layout: React.FC<LayoutProps> = ({ children, activeTab, onTabChange }) => {
    const [helpOpen, setHelpOpen] = useState(false);

    return (
        <div className="fixed inset-0 bg-[#0f111a] text-slate-50 flex flex-col">
            {/* Header */}
          <header className="border-b border-white/5 bg-[#0f111a]/80 backdrop-blur-xl z-20 shrink-0">
                <div className="w-full px-6 h-14 flex items-center justify-between">
                    {/* Logo & Brand */}
                   <div className="flex items-center gap-3">
                       <img src="fixtestdriver.svg" alt="FixTestDriver Logo" className="w-12 h-12 shadow-blue-600/50" />
                       <div>
                            <h1 className="text-base font-semibold tracking-tight leading-none">
                               FixTestDriver
                            </h1>
                            <span className="text-[10px] text-surface-500 font-medium">v2.0</span>
                        </div>
                    </div>

                    {/* Tab Navigation */}
                   <nav className="flex gap-1 bg-white/[0.03] p-1 rounded-xl border border-white/5">
                        {tabs.map(({ id, icon: Icon, label }) => (
                            <button
                                key={id}
                               onClick={() => onTabChange(id)}
                                className={cn(
                                    'flex items-center gap-2 px-4 py-1.5 rounded-lg transition-all duration-200 text-sm font-medium',
                                    activeTab === id
                                        ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                                       : 'text-surface-400 hover:text-slate-200 hover:bg-white/[0.03]'
                                )}
                            >
                               <Icon size={16} />
                              <span className="hidden sm:inline">{label}</span>
                            </button>
                        ))}
                    </nav>

                    {/* Right section */}
                  <div className="flex items-center gap-2">
                        <button
                            onClick={() => setHelpOpen(true)}
                            className="text-slate-400 hover:text-slate-100 transition-colors p-1.5 rounded-lg hover:bg-white/5"
                            title="Help & Reference"
                        >
                            <HelpCircle size={16} />
                        </button>
                       <div className="w-2 h-2 rounded-full bg-emerald-500 shadow-sm shadow-emerald-500/50" title="Connected" />
                   </div>
                </div>
            </header>

            <HelpModal isOpen={helpOpen} onClose={() => setHelpOpen(false)} />

            {/* Main Content Area */}
          <main className="flex-1 w-full p-6 overflow-y-auto">
                <div className="h-full animate-fade-in">
                    {children}
                </div>
            </main>
         </div>
     );
};
