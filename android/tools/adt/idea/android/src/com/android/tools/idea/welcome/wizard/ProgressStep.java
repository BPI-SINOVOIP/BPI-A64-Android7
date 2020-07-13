/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.welcome.wizard;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Wizard step with progress bar and "more details" button.
 */
public abstract class ProgressStep extends FirstRunWizardStep {
  private final ConsoleHighlighter myHighlighter;
  private final EditorEx myConsoleEditor;
  private JPanel myRoot;
  private JProgressBar myProgressBar;
  private JButton myShowDetailsButton;
  private JLabel myLabel;
  private JPanel myConsole;
  private ProgressIndicator myProgressIndicator;
  private double myFraction = 0;

  public ProgressStep(@NotNull Disposable parent) {
    super("Downloading Components");
    setComponent(myRoot);
    myLabel.setText("Installing");
    //noinspection ConstantConditions
    myConsoleEditor = ConsoleViewUtil.setupConsoleEditor((Project)null, false, false);
    myConsoleEditor.getSettings().setUseSoftWraps(true);
    myConsoleEditor.reinitSettings();
    Disposer.register(parent, new Disposable() {
      @Override
      public void dispose() {
        EditorFactory.getInstance().releaseEditor(myConsoleEditor);
      }
    });
    myHighlighter = new ConsoleHighlighter();
    myHighlighter.setModalityState(ModalityState.stateForComponent(myLabel));
    myConsoleEditor.setHighlighter(myHighlighter);
    JComponent editorComponent = myConsoleEditor.getComponent();
    myConsole.add(editorComponent, BorderLayout.CENTER);
    editorComponent.setVisible(false);
    myShowDetailsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showConsole();
      }
    });
  }

  @Override
  public void init() {
    // Do nothing
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        execute();
      }
    });
  }

  protected abstract void execute();

  @Nullable
  @Override
  public JLabel getMessageLabel() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myShowDetailsButton;
  }

  /**
   * @return progress indicator that will report the progress to this wizard step.
   */
  @NotNull
  public synchronized ProgressIndicator getProgressIndicator() {
    if (myProgressIndicator == null) {
      myProgressIndicator = new ProgressIndicatorIntegration();
    }
    return myProgressIndicator;
  }

  /**
   * Output text to the console pane.
   *
   * @param s           text to print
   * @param contentType attributes of the text to output
   */
  public void print(@NotNull String s, @NotNull ConsoleViewContentType contentType) {
    myHighlighter.setModalityState(ModalityState.stateForComponent(myConsole));
    myHighlighter.print(s, contentType.getAttributes());
  }

  /**
   * Will output process standard in and out to the console view.
   * <p/>
   * Note: current version does not support collecting user input. We may
   * reconsider this at a later point.
   *
   * @param processHandler  process to track
   */
  public void attachToProcess(ProcessHandler processHandler) {
    myHighlighter.attachToProcess(processHandler);
  }

  public boolean isCanceled() {
    return getProgressIndicator().isCanceled();
  }

  /**
   * Displays console widget if one was not visible already
   */
  public void showConsole() {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        JComponent editorComponent = myConsoleEditor.getComponent();
        if (!editorComponent.isVisible()) {
          myShowDetailsButton.getParent().remove(myShowDetailsButton);
          editorComponent.setVisible(true);
        }
      }
    });
  }

  /**
   * Runs the computable under progress manager but only gives a portion of the progress bar to it.
   */
  public void run(final Runnable runnable, double progressPortion) {
    ProgressIndicator progress = new ProgressPortionReporter(getProgressIndicator(), myFraction, progressPortion);
    ProgressManager.getInstance().executeProcessUnderProgress(runnable, progress);
  }

  private void setFraction(double fraction) {
    myFraction = fraction;
    myProgressBar.setMaximum(1000);
    myProgressBar.setValue((int)(1000 * fraction));
  }

  public void advance(double progress) {
    getProgressIndicator().setFraction(myFraction + progress);
  }

  /**
   * Progress indicator that scales task to only use a portion of the parent indicator.
   */
  public static class ProgressPortionReporter extends DelegatingProgressIndicator {
    private final double myStart;
    private final double myPortion;

    public ProgressPortionReporter(@NotNull ProgressIndicator indicator, double start, double portion) {
      super(indicator);
      myStart = start;
      myPortion = portion;
    }

    @Override
    public void start() {
      setFraction(0);
    }

    @Override
    public void stop() {
      setFraction(myPortion);
    }

    @Override
    public void setFraction(double fraction) {
      super.setFraction(myStart + (fraction * myPortion));
    }
  }

  /**
   * Progress indicator integration for this wizard step
   */
  private class ProgressIndicatorIntegration extends ProgressIndicatorBase {
    @Override
    public void start() {
      super.start();
      setIndeterminate(false);
    }

    @Override
    public void setText(final String text) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myLabel.setText(text);
        }
      });
    }

    @Override
    public void setText2(String text) {
      print(text + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
      super.setText2(text);
    }

    @Override
    public void stop() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myLabel.setText(null);
          myProgressBar.setVisible(false);
          showConsole();
        }
      }, ModalityState.stateForComponent(myProgressBar));
      super.stop();
    }

    @Override
    public void setIndeterminate(final boolean indeterminate) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          myProgressBar.setIndeterminate(indeterminate);
        }
      });
    }

    @Override
    public void setFraction(final double fraction) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ProgressStep.this.setFraction(fraction);
        }
      });
    }
  }
}
