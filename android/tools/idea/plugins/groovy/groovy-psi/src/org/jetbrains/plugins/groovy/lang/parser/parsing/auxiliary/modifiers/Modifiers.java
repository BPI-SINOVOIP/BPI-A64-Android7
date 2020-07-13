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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * Modifiers ::= "def" nls
 *              | {modifier nls}+
 *              | {annotation nls}+
 */

public class Modifiers {
  public static boolean parse(PsiBuilder builder, GroovyParser parser) {
    boolean endsWithNewLine;
    PsiBuilder.Marker modifiersMarker = builder.mark();

    if (!Annotation.parse(builder, parser) && !parseModifier(builder)) {
      modifiersMarker.done(GroovyElementTypes.MODIFIERS);
      return false;
    }

    PsiBuilder.Marker newLineMarker = builder.mark();
    while (true) {
      newLineMarker.drop();
      newLineMarker = builder.mark();
      endsWithNewLine = ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);

      if (!Annotation.parse(builder, parser) && !parseModifier(builder)) break;
    }

    // Do not include last newline
    if (endsWithNewLine) {
      newLineMarker.rollbackTo();
    } else {
      newLineMarker.drop();
    }
    modifiersMarker.done(GroovyElementTypes.MODIFIERS);
    ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
    return true;

  }

  public static boolean parseModifier(PsiBuilder builder) {
    if (TokenSets.MODIFIERS.contains(builder.getTokenType())) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }
}
