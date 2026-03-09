import React, { createContext, useContext, useState, useCallback, ReactNode, useEffect } from 'react';
import { ProjectSummary } from '../helpers/getUserProjects/getUserProjects.interface';
import { useUserProjects } from '../hooks';
import { TenantRole } from '../constants/Roles';
import { TIERS, TierType } from '../constants/Tiers';
import { TenantInfo, TenantContextType, StoredTenantData } from './TenantContext.interface';

const TenantContext = createContext<TenantContextType | undefined>(undefined);

const STORAGE_KEY = 'pulse_tenant_context';

export function TenantProvider({ children }: { children: ReactNode }) {
  const [tenantId, setTenantId] = useState<string | null>(null);
  const [tenantName, setTenantName] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<TenantRole | null>(null);
  const [tier, setTier] = useState<TierType | null>(null);
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [shouldFetchProjects, setShouldFetchProjects] = useState(false);

  // Use React Query hook for fetching projects
  const { data: projectsData, isLoading, refetch } = useUserProjects(shouldFetchProjects);

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
          
          // Enable project fetching and refetch in background for fresh data
          setShouldFetchProjects(true);
          setTimeout(() => {
            refetch();
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

  // Update projects when React Query data changes
  useEffect(() => {
    if (projectsData?.data) {
      setProjects(projectsData.data.projects);
      
      // Update tenant information from API response
      if (projectsData.data.tenantName) {
        setTenantName(projectsData.data.tenantName);
      }
      if (projectsData.data.tenantId) {
        setTenantId(projectsData.data.tenantId);
      }
    }
  }, [projectsData]);

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

    // Enable fetching and trigger refetch
    setShouldFetchProjects(true);
    await refetch();
  }, [tenantId, refetch]);

  const setTenantInfo = useCallback((tenant: TenantInfo) => {
    setTenantId(tenant.tenantId);
    setTenantName(tenant.tenantName);
    setUserRole(tenant.userRole);
    setTier(tenant.tier);
    
    // Auto-fetch projects when tenant is set
    if (tenant.tenantId) {
      setShouldFetchProjects(true);
      setTimeout(() => refetch(), 0);
    }
  }, [refetch]);

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
