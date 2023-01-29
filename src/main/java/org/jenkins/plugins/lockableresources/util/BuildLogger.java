

package org.jenkins.plugins.lockableresources.util;


import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import java.io.PrintStream;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.model.TaskListener;
import hudson.model.Run;


public class BuildLogger {

  public static final String LOG_PREFIX = "[lockable-resources]";

  StepContext context = null;
  Logger logger = null;

  public BuildLogger(@NonNull Logger logger, @Nullable StepContext context) {
    this.logger = logger;
    this.context = context;
  }

  @Restricted(NoExternalUse.class)
  public void info(@NonNull String message) {
    this.log(Level.INFO, message);
  }

  @Restricted(NoExternalUse.class)
  public void warning(@NonNull String message) {
    this.log(Level.WARNING, message);
  }

  @Restricted(NoExternalUse.class)
  public void severe(@NonNull String message) {
    this.log(Level.SEVERE, message);
  }

  @Restricted(NoExternalUse.class)
  public void config(@NonNull String message) {
    this.log(Level.CONFIG, message);
  }

  @Restricted(NoExternalUse.class)
  public void fine(@NonNull String message) {
    this.log(Level.FINE, message);
  }

  @Restricted(NoExternalUse.class)
  public void finest(@NonNull String message) {
    this.log(Level.FINEST, message);
  }

//   @Restricted(NoExternalUse.class)
  private PrintStream getBuildLogger() {
    try {
      return this.context != null ? this.context.get(TaskListener.class).getLogger() : null;
    } catch(java.io.IOException | java.lang.InterruptedException error) {
      this.logger.warning(error.toString());
      return null;
    }
  }

  @Restricted(NoExternalUse.class)
  public void log(@NonNull Level level, @NonNull String message) {

    PrintStream buildLogger = this.getBuildLogger();
    if (buildLogger != null) {
      buildLogger.println(LOG_PREFIX + " " + level.getName() + ": " + message);
    }

    if (this.context != null) {
      try {
        Run<?, ?> run = context.get(Run.class);
        if (run != null) {
          // todo try hudson.console.ModelHyperlinkNote.encodeTo(url, text);
          // to make direct link in the log
          message = "Build " + run.getExternalizableId() + "\n" + message;
        }
      } catch(java.io.IOException | java.lang.InterruptedException error) {
        this.logger.warning(error.toString());
      }
    }
    this.logger.log(level, message);
  }
}