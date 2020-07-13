/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.idea;

import com.intellij.ide.Bootstrap;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Restarter;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.Locale;


@SuppressWarnings({"UseOfSystemOutOrSystemErr", "MethodNamesDifferingOnlyByCase"})
public class Main {
  public static final int UPDATE_FAILED = 1;
  public static final int STARTUP_EXCEPTION = 2;
  public static final int STARTUP_IMPOSSIBLE = 3;
  public static final int LICENSE_ERROR = 4;
  public static final int PLUGIN_ERROR = 5;

  private static final String AWT_HEADLESS = "java.awt.headless";
  private static final String PLATFORM_PREFIX_PROPERTY = "idea.platform.prefix";
  private static final String[] NO_ARGS = {};

  private static final String MAIN_LOG_PROPERTY = "com.intellij.idea.Main.DelayedLog";

  private static boolean isHeadless;
  private static boolean isCommandLine;

  private Main() { }

  public static void main(String[] args) {
    if (args.length == 1 && "%f".equals(args[0])) {
      args = NO_ARGS;
    }

    setFlags(args);

    if (isHeadless()) {
      System.setProperty(AWT_HEADLESS, Boolean.TRUE.toString());
    }
    else {
      if (GraphicsEnvironment.isHeadless()) {
        throw new HeadlessException("Unable to detect graphics environment");
      }

      if (args.length == 0) {
        try {
          installPatch();
        }
        catch (Throwable t) {
          appendLog("Exception: " + t.toString() + '\n');
          showMessage("Update Failed", t);
          exit(UPDATE_FAILED);
        }
      }
    }

    try {
      Bootstrap.main(args, Main.class.getName() + "Impl", "start");
    }
    catch (Throwable t) {
      showMessage("Start Failed", t);
      exit(STARTUP_EXCEPTION);
    }
  }

  private static void exit(int code) {
    dumpDelayedLogging();
    System.exit(code);
  }

  public static boolean isHeadless() {
    return isHeadless;
  }

  public static boolean isCommandLine() {
    return isCommandLine;
  }

  public static void setFlags(String[] args) {
    isHeadless = isHeadless(args);
    isCommandLine = isCommandLine(args);
  }

  private static boolean isHeadless(String[] args) {
    if (Boolean.valueOf(System.getProperty(AWT_HEADLESS))) {
      return true;
    }

    if (args.length == 0) {
      return false;
    }

    String firstArg = args[0];
    return Comparing.strEqual(firstArg, "ant") ||
           Comparing.strEqual(firstArg, "duplocate") ||
           Comparing.strEqual(firstArg, "traverseUI") ||
           (firstArg.length() < 20 && firstArg.endsWith("inspect"));
  }

  private static boolean isCommandLine(String[] args) {
    if (isHeadless()) return true;
    return args.length > 0 && Comparing.strEqual(args[0], "diff");
  }

  public static boolean isUITraverser(final String[] args) {
    return args.length > 0 && Comparing.strEqual(args[0], "traverseUI");
  }

  private static void installPatch() throws IOException {
    String platform = System.getProperty(PLATFORM_PREFIX_PROPERTY, "idea");
    String patchFileName = ("jetbrains.patch.jar." + platform).toLowerCase(Locale.US);
    String tempDir = System.getProperty("java.io.tmpdir");

    // always delete previous patch copy
    File patchCopy = new File(tempDir, patchFileName + "_copy");
    File log4jCopy = new File(tempDir, "log4j.jar." + platform + "_copy");
    if (!FileUtilRt.delete(patchCopy) || !FileUtilRt.delete(log4jCopy)) {
      appendLog("Cannot delete temporary files in " + tempDir);
      throw new IOException("Cannot delete temporary files in " + tempDir);
    }

    File patch = new File(tempDir, patchFileName);
    appendLog("[Patch] Original patch %s: %s\n", patch.exists() ? "exists" : "does not exist",
              patch.getAbsolutePath());
    if (!patch.exists()) return;
    File log4j = new File(PathManager.getLibPath(), "log4j.jar");
    if (!log4j.exists()) {
      appendLog("Log4J missing: " + log4j);
      throw new IOException("Log4J missing: " + log4j);
    }
    copyFile(patch, patchCopy, true);
    copyFile(log4j, log4jCopy, false);

    int status = 0;
    if (Restarter.isSupported()) {
      List<String> args = new ArrayList<String>();

      if (SystemInfoRt.isWindows) {
        File launcher = new File(PathManager.getBinPath(), "VistaLauncher.exe");
        args.add(Restarter.createTempExecutable(launcher).getPath());
      }

      //noinspection SpellCheckingInspection
      Collections.addAll(args,
                         System.getProperty("java.home") + "/bin/java".replace('/', File.separatorChar),
                         "-Xmx500m",
                         "-classpath",
                         patchCopy.getPath() + File.pathSeparator + log4jCopy.getPath(),
                         "-Djava.io.tmpdir=" + tempDir,
                         "-Didea.updater.log=" + PathManager.getLogPath(),
                         "-Dswing.defaultlaf=" + UIManager.getSystemLookAndFeelClassName(),
                         "com.intellij.updater.Runner",
                         "install",
                         PathManager.getHomePath());

      appendLog("[Patch] Restarted cmd: %s\n", args.toString());

      status = Restarter.scheduleRestart(ArrayUtilRt.toStringArray(args));

      appendLog("[Patch] Restarted status: %d\n", status);
    }
    else {
      appendLog("[Patch] Restart is not supported\n");
      String message = "Patch update is not supported - please do it manually";
      showMessage("Update Error", message, true);
    }

    exit(status);
  }

