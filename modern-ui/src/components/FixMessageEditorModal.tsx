import React, {useEffect, useState, useRef} from 'react';
import {cn} from '../lib/utils';
import {apiClient} from '../api/client';
import {X, Plus, Trash2, Send, Save, RefreshCw, HelpCircle} from 'lucide-react';

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

     // Close on ESC
    useEffect(() => {
        if (!isOpen) return;
        const handleKey = (e: KeyboardEvent) => {
            if (e.key === 'Escape') onClose();
        };
        document.addEventListener('keydown', handleKey);
        return () => document.removeEventListener('keydown', handleKey);
    }, [isOpen, onClose]);

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
            const response = await apiClient.post('/dictionary/decode-message', {message: msg});
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
        setRawMsg(generated);
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
            updated[index] = {...updated[index], tag: newTagNum};

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
            updated[index] = {...updated[index], value};
         }
        setFields(updated);
        updateRawFromFields(updated);
     };

     // Add new field
    const handleAddField = () => {
        const newField: FixField = {tag: 0, name: 'New Tag', value: '', type: 'UNKNOWN'};
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
         <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-fade-in" onClick={onClose}>
             <div className="bg-surface-900 border border-white/10 rounded-2xl w-full max-w-4xl max-h-[85vh] flex flex-col shadow-2xl overflow-hidden animate-scale-up" onClick={(e) => e.stopPropagation()}>

                 {/* Header */}
                <div className="p-5 border-b border-white/5 bg-white/[0.02] flex justify-between items-center shrink-0">
                    <div>
                        <h3 className="text-base font-bold text-white">{title}</h3>
                       <p className="text-[11px] text-surface-500 mt-0.5">Configure FIX tags and values</p>
                    </div>
                   <div className="flex items-center gap-3">
                        {/* Dictionary selector */}
                       <select
                            value={beginString}
                            onChange={(e) => setBeginString(e.target.value)}
                            className="input-dark text-xs px-2.5 py-1.5"
                        >
                            <option value="FIX.4.0">FIX 4.0</option>
                            <option value="FIX.4.1">FIX 4.1</option>
                            <option value="FIX.4.2">FIX 4.2</option>
                            <option value="FIX.4.3">FIX 4.3</option>
                            <option value="FIX.4.4">FIX 4.4</option>
                            <option value="FIX.5.0">FIX 5.0</option>
                        </select>
                       <button
                            onClick={onClose}
                            className="p-1.5 rounded-lg text-surface-500 hover:text-white hover:bg-white/[0.05] transition-all"
                         >
                           <X size={16} />
                       </button>
                   </div>
               </div>

               {/* Body */}
              <div className="flex-1 overflow-y-auto p-5 space-y-5">
                  {error && (
                        <div className="p-3.5 bg-rose-500/10 border border-rose-500/20 text-rose-400 text-xs rounded-xl flex items-start gap-2.5">
                            <HelpCircle size={16} className="mt-0.5 shrink-0" />
                           <span>{error}</span>
                       </div>
                   )}

                   {/* Raw Text Box */}
                  <div className="space-y-2">
                      <label className="block text-[10px] font-bold text-surface-500 uppercase tracking-wider">Raw Message (Pipe Delimited)</label>
                      <div className="relative">
                          <textarea
                                 value={rawMsg}
                                onChange={handleRawMsgChange}
                                placeholder="8=FIX.4.2|9=123|35=D|..."
                                rows={2}
                                className="w-full bg-white/[0.03] border border-white/5 rounded-xl p-4 font-mono text-[11px] text-slate-300 placeholder-surface-700 focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/50 resize-none transition-colors"
                             />
                          {loading && (
                                <div className="absolute right-3 top-1/2 -translate-y-1/2">
                                    <RefreshCw className="text-blue-500 animate-spin" size={14} />
                                </div>
                           )}
                       </div>
                   </div>

                   {/* Decoded Fields Table */}
                  <div className="space-y-3">
                      <div className="flex justify-between items-center">
                          <label className="block text-[10px] font-bold text-surface-500 uppercase tracking-wider">Decoded Fields</label>
                          <button
                                 onClick={handleAddField}
                                className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white text-[11px] font-bold rounded-lg flex items-center gap-1.5 transition-all shadow-md shadow-blue-600/10"
                             >
                               <Plus size={12} />
                               <span>Add Field</span>
                           </button>
                      </div>

                      <div className="border border-white/5 rounded-xl overflow-hidden bg-white/[0.02]">
                          <table className="w-full text-left border-collapse">
                              <thead>
                                  <tr className="bg-white/[0.02] border-b border-white/5 text-[10px] text-surface-600 font-semibold uppercase tracking-wider">
                                      <th className="py-2.5 px-4 w-28">Tag</th>
                                     <th className="py-2.5 px-4 w-44">Name</th>
                                     <th className="py-2.5 px-4">Value</th>
                                     <th className="py-2.5 px-4 w-32">Type</th>
                                     <th className="py-2.5 px-4 w-12 text-center"></th>
                                 </tr>
                              </thead>
                              <tbody className="divide-y divide-white/[0.03]">
                                  {fields.length === 0 ? (
                                       <tr>
                                           <td colSpan={5} className="py-8 text-center text-xs text-surface-600">
                                               No fields to display — paste a raw message or add a field
                                           </td>
                                       </tr>
                                   ) : (
                                      fields.map((field, idx) => (
                                           <tr key={idx} className="group hover:bg-white/[0.02] transition-colors">
                                               <td className="py-2 px-4">
                                                   <input
                                                          type="text"
                                                          value={field.tag === 0 ? '' : field.tag}
                                                          onChange={(e) => handleFieldChange(idx, 'tag', e.target.value)}
                                                          className="w-full bg-white/[0.03] border border-white/5 rounded-lg px-2.5 py-1.5 text-xs font-mono text-slate-300 focus:outline-none focus:border-blue-500/50 transition-colors"
                                                          placeholder="e.g. 55"
                                                       />
                                               </td>
                                               <td className="py-2 px-4 text-xs font-medium text-surface-500 truncate max-w-[10rem]">
                                                   {field.name}
                                               </td>
                                               <td className="py-2 px-4">
                                                   <input
                                                          type="text"
                                                          value={field.value}
                                                          onChange={(e) => handleFieldChange(idx, 'value', e.target.value)}
                                                          className="w-full bg-white/[0.03] border border-white/5 rounded-lg px-2.5 py-1.5 text-xs font-mono text-slate-300 focus:outline-none focus:border-blue-500/50 transition-colors"
                                                          placeholder="Value"
                                                       />
                                               </td>
                                               <td className="py-2 px-4">
                                                   <span className="text-[9px] font-semibold px-2 py-1 rounded bg-white/[0.05] text-surface-600 uppercase tracking-wider block text-center">
                                                       {field.type}
                                                   </span>
                                               </td>
                                               <td className="py-2 px-4 text-center">
                                                   <button
                                                          onClick={() => handleDeleteField(idx)}
                                                          className="p-1 rounded-lg text-surface-600 hover:text-rose-400 hover:bg-rose-500/10 transition-all"
                                                          title="Delete Row"
                                                       >
                                                       <Trash2 size={13} />
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
              <div className="p-5 border-t border-white/5 bg-white/[0.02] flex justify-end gap-2.5 shrink-0">
                  <button onClick={onClose} className="btn-secondary px-5 py-2.5 rounded-xl text-xs font-semibold">
                      Cancel
                   </button>
                  <button
                        onClick={handleSubmit}
                       disabled={submitting || fields.length === 0}
                       className={cn(
                             'px-5 py-2.5 rounded-xl flex items-center gap-2 text-xs font-bold shadow-lg transition-all',
                            submitting ? 'bg-blue-600/50 cursor-wait' : 'bg-blue-600 hover:bg-blue-500 text-white shadow-blue-600/10 hover:shadow-blue-500/20'
                         )}
                   >
                       {submitting ? (
                            <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                        ) : onSave ? (
                            <Save size={14} />
                        ) : (
                            <Send size={14} />
                        )}
                      <span>{onSave ? 'Save to Testcase' : 'Send Message'}</span>
                   </button>
               </div>
           </div>
       </div>
   );
};
