package org.jetbrains.android.uipreview;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class RenderingException extends Exception {
  private final String myPresentableMessage;
  private final Throwable[] myCauses;

  public RenderingException() {
    super();
    myPresentableMessage = null;
    myCauses = new Throwable[0];
  }

  public RenderingException(String message, Throwable... causes) {
    super(message, causes.length > 0 ? causes[0] : null);
    myPresentableMessage = message;
    myCauses = causes;
  }

  public RenderingException(@NotNull Throwable... causes) {
    myPresentableMessage = null;
    myCauses = causes;
  }

  @NotNull
  public Throwable[] getCauses() {
    return myCauses;
  }

  public String getPresentableMessage() {
    return myPresentableMessage;
  }
}
