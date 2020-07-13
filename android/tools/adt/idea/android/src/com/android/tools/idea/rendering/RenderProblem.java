/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.utils.HtmlBuilder;
import com.android.utils.XmlUtils;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A {@linkplain RenderProblem} holds information about a layout rendering
 * error, suitable for display in a {@link RenderErrorPanel}.
 */
public abstract class RenderProblem implements Comparable<RenderProblem> {
  public static final int PRIORITY_UNEXPECTED = 10;
  public static final int PRIORITY_MISSING_CLASSES = 20;
  public static final int PRIORITY_BROKEN_CLASSES = 30;
  public static final int PRIORITY_MISSING_ATTRIBUTE = 40;
  public static final int PRIORITY_MISSING_STYLE = 50;
  public static final int PRIORITY_NINEPATCH_RENDER_ERROR = 60;
  public static final int PRIORITY_RENDERING_FIDELITY = 1000;

  @NotNull final private HighlightSeverity mySeverity;
  private final int myOrdinal;
  private int myPriority = PRIORITY_UNEXPECTED;
  @Nullable private Throwable myThrowable;
  @Nullable private Object myClientData;
  @Nullable private String myTag;

  private static int ourNextOrdinal;

  /**
   * Constructs a full error message showing the given text
   *
   * @param severity the severity of the error
   * @param message plain text, not HTML
   * @return a new error message
   */
  @NotNull
  public static RenderProblem createPlain(@NotNull HighlightSeverity severity, @Nullable String message) {
    return new Plain(severity, ourNextOrdinal++, message != null ? XmlUtils.toXmlTextValue(message) : "");
  }

  @NotNull
  public static RenderProblem createPlain(@NotNull HighlightSeverity severity,
                                          @NotNull String message,
                                          @NotNull Project project,
                                          @NotNull HtmlLinkManager linkManager,
                                          @Nullable Throwable throwable) {
    Html problem = new Html(severity, ourNextOrdinal++);
    HtmlBuilder builder = problem.getHtmlBuilder();
    builder.add(message);
    if (throwable != null) {
      String url = linkManager.createRunnableLink(new ShowExceptionFix(project, throwable));
      builder.add(" (").addLink("Details", url).add(")");
    }
    return problem;
  }

  /**
   * Constructs a blank error message which can be appended to
   * by the various {@link HtmlBuilder#add} and {@link HtmlBuilder#newline} methods.
   *
   * @param severity the severity of the error
   * @return a new error message
   */
  @NotNull
  public static RenderProblem.Html create(@NotNull HighlightSeverity severity) {
    return new Html(severity, ourNextOrdinal++);
  }

  /**
   * Constructs an error whose message is lazily computed from the error description
   * and the throwable
   *
   * @param severity the severity of the error
   * @param tag the tag for thee error type, if known
   * @param text the text message for the error
   * @param throwable the associated exception, if any
   * @return a new error message
   */
  @NotNull
  public static RenderProblem createDeferred(@NotNull HighlightSeverity severity,
                                             @Nullable String tag,
                                             @NotNull String text,
                                             @Nullable Throwable throwable) {
    return new Deferred(severity, tag, text, throwable);
  }

  private RenderProblem(@NotNull HighlightSeverity severity, int ordinal) {
    mySeverity = severity;
    myOrdinal = ordinal;
  }

  public RenderProblem throwable(@Nullable Throwable throwable) {
    myThrowable = throwable;

    return this;
  }

  public RenderProblem priority(int priority) {
    myPriority = priority;

    return this;
  }

  public RenderProblem tag(@Nullable String tag) {
    myTag = tag;

    return this;
  }

  @Nullable
  public String getTag() {
    return myTag;
  }

  @NotNull
  public abstract String getHtml();

  public void appendHtml(@NotNull StringBuilder stringBuilder) {
    stringBuilder.append(getHtml());
  }

  @Override
  public int compareTo(RenderProblem other) {
    if (mySeverity != other.mySeverity) {
      return mySeverity.compareTo(other.mySeverity);
    }
    if (myPriority != other.myPriority) {
      return myPriority - other.myPriority;
    }
    return myOrdinal - other.myOrdinal;
  }

  @NotNull
  public static String format(List<RenderProblem> messages) {
    StringBuilder sb = new StringBuilder();

    for (RenderProblem message : messages) {
      sb.append(message.getHtml());
      sb.append("<br/>\n");
    }

    return sb.toString();
  }

  @NotNull
  public HighlightSeverity getSeverity() {
    return mySeverity;
  }

  @Nullable
  public Throwable getThrowable() {
    return myThrowable;
  }

  @Nullable
  public Object getClientData() {
    return myClientData;
  }

  public void setClientData(@Nullable Object clientData) {
    myClientData = clientData;
  }

  public static class Plain extends RenderProblem {
    @NotNull private final String myHtml;

    private Plain(@NotNull HighlightSeverity severity, int ordinal, @NotNull String text) {
      super(severity, ordinal);
      myHtml = text;
    }

    @Override
    @NotNull
    public String getHtml() {
      return myHtml;
    }
  }

  public static class Html extends RenderProblem {
    @NotNull private final HtmlBuilder myBuilder;

    private Html(@NotNull HighlightSeverity severity, int ordinal) {
      super(severity, ordinal);
      myBuilder = new HtmlBuilder();
    }

    @NotNull
    @Override
    public String getHtml() {
      return myBuilder.getHtml();
    }

    @NotNull
    public HtmlBuilder getHtmlBuilder() {
      return myBuilder;
    }
  }

  /** Render error message whose actual status is computed lazily */
  public static class Deferred extends RenderProblem {
    protected final String myTag;
    protected final String myText;

    protected Deferred(@NotNull HighlightSeverity severity,
                       @Nullable String tag,
                       @NotNull String text,
                       @Nullable Throwable throwable) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      super(severity, ourNextOrdinal++);
      myTag = tag;
      myText = text;
      throwable(throwable);
    }

    @Override
    @NotNull
    public String getHtml() {
      return new HtmlBuilder().add(myText).getHtml();
    }
  }
}
