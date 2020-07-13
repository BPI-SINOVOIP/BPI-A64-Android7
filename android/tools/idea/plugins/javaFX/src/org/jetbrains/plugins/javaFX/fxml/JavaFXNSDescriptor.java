package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassBackedElementDescriptor;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxDefaultPropertyElementDescriptor;

import java.util.ArrayList;

/**
* User: anna
* Date: 1/9/13
*/
public class JavaFXNSDescriptor implements XmlNSDescriptor, Validator<XmlDocument> {
  private XmlFile myFile;

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    final String name = tag.getName();

    if (tag.getName().equals(FxmlConstants.FX_ROOT)) {
      return new JavaFxDefaultPropertyElementDescriptor(name, tag);
    }
    final XmlTag parentTag = tag.getParentTag();
    if (parentTag != null) {
      final XmlElementDescriptor descriptor = parentTag.getDescriptor();
      if (descriptor != null) {
        return descriptor.getElementDescriptor(tag, parentTag);
      }
    }
    return new JavaFxClassBackedElementDescriptor(name, tag);
  }

  @NotNull
  @Override
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable XmlDocument document) {
    if (document != null) {
      final Project project = document.getProject();
      final PsiClass paneClass = JavaPsiFacade.getInstance(project).findClass(JavaFxCommonClassNames.JAVAFX_SCENE_LAYOUT_PANE, GlobalSearchScope.allScope(project));
      if (paneClass != null) {
        final ArrayList<XmlElementDescriptor> result = new ArrayList<XmlElementDescriptor>();
        ClassInheritorsSearch.search(paneClass, paneClass.getUseScope(), true, true, false).forEach(new Processor<PsiClass>() {
          @Override
          public boolean process(PsiClass psiClass) {
            result.add(new JavaFxClassBackedElementDescriptor(psiClass.getName(), psiClass));
            return true;
          }
        });
        return result.toArray(new XmlElementDescriptor[result.size()]);
      }
    }
    return new XmlElementDescriptor[0];
  }

  @Nullable
   public XmlFile getDescriptorFile() {
     return myFile;
   }
 
   public boolean isHierarhyEnabled() {
     return false;
   }
 
   public PsiElement getDeclaration() {
     return myFile;
   }

  @Override
  public String getName(PsiElement context) {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public void init(PsiElement element) {
    XmlDocument document = (XmlDocument) element;
    myFile = ((XmlFile)document.getContainingFile());
  }

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public void validate(@NotNull XmlDocument context, @NotNull ValidationHost host) {}
}
