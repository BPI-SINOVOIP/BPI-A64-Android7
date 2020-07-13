/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.inspections.lint;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.CLASS_PARCEL;
import static com.android.SdkConstants.CLASS_PARCELABLE;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_ARRAY_LIST;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;

/**
 * Quick Fix for missing CREATOR field in an implementation of Parcelable.
 * This fix can either:
 * <ul>
 *   <li>Add methods for persisting the fields in the class.</li>
 *   <li>Remove Parcelable and all methods related to Parcelable.</li>
 *   <li>Remove existing implementation and redo the code generation.</li>
 * </ul>
 */
public class ParcelableQuickFix implements AndroidLintQuickFix {
  private static final String CREATOR =
          "public static final android.os.Parcelable.Creator<%1$s> CREATOR = new android.os.Parcelable.Creator<%1$s>() {\n" +
                  "  @Override\n" +
                  "  public %1$s createFromParcel(android.os.Parcel in) {\n" +
                  "    return new %1$s(in);\n" +
                  "  }\n\n" +
                  "  @Override\n" +
                  "  public %1$s[] newArray(int size) {\n" +
                  "    return new %1$s[size];\n" +
                  "  }\n" +
                  "};\n";
  private static final String CONSTRUCTOR =
          "protected %1$s(android.os.Parcel in) {\n" +
                  "}\n";
  private static final String CONSTRUCTOR_WITH_SUPER =
          "protected %1$s(android.os.Parcel in) {\n" +
                  "  super(in);\n" +
                  "}\n";
  private static final String DESCRIBE_CONTENTS =
          "@Override\n" +
                  "public int describeContents() {\n" +
                  "  return 0;\n" +
                  "}\n";
  private static final String WRITE_TO_PARCEL =
          "@Override\n" +
                  "public void writeToParcel(android.os.Parcel dest, int flags) {\n" +
                  "}\n";
  private static final String WRITE_TO_PARCEL_WITH_SUPER =
          "@Override\n" +
                  "public void writeToParcel(android.os.Parcel dest, int flags) {\n" +
                  "  super.writeToParcel(dest, flags);\n" +
                  "}\n";
  private static final String CLASS_T = "T";
  private static final String CLASS_T_ARRAY = "T[]";

  public enum Operation {
    IMPLEMENT,
    REMOVE,
    REIMPLEMENT,
  }

  private final String myName;
  private final Operation myOperation;

