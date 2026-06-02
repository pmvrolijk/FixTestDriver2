import React, {useEffect, useState} from 'react';
import {apiClient} from '../api/client';
import {TestResult} from '../types';
import {Check, CheckCircle2, ChevronRight, Code, Edit, FileCode, History, Play, Save, XCircle} from 'lucide-react';
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
                headers: { 'Content-Type': 'text/plain' }
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
        if (trimmed.startsWith('#')) return 'text-slate-500 italic';
        if (trimmed.startsWith('I')) return 'text-sky-300 font-mono';
        if (trimmed.startsWith('E')) return 'text-emerald-300 font-mono';
        if (trimmed.startsWith('WAIT')) return 'text-amber-400 font-mono font-semibold';
        if (trimmed.startsWith('RESET')) return 'text-rose-400 font-mono';
        if (trimmed.includes('CONNECT')) return 'text-purple-400 font-mono font-semibold';
        return 'text-slate-300';
    };

    return (
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-8 h-[calc(100vh-12rem)]">
            
            {/* Column 1: Test list */}
            <div className="lg:col-span-1 bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden flex flex-col shadow-xl">
                <div className="p-4 border-b border-slate-800 bg-slate-800/30 flex items-center gap-2">
                    <FileCode size={20} className="text-blue-400" />
                    <h2 className="font-bold">Available Tests</h2>
                </div>
                <div className="flex-1 overflow-y-auto p-3 space-y-3">
                    {availableTests.map((test) => (
                        <div
                            key={test}
                            className={`p-3 rounded-xl border transition-colors flex flex-col gap-2.5 ${
                                selectedTest === test
                                    ? 'bg-slate-800/50 border-blue-500'
                                    : 'bg-slate-950/40 border-slate-850 hover:border-slate-850 hover:bg-slate-900/30'
                            }`}
                        >
                            <span className="text-sm font-semibold text-slate-300 truncate" title={test}>
                                {test}
                            </span>

                            <div className="flex gap-2">
                                {/* Edit Button */}
                                <button
                                    onClick={() => selectTestcase(test)}
                                    className={`flex-1 py-1.5 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5 transition-all ${
                                        selectedTest === test
                                            ? 'bg-blue-600 text-white shadow-lg shadow-blue-600/20 border border-transparent'
                                            : 'bg-slate-850 hover:bg-slate-800 text-slate-400 hover:text-slate-200 border border-slate-800'
                                    }`}
                                    title="Open Visual and Raw Editor"
                                >
                                    <Edit size={12}/>
                                    <span>Edit</span>
                                </button>

                                {/* Run Button */}
                                <button
                                    onClick={() => runTest(test)}
                                    disabled={!!running}
                                    className="flex-1 py-1.5 bg-emerald-600/10 hover:bg-emerald-600 text-emerald-400 hover:text-white disabled:opacity-50 text-xs font-bold rounded-lg flex items-center justify-center gap-1.5 transition-all border border-emerald-500/20"
                                    title="Run Test Case"
                                >
                                    {running === test ? (
                                        <div
                                            className="w-3.5 h-3.5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin"/>
                                    ) : (
                                        <Play size={12}/>
                                    )}
                                    <span>Run</span>
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            {/* Column 2-4: Details Split Area (Editor & History) */}
            <div className="lg:col-span-3 flex flex-col h-full overflow-hidden gap-6">
                {selectedTest ? (
                    // --- Three-Pane Layout: Test Editor on Top, Run History at Bottom ---
                    <div className="flex-1 flex flex-col h-full overflow-hidden gap-6">

                        {/* Editor Pane (Top Part) */}
                        <div
                            className="flex-1 bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden flex flex-col shadow-xl">
                            {/* Editor Header */}
                            <div
                                className="p-4 border-b border-slate-800 bg-slate-800/30 flex items-center justify-between">
                                <div className="truncate pr-4">
                                    <h3 className="font-bold text-white text-base truncate">{selectedTest}</h3>
                                    <p className="text-xs text-slate-500 mt-0.5">Edit step configurations</p>
                                </div>
                                <div className="flex items-center gap-3 shrink-0">
                                    {/* Toggle Mode */}
                                    <button
                                        onClick={() => setRawMode(!rawMode)}
                                        className={`px-3 py-1.5 rounded-lg text-xs font-bold flex items-center gap-1.5 transition-all ${
                                            rawMode
                                                ? 'bg-blue-600 text-white shadow-lg'
                                                : 'bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white'
                                        }`}
                                    >
                                        <Code size={14}/>
                                        <span>{rawMode ? 'Visual Mode' : 'Raw Mode'}</span>
                                    </button>

                                    {/* Run Test */}
                                    <button
                                        onClick={() => runTest(selectedTest)}
                                        disabled={!!running}
                                        className="px-3 py-1.5 bg-emerald-600/10 hover:bg-emerald-600 text-emerald-400 hover:text-white text-xs font-bold rounded-lg flex items-center gap-1.5 transition-all"
                                    >
                                        {running === selectedTest ? (
                                            <div
                                                className="w-3.5 h-3.5 border-2 border-emerald-500 border-t-transparent rounded-full animate-spin"/>
                                        ) : (
                                            <Play size={14}/>
                                        )}
                                        <span>Run</span>
                                    </button>

                                    <button
                                        onClick={() => setSelectedTest(null)}
                                        className="text-xs text-slate-400 hover:text-white bg-slate-850 hover:bg-slate-800 px-3 py-1.5 rounded-lg font-medium border border-slate-800"
                                    >
                                        Close Editor
                                    </button>
                                </div>
                            </div>

                            {/* Editor Content Area */}
                            <div className="flex-1 overflow-y-auto bg-slate-950 p-4 font-mono text-sm leading-relaxed">
                                {rawMode ? (
                                    /* Raw Editor Mode */
                                    <div className="h-full flex flex-col space-y-4">
                                        <textarea
                                            value={testContent}
                                            onChange={(e) => setTestContent(e.target.value)}
                                            className="flex-1 w-full bg-slate-900 border border-slate-800 rounded-xl p-4 font-mono text-sm text-slate-300 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 resize-none min-h-[160px]"
                                        />
                                        <div className="flex items-center justify-between">
                                            <span className="text-xs text-slate-500 font-sans">
                                                Make changes and click 'Save File' to write.
                                            </span>
                                            <button
                                                onClick={() => saveTestcase(testContent)}
                                                disabled={saveStatus === 'saving'}
                                                className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-xl flex items-center gap-2 text-xs shadow-lg transition-all"
                                            >
                                                {saveStatus === 'saving' ? (
                                                    <div
                                                        className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"/>
                                                ) : saveStatus === 'saved' ? (
                                                    <Check size={14}/>
                                                ) : (
                                                    <Save size={14}/>
                                                )}
                                                <span>
                                                    {saveStatus === 'saving' ? 'Saving...' : saveStatus === 'saved' ? 'Saved' : 'Save File'}
                                                </span>
                                            </button>
                                        </div>
                                    </div>
                                ) : (
                                    /* Visual Step-by-Step Editor Mode */
                                    <div className="space-y-1.5">
                                        {testLines.map((line, index) => {
                                            const isFixMessage = line.startsWith('I') || line.startsWith('E');
                                            const trimmed = line.trim();
                                            const isActionable = trimmed.length > 0 && !trimmed.startsWith('#');

                                            return (
                                                <div
                                                    key={index}
                                                    className={`flex items-start gap-4 p-2 rounded-lg group transition-colors ${
                                                        isFixMessage ? 'hover:bg-slate-900' : ''
                                                    }`}
                                                >
                                                    {/* Line number */}
                                                    <span
                                                        className="w-8 select-none text-slate-650 text-right pr-2 text-xs font-sans mt-0.5">
                                                        {index + 1}
                                                    </span>

                                                    {/* Line badge */}
                                                    {isFixMessage ? (
                                                        <span
                                                            className={`text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded mt-0.5 select-none ${
                                                                line.startsWith('I')
                                                                    ? 'bg-sky-500/10 text-sky-400 border border-sky-500/20'
                                                                    : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20'
                                                            }`}>
                                                            {line.startsWith('I') ? 'Send' : 'Expect'}
                                                        </span>
                                                    ) : isActionable ? (
                                                        <span
                                                            className="text-[10px] uppercase font-bold tracking-wider px-2 py-0.5 rounded mt-0.5 select-none bg-slate-800 text-slate-500 border border-slate-700/50">
                                                            CMD
                                                        </span>
                                                    ) : (
                                                        <span className="w-14"></span>
                                                    )}

                                                    {/* Line content */}
                                                    <span
                                                        className={`flex-1 break-all whitespace-pre-wrap ${getLineStyle(line)}`}>
                                                        {line}
                                                    </span>

                                                    {/* Line actions */}
                                                    {isFixMessage && (
                                                        <button
                                                            onClick={() => openFixModal(line, index)}
                                                            className="p-1 opacity-0 group-hover:opacity-100 bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white rounded transition-all shrink-0"
                                                            title="Edit in FIX Message Modal"
                                                        >
                                                            <Edit size={12}/>
                                                        </button>
                                                    )}
                                                </div>
                                            );
                                        })}
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* Test History Pane (Bottom Part in Three-Pane view) */}
                        <div
                            className="h-72 bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden flex flex-col shadow-xl shrink-0 animate-slide-up">
                            <div
                                className="p-3 border-b border-slate-800 bg-slate-800/30 flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                    <History size={16} className="text-purple-400"/>
                                    <h2 className="font-bold text-xs text-slate-400 uppercase tracking-wider">Test Run
                                        History</h2>
                                </div>
                            </div>
                            <div className="flex-1 overflow-y-auto">
                                {results.length === 0 ? (
                                    <div
                                        className="h-full flex items-center justify-center text-slate-650 font-sans text-xs">
                                        No runs recorded yet. Click 'Run' to execute this testcase.
                                    </div>
                                ) : (
                                    <div className="divide-y divide-slate-800">
                                        {results.map((result) => (
                                            <details key={result.id} className="group border-b border-slate-800/50">
                                                <summary
                                                    className="flex items-center justify-between p-3.5 cursor-pointer hover:bg-slate-800/50 transition-colors list-none">
                                                    <div className="flex items-center gap-3">
                                                        {result.success ? (
                                                            <CheckCircle2 className="text-emerald-500" size={20}/>
                                                        ) : (
                                                            <XCircle className="text-rose-500" size={20}/>
                                                        )}
                                                        <div>
                                                            <div
                                                                className="text-sm font-semibold text-white">{result.testName}</div>
                                                            <div className="text-[10px] text-slate-500">
                                                                {new Date(result.startTime).toLocaleString()}
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <ChevronRight size={16}
                                                                  className="text-slate-600 group-open:rotate-90 transition-transform"/>
                                                </summary>
                                                <div
                                                    className="p-4 bg-black/50 border-t border-slate-800 font-mono text-xs whitespace-pre-wrap text-slate-400 max-h-48 overflow-y-auto">
                                                    {result.logOutput}
                                                    {result.errorMessage && (
                                                        <div
                                                            className="mt-3 p-2.5 bg-rose-500/10 border border-rose-500/20 text-rose-500 rounded-lg">
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

                    </div>
                ) : (
                    // --- Default view: Full Screen Test History when no test is selected ---
                    <div
                        className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden flex flex-col h-full shadow-xl">
                        <div className="p-4 border-b border-slate-800 bg-slate-800/30 flex items-center justify-between">
                            <div className="flex items-center gap-2">
                                <History size={20} className="text-purple-400" />
                                <h2 className="font-bold">Test History</h2>
                            </div>
                        </div>
                        <div className="flex-1 overflow-y-auto">
                            {results.length === 0 ? (
                                <div className="h-full flex flex-col items-center justify-center text-slate-600">
                                    <History size={48} className="mb-4 opacity-20" />
                                    <p>No tests run yet. Select 'Edit' or 'Run' on a testcase in the left pane.</p>
                                </div>
                            ) : (
                                <div className="divide-y divide-slate-800">
                                    {results.map((result) => (
                                        <details key={result.id} className="group">
                                            <summary className="flex items-center justify-between p-4 cursor-pointer hover:bg-slate-800/50 transition-colors list-none">
                                                <div className="flex items-center gap-4">
                                                    {result.success ? (
                                                        <CheckCircle2 className="text-emerald-500" size={24} />
                                                    ) : (
                                                        <XCircle className="text-rose-500" size={24} />
                                                    )}
                                                    <div>
                                                        <div className="font-bold text-white">{result.testName}</div>
                                                        <div className="text-xs text-slate-500">
                                                            {new Date(result.startTime).toLocaleString()}
                                                        </div>
                                                    </div>
                                                </div>
                                                <ChevronRight size={20} className="text-slate-600 group-open:rotate-90 transition-transform" />
                                            </summary>
                                            <div className="p-4 bg-black/50 border-t border-slate-800 font-mono text-xs whitespace-pre-wrap text-slate-400 max-h-96 overflow-y-auto">
                                                {result.logOutput}
                                                {result.errorMessage && (
                                                    <div className="mt-4 p-3 bg-rose-500/10 border border-rose-500/20 text-rose-500 rounded-lg">
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

            {/* Modal for editing FIX message step */}
            <FixMessageEditorModal
                isOpen={modalOpen}
                onClose={() => {
                    setModalOpen(false);
                    setModalLineIndex(null);
                }}
                initialMessage={modalInitialMsg}
                title={modalTitle}
                onSave={handleSaveFixMessage}
            />
        </div>
    );
};
