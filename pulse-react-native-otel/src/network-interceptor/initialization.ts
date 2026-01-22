import createXmlHttpRequestTracker from './request-tracker-xhr';
import type { NetworkHeaderConfig } from '../config';

let isInitialized = false;
let headerConfig: NetworkHeaderConfig = {
  requestHeaders: [],
  responseHeaders: [],
};

/**
 * Normalizes header name according to OpenTelemetry HTTP semantic conventions:
 * - Lowercase
 * - Dashes replaced by underscores
 *
 * Reference: https://opentelemetry.io/docs/specs/semconv/registry/attributes/http/
 *
 * @example
 * normalizeHeaderName('Content-Type') => 'content_type'
 * normalizeHeaderName('X-Request-ID') => 'x_request_id'
 */
export function normalizeHeaderName(headerName: string): string {
  return headerName.toLowerCase().replace(/-/g, '_');
}

/**
 * Checks if a header should be captured based on configuration.
 */
export function shouldCaptureHeader(
  headerName: string,
  headerList: string[]
): boolean {
  if (headerList.length === 0) return false;
  // Case-insensitive comparison
  return headerList.some(
    (configHeader) => configHeader.toLowerCase() === headerName.toLowerCase()
  );
}

export function getHeaderConfig(): NetworkHeaderConfig {
  return headerConfig;
}

export function initializeNetworkInterceptor(
  config?: NetworkHeaderConfig
): void {
  if (isInitialized) {
    console.warn('[Pulse] Network interceptor already initialized');
    return;
  }

  // Store header configuration
  if (config) {
    headerConfig = {
      requestHeaders: config.requestHeaders ?? [],
      responseHeaders: config.responseHeaders ?? [],
    };
  }

  console.log('[Pulse] 🔄 Starting network interceptor initialization...');

  try {
    // In react-native, we are intercepting XMLHttpRequest only, since axios and fetch both use it internally.
    // See: https://github.com/facebook/react-native/blob/main/packages/react-native/Libraries/Network/fetch.js
    if (typeof XMLHttpRequest !== 'undefined') {
      createXmlHttpRequestTracker(XMLHttpRequest);
    } else {
      console.warn('[Pulse] XMLHttpRequest is not available');
    }

    isInitialized = true;
  } catch (error) {
    console.error('[Pulse] Failed to initialize network interceptor:', error);
  }
}

export const isNetworkInterceptorInitialized = (): boolean => isInitialized;
