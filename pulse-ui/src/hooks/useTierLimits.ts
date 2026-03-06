import { useTenantContext } from '../contexts';
import { TIERS, TierType } from '../constants/Tiers';

export interface TierLimits {
  canCreateProjects: boolean;
  maxProjects: number | null; // null = unlimited
  currentProjectCount: number;
  isEnterprise: boolean;
  isFree: boolean;
  tier: TierType | null;
}

export function useTierLimits(): TierLimits {
  const { tier, projects } = useTenantContext();
  const currentProjectCount = projects.length;
  
  return {
    canCreateProjects: tier === TIERS.ENTERPRISE || currentProjectCount < 1,
    maxProjects: tier === TIERS.FREE ? 1 : null,
    currentProjectCount,
    isEnterprise: tier === TIERS.ENTERPRISE,
    isFree: tier === TIERS.FREE,
    tier,
  };
}
