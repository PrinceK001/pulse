import { useTenantContext } from '../contexts/TenantContext';
import { useProjectContext } from '../contexts/ProjectContext';

/**
 * Hook for checking user permissions based on their roles in tenant and project contexts.
 * 
 * Tenant-level permissions control organization-wide actions (invite members, create projects).
 * Project-level permissions control project-specific actions (edit settings, invite collaborators).
 */
export function usePermissions() {
  const { userRole: tenantRole } = useTenantContext();
  const { userRole: projectRole } = useProjectContext();
  
  return {
    // Tenant-level permissions (organization)
    canInviteTenantMembers: tenantRole === 'owner' || tenantRole === 'admin',
    canRemoveTenantMembers: tenantRole === 'owner' || tenantRole === 'admin',
    canUpdateTenantRoles: tenantRole === 'owner',
    canCreateProjects: tenantRole === 'owner' || tenantRole === 'admin',
    canManageOrgSettings: tenantRole === 'owner',
    canViewOrgMembers: !!tenantRole, // Any tenant member can view
    
    // Project-level permissions
    canInviteProjectMembers: projectRole === 'admin',
    canRemoveProjectMembers: projectRole === 'admin',
    canEditProject: projectRole === 'admin' || projectRole === 'editor',
    canDeleteProject: projectRole === 'admin',
    canViewProject: !!projectRole,
    canManageProjectSettings: projectRole === 'admin',
    
    // Role info (for display purposes)
    tenantRole,
    projectRole,
  };
}
