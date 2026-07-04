import {useEffect, useState} from 'react';
import {createWebSocketClient} from './api/client';
import {FixMessageEvent, SessionStatus} from './types';
import {Layout} from './components/Layout';
import {SessionDashboard} from './components/SessionDashboard';
import {TestRunner} from './components/TestRunner';
import {MessageLog} from './components/MessageLog';
import {ProductsEditor} from './components/ProductsEditor';
import {DictionaryExplorer} from './components/DictionaryExplorer';

function App() {
    const [messages, setMessages] = useState<FixMessageEvent[]>([]);
    const [sessions, setSessions] = useState<SessionStatus[]>([]);
    const [activeTab, setActiveTab] = useState<'sessions' | 'products' | 'tests' | 'logs' | 'dictionary'>('sessions');

    useEffect(() => {
        const wsClient = createWebSocketClient(
            (msg) => {
                setMessages((prev) => [msg, ...prev].slice(0, 100));
                // Sync sequence numbers from message events to session state
                setSessions((prev) => {
                    const index = prev.findIndex((s) => s.sessionId === msg.sessionId);
                    if (index >= 0 && (msg.senderSeqNum != null || msg.targetSeqNum != null)) {
                        const newSessions = [...prev];
                        newSessions[index] = {
                            ...newSessions[index],
                            ...(msg.senderSeqNum != null ? {expectedSenderNum: msg.senderSeqNum} : {}),
                            ...(msg.targetSeqNum != null ? {expectedTargetNum: msg.targetSeqNum} : {})
                        };
                        return newSessions;
                    }
                    return prev;
                });
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
        };
    }, []);

    return (
        <Layout activeTab={activeTab} onTabChange={setActiveTab}>
            {activeTab === 'sessions' &&
                <SessionDashboard sessions={sessions} setSessions={setSessions} messages={messages}/>}
            {activeTab === 'products' && <ProductsEditor />}
            {activeTab === 'tests' && <TestRunner />}
            {activeTab === 'logs' && <MessageLog messages={messages} />}
            {activeTab === 'dictionary' && <DictionaryExplorer />}
        </Layout>
    );
}

export default App;
