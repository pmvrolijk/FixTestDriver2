import React, {useState} from 'react';
import {cn} from '../lib/utils';
import {FixMessageEvent} from '../types';
import {Activity, ArrowDownLeft, ArrowUpRight, Eye, ShieldAlert, ShieldCheck} from 'lucide-react';
import {FixMessageEditorModal} from './FixMessageEditorModal';

interface MessageLogProps {
    messages: FixMessageEvent[];
}

export const MessageLog: React.FC<MessageLogProps> = ({messages}) => {
     const [viewingMessage, setViewingMessage] = useState<FixMessageEvent | null>(null);
     const formatTime = (ts: number) =>
         new Date(ts).toLocaleTimeString([], {hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit'});

     return (
          <div className="bg-surface-950 border border-white/5 rounded-2xl overflow-hidden flex flex-col h-full shadow-lg">
              {/* Header */}
             <div className="p-4 border-b border-white/5 bg-white/[0.02] shrink-0">
                 <h2 className="card-title flex items-center gap-2">
                     <Activity size={14} className="text-blue-400" />
                    Live Message Stream
                    {messages.length > 0 && (
                         <span className="ml-auto px-2 py-0.5 bg-blue-500/10 text-blue-400 text-[10px] font-bold rounded-full border border-blue-500/20">
                            {messages.length} message{messages.length !== 1 ? 's' : ''}
                         </span>
                    )}
                 </h2>
             </div>

             {/* Content */}
             <div className="flex-1 overflow-y-auto">
                 {messages.length === 0 ? (
                      <div className="h-full flex flex-col items-center justify-center text-surface-500">
                          <Activity size={32} className="mb-3 opacity-20" />
                         <p className="text-sm">Waiting for messages...</p>
                         <p className="text-xs text-surface-600 mt-1">Messages from all sessions appear here in real time</p>
                      </div>
                  ) : (
                       <table className="w-full text-left border-collapse">
                           <thead className="sticky top-0 bg-surface-950/95 backdrop-blur-sm z-10">
                               <tr className="text-surface-600 text-[10px] uppercase tracking-wider border-b border-white/5">
                                   <th className="px-4 py-3 font-bold">Time</th>
                                  <th className="px-4 py-3 font-bold">Dir</th>
                                  <th className="px-4 py-3 font-bold">Type</th>
                                  <th className="px-4 py-3 font-bold">Session</th>
                                  <th className="px-4 py-3 font-bold">Message</th>
                               </tr>
                           </thead>
                           <tbody>
                               {messages.map((msg, i) => {
                                   const isIncoming = msg.direction.includes('INCOMING');
                                   const isAdmin = msg.direction.includes('ADMIN');

                                   return (
                                         <tr key={msg.timestamp + i} className="hover:bg-white/[0.02] transition-colors group border-b border-white/[0.03]">
                                             <td className="px-4 py-2.5 font-mono text-[10px] text-surface-600">
                                                 {formatTime(msg.timestamp)}
                                             </td>
                                             <td className="px-4 py-2.5">
                                                 {isIncoming ? (
                                                     <div className={cn('flex items-center gap-1', isAdmin ? 'text-emerald-500' : 'text-emerald-400')}>
                                                         {isAdmin ? <ShieldCheck size={13} /> : <ArrowDownLeft size={13} />}
                                                        <span className="text-[9px] font-bold">IN</span>
                                                     </div>
                                                 ) : (
                                                     <div className={cn('flex items-center gap-1', isAdmin ? 'text-blue-500' : 'text-blue-400')}>
                                                         {isAdmin ? <ShieldAlert size={13} /> : <ArrowUpRight size={13} />}
                                                        <span className="text-[9px] font-bold">OUT</span>
                                                     </div>
                                                 )}
                                             </td>
                                             <td className="px-4 py-2.5">
                                                 <span className="px-2 py-0.5 bg-white/[0.05] rounded-md text-[9px] font-bold font-mono text-surface-400 group-hover:text-slate-300 transition-colors border border-white/5">
                                                     {msg.msgType}
                                                 </span>
                                             </td>
                                             <td className="px-4 py-2.5 text-[10px] text-surface-500 truncate max-w-[120px]" title={msg.sessionId}>
                                                 {msg.sessionId.split(':').pop()}
                                             </td>
                                             <td className="px-4 py-2.5 font-mono text-[10px] text-slate-300 break-all">
                                                 <div className="flex items-start gap-2">
                                                     <span className="flex-1">{msg.rawMessage}</span>
                                                     <button
                                                         onClick={(e) => { e.stopPropagation(); setViewingMessage(msg); }}
                                                         className="opacity-0 group-hover:opacity-100 transition-opacity shrink-0 p-1 rounded hover:bg-white/[0.05] text-surface-500 hover:text-slate-300"
                                                         title="View message"
                                                     >
                                                         <Eye size={12} />
                                                     </button>
                                                 </div>
                                             </td>
                                         </tr>
                                     );
                                 })}
                           </tbody>
                       </table>
                  )}
              </div>
             {viewingMessage && (
                 <FixMessageEditorModal
                     isOpen={true}
                     onClose={() => setViewingMessage(null)}
                     initialMessage={viewingMessage.rawMessage}
                     title={`View Message — ${viewingMessage.msgType}`}
                     readOnly={true}
                 />
             )}
          </div>
      );
};
