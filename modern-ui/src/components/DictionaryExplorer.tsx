import React, { useEffect, useRef, useState } from 'react';
import { ChevronDown, ChevronRight, RefreshCw } from 'lucide-react';
import { apiClient } from '../api/client';

// --- Types ---

interface VersionInfo {
    id: string;
    label: string;
    fixMajor: number;
    fixMinor: number;
}

interface MessageSummary {
    name: string;
    msgtype: string;
    msgcat: string;
}

interface FieldSummary {
    number: number;
    name: string;
    type: string;
    hasEnums: boolean;
}

interface MessageEntry {
    kind: 'field' | 'group';
    name: string;
    tag: number;
    type: string;
    required: boolean;
    children: MessageEntry[];
}

interface MessageDetail {
    name: string;
    msgtype: string;
    msgcat: string;
    entries: MessageEntry[];
}

interface EnumValue {
    code: string;
    description: string;
}

interface FieldDetail {
    number: number;
    name: string;
    type: string;
    values: EnumValue[];
    usedInMessages: string[];
}

type Selection =
    | { kind: 'message'; name: string }
    | { kind: 'field'; tag: number };

type DetailCache = Map<string, MessageDetail | FieldDetail>;

type HistoryEntry =
    | { kind: 'message'; name: string; label: string }
    | { kind: 'field'; tag: number; label: string };

// --- Component ---

