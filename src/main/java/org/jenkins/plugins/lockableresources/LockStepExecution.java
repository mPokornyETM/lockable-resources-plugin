package org.jenkins.plugins.lockableresources;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkins.plugins.lockableresources.queue.LockableResourcesStruct;
import org.jenkins.plugins.lockableresources.util.ActionLogs;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;

public class LockStepExecution extends AbstractStepExecutionImpl implements Serializable {

  private static final long serialVersionUID = 1391734561272059623L;

  private static final Logger LOGGER = Logger.getLogger(LockStepExecution.class.getName());

  private final LockStep step;

  public LockStepExecution(LockStep step, StepContext context) {
    super(context);
    this.step = step;
  }

  @Override
  public boolean start() throws Exception {
    step.validate();

    final String buildId = getContext().get(Run.class).getExternalizableId();

    getContext().get(FlowNode.class).addAction(new PauseAction("Lock"));
    PrintStream logger = getContext().get(TaskListener.class).getLogger();
    ActionLogs.add(
      logger,
      Level.INFO,
      Messages.log_tryLock(buildId, step));

    List<LockableResourcesStruct> resourceHolderList = new ArrayList<>();

    for (LockStepResource resource : step.getResources()) {
      List<String> resources = new ArrayList<>();
      if (resource.resource != null) {
        if (LockableResourcesManager.get().createResource(resource.resource)) {
          // ActionLogs.add(
          //   logger,
          //   Level.INFO,
          //   "Resource [" + resource + "] required for build [" + buildId + "] did not exist. Created.",
          //   resource.resource);
        }
        resources.add(resource.resource);
      }
      resourceHolderList.add(
        new LockableResourcesStruct(resources, resource.label, resource.quantity));
    }

    Run<?, ?> run = getContext().get(Run.class);
    // determine if there are enough resources available to proceed
    List<LockableResource> available =
      LockableResourcesManager.get()
        .checkResourcesAvailability2(resourceHolderList, logger, null, step.skipIfLocked, run);
    
    if (available == null
      || !LockableResourcesManager.get()
      .lock(
        available,
        run,
        getContext(),
        step.toString(),
        step.variable,
        step.inversePrecedence)) {
      // if the resource is known, we could output the active/blocking job/build
      LockableResource resource = LockableResourcesManager.get().fromName(step.resource);
      if (resource != null) {
        if (step.skipIfLocked) {
          ActionLogs.add(
            logger,
            Level.WARNING,
            "Skipping execution in the build [" + run.getExternalizableId() + "].\n" + 
            resource.getLockCauseFormattedForLogs());
          getContext().onSuccess(null);
          return true;
        } else {
          ActionLogs.add(
            logger,
            Level.WARNING,
            "The build [" + run.getExternalizableId() + "] must wait now.\n" +
            resource.getLockCauseFormattedForLogs());
          LockableResourcesManager.get()
            .queueContext(getContext(), resourceHolderList, step.toString(), step.variable);
        }
      }
    } // proceed is called inside lock if execution is possible
    return false;
  }

  @SuppressFBWarnings(value = "REC_CATCH_EXCEPTION", justification = "not sure which exceptions might be catch.")
  public static void proceed(
    final List<String> resourceNames,
    StepContext context,
    String resourceDescription,
    final String variable,
    boolean inversePrecedence) {
    Run<?, ?> r;
    FlowNode node;
    String buildId;

    try {
      buildId = context.get(Run.class).getExternalizableId();
      r = context.get(Run.class);
      node = context.get(FlowNode.class);
      for (int index = 0; index < resourceNames.size(); ++index) {
        ActionLogs.add(
          context.get(TaskListener.class).getLogger(),
          Level.INFO,
          Messages.log_acquired(resourceNames.get(index), buildId));
      }     
    } catch (Exception e) {
      context.onFailure(e);
      return;
    }

    LOGGER.finest("Lock acquired on resource [" + resourceDescription + "] by build [" + buildId + "]");
    try {
      PauseAction.endCurrentPause(node);
      BodyInvoker bodyInvoker =
        context
          .newBodyInvoker()
          .withCallback(new Callback(resourceNames, resourceDescription, inversePrecedence));
      if (variable != null && variable.length() > 0) {
        // set the variable for the duration of the block
        bodyInvoker.withContext(
          EnvironmentExpander.merge(
            context.get(EnvironmentExpander.class),
            new EnvironmentExpander() {
              private static final long serialVersionUID = -3431466225193397896L;

              @Override
              public void expand(@NonNull EnvVars env) {
                final Map<String, String> variables = new HashMap<>();
                final String resources = String.join(",", resourceNames);
                variables.put(variable, resources);
                for (int index = 0; index < resourceNames.size(); ++index) {
                  variables.put(variable + index, resourceNames.get(index));
                }
                LOGGER.finest("Setting "
                  + variables.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "))
                  + " for the duration of the block");
                env.overrideAll(variables);
              }
            }));
      }
      bodyInvoker.start();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class Callback extends BodyExecutionCallback.TailCall {

    private static final long serialVersionUID = -2024890670461847666L;
    private final List<String> resourceNames;
    private final String resourceDescription;
    private final boolean inversePrecedence;

    Callback(
      List<String> resourceNames,
      String resourceDescription,
      boolean inversePrecedence) {
      this.resourceNames = resourceNames;
      this.resourceDescription = resourceDescription;
      this.inversePrecedence = inversePrecedence;
    }

    @Override
    protected void finished(StepContext context) throws Exception {
      LockableResourcesManager.get()
        .unlockNames(this.resourceNames, context.get(Run.class), this.inversePrecedence);
      
      final String buildId = context.get(Run.class).getExternalizableId();
      for (int index = 0; index < resourceNames.size(); ++index) {
        ActionLogs.add(
          context.get(TaskListener.class).getLogger(),
          Level.INFO,
          Messages.log_released(resourceDescription, buildId));
      }
      LOGGER.finest("Lock released on resource [" + resourceDescription + "] used by build [" + buildId + "]");
    }
  }

  @Override
  public void stop(@NonNull Throwable cause) {
    boolean cleaned = LockableResourcesManager.get().unqueueContext(getContext());
    if (!cleaned) {
      LOGGER.log(
        Level.WARNING,
        "Cannot remove context from lockable resource waiting list. "
          + "The context is not in the waiting list.");
    }
    getContext().onFailure(cause);
  }
}
