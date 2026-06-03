import React, {useEffect, useState} from 'react';
import {apiClient} from '../api/client';
import {TestResult} from '../types';
import {cn} from '../lib/utils';
import {Check, CheckCircle2, ChevronRight, Code, Edit, FileCode, History, Play, Save, X, XCircle} from 'lucide-react';
import {FixMessageEditorModal} from './FixMessageEditorModal';

export const TestRunner: React.FC = () => {
    const [availableTests, setAvailableTests] = useState<string[]>([]);
    const [results, setResults] = useState<TestResult[]>([]);
    const [running, setRunning] = useState<string | null>(null);

    // Selected test and its content state
    const [selectedTest, setSelectedTest] = useState<string | null>(null);
    const [testContent, setTestContent] = useState<string>('');
    const [testLines, setTestLines] = useState<string[]>([]);
    const [rawMode, setRawMode] = useState<boolean>(false);
    const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');

    // FIX Message Modal state
    const [modalOpen, setModalOpen] = useState(false);
    const [modalLineIndex, setModalLineIndex] = useState<number | null>(null);
    const [modalInitialMsg, setModalInitialMsg] = useState('');
    const [modalTitle, setModalTitle] = useState('');
    const [modalPrefix, setModalPrefix] = useState<'I' | 'E'>('I');

    useEffect(() => {
        apiClient.get('/tests').then((res) => setAvailableTests(res.data));
        apiClient.get('/tests/results').then((res) => setResults(res.data.reverse()));
    }, []);

    const runTest = async (path: string) => {
        setRunning(path);
        try {
            const res = await apiClient.post(`/tests/run?path=${encodeURIComponent(path)}`);
            setResults((prev) => [res.data, ...prev]);
        } finally {
            setRunning(null);
        }
    };

    // Load testcase content
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

    // Save testcase content
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

    // Save updated FIX message from modal
    const handleSaveFixMessage = (updatedMsg: string) => {
        if (modalLineIndex === null) return;
        const newLines = [...testLines];
        newLines[modalLineIndex] = modalPrefix + updatedMsg;
        const newContent = newLines.join('\n');
        saveTestcase(newContent);
    };

    // Open FIX Modal for a specific line
    const openFixModal = (line: string, index: number) => {
        const prefix = line.startsWith('I') ? 'I' : 'E';
        const msg = line.substring(1);
        setModalLineIndex(index);
        setModalPrefix(prefix);
        setModalInitialMsg(msg);
        setModalTitle(`Edit ${prefix === 'I' ? 'Outgoing' : 'Expected'} Message (Line ${index + 1})`);
        setModalOpen(true);
    };

    // Line helper to determine styling/actions
    const getLineStyle = (line: string) => {
        const trimmed = line.trim();
        if (trimmed.startsWith('#')) return 'text-surface-600 italic';
        if (trimmed.startsWith('I')) return 'text-sky-400 font-mono';
        if (trimmed.startsWith('E')) return 'text-emerald-400 font-mono';
        if (trimmed.startsWith('WAIT')) return 'text-amber-400 font-mono font-semibold';
        if (trimmed.startsWith('RESET')) return 'text-rose-400 font-mono';
        if (trimmed.includes('CONNECT')) return 'text-purple-400 font-mono font-semibold';
        return 'text-slate-400';
    };

    return (
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-4 h-[calc(100vh-9rem)]">

            {/* Column 1: Test List Sidebar */}
            <div className="lg:col-span-1 bg-surface-950 border border-white/5 rounded-2xl overflow-hidden flex flex-col shadow-lg">
                <div className="p-4 border-b border-white/5 bg-white/[0.02] flex items-center gap-2 shrink-0">
                    <FileCode size={16} className="text-blue-400" />
                    <h2 className="font-semibold text-sm uppercase tracking-wider text-surface-400">Available Tests</h2>
                    {availableTests.length > 0 && (
                        <span className="ml-auto px-1.5 py-0.5 bg-blue-500/10 text-blue-400 text-[10px] font-bold rounded-full">
                            {availableTests.length}
                        </span>
                    )}
                </div>
                <div className="flex-1 overflow-y-auto p-3 space-y-2">
                    {availableTests.length === 0 ? (
                        <div className="py-12 flex flex-col items-center justify-center text-surface-500">
                            <FileCode size={28} className="mb-2 opacity-20" />
                            <p className="text-xs">No test scripts found</p>
                        </div>
                    ) : availableTests.map((test) => (
                        <div
                            key={test}
                            className={cn(
                                'p-3 rounded-xl border transition-all duration-150 flex flex-col gap-2',
                                selectedTest === test
                                    ? 'bg-blue-600/[0.07] border-blue-500/30'
                                    : 'bg-white/[0.02] border-white/5 hover:bg-white/[0.04] hover:border-white/10'
                            )}
                        >
                            <span className="text-xs font-semibold text-slate-300 truncate" title={test}>
                                {test}
                            </span>
                            <div className="flex gap-1.5">
                                <button
                                    onClick={() => selectTestcase(test)}
                                    className={cn(
                                        'flex-1 py-1.5 rounded-lg text-[11px] font-bold flex items-center justify-center gap-1 transition-all',
                                        selectedTest === test
                                            ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                                            : 'bg-white/[0.05] hover:bg-white/[0.08] text-surface-400 hover:text-slate-200'
                                    )}
                                >
                                    <Edit size={11} />
                                    Edit
                                </button>
                                <button
                                    onClick={() => runTest(test)}
                                    disabled={!!running}
                                    className={cn(
                                        'flex-1 py-1.5 rounded-lg text-[11px] font-bold flex items-center justify-center gap-1 transition-all',
                                        running === test
                                            ? 'bg-emerald-600/20 text-emerald-400 cursor-wait'
                                            : 'bg-emerald-600/10 hover:bg-emerald-600 text-emerald-400 hover:text-white'
                                    )}
                                >
                                    {running === test ? (
                                        <div className="w-3 h-3 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
                                    ) : (
                                        <Play size={11} />
                                    )}
                                    {running === test ? 'Running' : 'Run'}
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* Columns 2-5: Editor + History Area */}
            <div className="lg:col-span-4 flex flex-col h-full overflow-hidden gap-4">
                {selectedTest ? (
                    <>
                    <div className="flex-1 bg-surface-950 border border-white/5 rounded-2xl overflow-hidden flex flex-col shadow-lg">
                        {/* Editor Header */}
                        <div className="p-4 border-b border-white/5 bg-white/[0.02] flex items-center justify-between shrink-0">
                            <div className="truncate pr-4">
                                <h3 className="font-bold text-sm text-white truncate">{selectedTest}</h3>
                                <p className="text-[11px] text-surface-500 mt-0.5">Edit test steps and run</p>
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                                {/* Toggle Mode */}
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

                                {/* Run Test */}
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
                                    {running === selectedTest ? (
                                        <div className="w-3 h-3 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin" />
                                    ) : (
                                        <Play size={13} />
                                    )}
                                    Run
                                </button>

                                {/* Close Editor */}
                                <button
                                    onClick={() => setSelectedTest(null)}
                                    className="p-1.5 rounded-lg text-surface-500 hover:text-white hover:bg-white/[0.05] transition-all"
                                    title="Close Editor"
                                >
                                    <X size={14} />
                                </button>
                            </div>
                        </div>

                        {/* Editor Content Area */}
                        <div className="flex-1 overflow-y-auto p-4 font-mono">
                            {rawMode ? (
                                /* Raw Editor Mode */
                                <div className="h-full flex flex-col gap-3">
                                    <textarea
                                        value={testContent}
                                        onChange={(e) => setTestContent(e.target.value)}
                                        className="flex-1 w-full bg-white/[0.03] border border-white/5 rounded-xl p-4 font-mono text-[11px] text-slate-300 focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/50 resize-none"
                                    />
                                    <div className="flex items-center justify-between">
                                        <span className="text-[11px] text-surface-500 font-sans">
                                            Make changes and click 'Save' to write to disk.
                                        </span>
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
                                            {saveStatus === 'saving' ? (
                                                <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                                            ) : saveStatus === 'saved' ? (
                                                <Check size={13} />
                                            ) : (
                                                <Save size={13} />
                                            )}
                                            {saveStatus === 'saving' ? 'Saving...' : saveStatus === 'saved' ? 'Saved' : 'Save'}
                                        </button>
                                    </div>
                                </div>
                            ) : (
                                /* Visual Step-by-Step Editor Mode */
                                <div className="space-y-1">
                                    {testLines.map((line, index) => {
                                        const isFixMessage = line.startsWith('I') || line.startsWith('E');
                                        const trimmed = line.trim();
                                        const isActionable = trimmed.length > 0 && !trimmed.startsWith('#');

                                        return (
                                            <div
                                                key={index}
                                                className={cn(
                                                    'flex items-start gap-3 p-2 rounded-lg group transition-colors',
                                                    isFixMessage ? 'hover:bg-white/[0.02]' : ''
                                                )}
                                            >
                                                {/* Line number */}
                                                <span className="w-7 select-none text-surface-700 text-right pr-2 text-[10px] font-sans mt-0.5">
                                                    {index + 1}
                                                </span>

                                                {/* Line badge */}
                                                {isFixMessage ? (
                                                    <span className={cn(
                                                        'text-[9px] uppercase font-bold tracking-wider px-2 py-0.5 rounded mt-0.5 select-none border',
                                                        line.startsWith('I')
                                                            ? 'bg-sky-500/10 text-sky-400 border-sky-500/20'
                                                            : 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20'
                                                    )}>
                                                        {line.startsWith('I') ? 'Send' : 'Expect'}
                                                    </span>
                                                ) : isActionable ? (
                                                    <span className="text-[9px] uppercase font-bold tracking-wider px-2 py-0.5 rounded mt-0.5 select-none bg-white/[0.05] text-surface-500 border border-white/10">
                                                        CMD
                                                    </span>
                                                ) : (
                                                    <span className="w-14" />
                                                )}

                                                {/* Line content */}
                                                <span className={cn('flex-1 break-all whitespace-pre-wrap text-[11px] leading-relaxed', getLineStyle(line))}>
                                                    {line}
                                                </span>

                                                {/* Line actions */}
                                                {isFixMessage && (
                                                    <button
                                                        onClick={() => openFixModal(line, index)}
                                                        className="p-1 opacity-0 group-hover:opacity-100 bg-white/[0.05] hover:bg-blue-600/20 text-surface-500 hover:text-blue-400 rounded-lg transition-all shrink-0"
                                                        title="Edit in FIX Message Modal"
                                                    >
                                                        <Edit size={12} />
                                                    </button>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="h-64 bg-surface-950 border border-white/5 rounded-2xl overflow-hidden flex flex-col shadow-lg shrink-0 animate-slide-up">
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
                                    {results.map((result) => (
                                        <details key={result.id} className="group">
                                            <summary className="flex items-center justify-between p-3 cursor-pointer hover:bg-white/[0.02] transition-colors list-none">
                                                <div className="flex items-center gap-3">
                                                    {result.success ? (
                                                        <CheckCircle2 size={18} className="text-emerald-500" />
                                                    ) : (
                                                        <XCircle size={18} className="text-rose-500" />
                                                    )}
                                                    <div>
                                                        <div className="text-xs font-semibold text-slate-200">{result.testName}</div>
                                                        <div className="text-[10px] text-surface-600">
                                                            {new Date(result.startTime).toLocaleString()}
                                                        </div>
                                                    </div>
                                                </div>
                                                <ChevronRight size={14} className="text-surface-600 group-open:rotate-90 transition-transform" />
                                            </summary>
                                            <div className="p-3 bg-white/[0.02] border-t border-white/[0.05] font-mono text-[10px] whitespace-pre-wrap text-surface-500 max-h-48 overflow-y-auto">
                                                {result.logOutput}
                                                {result.errorMessage && (
                                                    <div className="mt-2 p-2 bg-rose-500/10 border border-rose-500/20 text-rose-400 rounded-lg">
                                                        Error: {result.errorMessage}
                                                    </div>
                                                )}
                                            </div>
                                        </details>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                    </>
                ) : (
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
                                    <p className="text-xs text-surface-600 mt-1">Select a test from the sidebar and click Run or Edit</p>
                                </div>
                            ) : (
                                <div className="divide-y divide-white/[0.03]">
                                    {results.map((result) => (
                                        <details key={result.id} className="group">
                                            <summary className="flex items-center justify-between p-4 cursor-pointer hover:bg-white/[0.02] transition-colors list-none">
                                                <div className="flex items-center gap-3">
                                                    {result.success ? (
                                                        <CheckCircle2 size={18} className="text-emerald-500" />
                                                    ) : (
                                                        <XCircle size={18} className="text-rose-500" />
                                                    )}
                                                    <div>
                                                        <div className="text-xs font-semibold text-slate-200">{result.testName}</div>
                                                        <div className="text-[10px] text-surface-600">
                                                            {new Date(result.startTime).toLocaleString()}
                                                        </div>
                                                    </div>
                                                </div>
                                                <ChevronRight size={14} className="text-surface-600 group-open:rotate-90 transition-transform" />
                                            </summary>
                                            <div className="p-3 bg-white/[0.02] border-t border-white/[0.05] font-mono text-[10px] whitespace-pre-wrap text-surface-500 max-h-48 overflow-y-auto">
                                                {result.logOutput}
                                                {result.errorMessage && (
                                                    <div className="mt-2 p-2 bg-rose-500/10 border border-rose-500/20 text-rose-400 rounded-lg">
                                                        Error: {result.errorMessage}
                                                    </div>
                                                )}
                                            </div>
                                        </details>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                )}
            </div>

            <FixMessageEditorModal
                isOpen={modalOpen}
                onClose={() => setModalOpen(false)}
                initialMessage={modalInitialMsg}
                title={modalTitle}
                onSave={handleSaveFixMessage}
            />
        </div>
    );
};