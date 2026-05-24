export interface SessionStatus {
    sessionId: String;
    loggedOn: boolean;
    expectedSenderNum?: number;
    expectedTargetNum?: number;
}

export interface FixMessageEvent {
    sessionId: string;
    direction: 'INCOMING' | 'OUTGOING' | 'INCOMING_ADMIN' | 'OUTGOING_ADMIN';
    rawMessage: string;
    msgType: string;
    timestamp: number;
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
