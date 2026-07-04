import React from 'react';
import { X, HelpCircle } from 'lucide-react';

interface HelpModalProps {
    isOpen: boolean;
    onClose: () => void;
}

const TABS = [
    { name: 'Sessions', purpose: 'Manage FIX engine sessions — connect, disconnect, reset sequence numbers, and edit session configuration.' },
    { name: 'Products', purpose: 'Edit the product symbol dictionary. Products defined here are available via the <Product=SYM,PROP> macro in test scripts.' },
    { name: 'Tests', purpose: 'Browse, create, edit, and run .def test scripts. View per-run pass/fail output.' },
    { name: 'Logs', purpose: 'Real-time stream of all FIX messages sent and received across all sessions.' },
    { name: 'Dictionary', purpose: 'Browse FIX field definitions, message types, and enum values from the loaded QuickFIX dictionaries.' },
];

const COMMANDS = [
    { command: 'I<fields>', description: 'Send a pipe-separated FIX message on the active session.', example: 'I8=FIX.4.2|35=D|49=INIT|56=ACC|11=<Clordid=Ord1>|55=MSFT|60=<Date>|' },
    { command: 'E<fields>', description: 'Wait up to 10 s for an incoming message matching the expected fields. Values inside <…> are treated as Java regex patterns.', example: 'E8=FIX.4.2|35=8|11=<Ord1-[0-9]*>|39=0|' },
    { command: 'WAIT <ms>', description: 'Pause execution for N milliseconds.', example: 'WAIT 1000' },
    { command: 'iCONNECT <sessionId>', description: 'Switch the active session context. The session ID must match an entry in FixEngine.cfg.', example: 'iCONNECT FIX.4.2:INIT->ACC' },
    { command: 'LOOP <n> … END', description: 'Repeat the enclosed block of steps N times.', example: 'LOOP 5\nI8=FIX.4.2|35=D|…|\nWAIT 200\nEND' },
    { command: 'RESET <id>', description: 'Clear the stored ClOrdID mapping for the given template ID.', example: 'RESET Ord1' },
    { command: 'eDISCONNECT', description: 'Mark that a session disconnect is expected at this point in the script.', example: 'eDISCONNECT' },
    { command: '# <text>', description: 'Line comment — ignored by the parser.', example: '# This is a comment' },
];

const MACROS = [
    { macro: '<Clordid=NAME>', example: '11=<Clordid=Ord1>', description: 'Generate a unique ClOrdID (NAME-NNNNNN) and store it under NAME for later reference with <OrigClordid=NAME>.' },
    { macro: '<OrigClordid=NAME>', example: '41=<OrigClordid=Ord1>', description: 'Substitute the actual ClOrdID previously generated for NAME. Used in cancel/replace messages (tag 41).' },
    { macro: '<Date>', example: '60=<Date>', description: 'Current timestamp formatted as yyyyMMdd-HH:mm:ss.' },
    { macro: '<Date+Nh>', example: '60=<Date+2h>', description: 'Timestamp N hours from now in default format.' },
    { macro: '<Date+Nd>', example: '60=<Date+1d>', description: 'Timestamp N days from now in default format.' },
    { macro: '<Date+Nd,format>', example: '60=<Date+1d,yyyyMMdd>', description: 'Date with offset and a custom Java SimpleDateFormat pattern.' },
    { macro: '<Product=SYM,PROP>', example: '55=<Product=MSFT,Symbol>', description: 'Look up property PROP for product SYM from the Products dictionary. Defined on the Products tab.' },
    { macro: '<REGEX> (E lines)', example: '11=<Ord1-[0-9]*>', description: 'In Expect lines, any value inside <…> is matched as a Java regex against the actual field value.' },
];

