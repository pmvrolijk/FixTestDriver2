import React, {useEffect, useMemo, useState} from 'react';
import {apiClient} from '../api/client';
import {LatencyTraceDto, TestResult} from '../types';
import {X, Printer, Download, Activity} from 'lucide-react';

interface LatencyReportModalProps {
    isOpen: boolean;
    onClose: () => void;
    result: TestResult | null;
}

// A single latency sample, carrying its wall-clock receive time for the time-series axis.
interface Sample {
    latencyMs: number;
    receiveEpochMs: number;
    ordStatus: string;
}

interface Stats {
    count: number;
    min: number;
    max: number;
    mean: number;
    p50: number;
    p90: number;
    p95: number;
    p99: number;
}

// Self-contained CSS for the report container. Rendered inside #latency-report so it travels
// with the DOM when the report is serialized for the Download-HTML export, and scoped under
// #latency-report so it never leaks into the surrounding Tailwind-styled app.
const REPORT_CSS = `
#latency-report { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; color: #cbd5e1; }
#latency-report .lr-title { font-size: 15px; font-weight: 700; color: #f1f5f9; letter-spacing: 0.02em; }
#latency-report .lr-sub { font-size: 11px; color: #64748b; margin-top: 2px; }
#latency-report .lr-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
#latency-report .lr-panel { background: #0b1120; border: 1px solid rgba(148,163,184,0.12); border-radius: 12px; padding: 14px; }
#latency-report .lr-panel-title { font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: #7c8aa5; margin-bottom: 10px; }
#latency-report svg { display: block; width: 100%; height: auto; }
#latency-report .lr-table { width: 100%; border-collapse: collapse; font-size: 12px; }
#latency-report .lr-table th, #latency-report .lr-table td { text-align: right; padding: 6px 10px; border-bottom: 1px solid rgba(148,163,184,0.1); }
#latency-report .lr-table th:first-child, #latency-report .lr-table td:first-child { text-align: left; color: #94a3b8; }
#latency-report .lr-table th { color: #7c8aa5; font-weight: 600; text-transform: uppercase; font-size: 10px; letter-spacing: 0.06em; }
#latency-report .lr-table td { color: #e2e8f0; }
#latency-report .lr-big { font-variant-numeric: tabular-nums; }
`;

const AXIS = '#334155';
const GRID = 'rgba(148,163,184,0.10)';
const TICK = '#64748b';
const BAR = '#a855f7';
const LINE = '#38bdf8';
const MARK = '#f472b6';

function nearestRank(sorted: number[], p: number): number {
    if (sorted.length === 0) return 0;
    const idx = Math.ceil((p / 100) * sorted.length) - 1;
    return sorted[Math.min(Math.max(idx, 0), sorted.length - 1)];
}

function computeStats(sorted: number[]): Stats {
    const n = sorted.length;
    if (n === 0) {
        return {count: 0, min: 0, max: 0, mean: 0, p50: 0, p90: 0, p95: 0, p99: 0};
    }
    const sum = sorted.reduce((a, b) => a + b, 0);
    return {
        count: n,
        min: sorted[0],
        max: sorted[n - 1],
        mean: sum / n,
        p50: nearestRank(sorted, 50),
        p90: nearestRank(sorted, 90),
        p95: nearestRank(sorted, 95),
        p99: nearestRank(sorted, 99),
    };
}

function fmt(ms: number): string {
    if (ms >= 100) return ms.toFixed(0);
    if (ms >= 1) return ms.toFixed(2);
    return ms.toFixed(3);
}

