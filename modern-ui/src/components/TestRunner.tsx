import React, { useEffect, useState } from 'react';
import { apiClient } from '../api/client';
import { TestResult } from '../types';
import { CheckCircle2, XCircle, Play, ChevronRight, FileCode, History } from 'lucide-react';

export const TestRunner: React.FC = () => {
    const [availableTests, setAvailableTests] = useState<string[]>([]);
    const [results, setResults] = useState<TestResult[]>([]);
    const [running, setRunning] = useState<string | null>(null);

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

    return (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-8 h-[calc(100vh-12rem)]">
            <div className="lg:col-span-1 bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden flex flex-col">
                <div className="p-4 border-b border-slate-800 bg-slate-800/30 flex items-center gap-2">
                    <FileCode size={20} className="text-blue-400" />
                    <h2 className="font-bold">Available Tests</h2>
                </div>
                <div className="flex-1 overflow-y-auto p-2 space-y-1">
                    {availableTests.map((test) => (
                        <button
                            key={test}
                            onClick={() => runTest(test)}
                            disabled={!!running}
                            className="w-full text-left p-3 rounded-xl hover:bg-slate-800 transition-colors flex items-center justify-between group disabled:opacity-50"
                        >
                            <span className="text-sm font-medium text-slate-300 group-hover:text-white truncate">{test}</span>
                            {running === test ? (
                                <div className="w-5 h-5 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
                            ) : (
                                <Play size={16} className="text-slate-600 group-hover:text-blue-400" />
                            )}
                        </button>
                    ))}
                </div>
            </div>

            <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden flex flex-col">
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
                            <p>No tests run yet</p>
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
                                                <div className="font-bold">{result.testName}</div>
                                                <div className="text-xs text-slate-500">{new Date(result.startTime).toLocaleString()}</div>
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
        </div>
    );
};
