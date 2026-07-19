import React, {useMemo, useRef, useState} from 'react';
import {useVirtualizer} from '@tanstack/react-virtual';
import {cn} from '../lib/utils';
import {FixMessageEvent} from '../types';
import {Activity, ArrowDownLeft, ArrowUpRight, Eye, Search, ShieldAlert, ShieldCheck, X} from 'lucide-react';
import {FixMessageEditorModal} from './FixMessageEditorModal';

interface MessageLogProps {
    messages: FixMessageEvent[];
}

// Shared grid template so the header and every virtualized row keep their columns
// aligned without a <table> (tables don't virtualize cleanly).
const GRID_COLS = 'grid grid-cols-[72px_56px_auto_120px_1fr] items-center gap-x-2 px-4';
const ROW_HEIGHT = 36;

const formatTime = (ts: number) =>
    new Date(ts).toLocaleTimeString([], {hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit'});

interface MessageRowProps {
    msg: FixMessageEvent;
    onView: (msg: FixMessageEvent) => void;
}

// Memoized so only rows whose message actually changes re-render. Because messages
// carry a stable `_id`, React reuses row instances across prepends.
const MessageRow = React.memo(({msg, onView}: MessageRowProps) => {
    const isIncoming = msg.direction.includes('INCOMING');
    const isAdmin = msg.direction.includes('ADMIN');

    return (
        <div className={cn(GRID_COLS, 'h-full hover:bg-white/[0.02] transition-colors group border-b border-white/[0.03]')}>
            <span className="font-mono text-[10px] text-surface-600">{formatTime(msg.timestamp)}</span>
            <span>
                {isIncoming ? (
                    <span className={cn('flex items-center gap-1', isAdmin ? 'text-emerald-500' : 'text-emerald-400')}>
                        {isAdmin ? <ShieldCheck size={13} /> : <ArrowDownLeft size={13} />}
                        <span className="text-[9px] font-bold">IN</span>
                    </span>
                ) : (
                    <span className={cn('flex items-center gap-1', isAdmin ? 'text-blue-500' : 'text-blue-400')}>
                        {isAdmin ? <ShieldAlert size={13} /> : <ArrowUpRight size={13} />}
                        <span className="text-[9px] font-bold">OUT</span>
                    </span>
                )}
            </span>
            <span className="px-2 py-0.5 bg-white/[0.05] rounded-md text-[9px] font-bold font-mono text-surface-400 group-hover:text-slate-300 transition-colors border border-white/5 justify-self-start">
                {msg.msgType}
            </span>
            <span className="text-[10px] text-surface-500 truncate" title={msg.sessionId}>
                {msg.sessionId.split(':').pop()}
            </span>
            <span className="flex items-center gap-2 min-w-0">
                <span className="flex-1 font-mono text-[10px] text-slate-300 truncate">{msg.rawMessage}</span>
                <button
                    onClick={(e) => { e.stopPropagation(); onView(msg); }}
                    className="opacity-0 group-hover:opacity-100 transition-opacity shrink-0 p-1 rounded hover:bg-white/[0.05] text-surface-500 hover:text-slate-300"
                    title="View message"
                >
                    <Eye size={12} />
                </button>
            </span>
        </div>
    );
});
MessageRow.displayName = 'MessageRow';

export const MessageLog: React.FC<MessageLogProps> = ({messages}) => {
    const [viewingMessage, setViewingMessage] = useState<FixMessageEvent | null>(null);
    const [filter, setFilter] = useState('');
    const scrollRef = useRef<HTMLDivElement>(null);

    // Case-insensitive substring match across the fields that identify a message.
    const filtered = useMemo(() => {
        const q = filter.trim().toLowerCase();
        if (!q) return messages;
        return messages.filter((m) =>
            m.rawMessage.toLowerCase().includes(q) ||
            m.msgType.toLowerCase().includes(q) ||
            m.sessionId.toLowerCase().includes(q) ||
            m.direction.toLowerCase().includes(q)
        );
    }, [messages, filter]);

    const virtualizer = useVirtualizer({
        count: filtered.length,
        getScrollElement: () => scrollRef.current,
        estimateSize: () => ROW_HEIGHT,
        overscan: 12,
    });

    const virtualItems = virtualizer.getVirtualItems();
    const onView = useMemo(() => (msg: FixMessageEvent) => setViewingMessage(msg), []);

    return (
        <div className="bg-surface-950 border border-white/5 rounded-2xl overflow-hidden flex flex-col h-full shadow-lg">
            {/* Header */}
            <div className="p-4 border-b border-white/5 bg-white/[0.02] shrink-0 flex items-center gap-3">
                <h2 className="card-title flex items-center gap-2 shrink-0">
                    <Activity size={14} className="text-blue-400" />
                    Live Message Stream
                </h2>
                <div className="relative ml-auto w-56 max-w-[45%]">
                    <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-surface-600 pointer-events-none" />
                    <input
                        type="text"
                        value={filter}
                        onChange={(e) => setFilter(e.target.value)}
                        placeholder="Filter messages..."
                        className="w-full pl-7 pr-7 py-1.5 bg-white/[0.03] border border-white/10 rounded-lg text-[11px] text-slate-300 placeholder:text-surface-600 focus:outline-none focus:border-blue-500/40"
                    />
                    {filter && (
                        <button
                            onClick={() => setFilter('')}
                            className="absolute right-2 top-1/2 -translate-y-1/2 text-surface-500 hover:text-slate-300"
                            title="Clear filter"
                        >
                            <X size={12} />
                        </button>
                    )}
                </div>
                {messages.length > 0 && (
                    <span className="shrink-0 px-2 py-0.5 bg-blue-500/10 text-blue-400 text-[10px] font-bold rounded-full border border-blue-500/20">
                        {filter.trim()
                            ? `${filtered.length} / ${messages.length}`
                            : `${messages.length} message${messages.length !== 1 ? 's' : ''}`}
                    </span>
                )}
            </div>

            {messages.length === 0 ? (
                <div className="flex-1 flex flex-col items-center justify-center text-surface-500">
                    <Activity size={32} className="mb-3 opacity-20" />
                    <p className="text-sm">Waiting for messages...</p>
                    <p className="text-xs text-surface-600 mt-1">Messages from all sessions appear here in real time</p>
                </div>
            ) : (
                <>
                    {/* Column header (fixed, outside the scroll area) */}
                    <div className={cn(GRID_COLS, 'py-3 text-surface-600 text-[10px] uppercase tracking-wider border-b border-white/5 bg-surface-950/95 shrink-0 font-bold')}>
                        <span>Time</span>
                        <span>Dir</span>
                        <span>Type</span>
                        <span>Session</span>
                        <span>Message</span>
                    </div>

                    {/* Virtualized scroll body */}
                    <div ref={scrollRef} className="flex-1 overflow-y-auto">
                        {filtered.length === 0 ? (
                            <div className="h-full flex flex-col items-center justify-center text-surface-500">
                                <Search size={28} className="mb-3 opacity-20" />
                                <p className="text-sm">No messages match "{filter.trim()}"</p>
                            </div>
                        ) : (
                        <div style={{height: virtualizer.getTotalSize(), position: 'relative', width: '100%'}}>
                            {virtualItems.map((virtualRow) => {
                                const msg = filtered[virtualRow.index];
                                return (
                                    <div
                                        key={msg._id ?? virtualRow.index}
                                        style={{
                                            position: 'absolute',
                                            top: 0,
                                            left: 0,
                                            width: '100%',
                                            height: virtualRow.size,
                                            transform: `translateY(${virtualRow.start}px)`,
                                        }}
                                    >
                                        <MessageRow msg={msg} onView={onView} />
                                    </div>
                                );
                            })}
                        </div>
                        )}
                    </div>
                </>
            )}

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
