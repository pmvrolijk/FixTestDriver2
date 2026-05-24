import React, { useEffect, useState } from 'react';
import { createWebSocketClient } from './api/client';
import { FixMessageEvent, SessionStatus } from './types';
import { Layout } from './components/Layout';
import { SessionDashboard } from './components/SessionDashboard';
import { TestRunner } from './components/TestRunner';
import { MessageLog } from './components/MessageLog';

function App() {
    const [messages, setMessages] = useState<FixMessageEvent[]>([]);
    const [sessions, setSessions] = useState<SessionStatus[]>([]);
    const [activeTab, setActiveTab] = useState<'sessions' | 'tests' | 'logs'>('sessions');

    useEffect(() => {
        const wsClient = createWebSocketClient(
            (msg) => setMessages((prev) => [msg, ...prev].slice(0, 100)),
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
            {activeTab === 'sessions' && <SessionDashboard sessions={sessions} setSessions={setSessions} />}
            {activeTab === 'tests' && <TestRunner />}
            {activeTab === 'logs' && <MessageLog messages={messages} />}
        </Layout>
    );
}

export default App;
