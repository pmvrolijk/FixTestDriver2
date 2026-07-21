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
    // Client-assigned monotonic id, stamped on receipt. Used as a stable React key
    // so the log list reconciles cheaply even though messages are prepended.
    _id?: number;
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
    hasTrace: boolean;
    traceCount: number;
}

export interface LatencyTraceEventDto {
    receiveTime: string;
    receiveNanos: number;
    latencyNanos: number;
    ordStatus: string;
}

export interface LatencyTraceDto {
    id: number;
    msgType: string;
    idTag: number;
    idValue: string;
    sendTime: string;
    sendNanos: number;
    events: LatencyTraceEventDto[];
}

export interface FileTreeNode {
    name: string;
    path: string;
    directory: boolean;
    children?: FileTreeNode[];
}

export interface ProductTable {
    headers: string[];
    rows: string[][];
}

export interface SessionConfig {
    beginString: string;
    senderCompID: string;
    targetCompID: string;
    sessionQualifier?: string;
    connectionType: string;
    socketConnectHost?: string;
    socketConnectPort?: string;
    socketAcceptPort?: string;
    startTime?: string;
    endTime?: string;
    heartBtInt?: string;
    reconnectInterval?: string;
    logonTimeout?: string;
    logoutTimeout?: string;
    username?: string;
    password?: string;
    resetOnLogon?: string;
    resetOnLogout?: string;
    resetOnDisconnect?: string;
    useDataDictionary?: string;
    dataDictionary?: string;
    appDataDictionary?: string;
    fileStorePath?: string;
    fileLogPath?: string;
    refreshOnLogon?: string;
    checkCompID?: string;
    checkLatency?: string;
    validateFieldsHaveValues?: string;
    validateFieldsOutOfOrder?: string;
}
