import React, {useEffect, useState, useRef} from 'react';
import {apiClient} from '../api/client';
import {FixMessageEvent, SessionStatus} from '../types';
import {cn} from '../lib/utils';
import {
    AlertTriangle,
    ArrowDownLeft,
    ArrowUpRight,
    LogOut,
    Play,
    RefreshCcw,
    Send,
    ShieldAlert,
    ShieldCheck,
    Square,
    Hash,
    Radio,
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
     const [confirmResetSession, setConfirmResetSession] = useState<SessionStatus | null>(null);
     const messagesEndRef = useRef<HTMLDivElement>(null);

     useEffect(() => {
         apiClient.get('/sessions').then((res) => {
             setSessions(res.data);
             if (res.data.length > 0 && !selectedSessionId) {
                 setSelectedSessionId(res.data[0].sessionId);
                 const bs = res.data[0].sessionId.split(':')[0] || 'FIX.4.2';
                 setSelectedSessionBeginString(bs);
              }
          });
      }, []);

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

     const filteredMessages = selectedSessionId
         ? messages.filter(m => m.sessionId === selectedSessionId)
          : [];

     // Format timestamp consistently
    const formatTime = (ts: number) =>
         new Date(ts).toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });

     return (
          <div className="flex flex-col gap-4 h-[calc(100vh-9rem)]">

              {/* Sessions List */}
             <div className="bg-surface-950 border border-white/5 rounded-2xl overflow-hidden shadow-lg flex flex-col max-h-[55%] min-h-[200px]">
                 <div className="p-4 border-b border-white/5 bg-white/[0.02] flex items-center justify-between shrink-0">
                     <h2 className="card-title flex items-center gap-2">
                         <Radio size={14} className="text-blue-400" />
                         Active FIX Sessions
                        {sessions.length > 0 && (
                             <span className="ml-2 px-1.5 py-0.5 bg-blue-500/10 text-blue-400 text-[10px] font-bold rounded-full">
                                {sessions.length}
                             </span>
                        )}
                     </h2>
                 </div>

                 <div className="flex-1 overflow-y-auto">
                      {sessions.length === 0 ? (
                            <div className="h-full flex flex-col items-center justify-center text-surface-500 py-12">
                                <Radio size={32} className="mb-3 opacity-20" />
                               <p className="text-sm">No sessions configured</p>
                               <p className="text-xs text-surface-600 mt-1">Check your FixEngine.cfg for session definitions</p>
                            </div>
                        ) : (
                             <div className="divide-y divide-white/[0.03]">
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
                                              className={cn(
                                                   'p-4 flex flex-col lg:flex-row lg:items-center justify-between gap-3 cursor-pointer transition-all duration-150',
                                                  isSelected
                                                       ? 'bg-blue-600/[0.07] border-l-[3px] border-blue-500'
                                                      : 'hover:bg-white/[0.02] border-l-[3px] border-transparent'
                                              )}
                                           >
                                               {/* Column 1: Status & Identity */}
                                              <div className="flex items-center gap-3">
                                                   <div className={cn(
                                                       'w-2.5 h-2.5 rounded-full shrink-0',
                                                      isLoggedOn ? 'bg-emerald-500 shadow-sm shadow-emerald-500/50' : 'bg-surface-600'
                                                  )} />
                                                  <div>
                                                       <h3 className={cn('text-sm font-semibold truncate', isSelected ? 'text-white' : 'text-slate-300')}
                                                           title={session.sessionId.toString()}>
                                                          {sessionLabel}
                                                       </h3>
                                                       <p className="text-[10px] text-surface-500 font-mono mt-0.5 truncate max-w-[200px] lg:max-w-[280px]">
                                                          {session.sessionId.toString()}
                                                       </p>
                                                  </div>
                                              </div>

                                               {/* Column 2: Connection Details */}
                                              <div className="flex items-center gap-2">
                                                   <span className={cn(
                                                       'px-2 py-0.5 text-[9px] uppercase font-bold rounded-full border',
                                                      isAcceptor
                                                           ? 'bg-purple-500/10 text-purple-400 border-purple-500/20'
                                                          : 'bg-blue-500/10 text-blue-400 border-blue-500/20'
                                                  )}>
                                                      {isAcceptor ? 'Acceptor' : 'Initiator'}
                                                   </span>

                                                    {/* Sequence Numbers */}
                                                  <div className="flex items-center gap-1.5 ml-2">
                                                       <div className={cn(
                                                           'px-2 py-1 rounded-lg flex items-center gap-1.5',
                                                          'bg-white/[0.03] border border-white/5'
                                                      )}>
                                                          <span className="text-[9px] text-surface-500 uppercase font-medium">Out</span>
                                                         <span className={cn('text-xs font-mono font-semibold', isSelected ? 'text-slate-200' : 'text-slate-400')}>
                                                              {session.expectedSenderNum ?? '-'}
                                                          </span>
                                                       </div>
                                                       <div className={cn(
                                                           'px-2 py-1 rounded-lg flex items-center gap-1.5',
                                                          'bg-white/[0.03] border border-white/5'
                                                      )}>
                                                          <span className="text-[9px] text-surface-500 uppercase font-medium">In</span>
                                                         <span className={cn('text-xs font-mono font-semibold', isSelected ? 'text-slate-200' : 'text-slate-400')}>
                                                              {session.expectedTargetNum ?? '-'}
                                                          </span>
                                                       </div>
                                                  </div>

                                                   {/* Action Buttons */}
                                                  <div className="flex items-center gap-1.5 ml-auto" onClick={(e) => e.stopPropagation()}>
                                                       {isAcceptor ? (
                                                           <>
                                                                <button onClick={() => handleLogon(session.sessionId)} disabled={isLoggedOn}
                                                                    className={cn('p-1.5 rounded-lg transition-all duration-150', isLoggedOn ? 'opacity-20' : 'bg-emerald-600/10 hover:bg-emerald-600 text-emerald-400 hover:text-white')}
                                                                    title="Start Session">
                                                                    <Play size={13} />
                                                                </button>
                                                                <button onClick={() => handleStop(session.sessionId)} disabled={!isLoggedOn}
                                                                    className={cn('p-1.5 rounded-lg transition-all duration-150', !isLoggedOn ? 'opacity-20' : 'bg-rose-600/10 hover:bg-rose-600 text-rose-400 hover:text-white')}
                                                                    title="Stop Session">
                                                                    <Square size={13} />
                                                                </button>
                                                           </>
                                                       ) : (
                                                           <>
                                                                <button onClick={() => handleLogon(session.sessionId)} disabled={isLoggedOn}
                                                                    className={cn('p-1.5 rounded-lg transition-all duration-150', isLoggedOn ? 'opacity-20' : 'bg-emerald-600/10 hover:bg-emerald-600 text-emerald-400 hover:text-white')}
                                                                    title="Logon">
                                                                    <Play size={13} />
                                                                </button>
                                                                <button onClick={() => handleLogout(session.sessionId)} disabled={!isLoggedOn}
                                                                    className={cn('p-1.5 rounded-lg transition-all duration-150', !isLoggedOn ? 'opacity-20' : 'bg-amber-600/10 hover:bg-amber-600 text-amber-400 hover:text-white')}
                                                                    title="Logout">
                                                                    <LogOut size={13} />
                                                                </button>
                                                           </>
                                                       )}

                                                        <button onClick={() => { setSelectedSessionId(session.sessionId); const bs = session.sessionId.toString().split(':')[0] || 'FIX.4.2'; setSelectedSessionBeginString(bs); setModalOpen(true); }}
                                                            className="p-1.5 rounded-lg bg-white/[0.03] hover:bg-blue-600/20 text-surface-500 hover:text-blue-400 transition-all duration-150"
                                                            title="Send Message">
                                                            <Send size={13} />
                                                        </button>

                                                         <button onClick={() => setConfirmResetSession(session)}
                                                             className="p-1.5 rounded-lg bg-white/[0.03] hover:bg-white/[0.08] text-surface-500 hover:text-slate-300 transition-all duration-150"
                                                             title="Reset Sequence Numbers">
                                                             <RefreshCcw size={13} />
                                                         </button>
                                                  </div>
                                              </div>
                                          </div>
                                      );
                                  })}
                             </div>
                        )}
                  </div>
              </div>

              {/* Live Session Message Feed */}
             <div className="bg-surface-950 border border-white/5 rounded-2xl overflow-hidden flex flex-col flex-1 min-h-[200px]">
                 <div className="p-3.5 border-b border-white/5 bg-white/[0.02] flex items-center justify-between shrink-0">
                     <h2 className="text-xs font-semibold uppercase tracking-wider text-surface-400 flex items-center gap-2">
                         <Hash size={12} className="text-blue-400" />
                         Live Message Feed
                        {selectedSessionId && (
                             <span className="px-2 py-0.5 bg-blue-500/10 text-blue-400 font-mono text-[10px] rounded-full border border-blue-500/20 lowercase">
                                {selectedSessionId.toString().split(':').pop()}
                             </span>
                        )}
                     </h2>
                    {selectedSessionId && filteredMessages.length > 0 && (
                         <span className="text-[10px] text-surface-500">
                           {filteredMessages.length} message{filteredMessages.length !== 1 ? 's' : ''}
                          </span>
                    )}
                 </div>

                 <div className="flex-1 overflow-y-auto p-3">
                      {!selectedSessionId ? (
                             <EmptyState icon={Radio} message="Select a session above to view messages" />
                         ) : filteredMessages.length === 0 ? (
                            <EmptyState icon={ArrowDownLeft} message="No messages yet — send a message or logon to start" />
                        ) : (
                             <div className="space-y-1">
                                 {filteredMessages.map((msg, idx) => {
                                      const isIncoming = msg.direction.includes('INCOMING');
                                      const isAdmin = msg.direction.includes('ADMIN');

                                      return (
                                           <div key={idx} className="group p-2 rounded-lg hover:bg-white/[0.03] transition-colors flex items-start gap-3 animate-fade-in">
                                                <span className="text-surface-600 shrink-0 select-none text-[11px] font-mono pt-0.5 w-14">
                                                  {formatTime(msg.timestamp)}
                                                </span>

                                               <span className="shrink-0 select-none pt-0.5">
                                                   {isIncoming ? (
                                                        <span className={cn('text-xs font-bold flex items-center gap-0.5', isAdmin ? 'text-emerald-500' : 'text-emerald-400')}>
                                                            {isAdmin ? <ShieldCheck size={12} /> : <ArrowDownLeft size={12} />}
                                                           <span>IN</span>
                                                        </span>
                                                    ) : (
                                                        <span className={cn('text-xs font-bold flex items-center gap-0.5', isAdmin ? 'text-blue-500' : 'text-blue-400')}>
                                                            {isAdmin ? <ShieldAlert size={12} /> : <ArrowUpRight size={12} />}
                                                           <span>OUT</span>
                                                        </span>
                                                   )}
                                              </span>

                                               <span className="px-1.5 py-0.5 rounded text-[9px] font-bold shrink-0 bg-white/[0.05] text-surface-400 group-hover:text-slate-300 transition-colors">
                                                  {msg.msgType}
                                              </span>

                                              <span className="text-slate-300 break-all select-all font-mono text-[11px] leading-relaxed fix-message">
                                                  {msg.rawMessage}
                                              </span>
                                          </div>
                                      );
                                  })}
                                 <div ref={messagesEndRef} />
                             </div>
                        )}
                  </div>
              </div>

              {/* FIX Message Send Modal */}
             <FixMessageEditorModal
                 isOpen={modalOpen}
                 onClose={() => { setModalOpen(false); setSelectedSessionId(null); }}
                initialMessage={`8=${selectedSessionBeginString}|35=D|`}
                 title={`Send Message to ${selectedSessionId?.toString().split(':').pop()}`}
                onSend={handleSend}
                 defaultBeginString={selectedSessionBeginString}
              />

              {/* Sequence Reset Confirmation Modal */}
             {confirmResetSession && (
                  <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in" onClick={() => setConfirmResetSession(null)}>
                       <div className="bg-surface-900 border border-white/10 rounded-2xl w-full max-w-md p-6 shadow-2xl overflow-hidden animate-scale-up space-y-5" onClick={(e) => e.stopPropagation()}>
                           <div className="flex items-start gap-4">
                               <div className="w-10 h-10 rounded-xl bg-rose-500/10 flex items-center justify-center shrink-0">
                                   <AlertTriangle className="text-rose-500" size={20} />
                               </div>
                              <div>
                                   <h3 className="text-base font-bold text-white">Reset Sequence Numbers?</h3>
                                  <p className="text-xs text-surface-400 mt-1.5 leading-relaxed">
                                       Reset session sequence numbers back to 1?
                                   </p>
                                  <p className="text-xs font-mono font-bold text-rose-400 bg-rose-500/10 p-2.5 rounded-xl border border-rose-500/20 mt-2">
                                       {confirmResetSession.sessionId}
                                   </p>
                                  <p className="text-[11px] text-surface-500 mt-2">
                                       This resets both sender and target sequence numbers. This cannot be undone.
                                   </p>
                              </div>
                          </div>
                           <div className="flex justify-end gap-3 pt-2">
                               <button onClick={() => setConfirmResetSession(null)}
                                   className="btn-secondary px-5 py-2.5 text-sm rounded-xl">
                                  Cancel
                               </button>
                              <button onClick={() => handleReset(confirmResetSession.sessionId)}
                                   className="px-5 py-2.5 bg-rose-600 hover:bg-rose-500 text-white text-sm font-semibold rounded-xl shadow-lg transition-all duration-150">
                                  Reset Sequences
                               </button>
                          </div>
                      </div>
                  </div>
             )}
          </div>
      );
};

// Reusable Empty State Component
function EmptyState({ icon: Icon, message }: { icon: React.ElementType; message: string }) {
     return (
         <div className="h-full flex flex-col items-center justify-center text-surface-500">
             <Icon size={32} className="mb-3 opacity-20" />
            <p className="text-sm">{message}</p>
         </div>
     );
}
