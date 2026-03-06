import React, { createContext, useContext, useState, useCallback, ReactNode, useEffect } from 'react';
import { getUserProjects, ProjectSummary } from '../helpers/getUserProjects';
import { TenantRole } from '../constants/Roles';
import { TIERS, TierType } from '../constants/Tiers';

interface TenantInfo {
  tenantId: string;
  tenantName: string;
  userRole: TenantRole;
  tier: TierType;
}

interface TenantContextType {
  // State
  tenantId: string | null;
  tenantName: string | null;
  userRole: TenantRole | null;
  tier: TierType | null;
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
  userRole: TenantRole;
  tier: TierType;
  projects: ProjectSummary[];
  timestamp: number;
}

export function TenantProvider({ children }: { children: ReactNode }) {
  const [tenantId, setTenantId] = useState<string | null>(null);
  const [tenantName, setTenantName] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<TenantRole | null>(null);
  const [tier, setTier] = useState<TierType | null>(null);
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
          setTier(data.tier);
          setProjects(data.projects);
          
          // Always refetch in background for fresh data
          setTimeout(() => {
            refreshProjects();
          }, 100);
        } else {
          sessionStorage.removeItem(STORAGE_KEY);
        }
      } catch (error) {
        sessionStorage.removeItem(STORAGE_KEY);
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Persist to sessionStorage whenever state changes
  useEffect(() => {
    if (tenantId && userRole) {
      const data: StoredTenantData = {
        tenantId,
        tenantName: tenantName || '',
        userRole,
        tier: tier || TIERS.FREE,
        projects,
        timestamp: Date.now(),
      };
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    }
  }, [tenantId, tenantName, userRole, tier, projects]);

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
      return;
    }

    setIsLoading(true);
    
    try {
      const response = await getUserProjects();
      if (response.data) {
        setProjects(response.data.projects);
        
        // Update tenant information from API response
        if (response.data.tenantName) {
          setTenantName(response.data.tenantName);
        }
        if (response.data.tenantId) {
          setTenantId(response.data.tenantId);
        }
      } else {
      }
    } catch (error) {
      console.error('[TenantContext] Error fetching projects:', error);
    } finally {
      setIsLoading(false);
    }
  }, [tenantId]);

  const setTenantInfo = useCallback((tenant: TenantInfo) => {
    setTenantId(tenant.tenantId);
    setTenantName(tenant.tenantName);
    setUserRole(tenant.userRole);
    setTier(tenant.tier);
    
    // Auto-fetch projects when tenant is set
    if (tenant.tenantId) {
      refreshProjects();
    }
  }, [refreshProjects]);

  const addProject = useCallback((project: ProjectSummary) => {
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
    setTenantId(null);
    setTenantName(null);
    setUserRole(null);
    setTier(null);
    setProjects([]);
    sessionStorage.removeItem(STORAGE_KEY);
  }, []);

  const value: TenantContextType = {
    tenantId,
    tenantName,
    userRole,
    tier,
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
