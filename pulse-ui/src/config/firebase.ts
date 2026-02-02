import { getApp, getApps, initializeApp, FirebaseApp } from "firebase/app";
import { getAuth, connectAuthEmulator, Auth } from "firebase/auth";

const firebaseConfig = {
  apiKey: process.env.REACT_APP_FIREBASE_API_KEY,
  authDomain:
    process.env.REACT_APP_FIREBASE_AUTH_DOMAIN ||
    (process.env.REACT_APP_FIREBASE_PROJECT_ID
      ? `${process.env.REACT_APP_FIREBASE_PROJECT_ID}.firebaseapp.com`
      : undefined),
  projectId: process.env.REACT_APP_FIREBASE_PROJECT_ID,
  storageBucket: process.env.REACT_APP_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.REACT_APP_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.REACT_APP_FIREBASE_APP_ID,
};

let app: FirebaseApp;
let auth: Auth;

export function getFirebaseAuth(): Auth {
  if (!auth) {
    app = getApps().length ? getApp() : initializeApp(firebaseConfig);
    auth = getAuth(app);
    if (process.env.REACT_APP_FIREBASE_AUTH_EMULATOR === "true") {
      try {
        connectAuthEmulator(auth, "http://127.0.0.1:9099");
      } catch {}
    }
  }
  return auth;
}

export function isGcpMultiTenantEnabled(): boolean {
  return process.env.REACT_APP_GCP_MULTI_TENANT_ENABLED === "true";
}

export function getGcpTenantIds(): string[] {
  const list = process.env.REACT_APP_GCP_TENANTS;
  if (list && list.trim()) {
    return list.split(",").map((t) => t.trim()).filter(Boolean);
  }
  return [];
}

export function getDefaultGcpTenantId(): string | undefined {
  const ids = getGcpTenantIds();
  return ids[0];
}

const tenantLabelMap = ((): Record<string, string> => {
  const raw = process.env.REACT_APP_GCP_TENANT_LABELS;
  if (!raw || !raw.trim()) return {};
  const out: Record<string, string> = {};
  raw.split(",").forEach((part) => {
    const trimmed = part.trim();
    const colon = trimmed.indexOf(":");
    if (colon > 0) {
      const id = trimmed.slice(0, colon).trim();
      const label = trimmed.slice(colon + 1).trim();
      if (id) out[id] = label || id;
    }
  });
  return out;
})();

export function getGcpTenantDisplayName(tenantId: string): string {
  return tenantLabelMap[tenantId] ?? tenantId;
}

export function getGcpTenantOptions(): { value: string; label: string }[] {
  return getGcpTenantIds().map((id) => ({
    value: id,
    label: getGcpTenantDisplayName(id),
  }));
}