export function DictionaryExplorer() {
    const [versions, setVersions] = useState<VersionInfo[]>([]);
    const [selectedVersionId, setSelectedVersionId] = useState('');
    const [messages, setMessages] = useState<MessageSummary[]>([]);
    const [fields, setFields] = useState<FieldSummary[]>([]);
    const [search, setSearch] = useState('');
    const [selection, setSelection] = useState<Selection | null>(null);
    const [messageDetail, setMessageDetail] = useState<MessageDetail | null>(null);
    const [fieldDetail, setFieldDetail] = useState<FieldDetail | null>(null);
    const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());
    const [loading, setLoading] = useState(false);
    const [history, setHistory] = useState<HistoryEntry[]>([]);
    const cacheRef = useRef<DetailCache>(new Map());
    const rightPaneRef = useRef<HTMLDivElement>(null);

    // Load versions on mount
    useEffect(() => {
        apiClient.get<VersionInfo[]>('/fix-dictionary/versions').then(r => {
            setVersions(r.data);
            if (r.data.length > 0) setSelectedVersionId(r.data[0].id);
        });
    }, []);

    // Load messages + fields when version changes
    useEffect(() => {
        if (!selectedVersionId) return;
        cacheRef.current.clear();
        setSelection(null);
        setMessageDetail(null);
        setFieldDetail(null);
        setSearch('');
        setExpandedGroups(new Set());
        setHistory([]);

        Promise.all([
            apiClient.get<MessageSummary[]>(`/fix-dictionary/${selectedVersionId}/messages`),
            apiClient.get<FieldSummary[]>(`/fix-dictionary/${selectedVersionId}/fields`),
        ]).then(([mr, fr]) => {
            setMessages(mr.data);
            setFields(fr.data);
        });
    }, [selectedVersionId]);

    // Scroll right pane to top when detail content lands (after content replaces the previous panel)
    useEffect(() => {
        if (fieldDetail || messageDetail) {
            rightPaneRef.current?.scrollTo({ top: 0 });
        }
    }, [fieldDetail, messageDetail]);

    // Load detail when selection changes
    useEffect(() => {
        if (!selection || !selectedVersionId) return;

        const cacheKey = selection.kind === 'message'
            ? `msg:${selection.name}`
            : `field:${selection.tag}`;

        const cached = cacheRef.current.get(cacheKey);
        if (cached) {
            if (selection.kind === 'message') {
                setFieldDetail(null);
                setMessageDetail(cached as MessageDetail);
            } else {
                setMessageDetail(null);
                setFieldDetail(cached as FieldDetail);
            }
            return;
        }

        setLoading(true);
        if (selection.kind === 'message') {
            setFieldDetail(null);
            apiClient.get<MessageDetail>(
                `/fix-dictionary/${selectedVersionId}/messages/${selection.name}`
            ).then(r => {
                cacheRef.current.set(cacheKey, r.data);
                setMessageDetail(r.data);
            }).finally(() => setLoading(false));
        } else {
            setMessageDetail(null);
            apiClient.get<FieldDetail>(
                `/fix-dictionary/${selectedVersionId}/fields/${selection.tag}`
            ).then(r => {
                cacheRef.current.set(cacheKey, r.data);
                setFieldDetail(r.data);
            }).finally(() => setLoading(false));
        }
    }, [selection, selectedVersionId]);

    const handleReload = () => {
        apiClient.post<VersionInfo[]>('/fix-dictionary/reload').then(r => {
            setVersions(r.data);
            if (r.data.length > 0 && !r.data.find(v => v.id === selectedVersionId)) {
                setSelectedVersionId(r.data[0].id);
            }
        });
    };

    const q = search.trim().toLowerCase();
    const filteredMessages = messages.filter(m =>
        m.name.toLowerCase().includes(q) || m.msgtype === search.trim()
    );
    const filteredFields = fields.filter(f =>
        f.name.toLowerCase().includes(q) || String(f.number) === search.trim()
    );

    const toggleGroup = (key: string) => {
        setExpandedGroups(prev => {
            const next = new Set(prev);
            if (next.has(key)) next.delete(key);
            else next.add(key);
            return next;
        });
    };

    const isSameSelection = (a: Selection, b: Selection) =>
        a.kind === b.kind && (a.kind === 'message' ? a.name === (b as typeof a).name : a.tag === (b as { kind: 'field'; tag: number }).tag);

    const navigate = (next: Selection, fromPanel: boolean) => {
        if (selection && isSameSelection(next, selection)) return;
        if (fromPanel && selection) {
            let label: string;
            if (selection.kind === 'message') {
                label = selection.name;
            } else {
                label = fieldDetail?.name ?? fields.find(f => f.number === selection.tag)?.name ?? `Tag ${selection.tag}`;
            }
            setHistory(prev => [...prev, { ...selection, label } as HistoryEntry]);
        } else {
            setHistory([]);
        }
        if (next.kind === 'message') setExpandedGroups(new Set());
        setSelection(next);
    };

    const navigateToCrumb = (idx: number) => {
        const crumb = history[idx];
        setHistory(prev => prev.slice(0, idx));
        const sel: Selection = crumb.kind === 'message'
            ? { kind: 'message', name: crumb.name }
            : { kind: 'field', tag: crumb.tag };
        if (crumb.kind === 'message') setExpandedGroups(new Set());
        setSelection(sel);
    };

    const selectMessage = (name: string, fromPanel = false) => navigate({ kind: 'message', name }, fromPanel);
    const selectField = (tag: number, fromPanel = false) => navigate({ kind: 'field', tag }, fromPanel);

    return (
        <div className="flex flex-col h-full gap-3">
            {/* Top bar */}
            <div className="flex items-center gap-3 flex-shrink-0">
                <select
                    value={selectedVersionId}
                    onChange={e => setSelectedVersionId(e.target.value)}
                    className="bg-white/5 border border-white/10 text-slate-200 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
                >
                    {versions.map(v => (
                        <option key={v.id} value={v.id}>{v.label}</option>
                    ))}
                </select>
                <input
                    type="search"
                    placeholder="Search name, msgtype or tag number…"
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                    className="flex-1 bg-white/5 border border-white/10 text-slate-200 placeholder-slate-500 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500"
                />
                <button
                    onClick={handleReload}
                    className="flex items-center gap-1.5 px-3 py-1.5 text-sm text-slate-300 hover:text-white bg-white/5 hover:bg-white/10 border border-white/10 rounded-lg transition-colors"
                    title="Reload dictionaries"
                >
                    <RefreshCw size={14} />
                    Reload
                </button>
            </div>

            {/* Two-pane body */}
            <div className="flex gap-4 flex-1 min-h-0">
                {/* Left pane — Messages (1/3) + Fields (2/3), each independently scrolling */}
                <div className="w-80 flex-shrink-0 flex flex-col gap-3 min-h-0">
                    <Section title={`Messages (${filteredMessages.length})`} className="flex-1 min-h-0">
                        {filteredMessages.map(m => (
                            <ListRow
                                key={m.name}
                                active={selection?.kind === 'message' && selection.name === m.name}
                                onClick={() => selectMessage(m.name)}
                            >
                                <span className="font-medium text-slate-200">{m.name}</span>
                                <span className="ml-auto text-xs text-slate-500 font-mono">{m.msgtype}</span>
                            </ListRow>
                        ))}
                        {filteredMessages.length === 0 && (
                            <p className="text-xs text-slate-600 px-2 py-1">No messages match</p>
                        )}
                    </Section>

                    <Section title={`Fields (${filteredFields.length})`} className="min-h-0" style={{ flex: '2 1 0%' }}>
                        {filteredFields.map(f => (
                            <ListRow
                                key={f.number}
                                active={selection?.kind === 'field' && selection.tag === f.number}
                                onClick={() => selectField(f.number)}
                            >
                                <span className="font-medium text-slate-200">{f.name}</span>
                                <span className="ml-auto text-xs text-slate-500 font-mono">{f.number}</span>
                            </ListRow>
                        ))}
                        {filteredFields.length === 0 && (
                            <p className="text-xs text-slate-600 px-2 py-1">No fields match</p>
                        )}
                    </Section>
                </div>

                {/* Right pane */}
                <div ref={rightPaneRef} className="flex-1 min-w-0 overflow-y-auto bg-white/[0.02] border border-white/5 rounded-xl p-5" style={{ overflowAnchor: 'none' }}>
                    {history.length > 0 && selection && (
                        <div className="flex items-center gap-1 mb-4 text-xs flex-wrap">
                            {history.flatMap((crumb, idx) => [
                                <button
                                    key={`crumb-${idx}`}
                                    onClick={() => navigateToCrumb(idx)}
                                    className="text-blue-400 hover:text-blue-300 hover:underline"
                                >
                                    {crumb.label}
                                </button>,
                                <ChevronRight key={`sep-${idx}`} size={10} className="text-slate-600 shrink-0" />,
                            ])}
                            <span className="text-slate-300">
                                {selection.kind === 'message'
                                    ? selection.name
                                    : (fieldDetail?.name ?? fields.find(f => f.number === selection.tag)?.name ?? `Tag ${selection.tag}`)}
                            </span>
                        </div>
                    )}

                    {loading && (
                        <p className="text-slate-500 text-sm">Loading…</p>
                    )}

                    {!loading && !selection && (
                        <div className="flex items-center justify-center h-full text-slate-600 text-sm">
                            Select a message or field from the left panel
                        </div>
                    )}

                    {!loading && messageDetail && (
                        <MessageDetailPanel
                            detail={messageDetail}
                            expandedGroups={expandedGroups}
                            onToggleGroup={toggleGroup}
                            onFieldClick={(tag) => selectField(tag, true)}
                        />
                    )}

                    {!loading && fieldDetail && (
                        <FieldDetailPanel
                            detail={fieldDetail}
                            onMessageClick={(name) => selectMessage(name, true)}
                        />
                    )}
                </div>
            </div>
        </div>
    );
}

