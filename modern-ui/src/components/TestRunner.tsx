import React, {useEffect, useState} from 'react';
import {apiClient} from '../api/client';
import {FileTreeNode, TestResult} from '../types';
import {cn} from '../lib/utils';
import {
    Check, CheckCircle2, ChevronDown, ChevronRight, Clock, Code, Edit, FileCode,
    FilePlus, Folder, FolderOpen, FolderPlus, GripVertical, History, Link2, LogOut,
    Pencil, Play, Plus, Repeat, RotateCcw, Save, Trash2, X, XCircle
} from 'lucide-react';
import {FixMessageEditorModal} from './FixMessageEditorModal';
import { DndContext, closestCenter, PointerSensor, useSensor, useSensors } from '@dnd-kit/core';
import type { DragEndEvent } from '@dnd-kit/core';
import { SortableContext, useSortable, verticalListSortingStrategy, arrayMove } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

// ─── File Tree ───────────────────────────────────────────────────────────────

interface FileTreeEntryProps {
    node: FileTreeNode;
    depth: number;
    selectedTest: string | null;
    running: string | null;
    expandedFolders: Set<string>;
    renamingPath: string | null;
    renameValue: string;
    onToggleFolder: (path: string) => void;
    onSelect: (path: string) => void;
    onRun: (path: string) => void;
    onRenameStart: (node: FileTreeNode) => void;
    onRenameChange: (value: string) => void;
    onRenameSubmit: (node: FileTreeNode) => void;
    onRenameCancel: () => void;
    onDelete: (path: string, isDir: boolean) => void;
    onCreateFile: (parentPath: string) => void;
    onCreateDir: (parentPath: string) => void;
}

const FileTreeEntry: React.FC<FileTreeEntryProps> = ({
    node, depth, selectedTest, running, expandedFolders,
    renamingPath, renameValue, onToggleFolder, onSelect, onRun,
    onRenameStart, onRenameChange, onRenameSubmit, onRenameCancel,
    onDelete, onCreateFile, onCreateDir
}) => {
    const isExpanded = expandedFolders.has(node.path);
    const isRenaming = renamingPath === node.path;
    const indent = 8 + depth * 14;

    if (node.directory) {
        return (
            <>
                <div
                    className="flex items-center gap-1 py-1 rounded-lg cursor-pointer group transition-colors hover:bg-white/[0.04]"
                    style={{ paddingLeft: `${indent}px`, paddingRight: '4px' }}
                    onClick={() => onToggleFolder(node.path)}
                >
                    {isExpanded
                        ? <ChevronDown size={12} className="text-surface-600 shrink-0" />
                        : <ChevronRight size={12} className="text-surface-600 shrink-0" />
                    }
                    {isExpanded
                        ? <FolderOpen size={13} className="text-amber-400 shrink-0" />
                        : <Folder size={13} className="text-amber-400 shrink-0" />
                    }
                    {isRenaming ? (
                        <input
                            autoFocus
                            className="flex-1 text-xs bg-white/[0.08] border border-blue-500/50 rounded px-1 py-0.5 text-white outline-none min-w-0"
                            value={renameValue}
                            onChange={e => onRenameChange(e.target.value)}
                            onKeyDown={e => {
                                if (e.key === 'Enter') onRenameSubmit(node);
                                if (e.key === 'Escape') onRenameCancel();
                            }}
                            onBlur={onRenameCancel}
                            onClick={e => e.stopPropagation()}
                        />
                    ) : (
                        <span className="flex-1 text-xs text-slate-300 font-medium truncate min-w-0">{node.name}</span>
                    )}
                    <div className="hidden group-hover:flex items-center gap-0.5 ml-1 shrink-0">
                        <button onClick={e => { e.stopPropagation(); onCreateFile(node.path); }} title="New file"
                            className="p-0.5 rounded text-surface-600 hover:text-blue-400 hover:bg-blue-500/10 transition-colors">
                            <FilePlus size={11} />
                        </button>
                        <button onClick={e => { e.stopPropagation(); onCreateDir(node.path); }} title="New folder"
                            className="p-0.5 rounded text-surface-600 hover:text-amber-400 hover:bg-amber-500/10 transition-colors">
                            <FolderPlus size={11} />
                        </button>
                        <button onClick={e => { e.stopPropagation(); onRenameStart(node); }} title="Rename"
                            className="p-0.5 rounded text-surface-600 hover:text-white hover:bg-white/10 transition-colors">
                            <Pencil size={11} />
                        </button>
                        <button onClick={e => { e.stopPropagation(); onDelete(node.path, true); }} title="Delete folder"
                            className="p-0.5 rounded text-surface-600 hover:text-rose-400 hover:bg-rose-500/10 transition-colors">
                            <Trash2 size={11} />
                        </button>
                    </div>
                </div>
                {isExpanded && node.children?.map(child => (
                    <FileTreeEntry
                        key={child.path} node={child} depth={depth + 1}
                        selectedTest={selectedTest} running={running}
                        expandedFolders={expandedFolders} renamingPath={renamingPath} renameValue={renameValue}
                        onToggleFolder={onToggleFolder} onSelect={onSelect} onRun={onRun}
                        onRenameStart={onRenameStart} onRenameChange={onRenameChange}
                        onRenameSubmit={onRenameSubmit} onRenameCancel={onRenameCancel}
                        onDelete={onDelete} onCreateFile={onCreateFile} onCreateDir={onCreateDir}
                    />
                ))}
            </>
        );
    }

    const isSelected = selectedTest === node.path;
    return (
        <div
            className={cn(
                'flex items-center gap-1.5 py-1 rounded-lg cursor-pointer group transition-colors',
                isSelected ? 'bg-blue-600/[0.1]' : 'hover:bg-white/[0.04]'
            )}
            style={{ paddingLeft: `${indent + 14}px`, paddingRight: '4px' }}
            onClick={() => onSelect(node.path)}
        >
            <FileCode size={12} className={cn('shrink-0', isSelected ? 'text-blue-400' : 'text-surface-500')} />
            {isRenaming ? (
                <input
                    autoFocus
                    className="flex-1 text-xs bg-white/[0.08] border border-blue-500/50 rounded px-1 py-0.5 text-white outline-none min-w-0"
                    value={renameValue}
                    onChange={e => onRenameChange(e.target.value)}
                    onKeyDown={e => {
                        if (e.key === 'Enter') onRenameSubmit(node);
                        if (e.key === 'Escape') onRenameCancel();
                    }}
                    onBlur={onRenameCancel}
                    onClick={e => e.stopPropagation()}
                />
            ) : (
                <span className={cn(
                    'flex-1 text-xs truncate min-w-0',
                    isSelected ? 'text-blue-300 font-medium' : 'text-slate-400'
                )}>
                    {node.name}
                </span>
            )}
            <div className="hidden group-hover:flex items-center gap-0.5 ml-1 shrink-0">
                <button onClick={e => { e.stopPropagation(); onRun(node.path); }} title="Run" disabled={!!running}
                    className="p-0.5 rounded text-surface-600 hover:text-emerald-400 hover:bg-emerald-500/10 transition-colors disabled:opacity-40">
                    {running === node.path
                        ? <div className="w-2.5 h-2.5 border border-emerald-500 border-t-transparent rounded-full animate-spin" />
                        : <Play size={11} />
                    }
                </button>
                <button onClick={e => { e.stopPropagation(); onRenameStart(node); }} title="Rename"
                    className="p-0.5 rounded text-surface-600 hover:text-white hover:bg-white/10 transition-colors">
                    <Pencil size={11} />
                </button>
                <button onClick={e => { e.stopPropagation(); onDelete(node.path, false); }} title="Delete"
                    className="p-0.5 rounded text-surface-600 hover:text-rose-400 hover:bg-rose-500/10 transition-colors">
                    <Trash2 size={11} />
                </button>
            </div>
        </div>
    );
};

