import React, {useEffect, useState} from 'react';
import {X, ChevronDown, ChevronRight, Loader2} from 'lucide-react';
import {apiClient} from '../api/client';
import {SessionConfig, SessionStatus} from '../types';

interface Props {
    mode: 'edit' | 'add';
    sessionStatus?: SessionStatus;
    onClose: () => void;
    onApplied: () => void;
}

const BEGIN_STRINGS = ['FIX.4.0', 'FIX.4.1', 'FIX.4.2', 'FIX.4.3', 'FIX.4.4', 'FIXT.1.1'];
const YN_OPTIONS = ['', 'Y', 'N'];

const EMPTY_CONFIG: SessionConfig = {
    beginString: 'FIX.4.2',
    senderCompID: '',
    targetCompID: '',
    connectionType: 'initiator',
};

function Section({title, defaultOpen = true, children}: {title: string; defaultOpen?: boolean; children: React.ReactNode}) {
    const [open, setOpen] = useState(defaultOpen);
    return (
        <div className="border border-gray-700 rounded mb-3">
            <button
                type="button"
                className="w-full flex items-center gap-2 px-4 py-2 text-sm font-semibold text-gray-300 bg-gray-800 hover:bg-gray-750 rounded-t select-none"
                onClick={() => setOpen(o => !o)}
            >
                {open ? <ChevronDown className="w-4 h-4"/> : <ChevronRight className="w-4 h-4"/>}
                {title}
            </button>
            {open && <div className="p-4 grid grid-cols-2 gap-3">{children}</div>}
        </div>
    );
}

function Field({label, children, full}: {label: string; children: React.ReactNode; full?: boolean}) {
    return (
        <div className={full ? 'col-span-2' : ''}>
            <label className="block text-xs text-gray-400 mb-1">{label}</label>
            {children}
        </div>
    );
}

const inputClass = "w-full bg-gray-700 border border-gray-600 rounded px-2 py-1.5 text-sm text-white focus:outline-none focus:border-blue-500";
const readonlyClass = "w-full bg-gray-800 border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-400 cursor-not-allowed";

