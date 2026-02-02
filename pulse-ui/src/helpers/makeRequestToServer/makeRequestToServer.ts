import { API_METHODS, COOKIES_KEY } from "../../constants";
import { getCookies } from "../cookies";
import { MakeRequestConfig } from "../makeRequest";
import { getFirebaseIdToken, isGcpMultiTenantEnabled } from "../gcpAuth";

// Mock server import - only loaded when needed
let MockServer: any = null;

// Lazy load mock server to avoid bundling when not needed
const getMockServer = async () => {
  if (!MockServer && process.env.REACT_APP_USE_MOCK_SERVER === "true") {
    const mockModule = await import("../../mocks");
    MockServer = mockModule.MockServer;
  }
  return MockServer;
};

async function buildAuthHeaders(): Promise<Record<string, string>> {
  if (isGcpMultiTenantEnabled()) {
    const firebaseToken = await getFirebaseIdToken();
    if (firebaseToken) {
      const tenantId = getCookies(COOKIES_KEY.TENANT_ID);
      const base: Record<string, string> = {
        Authorization: `Bearer ${firebaseToken}`,
        "user-email": `${getCookies(COOKIES_KEY.USER_EMAIL)}`,
      };
      if (tenantId && tenantId !== "undefined") {
        base["tenant-id"] = tenantId;
      }
      return base;
    }
  }
  return {
    Authorization: `${getCookies(COOKIES_KEY.TOKEN_TYPE)} ${getCookies(COOKIES_KEY.ACCESS_TOKEN)}`,
    "user-email": `${getCookies(COOKIES_KEY.USER_EMAIL)}`,
  };
}

export const makeRequestToServer = async (
  requestConfig: MakeRequestConfig,
): Promise<Response> => {
  // Check if mock server is enabled
  if (process.env.REACT_APP_USE_MOCK_SERVER === "true") {
    try {
      const MockServerClass = await getMockServer();
      if (MockServerClass) {
        const mockServer = MockServerClass.getInstance();
        if (mockServer.isEnabled()) {
          return await mockServer.handleRequest(requestConfig);
        }
      }
    } catch (error) {
      console.warn(
        "[Mock Server] Failed to load mock server, falling back to real API:",
        error,
      );
    }
  }

  // Original implementation - real API call
  const { url, init } = requestConfig;
  const { headers, body, method, ...rest } = init ?? {};
  const authHeaders = await buildAuthHeaders();

  return await fetch(url, {
    method: method ?? API_METHODS.GET,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...authHeaders,
      ...(headers && { ...headers }),
    },
    ...(body && { body }),
    ...rest,
  });
};
