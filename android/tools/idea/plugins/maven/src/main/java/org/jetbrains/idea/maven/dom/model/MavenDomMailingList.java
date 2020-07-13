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

import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomElement;
import org.jetbrains.idea.maven.dom.converters.MavenUrlConverter;

/**
 * http://maven.apache.org/POM/4.0.0:MailingList interface.
 * <pre>
 * <h3>Type http://maven.apache.org/POM/4.0.0:MailingList documentation</h3>
 * 3.0.0+
 * </pre>
 */
public interface MavenDomMailingList extends MavenDomElement {

  /**
   * Returns the value of the name child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:name documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the name child.
   */
  @NotNull
  GenericDomValue<String> getName();

  /**
   * Returns the value of the subscribe child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:subscribe documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the subscribe child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getSubscribe();

  /**
   * Returns the value of the unsubscribe child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:unsubscribe documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the unsubscribe child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getUnsubscribe();

  /**
   * Returns the value of the post child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:post documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the post child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getPost();

  /**
   * Returns the value of the archive child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:archive documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the archive child.
   */
  @NotNull
  @Convert(MavenUrlConverter.class)
  GenericDomValue<String> getArchive();

  /**
   * Returns the value of the otherArchives child.
   * <pre>
   * <h3>Element http://maven.apache.org/POM/4.0.0:otherArchives documentation</h3>
   * 3.0.0+
   * </pre>
   *
   * @return the value of the otherArchives child.
   */
  @NotNull
  MavenDomOtherArchives getOtherArchives();
}
