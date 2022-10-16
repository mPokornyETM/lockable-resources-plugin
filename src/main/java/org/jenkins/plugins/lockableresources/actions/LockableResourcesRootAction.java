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
import hudson.model.User;
import hudson.security.AccessDeniedException2;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import org.jenkins.plugins.lockableresources.LockableResource;
import org.jenkins.plugins.lockableresources.LockableResourcesManager;
import org.jenkins.plugins.lockableresources.Messages;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.kohsuke.stapler.interceptor.RequirePOST;

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

  public static final String ICON = "/plugin/lockable-resources/img/device.svg";

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

  public int getFreeResourceAmount(String label) {
    return LockableResourcesManager.get().getFreeResourceAmount(label);
  }

  public Set <String> getAllLabels() {
    return LockableResourcesManager.get().getAllLabels();
  }

  public int getNumberOfAllLabels() {
    return LockableResourcesManager.get().getAllLabels().size();
  }

  private List<LockableResource> getLockableResourcesFromRequest(Permission permission, StaplerRequest req, StaplerResponse rsp)
  throws IOException, ServletException {
    Jenkins.get().checkPermission(permission);

    List<String> tryToFind = new ArrayList <> ();

    String name = req.getParameter("resource");
    if (name != null) {
      tryToFind.add(name);
    } else {
      name = req.getParameter("resources");
      if (name != null) {
        tryToFind.addAll(Arrays.asList(name.split("\n")));
      }
    }

    if (tryToFind.size() <= 0) {
      rsp.sendError(404, "Invalid request, parameter 'resources' is missing");
      return null;
    }

    List<LockableResource> resources = new ArrayList <> ();

    for (String rName : tryToFind) {
      LockableResource r = LockableResourcesManager.get().fromName(rName);
      if (r == null) {
        rsp.sendError(404, "Resource not found " + rName);
        return null;
      }
      resources.add(r);
    }
    return resources;
  }

  @RequirePOST
  public void doUnlock(StaplerRequest req, StaplerResponse rsp)
  throws IOException, ServletException {
    List<LockableResource> resources = getLockableResourcesFromRequest(UNLOCK, req, rsp);

    if (resources == null) {
      return;
    }

    LockableResourcesManager.get().unlock(resources, null);

    rsp.forwardToPreviousPage(req);
  }

  @RequirePOST
  public void doReserve(StaplerRequest req, StaplerResponse rsp)
  throws IOException, ServletException {
    List<LockableResource> resources = getLockableResourcesFromRequest(RESERVE, req, rsp);

    if (resources == null) {
      return;
    }

    String userName = this.getUserName();
    if (userName != null) {
      if (!LockableResourcesManager.get().reserve(resources, userName)) {
        rsp.sendError(423, "Resources already reserved or locked!");
        return;
      }
    }

    rsp.forwardToPreviousPage(req);
  }

  @RequirePOST
  public void doSteal(StaplerRequest req, StaplerResponse rsp)
  throws IOException, ServletException {
    List<LockableResource> resources = getLockableResourcesFromRequest(STEAL, req, rsp);

    if (resources == null) {
      return;
    }

    String userName = this.getUserName();
    if (userName != null) {
      LockableResourcesManager.get().steal(resources, userName);
    }

    rsp.forwardToPreviousPage(req);;
  }

  @RequirePOST
  public void doReassign(StaplerRequest req, StaplerResponse rsp)
  throws IOException, ServletException {
    List<LockableResource> resources = getLockableResourcesFromRequest(STEAL, req, rsp);

    if (resources == null) {
      return;
    }

    String userName = this.getUserName();
    if (userName == null ||
      (!Jenkins.get().hasPermission(Jenkins.ADMINISTER) &&
        !Jenkins.get().hasPermission(STEAL))
    ) {
      throw new AccessDeniedException2(Jenkins.getAuthentication(), STEAL);
    }

    for (LockableResource r : resources) {
      if (userName.equals(r.getReservedBy())) {
        // Can not achieve much by re-assigning the
        // resource I already hold to myself again,
        // that would just burn the compute resources.
        // ...unless something catches the event? (TODO?)
        return;
      }
    }

    LockableResourcesManager.get().reassign(resources, userName);

    rsp.forwardToPreviousPage(req);;
  }

  @RequirePOST
  public void doUnreserve(StaplerRequest req, StaplerResponse rsp)
  throws IOException, ServletException {
    List<LockableResource> resources = getLockableResourcesFromRequest(RESERVE, req, rsp);

    if (resources == null) {
      return;
    }

    String userName = this.getUserName();

    for (LockableResource r : resources) {
      if ((userName == null || !userName.equals(r.getReservedBy())) &&
        !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
      ) {
        throw new AccessDeniedException2(Jenkins.getAuthentication(), RESERVE);
      }
    }

    LockableResourcesManager.get().unreserve(resources);

    rsp.forwardToPreviousPage(req);;
  }

  @RequirePOST
  public void doReset(StaplerRequest req, StaplerResponse rsp)
  throws IOException, ServletException {
    // Should this also be permitted by "STEAL"?..
    List<LockableResource> resources = getLockableResourcesFromRequest(UNLOCK, req, rsp);

    if (resources == null) {
      return;
    }

    LockableResourcesManager.get().reset(resources);

    rsp.forwardToPreviousPage(req);;
  }

  @RequirePOST
  public void doSaveNote(final StaplerRequest req, final StaplerResponse rsp)
  throws IOException, ServletException {
    List<LockableResource> resources = getLockableResourcesFromRequest(RESERVE, req, rsp);

    if (resources == null) {
      return;
    }

    String resourceNote = req.getParameter("note");
    if (resourceNote == null) {
      resourceNote = req.getParameter("resourceNote");
    }

    for (LockableResource resource : resources) {
      resource.setNote(resourceNote);
    }

    LockableResourcesManager.get().save();

    rsp.forwardToPreviousPage(req);;

  }
}