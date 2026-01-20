export interface GetSpanDetailsParams {
  dataType: "TRACES" | "LOGS" | "EXCEPTIONS";
  traceId: string;
  spanId: string;
  timestamp: string;
  groupId?: string; // Used for exceptions
  enabled?: boolean;
}

export interface SpanDetailsResponse {
  resourceAttributes: Record<string, string>;
  spanAttributes: Record<string, string>;
  events: Array<{
    timestamp: string;
    name: string;
    attributes: Record<string, string>;
  }>;
  links: Array<{
    traceId: string;
    spanId: string;
    attributes: Record<string, string>;
  }>;
}

export interface LogDetailsResponse {
  resourceAttributes: Record<string, string>;
  logAttributes: Record<string, string>;
  scopeAttributes: Record<string, string>;
  body: string;
  severityText: string;
  severityNumber: number;
}

export interface ExceptionDetailsResponse {
  resourceAttributes: Record<string, string>;
  logAttributes: Record<string, string>;
  scopeAttributes: Record<string, string>;
  // Exception-specific fields
  exceptionStackTrace: string;
  exceptionStackTraceRaw: string;
  exceptionMessage: string;
  exceptionType: string;
  title: string;
  eventName: string;
  // Context
  screenName: string;
  interactions: string[];
  userId: string;
  // Device/app info
  platform: string;
  osVersion: string;
  deviceModel: string;
  appVersion: string;
  appVersionCode: string;
  sdkVersion: string;
  bundleId: string;
  // Grouping
  groupId: string;
  signature: string;
  fingerprint: string;
}

