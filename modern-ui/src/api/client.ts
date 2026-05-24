import axios from 'axios';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';
const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws-fix';

export const apiClient = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

export const createWebSocketClient = (onMessage: (msg: any) => void, onStatus: (status: any) => void) => {
    const client = new Client({
        webSocketFactory: () => new SockJS(WS_URL),
        debug: (str) => {
            console.log(str);
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
    });

    client.onConnect = () => {
        console.log('Connected to WebSocket');
        client.subscribe('/topic/messages', (message) => {
            onMessage(JSON.parse(message.body));
        });
        client.subscribe('/topic/sessions', (message) => {
            onStatus(JSON.parse(message.body));
        });
    };

    client.onStompError = (frame) => {
        console.error('Broker reported error: ' + frame.headers['message']);
        console.error('Additional details: ' + frame.body);
    };

    return client;
};
