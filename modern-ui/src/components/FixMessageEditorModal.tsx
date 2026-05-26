import React, { useEffect, useState, useRef } from 'react';
import { apiClient } from '../api/client';
import { X, Plus, Trash2, Send, Save, RefreshCw, HelpCircle } from 'lucide-react';

interface FixField {
    tag: number;
    name: string;
    value: string;
    type: string;
}

interface FixMessageEditorModalProps {
    isOpen: boolean;
    onClose: () => void;
    initialMessage: string;
    title: string;
    onSave?: (rawMsg: string) => void;
    onSend?: (rawMsg: string) => Promise<void>;
    defaultBeginString?: string;
}

export const FixMessageEditorModal: React.FC<FixMessageEditorModalProps> = ({
    isOpen,
    onClose,
    initialMessage,
    title,
    onSave,
    onSend,
    defaultBeginString = 'FIX.4.2'
}) => {
    const [rawMsg, setRawMsg] = useState(initialMessage);
    const [fields, setFields] = useState<FixField[]>([]);
    const [beginString, setBeginString] = useState(defaultBeginString);
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const parseTimeout = useRef<any | null>(null);

    // Initial load and parse
    useEffect(() => {
        if (isOpen) {
            setRawMsg(initialMessage);
            decodeRawMessage(initialMessage);
        }
    }, [isOpen, initialMessage]);

    // Decode message using backend
    const decodeRawMessage = async (msg: string) => {
        if (!msg || msg.trim() === '') {
            setFields([]);
            return;
        }
        setLoading(true);
        setError(null);
        try {
            const response = await apiClient.post('/dictionary/decode-message', { message: msg });
            setFields(response.data);
            
            // Detect BeginString from fields if present
            const beginField = response.data.find((f: any) => f.tag === 8);
            if (beginField) {
                setBeginString(beginField.value);
            }
        } catch (err: any) {
            setError(err.response?.data?.message || 'Failed to parse FIX message');
        } finally {
            setLoading(false);
        }
    };

    // Update raw message based on fields
    const updateRawFromFields = (updatedFields: FixField[]) => {
        const generated = updatedFields
            .map(f => `${f.tag}=${f.value}`)
            .join('|');
        const finalMsg = generated ? `${generated}|` : '';
        setRawMsg(finalMsg);
    };

    // Handle change of raw message in top input
    const handleRawMsgChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
        const val = e.target.value;
        setRawMsg(val);

        if (parseTimeout.current) clearTimeout(parseTimeout.current);
        parseTimeout.current = setTimeout(() => {
            decodeRawMessage(val);
        }, 500);
    };

    // Handle field updates
    const handleFieldChange = async (index: number, key: 'tag' | 'value', value: string) => {
        const updated = [...fields];
        if (key === 'tag') {
            const newTagNum = parseInt(value) || 0;
            updated[index] = { ...updated[index], tag: newTagNum };
            
            // Fetch metadata for new tag
            if (newTagNum > 0) {
                try {
                    const res = await apiClient.get(`/dictionary/lookup-tag?beginString=${beginString}&tag=${newTagNum}`);
                    updated[index].name = res.data.name;
                    updated[index].type = res.data.type;
                } catch {
                    updated[index].name = 'Unknown Tag';
                    updated[index].type = 'UNKNOWN';
                }
            }
        } else {
            updated[index] = { ...updated[index], value: value };
        }
        setFields(updated);
        updateRawFromFields(updated);
    };

    // Add new field
    const handleAddField = () => {
        const newField: FixField = {
            tag: 0,
            name: 'New Tag',
            value: '',
            type: 'UNKNOWN'
        };
        const updated = [...fields, newField];
        setFields(updated);
        updateRawFromFields(updated);
    };

    // Delete field
    const handleDeleteField = (index: number) => {
        const updated = fields.filter((_, i) => i !== index);
        setFields(updated);
        updateRawFromFields(updated);
    };

    // Save/Send handler
    const handleSubmit = async () => {
        setSubmitting(true);
        setError(null);
        try {
            if (onSave) {
                onSave(rawMsg);
                onClose();
            } else if (onSend) {
                await onSend(rawMsg);
                onClose();
            }
        } catch (err: any) {
            setError(err.response?.data?.message || err.message || 'Operation failed');
        } finally {
            setSubmitting(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in">
            <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-4xl max-h-[85vh] flex flex-col shadow-2xl overflow-hidden animate-scale-up">
                
                {/* Header */}
                <div className="p-5 border-b border-slate-800 bg-slate-800/30 flex justify-between items-center">
                    <div>
                        <h3 className="text-lg font-bold text-white">{title}</h3>
                        <p className="text-xs text-slate-400 mt-1">Configure your FIX tags and values below.</p>
                    </div>
                    <div className="flex items-center gap-4">
                        <div className="flex items-center gap-2">
                            <span className="text-xs text-slate-500 font-bold uppercase tracking-wider">Dictionary:</span>
                            <select
                                value={beginString}
                                onChange={(e) => setBeginString(e.target.value)}
                                className="bg-slate-800 border border-slate-700 text-slate-300 text-xs rounded-lg px-2.5 py-1.5 focus:outline-none focus:ring-2 focus:ring-blue-500"
                            >
                                <option value="FIX.4.0">FIX 4.0</option>
                                <option value="FIX.4.1">FIX 4.1</option>
                                <option value="FIX.4.2">FIX 4.2</option>
                                <option value="FIX.4.3">FIX 4.3</option>
                                <option value="FIX.4.4">FIX 4.4</option>
                                <option value="FIX.5.0">FIX 5.0</option>
                            </select>
                        </div>
                        <button
                            onClick={onClose}
                            className="p-1.5 bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-white rounded-lg transition-colors"
                        >
                            <X size={18} />
                        </button>
                    </div>
                </div>

                {/* Body */}
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                    {error && (
                        <div className="p-4 bg-rose-500/10 border border-rose-500/20 text-rose-400 text-sm rounded-xl flex items-start gap-2">
                            <HelpCircle size={18} className="mt-0.5 shrink-0" />
                            <span>{error}</span>
                        </div>
                    )}

                    {/* Raw Text Box */}
                    <div className="space-y-2">
                        <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">Raw Message (Pipe Delimited)</label>
                        <div className="relative">
                            <textarea
                                value={rawMsg}
                                onChange={handleRawMsgChange}
                                placeholder="8=FIX.4.2|9=123|35=D|..."
                                rows={2}
                                className="w-full bg-slate-950 border border-slate-800 rounded-xl p-4 font-mono text-sm text-slate-200 placeholder-slate-700 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 resize-none transition-colors"
                            />
                            {loading && (
                                <div className="absolute right-3 bottom-3">
                                    <RefreshCw className="text-blue-500 animate-spin" size={16} />
                                </div>
                            )}
                        </div>
                    </div>

                    {/* Table View */}
                    <div className="space-y-3">
                        <div className="flex justify-between items-center">
                            <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider">Decoded Fields</label>
                            <button
                                onClick={handleAddField}
                                className="px-3 py-1.5 bg-blue-600/10 hover:bg-blue-600 text-blue-400 hover:text-white text-xs font-bold rounded-lg flex items-center gap-1.5 transition-all"
                            >
                                <Plus size={14} />
                                <span>Add Field</span>
                            </button>
                        </div>

                        <div className="border border-slate-800/80 rounded-xl overflow-hidden bg-slate-950/40">
                            <table className="w-full text-left border-collapse">
                                <thead>
                                    <tr className="bg-slate-900/60 border-b border-slate-800 text-xs text-slate-400 font-semibold">
                                        <th className="py-3 px-4 w-28">Tag</th>
                                        <th className="py-3 px-4 w-48">Name</th>
                                        <th className="py-3 px-4">Value</th>
                                        <th className="py-3 px-4 w-36">Type</th>
                                        <th className="py-3 px-4 w-16 text-center"></th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-800/60">
                                    {fields.length === 0 ? (
                                        <tr>
                                            <td colSpan={5} className="py-8 text-center text-sm text-slate-600 font-medium">
                                                No fields parsed. Paste a message or click 'Add Field'.
                                            </td>
                                        </tr>
                                    ) : (
                                        fields.map((field, idx) => (
                                            <tr key={idx} className="group hover:bg-slate-800/10 transition-colors">
                                                <td className="py-2.5 px-4">
                                                    <input
                                                        type="text"
                                                        value={field.tag === 0 ? '' : field.tag}
                                                        onChange={(e) => handleFieldChange(idx, 'tag', e.target.value)}
                                                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2 py-1.5 text-sm font-mono text-slate-300 focus:outline-none focus:border-blue-500 transition-colors"
                                                        placeholder="e.g. 55"
                                                    />
                                                </td>
                                                <td className="py-2.5 px-4 text-sm font-medium text-slate-400 truncate max-w-[12rem]">
                                                    {field.name}
                                                </td>
                                                <td className="py-2.5 px-4">
                                                    <input
                                                        type="text"
                                                        value={field.value}
                                                        onChange={(e) => handleFieldChange(idx, 'value', e.target.value)}
                                                        className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2 py-1.5 text-sm font-mono text-slate-300 focus:outline-none focus:border-blue-500 transition-colors"
                                                        placeholder="Value"
                                                    />
                                                </td>
                                                <td className="py-2.5 px-4">
                                                    <span className="text-xs font-semibold px-2 py-1 rounded bg-slate-800 text-slate-500 uppercase tracking-wider block text-center truncate">
                                                        {field.type}
                                                    </span>
                                                </td>
                                                <td className="py-2.5 px-4 text-center">
                                                    <button
                                                        onClick={() => handleDeleteField(idx)}
                                                        className="p-1.5 text-slate-600 hover:text-rose-400 hover:bg-rose-500/10 rounded-lg transition-all"
                                                        title="Delete Row"
                                                    >
                                                        <Trash2 size={15} />
                                                    </button>
                                                </td>
                                            </tr>
                                        ))
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>

                {/* Footer */}
                <div className="p-5 border-t border-slate-800 bg-slate-800/10 flex justify-end gap-3">
                    <button
                        onClick={onClose}
                        className="px-4 py-2 border border-slate-800 hover:border-slate-700 text-slate-400 hover:text-white text-sm font-bold rounded-xl transition-all"
                    >
                        Cancel
                    </button>
                    <button
                        onClick={handleSubmit}
                        disabled={submitting || fields.length === 0}
                        className="px-5 py-2.5 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:hover:bg-blue-600 text-white text-sm font-bold rounded-xl flex items-center gap-2 shadow-lg hover:shadow-blue-500/20 transition-all"
                    >
                        {submitting ? (
                            <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                        ) : onSave ? (
                            <Save size={16} />
                        ) : (
                            <Send size={16} />
                        )}
                        <span>{onSave ? 'Save to Testcase' : 'Send Message'}</span>
                    </button>
                </div>
            </div>
        </div>
    );
};