export const HelpModal: React.FC<HelpModalProps> = ({ isOpen, onClose }) => {
    if (!isOpen) return null;

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
            onClick={onClose}
        >
            <div
                className="relative bg-[#0f111a] border border-white/10 rounded-2xl shadow-2xl w-full max-w-3xl max-h-[85vh] flex flex-col mx-4"
                onClick={(e) => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center gap-3 px-6 py-4 border-b border-white/5 flex-shrink-0">
                    <HelpCircle size={18} className="text-blue-400" />
                    <h2 className="text-sm font-semibold text-slate-100 flex-1">FixTestDriver — Help & Reference</h2>
                    <button
                        onClick={onClose}
                        className="text-slate-400 hover:text-slate-100 transition-colors p-1 rounded-lg hover:bg-white/5"
                    >
                        <X size={16} />
                    </button>
                </div>

                {/* Scrollable body */}
                <div className="overflow-y-auto flex-1 px-6 py-5 space-y-8">

                    {/* Introduction */}
                    <div>
                        <p className="text-xs text-slate-300 leading-relaxed">
                            FixTestDriver is a web-based test harness for FIX protocol engines. It lets you define test
                            scenarios in <span className="font-mono text-blue-300">.def</span> script files, run them
                            against live FIX sessions, and inspect every message in real time.
                        </p>
                    </div>

                    {/* Tabs overview */}
                    <section>
                        <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">Tabs</h3>
                        <table className="w-full text-xs border-collapse">
                            <thead>
                                <tr className="text-left text-slate-500 border-b border-white/5">
                                    <th className="pb-2 pr-4 font-medium w-24">Tab</th>
                                    <th className="pb-2 font-medium">Purpose</th>
                                </tr>
                            </thead>
                            <tbody>
                                {TABS.map((row, i) => (
                                    <tr key={row.name} className={i % 2 === 0 ? 'bg-white/[0.02]' : ''}>
                                        <td className="py-2 pr-4 font-medium text-blue-300 align-top whitespace-nowrap">{row.name}</td>
                                        <td className="py-2 text-slate-300 leading-relaxed">{row.purpose}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </section>

                    {/* Commands */}
                    <section>
                        <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">.def Script Commands</h3>
                        <table className="w-full text-xs border-collapse">
                            <thead>
                                <tr className="text-left text-slate-500 border-b border-white/5">
                                    <th className="pb-2 pr-4 font-medium w-40">Command</th>
                                    <th className="pb-2 pr-4 font-medium">Description</th>
                                    <th className="pb-2 font-medium w-52">Example</th>
                                </tr>
                            </thead>
                            <tbody>
                                {COMMANDS.map((row, i) => (
                                    <tr key={row.command} className={i % 2 === 0 ? 'bg-white/[0.02]' : ''}>
                                        <td className="py-2 pr-4 font-mono text-emerald-300 align-top whitespace-nowrap">{row.command}</td>
                                        <td className="py-2 pr-4 text-slate-300 leading-relaxed align-top">{row.description}</td>
                                        <td className="py-2 align-top">
                                            <pre className="font-mono text-slate-400 whitespace-pre-wrap break-all leading-relaxed">{row.example}</pre>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </section>

                    {/* Macros */}
                    <section>
                        <h3 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">.def Macros</h3>
                        <p className="text-xs text-slate-500 mb-3">
                            Macros are substituted before a message is sent. They may appear in field values of
                            <span className="font-mono text-blue-300 mx-1">I</span> lines (and in
                            <span className="font-mono text-blue-300 mx-1">E</span> lines for regex matching).
                        </p>
                        <table className="w-full text-xs border-collapse">
                            <thead>
                                <tr className="text-left text-slate-500 border-b border-white/5">
                                    <th className="pb-2 pr-4 font-medium w-44">Macro</th>
                                    <th className="pb-2 pr-4 font-medium">Description</th>
                                    <th className="pb-2 font-medium w-48">Example</th>
                                </tr>
                            </thead>
                            <tbody>
                                {MACROS.map((row, i) => (
                                    <tr key={row.macro} className={i % 2 === 0 ? 'bg-white/[0.02]' : ''}>
                                        <td className="py-2 pr-4 font-mono text-amber-300 align-top whitespace-nowrap">{row.macro}</td>
                                        <td className="py-2 pr-4 text-slate-300 leading-relaxed align-top">{row.description}</td>
                                        <td className="py-2 align-top">
                                            <pre className="font-mono text-slate-400 whitespace-pre-wrap break-all leading-relaxed">{row.example}</pre>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </section>

                </div>
            </div>
        </div>
    );
};