export const SessionConfigModal: React.FC<Props> = ({mode, sessionStatus, onClose, onApplied}) => {
    const [config, setConfig] = useState<SessionConfig>(EMPTY_CONFIG);
    const [loading, setLoading] = useState(mode === 'edit');
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (mode === 'edit' && sessionStatus) {
            const encodedId = encodeURIComponent(sessionStatus.sessionId as string);
            apiClient.get(`/sessions/${encodedId}/config`)
                .then(res => setConfig(res.data))
                .catch(e => setError(e?.response?.data ?? e.message))
                .finally(() => setLoading(false));
        }
    }, [mode, sessionStatus]);

    useEffect(() => {
        const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [onClose]);

    const set = (field: keyof SessionConfig) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
        setConfig(c => ({...c, [field]: e.target.value}));

    const handleApply = async () => {
        setSaving(true);
        setError(null);
        try {
            if (mode === 'edit' && sessionStatus) {
                const encodedId = encodeURIComponent(sessionStatus.sessionId as string);
                await apiClient.put(`/sessions/${encodedId}/config`, config);
            } else {
                await apiClient.post('/sessions/new', config);
            }
            onApplied();
            onClose();
        } catch (e: unknown) {
            const err = e as {response?: {data?: string}; message?: string};
            setError(err?.response?.data ?? err?.message ?? 'Request failed');
        } finally {
            setSaving(false);
        }
    };

    const isInitiator = config.connectionType === 'initiator';

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
            <div className="bg-gray-900 border border-gray-700 rounded-xl w-full max-w-2xl max-h-[90vh] flex flex-col shadow-2xl">
                {/* Header */}
                <div className="flex items-center justify-between px-5 py-4 border-b border-gray-700">
                    <h2 className="text-white font-semibold text-base">
                        {mode === 'edit' ? 'Edit Session' : 'Add Session'}
                    </h2>
                    <button onClick={onClose} className="text-gray-400 hover:text-white">
                        <X className="w-5 h-5"/>
                    </button>
                </div>

                {/* Status strip — edit mode only */}
                {mode === 'edit' && sessionStatus && (
                    <div className="flex items-center gap-6 px-5 py-2.5 bg-gray-800 border-b border-gray-700 text-xs text-gray-300">
                        <span className="flex items-center gap-1.5">
                            <span className={`w-2 h-2 rounded-full ${sessionStatus.loggedOn ? 'bg-green-400' : 'bg-gray-500'}`}/>
                            {sessionStatus.loggedOn ? 'Logged on' : 'Offline'}
                        </span>
                        <span>Out seq: <span className="text-white font-mono">{sessionStatus.expectedSenderNum ?? '—'}</span></span>
                        <span>In seq: <span className="text-white font-mono">{sessionStatus.expectedTargetNum ?? '—'}</span></span>
                        <span className="text-gray-500">{sessionStatus.sessionId}</span>
                    </div>
                )}

                {/* Body */}
                <div className="flex-1 overflow-y-auto px-5 py-4">
                    {loading
                        ? <div className="flex justify-center py-12"><Loader2 className="w-6 h-6 animate-spin text-gray-400"/></div>
                        : <>
                            <Section title="Identity">
                                <Field label="Begin String">
                                    {mode === 'edit'
                                        ? <input className={readonlyClass} value={config.beginString} readOnly/>
                                        : <select className={inputClass} value={config.beginString} onChange={set('beginString')}>
                                            {BEGIN_STRINGS.map(s => <option key={s}>{s}</option>)}
                                        </select>
                                    }
                                </Field>
                                <Field label="Sender Comp ID">
                                    <input className={mode === 'edit' ? readonlyClass : inputClass}
                                           value={config.senderCompID} readOnly={mode === 'edit'}
                                           onChange={mode === 'add' ? set('senderCompID') : undefined}/>
                                </Field>
                                <Field label="Target Comp ID">
                                    <input className={mode === 'edit' ? readonlyClass : inputClass}
                                           value={config.targetCompID} readOnly={mode === 'edit'}
                                           onChange={mode === 'add' ? set('targetCompID') : undefined}/>
                                </Field>
                                <Field label="Session Qualifier">
                                    <input className={mode === 'edit' ? readonlyClass : inputClass}
                                           value={config.sessionQualifier ?? ''} readOnly={mode === 'edit'}
                                           onChange={mode === 'add' ? set('sessionQualifier') : undefined}
                                           placeholder="optional"/>
                                </Field>
                            </Section>

                            <Section title="Connection">
                                <Field label="Connection Type" full>
                                    <div className="flex gap-4 mt-1">
                                        {['initiator', 'acceptor'].map(ct => (
                                            <label key={ct} className="flex items-center gap-2 text-sm text-gray-300 cursor-pointer">
                                                <input type="radio" name="connectionType" value={ct}
                                                       checked={config.connectionType === ct}
                                                       onChange={set('connectionType')}
                                                       className="accent-blue-500"/>
                                                {ct.charAt(0).toUpperCase() + ct.slice(1)}
                                            </label>
                                        ))}
                                    </div>
                                </Field>
                                {isInitiator ? <>
                                    <Field label="Socket Connect Host">
                                        <input className={inputClass} value={config.socketConnectHost ?? ''}
                                               onChange={set('socketConnectHost')} placeholder="localhost"/>
                                    </Field>
                                    <Field label="Socket Connect Port">
                                        <input className={inputClass} value={config.socketConnectPort ?? ''}
                                               onChange={set('socketConnectPort')} placeholder="e.g. 9999"/>
                                    </Field>
                                </> : (
                                    <Field label="Socket Accept Port">
                                        <input className={inputClass} value={config.socketAcceptPort ?? ''}
                                               onChange={set('socketAcceptPort')} placeholder="e.g. 9888"/>
                                    </Field>
                                )}
                            </Section>

                            <Section title="Timing" defaultOpen={false}>
                                <Field label="Start Time">
                                    <input className={inputClass} value={config.startTime ?? ''}
                                           onChange={set('startTime')} placeholder="00:00:00"/>
                                </Field>
                                <Field label="End Time">
                                    <input className={inputClass} value={config.endTime ?? ''}
                                           onChange={set('endTime')} placeholder="00:00:00"/>
                                </Field>
                                <Field label="HeartBtInt (s)">
                                    <input className={inputClass} value={config.heartBtInt ?? ''}
                                           onChange={set('heartBtInt')} placeholder="30"/>
                                </Field>
                                <Field label="Reconnect Interval (s)">
                                    <input className={inputClass} value={config.reconnectInterval ?? ''}
                                           onChange={set('reconnectInterval')} placeholder="5"/>
                                </Field>
                                <Field label="Logon Timeout (s)">
                                    <input className={inputClass} value={config.logonTimeout ?? ''}
                                           onChange={set('logonTimeout')} placeholder="10"/>
                                </Field>
                                <Field label="Logout Timeout (s)">
                                    <input className={inputClass} value={config.logoutTimeout ?? ''}
                                           onChange={set('logoutTimeout')} placeholder="2"/>
                                </Field>
                            </Section>

                            <Section title="Authentication" defaultOpen={false}>
                                <Field label="Username">
                                    <input className={inputClass} value={config.username ?? ''}
                                           onChange={set('username')} placeholder="optional"/>
                                </Field>
                                <Field label="Password">
                                    <input type="password" className={inputClass} value={config.password ?? ''}
                                           onChange={set('password')} placeholder="optional"/>
                                </Field>
                                <Field label="Reset On Logon">
                                    <select className={inputClass} value={config.resetOnLogon ?? ''}
                                            onChange={set('resetOnLogon')}>
                                        {YN_OPTIONS.map(o => <option key={o} value={o}>{o || '— inherit default —'}</option>)}
                                    </select>
                                </Field>
                                <Field label="Reset On Logout">
                                    <select className={inputClass} value={config.resetOnLogout ?? ''}
                                            onChange={set('resetOnLogout')}>
                                        {YN_OPTIONS.map(o => <option key={o} value={o}>{o || '— inherit default —'}</option>)}
                                    </select>
                                </Field>
                                <Field label="Reset On Disconnect">
                                    <select className={inputClass} value={config.resetOnDisconnect ?? ''}
                                            onChange={set('resetOnDisconnect')}>
                                        {YN_OPTIONS.map(o => <option key={o} value={o}>{o || '— inherit default —'}</option>)}
                                    </select>
                                </Field>
                            </Section>

                            <Section title="Dictionary" defaultOpen={false}>
                                <Field label="Use Data Dictionary">
                                    <select className={inputClass} value={config.useDataDictionary ?? ''}
                                            onChange={set('useDataDictionary')}>
                                        {YN_OPTIONS.map(o => <option key={o} value={o}>{o || '— inherit default —'}</option>)}
                                    </select>
                                </Field>
                                <Field label="Data Dictionary" full>
                                    <input className={inputClass} value={config.dataDictionary ?? ''}
                                           onChange={set('dataDictionary')} placeholder="e.g. config/quickfix/FIX42.xml"/>
                                </Field>
                                <Field label="App Data Dictionary" full>
                                    <input className={inputClass} value={config.appDataDictionary ?? ''}
                                           onChange={set('appDataDictionary')} placeholder="FIXT.1.1 transport only"/>
                                </Field>
                            </Section>

                            <Section title="Advanced" defaultOpen={false}>
                                <Field label="File Store Path">
                                    <input className={inputClass} value={config.fileStorePath ?? ''}
                                           onChange={set('fileStorePath')} placeholder="data/FixEngine"/>
                                </Field>
                                <Field label="File Log Path">
                                    <input className={inputClass} value={config.fileLogPath ?? ''}
                                           onChange={set('fileLogPath')} placeholder="logs/FixEngine"/>
                                </Field>
                                <Field label="Refresh On Logon">
                                    <select className={inputClass} value={config.refreshOnLogon ?? ''}
                                            onChange={set('refreshOnLogon')}>
                                        {YN_OPTIONS.map(o => <option key={o} value={o}>{o || '— inherit default —'}</option>)}
                                    </select>
                                </Field>
                                <Field label="Check Comp ID">
                                    <select className={inputClass} value={config.checkCompID ?? ''}
                                            onChange={set('checkCompID')}>
                                        {YN_OPTIONS.map(o => <option key={o} value={o}>{o || '— inherit default —'}</option>)}
                                    </select>
                                </Field>
                                <Field label="Check Latency">
                                    <select className={inputClass} value={config.checkLatency ?? ''}
                                            onChange={set('checkLatency')}>
                                        {YN_OPTIONS.map(o => <option key={o} value={o}>{o || '— inherit default —'}</option>)}
                                    </select>
                                </Field>
                                <Field label="Validate Fields Have Values">
                                    <select className={inputClass} value={config.validateFieldsHaveValues ?? ''}
                                            onChange={set('validateFieldsHaveValues')}>
                                        {YN_OPTIONS.map(o => <option key={o} value={o}>{o || '— inherit default —'}</option>)}
                                    </select>
                                </Field>
                                <Field label="Validate Fields Out Of Order">
                                    <select className={inputClass} value={config.validateFieldsOutOfOrder ?? ''}
                                            onChange={set('validateFieldsOutOfOrder')}>
                                        {YN_OPTIONS.map(o => <option key={o} value={o}>{o || '— inherit default —'}</option>)}
                                    </select>
                                </Field>
                            </Section>
                        </>
                    }
                </div>

                {/* Footer */}
                <div className="px-5 py-4 border-t border-gray-700 flex flex-col gap-2">
                    {error && <p className="text-red-400 text-sm">{error}</p>}
                    <div className="flex justify-end gap-3">
                        <button onClick={onClose}
                                className="px-4 py-2 text-sm text-gray-300 hover:text-white border border-gray-600 rounded-lg hover:border-gray-500">
                            Cancel
                        </button>
                        <button onClick={handleApply} disabled={saving || loading}
                                className="flex items-center gap-2 px-4 py-2 text-sm bg-blue-600 hover:bg-blue-700 disabled:opacity-50 text-white rounded-lg font-medium">
                            {saving && <Loader2 className="w-4 h-4 animate-spin"/>}
                            Apply
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};