const countFiles = (nodes: FileTreeNode[]): number =>
    nodes.reduce((sum, n) => sum + (n.directory ? countFiles(n.children ?? []) : 1), 0);

// ─── Command Editor Modal ────────────────────────────────────────────────────

interface CmdModalState {
    lineIndex: number | null;
    type: 'WAIT' | 'CONNECT' | 'DISCONNECT' | 'RESET' | 'LOOP' | 'END';
    value: string;
}

interface CmdEditorProps {
    state: CmdModalState;
    sessions: string[];
    onChange: (s: CmdModalState) => void;
    onSubmit: () => void;
    onClose: () => void;
}

const CmdEditorModal: React.FC<CmdEditorProps> = ({ state, sessions, onChange, onSubmit, onClose }) => {
    const isInsert = state.lineIndex === null;
    const titleMap = { WAIT: 'Wait Step', CONNECT: 'Connect Step', DISCONNECT: 'Disconnect Step', RESET: 'Reset Step', LOOP: 'Loop Step', END: 'End Loop' };
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm" onClick={onClose}>
            <div className="bg-surface-950 border border-white/10 rounded-2xl shadow-2xl p-6 w-[400px]" onClick={e => e.stopPropagation()}>
                <div className="flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                        {state.type === 'WAIT' && <Clock size={16} className="text-amber-400" />}
                        {state.type === 'CONNECT' && <Link2 size={16} className="text-purple-400" />}
                        {state.type === 'DISCONNECT' && <LogOut size={16} className="text-orange-400" />}
                        {state.type === 'RESET' && <RotateCcw size={16} className="text-rose-400" />}
                        {state.type === 'LOOP' && <Repeat size={16} className="text-violet-400" />}
                        {state.type === 'END' && <Repeat size={16} className="text-violet-400" />}
                        <h3 className="font-bold text-sm text-white">{titleMap[state.type]}</h3>
                    </div>
                    <button onClick={onClose} className="p-1 rounded text-surface-500 hover:text-white transition-colors">
                        <X size={14} />
                    </button>
                </div>

                {state.type === 'WAIT' && (
                    <div>
                        <p className="text-[11px] text-surface-500 mb-4">Pause test execution for the specified duration before advancing to the next step.</p>
                        <label className="block text-xs text-surface-400 mb-1.5 font-medium">Delay (milliseconds)</label>
                        <input
                            autoFocus type="number" min={0} step={100}
                            value={state.value}
                            onChange={e => onChange({ ...state, value: e.target.value })}
                            onKeyDown={e => { if (e.key === 'Enter') onSubmit(); if (e.key === 'Escape') onClose(); }}
                            className="w-full bg-white/[0.05] border border-white/10 rounded-xl px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/20"
                        />
                        <p className="text-[10px] text-surface-600 mt-1.5">1000 ms = 1 second</p>
                    </div>
                )}

                {state.type === 'CONNECT' && (
                    <div>
                        <p className="text-[11px] text-surface-500 mb-4">Connect to a FIX session before sending or expecting messages. The session must be configured in FixEngine.cfg.</p>
                        <label className="block text-xs text-surface-400 mb-1.5 font-medium">Session ID</label>
                        <input
                            autoFocus list="cmd-sessions-list"
                            placeholder="e.g. FIX.4.2:SENDER->TARGET"
                            value={state.value}
                            onChange={e => onChange({ ...state, value: e.target.value })}
                            onKeyDown={e => { if (e.key === 'Enter') onSubmit(); if (e.key === 'Escape') onClose(); }}
                            className="w-full bg-white/[0.05] border border-white/10 rounded-xl px-3 py-2 text-sm text-white font-mono placeholder-surface-600 focus:outline-none focus:border-purple-500/50 focus:ring-1 focus:ring-purple-500/20"
                        />
                        <datalist id="cmd-sessions-list">
                            {sessions.map(s => <option key={s} value={s} />)}
                        </datalist>
                        {sessions.length > 0 && (
                            <p className="text-[10px] text-surface-600 mt-1.5">{sessions.length} configured session{sessions.length !== 1 ? 's' : ''} available</p>
                        )}
                    </div>
                )}

                {state.type === 'DISCONNECT' && (
                    <div>
                        <p className="text-[11px] text-surface-500 mb-4">
                            Disconnect the current acceptor session. This sends a Logout message to the counterparty and closes the FIX connection.
                            No parameters are required.
                        </p>
                        <div className="flex items-center gap-2 px-3 py-2 bg-orange-500/5 border border-orange-500/20 rounded-xl">
                            <LogOut size={13} className="text-orange-400 shrink-0" />
                            <span className="text-xs font-mono text-orange-300">eDISCONNECT</span>
                        </div>
                    </div>
                )}

                {state.type === 'RESET' && (
                    <div>
                        <p className="text-[11px] text-surface-500 mb-4">
                            Clears the generated ClOrdID for the given order prefix, allowing cancel/replace steps to reference a fresh order.
                            The prefix must match the ClOrdID prefix used in the preceding send step.
                        </p>
                        <label className="block text-xs text-surface-400 mb-1.5 font-medium">ClOrdID prefix</label>
                        <input
                            autoFocus type="text" placeholder="e.g. ORD001"
                            value={state.value}
                            onChange={e => onChange({ ...state, value: e.target.value })}
                            onKeyDown={e => { if (e.key === 'Enter') onSubmit(); if (e.key === 'Escape') onClose(); }}
                            className="w-full bg-white/[0.05] border border-white/10 rounded-xl px-3 py-2 text-sm text-white font-mono placeholder-surface-600 focus:outline-none focus:border-rose-500/50 focus:ring-1 focus:ring-rose-500/20"
                        />
                    </div>
                )}

                {state.type === 'LOOP' && (
                    <div>
                        <p className="text-[11px] text-surface-500 mb-4">
                            Repeat all steps between this LOOP and the matching END a fixed number of times.
                            Add an END step after the last step you want to repeat.
                        </p>
                        <label className="block text-xs text-surface-400 mb-1.5 font-medium">Iterations</label>
                        <input
                            autoFocus type="number" min={1} step={1}
                            value={state.value}
                            onChange={e => onChange({ ...state, value: e.target.value })}
                            onKeyDown={e => { if (e.key === 'Enter') onSubmit(); if (e.key === 'Escape') onClose(); }}
                            className="w-full bg-white/[0.05] border border-white/10 rounded-xl px-3 py-2 text-sm text-white font-mono focus:outline-none focus:border-violet-500/50 focus:ring-1 focus:ring-violet-500/20"
                        />
                        <p className="text-[10px] text-surface-600 mt-1.5">Steps inside the loop will execute this many times before continuing.</p>
                    </div>
                )}

                {state.type === 'END' && (
                    <div className="flex items-start gap-3 px-3 py-3 bg-violet-500/5 border border-violet-500/20 rounded-xl">
                        <Repeat size={14} className="text-violet-400 shrink-0 mt-0.5" />
                        <p className="text-[11px] text-surface-400 leading-relaxed">
                            Marks the end of a LOOP block. All steps between the matching LOOP and this END are repeated for the configured number of iterations. No parameters.
                        </p>
                    </div>
                )}

                <div className="flex justify-end gap-2 mt-5">
                    <button onClick={onClose}
                        className="px-4 py-2 rounded-xl text-xs font-bold text-surface-400 hover:text-white hover:bg-white/[0.05] transition-all">
                        {state.type === 'END' ? 'Close' : 'Cancel'}
                    </button>
                    {state.type !== 'END' && (
                        <button onClick={onSubmit}
                            className="px-4 py-2 rounded-xl text-xs font-bold bg-blue-600 hover:bg-blue-500 text-white transition-all">
                            {isInsert ? 'Add Step' : 'Save'}
                        </button>
                    )}
                </div>
            </div>
        </div>
    );
};

