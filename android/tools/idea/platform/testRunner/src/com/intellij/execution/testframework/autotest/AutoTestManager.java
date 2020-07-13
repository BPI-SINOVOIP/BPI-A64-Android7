package com.intellij.execution.testframework.autotest;

import com.intellij.execution.DelayedDocumentWatcher;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.util.Consumer;
import com.intellij.util.containers.WeakHashMap;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

/**
 * @author yole
 */
public class AutoTestManager {
  static final Key<Boolean> AUTOTESTABLE = Key.create("auto.test.manager.supported");
  public static final String AUTO_TEST_MANAGER_DELAY = "auto.test.manager.delay";

  private final Project myProject;

  private int myDelay;
  private DelayedDocumentWatcher myDocumentWatcher;

  // accessed only from EDT
  private final Set<Content> myEnabledDescriptors = Collections.newSetFromMap(new WeakHashMap<Content, Boolean>());

  public static AutoTestManager getInstance(Project project) {
    return ServiceManager.getService(project, AutoTestManager.class);
  }

  public AutoTestManager(Project project) {
    myProject = project;
    myDelay = PropertiesComponent.getInstance(myProject).getOrInitInt(AUTO_TEST_MANAGER_DELAY, 3000);
    myDocumentWatcher = createWatcher();
  }

  private DelayedDocumentWatcher createWatcher() {
    return new DelayedDocumentWatcher(myProject, myDelay, new Consumer<Set<VirtualFile>>() {
      @Override
      public void consume(Set<VirtualFile> files) {
        for (Content content : myEnabledDescriptors) {
          runAutoTest(content);
        }
      }
    }, new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile file) {
        // Vladimir.Krivosheev — I don't know, why AutoTestManager checks it, but old behavior is preserved
        return FileEditorManager.getInstance(myProject).isFileOpen(file);
      }
    });
  }

  public void setAutoTestEnabled(RunContentDescriptor descriptor, boolean enabled) {
    Content content = descriptor.getAttachedContent();
    if (enabled) {
      myEnabledDescriptors.add(content);
      myDocumentWatcher.activate();
    }
    else {
      myEnabledDescriptors.remove(content);
      if (myEnabledDescriptors.isEmpty()) {
        myDocumentWatcher.deactivate();
      }
    }
  }

  public boolean isAutoTestEnabled(RunContentDescriptor descriptor) {
    return myEnabledDescriptors.contains(descriptor.getAttachedContent());
  }

  private static void runAutoTest(Content content) {
    JComponent component = content.getComponent();
    if (component != null) {
      DataContext dataContext = DataManager.getInstance().getDataContext(component);
      RunContentDescriptor descriptor = LangDataKeys.RUN_CONTENT_DESCRIPTOR.getData(dataContext);
      if (descriptor != null) {
        ProcessHandler processHandler = descriptor.getProcessHandler();
        if (processHandler != null && !processHandler.isProcessTerminated()) {
          return;
        }

        descriptor.setActivateToolWindowWhenAdded(false);
        descriptor.setReuseToolWindowActivation(true);
        ExecutionUtil.restart(content);
      }
    }
  }

  int getDelay() {
    return myDelay;
  }

  void setDelay(int delay) {
    myDelay = delay;
    myDocumentWatcher.deactivate();
    myDocumentWatcher = createWatcher();
    if (!myEnabledDescriptors.isEmpty()) {
      myDocumentWatcher.activate();
    }
    PropertiesComponent.getInstance(myProject).setValue(AUTO_TEST_MANAGER_DELAY, String.valueOf(myDelay));
  }
}
