import React, {useEffect, useMemo, useState} from 'react';
import {apiClient} from '../api/client';
import {LatencyTraceDto, TestResult} from '../types';
import {X, Printer, Download, Activity} from 'lucide-react';

interface LatencyReportModalProps {
    isOpen: boolean;
    onClose: () => void;
    result: TestResult | null;
}

// A single latency sample. Carries the request (send) wall-clock time — the scatter plots x by
// send time — and the wall-clock receive time.
interface Sample {
    latencyMs: number;
    sendEpochMs: number;
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
#latency-report .lr-stack > * + * { margin-top: 16px; }
#latency-report .lr-row2 { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
#latency-report .lr-panel { background: #0b1120; border: 1px solid rgba(148,163,184,0.12); border-radius: 12px; padding: 14px; }
#latency-report .lr-panel-title { font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: #7c8aa5; margin-bottom: 10px; }
#latency-report svg { display: block; width: 100%; height: auto; }
#latency-report .lr-table { width: 100%; border-collapse: collapse; font-size: 12px; }
#latency-report .lr-table th, #latency-report .lr-table td { text-align: right; padding: 6px 10px; border-bottom: 1px solid rgba(148,163,184,0.1); }
#latency-report .lr-table th:first-child, #latency-report .lr-table td:first-child { text-align: left; color: #94a3b8; }
#latency-report .lr-table th { color: #7c8aa5; font-weight: 600; text-transform: uppercase; font-size: 10px; letter-spacing: 0.06em; }
#latency-report .lr-table td { color: #e2e8f0; }
#latency-report .lr-big { font-variant-numeric: tabular-nums; }
#latency-report .lr-swatch { display: inline-block; width: 10px; height: 10px; border-radius: 2px; margin-right: 8px; vertical-align: middle; }
#latency-report .lr-table tbody tr.lr-clickable { cursor: pointer; }
#latency-report .lr-table tbody tr.lr-clickable:hover td { background: rgba(148,163,184,0.06); }
#latency-report .lr-table tbody tr.lr-dim td { opacity: 0.3; }
`;

const AXIS = '#334155';
const GRID = 'rgba(148,163,184,0.10)';
const TICK = '#64748b';

// Axis divisions: DIV+1 evenly spaced ticks/gridlines on both axes of every chart.
const DIV = 5;
const FRACS = Array.from({length: DIV + 1}, (_, k) => k / DIV);
// Anchor edge tick labels inward so they don't clip the plot border.
const tickAnchor = (k: number): 'start' | 'middle' | 'end' =>
    k === 0 ? 'start' : k === DIV ? 'end' : 'middle';

// Consistent per-OrdStatus colours: blues for the "new" phase, greens for the "fill"
// phase, reds/orange for terminal/negative outcomes. Keys match the resolved dictionary
// names carried by Sample.ordStatus (tag 39). Unknown statuses fall back to neutral grey.
const STATUS_COLORS: Record<string, string> = {
    PENDING_NEW: '#7dd3fc',      // light blue
    NEW: '#2563eb',              // dark blue
    PARTIALLY_FILLED: '#4ade80', // light green
    FILLED: '#16a34a',           // dark green
    CANCELED: '#f87171',         // red
    REJECTED: '#b91c1c',         // dark red
    EXPIRED: '#fb923c',          // red-orange
};
const FALLBACK_COLOR = '#94a3b8';
const colorFor = (s: string): string => STATUS_COLORS[s] ?? FALLBACK_COLOR;

// One status's samples, pre-sorted with its computed stats and display colour.
interface StatusGroup {
    status: string;
    samples: Sample[];
    sorted: number[];
    stats: Stats;
    color: string;
}

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

// ---- Histogram (log-spaced bins, per-status overlay) ----------------------------------------
const Histogram: React.FC<{groups: StatusGroup[]}> = ({groups}) => {
    const W = 520, H = 220, padL = 40, padR = 12, padT = 12, padB = 34;
    const N = 24;
    const model = useMemo(() => {
        const positive = groups.flatMap(g => g.sorted.filter(v => v > 0));
        if (positive.length === 0) return null;
        const min = Math.min(...positive);
        const max = Math.max(...positive);
        const logMin = Math.log10(min);
        const logMax = Math.log10(max === min ? max * 10 : max);
        const span = logMax - logMin || 1;
        const series = groups.map(g => {
            const counts = new Array(N).fill(0);
            for (const v of g.sorted) {
                if (v <= 0) continue;
                let i = Math.floor(((Math.log10(v) - logMin) / span) * N);
                if (i >= N) i = N - 1;
                if (i < 0) i = 0;
                counts[i]++;
            }
            return {color: g.color, counts};
        });
        const maxCount = Math.max(1, ...series.flatMap(s => s.counts));
        return {series, maxCount, min, max, logMin, span};
    }, [groups]);

    if (!model) return <div className="lr-sub">No positive latency samples.</div>;
    const plotW = W - padL - padR;
    const plotH = H - padT - padB;
    const bw = plotW / N;

    return (
        <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Latency histogram">
            <line x1={padL} y1={padT} x2={padL} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            <line x1={padL} y1={H - padB} x2={W - padR} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            {FRACS.map((f, k) => {
                const y = padT + plotH * (1 - f);
                return (
                    <g key={`y${k}`}>
                        <line x1={padL} y1={y} x2={W - padR} y2={y} stroke={GRID} strokeWidth={1}/>
                        <text x={padL - 6} y={y + 3} textAnchor="end" fontSize={9} fill={TICK}>
                            {Math.round(model.maxCount * f)}
                        </text>
                    </g>
                );
            })}
            {FRACS.map((f, k) => {
                const x = padL + plotW * f;
                return (
                    <g key={`x${k}`}>
                        <line x1={x} y1={padT} x2={x} y2={H - padB} stroke={GRID} strokeWidth={1}/>
                        <text x={x} y={H - 20} textAnchor={tickAnchor(k)} fontSize={9} fill={TICK}>
                            {fmt(Math.pow(10, model.logMin + model.span * f))}
                        </text>
                    </g>
                );
            })}
            {model.series.map((s, si) => (
                <g key={si}>
                    {s.counts.map((c, i) => {
                        if (c === 0) return null;
                        const h = (c / model.maxCount) * plotH;
                        const x = padL + i * bw;
                        return (
                            <rect key={i} x={x + 0.5} y={padT + plotH - h} width={Math.max(bw - 1, 1)} height={h}
                                  fill={s.color} opacity={0.45}/>
                        );
                    })}
                </g>
            ))}
            <text x={(padL + W - padR) / 2} y={H - 6} textAnchor="middle" fontSize={9} fill={TICK}>
                latency ms (log scale)
            </text>
        </svg>
    );
};

// ---- CDF (per-status overlay) --------------------------------------------------------------
const CdfChart: React.FC<{groups: StatusGroup[]}> = ({groups}) => {
    const W = 520, H = 220, padL = 40, padR = 12, padT = 12, padB = 34;
    const plotW = W - padL - padR;
    const plotH = H - padT - padB;
    const all = useMemo(() => groups.flatMap(g => g.sorted), [groups]);
    if (all.length === 0) return <div className="lr-sub">No samples.</div>;
    const min = Math.min(...all);
    const max = Math.max(...all);
    const range = max - min || 1;
    const xOf = (v: number) => padL + ((v - min) / range) * plotW;
    const yOf = (f: number) => padT + (1 - f) * plotH;

    return (
        <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Latency CDF">
            <line x1={padL} y1={padT} x2={padL} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            <line x1={padL} y1={H - padB} x2={W - padR} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            {FRACS.map((f, k) => {
                const y = yOf(f);
                return (
                    <g key={`y${k}`}>
                        <line x1={padL} y1={y} x2={W - padR} y2={y} stroke={GRID} strokeWidth={1}/>
                        <text x={padL - 6} y={y + 3} textAnchor="end" fontSize={9} fill={TICK}>
                            {(f * 100).toFixed(0)}%
                        </text>
                    </g>
                );
            })}
            {FRACS.map((f, k) => {
                const x = padL + plotW * f;
                return (
                    <g key={`x${k}`}>
                        <line x1={x} y1={padT} x2={x} y2={H - padB} stroke={GRID} strokeWidth={1}/>
                        <text x={x} y={H - 20} textAnchor={tickAnchor(k)} fontSize={9} fill={TICK}>
                            {fmt(min + range * f)}
                        </text>
                    </g>
                );
            })}
            {groups.map(g => {
                if (g.sorted.length === 0) return null;
                const points = g.sorted
                    .map((v, i) => `${xOf(v).toFixed(1)},${yOf((i + 1) / g.sorted.length).toFixed(1)}`)
                    .join(' ');
                return (
                    <polyline key={g.status} points={points} fill="none" stroke={g.color}
                              strokeWidth={1.6} opacity={0.9}/>
                );
            })}
            <text x={(padL + W - padR) / 2} y={H - 6} textAnchor="middle" fontSize={9} fill={TICK}>
                latency (ms)
            </text>
        </svg>
    );
};

// ---- Time-series ---------------------------------------------------------------------------
const TimeSeriesChart: React.FC<{samples: Sample[]}> = ({samples}) => {
    const W = 520, H = 220, padL = 40, padR = 12, padT = 12, padB = 34;
    // x is the request (send) time, y is the response latency.
    const ordered = useMemo(() => [...samples].sort((a, b) => a.sendEpochMs - b.sendEpochMs), [samples]);
    if (ordered.length === 0) return <div className="lr-sub">No samples.</div>;
    const plotW = W - padL - padR;
    const plotH = H - padT - padB;
    const t0 = ordered[0].sendEpochMs;
    const t1 = ordered[ordered.length - 1].sendEpochMs;
    const tRange = t1 - t0 || 1;
    const maxLat = Math.max(...ordered.map(s => s.latencyMs), 1);
    const xOf = (t: number) => padL + ((t - t0) / tRange) * plotW;
    const yOf = (v: number) => padT + (1 - v / maxLat) * plotH;

    return (
        <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Latency over time">
            <line x1={padL} y1={padT} x2={padL} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            <line x1={padL} y1={H - padB} x2={W - padR} y2={H - padB} stroke={AXIS} strokeWidth={1}/>
            {FRACS.map((f, k) => {
                const y = padT + plotH * (1 - f);
                return (
                    <g key={`y${k}`}>
                        <line x1={padL} y1={y} x2={W - padR} y2={y} stroke={GRID} strokeWidth={1}/>
                        <text x={padL - 6} y={y + 3} textAnchor="end" fontSize={9} fill={TICK}>
                            {fmt(maxLat * f)}
                        </text>
                    </g>
                );
            })}
            {FRACS.map((f, k) => {
                const x = padL + plotW * f;
                return (
                    <g key={`x${k}`}>
                        <line x1={x} y1={padT} x2={x} y2={H - padB} stroke={GRID} strokeWidth={1}/>
                        <text x={x} y={H - 20} textAnchor={tickAnchor(k)} fontSize={9} fill={TICK}>
                            +{((tRange * f) / 1000).toFixed(1)}s
                        </text>
                    </g>
                );
            })}
            {ordered.map((s, i) => (
                <circle key={i} cx={xOf(s.sendEpochMs)} cy={yOf(s.latencyMs)} r={2} fill={colorFor(s.ordStatus)}/>
            ))}
            <text x={(padL + W - padR) / 2} y={H - 6} textAnchor="middle" fontSize={9} fill={TICK}>
                latency (ms) over time
            </text>
        </svg>
    );
};

// ---- Summary table (horizontal, one row per status) ----------------------------------------
// Rows are clickable to toggle a status in/out of the charts below; a hidden status is dimmed but
// its row (and all its numbers) stay in place. The "All" row re-enables every status.
interface SummaryTableProps {
    groups: StatusGroup[];
    allStats: Stats;
    hidden: Set<string>;
    onToggle: (status: string) => void;
    onShowAll: () => void;
}

const SummaryTable: React.FC<SummaryTableProps> = ({groups, allStats, hidden, onToggle, onShowAll}) => (
    <table className="lr-table lr-big">
        <thead>
        <tr>
            <th>Status</th>
            <th>count</th>
            <th>min</th>
            <th>mean</th>
            <th>p50</th>
            <th>p90</th>
            <th>p95</th>
            <th>p99</th>
            <th>max</th>
        </tr>
        </thead>
        <tbody>
        {groups.map(g => (
            <tr key={g.status} className={`lr-clickable${hidden.has(g.status) ? ' lr-dim' : ''}`}
                onClick={() => onToggle(g.status)}>
                <td><span className="lr-swatch" style={{background: g.color}}/>{g.status}</td>
                <td>{g.stats.count}</td>
                <td>{fmt(g.stats.min)}</td>
                <td>{fmt(g.stats.mean)}</td>
                <td>{fmt(g.stats.p50)}</td>
                <td>{fmt(g.stats.p90)}</td>
                <td>{fmt(g.stats.p95)}</td>
                <td>{fmt(g.stats.p99)}</td>
                <td>{fmt(g.stats.max)}</td>
            </tr>
        ))}
        {groups.length > 1 && (
            <tr className="lr-clickable" onClick={onShowAll}>
                <td>All</td>
                <td>{allStats.count}</td>
                <td>{fmt(allStats.min)}</td>
                <td>{fmt(allStats.mean)}</td>
                <td>{fmt(allStats.p50)}</td>
                <td>{fmt(allStats.p90)}</td>
                <td>{fmt(allStats.p95)}</td>
                <td>{fmt(allStats.p99)}</td>
                <td>{fmt(allStats.max)}</td>
            </tr>
        )}
        </tbody>
    </table>
);

export const LatencyReportModal: React.FC<LatencyReportModalProps> = ({isOpen, onClose, result}) => {
    const [traces, setTraces] = useState<LatencyTraceDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    // Statuses toggled out of the charts below (table always shows every row). Empty = all active.
    const [hidden, setHidden] = useState<Set<string>>(new Set());

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
        setHidden(new Set());
        apiClient.get<LatencyTraceDto[]>(`/tests/results/${result.id}/trace`)
            .then(r => setTraces(r.data))
            .catch(e => setError(e?.response?.data?.message || e?.message || 'Failed to load trace data'))
            .finally(() => setLoading(false));
    }, [isOpen, result]);

    const samples = useMemo<Sample[]>(() => {
        const out: Sample[] = [];
        for (const t of traces) {
            const sendEpochMs = t.sendTime ? new Date(t.sendTime).getTime() : 0;
            for (const ev of t.events || []) {
                out.push({
                    latencyMs: ev.latencyNanos / 1e6,
                    sendEpochMs,
                    receiveEpochMs: ev.receiveTime ? new Date(ev.receiveTime).getTime() : 0,
                    ordStatus: ev.ordStatus,
                });
            }
        }
        return out;
    }, [traces]);

    const sorted = useMemo(() => samples.map(s => s.latencyMs).sort((a, b) => a - b), [samples]);
    const stats = useMemo(() => computeStats(sorted), [sorted]);

    // Group samples by resolved OrdStatus, preserving first-seen order so colours/legend are stable.
    const groups = useMemo<StatusGroup[]>(() => {
        const byStatus = new Map<string, Sample[]>();
        for (const s of samples) {
            const list = byStatus.get(s.ordStatus);
            if (list) list.push(s);
            else byStatus.set(s.ordStatus, [s]);
        }
        return Array.from(byStatus.entries()).map(([status, list]) => {
            const groupSorted = list.map(s => s.latencyMs).sort((a, b) => a - b);
            return {status, samples: list, sorted: groupSorted, stats: computeStats(groupSorted), color: colorFor(status)};
        });
    }, [samples]);
    // Filtered views feeding the charts; the summary table always receives the full `groups`.
    const visibleGroups = useMemo(() => groups.filter(g => !hidden.has(g.status)), [groups, hidden]);
    const visibleSamples = useMemo(() => samples.filter(s => !hidden.has(s.ordStatus)), [samples, hidden]);

    const toggleStatus = (status: string) => {
        setHidden(prev => {
            const next = new Set(prev);
            if (next.has(status)) next.delete(status);
            else next.add(status);
            return next;
        });
    };
    const showAll = () => setHidden(new Set());

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
                            <div className="lr-stack">
                                <div className="lr-panel">
                                    <div className="lr-panel-title">Summary</div>
                                    <SummaryTable groups={groups} allStats={stats}
                                                  hidden={hidden} onToggle={toggleStatus} onShowAll={showAll}/>
                                </div>
                                <div className="lr-row2">
                                    <div className="lr-panel">
                                        <div className="lr-panel-title">Distribution (histogram)</div>
                                        <Histogram groups={visibleGroups}/>
                                    </div>
                                    <div className="lr-panel">
                                        <div className="lr-panel-title">Cumulative distribution (CDF)</div>
                                        <CdfChart groups={visibleGroups}/>
                                    </div>
                                </div>
                                <div className="lr-panel">
                                    <div className="lr-panel-title">Latency over time</div>
                                    <TimeSeriesChart samples={visibleSamples}/>
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