  private static void copyFile(File original, File copy, boolean move) throws IOException {
    if (move) {
      if (!original.renameTo(copy) || !FileUtilRt.delete(original)) {
        throw new IOException("Cannot create temporary file: " + copy);
      }
    }
    else {
      FileUtilRt.copy(original, copy);
      if (!copy.exists()) {
        throw new IOException("Cannot create temporary file: " + copy);
      }
    }
  }

  public static void showMessage(String title, Throwable t) {
    StringWriter message = new StringWriter();
    message.append("Internal error. Please report to http://");
    boolean studio = "AndroidStudio".equalsIgnoreCase(System.getProperty(PLATFORM_PREFIX_PROPERTY));
    message.append(studio ? "code.google.com/p/android/issues" : "youtrack.jetbrains.com");
    message.append("\n\n");
    t.printStackTrace(new PrintWriter(message));

    String p = System.getProperty(MAIN_LOG_PROPERTY);
    if (p != null) {
      message.append('\n').append(p);
    }

    showMessage(title, message.toString(), true);
  }

  @SuppressWarnings({"UseJBColor", "UndesirableClassUsage"})
  public static void showMessage(String title, String message, boolean error) {
    if (isCommandLine()) {
      PrintStream stream = error ? System.err : System.out;
      stream.println("\n" + title + ": " + message);
    }
    else {
      try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
      catch (Throwable ignore) { }

      JTextPane textPane = new JTextPane();
      textPane.setEditable(false);
      textPane.setText(message.replaceAll("\t", "    "));
      textPane.setBackground(Color.white);
      textPane.setCaretPosition(0);
      JScrollPane scrollPane = new JScrollPane(
        textPane, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

      int maxHeight = Toolkit.getDefaultToolkit().getScreenSize().height - 150;
      Dimension component = scrollPane.getPreferredSize();
      if (component.height >= maxHeight) {
        Object setting = UIManager.get("ScrollBar.width");
        int width = setting instanceof Integer ? ((Integer)setting).intValue() : 20;
        scrollPane.setPreferredSize(new Dimension(component.width + width, maxHeight));
      }

      int type = error ? JOptionPane.ERROR_MESSAGE : JOptionPane.INFORMATION_MESSAGE;
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), scrollPane, title, type);
    }
  }


  /**
   * Appends the non-null string to an internal log property because at
   * this point when the updater runs the main logger hasn't been setup yet.
   *
   * We use a system property rather than a global static variable because
   * both codes do not run in the same ClassLoader and don't have the same
   * globals.
   */
  private static void appendLog(String message, Object...params) {
    String p = System.getProperty(MAIN_LOG_PROPERTY);
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss ");
    String s = dateFormat.format(new Date()) + String.format(message, params);
    if (p == null) {
      p = s;
    } else {
      p += s;
    }
    System.setProperty(MAIN_LOG_PROPERTY, p);
  }

  /** Invoked by Main to dump the log when the Main is exiting right away.
   * The normal IDE log will not be used. */
  public static void dumpDelayedLogging() {
    String p = System.getProperty(MAIN_LOG_PROPERTY);
    if (p != null) {
      System.clearProperty(MAIN_LOG_PROPERTY);
      File log = new File(PathManager.getLogPath());
      //noinspection ResultOfMethodCallIgnored
      log.mkdirs();
      log = new File(log, "idea_patch.log");
      FileOutputStream fos = null;
      try {
        //noinspection IOResourceOpenedButNotSafelyClosed
        fos = new FileOutputStream(log, true /*append*/);
        fos.write(p.getBytes("UTF-8"));
      } catch (IOException ignore) {
      } finally {
        if (fos != null) {
          try { fos.close(); } catch (IOException ignored) {}
        }
      }
    }
  }

  /** Invoked by StartupUtil once the main logger is setup. */
  public static void dumpDelayedLogging(Logger log) {
    if (log != null) {
      String p = System.getProperty(MAIN_LOG_PROPERTY);
      if (p != null) {
        log.info(p);
        System.clearProperty(MAIN_LOG_PROPERTY);
      }
    }
  }
}
