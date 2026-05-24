import React from 'react';
import { FixMessageEvent } from '../types';
import { ArrowUpRight, ArrowDownLeft, ShieldCheck, ShieldAlert } from 'lucide-react';

interface MessageLogProps {
    messages: FixMessageEvent[];
}

export const MessageLog: React.FC<MessageLogProps> = ({ messages }) => {
    return (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden flex flex-col h-[calc(100vh-12rem)]">
            <div className="p-4 border-b border-slate-800 bg-slate-800/30">
                <h2 className="font-bold flex items-center gap-2">
                    Live Message Stream
                    <span className="px-2 py-0.5 bg-blue-500/10 text-blue-500 text-[10px] uppercase tracking-widest rounded-full border border-blue-500/20">Realtime</span>
                </h2>
            </div>
            <div className="flex-1 overflow-y-auto">
                <table className="w-full text-left border-collapse">
                    <thead className="sticky top-0 bg-slate-900 z-10 shadow-sm">
                        <tr className="text-slate-500 text-[10px] uppercase tracking-wider border-b border-slate-800">
                            <th className="px-6 py-4 font-bold">Time</th>
                            <th className="px-6 py-4 font-bold">Dir</th>
                            <th className="px-6 py-4 font-bold">Type</th>
                            <th className="px-6 py-4 font-bold">Session</th>
                            <th className="px-6 py-4 font-bold">Message</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-800/50">
                        {messages.map((msg, i) => (
                            <tr key={msg.timestamp + i} className="hover:bg-slate-800/30 transition-colors group">
                                <td className="px-6 py-3 font-mono text-[10px] text-slate-500">
                                    {new Date(msg.timestamp).toLocaleTimeString([], { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                                </td>
                                <td className="px-6 py-3">
                                    {msg.direction.includes('INCOMING') ? (
                                        <div className="flex items-center gap-1.5 text-emerald-400">
                                            {msg.direction.includes('ADMIN') ? <ShieldCheck size={14} /> : <ArrowDownLeft size={14} />}
                                            <span className="text-[10px] font-bold">IN</span>
                                        </div>
                                    ) : (
                                        <div className="flex items-center gap-1.5 text-blue-400">
                                            {msg.direction.includes('ADMIN') ? <ShieldAlert size={14} /> : <ArrowUpRight size={14} />}
                                            <span className="text-[10px] font-bold">OUT</span>
                                        </div>
                                    )}
                                </td>
                                <td className="px-6 py-3">
                                    <span className="px-2 py-0.5 bg-slate-800 rounded-md text-[10px] font-bold font-mono group-hover:bg-slate-700 transition-colors">
                                        {msg.msgType}
                                    </span>
                                </td>
                                <td className="px-6 py-3 text-xs text-slate-400 truncate max-w-[120px]" title={msg.sessionId}>
                                    {msg.sessionId.split(':').pop()}
                                </td>
                                <td className="px-6 py-3 font-mono text-[10px] text-slate-300 break-all">
                                    {msg.rawMessage}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                {messages.length === 0 && (
                    <div className="py-20 flex flex-col items-center justify-center text-slate-600">
                        <p>Waiting for messages...</p>
                    </div>
                )}
            </div>
        </div>
    );
};