// --- Sub-components ---

function Section({ title, children, className, style }: {
    title: string;
    children: React.ReactNode;
    className?: string;
    style?: React.CSSProperties;
}) {
    return (
        <div
            className={`bg-white/[0.02] border border-white/5 rounded-xl overflow-hidden flex flex-col${className ? ` ${className}` : ''}`}
            style={style}
        >
            <div className="px-3 py-2 border-b border-white/5 text-xs font-semibold text-slate-400 uppercase tracking-wide shrink-0">
                {title}
            </div>
            <div className="py-1 flex-1 overflow-y-auto overflow-x-hidden min-h-0">{children}</div>
        </div>
    );
}

function ListRow({
    active,
    onClick,
    children,
}: {
    active: boolean;
    onClick: () => void;
    children: React.ReactNode;
}) {
    return (
        <button
            onClick={onClick}
            className={`w-full flex items-center gap-2 px-3 py-1.5 text-sm text-left transition-colors ${
                active
                    ? 'bg-blue-600/20 text-blue-300'
                    : 'text-slate-400 hover:bg-white/5 hover:text-slate-200'
            }`}
        >
            {children}
        </button>
    );
}

function MessageDetailPanel({
    detail,
    expandedGroups,
    onToggleGroup,
    onFieldClick,
}: {
    detail: MessageDetail;
    expandedGroups: Set<string>;
    onToggleGroup: (key: string) => void;
    onFieldClick: (tag: number) => void;
}) {
    return (
        <div>
            <div className="flex items-baseline gap-3 mb-4">
                <h2 className="text-lg font-semibold text-slate-100">{detail.name}</h2>
                <span className="text-xs font-mono bg-blue-600/20 text-blue-300 px-2 py-0.5 rounded">
                    35={detail.msgtype}
                </span>
                <span className="text-xs text-slate-500">{detail.msgcat}</span>
            </div>

            <table className="w-full text-sm border-collapse">
                <thead>
                    <tr className="text-left text-xs text-slate-500 uppercase tracking-wide border-b border-white/5">
                        <th className="pb-2 pr-4 w-16">Tag</th>
                        <th className="pb-2 pr-4">Name</th>
                        <th className="pb-2 pr-4 w-8">Req</th>
                        <th className="pb-2">Type</th>
                    </tr>
                </thead>
                <tbody>
                    {detail.entries.map((entry, idx) => (
                        <EntryRows
                            key={idx}
                            entry={entry}
                            depth={0}
                            path={String(idx)}
                            expandedGroups={expandedGroups}
                            onToggleGroup={onToggleGroup}
                            onFieldClick={onFieldClick}
                        />
                    ))}
                </tbody>
            </table>
        </div>
    );
}

