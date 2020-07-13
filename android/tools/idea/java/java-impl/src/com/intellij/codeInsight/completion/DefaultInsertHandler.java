/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.CharTailType;
import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class DefaultInsertHandler extends TemplateInsertHandler implements Cloneable {

  public static final DefaultInsertHandler NO_TAIL_HANDLER = new DefaultInsertHandler(){
    @Override
    protected TailType getTailType(char completionChar, LookupItem item) {
      return TailType.NONE;
    }
  };

  @Override
  public void handleInsert(final InsertionContext context, LookupElement item) {
    super.handleInsert(context, item);

    handleInsertInner(context, (LookupItem)item, context.getCompletionChar());
  }

  private void handleInsertInner(InsertionContext context, LookupItem item, final char completionChar) {
    final Project project = context.getProject();
    final Editor editor = context.getEditor();
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);

    TailType tailType = getTailType(completionChar, item);

    InsertHandlerState state = new InsertHandlerState(context.getSelectionEndOffset(), context.getSelectionEndOffset());

    if (completionChar == Lookup.REPLACE_SELECT_CHAR) {
      removeEndOfIdentifier(context);
    }
    else if(context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET) != context.getSelectionEndOffset()) {
      JavaCompletionUtil.resetParensInfo(context.getOffsetMap());
    }

    handleParentheses(false, false, tailType, context, state);

    context.setTailOffset(state.tailOffset);
    state.caretOffset = processTail(tailType, state.caretOffset, state.tailOffset, editor);
    editor.getSelectionModel().removeSelection();

    if (tailType == TailType.DOT || context.getCompletionChar() == '.') {
      AutoPopupController.getInstance(project).autoPopupMemberLookup(editor, null);
    }

  }

  private static void handleParentheses(final boolean hasParams, final boolean needParenth, TailType tailType, InsertionContext context, InsertHandlerState myState){
    final Document document = context.getEditor().getDocument();
    boolean insertRightParenth = context.getCompletionChar() != Lookup.COMPLETE_STATEMENT_SELECT_CHAR;

    if (needParenth){
      if (context.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET) >= 0 && context.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET) >= 0){
        myState.tailOffset = context.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET);
        if (context.getOffsetMap().getOffset(JavaCompletionUtil.RPAREN_OFFSET) < 0 && insertRightParenth){
          document.insertString(myState.tailOffset, ")");
          myState.tailOffset += 1;
        }
        if (hasParams){
          myState.caretOffset = context.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET) + 1;
        }
        else{
          myState.caretOffset = context.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET);
        }
      }
      else{
        final CommonCodeStyleSettings styleSettings = context.getCodeStyleSettings();
        myState.tailOffset = context.getSelectionEndOffset();
        myState.caretOffset = context.getSelectionEndOffset();

        if(styleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES){
          document.insertString(myState.tailOffset++, " ");
          myState.caretOffset ++;
        }
        if (insertRightParenth) {
          final CharSequence charsSequence = document.getCharsSequence();
          if (charsSequence.length() <= myState.tailOffset || charsSequence.charAt(myState.tailOffset) != '(') {
            document.insertString(myState.tailOffset, "(");
          }

          document.insertString(myState.tailOffset + 1, ")");
          if (hasParams){
            myState.tailOffset += 2;
            myState.caretOffset++;
          }
          else{
            if (tailType != TailTypes.CALL_RPARENTH) {
              myState.tailOffset += 2;
              myState.caretOffset += 2;
            }
            else {
              myState.tailOffset++;
              myState.caretOffset++;
            }
          }
        }
        else{
          document.insertString(myState.tailOffset++, "(");
          myState.caretOffset ++;
        }

        if(hasParams && styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES){
          document.insertString(myState.caretOffset++, " ");
          myState.tailOffset++;
        }
      }
    }
  }

  public static void removeEndOfIdentifier(InsertionContext context){
    final Document document = context.getEditor().getDocument();
    JavaCompletionUtil.initOffsets(context.getFile(), context.getOffsetMap());
    document.deleteString(context.getSelectionEndOffset(), context.getOffsetMap().getOffset(CompletionInitializationContext.IDENTIFIER_END_OFFSET));
    if(context.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET) > 0){
      document.deleteString(context.getOffsetMap().getOffset(JavaCompletionUtil.LPAREN_OFFSET),
                              context.getOffsetMap().getOffset(JavaCompletionUtil.ARG_LIST_END_OFFSET));
      JavaCompletionUtil.resetParensInfo(context.getOffsetMap());
    }
  }

  protected TailType getTailType(final char completionChar, LookupItem item){
    switch(completionChar){
      case '.': return new CharTailType('.', false);
      case ',': return TailType.COMMA;
      case ';': return TailType.SEMICOLON;
      case '=': return TailType.EQ;
      case ' ': return TailType.SPACE;
      case ':': return TailType.CASE_COLON; //?
      case '<':
      case '>':
      case '\"':
    }
    final TailType attr = item.getTailType();
    return attr == TailType.UNKNOWN ? TailType.NONE : attr;
  }

  private static int processTail(TailType tailType, int caretOffset, int tailOffset, Editor editor) {
    editor.getCaretModel().moveToOffset(caretOffset);
    tailType.processTail(editor, tailOffset);
    return editor.getCaretModel().getOffset();
  }

  @Override
  protected void populateInsertMap(@NotNull final PsiFile file, @NotNull final OffsetMap offsetMap) {
    JavaCompletionUtil.initOffsets(file, offsetMap);
  }

  public static class InsertHandlerState{
    int tailOffset;
    int caretOffset;

    public InsertHandlerState(int caretOffset, int tailOffset){
      this.caretOffset = caretOffset;
      this.tailOffset = tailOffset;
    }
  }
}
