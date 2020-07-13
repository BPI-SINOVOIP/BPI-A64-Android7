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

// Generated on Mon Mar 17 18:02:09 MSK 2008
// DTD/Schema  :    http://maven.apache.org/POM/4.0.0

package org.jetbrains.idea.maven.dom.model;

import com.intellij.openapi.paths.PathReference;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.references.MavenDirectoryPathReferenceConverter;

/**
 * http://maven.apache.org/POM/4.0.0:ActivationFile interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:ActivationFile documentation</h3>
 * 4.0.0
 * </pre>
 */
public interface MavenDomActivationFile extends MavenDomElement {

  /**
   * Returns the value of the missing child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:missing documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the missing child.
   */
  @NotNull
  @Convert(value = MavenDirectoryPathReferenceConverter.class, soft = true)
  GenericDomValue<PathReference> getMissing();

  /**
   * Returns the value of the exists child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:exists documentation</h3>
   * 4.0.0
   * </pre>
   *
   * @return the value of the exists child.
   */
  @NotNull
  @Convert(value = MavenDirectoryPathReferenceConverter.class, soft = true)
  GenericDomValue<PathReference> getExists();
}