// ─── Step type badge ─────────────────────────────────────────────────────────

const StepBadge: React.FC<{ line: string }> = ({ line }) => {
    const t = line.trim();
    if (t === 'END') return (
        <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded select-none border shrink-0 bg-violet-500/10 text-violet-400 border-violet-500/20">End</span>
    );
    if (t.startsWith('I')) return (
        <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded select-none border shrink-0 bg-sky-500/10 text-sky-400 border-sky-500/20">Send</span>
    );
    if (t.startsWith('E')) return (
        <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded select-none border shrink-0 bg-emerald-500/10 text-emerald-400 border-emerald-500/20">Expect</span>
    );
    if (t.startsWith('WAIT')) return (
        <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded select-none border shrink-0 bg-amber-500/10 text-amber-400 border-amber-500/20">Wait</span>
    );
    if (t.startsWith('RESET')) return (
        <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded select-none border shrink-0 bg-rose-500/10 text-rose-400 border-rose-500/20">Reset</span>
    );
    if (t.includes('DISCONNECT')) return (
        <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded select-none border shrink-0 bg-orange-500/10 text-orange-400 border-orange-500/20">Disconn</span>
    );
    if (t.includes('CONNECT')) return (
        <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded select-none border shrink-0 bg-purple-500/10 text-purple-400 border-purple-500/20">Connect</span>
    );
    if (t.startsWith('LOOP')) return (
        <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded select-none border shrink-0 bg-violet-500/10 text-violet-400 border-violet-500/20">Loop</span>
    );
    if (t.length > 0 && !t.startsWith('#')) return (
        <span className="text-[9px] uppercase font-bold tracking-wider px-1.5 py-0.5 rounded select-none border shrink-0 bg-white/[0.04] text-surface-600 border-white/10">CMD</span>
    );
    return <span className="w-14 shrink-0" />;
};

// ─── Line style helper ────────────────────────────────────────────────────────

const getLineStyle = (line: string) => {
    const t = line.trim();
    if (t.startsWith('#')) return 'text-surface-600 italic';
    if (t === 'END' || t.startsWith('LOOP')) return 'text-violet-400 font-semibold';
    if (t.startsWith('I')) return 'text-sky-400';
    if (t.startsWith('E')) return 'text-emerald-400';
    if (t.startsWith('WAIT')) return 'text-amber-400 font-semibold';
    if (t.startsWith('RESET')) return 'text-rose-400';
    if (t.includes('DISCONNECT')) return 'text-orange-400 font-semibold';
    if (t.includes('CONNECT')) return 'text-purple-400 font-semibold';
    return 'text-slate-400';
};