function EntryRows({
    entry,
    depth,
    path,
    expandedGroups,
    onToggleGroup,
    onFieldClick,
}: {
    entry: MessageEntry;
    depth: number;
    path: string;
    expandedGroups: Set<string>;
    onToggleGroup: (key: string) => void;
    onFieldClick: (tag: number) => void;
}) {
    const isGroup = entry.kind === 'group';
    const isExpanded = expandedGroups.has(path);
    const indent = depth * 16;

    return (
        <>
            <tr className="border-b border-white/[0.03] hover:bg-white/[0.02]">
                <td className="py-1.5 pr-4 font-mono text-xs text-slate-500">{entry.tag || ''}</td>
                <td className="py-1.5 pr-4" style={{ paddingLeft: indent }}>
                    {isGroup ? (
                        <button
                            onClick={() => onToggleGroup(path)}
                            className="flex items-center gap-1 text-amber-400 hover:text-amber-300 font-medium"
                        >
                            {isExpanded
                                ? <ChevronDown size={13} />
                                : <ChevronRight size={13} />}
                            {entry.name}
                        </button>
                    ) : (
                        <button
                            onClick={() => entry.tag > 0 && onFieldClick(entry.tag)}
                            className="text-blue-400 hover:text-blue-300 hover:underline text-left"
                        >
                            {entry.name}
                        </button>
                    )}
                </td>
                <td className="py-1.5 pr-4 text-xs text-slate-500">{entry.required ? 'Y' : 'N'}</td>
                <td className="py-1.5 font-mono text-xs text-slate-400">{entry.type}</td>
            </tr>
            {isGroup && isExpanded && entry.children.map((child, idx) => (
                <EntryRows
                    key={idx}
                    entry={child}
                    depth={depth + 1}
                    path={`${path}.${idx}`}
                    expandedGroups={expandedGroups}
                    onToggleGroup={onToggleGroup}
                    onFieldClick={onFieldClick}
                />
            ))}
        </>
    );
}

function FieldDetailPanel({
    detail,
    onMessageClick,
}: {
    detail: FieldDetail;
    onMessageClick: (name: string) => void;
}) {
    return (
        <div>
            <div className="flex items-baseline gap-3 mb-4">
                <h2 className="text-lg font-semibold text-slate-100">{detail.name}</h2>
                <span className="text-xs font-mono text-slate-400">tag {detail.number}</span>
                <span className="text-xs font-mono bg-slate-700 text-slate-300 px-2 py-0.5 rounded">
                    {detail.type}
                </span>
            </div>

            {detail.values.length > 0 && (
                <div className="mb-6">
                    <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">
                        Enum values
                    </h3>
                    <table className="w-full text-sm border-collapse">
                        <thead>
                            <tr className="text-left text-xs text-slate-500 uppercase tracking-wide border-b border-white/5">
                                <th className="pb-2 pr-6 w-20">Code</th>
                                <th className="pb-2">Description</th>
                            </tr>
                        </thead>
                        <tbody>
                            {detail.values.map(ev => (
                                <tr key={ev.code} className="border-b border-white/[0.03]">
                                    <td className="py-1.5 pr-6 font-mono text-slate-300">{ev.code}</td>
                                    <td className="py-1.5 text-slate-400">{ev.description}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {detail.usedInMessages.length > 0 && (
                <div>
                    <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wide mb-2">
                        Used in messages ({detail.usedInMessages.length})
                    </h3>
                    <div className="flex flex-wrap gap-2">
                        {detail.usedInMessages.map(name => (
                            <button
                                key={name}
                                onClick={() => onMessageClick(name)}
                                className="text-xs text-blue-400 hover:text-blue-300 hover:underline bg-blue-600/10 hover:bg-blue-600/20 px-2 py-1 rounded transition-colors"
                            >
                                {name}
                            </button>
                        ))}
                    </div>
                </div>
            )}

            {detail.values.length === 0 && detail.usedInMessages.length === 0 && (
                <p className="text-slate-600 text-sm">No enum values or message references found.</p>
            )}
        </div>
    );
}