// ---- Histogram (log-spaced bins) ------------------------------------------------------------
const Histogram: React.FC<{samples: number[]}> = ({samples}) => {
    const W = 520, H = 220, padL = 40, padR = 12, padT = 12, padB = 34;
    const bins = useMemo(() => {
        const positive = samples.filter(v => v > 0);
        if (positive.length === 0) return null;
        const min = Math.min(...positive);
        const max = Math.max(...positive);
        const N = 24;
        const logMin = Math.log10(min);
        const logMax = Math.log10(max === min ? max * 10 : max);
        const span = logMax - logMin || 1;
        const counts = new Array(N).fill(0);
        for (const v of positive) {
            let i = Math.floor(((Math.log10(v) - logMin) / span) * N);
            if (i >= N) i = N - 1;
            if (i < 0) i = 0;
            counts[i]++;
        }
        return {counts, logMin, logMax, min, max};
    }, [samples]);

    if (!bins) return <div className="lr-sub">No positive latency samples.</div>;
    const maxCount = Math.max(...bins.counts, 1);
    const plotW = W - padL - padR;
    const plotH = H - padT - padB;
    const bw = plotW / bins.counts.length;

    return (
        <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Latency histogram">
            <line x1={padL} y1={padT} x2={padL} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            <line x1={padL} y1={H - padB} x2={W - padR} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            {[0, 0.5, 1].map(f => {
                const y = padT + plotH * (1 - f);
                return (
                    <g key={f}>
                        <line x1={padL} y1={y} x2={W - padR} y2={y} stroke={GRID} strokeWidth={1}/>
                        <text x={padL - 6} y={y + 3} textAnchor="end" fontSize={9} fill={TICK}>
                            {Math.round(maxCount * f)}
                        </text>
                    </g>
                );
            })}
            {bins.counts.map((c, i) => {
                const h = (c / maxCount) * plotH;
                const x = padL + i * bw;
                return (
                    <rect key={i} x={x + 0.5} y={padT + plotH - h} width={Math.max(bw - 1, 1)} height={h}
                          fill={BAR} opacity={0.85}/>
                );
            })}
            <text x={padL} y={H - 8} textAnchor="start" fontSize={9} fill={TICK}>
                {fmt(bins.min)}ms
            </text>
            <text x={W - padR} y={H - 8} textAnchor="end" fontSize={9} fill={TICK}>
                {fmt(bins.max)}ms
            </text>
            <text x={(padL + W - padR) / 2} y={H - 8} textAnchor="middle" fontSize={9} fill={TICK}>
                latency (log scale)
            </text>
        </svg>
    );
};

// ---- CDF -----------------------------------------------------------------------------------
const CdfChart: React.FC<{sorted: number[]; stats: Stats}> = ({sorted, stats}) => {
    const W = 520, H = 220, padL = 40, padR = 12, padT = 12, padB = 34;
    if (sorted.length === 0) return <div className="lr-sub">No samples.</div>;
    const plotW = W - padL - padR;
    const plotH = H - padT - padB;
    const min = sorted[0];
    const max = sorted[sorted.length - 1];
    const range = max - min || 1;
    const xOf = (v: number) => padL + ((v - min) / range) * plotW;
    const yOf = (f: number) => padT + (1 - f) * plotH;

    const points = sorted.map((v, i) => `${xOf(v).toFixed(1)},${yOf((i + 1) / sorted.length).toFixed(1)}`).join(' ');
    const markers: {p: number; v: number}[] = [
        {p: 50, v: stats.p50}, {p: 90, v: stats.p90}, {p: 95, v: stats.p95}, {p: 99, v: stats.p99},
    ];

    return (
        <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Latency CDF">
            <line x1={padL} y1={padT} x2={padL} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            <line x1={padL} y1={H - padB} x2={W - padR} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            {[0, 0.25, 0.5, 0.75, 1].map(f => {
                const y = yOf(f);
                return (
                    <g key={f}>
                        <line x1={padL} y1={y} x2={W - padR} y2={y} stroke={GRID} strokeWidth={1}/>
                        <text x={padL - 6} y={y + 3} textAnchor="end" fontSize={9} fill={TICK}>
                            {(f * 100).toFixed(0)}%
                        </text>
                    </g>
                );
            })}
            <polyline points={points} fill="none" stroke={LINE} strokeWidth={1.6}/>
            {markers.map(m => {
                const x = xOf(m.v);
                return (
                    <g key={m.p}>
                        <line x1={x} y1={padT} x2={x} y2={H - padB} stroke={MARK} strokeWidth={0.8}
                              strokeDasharray="3 3" opacity={0.7}/>
                        <text x={x} y={padT + 9} textAnchor="middle" fontSize={8} fill={MARK}>
                            p{m.p}
                        </text>
                    </g>
                );
            })}
            <text x={padL} y={H - 8} textAnchor="start" fontSize={9} fill={TICK}>{fmt(min)}ms</text>
            <text x={W - padR} y={H - 8} textAnchor="end" fontSize={9} fill={TICK}>{fmt(max)}ms</text>
        </svg>
    );
};

