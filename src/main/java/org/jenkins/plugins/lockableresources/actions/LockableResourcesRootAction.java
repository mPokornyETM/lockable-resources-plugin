/* * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
 * Copyright (c) 2013, 6WIND S.A. All rights reserved.                 *
 *                                                                     *
 * This file is part of the Jenkins Lockable Resources Plugin and is   *
 * published under the MIT license.                                    *
 *                                                                     *
 * See the "LICENSE.txt" file for more information.                    *
 * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
package org.jenkins.plugins.lockableresources.actions;

import hudson.Extension;
import hudson.model.Api;
import hudson.model.RootAction;
import hudson.security.AccessDeniedException3;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourcesManager;
import org.jenkins.plugins.lockableresources.Messages;
import org.jenkins.plugins.lockableresources.util.ActionLogs;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.util.logging.Level;

@Extension
@ExportedBean
public class LockableResourcesRootAction implements RootAction {

  public static final PermissionGroup PERMISSIONS_GROUP =
    new PermissionGroup(
      LockableResourcesManager.class, Messages._LockableResourcesRootAction_PermissionGroup());
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

  public static final String ICON = "symbol-lock-closed";

  @Override
  public String getIconFileName() {
    return Jenkins.get().hasPermission(VIEW) ? ICON : null;
  }

  public Api getApi() {
    return new Api(this);
  }

  public String getUserName() {
    return LockableResource.getUserName();
  }

  @Override
  public String getDisplayName() {
    return Messages.LockableResourcesRootAction_PermissionGroup();
  }

  @Override
  public String getUrlName() {
    return Jenkins.get().hasPermission(VIEW) ? "lockable-resources" : "";
  }

  @Exported
  public List<LockableResource> getResources() {
    return LockableResourcesManager.get().getResources();
  }

  public LockableResource getResource(final String resourceName) {
    return LockableResourcesManager.get().fromName(resourceName);
  }

  /**
   * Get amount of free resources assigned to given *label*
   * @param label Label to search.
   * @return Amount of free labels.
   */
  public int getFreeResourceAmount(String label) {
    return LockableResourcesManager.get().getFreeResourceAmount(label);
  }

  /** 
   * Get percentage (0-100) usage of resources assigned to given *label*
   * 
   * Used by {@code actions/LockableResourcesRootAction/index.jelly}
   * @since 2.19
   * @param label Label to search.
   * @return Percentage usages of *label* around all resources
   */
  @Restricted(NoExternalUse.class)
  public int getFreeResourcePercentage(String label) {
    final int allCount = this.getAssignedResourceAmount(label);
    if (allCount == 0) {
      return allCount;
    }
    return (int)((double)this.getFreeResourceAmount(label) / (double)allCount * 100);
  }

  /**
   * Get all existing labels as list.
   * @return All possible labels.
   */
  public Set<String> getAllLabels() {
    return LockableResourcesManager.get().getAllLabels();
  }

  /**
   * Get amount of all labels.
   * @return Amount of all labels.
   */
  public int getNumberOfAllLabels() {
    return LockableResourcesManager.get().getAllLabels().size();
  }

  /**
   * Get amount of resources assigned to given *label*
   * 
   * Used by {@code actions/LockableResourcesRootAction/index.jelly}
   * @param label Label to search.
   * @return Amount of assigned resources.
   */
  @Restricted(NoExternalUse.class)
  public int getAssignedResourceAmount(String label) {
    return LockableResourcesManager.get().getResourcesWithLabel(label, null).size();
  }

  @Restricted(NoExternalUse.class)
  public List<ActionLogs.Entry> getAllLogs() {
    return LockableResourcesManager.get().getAllLogs();
  }

  @RequirePOST
  public void doUnlock(StaplerRequest req, StaplerResponse rsp)
    throws IOException, ServletException
  {
    Jenkins.get().checkPermission(UNLOCK);

    String name = req.getParameter("resource");
    
    ActionLogs.add(
      Level.INFO,
      "Try to unlock resource [" + name + "] by user [" + getUserName() + "]");
    LockableResource r = LockableResourcesManager.get().fromName(name);
    if (r == null) {
      sendRspError(rsp, 404, Messages.error_resourceDoesNotExist(name));
      return;
    }

    List<LockableResource> resources = new ArrayList<>();
    resources.add(r);
    LockableResourcesManager.get().unlock(resources, null);

    forwardToPreviousPage(req, rsp, "Resource [" + name + "] unlocked");
  }

  @RequirePOST
  public void doReserve(StaplerRequest req, StaplerResponse rsp)
    throws IOException, ServletException
  {
    Jenkins.get().checkPermission(RESERVE);

    String name = req.getParameter("resource");
    ActionLogs.add(
      Level.INFO,
      "Try to reserve resource [" + name + "] by user [" + getUserName() + "]");
    LockableResource r = LockableResourcesManager.get().fromName(name);
    if (r == null) {
      sendRspError(rsp, 404, Messages.error_resourceDoesNotExist(name));
      return;
    }

    List<LockableResource> resources = new ArrayList<>();
    resources.add(r);
    String userName = getUserName();
    if (userName != null) {
      if (!LockableResourcesManager.get().reserve(resources, userName)) {
        sendRspError(rsp, 423, Messages.error_resourceAlreadyLocked(name));
        return;
      }
    }
    forwardToPreviousPage(req, rsp, "Resource [" + name + "] reserved");
  }

  @RequirePOST
  public void doSteal(StaplerRequest req, StaplerResponse rsp)
    throws IOException, ServletException
  {
    Jenkins.get().checkPermission(STEAL);

    String name = req.getParameter("resource");
    ActionLogs.add(
      Level.INFO,
      "Try to steal resource [" + name + "] by user [" + getUserName() + "]");
    LockableResource r = LockableResourcesManager.get().fromName(name);
    if (r == null) {
      sendRspError(rsp, 404, Messages.error_resourceDoesNotExist(name));
      return;
    }

    List<LockableResource> resources = new ArrayList<>();
    resources.add(r);
    String userName = getUserName();

    if (userName == null) {
      // defensive: this can not happens because we check you permissions few lines before
      // therefore you must be logged in
      throw new AccessDeniedException3(Jenkins.getAuthentication2(), STEAL);
    }
    
    if (!LockableResourcesManager.get().steal(resources, userName)) {
      sendRspError(rsp, 423, "Resource [" + name + "] can not be stolen at the moment!");
      return;
    }

    forwardToPreviousPage(req, rsp, "Resource [" + name + "] stolen");
  }

  @RequirePOST
  public void doReassign(StaplerRequest req, StaplerResponse rsp)
    throws IOException, ServletException
  {
    Jenkins.get().checkPermission(STEAL);

    String userName = getUserName();
    if (userName == null) {
      // defensive: this can not happens because we check you permissions few lines before
      // therefore you must be logged in
      throw new AccessDeniedException3(Jenkins.getAuthentication2(), STEAL);
    }

    String name = req.getParameter("resource");
    ActionLogs.add(
      Level.INFO,
      "Try to reassign resource [" + name + "] by user [" + getUserName() + "]");
    LockableResource r = LockableResourcesManager.get().fromName(name);
    if (r == null) {
      sendRspError(rsp, 404, Messages.error_resourceDoesNotExist(name));
      return;
    }

    List<LockableResource> resources = new ArrayList<>();
    resources.add(r);
    
    if (!LockableResourcesManager.get().reassign(resources, userName)) {
      sendRspError(rsp, 423, "Resource [" + name + "] can not be reassign at the moment!");
      return;
    }

    forwardToPreviousPage(req, rsp, "Resource [" + name + "] reassigned");
  }

  @RequirePOST
  public void doUnreserve(StaplerRequest req, StaplerResponse rsp)
    throws IOException, ServletException
  {
    Jenkins.get().checkPermission(RESERVE);

    String name = req.getParameter("resource");
    ActionLogs.add(
      Level.INFO,
      "Try to un-reserve resource [" + name + "] by user [" + getUserName() + "]");
    LockableResource r = LockableResourcesManager.get().fromName(name);
    if (r == null) {
      sendRspError(rsp, 404, Messages.error_resourceDoesNotExist(name));
      return;
    }

    String userName = getUserName();
    if ((userName == null || !r.isReservedBy(userName))
        && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
    ) {
      throw new AccessDeniedException3(Jenkins.getAuthentication2(), RESERVE);
    }

    List<LockableResource> resources = new ArrayList<>();
    resources.add(r);
    
    if (!LockableResourcesManager.get().unreserve(resources)) {
      sendRspError(rsp, 423, "Resource [" + name + "] can not be un-reserve at the moment!");
      return;
    }

    forwardToPreviousPage(req, rsp, "Resource [" + name + "] un-reserved");
  }

  @RequirePOST
  public void doReset(StaplerRequest req, StaplerResponse rsp)
    throws IOException, ServletException
  {
    Jenkins.get().checkPermission(UNLOCK);
    // Should this also be permitted by "STEAL"?..

    String name = req.getParameter("resource");
    ActionLogs.add(
      Level.INFO,
      "Try to reset resource [" + name + "] by user [" + getUserName() + "]");
    LockableResource r = LockableResourcesManager.get().fromName(name);
    if (r == null) {
      sendRspError(rsp, 404, Messages.error_resourceDoesNotExist(name));
      return;
    }

    List<LockableResource> resources = new ArrayList<>();
    resources.add(r);
    
    LockableResourcesManager.get().reset(resources);

    forwardToPreviousPage(req, rsp, "Resource [" + name + "] reset");
  }

  @RequirePOST
  public void doSaveNote(final StaplerRequest req, final StaplerResponse rsp)
      throws IOException, ServletException {
    Jenkins.get().checkPermission(RESERVE);

    String name = req.getParameter("resource");
    if (name == null) {
      name = req.getParameter("resourceName");
    }

    ActionLogs.add(
      Level.INFO,
      "Try to save note on resource [" + name + "] by user [" + getUserName() + "]");

    final LockableResource resource = getResource(name);
    if (resource == null) {
      sendRspError(rsp, 404, Messages.error_resourceDoesNotExist(name));
      return;
    }

    String resourceNote = req.getParameter("note");
    if (resourceNote == null) {
      resourceNote = req.getParameter("resourceNote");
    }
    resource.setNote(resourceNote);
    LockableResourcesManager.get().save();

    forwardToPreviousPage(req, rsp, "Resource [" + name + "] note set");
  }

  private void sendRspError(final StaplerResponse rsp, int sc, final String message) throws IOException, ServletException {
    ActionLogs.add(
      Level.WARNING,
      "User [" + getUserName() + "] action fails.\n" + message);
    rsp.sendError(sc, message.replace('[', '\'').replace(']', '\''));
  }

  private void forwardToPreviousPage(final StaplerRequest req, final StaplerResponse rsp, final String message) throws IOException, ServletException {
    ActionLogs.add(
      Level.INFO,
      message + " by user [" + getUserName() + "]");
    rsp.forwardToPreviousPage(req);
  }
}
