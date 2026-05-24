import React, { useEffect } from 'react';
import { apiClient } from '../api/client';
import { SessionStatus } from '../types';
import { Power, RefreshCcw, Wifi, WifiOff } from 'lucide-react';

interface SessionDashboardProps {
    sessions: SessionStatus[];
    setSessions: React.Dispatch<React.SetStateAction<SessionStatus[]>>;
}

export const SessionDashboard: React.FC<SessionDashboardProps> = ({ sessions, setSessions }) => {
    useEffect(() => {
        apiClient.get('/sessions').then((res) => setSessions(res.data));
    }, []);

    const handleLogon = async (sessionId: String) => {
        await apiClient.post(`/sessions/${sessionId}/logon`);
    };

    const handleLogout = async (sessionId: String) => {
        await apiClient.post(`/sessions/${sessionId}/logout`);
    };

    const handleReset = async (sessionId: String) => {
        await apiClient.post(`/sessions/${sessionId}/reset`);
    };

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {sessions.map((session) => (
                <div key={session.sessionId.toString()} className="bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-xl hover:border-slate-700 transition-colors group">
                    <div className="flex justify-between items-start mb-4">
                        <div className="p-3 bg-slate-800 rounded-xl">
                            {session.loggedOn ? (
                                <Wifi className="text-emerald-400" size={24} />
                            ) : (
                                <WifiOff className="text-rose-400" size={24} />
                            )}
                        </div>
                        <div className="flex gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button
                                onClick={() => handleReset(session.sessionId)}
                                className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white rounded-lg transition-colors"
                                title="Reset Sequence"
                            >
                                <RefreshCcw size={16} />
                            </button>
                        </div>
                    </div>
                    
                    <h3 className="text-lg font-semibold mb-1 truncate" title={session.sessionId.toString()}>
                        {session.sessionId.toString().split(':').pop()}
                    </h3>
                    <p className="text-sm text-slate-500 mb-6">{session.sessionId.toString()}</p>

                    <div className="grid grid-cols-2 gap-4 mb-6">
                        <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-800">
                            <span className="text-xs text-slate-500 uppercase font-bold tracking-wider">Expected Out</span>
                            <div className="text-xl font-mono mt-1">{session.expectedSenderNum ?? '-'}</div>
                        </div>
                        <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-800">
                            <span className="text-xs text-slate-500 uppercase font-bold tracking-wider">Expected In</span>
                            <div className="text-xl font-mono mt-1">{session.expectedTargetNum ?? '-'}</div>
                        </div>
                    </div>

                    <button
                        onClick={() => session.loggedOn ? handleLogout(session.sessionId) : handleLogon(session.sessionId)}
                        className={`w-full py-3 rounded-xl font-bold flex items-center justify-center gap-2 transition-all ${
                            session.loggedOn
                                ? 'bg-rose-500/10 text-rose-500 hover:bg-rose-500 hover:text-white'
                                : 'bg-emerald-500/10 text-emerald-500 hover:bg-emerald-500 hover:text-white'
                        }`}
                    >
                        <Power size={18} />
                        <span>{session.loggedOn ? 'Logout' : 'Logon'}</span>
                    </button>
                </div>
            ))}
        </div>
    );
};