// ---- Time-series ---------------------------------------------------------------------------
const TimeSeriesChart: React.FC<{samples: Sample[]}> = ({samples}) => {
    const W = 520, H = 220, padL = 40, padR = 12, padT = 12, padB = 34;
    const ordered = useMemo(() => [...samples].sort((a, b) => a.receiveEpochMs - b.receiveEpochMs), [samples]);
    if (ordered.length === 0) return <div className="lr-sub">No samples.</div>;
    const plotW = W - padL - padR;
    const plotH = H - padT - padB;
    const t0 = ordered[0].receiveEpochMs;
    const t1 = ordered[ordered.length - 1].receiveEpochMs;
    const tRange = t1 - t0 || 1;
    const maxLat = Math.max(...ordered.map(s => s.latencyMs), 1);
    const xOf = (t: number) => padL + ((t - t0) / tRange) * plotW;
    const yOf = (v: number) => padT + (1 - v / maxLat) * plotH;
    const line = ordered.map(s => `${xOf(s.receiveEpochMs).toFixed(1)},${yOf(s.latencyMs).toFixed(1)}`).join(' ');

    return (
        <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Latency over time">
            <line x1={padL} y1={padT} x2={padL} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            <line x1={padL} y1={H - padB} x2={W - padR} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            {[0, 0.5, 1].map(f => {
                const y = padT + plotH * (1 - f);
                return (
                    <g key={f}>
                        <line x1={padL} y1={y} x2={W - padR} y2={y} stroke={GRID} strokeWidth={1}/>
                        <text x={padL - 6} y={y + 3} textAnchor="end" fontSize={9} fill={TICK}>
                            {fmt(maxLat * f)}
                        </text>
                    </g>
                );
            })}
            {ordered.length > 1 && <polyline points={line} fill="none" stroke={LINE} strokeWidth={1} opacity={0.5}/>}
            {ordered.map((s, i) => (
                <circle key={i} cx={xOf(s.receiveEpochMs)} cy={yOf(s.latencyMs)} r={2} fill={BAR}/>
            ))}
            <text x={padL} y={H - 8} textAnchor="start" fontSize={9} fill={TICK}>t0</text>
            <text x={W - padR} y={H - 8} textAnchor="end" fontSize={9} fill={TICK}>
                +{((t1 - t0) / 1000).toFixed(1)}s
            </text>
            <text x={(padL + W - padR) / 2} y={H - 8} textAnchor="middle" fontSize={9} fill={TICK}>
                latency (ms) over receive time
            </text>
        </svg>
    );
};

// ---- Summary table -------------------------------------------------------------------------
const SummaryTable: React.FC<{stats: Stats}> = ({stats}) => (
    <table className="lr-table lr-big">
        <thead>
        <tr>
            <th>Metric</th>
            <th>ms</th>
        </tr>
        </thead>
        <tbody>
        <tr><td>count</td><td>{stats.count}</td></tr>
        <tr><td>min</td><td>{fmt(stats.min)}</td></tr>
        <tr><td>mean</td><td>{fmt(stats.mean)}</td></tr>
        <tr><td>p50</td><td>{fmt(stats.p50)}</td></tr>
        <tr><td>p90</td><td>{fmt(stats.p90)}</td></tr>
        <tr><td>p95</td><td>{fmt(stats.p95)}</td></tr>
        <tr><td>p99</td><td>{fmt(stats.p99)}</td></tr>
        <tr><td>max</td><td>{fmt(stats.max)}</td></tr>
        </tbody>
    </table>
);

