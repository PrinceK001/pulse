import {
  signInWithPopup,
  GoogleAuthProvider,
  User,
  Auth,
} from "firebase/auth";
import {
  getFirebaseAuth,
  isGcpMultiTenantEnabled,
  getGcpTenantIds,
  getDefaultGcpTenantId,
  getGcpTenantDisplayName,
  getGcpTenantOptions,
} from "../../config/firebase";

export type GcpAuthResult = {
  idToken: string;
  user: User;
  email: string;
  tenantId: string | undefined;
};

export async function signInWithGoogleGcp(
  tenantId?: string,
): Promise<GcpAuthResult> {
  const auth = getFirebaseAuth() as Auth & { tenantId?: string | null };

  if (tenantId) {
    auth.tenantId = tenantId;
  }

  const provider = new GoogleAuthProvider();
  const result = await signInWithPopup(auth, provider);
  const idToken = await result.user.getIdToken();
  const email = result.user.email ?? "";

  if (tenantId) {
    auth.tenantId = null;
  }

  const effectiveTenantId = tenantId ?? undefined;

  return {
    idToken,
    user: result.user,
    email,
    tenantId: effectiveTenantId,
  };
}

export async function getFirebaseIdToken(): Promise<string | null> {
  if (!isGcpMultiTenantEnabled()) return null;
  const auth = getFirebaseAuth();
  const user = auth.currentUser;
  if (!user) return null;
  return user.getIdToken();
}

export async function signOutFirebase(): Promise<void> {
  if (!isGcpMultiTenantEnabled()) return;
  const auth = getFirebaseAuth();
  await auth.signOut();
}

export {
  isGcpMultiTenantEnabled,
  getGcpTenantIds,
  getDefaultGcpTenantId,
  getGcpTenantDisplayName,
  getGcpTenantOptions,
};
