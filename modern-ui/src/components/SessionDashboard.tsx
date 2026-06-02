import React, {useEffect, useState} from 'react';
import {apiClient} from '../api/client';
import {FixMessageEvent, SessionStatus} from '../types';
import {
    AlertTriangle,
    ArrowDownLeft,
    ArrowUpRight,
    Globe,
    LogOut,
    Play,
    RefreshCcw,
    Send,
    Server,
    ShieldAlert,
    ShieldCheck,
    Square
} from 'lucide-react';
import {FixMessageEditorModal} from './FixMessageEditorModal';

interface SessionDashboardProps {
    sessions: SessionStatus[];
    setSessions: React.Dispatch<React.SetStateAction<SessionStatus[]>>;
    messages: FixMessageEvent[];
}

export const SessionDashboard: React.FC<SessionDashboardProps> = ({sessions, setSessions, messages}) => {
    const [selectedSessionId, setSelectedSessionId] = useState<String | null>(null);
    const [selectedSessionBeginString, setSelectedSessionBeginString] = useState<string>('FIX.4.2');
    const [modalOpen, setModalOpen] = useState(false);

    // Confirmation Modal State
    const [confirmResetSession, setConfirmResetSession] = useState<SessionStatus | null>(null);

    useEffect(() => {
        apiClient.get('/sessions').then((res) => {
            setSessions(res.data);
            // Default select the first session if none is selected
            if (res.data.length > 0 && !selectedSessionId) {
                setSelectedSessionId(res.data[0].sessionId);
                const bs = res.data[0].sessionId.split(':')[0] || 'FIX.4.2';
                setSelectedSessionBeginString(bs);
            }
        });
    }, []);

    // Helper to refetch sessions after an action (covers cases where WebSocket broadcasts may lag)
    const refetchSessions = () => {
        apiClient.get('/sessions').then((res) => setSessions(res.data));
    };

    const handleLogon = async (sessionId: String) => {
        await apiClient.post(`/sessions/${sessionId}/logon`);
        refetchSessions();
    };

    const handleLogout = async (sessionId: String) => {
        await apiClient.post(`/sessions/${sessionId}/logout`);
        refetchSessions();
    };

    const handleStop = async (sessionId: String) => {
        await apiClient.post(`/sessions/${sessionId}/stop`);
        refetchSessions();
    };

    const handleReset = async (sessionId: String) => {
        await apiClient.post(`/sessions/${sessionId}/reset`);
        setConfirmResetSession(null);
        refetchSessions();
    };

    const handleSend = async (rawMsg: string) => {
        if (selectedSessionId) {
            await apiClient.post(`/sessions/${selectedSessionId}/send`, rawMsg, {
                headers: { 'Content-Type': 'text/plain' }
            });
        }
    };

    // Filter messages for selected session
    const filteredMessages = selectedSessionId
        ? messages.filter(m => m.sessionId === selectedSessionId)
        : [];

    return (
        <div className="flex flex-col gap-6 h-[calc(100vh-12rem)]">

            {/* Top Section: Sessions List */}
            <div className="flex-1 overflow-y-auto space-y-4">
                <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow-xl">
                    <div className="p-4 border-b border-slate-800 bg-slate-800/30">
                        <h2 className="font-bold text-sm text-slate-400 uppercase tracking-wider">Active FIX
                            Sessions</h2>
                    </div>
                    <div className="divide-y divide-slate-800">
                        {sessions.map((session) => {
                            const isSelected = selectedSessionId === session.sessionId;
                            const isLoggedOn = session.loggedOn;
                            const sessionLabel = session.sessionId.toString().split(':').pop();
                            const isAcceptor = session.connectionType === 'acceptor';

                            return (
                                <div
                                    key={session.sessionId.toString()}
                                    onClick={() => {
                                        setSelectedSessionId(session.sessionId);
                                        const bs = session.sessionId.toString().split(':')[0] || 'FIX.4.2';
                                        setSelectedSessionBeginString(bs);
                                    }}
                                    className={`p-4 flex flex-col md:flex-row md:items-center justify-between gap-4 cursor-pointer transition-colors ${
                                        isSelected ? 'bg-slate-800/40 border-l-4 border-blue-500 pl-3' : 'hover:bg-slate-800/20'
                                    }`}
                                >
                                    {/* Column 1: Status & Identity */}
                                    <div className="flex items-center gap-3 shrink-0">
                                        <span
                                            className={`w-2.5 h-2.5 rounded-full ${isLoggedOn ? 'bg-emerald-500 animate-pulse' : 'bg-rose-500'}`}/>
                                        <div>
                                            <h3 className="text-sm font-semibold text-white truncate"
                                                title={session.sessionId.toString()}>
                                                {sessionLabel}
                                            </h3>
                                            <p className="text-[11px] text-slate-500 font-mono mt-0.5 truncate max-w-xs md:max-w-sm">
                                                {session.sessionId.toString()}
                                            </p>
                                        </div>
                                    </div>

                                    {/* Column 2: Connection Details */}
                                    <div className="flex items-center gap-3 shrink-0">
                                         <span
                                             className={`px-2 py-0.5 text-[9px] uppercase font-bold rounded-full border ${
                                                 isAcceptor
                                                     ? 'bg-purple-500/10 text-purple-400 border-purple-500/20'
                                                     : 'bg-blue-500/10 text-blue-400 border-blue-500/20'
                                             }`}>
                                             {isAcceptor ? 'Acceptor' : 'Initiator'}
                                         </span>
                                        <div className="flex items-center gap-1 text-[10px] text-slate-500">
                                            <Globe size={10}/>
                                            <span>{session.host || '-'}</span>
                                        </div>
                                        <div className="flex items-center gap-1 text-[10px] text-slate-500">
                                            <Server size={10}/>
                                            <span>:{session.port || '?'}</span>
                                        </div>
                                    </div>

                                    {/* Column 3: Sequence Numbers & Actions */}
                                    <div className="flex flex-col items-end gap-2 shrink-0">
                                        {/* Sequence Numbers Row */}
                                        <div className="flex gap-2">
                                            <div
                                                className="bg-slate-950/50 border border-slate-850 px-2.5 py-1 rounded-lg flex flex-col items-center min-w-[60px]">
                                                <span className="text-[9px] text-slate-500 uppercase font-semibold">Seq Out</span>
                                                <span className="text-xs font-mono font-bold mt-0.5 text-slate-300">
                                                     {session.expectedSenderNum ?? '-'}
                                                 </span>
                                            </div>
                                            <div
                                                className="bg-slate-950/50 border border-slate-850 px-2.5 py-1 rounded-lg flex flex-col items-center min-w-[60px]">
                                                <span className="text-[9px] text-slate-500 uppercase font-semibold">Seq In</span>
                                                <span className="text-xs font-mono font-bold mt-0.5 text-slate-300">
                                                     {session.expectedTargetNum ?? '-'}
                                                 </span>
                                            </div>
                                        </div>

                                        {/* Action Buttons Row */}
                                        <div className="flex flex-wrap items-center gap-2"
                                             onClick={(e) => e.stopPropagation()}>
                                            {isAcceptor ? (
                                                /* Acceptor buttons: Start, Stop, Reset, Send */
                                                <>
                                                    <button
                                                        onClick={() => handleLogon(session.sessionId)}
                                                        disabled={isLoggedOn}
                                                        className="px-3 py-1.5 bg-emerald-600/10 hover:bg-emerald-600 text-emerald-400 hover:text-white disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-emerald-400 text-xs font-bold rounded-lg flex items-center gap-1.5 transition-all"
                                                        title="Start Session (Logon)"
                                                    >
                                                        <Play size={12}/>
                                                        <span>Start</span>
                                                    </button>

                                                    <button
                                                        onClick={() => handleStop(session.sessionId)}
                                                        disabled={!isLoggedOn}
                                                        className="px-3 py-1.5 bg-rose-600/10 hover:bg-rose-600 text-rose-400 hover:text-white disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-rose-400 text-xs font-bold rounded-lg flex items-center gap-1.5 transition-all"
                                                        title="Stop Session (Logout + Disconnect)"
                                                    >
                                                        <Square size={12}/>
                                                        <span>Stop</span>
                                                    </button>
                                                </>
                                            ) : (
                                                /* Initiator buttons: Logon, Logout, Reset, Send */
                                                <>
                                                    <button
                                                        onClick={() => handleLogon(session.sessionId)}
                                                        disabled={isLoggedOn}
                                                        className="px-3 py-1.5 bg-emerald-600/10 hover:bg-emerald-600 text-emerald-400 hover:text-white disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-emerald-400 text-xs font-bold rounded-lg flex items-center gap-1.5 transition-all"
                                                        title="Initiate Logon Sequence"
                                                    >
                                                        <Play size={12}/>
                                                        <span>Logon</span>
                                                    </button>

                                                    <button
                                                        onClick={() => handleLogout(session.sessionId)}
                                                        disabled={!isLoggedOn}
                                                        className="px-3 py-1.5 bg-amber-600/10 hover:bg-amber-600 text-amber-400 hover:text-white disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-amber-400 text-xs font-bold rounded-lg flex items-center gap-1.5 transition-all"
                                                        title="Graceful Logout"
                                                    >
                                                        <LogOut size={12}/>
                                                        <span>Logout</span>
                                                    </button>
                                                </>
                                            )}

                                            {/* Send Message - available for all */}
                                            <button
                                                onClick={() => {
                                                    setSelectedSessionId(session.sessionId);
                                                    const bs = session.sessionId.toString().split(':')[0] || 'FIX.4.2';
                                                    setSelectedSessionBeginString(bs);
                                                    setModalOpen(true);
                                                }}
                                                className="p-1.5 bg-slate-800 hover:bg-blue-600 text-slate-400 hover:text-white rounded-lg transition-all"
                                                title="Send One-Shot Message"
                                            >
                                                <Send size={13}/>
                                            </button>

                                            {/* Reset Sequences - available for all */}
                                            <button
                                                onClick={() => setConfirmResetSession(session)}
                                                className="p-1.5 bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white rounded-lg transition-all"
                                                title="Reset Sequence Numbers"
                                            >
                                                <RefreshCcw size={13}/>
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            </div>

            {/* Bottom Section: Selected Session Real-time Log */}
            <div
                className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden flex flex-col h-72 shadow-xl shrink-0">
                <div className="p-3.5 border-b border-slate-800 bg-slate-800/30 flex items-center justify-between">
                    <h2 className="font-bold text-xs text-slate-400 uppercase tracking-wider flex items-center gap-2">
                        <span>Live Session Message Feed</span>
                        {selectedSessionId && (
                            <span
                                className="px-2 py-0.5 bg-blue-500/10 text-blue-400 font-mono text-[10px] lowercase tracking-normal rounded-full border border-blue-500/20">
                                 {selectedSessionId.toString().split(':').pop()}
                             </span>
                        )}
                    </h2>
                    <span
                        className="px-2 py-0.5 bg-emerald-500/10 text-emerald-400 text-[9px] uppercase font-bold tracking-wider rounded-full border border-emerald-500/20">Realtime</span>
                </div>

                <div
                    className="flex-1 overflow-y-auto p-4 bg-slate-950 font-mono text-[11px] leading-relaxed space-y-2">
                    {!selectedSessionId ? (
                        <div className="h-full flex items-center justify-center text-slate-600 font-sans text-xs">
                            Select a session from the list above to view its live log stream.
                        </div>
                    ) : filteredMessages.length === 0 ? (
                        <div className="h-full flex items-center justify-center text-slate-700 font-sans text-xs">
                            No messages streamed for this session yet.
                        </div>
                    ) : (
                        <div className="space-y-1">
                            {filteredMessages.map((msg, idx) => {
                                const isIncoming = msg.direction.includes('INCOMING');
                                const isAdmin = msg.direction.includes('ADMIN');
                                const timeStr = new Date(msg.timestamp).toLocaleTimeString([], {
                                    hour12: false,
                                    hour: '2-digit',
                                    minute: '2-digit',
                                    second: '2-digit'
                                });

                                return (
                                    <div key={idx}
                                         className="p-1.5 hover:bg-slate-900 rounded transition-colors flex items-start gap-3">
                                        <span className="text-slate-650 shrink-0 select-none">[{timeStr}]</span>
                                        <span className="shrink-0 select-none">
                                              {isIncoming ? (
                                                  <span
                                                      className="text-emerald-400 font-bold flex items-center gap-0.5">
                                                      {isAdmin ? <ShieldCheck size={12}/> : <ArrowDownLeft size={12}/>}
                                                      <span>IN</span>
                                                  </span>
                                              ) : (
                                                  <span className="text-blue-400 font-bold flex items-center gap-0.5">
                                                      {isAdmin ? <ShieldAlert size={12}/> : <ArrowUpRight size={12}/>}
                                                      <span>OUT</span>
                                                  </span>
                                              )}
                                          </span>
                                        <span
                                            className="bg-slate-800 text-slate-400 px-1 py-0.5 rounded text-[10px] font-bold shrink-0">
                                              {msg.msgType}
                                          </span>
                                        <span
                                            className="text-slate-300 break-all select-all font-sans font-medium text-[11px]">
                                              {msg.rawMessage}
                                          </span>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>

            {/* FIX Message Modal */}
            <FixMessageEditorModal
                isOpen={modalOpen}
                onClose={() => {
                    setModalOpen(false);
                    setSelectedSessionId(null);
                }}
                initialMessage={`8=${selectedSessionBeginString}|35=D|`}
                title={`Send Message to ${selectedSessionId?.toString().split(':').pop()}`}
                onSend={handleSend}
                defaultBeginString={selectedSessionBeginString}
            />

            {/* Sequence Reset Confirmation Modal */}
            {confirmResetSession && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
                    <div
                        className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-md p-6 shadow-2xl overflow-hidden animate-scale-up space-y-4">
                        <div className="flex items-start gap-3 text-rose-500">
                            <AlertTriangle className="shrink-0" size={24}/>
                            <div>
                                <h3 className="text-base font-bold text-white">Reset Sequence Numbers?</h3>
                                <p className="text-xs text-slate-400 mt-1 leading-relaxed">
                                    Are you sure you want to reset sequence numbers for session:
                                </p>
                                <p className="text-xs font-mono font-bold text-rose-400 bg-rose-500/10 p-2 rounded border border-rose-500/20 mt-2 truncate">
                                    {confirmResetSession.sessionId}
                                </p>
                                <p className="text-[11px] text-slate-500 mt-2">
                                    This will reset both the expected sender and target sequence numbers to 1. This
                                    action is irreversible.
                                </p>
                            </div>
                        </div>
                        <div className="flex justify-end gap-3 pt-2">
                            <button
                                onClick={() => setConfirmResetSession(null)}
                                className="px-4 py-2 border border-slate-800 hover:border-slate-700 text-slate-400 hover:text-white text-xs font-bold rounded-xl transition-all"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={() => handleReset(confirmResetSession.sessionId)}
                                className="px-4 py-2 bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold rounded-xl shadow-lg hover:shadow-rose-500/20 transition-all"
                            >
                                Confirm Reset
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};
