
package org.jenkins.plugins.lockableresources;

import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;

public class Permissions{

  public static final PermissionGroup PERMISSIONS_GROUP =
    new PermissionGroup(
      Permissions.class, Messages._LockableResourcesRootAction_PermissionGroup());
  public static final Permission UNLOCK =
    new Permission(
      PERMISSIONS_GROUP,
      Messages.LockableResourcesRootAction_UnlockPermission(),
      Messages._LockableResourcesRootAction_UnlockPermission_Description(),
      Jenkins.ADMINISTER,
      PermissionScope.JENKINS);
  public static final Permission RESERVE =
    new Permission(
      PERMISSIONS_GROUP,
      Messages.LockableResourcesRootAction_ReservePermission(),
      Messages._LockableResourcesRootAction_ReservePermission_Description(),
      Jenkins.ADMINISTER,
      PermissionScope.JENKINS);
  public static final Permission STEAL =
    new Permission(
      PERMISSIONS_GROUP,
      Messages.LockableResourcesRootAction_StealPermission(),
      Messages._LockableResourcesRootAction_StealPermission_Description(),
      Jenkins.ADMINISTER,
      PermissionScope.JENKINS);
  public static final Permission VIEW =
    new Permission(
      PERMISSIONS_GROUP,
      Messages.LockableResourcesRootAction_ViewPermission(),
      Messages._LockableResourcesRootAction_ViewPermission_Description(),
      Jenkins.ADMINISTER,
      PermissionScope.JENKINS);

  public static boolean hasCurrentUserPermission(Permission permission) {
    return Jenkins.get().hasPermission(permission) || Jenkins.get().hasPermission(Jenkins.ADMINISTER);
  }
}