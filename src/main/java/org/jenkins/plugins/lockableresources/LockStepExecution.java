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
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkins.plugins.lockableresources.queue.LockableResourcesStruct;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.support.actions.PauseAction;


import org.jenkins.plugins.lockableresources.util.BuildLogger;

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

    BuildLogger buildLogger = new BuildLogger(LOGGER, getContext());
    buildLogger.info("Trying to acquire lock on [" + step + "]");
    
    getContext().get(FlowNode.class).addAction(new PauseAction("Lock"));

    List<LockableResourcesStruct> resourceHolderList = new ArrayList<>();

    for (LockStepResource resource : step.getResources()) {
      List<String> resources = new ArrayList<>();
      if (resource.resource != null) {
        if (LockableResourcesManager.get().createResource(resource.resource)) {
          buildLogger.config("Resource [" + resource.resource + "] did not exist. Created.");
        }
        resources.add(resource.resource);
      }
      resourceHolderList.add(
        new LockableResourcesStruct(resources, resource.label, resource.quantity));
    }

    ResourceSelectStrategy resourceSelectStrategy;
    try {
      resourceSelectStrategy = ResourceSelectStrategy.valueOf(step.resourceSelectStrategy.toUpperCase(Locale.ENGLISH));
    } catch (IllegalArgumentException e) {
      buildLogger.severe("[" + step + "] Invalid resourceSelectStrategy: " + step.resourceSelectStrategy);
      return true;
    }
    // determine if there are enough resources available to proceed
    List<LockableResource> available =
      LockableResourcesManager.get()
        .checkResourcesAvailability(resourceHolderList, buildLogger, null, null, step.skipIfLocked, resourceSelectStrategy);
    
    boolean lockProceeded;
    if (available == null) {
      buildLogger.warning("Can not find available resources for [" + step + "]");
      lockProceeded = false;
    } else {
      Run<?, ?> run = getContext().get(Run.class);
      lockProceeded =  LockableResourcesManager.get()
                                               .lock(
                                                 available,
                                                 run,
                                                 getContext(),
                                                 step.toString(),
                                                 step.variable,
                                                 step.inversePrecedence
                                                 );
    }

    if (!lockProceeded) {
      // No available resources, or we failed to lock available resources
      // if the resource is known, we could output the active/blocking job/build
      if (step.skipIfLocked) {
        buildLogger.info("[" + step + "] skipping execution...");
      } else {
        buildLogger.info("[" + step + "] waiting...");
      }

      if (step.skipIfLocked) {
        getContext().onSuccess(null);
        return true;
      } else {
        LockableResourcesManager.get()
          .queueContext(getContext(), resourceHolderList, step.toString(), step.variable);
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
    BuildLogger buildLogger = new BuildLogger(LOGGER, context);
    try {
      r = context.get(Run.class);
      node = context.get(FlowNode.class);
      buildLogger.info("Lock acquired on [" + resourceDescription + "]");
    } catch (Exception e) {
      buildLogger.warning(e.toString());
      context.onFailure(e);
      return;
    }

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
      BuildLogger buildLogger = new BuildLogger(LOGGER, context);
      buildLogger.finest("Unlock resources: " + this.resourceNames);
      LockableResourcesManager.get()
        .unlockNames(this.resourceNames, context.get(Run.class), this.inversePrecedence);
      buildLogger.info("Lock released on resource [" + resourceDescription + "] at " + context.get(Run.class).getExternalizableId());
    }
  }

  @Override
  public void stop(@NonNull Throwable cause) {
    BuildLogger buildLogger = new BuildLogger(LOGGER, getContext());
    boolean cleaned = LockableResourcesManager.get().unqueueContext(getContext());
    if (!cleaned) {
      buildLogger.warning("Cannot remove context from lockable resource waiting list. " +
                          "The context is not in the waiting list.");
    }
    getContext().onFailure(cause);
  }
}
