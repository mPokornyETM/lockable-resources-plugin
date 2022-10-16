/*
 * TBD
 */
package org.jenkins.plugins.lockableresources;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import hudson.util.FormApply;
import java.io.IOException;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkins.plugins.lockableresources.actions.LockableResourcesRootAction;
import org.jenkins.plugins.lockableresources.LockableResourcesManager;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.POST;

/**
 * {@link ManagementLink} of the plugin to add a link in the "/manage" page, for the agents next to the one for the master.
 * @author Martin Pokorny
 */
@Extension(ordinal = Integer.MAX_VALUE - 491)
public class LockableResourcesManagementLink extends ManagementLink {
	/**
	 * Mostly works like {@link hudson.model.Action#getIconFileName()}, except
	 * that the expected icon size is 48x48, not 24x24. So if you give just a
	 * file name, "/images/48x48" will be assumed.
	 * @return As a special case, return null to exclude this object from the
	 *         management link. This is useful for defining
	 *         {@link ManagementLink} that only shows up under certain
	 *         circumstances.
	 */
	@Override
	public String getIconFileName() {
		return LockableResourcesRootAction.ICON;
	}

	/**
	 * Returns a short description of what this link does. This text is the one
	 * that's displayed in grey. This can include HTML, although the use of
	 * block tags is highly discouraged.
	 * Optional.
	 */
	@Override
	public String getDescription() {
		return "Locked Resources management.";
	}

	/**
	 * Gets the string to be displayed.
	 * The convention is to capitalize the first letter of each word, such as
	 * "Test Result".
	 */
	@Override
	public String getDisplayName() {
		return "Locked Resources";
	}

	/** {@inheritDoc} */
	@Override
	public Permission getRequiredPermission() {
		//This link is displayed to any user with permission to access the management menu
		return Jenkins.READ;
	}

	/** {@inheritDoc} */
	@Override
	public String getUrlName() {
		return "lockable-resources";
	}

    @NonNull
    @Override
    public Category getCategory() {
        return Category.CONFIGURATION;
    }

    @POST
    public synchronized void doConfigure(StaplerRequest req, StaplerResponse rsp) throws Descriptor.FormException, IOException, ServletException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        JSONObject json = req.getSubmittedForm();
     //   Jenkins.get().clouds.rebuildHetero(req, json, LockableResourcesManager.get().getResources(), "lockable-resources");
        FormApply.success(req.getContextPath() + "/config").generateResponse(req, rsp, null);
    }
}
