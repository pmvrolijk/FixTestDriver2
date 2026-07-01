export interface SessionStatus {
    sessionId: String;
    loggedOn: boolean;
    expectedSenderNum?: number;
    expectedTargetNum?: number;
    connectionType?: string;
    host?: string;
    port?: string;
}

export interface FixMessageEvent {
    sessionId: string;
    direction: 'INCOMING' | 'OUTGOING' | 'INCOMING_ADMIN' | 'OUTGOING_ADMIN';
    rawMessage: string;
    msgType: string;
    timestamp: number;
    senderSeqNum?: number;
    targetSeqNum?: number;
}

export interface TestResult {
    id: number;
    testName: string;
    startTime: string;
    endTime: string;
    success: boolean;
    logOutput: string;
    errorMessage?: string;
}

export interface FileTreeNode {
    name: string;
    path: string;
    directory: boolean;
    children?: FileTreeNode[];
}
