import React, { createContext, useContext, useState, useCallback, ReactNode, useEffect } from 'react';
import { getUserProjects, ProjectSummary } from '../helpers/getUserProjects';

interface TenantInfo {
  tenantId: string;
  tenantName: string;
  userRole: 'owner' | 'admin' | 'member';
}

interface TenantContextType {
  // State
  tenantId: string | null;
  tenantName: string | null;
  userRole: 'owner' | 'admin' | 'member' | null;
  projects: ProjectSummary[];
  isLoading: boolean;
  
  // Methods
  setTenantInfo: (tenant: TenantInfo) => void;
  refreshProjects: () => Promise<void>;
  addProject: (project: ProjectSummary) => void;
  clearTenant: () => void;
}

const TenantContext = createContext<TenantContextType | undefined>(undefined);

const STORAGE_KEY = 'pulse_tenant_context';

interface StoredTenantData {
  tenantId: string;
  tenantName: string;
  userRole: 'owner' | 'admin' | 'member';
  projects: ProjectSummary[];
  timestamp: number;
}

export function TenantProvider({ children }: { children: ReactNode }) {
  const [tenantId, setTenantId] = useState<string | null>(null);
  const [tenantName, setTenantName] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<'owner' | 'admin' | 'member' | null>(null);
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  // Hydrate from sessionStorage on mount
  useEffect(() => {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    if (stored) {
      try {
        const data: StoredTenantData = JSON.parse(stored);
        // Check if data is less than 1 hour old
        const ONE_HOUR = 60 * 60 * 1000;
        if (Date.now() - data.timestamp < ONE_HOUR) {
          setTenantId(data.tenantId);
          setTenantName(data.tenantName);
          setUserRole(data.userRole);
          setProjects(data.projects);
          console.log('[TenantContext] Hydrated from sessionStorage:', data.tenantId);
          
          // Always refetch in background for fresh data
          setTimeout(() => {
            console.log('[TenantContext] Background refresh for fresh data');
            refreshProjects();
          }, 100);
        } else {
          sessionStorage.removeItem(STORAGE_KEY);
        }
      } catch (error) {
        console.error('[TenantContext] Failed to parse stored data:', error);
        sessionStorage.removeItem(STORAGE_KEY);
      }
    }
  }, []);

  // Persist to sessionStorage whenever state changes
  useEffect(() => {
    if (tenantId && userRole) {
      const data: StoredTenantData = {
        tenantId,
        tenantName: tenantName || '',
        userRole,
        projects,
        timestamp: Date.now(),
      };
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    }
  }, [tenantId, tenantName, userRole, projects]);

  const setTenantInfo = useCallback((tenant: TenantInfo) => {
    console.log('[TenantContext] Setting tenant info:', tenant);
    setTenantId(tenant.tenantId);
    setTenantName(tenant.tenantName);
    setUserRole(tenant.userRole);
    
    // Auto-fetch projects when tenant is set
    if (tenant.tenantId) {
      refreshProjects();
    }
  }, []);

  const refreshProjects = useCallback(async () => {
    // Get tenantId from state or sessionStorage
    const currentTenantId = tenantId || (() => {
      const stored = sessionStorage.getItem(STORAGE_KEY);
      if (stored) {
        try {
          const data: StoredTenantData = JSON.parse(stored);
          return data.tenantId;
        } catch {
          return null;
        }
      }
      return null;
    })();

    if (!currentTenantId) {
      console.log('[TenantContext] No tenant ID available, skipping project refresh');
      return;
    }

    setIsLoading(true);
    console.log('[TenantContext] Fetching projects for tenant:', currentTenantId);
    
    try {
      const response = await getUserProjects();
      if (response.data) {
        console.log('[TenantContext] Fetched projects:', response.data.projects.length);
        setProjects(response.data.projects);
      } else {
        console.error('[TenantContext] Failed to fetch projects:', response.error);
      }
    } catch (error) {
      console.error('[TenantContext] Error fetching projects:', error);
    } finally {
      setIsLoading(false);
    }
  }, [tenantId]);

  const addProject = useCallback((project: ProjectSummary) => {
    console.log('[TenantContext] Adding project:', project.projectId);
    setProjects(prev => {
      // Check if project already exists
      const exists = prev.some(p => p.projectId === project.projectId);
      if (exists) {
        return prev.map(p => p.projectId === project.projectId ? project : p);
      }
      return [...prev, project];
    });
  }, []);

  const clearTenant = useCallback(() => {
    console.log('[TenantContext] Clearing tenant context');
    setTenantId(null);
    setTenantName(null);
    setUserRole(null);
    setProjects([]);
    sessionStorage.removeItem(STORAGE_KEY);
  }, []);

  const value: TenantContextType = {
    tenantId,
    tenantName,
    userRole,
    projects,
    isLoading,
    setTenantInfo,
    refreshProjects,
    addProject,
    clearTenant,
  };

  return (
    <TenantContext.Provider value={value}>
      {children}
    </TenantContext.Provider>
  );
}

export function useTenantContext(): TenantContextType {
  const context = useContext(TenantContext);
  if (context === undefined) {
    throw new Error('useTenantContext must be used within a TenantProvider');
  }
  return context;
}