  public ParcelableQuickFix(String name, Operation operation) {
    myName = name;
    myOperation = operation;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    PsiClass parcelable = getClassOfSupportedElement(startElement);
    if (parcelable == null || findParcelableFromImplementsList(parcelable) == null) {
      return false;
    }
    switch (myOperation) {
      case IMPLEMENT:
        return parcelable.findFieldByName("CREATOR", false) == null;
      case REIMPLEMENT:
        return parcelable.findFieldByName("CREATOR", false) != null;
      case REMOVE:
        return true;
      default:
        return false;
    }
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    PsiClass parcelable = getClassOfSupportedElement(startElement);
    if (parcelable == null) {
      return;
    }
    QuickFixWorker worker = new QuickFixWorker(parcelable);
    switch (myOperation) {
      case IMPLEMENT:
        worker.implement();
        break;
      case REMOVE:
        worker.remove();
        break;
      case REIMPLEMENT:
        worker.reimplement();
        break;
    }
  }
  @Nullable
  private PsiClass getClassOfSupportedElement(@NotNull PsiElement element) {
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiClass) {
      return (PsiClass)element.getParent();
    }
    if (element instanceof PsiClass && myOperation != Operation.IMPLEMENT) {
      return (PsiClass)element;
    }
    if (element instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement)element;
      if (reference.getCanonicalText().equals(CLASS_PARCELABLE) &&
              reference.getParent() instanceof PsiReferenceList &&
              reference.getParent().getParent() instanceof PsiClass) {
        return (PsiClass)reference.getParent().getParent();
      }
    }
    return null;
  }

  @Nullable
  private static PsiElement findParcelableFromImplementsList(@NotNull PsiClass parcelable) {
    PsiReferenceList implementsList = parcelable.getImplementsList();
    if (implementsList != null) {
      for (PsiJavaCodeReferenceElement element : implementsList.getReferenceElements()) {
        if (CLASS_PARCELABLE.equals(element.getQualifiedName())) {
          return element;
        }
      }
    }
    return null;
  }

  private static class QuickFixWorker {
    private final Project myProject;
    private final JavaPsiFacade myFacade;
    private final PsiElementFactory myFactory;
    private final PsiClass myParcelable;
    private final PsiClassType myParcelableType;
    private final PsiClassType myListType;
    private final PsiClass myList;
    private final PsiClassType myTType;
    private final PsiClassType myTArrayType;
    private final PsiClassType myTListType;
    private final Map<PsiType, FieldPersistence> myPersistenceMap;
    private final Set<String> myIgnoredMethods;

    private QuickFixWorker(@NotNull PsiClass parcelable) {
      myProject = parcelable.getProject();
      myFacade = JavaPsiFacade.getInstance(myProject);
      myFactory = myFacade.getElementFactory();
      myParcelable = parcelable;
      myParcelableType = PsiType.getTypeByName(CLASS_PARCELABLE, myProject, GlobalSearchScope.allScope(myProject));
      myListType = PsiType.getTypeByName(JAVA_UTIL_LIST, myProject, GlobalSearchScope.allScope(myProject));
      myList = myListType.resolve();
      assert myList != null;
      myTType = PsiType.getTypeByName(CLASS_T, myProject, GlobalSearchScope.allScope(myProject));
      myTArrayType = PsiType.getTypeByName(CLASS_T_ARRAY, myProject, GlobalSearchScope.allScope(myProject));
      myTListType = myFactory.createType(myList, myTType);
      myPersistenceMap = new HashMap<PsiType, FieldPersistence>();
      myIgnoredMethods = new HashSet<String>();
      populateIgnoredMethods();
      populateFieldPersistenceByType();
    }

    private void implement() {
      findOrCreateParcelableFromImplementsList();

      PsiMethod constructor = findOrCreateConstructor();
      addFieldReadsToConstructor(constructor);

      PsiMethod writeToParcel = findOrCreateWriteToParcel();
      addFieldWrites(writeToParcel);

      PsiMethod describeContents = findOrCreateDescribeContents();
      PsiField creator = findOrCreateCreator();
      PsiElement insertionPoint = findInsertionPoint();

      addBefore(myParcelable, constructor, insertionPoint);
      addBefore(myParcelable, writeToParcel, insertionPoint);
      addBefore(myParcelable, describeContents, insertionPoint);
      addBefore(myParcelable, creator, insertionPoint);

      save();
    }

    private void remove() {
      delete(findConstructor());
      delete(findWriteToParcel());
      delete(findDescribeContents());
      delete(findCreator());
      delete(findParcelableFromImplementsList(myParcelable));
      delete(findImportOfClass(CLASS_PARCEL));
      delete(findImportOfClass(CLASS_PARCELABLE));

      save();
    }

    private void reimplement() {
      delete(findConstructor());
      delete(findWriteToParcel());
      delete(findDescribeContents());
      delete(findCreator());

      implement();
      save();
    }

    private void save() {
      Document document = FileDocumentManager.getInstance().getDocument(myParcelable.getContainingFile().getVirtualFile());
      if (document != null) {
        PsiDocumentManager.getInstance(myProject).commitDocument(document);
      }
    }

    @Nullable
    private PsiField findCreator() {
      return myParcelable.findFieldByName("CREATOR", false);
    }

    @NotNull
    private PsiField findOrCreateCreator() {
      PsiField field = findCreator();
      if (field == null) {
        field = myFactory.createFieldFromText(String.format(CREATOR, myParcelable.getName()), myParcelable);
      }
      JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(field);
      return field;
    }

    @Nullable
    private PsiMethod findConstructor() {
      for (PsiMethod method : myParcelable.getConstructors()) {
        if (isConstructorWithParcelParameter(method)) {
          return method;
        }
      }
      return null;
    }

    @NotNull
    private PsiMethod findOrCreateConstructor() {
      PsiMethod method = findConstructor();
      if (method == null) {
        String fmt = doesSuperClassImplementParcelable() ? CONSTRUCTOR_WITH_SUPER : CONSTRUCTOR;
        method = createMethodWithShortClassReferences(String.format(fmt, myParcelable.getName()));
      }
      return method;
    }

    @Nullable
    private PsiMethod findDescribeContents() {
      for (PsiMethod method : myParcelable.getMethods()) {
        PsiParameterList params = method.getParameterList();
        if (method.getName().equals("describeContents") && params.getParametersCount() == 0) {
          return method;
        }
      }
      return null;
    }

    @NotNull
    private PsiMethod findOrCreateDescribeContents() {
      PsiMethod method = findDescribeContents();
      if (method == null) {
        method = createMethodWithShortClassReferences(DESCRIBE_CONTENTS);
      }
      return method;
    }

    @Nullable
    private PsiMethod findWriteToParcel() {
      for (PsiMethod method : myParcelable.getMethods()) {
        if (isWriteToParcelMethod(method)) {
          return method;
        }
      }
      return null;
    }

    @NotNull
    private PsiMethod findOrCreateWriteToParcel() {
      PsiMethod method = findWriteToParcel();
      if (method == null) {
        String text = doesSuperClassImplementParcelable() ? WRITE_TO_PARCEL_WITH_SUPER : WRITE_TO_PARCEL;
        method = createMethodWithShortClassReferences(text);
      }
      return method;
    }

    private void findOrCreateParcelableFromImplementsList() {
      PsiElement element = findParcelableFromImplementsList(myParcelable);
      if (element == null) {
        PsiReferenceList implementsList = myParcelable.getImplementsList();
        if (implementsList != null) {
          PsiJavaCodeReferenceElement implementsParcelable =
                  myFactory.createReferenceElementByFQClassName(CLASS_PARCELABLE, myParcelable.getResolveScope());
          implementsList.add(implementsParcelable);
        }
      }
    }

    @Nullable
    private PsiElement findImportOfClass(@NotNull String className) {
      PsiFile file = myParcelable.getContainingFile();
      if (file != null && file instanceof PsiJavaFile) {
        PsiImportList importList = ((PsiJavaFile)file).getImportList();
        if (importList != null) {
          return importList.findSingleClassImportStatement(className);
        }
      }
      return null;
    }

    private PsiMethod createMethodWithShortClassReferences(@NotNull String text) {
      PsiMethod method = myFactory.createMethodFromText(text, myParcelable);
      JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(method);
      return method;
    }

    private static boolean isConstructorWithParcelParameter(@NotNull PsiMethod method) {
      PsiParameterList params = method.getParameterList();
      return method.isConstructor() && params.getParametersCount() == 1 && params.getParameters()[0].getType().equalsToText(CLASS_PARCEL);
    }

    private static boolean isWriteToParcelMethod(@NotNull PsiMethod method) {
      PsiParameterList params = method.getParameterList();
      return method.getName().equals("writeToParcel") &&
              params.getParametersCount() == 2 &&
              params.getParameters()[0].getType().equalsToText(CLASS_PARCEL) &&
              params.getParameters()[1].getType().equalsToText(PsiType.INT.getCanonicalText());
    }

    private boolean doesSuperClassImplementParcelable() {
      PsiClass superClass = myParcelable.getSuperClass();
      if (superClass == null) {
        return false;
      }
      return findParcelableFromImplementsList(superClass) != null;
    }

    private void addFieldReadsToConstructor(@NotNull PsiMethod constructor) {
      assert isConstructorWithParcelParameter(constructor);
      if (!isEmptyMethod(constructor)) {
        return;
      }
      removeInitialBlankLines(constructor);
      String paramName = constructor.getParameterList().getParameters()[0].getName();
      PsiCodeBlock body = constructor.getBody();
      assert body != null;
      for (PsiField field : myParcelable.getFields()) {
        FieldPersistence persistence = findFieldPersistence(field);
        if (persistence != null) {
          createStatements(persistence.formatRead(field, paramName), body, constructor);
        }
      }
      JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(constructor);
    }

    private void addFieldWrites(@NotNull PsiMethod writeToParcel) {
      assert isWriteToParcelMethod(writeToParcel);
      if (!isEmptyMethod(writeToParcel)) {
        return;
      }
      removeInitialBlankLines(writeToParcel);
      String parcelName = writeToParcel.getParameterList().getParameters()[0].getName();
      String flagsName = writeToParcel.getParameterList().getParameters()[1].getName();
      PsiCodeBlock body = writeToParcel.getBody();
      assert body != null;
      for (PsiField field : myParcelable.getFields()) {
        FieldPersistence persistence = findFieldPersistence(field);
        if (persistence != null) {
          createStatements(persistence.formatWrite(field, parcelName, flagsName), body, writeToParcel);
        }
      }
    }

    private boolean isEmptyMethod(@NotNull PsiMethod method) {
      int expectedStatementCount = doesSuperClassImplementParcelable() ? 1 : 0;
      return method.getBody() == null || method.getBody().getStatements().length <= expectedStatementCount;
    }

    private void removeInitialBlankLines(@NotNull PsiMethod method) {
      PsiWhiteSpace whiteSpace = PsiTreeUtil.getChildOfType(method.getBody(), PsiWhiteSpace.class);
      if (whiteSpace != null && whiteSpace.getText().startsWith("\n\n")) {
        method.getBody().replace(myFactory.createCodeBlock());
      }
    }

    private void createStatements(@NotNull String[] stmtText, @NotNull PsiCodeBlock body, @NotNull PsiMethod method) {
      for (String text : stmtText) {
        PsiStatement stmt = myFactory.createStatementFromText(text, method);
        body.add(stmt);
      }
    }

    @Nullable
    private FieldPersistence findFieldPersistence(@NotNull PsiField field) {
      if (field.hasModifierProperty(PsiModifier.TRANSIENT) ||
              field.hasModifierProperty(PsiModifier.STATIC)) {
        return null;
      }
      PsiType type = field.getType();
      FieldPersistence persistence = myPersistenceMap.get(type);
      if (persistence != null) {
        return persistence;
      }
      if (myParcelableType.isAssignableFrom(type.getDeepComponentType()) && !myParcelableType.equals(type.getDeepComponentType())) {
        if (type.equals(type.getDeepComponentType())) {
          return myPersistenceMap.get(myTType);
        }
        if (type instanceof PsiArrayType) {
          PsiArrayType arrayType = (PsiArrayType)type;
          if (arrayType.getComponentType().equals(type.getDeepComponentType())) {
            return myPersistenceMap.get(myTArrayType);
          }
        }
      }
      PsiType elemType = getListElementType(type);
      if (elemType != null && myParcelableType.isAssignableFrom(elemType) && !myParcelableType.equals(elemType)) {
        return myPersistenceMap.get(myTListType);
      }
      return null;
    }

    @Nullable
    private PsiElement findInsertionPoint() {
      for (PsiMethod method : myParcelable.getMethods()) {
        if (!method.isConstructor()) {
          return method;
        }
      }
      return null;
    }

    private static void addBefore(@NotNull PsiElement parent, @NotNull PsiElement element, @Nullable PsiElement insertionPoint) {
      if (element.getParent() == parent) {
        // Nothing to do: the element is already added to the parent.
        return;
      }
      parent.addBefore(element, insertionPoint);
    }

    private static void delete(@Nullable PsiElement element) {
      if (element != null) {
        element.delete();
      }
    }

    private void populateIgnoredMethods() {
      myIgnoredMethods.add("writeParcelable");
      myIgnoredMethods.add("readParcelable");
      myIgnoredMethods.add("writeParcelableArray");
      myIgnoredMethods.add("readParcelableArray");
      myIgnoredMethods.add("readSerializable");
      myIgnoredMethods.add("writeSerializable");
      myIgnoredMethods.add("readValue");
      myIgnoredMethods.add("writeValue");
      myIgnoredMethods.add("readArray");
      myIgnoredMethods.add("writeArray");
    }

    private void populateFieldPersistenceByType() {
      PsiClass parcel = myFacade.findClass(CLASS_PARCEL, GlobalSearchScope.allScope(myProject));
      if (parcel == null) {
        return;
      }
      Map<PsiType, PsiMethod> setters = new HashMap<PsiType, PsiMethod>();
      Map<PsiType, PsiMethod> getters = new HashMap<PsiType, PsiMethod>();
      for (PsiMethod method : parcel.getMethods()) {
        if (!myIgnoredMethods.contains(method.getName())) {
          if (isSimpleWrite(method) || isWriteWithParcelableFlags(method)) {
            PsiType type = method.getParameterList().getParameters()[0].getType();
            setters.put(type, method);
          }
          else if (isSimpleRead(method)) {
            PsiType type = method.getReturnType();
            getters.put(type, method);
          }
        }
      }
      for (PsiType type : getters.keySet()) {
        PsiType setterType = getTypicalSetterType(type);
        PsiMethod getter = getters.get(type);
        PsiMethod setter = setters.get(setterType);
        if (getter != null && setter != null) {
          FieldPersistence persistence;
          if (isSimpleWrite(setter)) {
            persistence = new SimpleFieldPersistence(setter.getName(), getter.getName());
          } else if (isWriteWithParcelableFlags(setter)) {
            persistence = new SimpleWithFlagsFieldPersistence(setter.getName(), getter.getName());
          } else {
            continue;
          }
          myPersistenceMap.put(type, persistence);
          myPersistenceMap.put(setterType, persistence);
        }
      }
      myPersistenceMap.put(myTType, new ParcelableFieldPersistence());
      myPersistenceMap.put(myTArrayType, new EfficientParcelableArrayFieldPersistence());
      myPersistenceMap.put(myTListType, new EfficientParcelableListFieldPersistence());
    }

    @NotNull
    private PsiType getTypicalSetterType(@NotNull PsiType type) {
      PsiType elemType = getListElementType(type);
      return elemType == null ? type : myFactory.createType(myList, elemType);
    }

    @Nullable
    private static PsiType getListElementType(@NotNull PsiType type) {
      if (type instanceof PsiClassReferenceType) {
        PsiClassReferenceType refType = (PsiClassReferenceType)type;
        PsiType[] elemTypes = refType.getParameters();
        if (elemTypes.length == 1 &&
                (type.getCanonicalText().startsWith(JAVA_UTIL_LIST) || type.getCanonicalText().startsWith(JAVA_UTIL_ARRAY_LIST))) {
          return elemTypes[0];
        }
      }
      return null;
    }

    private static boolean isSimpleWrite(@NotNull PsiMethod method) {
      return method.getName().startsWith("write") && method.getParameterList().getParametersCount() == 1;
    }

    private static boolean isWriteWithParcelableFlags(@NotNull PsiMethod method) {
      if (!method.getName().startsWith("write") || method.getParameterList().getParametersCount() != 2) {
        return false;
      }
      PsiParameter param = method.getParameterList().getParameters()[1];
      return param.getType().equals(PsiType.INT) && param.getName().equals("parcelableFlags");
    }

    private static boolean isSimpleRead(@NotNull PsiMethod method) {
      return (method.getName().startsWith("read") || method.getName().startsWith("create")) &&
              method.getParameterList().getParametersCount() == 0;
    }

    private interface FieldPersistence {
      /**
       * Format the code for saving fieldName in a Parcel.
       */
      String[] formatWrite(@NotNull PsiField field, @NotNull String parcelVariableName, @NotNull String flagsVariableName);

      /**
       * Format the code for reading fieldName from a Parcel.
       */
      String[] formatRead(@NotNull PsiField field, @NotNull String parcelVariableName);
    }

    private static class SimpleFieldPersistence implements FieldPersistence {
      protected String myWriteMethodName;
      protected String myReadMethodName;

      SimpleFieldPersistence(@NotNull String writeMethod, @NotNull String readMethod) {
        myWriteMethodName = writeMethod;
        myReadMethodName = readMethod;
      }

      @Override
      public String[] formatWrite(@NotNull PsiField field, @NotNull String parcelVariableName, @NotNull String flagsVariableName) {
        return new String[]{String.format("%1$s.%2$s(%3$s);\n", parcelVariableName, myWriteMethodName, field.getName())};
      }

      @Override
      public String[] formatRead(@NotNull PsiField field, @NotNull String parcelVariableName) {
        return new String[]{String.format("%1$s = %2$s.%3$s();\n", field.getName(), parcelVariableName, myReadMethodName)};
      }
    }

    private static class SimpleWithFlagsFieldPersistence extends SimpleFieldPersistence {
      SimpleWithFlagsFieldPersistence(@NotNull String writeMethod, @NotNull String readMethod) {
        super(writeMethod, readMethod);
      }

      @Override
      public String[] formatWrite(@NotNull PsiField field, @NotNull String parcelVariableName, @NotNull String flagsVariableName) {
        return new String[]{
                String.format("%1$s.%2$s(%3$s, %4$s);\n", parcelVariableName, myWriteMethodName, field.getName(), flagsVariableName)
        };
      }
    }

    private static class ParcelableFieldPersistence implements FieldPersistence {
      @Override
      public String[] formatWrite(@NotNull PsiField field, @NotNull String parcelVariableName, @NotNull String flagsVariableName) {
        return new String[]{
                String.format("%1$s.writeParcelable(%2$s, %3$s);\n", parcelVariableName, field.getName(), flagsVariableName)
        };
      }

      @Override
      public String[] formatRead(@NotNull PsiField field, @NotNull String parcelVariableName) {
        return new String[]{
                String.format("%1$s = %2$s.readParcelable(%3$s.class.getClassLoader());\n",
                        field.getName(), parcelVariableName, field.getType().getCanonicalText())
        };
      }
    }

    private static class EfficientParcelableArrayFieldPersistence implements FieldPersistence {
      @Override
      public String[] formatWrite(@NotNull PsiField field, @NotNull String parcelVariableName, @NotNull String flagsVariableName) {
        return new String[]{String.format("%1$s.writeTypedArray(%2$s, %3$s);\n", parcelVariableName, field.getName(), flagsVariableName)};
      }

      @Override
      public String[] formatRead(@NotNull PsiField field, @NotNull String parcelVariableName) {
        String typeName = field.getType().getDeepComponentType().getCanonicalText();
        return new String[]{String.format("%1$s = %2$s.createTypedArray(%3$s.CREATOR);\n", field.getName(), parcelVariableName, typeName)};
      }
    }

    private static class EfficientParcelableListFieldPersistence implements FieldPersistence {
      @Override
      public String[] formatWrite(@NotNull PsiField field, @NotNull String parcelVariableName, @NotNull String flagsVariableName) {
        return new String[]{
                String.format("%1$s.writeTypedList(%2$s);\n", parcelVariableName, field.getName()),
        };
      }

      @Override
      public String[] formatRead(@NotNull PsiField field, @NotNull String parcelVariableName) {
        PsiType elemType = getListElementType(field.getType());
        assert elemType != null;
        return new String[]{
                String.format("%1$s = %2$s.createTypedArrayList(%3$s.CREATOR);\n",
                        field.getName(), parcelVariableName, elemType.getCanonicalText())
        };
      }
    }
  }
}
