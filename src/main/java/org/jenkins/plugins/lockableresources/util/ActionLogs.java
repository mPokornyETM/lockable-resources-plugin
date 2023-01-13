package org.jenkins.plugins.lockableresources.util;

import static java.text.DateFormat.MEDIUM;
import static java.text.DateFormat.SHORT;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ctc.wstx.shaded.msv_core.util.Util;

import java.util.logging.LogRecord;

import hudson.model.Run;
import hudson.model.User;
import jenkins.model.Jenkins;


/*import jenkins.model.Jenkins;
import hudson.model.User;
import org.jenkins.plugins.lockableresources.LockableResource;*/

public class ActionLogs {
  
  // private static List<Entry> entries = new ArrayList<>();

  public static void add(Level prio, String message) {
    add(null, prio, message);
  }
  public static void add(PrintStream logger, Level prio, String message) {
    Entry entry = new Entry(prio, message);
    entry.print(logger);
  }

  private static final Logger LOGGER = Logger.getLogger("lockable-resource-action-history");
  public static List<Entry> getEntries() {

    List<Entry> entries = new ArrayList<>();

    for(LogRecord logRecord : Jenkins.logRecords) {
      if (logRecord == null) {
        continue;
      }

      if (logRecord.getLoggerName().equals(LOGGER.getName()) || logRecord.getLoggerName().contains("org.jenkins.plugins.lockableresources")) {
        Entry entry = new Entry(logRecord);
        entries.add(entry);
      }
    }
    return Collections.unmodifiableList(entries);
  }

  public static class Entry {
    private static final Logger LOGGER = Logger.getLogger("lockable-resource-action-history");
    Level prio;
    String message;
    Date timestamp;
    String resourceId;
    String requester;

    
    Entry(LogRecord logRecord) {
      this.prio = logRecord.getLevel();
      this.timestamp = new Date(logRecord.getMillis());

      this.message = logRecord.getMessage();
      if (this.message == null) {
        this.message = "";
      }

      this.resourceId = getTokenFromMessage("resource");
      if (this.resourceId.isEmpty()) {
        this.resourceId = getTokenFromMessage("resources");
      }

      String token = getTokenFromMessage("build");
      if (!token.isEmpty()) {
        this.requester = "B:" + token;
      } else {
        token = getTokenFromMessage("user");
        if (!token.isEmpty()) {
          this.requester = "U:" + token;
        }
      }
    }

    Entry(Level prio, String message) {
      this.prio = prio;
      this.message = message;
      this.resourceId = "";
      this.requester = "";
      this.timestamp = new Date();

      if (this.message != null) {
        this.resourceId = getTokenFromMessage("resource");
        if (this.resourceId.isEmpty()) {
          this.resourceId = getTokenFromMessage("resources");
        }

        String token = getTokenFromMessage("build");
        if (!token.isEmpty()) {
          this.requester = "B:" + token;
        } else {
          token = getTokenFromMessage("user");
          if (!token.isEmpty()) {
            this.requester = "U:" + token;
          }
        }
      }
    }

    public Level getPriority() {
      return this.prio;
    }

    public String getRequester() {
      if (this.requester == null) {
        return ""; // defensive
      }
      else if (this.requester.startsWith("U:")) {
        final String userId = this.requester.substring(2);
        User user = Jenkins.get().getUser(userId);
        return "User " + makeModelUrl(user.getUrl(), user.getDisplayName());
      }
      else if (this.requester.startsWith("B:")) {
        final String buildExternalizableId = this.requester.substring(2);
        Run<?, ?> build = Run.fromExternalizableId(buildExternalizableId);
        if (build == null) {
          return buildExternalizableId;
        }
        return "Build " + makeModelUrl(build.getUrl(), build.getFullDisplayName());
      }
      else {
        return this.requester;
      }
    }

    private static String makeModelUrl(final String relUrl, final String showCase) {
      return "<a class=\"jenkins-table__link model-link jenkins-table__badge\" href=\"" + Jenkins.get().getRootUrl() + relUrl+ "\">" + showCase + "</a>";
    }

    public String getMessage() {
      return this.message.replace('[', '\'').replace(']', '\'').replace("\n", "<br/>");
    }

    public String getResourceID() {
      return this.resourceId;
    }

    public Date getDate() {
      return this.timestamp;
    }

    public void print(PrintStream logger) {
      LOGGER.log(prio, message);
      if (logger != null)
        logger.println(message);
    }

    private String getTokenFromMessage(final String tokenIdentifier) {
      
      int startDef = this.message.toLowerCase().indexOf(tokenIdentifier + " [");
      if (startDef < 0) {
        return "";
      }
      startDef += tokenIdentifier.length() + 2; // + 2 because a space and [
      final int endDef = this.message.indexOf("]", startDef);
      if (endDef < 0) {
        return "";
      } 
      return this.message.substring(startDef, endDef).trim();
    }
  } 
}