export const LatencyReportModal: React.FC<LatencyReportModalProps> = ({isOpen, onClose, result}) => {
    const [traces, setTraces] = useState<LatencyTraceDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Close on ESC
    useEffect(() => {
        if (!isOpen) return;
        const handleKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        document.addEventListener('keydown', handleKey);
        return () => document.removeEventListener('keydown', handleKey);
    }, [isOpen, onClose]);

    useEffect(() => {
        if (!isOpen || !result) return;
        setLoading(true);
        setError(null);
        setTraces([]);
        apiClient.get<LatencyTraceDto[]>(`/tests/results/${result.id}/trace`)
            .then(r => setTraces(r.data))
            .catch(e => setError(e?.response?.data?.message || e?.message || 'Failed to load trace data'))
            .finally(() => setLoading(false));
    }, [isOpen, result]);

    const samples = useMemo<Sample[]>(() => {
        const out: Sample[] = [];
        for (const t of traces) {
            for (const ev of t.events || []) {
                out.push({
                    latencyMs: ev.latencyNanos / 1e6,
                    receiveEpochMs: ev.receiveTime ? new Date(ev.receiveTime).getTime() : 0,
                    ordStatus: ev.ordStatus,
                });
            }
        }
        return out;
    }, [traces]);

    const sorted = useMemo(() => samples.map(s => s.latencyMs).sort((a, b) => a - b), [samples]);
    const stats = useMemo(() => computeStats(sorted), [sorted]);

    const handlePrint = () => window.print();

    const handleDownload = () => {
        const el = document.getElementById('latency-report');
        if (!el) return;
        const html = `<!doctype html><html><head><meta charset="utf-8">` +
            `<title>Latency Report — ${result?.testName ?? ''}</title>` +
            `<style>body{margin:0;padding:24px;background:#020617;}</style></head>` +
            `<body>${el.outerHTML}</body></html>`;
        const blob = new Blob([html], {type: 'text/html'});
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `latency-${result?.testName ?? 'report'}-${result?.id ?? ''}.html`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    };

    if (!isOpen || !result) return null;

    const hasData = samples.length > 0;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in"
             onClick={onClose}>
            <div className="bg-surface-900 border border-white/10 rounded-2xl w-full max-w-5xl max-h-[85vh] flex flex-col shadow-2xl overflow-hidden animate-scale-up"
                 onClick={(e) => e.stopPropagation()}>
                {/* Header */}
                <div className="p-5 border-b border-white/5 bg-white/[0.02] flex justify-between items-center shrink-0">
                    <div className="flex items-center gap-2.5">
                        <Activity size={18} className="text-purple-400"/>
                        <div>
                            <h2 className="text-sm font-semibold text-slate-100">Latency Stats Report</h2>
                            <p className="text-[11px] text-surface-500">{result.testName}</p>
                        </div>
                    </div>
                    <button onClick={onClose}
                            className="p-1.5 rounded-lg text-surface-500 hover:text-white hover:bg-white/[0.05] transition-all">
                        <X size={16}/>
                    </button>
                </div>

                {/* Body */}
                <div className="flex-1 overflow-y-auto p-5 space-y-5">
                    {error && (
                        <div className="p-3.5 bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs rounded-xl">
                            {error}
                        </div>
                    )}

                    {loading ? (
                        <div className="flex items-center justify-center gap-3 py-16 text-surface-500 text-xs">
                            <div className="w-4 h-4 border-2 border-purple-400 border-t-transparent rounded-full animate-spin"/>
                            Loading trace data…
                        </div>
                    ) : !hasData ? (
                        <div className="flex flex-col items-center justify-center py-16 text-surface-500">
                            <Activity size={28} className="mb-2 opacity-20"/>
                            <p className="text-xs">No latency events captured for this run</p>
                        </div>
                    ) : (
                        <div id="latency-report">
                            <style dangerouslySetInnerHTML={{__html: REPORT_CSS}}/>
                            <div style={{marginBottom: 16}}>
                                <div className="lr-title">Latency Stats Report — {result.testName}</div>
                                <div className="lr-sub">
                                    {stats.count} events · {traces.length} traced order{traces.length === 1 ? '' : 's'} ·
                                    {' '}run #{result.id} · {new Date(result.startTime).toLocaleString()}
                                </div>
                            </div>
                            <div className="lr-grid">
                                <div className="lr-panel">
                                    <div className="lr-panel-title">Distribution (histogram)</div>
                                    <Histogram samples={sorted}/>
                                </div>
                                <div className="lr-panel">
                                    <div className="lr-panel-title">Cumulative distribution (CDF)</div>
                                    <CdfChart sorted={sorted} stats={stats}/>
                                </div>
                                <div className="lr-panel">
                                    <div className="lr-panel-title">Latency over time</div>
                                    <TimeSeriesChart samples={samples}/>
                                </div>
                                <div className="lr-panel">
                                    <div className="lr-panel-title">Summary</div>
                                    <SummaryTable stats={stats}/>
                                </div>
                            </div>
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="p-5 border-t border-white/5 bg-white/[0.02] flex justify-end gap-2.5 shrink-0">
                    <button onClick={handlePrint} disabled={!hasData}
                            className="btn-secondary px-5 py-2.5 rounded-xl text-xs font-semibold flex items-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed">
                        <Printer size={14}/> Print PDF
                    </button>
                    <button onClick={handleDownload} disabled={!hasData}
                            className="bg-blue-600 hover:bg-blue-500 text-white px-5 py-2.5 rounded-xl text-xs font-semibold flex items-center gap-2 transition-colors disabled:opacity-40 disabled:cursor-not-allowed">
                        <Download size={14}/> Download HTML
                    </button>
                </div>
            </div>
        </div>
    );
};