const highlightMacros = (line: string) => {
    const parts = line.split(/(<[^>]+>)/g);
    return parts.map((part, i) =>
        /^<[^>]+>$/.test(part)
            ? <span key={i} className="text-amber-400 font-semibold">{part}</span>
            : part
    );
};

// Colorizes the inline expect-diff sentinels emitted by the backend:
//   «…» mismatch (rose), ⟪…⟫ missing/unexpected (amber), ⟨…⟩ ignored/dynamic (muted).
const highlightDiff = (line: string) => {
    const parts = line.split(/(«[^»]*»|⟪[^⟫]*⟫|⟨[^⟩]*⟩)/g);
    return parts.map((part, i) => {
        if (/^«.*»$/.test(part)) return <span key={i} className="text-rose-400 font-semibold">{part.slice(1, -1)}</span>;
        if (/^⟪.*⟫$/.test(part)) return <span key={i} className="text-amber-400 font-semibold">{part.slice(1, -1)}</span>;
        if (/^⟨.*⟩$/.test(part)) return <span key={i} className="text-surface-600">{part.slice(1, -1)}</span>;
        return part;
    });
};

const LogOutput = ({ text, errorMessage }: { text?: string; errorMessage?: string }) => (
    <div className="p-3 bg-white/[0.02] border-t border-white/[0.05] font-mono text-[10px] whitespace-pre-wrap text-surface-500 max-h-48 overflow-y-auto">
        {(text ?? '').split('\n').map((line, i) => {
            let lineClass = '';
            if (line.includes('Expect PASS')) lineClass = 'text-emerald-400 font-semibold';
            else if (line.includes('Expect FAIL')) lineClass = 'text-rose-400 font-semibold';
            return <div key={i} className={lineClass}>{highlightDiff(line)}</div>;
        })}
        {errorMessage && (
            <div className="mt-2 p-2 bg-rose-500/10 border border-rose-500/20 text-rose-400 rounded-lg">
                Error: {errorMessage}
            </div>
        )}
    </div>
);

// ─── Sortable step row ────────────────────────────────────────────────────────

interface SortableStepRowProps {
    id: string;
    line: string;
    index: number;
    depth: number;
    onEditFix: (line: string, index: number) => void;
    onEditCmd: (line: string, index: number) => void;
    onDelete: (index: number) => void;
}

function SortableStepRow({ id, line, index, depth, onEditFix, onEditCmd, onDelete }: SortableStepRowProps) {
    const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id });
    const t = line.trim();
    const isFixMsg = t.startsWith('I') || (t.startsWith('E') && t !== 'END');
    const isCmd = t.startsWith('WAIT') || t.startsWith('RESET') || t.includes('CONNECT') || t.includes('DISCONNECT') || t.startsWith('LOOP') || t === 'END';
    const isBlank = t.length === 0;

    return (
        <div
            ref={setNodeRef}
            style={{ transform: CSS.Transform.toString(transform), transition, opacity: isDragging ? 0.4 : 1, paddingLeft: depth * 16 }}
            className="flex items-center gap-2 py-0.5 px-1.5 rounded group hover:bg-white/[0.03] transition-colors"
        >
            <button
                {...attributes}
                {...listeners}
                className="cursor-grab active:cursor-grabbing text-surface-800 hover:text-surface-500 shrink-0 touch-none"
                title="Drag to reorder"
                tabIndex={-1}
            >
                <GripVertical size={12} />
            </button>
            <span className="w-5 select-none text-surface-700 text-right text-[9px] font-sans shrink-0">
                {index + 1}
            </span>
            <StepBadge line={line} />
            <span className={cn(
                'flex-1 font-mono break-all text-[11px] leading-tight min-w-0',
                getLineStyle(line),
                isBlank && 'opacity-0 select-none'
            )}>
                {isBlank ? '.' : highlightMacros(line)}
            </span>
            <div className="hidden group-hover:flex items-center gap-0.5 shrink-0">
                {isFixMsg && (
                    <button onClick={() => onEditFix(line, index)}
                        className="p-1 hover:bg-blue-600/20 text-surface-600 hover:text-blue-400 rounded transition-all"
                        title="Edit FIX Message">
                        <Edit size={11} />
                    </button>
                )}
                {isCmd && (
                    <button onClick={() => onEditCmd(line, index)}
                        className="p-1 hover:bg-amber-600/20 text-surface-600 hover:text-amber-400 rounded transition-all"
                        title="Edit Command">
                        <Edit size={11} />
                    </button>
                )}
                <button onClick={() => onDelete(index)}
                    className="p-1 hover:bg-rose-600/20 text-surface-600 hover:text-rose-400 rounded transition-all"
                    title="Delete Line">
                    <Trash2 size={11} />
                </button>
            </div>
        </div>
    );
}

// ─── FIX template for new steps ──────────────────────────────────────────────

const FIX_TEMPLATE = '8=FIX.4.2|9=0|35=D|49=SENDER|56=TARGET|34=1|52=20060101-00:00:00|10=000';

// ─── Main Component ───────────────────────────────────────────────────────────

