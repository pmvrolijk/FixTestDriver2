import {ReactNode, useEffect, useRef, useState} from 'react';
import {createWebSocketClient} from './api/client';
import {FixMessageEvent, SessionStatus} from './types';
import {cn} from './lib/utils';
import {Layout} from './components/Layout';
import {SessionDashboard} from './components/SessionDashboard';
import {TestRunner} from './components/TestRunner';
import {MessageLog} from './components/MessageLog';
import {ProductsEditor} from './components/ProductsEditor';
import {DictionaryExplorer} from './components/DictionaryExplorer';

// Keeps every tab mounted so its local state survives tab switches; inactive tabs
// are hidden with CSS rather than unmounted.
function TabPanel({active, children}: {active: boolean; children: ReactNode}) {
    return <div className={cn('h-full', !active && 'hidden')}>{children}</div>;
}

// Maximum messages retained in the scrollback. The list is virtualized, so a large
// cap stays cheap — only visible rows are ever in the DOM.
const MESSAGE_CAP = 1000;

// Module-level monotonic counter used to stamp a stable `_id` on every message,
// so React keys never collide and prepending doesn't force a full re-render.
let messageSeq = 0;

function App() {
    const [messages, setMessages] = useState<FixMessageEvent[]>([]);
    const [sessions, setSessions] = useState<SessionStatus[]>([]);
    const [activeTab, setActiveTab] = useState<'sessions' | 'products' | 'tests' | 'logs' | 'dictionary'>('sessions');

    // Buffer of messages received since the last animation frame, plus the pending
    // rAF handle. Coalescing bursts into one flush per frame keeps the UI responsive
    // under high message volume.
    const bufferRef = useRef<FixMessageEvent[]>([]);
    const rafRef = useRef<number | null>(null);

    useEffect(() => {
        const flush = () => {
            rafRef.current = null;
            const batch = bufferRef.current;
            if (batch.length === 0) return;
            bufferRef.current = [];

            // Newest first: reverse the buffer (arrival order) and prepend, then cap.
            setMessages((prev) => [...batch.reverse(), ...prev].slice(0, MESSAGE_CAP));

            // Fold sequence-number syncing into a single pass: keep the latest
            // sender/target seq num seen per session across the whole batch.
            const latestBySession = new Map<string, {sender?: number; target?: number}>();
            for (const msg of batch) {
                if (msg.senderSeqNum == null && msg.targetSeqNum == null) continue;
                const entry = latestBySession.get(msg.sessionId) ?? {};
                if (msg.senderSeqNum != null) entry.sender = msg.senderSeqNum;
                if (msg.targetSeqNum != null) entry.target = msg.targetSeqNum;
                latestBySession.set(msg.sessionId, entry);
            }
            if (latestBySession.size > 0) {
                setSessions((prev) => {
                    let changed = false;
                    const next = prev.map((s) => {
                        const seq = latestBySession.get(s.sessionId as string);
                        if (!seq) return s;
                        changed = true;
                        return {
                            ...s,
                            ...(seq.sender != null ? {expectedSenderNum: seq.sender} : {}),
                            ...(seq.target != null ? {expectedTargetNum: seq.target} : {})
                        };
                    });
                    return changed ? next : prev;
                });
            }
        };

        const wsClient = createWebSocketClient(
            (msg) => {
                msg._id = messageSeq++;
                bufferRef.current.push(msg);
                if (rafRef.current == null) {
                    rafRef.current = requestAnimationFrame(flush);
                }
            },
            (status) => {
                setSessions((prev) => {
                    const index = prev.findIndex((s) => s.sessionId === status.sessionId);
                    if (index >= 0) {
                        const newSessions = [...prev];
                        newSessions[index] = { ...newSessions[index], ...status };
                        return newSessions;
                    }
                    return [...prev, status];
                });
            }
        );

        wsClient.activate();
        return () => {
            wsClient.deactivate();
            if (rafRef.current != null) {
                cancelAnimationFrame(rafRef.current);
                rafRef.current = null;
            }
        };
    }, []);

    return (
        <Layout activeTab={activeTab} onTabChange={setActiveTab}>
            <TabPanel active={activeTab === 'sessions'}>
                <SessionDashboard sessions={sessions} setSessions={setSessions} messages={messages}/>
            </TabPanel>
            <TabPanel active={activeTab === 'products'}><ProductsEditor /></TabPanel>
            <TabPanel active={activeTab === 'tests'}><TestRunner /></TabPanel>
            <TabPanel active={activeTab === 'logs'}><MessageLog messages={messages} /></TabPanel>
            <TabPanel active={activeTab === 'dictionary'}><DictionaryExplorer /></TabPanel>
        </Layout>
    );
}

export default App;
