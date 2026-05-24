import React from 'react';
import { Activity, PlayCircle, List, Settings } from 'lucide-react';
import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs));
}

interface LayoutProps {
    children: React.ReactNode;
    activeTab: 'sessions' | 'tests' | 'logs';
    onTabChange: (tab: 'sessions' | 'tests' | 'logs') => void;
}

export const Layout: React.FC<LayoutProps> = ({ children, activeTab, onTabChange }) => {
    return (
        <div className="min-h-screen w-full bg-slate-950 text-slate-50 flex flex-col">
            <header className="border-b border-slate-800 bg-slate-900/50 backdrop-blur-md sticky top-0 z-10">
                <div className="w-full px-6 h-16 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <div className="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center font-bold text-white">F</div>
                        <h1 className="text-xl font-bold tracking-tight">FixTestDriver <span className="text-slate-500 font-normal">v2.0</span></h1>
                    </div>
                    <nav className="flex gap-1 bg-slate-800/50 p-1 rounded-xl">
                        <button
                            onClick={() => onTabChange('sessions')}
                            className={cn(
                                "flex items-center gap-2 px-4 py-2 rounded-lg transition-all",
                                activeTab === 'sessions' ? "bg-blue-600 text-white shadow-lg" : "hover:bg-slate-700 text-slate-400"
                            )}
                        >
                            <Activity size={18} />
                            <span>Sessions</span>
                        </button>
                        <button
                            onClick={() => onTabChange('tests')}
                            className={cn(
                                "flex items-center gap-2 px-4 py-2 rounded-lg transition-all",
                                activeTab === 'tests' ? "bg-blue-600 text-white shadow-lg" : "hover:bg-slate-700 text-slate-400"
                            )}
                        >
                            <PlayCircle size={18} />
                            <span>Tests</span>
                        </button>
                        <button
                            onClick={() => onTabChange('logs')}
                            className={cn(
                                "flex items-center gap-2 px-4 py-2 rounded-lg transition-all",
                                activeTab === 'logs' ? "bg-blue-600 text-white shadow-lg" : "hover:bg-slate-700 text-slate-400"
                            )}
                        >
                            <List size={18} />
                            <span>Logs</span>
                        </button>
                    </nav>
                    <button className="p-2 text-slate-400 hover:text-slate-100 transition-colors">
                        <Settings size={20} />
                    </button>
                </div>
            </header>
            <main className="flex-1 w-full p-6">
                {children}
            </main>
        </div>
    );
};