export const TestRunner: React.FC = () => {
    const [fileTree, setFileTree] = useState<FileTreeNode[]>([]);
    const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
    const [renamingPath, setRenamingPath] = useState<string | null>(null);
    const [renameValue, setRenameValue] = useState('');
    const [results, setResults] = useState<TestResult[]>([]);
    const [running, setRunning] = useState<string | null>(null);

    const [selectedTest, setSelectedTest] = useState<string | null>(null);
    const [testContent, setTestContent] = useState<string>('');
    const [testLines, setTestLines] = useState<string[]>([]);
    const [rawMode, setRawMode] = useState<boolean>(false);
    const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');

    const [modalOpen, setModalOpen] = useState(false);
    const [modalLineIndex, setModalLineIndex] = useState<number | null>(null);
    const [modalInitialMsg, setModalInitialMsg] = useState('');
    const [modalTitle, setModalTitle] = useState('');
    const [modalPrefix, setModalPrefix] = useState<'I' | 'E'>('I');

    const [cmdModal, setCmdModal] = useState<CmdModalState | null>(null);
    const [addStepMenuOpen, setAddStepMenuOpen] = useState(false);
    const [availableSessions, setAvailableSessions] = useState<string[]>([]);

    const loadFileTree = () => {
        apiClient.get('/tests/tree').then(res => setFileTree(res.data));
    };

    useEffect(() => {
        loadFileTree();
        apiClient.get('/tests/results').then(res => setResults(res.data.reverse()));
        apiClient.get('/sessions').then(res => setAvailableSessions(res.data.map((s: { sessionId: string }) => s.sessionId)));
    }, []);

    const runTest = async (path: string) => {
        setRunning(path);
        try {
            const res = await apiClient.post(`/tests/run?path=${encodeURIComponent(path)}`);
            setResults(prev => [res.data, ...prev]);
        } finally {
            setRunning(null);
        }
    };

    const selectTestcase = async (path: string) => {
        setSelectedTest(path);
        setSaveStatus('idle');
        setRawMode(false);
        try {
            const res = await apiClient.get(`/tests/content?path=${encodeURIComponent(path)}`);
            const text = res.data;
            setTestContent(text);
            setTestLines(text.split(/\r?\n/));
        } catch {
            setTestContent('');
            setTestLines([]);
        }
    };

    const saveTestcase = async (contentStr: string) => {
        if (!selectedTest) return;
        setSaveStatus('saving');
        try {
            await apiClient.post(`/tests/content?path=${encodeURIComponent(selectedTest)}`, contentStr, {
                headers: {'Content-Type': 'text/plain'}
            });
            setSaveStatus('saved');
            setTestContent(contentStr);
            setTestLines(contentStr.split(/\r?\n/));
            setTimeout(() => setSaveStatus('idle'), 2000);
        } catch {
            setSaveStatus('error');
            setTimeout(() => setSaveStatus('idle'), 3000);
        }
    };

    // FIX message modal handlers
    const handleSaveFixMessage = (updatedMsg: string) => {
        const newLines = [...testLines];
        if (modalLineIndex === null) {
            newLines.push(modalPrefix + updatedMsg);
        } else {
            newLines[modalLineIndex] = modalPrefix + updatedMsg;
        }
        saveTestcase(newLines.join('\n'));
    };

    const openFixModal = (line: string, index: number) => {
        const prefix = line.startsWith('I') ? 'I' : 'E';
        setModalLineIndex(index);
        setModalPrefix(prefix);
        setModalInitialMsg(line.substring(1));
        setModalTitle(`Edit ${prefix === 'I' ? 'Outgoing' : 'Expected'} Message (Line ${index + 1})`);
        setModalOpen(true);
    };

    const openAddFixModal = (prefix: 'I' | 'E') => {
        setAddStepMenuOpen(false);
        setModalLineIndex(null);
        setModalPrefix(prefix);
        setModalInitialMsg(FIX_TEMPLATE);
        setModalTitle(`Add ${prefix === 'I' ? 'Outgoing Send' : 'Expected Receive'} FIX Message`);
        setModalOpen(true);
    };

    // Command modal handlers
    const openCmdEdit = (line: string, index: number) => {
        const t = line.trim();
        if (t.startsWith('WAIT')) {
            setCmdModal({ lineIndex: index, type: 'WAIT', value: t.split(/\s+/)[1] || '1000' });
        } else if (t.startsWith('RESET')) {
            const parts = t.split(/\s+/);
            setCmdModal({ lineIndex: index, type: 'RESET', value: parts[1] || '' });
        } else if (t.includes('DISCONNECT')) {
            setCmdModal({ lineIndex: index, type: 'DISCONNECT', value: '' });
        } else if (t.includes('CONNECT')) {
            const parts = t.split(/\s+/);
            setCmdModal({ lineIndex: index, type: 'CONNECT', value: parts[1] || '' });
        } else if (t.startsWith('LOOP')) {
            const parts = t.split(/\s+/);
            setCmdModal({ lineIndex: index, type: 'LOOP', value: parts[1] || '3' });
        } else if (t === 'END') {
            setCmdModal({ lineIndex: index, type: 'END', value: '' });
        }
    };

    const openAddCmd = (type: 'WAIT' | 'CONNECT' | 'DISCONNECT' | 'RESET' | 'LOOP') => {
        setAddStepMenuOpen(false);
        const defaultValue = type === 'WAIT' ? '1000' : type === 'LOOP' ? '3' : '';
        setCmdModal({ lineIndex: null, type, value: defaultValue });
    };

    const addEndStep = () => {
        setAddStepMenuOpen(false);
        saveTestcase([...testLines, 'END'].join('\n'));
    };

    const submitCmdModal = () => {
        if (!cmdModal) return;
        let line = '';
        if (cmdModal.type === 'WAIT') line = `WAIT ${cmdModal.value || '1000'}`;
        else if (cmdModal.type === 'CONNECT') line = `iCONNECT ${cmdModal.value}`;
        else if (cmdModal.type === 'DISCONNECT') line = 'eDISCONNECT';
        else if (cmdModal.type === 'LOOP') line = `LOOP ${cmdModal.value || '3'}`;
        else if (cmdModal.type === 'END') { setCmdModal(null); return; }
        else line = cmdModal.value.trim() ? `RESET ${cmdModal.value.trim()}` : 'RESET';

        const newLines = [...testLines];
        if (cmdModal.lineIndex === null) {
            newLines.push(line);
        } else {
            newLines[cmdModal.lineIndex] = line;
        }
        saveTestcase(newLines.join('\n'));
        setCmdModal(null);
    };

    const deleteLine = (index: number) => {
        saveTestcase(testLines.filter((_, i) => i !== index).join('\n'));
    };

    const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 8 } }));

    const handleDragEnd = (event: DragEndEvent) => {
        const { active, over } = event;
        if (!over || active.id === over.id) return;
        const reordered = arrayMove(testLines, Number(active.id), Number(over.id));
        saveTestcase(reordered.join('\n'));
    };

    // Compute indentation depth for each line based on LOOP/END nesting
    const depths: number[] = [];
    let loopDepth = 0;
    for (let i = 0; i < testLines.length; i++) {
        const t = testLines[i].trim();
        if (t === 'END') loopDepth = Math.max(0, loopDepth - 1);
        depths[i] = loopDepth;
        if (t.startsWith('LOOP')) loopDepth++;
    }

    // File tree handlers
    const handleToggleFolder = (path: string) => {
        setExpandedFolders(prev => {
            const next = new Set(prev);
            next.has(path) ? next.delete(path) : next.add(path);
            return next;
        });
    };

    const handleCreateFile = async (parentPath: string) => {
        const name = window.prompt('File name (e.g. my_test.def):');
        if (!name?.trim()) return;
        const path = parentPath ? `${parentPath}/${name.trim()}` : name.trim();
        try {
            await apiClient.post('/tests/file', { path, content: '' });
            if (parentPath) setExpandedFolders(prev => new Set([...prev, parentPath]));
            loadFileTree();
        } catch (err: any) {
            alert(err.response?.data || 'Failed to create file');
        }
    };

    const handleCreateDir = async (parentPath: string) => {
        const name = window.prompt('Folder name:');
        if (!name?.trim()) return;
        const path = parentPath ? `${parentPath}/${name.trim()}` : name.trim();
        try {
            await apiClient.post('/tests/directory', { path });
            if (parentPath) setExpandedFolders(prev => new Set([...prev, parentPath]));
            loadFileTree();
        } catch (err: any) {
            alert(err.response?.data || 'Failed to create folder');
        }
    };

    const handleRenameStart = (node: FileTreeNode) => {
        setRenamingPath(node.path);
        setRenameValue(node.name);
    };

    const handleRenameSubmit = async (node: FileTreeNode) => {
        const newName = renameValue.trim();
        setRenamingPath(null);
        if (!newName || newName === node.name) return;
        const parentDir = node.path.includes('/')
            ? node.path.substring(0, node.path.lastIndexOf('/'))
            : '';
        const newPath = parentDir ? `${parentDir}/${newName}` : newName;
        try {
            await apiClient.put('/tests/rename', { path: node.path, newPath });
            if (selectedTest === node.path) setSelectedTest(newPath);
            loadFileTree();
        } catch (err: any) {
            alert(err.response?.data || 'Failed to rename');
        }
    };

    const handleDelete = async (path: string, isDir: boolean) => {
        if (!window.confirm(`Delete ${isDir ? 'folder' : 'file'} "${path}"${isDir ? ' and all its contents' : ''}?`)) return;
        try {
            await apiClient.delete(`/tests/node?path=${encodeURIComponent(path)}`);
            if (selectedTest === path || (isDir && selectedTest?.startsWith(path + '/'))) {
                setSelectedTest(null);
                setTestContent('');
                setTestLines([]);
            }
            loadFileTree();
        } catch (err: any) {
            alert(err.response?.data || 'Failed to delete');
        }
    };

    const fileTreeProps = {
        selectedTest, running, expandedFolders, renamingPath, renameValue,
        onToggleFolder: handleToggleFolder, onSelect: selectTestcase, onRun: runTest,
        onRenameStart: handleRenameStart, onRenameChange: setRenameValue,
        onRenameSubmit: handleRenameSubmit, onRenameCancel: () => setRenamingPath(null),
        onDelete: handleDelete, onCreateFile: handleCreateFile, onCreateDir: handleCreateDir,
    };

    const fileCount = countFiles(fileTree);

    return (
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-4 h-full">

            {/* Column 1 – File Explorer */}
            <div className="lg:col-span-1 bg-surface-950 border border-white/5 rounded-2xl overflow-hidden flex flex-col shadow-lg">
                <div className="px-3 py-2.5 border-b border-white/5 bg-white/[0.02] flex items-center gap-1.5 shrink-0">
                    <FileCode size={14} className="text-blue-400 shrink-0" />
                    <h2 className="font-semibold text-xs uppercase tracking-wider text-surface-400 flex-1">Tests</h2>
                    {fileCount > 0 && (
                        <span className="px-1.5 py-0.5 bg-blue-500/10 text-blue-400 text-[10px] font-bold rounded-full">{fileCount}</span>
                    )}
                    <button onClick={() => handleCreateFile('')} title="New file at root"
                        className="p-1 rounded text-surface-500 hover:text-blue-400 hover:bg-blue-500/10 transition-colors">
                        <FilePlus size={13} />
                    </button>
                    <button onClick={() => handleCreateDir('')} title="New folder at root"
                        className="p-1 rounded text-surface-500 hover:text-amber-400 hover:bg-amber-500/10 transition-colors">
                        <FolderPlus size={13} />
                    </button>
                </div>
                <div className="flex-1 overflow-y-auto py-1.5 px-1.5">
                    {fileTree.length === 0 ? (
                        <div className="py-12 flex flex-col items-center justify-center text-surface-500">
                            <FileCode size={28} className="mb-2 opacity-20" />
                            <p className="text-xs">No test scripts found</p>
                        </div>
                    ) : fileTree.map(node => (
                        <FileTreeEntry key={node.path} node={node} depth={0} {...fileTreeProps} />
                    ))}
                </div>
            </div>

            {/* Columns 2-5 – Editor + History */}
            <div className="lg:col-span-4 flex flex-col h-full overflow-hidden gap-4">
                {selectedTest ? (
                    <>
                    <div className="flex-[2] bg-surface-950 border border-white/5 rounded-2xl overflow-hidden flex flex-col shadow-lg min-h-0">
                        {/* Editor header */}
                        <div className="p-4 border-b border-white/5 bg-white/[0.02] flex items-center justify-between shrink-0">
                            <div className="truncate pr-4">
                                <h3 className="font-bold text-sm text-white truncate">{selectedTest}</h3>
                                <p className="text-[11px] text-surface-500 mt-0.5">Edit test steps and run</p>
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                                <button
                                    onClick={() => setRawMode(!rawMode)}
                                    className={cn(
                                        'px-3 py-1.5 rounded-lg text-[11px] font-bold flex items-center gap-1.5 transition-all',
                                        rawMode
                                            ? 'bg-blue-600 text-white shadow-md'
                                            : 'bg-white/[0.05] hover:bg-white/[0.08] text-surface-400 hover:text-white'
                                    )}
                                >
                                    <Code size={13} />
                                    {rawMode ? 'Visual' : 'Raw'}
                                </button>
                                <button
                                    onClick={() => runTest(selectedTest)}
                                    disabled={!!running}
                                    className={cn(
                                        'px-3 py-1.5 rounded-lg text-[11px] font-bold flex items-center gap-1.5 transition-all',
                                        running === selectedTest
                                            ? 'bg-emerald-600/20 text-emerald-400'
                                            : 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-md shadow-emerald-600/10'
                                    )}
                                >
                                    {running === selectedTest
                                        ? <div className="w-3 h-3 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
                                        : <Play size={13} />
                                    }
                                    Run
                                </button>
                                <button onClick={() => setSelectedTest(null)}
                                    className="p-1.5 rounded-lg text-surface-500 hover:text-white hover:bg-white/[0.05] transition-all"
                                    title="Close Editor">
                                    <X size={14} />
                                </button>
                            </div>
                        </div>

                        {/* Editor body */}
                        <div className="flex-1 overflow-hidden flex flex-col">
                            {rawMode ? (
                                <div className="flex-1 flex flex-col gap-3 p-4 overflow-hidden">
                                    <textarea
                                        value={testContent}
                                        onChange={e => setTestContent(e.target.value)}
                                        className="flex-1 w-full bg-white/[0.03] border border-white/5 rounded-xl p-4 font-mono text-[11px] text-slate-300 focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/50 resize-none"
                                    />
                                    <div className="flex items-center justify-between shrink-0">
                                        <span className="text-[11px] text-surface-500">Make changes and click Save to write to disk.</span>
                                        <button
                                            onClick={() => saveTestcase(testContent)}
                                            disabled={saveStatus === 'saving'}
                                            className={cn(
                                                'px-4 py-2 rounded-xl flex items-center gap-2 text-xs font-bold shadow-lg transition-all',
                                                saveStatus === 'saved'
                                                    ? 'bg-emerald-600/20 text-emerald-400'
                                                    : 'bg-blue-600 hover:bg-blue-500 text-white'
                                            )}
                                        >
                                            {saveStatus === 'saving'
                                                ? <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                                                : saveStatus === 'saved' ? <Check size={13} /> : <Save size={13} />
                                            }
                                            {saveStatus === 'saving' ? 'Saving…' : saveStatus === 'saved' ? 'Saved' : 'Save'}
                                        </button>
                                    </div>
                                </div>
                            ) : (
                                <div className="flex-1 overflow-hidden flex flex-col">
                                    {/* Step list */}
                                    <div className="flex-1 overflow-y-auto px-2 py-1">
                                        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                                            <SortableContext items={testLines.map((_, i) => String(i))} strategy={verticalListSortingStrategy}>
                                                {testLines.map((line, index) => (
                                                    <SortableStepRow
                                                        key={index}
                                                        id={String(index)}
                                                        line={line}
                                                        index={index}
                                                        depth={depths[index] ?? 0}
                                                        onEditFix={openFixModal}
                                                        onEditCmd={openCmdEdit}
                                                        onDelete={deleteLine}
                                                    />
                                                ))}
                                            </SortableContext>
                                        </DndContext>
                                    </div>

                                    {/* Add step row */}
                                    <div className="border-t border-white/5 px-2 py-1.5 shrink-0 relative">
                                        <button
                                            onClick={() => setAddStepMenuOpen(v => !v)}
                                            className="w-full flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-medium text-surface-500 hover:text-white hover:bg-white/[0.05] transition-colors"
                                        >
                                            <Plus size={12} />
                                            Add Step
                                        </button>
                                        {addStepMenuOpen && (
                                            <>
                                                <div className="fixed inset-0 z-10" onClick={() => setAddStepMenuOpen(false)} />
                                                <div className="absolute bottom-full left-2 mb-1 z-20 bg-surface-900 border border-white/10 rounded-xl shadow-2xl p-1.5 min-w-[220px]">
                                                    <button onClick={() => openAddFixModal('I')}
                                                        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-sky-500/10 text-left transition-colors group">
                                                        <span className="text-[9px] uppercase font-bold px-1.5 py-0.5 rounded bg-sky-500/10 text-sky-400 border border-sky-500/20 shrink-0">Send</span>
                                                        <span className="text-xs text-slate-400 group-hover:text-white">Send FIX Message</span>
                                                    </button>
                                                    <button onClick={() => openAddFixModal('E')}
                                                        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-emerald-500/10 text-left transition-colors group">
                                                        <span className="text-[9px] uppercase font-bold px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 shrink-0">Expect</span>
                                                        <span className="text-xs text-slate-400 group-hover:text-white">Expect FIX Message</span>
                                                    </button>
                                                    <div className="my-1 border-t border-white/5" />
                                                    <button onClick={() => openAddCmd('WAIT')}
                                                        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-amber-500/10 text-left transition-colors">
                                                        <Clock size={13} className="text-amber-400 shrink-0" />
                                                        <span className="text-xs text-slate-400 hover:text-white">Wait</span>
                                                    </button>
                                                    <button onClick={() => openAddCmd('CONNECT')}
                                                        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-purple-500/10 text-left transition-colors">
                                                        <Link2 size={13} className="text-purple-400 shrink-0" />
                                                        <span className="text-xs text-slate-400 hover:text-white">Connect Session</span>
                                                    </button>
                                                    <button onClick={() => openAddCmd('DISCONNECT')}
                                                        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-orange-500/10 text-left transition-colors">
                                                        <LogOut size={13} className="text-orange-400 shrink-0" />
                                                        <span className="text-xs text-slate-400 hover:text-white">Disconnect</span>
                                                    </button>
                                                    <button onClick={() => openAddCmd('RESET')}
                                                        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-rose-500/10 text-left transition-colors">
                                                        <RotateCcw size={13} className="text-rose-400 shrink-0" />
                                                        <span className="text-xs text-slate-400 hover:text-white">Reset</span>
                                                    </button>
                                                    <div className="my-1 border-t border-white/5" />
                                                    <button onClick={() => openAddCmd('LOOP')}
                                                        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-violet-500/10 text-left transition-colors">
                                                        <Repeat size={13} className="text-violet-400 shrink-0" />
                                                        <span className="text-xs text-slate-400 hover:text-white">Loop N times…</span>
                                                    </button>
                                                    <button onClick={addEndStep}
                                                        className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg hover:bg-violet-500/10 text-left transition-colors">
                                                        <span className="text-[9px] uppercase font-bold px-1.5 py-0.5 rounded bg-violet-500/10 text-violet-400 border border-violet-500/20 shrink-0">End</span>
                                                        <span className="text-xs text-slate-400 hover:text-white">End Loop</span>
                                                    </button>
                                                </div>
                                            </>
                                        )}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Run History (collapsed strip) */}
                    <div className="flex-1 bg-surface-950 border border-white/5 rounded-2xl overflow-hidden flex flex-col shadow-lg min-h-0 animate-slide-up">
                        <div className="p-3.5 border-b border-white/5 bg-white/[0.02] flex items-center justify-between shrink-0">
                            <div className="flex items-center gap-2">
                                <History size={14} className="text-purple-400" />
                                <h2 className="font-semibold text-xs uppercase tracking-wider text-surface-400">Test Run History</h2>
                            </div>
                            {results.length > 0 && (
                                <span className="text-[10px] text-surface-600">{results.length} run{results.length !== 1 ? 's' : ''}</span>
                            )}
                        </div>
                        <div className="flex-1 overflow-y-auto">
                            {results.length === 0 ? (
                                <div className="h-full flex flex-col items-center justify-center text-surface-500">
                                    <History size={28} className="mb-2 opacity-20" />
                                    <p className="text-xs">No runs recorded yet — click Run to execute this test</p>
                                </div>
                            ) : (
                                <div className="divide-y divide-white/[0.03]">
                                    {results.map(result => (
                                        <details key={result.id} className="group">
                                            <summary className="flex items-center justify-between p-3 cursor-pointer hover:bg-white/[0.02] transition-colors list-none">
                                                <div className="flex items-center gap-3">
                                                    {result.success
                                                        ? <CheckCircle2 size={18} className="text-emerald-500" />
                                                        : <XCircle size={18} className="text-rose-500" />
                                                    }
                                                    <div>
                                                        <div className="text-xs font-semibold text-slate-200">{result.testName}</div>
                                                        <div className="text-[10px] text-surface-600">{new Date(result.startTime).toLocaleString()}</div>
                                                    </div>
                                                </div>
                                                <ChevronRight size={14} className="text-surface-600 group-open:rotate-90 transition-transform" />
                                            </summary>
                                            <LogOutput text={result.logOutput} errorMessage={result.errorMessage} />
                                        </details>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                    </>
                ) : (
                    /* No test selected – show full history */
                    <div className="bg-surface-950 border border-white/5 rounded-2xl overflow-hidden flex flex-col h-full shadow-lg">
                        <div className="p-4 border-b border-white/5 bg-white/[0.02] shrink-0">
                            <div className="flex items-center gap-2">
                                <History size={18} className="text-purple-400" />
                                <h2 className="font-semibold text-sm uppercase tracking-wider text-surface-400">Test Run History</h2>
                            </div>
                        </div>
                        <div className="flex-1 overflow-y-auto">
                            {results.length === 0 ? (
                                <div className="h-full flex flex-col items-center justify-center text-surface-500">
                                    <History size={40} className="mb-3 opacity-20" />
                                    <p className="text-sm">No tests run yet</p>
                                    <p className="text-xs text-surface-600 mt-1">Select a test from the sidebar and click Run</p>
                                </div>
                            ) : (
                                <div className="divide-y divide-white/[0.03]">
                                    {results.map(result => (
                                        <details key={result.id} className="group">
                                            <summary className="flex items-center justify-between p-4 cursor-pointer hover:bg-white/[0.02] transition-colors list-none">
                                                <div className="flex items-center gap-3">
                                                    {result.success
                                                        ? <CheckCircle2 size={18} className="text-emerald-500" />
                                                        : <XCircle size={18} className="text-rose-500" />
                                                    }
                                                    <div>
                                                        <div className="text-xs font-semibold text-slate-200">{result.testName}</div>
                                                        <div className="text-[10px] text-surface-600">{new Date(result.startTime).toLocaleString()}</div>
                                                    </div>
                                                </div>
                                                <ChevronRight size={14} className="text-surface-600 group-open:rotate-90 transition-transform" />
                                            </summary>
                                            <LogOutput text={result.logOutput} errorMessage={result.errorMessage} />
                                        </details>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </div>

            {/* FIX Message Editor Modal */}
            <FixMessageEditorModal
                isOpen={modalOpen}
                onClose={() => setModalOpen(false)}
                initialMessage={modalInitialMsg}
                title={modalTitle}
                onSave={handleSaveFixMessage}
            />

            {/* Command Editor Modal */}
            {cmdModal && (
                <CmdEditorModal
                    state={cmdModal}
                    sessions={availableSessions}
                    onChange={setCmdModal}
                    onSubmit={submitCmdModal}
                    onClose={() => setCmdModal(null)}
                />
            )}
        </div>
    );
};
