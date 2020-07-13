/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.packaging.impl.elements;

import com.intellij.compiler.ant.BuildProperties;
import com.intellij.compiler.ant.Generator;
import com.intellij.compiler.ant.taskdefs.Include;
import com.intellij.compiler.ant.taskdefs.Mkdir;
import com.intellij.compiler.ant.taskdefs.PatternSet;
import com.intellij.compiler.ant.taskdefs.Unzip;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.AntCopyInstructionCreator;
import com.intellij.packaging.elements.ArtifactAntGenerationContext;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.ui.ExtractedDirectoryPresentation;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.PathUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class ExtractedDirectoryPackagingElement extends FileOrDirectoryCopyPackagingElement<ExtractedDirectoryPackagingElement> {
  private String myPathInJar;

  public ExtractedDirectoryPackagingElement() {
    super(PackagingElementFactoryImpl.EXTRACTED_DIRECTORY_ELEMENT_TYPE);
  }

  public ExtractedDirectoryPackagingElement(String jarPath, String pathInJar) {
    super(PackagingElementFactoryImpl.EXTRACTED_DIRECTORY_ELEMENT_TYPE, jarPath);
    myPathInJar = pathInJar;
    if (!StringUtil.startsWithChar(myPathInJar, '/')) {
      myPathInJar = "/" + myPathInJar;
    }
    if (!StringUtil.endsWithChar(myPathInJar, '/')) {
      myPathInJar += "/";
    }
  }

  @Override
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new ExtractedDirectoryPresentation(this); 
  }

  @Override
  public String toString() {
    return "extracted:" + myFilePath + "!" + myPathInJar;
  }

  @Override
  public VirtualFile findFile() {
    final VirtualFile jarFile = super.findFile();
    if (jarFile == null) return null;

    final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile);
    if ("/".equals(myPathInJar)) return jarRoot;
    return jarRoot != null ? jarRoot.findFileByRelativePath(myPathInJar) : null;
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                          @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    final String jarPath = generationContext.getSubstitutedPath(myFilePath);
    final String pathInJar = StringUtil.trimStart(myPathInJar, "/");
    if (pathInJar.length() == 0) {
      return Collections.singletonList(creator.createExtractedDirectoryInstruction(jarPath));
    }

    final String archiveName = PathUtil.getFileName(myFilePath);
    final String tempDirProperty = generationContext.createNewTempFileProperty("temp.unpacked.path." + archiveName, archiveName);
    final String tempDirPath = BuildProperties.propertyRef(tempDirProperty);
    generationContext.runBeforeCurrentArtifact(new Mkdir(tempDirPath));

    final Unzip unzip = new Unzip(jarPath, tempDirPath);
    final PatternSet patterns = new PatternSet(null);
    patterns.add(new Include(pathInJar + "**"));
    unzip.add(patterns);
    generationContext.runBeforeCurrentArtifact(unzip);

    return Collections.singletonList(creator.createDirectoryContentCopyInstruction(tempDirPath + "/" + pathInJar));
  }


  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof ExtractedDirectoryPackagingElement && super.isEqualTo(element)
           && Comparing.equal(myPathInJar, ((ExtractedDirectoryPackagingElement)element).getPathInJar());
  }

  @Override
  public ExtractedDirectoryPackagingElement getState() {
    return this;
  }

  @Override
  public void loadState(ExtractedDirectoryPackagingElement state) {
    myFilePath = state.getFilePath();
    myPathInJar = state.getPathInJar();
  }

  @Attribute("path-in-jar")
  public String getPathInJar() {
    return myPathInJar;
  }

  public void setPathInJar(String pathInJar) {
    myPathInJar = pathInJar;
  }
}
