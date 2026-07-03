import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown, Check, GripVertical, Plus, Save, Table2, Trash2, X } from 'lucide-react';
import { apiClient } from '../api/client';
import { ProductTable } from '../types';

type SaveStatus = 'idle' | 'saving' | 'saved' | 'error';

function moveItem<T>(arr: T[], from: number, to: number): T[] {
    const next = [...arr];
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    return next;
}

export const ProductsEditor: React.FC = () => {
    const [headers, setHeaders] = useState<string[]>([]);
    const [rows, setRows] = useState<string[][]>([]);
    const [filters, setFilters] = useState<string[]>([]);
    const [sortCol, setSortCol] = useState<number | null>(null);
    const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');
    const [dragCol, setDragCol] = useState<number | null>(null);
    const [dragOverCol, setDragOverCol] = useState<number | null>(null);
    const [loading, setLoading] = useState(true);
    const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle');
    const [errorMsg, setErrorMsg] = useState<string | null>(null);
    const savedTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    useEffect(() => {
        apiClient.get<ProductTable>('/dictionary/table')
            .then((res: { data: ProductTable }) => {
                setHeaders(res.data.headers);
                setRows(res.data.rows.map((r: string[]) => [...r]));
                setFilters(new Array(res.data.headers.length).fill(''));
            })
            .catch(() => setErrorMsg('Failed to load product table.'))
            .finally(() => setLoading(false));
    }, []);

    // Filtered + sorted indices into `rows`
    const displayIndices = useMemo(() => {
        let indices = rows.map((_, i) => i);
        if (filters.some(f => f)) {
            indices = indices.filter(i =>
                filters.every((f, ci) =>
                    !f || (rows[i][ci] ?? '').toLowerCase().includes(f.toLowerCase())
                )
            );
        }
        if (sortCol !== null) {
            indices.sort((a, b) => {
                const av = (rows[a][sortCol] ?? '').toLowerCase();
                const bv = (rows[b][sortCol] ?? '').toLowerCase();
                return sortDir === 'asc' ? av.localeCompare(bv) : bv.localeCompare(av);
            });
        }
        return indices;
    }, [rows, filters, sortCol, sortDir]);

    const clearSavedTimer = () => {
        if (savedTimerRef.current) clearTimeout(savedTimerRef.current);
    };

    const focusCell = (displayRowIdx: number, colIdx: number) => {
        const el = document.querySelector<HTMLInputElement>(
            `input[data-drow="${displayRowIdx}"][data-col="${colIdx}"]`
        );
        el?.focus();
    };

    const setHeader = useCallback((i: number, value: string) => {
        setHeaders(prev => prev.map((h, idx) => idx === i ? value : h));
    }, []);

    const setFilter = (i: number, value: string) => {
        setFilters(prev => prev.map((f, idx) => idx === i ? value : f));
    };

    const setCell = useCallback((rowIdx: number, colIdx: number, value: string) => {
        setRows(prev => prev.map((row, ri) =>
            ri === rowIdx ? row.map((cell, ci) => ci === colIdx ? value : cell) : row
        ));
    }, []);

    const toggleSort = (colIdx: number) => {
        if (sortCol === colIdx) {
            if (sortDir === 'asc') { setSortDir('desc'); }
            else { setSortCol(null); setSortDir('asc'); }
        } else {
            setSortCol(colIdx);
            setSortDir('asc');
        }
    };

    const addColumn = () => {
        setHeaders(prev => [...prev, '']);
        setRows(prev => prev.map(row => [...row, '']));
        setFilters(prev => [...prev, '']);
    };

    const removeColumn = (i: number) => {
        if (i === 0) return;
        setHeaders(prev => prev.filter((_, idx) => idx !== i));
        setRows(prev => prev.map(row => row.filter((_, idx) => idx !== i)));
        setFilters(prev => prev.filter((_, idx) => idx !== i));
        if (sortCol === i) setSortCol(null);
        else if (sortCol !== null && sortCol > i) setSortCol(sortCol - 1);
    };

    const addRow = useCallback(() => {
        setRows(prev => [...prev, Array(headers.length).fill('')]);
    }, [headers.length]);

    const removeRow = (i: number) => {
        setRows(prev => prev.filter((_, idx) => idx !== i));
    };

    // Column drag & drop
    const handleDragStart = (e: React.DragEvent, colIdx: number) => {
        if (colIdx === 0) { e.preventDefault(); return; }
        setDragCol(colIdx);
        e.dataTransfer.effectAllowed = 'move';
    };

    const handleDragOver = (e: React.DragEvent, colIdx: number) => {
        if (colIdx === 0 || dragCol === null || dragCol === colIdx) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        if (dragOverCol !== colIdx) setDragOverCol(colIdx);
    };

    const handleDrop = (colIdx: number) => {
        if (dragCol === null || colIdx === 0 || dragCol === colIdx) {
            setDragCol(null);
            setDragOverCol(null);
            return;
        }
        setHeaders(prev => moveItem(prev, dragCol, colIdx));
        setRows(prev => prev.map(row => moveItem(row, dragCol, colIdx)));
        setFilters(prev => moveItem(prev, dragCol, colIdx));
        setSortCol(null);
        setDragCol(null);
        setDragOverCol(null);
    };

    const handleDragEnd = () => {
        setDragCol(null);
        setDragOverCol(null);
    };

    // Keyboard navigation in cells
    const handleCellKeyDown = (
        e: React.KeyboardEvent<HTMLInputElement>,
        displayRowIdx: number,
        colIdx: number
    ) => {
        const lastDRow = displayIndices.length - 1;
        const lastCol = headers.length - 1;

        switch (e.key) {
            case 'Enter':
            case 'ArrowDown':
                e.preventDefault();
                if (displayRowIdx < lastDRow) {
                    focusCell(displayRowIdx + 1, colIdx);
                } else if (e.key === 'Enter') {
                    addRow();
                    setTimeout(() => focusCell(displayRowIdx + 1, colIdx), 0);
                }
                break;
            case 'ArrowUp':
                e.preventDefault();
                if (displayRowIdx > 0) focusCell(displayRowIdx - 1, colIdx);
                break;
            case 'ArrowLeft':
                if (e.currentTarget.selectionStart === 0 && colIdx > 0) {
                    e.preventDefault();
                    focusCell(displayRowIdx, colIdx - 1);
                }
                break;
            case 'ArrowRight':
                if (e.currentTarget.selectionStart === e.currentTarget.value.length && colIdx < lastCol) {
                    e.preventDefault();
                    focusCell(displayRowIdx, colIdx + 1);
                }
                break;
        }
    };

    const validate = (): string | null => {
        if (headers.length === 0) return 'At least one column is required.';
        if (!headers[0].trim()) return 'First column header (product ID) must not be blank.';
        for (const h of headers) {
            if (!h.trim()) return 'Column headers must not be blank.';
        }
        const seen = new Set<string>();
        for (let i = 0; i < rows.length; i++) {
            const id = rows[i][0]?.trim() ?? '';
            if (!id) return `Product ID in row ${i + 1} must not be blank.`;
            if (seen.has(id)) return `Duplicate product ID: "${id}".`;
            seen.add(id);
        }
        return null;
    };

    const save = async () => {
        const err = validate();
        if (err) { setErrorMsg(err); setSaveStatus('error'); return; }
        setErrorMsg(null);
        setSaveStatus('saving');
        try {
            await apiClient.put('/dictionary/table', { headers, rows });
            setSaveStatus('saved');
            clearSavedTimer();
            savedTimerRef.current = setTimeout(() => setSaveStatus('idle'), 3000);
        } catch (e: unknown) {
            const axiosErr = e as { response?: { data?: { detail?: string; message?: string } } };
            const msg = axiosErr?.response?.data?.detail
                ?? axiosErr?.response?.data?.message
                ?? 'Failed to save. See server logs for details.';
            setErrorMsg(typeof msg === 'string' ? msg : JSON.stringify(msg));
            setSaveStatus('error');
        }
    };

    if (loading) {
        return (
            <div className="flex items-center justify-center h-[calc(100vh-9rem)]">
                <span className="text-surface-400 text-sm">Loading product table…</span>
            </div>
        );
    }

    return (
        <div className="flex flex-col h-[calc(100vh-9rem)] gap-4">
            {/* Toolbar */}
            <div className="card flex items-center gap-3 py-2 px-4">
                <Table2 size={14} className="text-blue-400 shrink-0" />
                <span className="text-sm font-medium text-slate-300 flex-1">products.def</span>
                {saveStatus === 'error' && <span className="text-xs text-rose-400">Validation error</span>}
                {saveStatus === 'saved' && (
                    <span className="flex items-center gap-1 text-xs text-emerald-400">
                        <Check size={13} /> Saved
                    </span>
                )}
                <button
                    onClick={save}
                    disabled={saveStatus === 'saving'}
                    className="btn-primary flex items-center gap-1.5 text-sm py-1.5 px-3 disabled:opacity-50"
                >
                    <Save size={14} />
                    {saveStatus === 'saving' ? 'Saving…' : 'Save'}
                </button>
            </div>

            {/* Error banner */}
            {errorMsg && (
                <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-4 py-2 text-sm text-rose-300 flex items-start gap-2">
                    <X size={14} className="mt-0.5 shrink-0" />
                    <span>{errorMsg}</span>
                </div>
            )}

            {/* Table */}
            <div className="card flex-1 overflow-auto p-0">
                <table className="w-full border-collapse text-sm">
                    <thead>
                        <tr className="border-b border-white/10">
                            {headers.map((h, i) => (
                                <th
                                    key={i}
                                    onDragOver={e => handleDragOver(e, i)}
                                    onDrop={() => handleDrop(i)}
                                    onDragEnd={handleDragEnd}
                                    className={[
                                        'p-0 border-r border-white/10 last:border-r-0 min-w-[120px] transition-colors',
                                        i === 0 ? 'bg-blue-500/[0.04]' : dragOverCol === i && dragCol !== i ? 'bg-blue-500/20' : 'bg-white/[0.03]',
                                        dragCol === i ? 'opacity-40' : '',
                                    ].join(' ')}
                                >
                                    {/* Column name + sort + delete */}
                                    <div className="flex items-center gap-1 px-2 pt-1.5 pb-0.5">
                                        {i > 0 && (
                                            <div
                                                draggable
                                                onDragStart={e => handleDragStart(e, i)}
                                                className="cursor-grab active:cursor-grabbing shrink-0 select-none"
                                            >
                                                <GripVertical size={12} className="text-surface-500" />
                                            </div>
                                        )}
                                        <input
                                            value={h}
                                            onChange={e => setHeader(i, e.target.value)}
                                            readOnly={i === 0}
                                            className={`flex-1 min-w-0 bg-transparent text-xs font-semibold outline-none focus:ring-1 focus:ring-blue-500/50 rounded px-1 py-0.5 ${i === 0 ? 'cursor-default text-blue-300' : 'text-slate-300'}`}
                                            placeholder={i === 0 ? 'product ID' : `column ${i + 1}`}
                                        />
                                        <button
                                            onClick={() => toggleSort(i)}
                                            className={`shrink-0 transition-colors ${sortCol === i ? 'text-blue-400' : 'text-surface-600 hover:text-surface-400'}`}
                                            title="Sort"
                                        >
                                            {sortCol === i
                                                ? (sortDir === 'asc' ? <ArrowUp size={12} /> : <ArrowDown size={12} />)
                                                : <ArrowUpDown size={12} />}
                                        </button>
                                        {i > 0 && (
                                            <button
                                                onClick={() => removeColumn(i)}
                                                className="text-surface-500 hover:text-rose-400 transition-colors shrink-0"
                                                title="Remove column"
                                            >
                                                <X size={12} />
                                            </button>
                                        )}
                                    </div>
                                    {/* Filter */}
                                    <div className="px-2 pb-1.5">
                                        <div className="relative">
                                            <input
                                                value={filters[i] ?? ''}
                                                onChange={e => setFilter(i, e.target.value)}
                                                placeholder="filter…"
                                                className="w-full bg-white/[0.05] text-xs text-slate-400 placeholder:text-surface-600 rounded px-1.5 py-0.5 outline-none focus:ring-1 focus:ring-blue-500/50 pr-5"
                                            />
                                            {filters[i] && (
                                                <button
                                                    onClick={() => setFilter(i, '')}
                                                    className="absolute right-1 top-1/2 -translate-y-1/2 text-surface-500 hover:text-slate-300 transition-colors"
                                                    title="Clear filter"
                                                >
                                                    <X size={9} />
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                </th>
                            ))}
                            <th className="p-0 w-14 bg-white/[0.03]">
                                <div className="flex items-center justify-center py-2">
                                    <button
                                        onClick={addColumn}
                                        className="text-surface-400 hover:text-blue-400 transition-colors"
                                        title="Add column"
                                    >
                                        <Plus size={14} />
                                    </button>
                                </div>
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        {displayIndices.map((rowIdx, di) => {
                            const row = rows[rowIdx];
                            return (
                                <tr key={rowIdx} className="border-b border-white/5 hover:bg-white/[0.02] group">
                                    {headers.map((_, ci) => (
                                        <td key={ci} className="p-0 border-r border-white/5 last:border-r-0">
                                            <input
                                                data-drow={di}
                                                data-col={ci}
                                                value={row[ci] ?? ''}
                                                onChange={e => setCell(rowIdx, ci, e.target.value)}
                                                onKeyDown={e => handleCellKeyDown(e, di, ci)}
                                                className={`w-full bg-transparent text-slate-200 outline-none focus:ring-1 focus:ring-inset focus:ring-blue-500/50 px-3 py-1.5 ${ci === 0 ? 'font-medium text-blue-300' : ''}`}
                                            />
                                        </td>
                                    ))}
                                    <td className="p-0 w-14">
                                        <div className="flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                                            <button
                                                onClick={() => removeRow(rowIdx)}
                                                className="text-surface-500 hover:text-rose-400 transition-colors p-1"
                                                title="Delete row"
                                            >
                                                <Trash2 size={13} />
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
                <div className="px-3 py-2 border-t border-white/5">
                    <button
                        onClick={addRow}
                        className="flex items-center gap-1.5 text-xs text-surface-400 hover:text-blue-400 transition-colors"
                    >
                        <Plus size={13} /> Add row
                    </button>
                </div>
            </div>
        </div>
    );
};
