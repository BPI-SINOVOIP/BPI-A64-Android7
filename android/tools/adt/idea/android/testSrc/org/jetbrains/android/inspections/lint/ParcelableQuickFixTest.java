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

import com.intellij.diff.comparison.ComparisonManager;
import com.intellij.diff.comparison.ComparisonManagerImpl;
import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.DumbProgressIndicator;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.jetbrains.android.inspections.lint.ParcelableQuickFix.Operation.IMPLEMENT;
import static org.jetbrains.android.inspections.lint.ParcelableQuickFix.Operation.REIMPLEMENT;
import static org.jetbrains.android.inspections.lint.ParcelableQuickFix.Operation.REMOVE;

public class ParcelableQuickFixTest extends AndroidTestCase {
  private static DumbProgressIndicator INDICATOR = DumbProgressIndicator.INSTANCE;
  private static ComparisonManager myComparisonManager = new ComparisonManagerImpl();

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  private void doTestApply(@NotNull ParcelableQuickFix.Operation operation,
                           @NotNull String source,
                           @NotNull String expected,
                           @NotNull String className) {
    PsiFile file = myFixture.addFileToProject(String.format("src/com/example/%s.java", className), source);
    final PsiIdentifier identifier = findClassIdentifier(file, className);
    final ParcelableQuickFix fix = new ParcelableQuickFix("Fix Parcelable", operation);
    assertTrue(fix.isApplicable(identifier, identifier, AndroidQuickfixContexts.DesignerContext.TYPE));
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        fix.apply(identifier, identifier, AndroidQuickfixContexts.DesignerContext.getInstance());
      }
    }.execute();
    Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    assert document != null;
    String actual = document.getText();
    String diff = lineDiff(actual, expected);
    assertTrue(diff, diff.isEmpty());
  }

  private static PsiIdentifier findClassIdentifier(PsiFile file, String className) {
    List<PsiClass> classes = PsiTreeUtil.getChildrenOfTypeAsList(file, PsiClass.class);
    PsiClass testClass = null;
    for (PsiClass cls : classes) {
      if (cls.getName().equals(className)) {
        testClass = cls;
        break;
      }
    }
    assertNotNull(testClass);
    return PsiTreeUtil.getChildOfType(testClass, PsiIdentifier.class);
  }

  private static String lineDiff(String actual, String expected) {
    List<LineFragment> fragments = myComparisonManager.compareLines(actual, expected, ComparisonPolicy.DEFAULT, INDICATOR);
    if (fragments.isEmpty()) {
      return "";
    }
    StringBuilder diff = new StringBuilder();
    diff.append("\n");
    for (LineFragment fragment : fragments) {
      diff.append(String.format("Expected in line: %s >>>\n", fragment.getStartLine2()));
      diff.append(expected.substring(fragment.getStartOffset2(), fragment.getEndOffset2()));
      diff.append("Actual: >>>\n");
      diff.append(actual.substring(fragment.getStartOffset1(), fragment.getEndOffset1()));
      diff.append(">>>\n\n");
    }
    return diff.toString();
  }

  public void testIsApplicable() throws Exception {
    ParcelableQuickFix fix = new ParcelableQuickFix("Fix Parcelable", IMPLEMENT);

    PsiFile file = myFixture.addFileToProject("src/com/example/Simple.java", SIMPLE_SOURCE);
    PsiIdentifier identifier = findClassIdentifier(file, "Simple");
    assertTrue(fix.isApplicable(identifier, identifier, AndroidQuickfixContexts.DesignerContext.TYPE));

    file = myFixture.addFileToProject("src/com/expected/Simple.java", SIMPLE_EXPECTED);
    identifier = findClassIdentifier(file, "Simple");
    assertFalse(fix.isApplicable(identifier, identifier, AndroidQuickfixContexts.DesignerContext.TYPE));
  }

  // ------------------------------------------------------------------------------ //

  @Language("JAVA")
  private static final String SIMPLE_SOURCE =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Parcel;\n" +
                  "import android.os.Parcelable;\n" +
                  "\n" +
                  "public class Simple implements Parcelable {\n" +
                  "    private final String name;\n" +
                  "    private final int age;\n" +
                  "    private Simple manager;\n" +
                  "\n" +
                  "    public Simple(String name, int age) {\n" +
                  "        this.name = name;\n" +
                  "        this.age = age;\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public int describeContents() {\n" +
                  "        return 0;\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public void writeToParcel(Parcel dest, int flags) {\n" +
                  "\n" +
                  "    }\n" +
                  "}\n";

  @Language("JAVA")
  private static final String SIMPLE_EXPECTED =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Parcel;\n" +
                  "import android.os.Parcelable;\n" +
                  "\n" +
                  "public class Simple implements Parcelable {\n" +
                  "    private final String name;\n" +
                  "    private final int age;\n" +
                  "    private Simple manager;\n" +
                  "\n" +
                  "    public Simple(String name, int age) {\n" +
                  "        this.name = name;\n" +
                  "        this.age = age;\n" +
                  "    }\n" +
                  "\n" +
                  "    protected Simple(Parcel in) {\n" +
                  "        name = in.readString();\n" +
                  "        age = in.readInt();\n" +
                  "        manager = in.readParcelable(Simple.class.getClassLoader());\n" +
                  "    }\n" +
                  "\n" +
                  "    public static final Creator<Simple> CREATOR = new Creator<Simple>() {\n" +
                  "        @Override\n" +
                  "        public Simple createFromParcel(Parcel in) {\n" +
                  "            return new Simple(in);\n" +
                  "        }\n" +
                  "\n" +
                  "        @Override\n" +
                  "        public Simple[] newArray(int size) {\n" +
                  "            return new Simple[size];\n" +
                  "        }\n" +
                  "    };\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public int describeContents() {\n" +
                  "        return 0;\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public void writeToParcel(Parcel dest, int flags) {\n" +
                  "        dest.writeString(name);\n" +
                  "        dest.writeInt(age);\n" +
                  "        dest.writeParcelable(manager, flags);\n" +
                  "    }\n" +
                  "}\n";

  public void testSimple() throws Exception {
    doTestApply(IMPLEMENT, SIMPLE_SOURCE, SIMPLE_EXPECTED, "Simple");
  }

  // ------------------------------------------------------------------------------ //

  @Language("JAVA")
  private static final String ALLTYPES_SOURCE =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Bundle;\n" +
                  "import android.os.IBinder;\n" +
                  "import android.os.Parcel;\n" +
                  "import android.os.Parcelable;\n" +
                  "import android.os.PersistableBundle;\n" +
                  "import android.util.Size;\n" +
                  "import android.util.SizeF;\n" +
                  "import android.util.SparseBooleanArray;\n" +
                  "\n" +
                  "import java.util.ArrayList;\n" +
                  "import java.util.List;\n" +
                  "\n" +
                  "public class AllTypes implements Parcelable {\n" +
                  "    // Primitives:\n" +
                  "    private byte myByte;\n" +
                  "    private double myDouble;\n" +
                  "    private float myFloat;\n" +
                  "    private int myInt;\n" +
                  "    private long myLong;\n" +
                  "    private String myString;\n" +
                  "\n" +
                  "    // Primitive Arrays:\n" +
                  "    private boolean[] myBooleans;\n" +
                  "    private byte[] myBytes;\n" +
                  "    private char[] myChars;\n" +
                  "    private double[] myDoubles;\n" +
                  "    private float[] myFloats;\n" +
                  "    private int[] myInts;\n" +
                  "    private long[] myLongs;\n" +
                  "    private String[] myStrings;\n" +
                  "    private SparseBooleanArray mySparseBooleans;\n" +
                  "\n" +
                  "    // Primitive Lists:\n" +
                  "    private List<String> myStringList;\n" +
                  "    private ArrayList<String> myStringArrayList;\n" +
                  "\n" +
                  "    // Known composites:\n" +
                  "    private Size mySize;\n" +
                  "    private SizeF mySizeF;\n" +
                  "\n" +
                  "    // Parcelables:\n" +
                  "    private AllTypes myReference;\n" +
                  "    private AllTypes[] myReferences;\n" +
                  "    private List<AllTypes> myReferenceList;\n" +
                  "    private ArrayList<AllTypes> myReferenceArrayList;\n" +
                  "\n" +
                  "    // Bundles:\n" +
                  "    private Bundle myBundle;\n" +
                  "    private PersistableBundle myPersistableBundle;\n" +
                  "\n" +
                  "    // Active Objects:\n" +
                  "    private IBinder myBinder;\n" +
                  "    private IBinder[] myBinders;\n" +
                  "    private List<IBinder> myBinderList;\n" +
                  "    private List<IBinder> myBinderArrayList;\n" +
                  "\n" +
                  "}\n";

  @Language("JAVA")
  private static final String ALLTYPES_EXPECTED =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Bundle;\n" +
                  "import android.os.IBinder;\n" +
                  "import android.os.Parcel;\n" +
                  "import android.os.Parcelable;\n" +
                  "import android.os.PersistableBundle;\n" +
                  "import android.util.Size;\n" +
                  "import android.util.SizeF;\n" +
                  "import android.util.SparseBooleanArray;\n" +
                  "\n" +
                  "import java.util.ArrayList;\n" +
                  "import java.util.List;\n" +
                  "\n" +
                  "public class AllTypes implements Parcelable {\n" +
                  "    // Primitives:\n" +
                  "    private byte myByte;\n" +
                  "    private double myDouble;\n" +
                  "    private float myFloat;\n" +
                  "    private int myInt;\n" +
                  "    private long myLong;\n" +
                  "    private String myString;\n" +
                  "\n" +
                  "    // Primitive Arrays:\n" +
                  "    private boolean[] myBooleans;\n" +
                  "    private byte[] myBytes;\n" +
                  "    private char[] myChars;\n" +
                  "    private double[] myDoubles;\n" +
                  "    private float[] myFloats;\n" +
                  "    private int[] myInts;\n" +
                  "    private long[] myLongs;\n" +
                  "    private String[] myStrings;\n" +
                  "    private SparseBooleanArray mySparseBooleans;\n" +
                  "\n" +
                  "    // Primitive Lists:\n" +
                  "    private List<String> myStringList;\n" +
                  "    private ArrayList<String> myStringArrayList;\n" +
                  "\n" +
                  "    // Known composites:\n" +
                  "    private Size mySize;\n" +
                  "    private SizeF mySizeF;\n" +
                  "\n" +
                  "    // Parcelables:\n" +
                  "    private AllTypes myReference;\n" +
                  "    private AllTypes[] myReferences;\n" +
                  "    private List<AllTypes> myReferenceList;\n" +
                  "    private ArrayList<AllTypes> myReferenceArrayList;\n" +
                  "\n" +
                  "    // Bundles:\n" +
                  "    private Bundle myBundle;\n" +
                  "    private PersistableBundle myPersistableBundle;\n" +
                  "\n" +
                  "    // Active Objects:\n" +
                  "    private IBinder myBinder;\n" +
                  "    private IBinder[] myBinders;\n" +
                  "    private List<IBinder> myBinderList;\n" +
                  "    private List<IBinder> myBinderArrayList;\n" +
                  "\n" +
                  "    protected AllTypes(Parcel in) {\n" +
                  "        myByte = in.readByte();\n" +
                  "        myDouble = in.readDouble();\n" +
                  "        myFloat = in.readFloat();\n" +
                  "        myInt = in.readInt();\n" +
                  "        myLong = in.readLong();\n" +
                  "        myString = in.readString();\n" +
                  "        myBooleans = in.createBooleanArray();\n" +
                  "        myBytes = in.createByteArray();\n" +
                  "        myChars = in.createCharArray();\n" +
                  "        myDoubles = in.createDoubleArray();\n" +
                  "        myFloats = in.createFloatArray();\n" +
                  "        myInts = in.createIntArray();\n" +
                  "        myLongs = in.createLongArray();\n" +
                  "        myStrings = in.createStringArray();\n" +
                  "        mySparseBooleans = in.readSparseBooleanArray();\n" +
                  "        myStringList = in.createStringArrayList();\n" +
                  "        myStringArrayList = in.createStringArrayList();\n" +
                  "        mySize = in.readSize();\n" +
                  "        mySizeF = in.readSizeF();\n" +
                  "        myReference = in.readParcelable(AllTypes.class.getClassLoader());\n" +
                  "        myReferences = in.createTypedArray(AllTypes.CREATOR);\n" +
                  "        myReferenceList = in.createTypedArrayList(AllTypes.CREATOR);\n" +
                  "        myReferenceArrayList = in.createTypedArrayList(AllTypes.CREATOR);\n" +
                  "        myBundle = in.readBundle();\n" +
                  "        myPersistableBundle = in.readPersistableBundle();\n" +
                  "        myBinder = in.readStrongBinder();\n" +
                  "        myBinders = in.createBinderArray();\n" +
                  "        myBinderList = in.createBinderArrayList();\n" +
                  "        myBinderArrayList = in.createBinderArrayList();\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public void writeToParcel(Parcel dest, int flags) {\n" +
                  "        dest.writeByte(myByte);\n" +
                  "        dest.writeDouble(myDouble);\n" +
                  "        dest.writeFloat(myFloat);\n" +
                  "        dest.writeInt(myInt);\n" +
                  "        dest.writeLong(myLong);\n" +
                  "        dest.writeString(myString);\n" +
                  "        dest.writeBooleanArray(myBooleans);\n" +
                  "        dest.writeByteArray(myBytes);\n" +
                  "        dest.writeCharArray(myChars);\n" +
                  "        dest.writeDoubleArray(myDoubles);\n" +
                  "        dest.writeFloatArray(myFloats);\n" +
                  "        dest.writeIntArray(myInts);\n" +
                  "        dest.writeLongArray(myLongs);\n" +
                  "        dest.writeStringArray(myStrings);\n" +
                  "        dest.writeSparseBooleanArray(mySparseBooleans);\n" +
                  "        dest.writeStringList(myStringList);\n" +
                  "        dest.writeStringList(myStringArrayList);\n" +
                  "        dest.writeSize(mySize);\n" +
                  "        dest.writeSizeF(mySizeF);\n" +
                  "        dest.writeParcelable(myReference, flags);\n" +
                  "        dest.writeTypedArray(myReferences, flags);\n" +
                  "        dest.writeTypedList(myReferenceList);\n" +
                  "        dest.writeTypedList(myReferenceArrayList);\n" +
                  "        dest.writeBundle(myBundle);\n" +
                  "        dest.writePersistableBundle(myPersistableBundle);\n" +
                  "        dest.writeStrongBinder(myBinder);\n" +
                  "        dest.writeBinderArray(myBinders);\n" +
                  "        dest.writeBinderList(myBinderList);\n" +
                  "        dest.writeBinderList(myBinderArrayList);\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public int describeContents() {\n" +
                  "        return 0;\n" +
                  "    }\n" +
                  "\n" +
                  "    public static final Creator<AllTypes> CREATOR = new Creator<AllTypes>() {\n" +
                  "        @Override\n" +
                  "        public AllTypes createFromParcel(Parcel in) {\n" +
                  "            return new AllTypes(in);\n" +
                  "        }\n" +
                  "\n" +
                  "        @Override\n" +
                  "        public AllTypes[] newArray(int size) {\n" +
                  "            return new AllTypes[size];\n" +
                  "        }\n" +
                  "    };\n" +
                  "}\n";

  public void testAllTypes() throws Exception {
    doTestApply(IMPLEMENT, ALLTYPES_SOURCE, ALLTYPES_EXPECTED, "AllTypes");
  }

  // ------------------------------------------------------------------------------ //

  @Language("JAVA")
  private static final String LONG_CLASS_NAMES_SOURCE =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Parcelable;\n" +
                  "\n" +
                  "public class LongClassNames implements Parcelable {\n" +
                  "    private com.example.Simple simple;\n" +
                  "    private com.example.Simple[] simples;\n" +
                  "    private java.util.List<com.example.Simple> simpleList;\n" +
                  "    private java.util.ArrayList<com.example.Simple> simpleArrayList;\n" +
                  "\n" +
                  "    private static class Parcel{}\n" +
                  "    private static class Parcelable{}\n" +
                  "    private static class Creator{}\n" +
                  "    private static class Simple{}\n" +
                  "}";

  @Language("JAVA")
  private static final String LONG_CLASS_NAMES_EXPECTED =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Parcelable;\n" +
                  "\n" +
                  "public class LongClassNames implements Parcelable {\n" +
                  "    private com.example.Simple simple;\n" +
                  "    private com.example.Simple[] simples;\n" +
                  "    private java.util.List<com.example.Simple> simpleList;\n" +
                  "    private java.util.ArrayList<com.example.Simple> simpleArrayList;\n" +
                  "\n" +
                  "    private static class Parcel{}\n" +
                  "    private static class Parcelable{}\n" +
                  "    private static class Creator{}\n" +
                  "    private static class Simple{}\n" +
                  "\n" +
                  "    protected LongClassNames(android.os.Parcel in) {\n" +
                  "        simple = in.readParcelable(com.example.Simple.class.getClassLoader());\n" +
                  "        simples = in.createTypedArray(com.example.Simple.CREATOR);\n" +
                  "        simpleList = in.createTypedArrayList(com.example.Simple.CREATOR);\n" +
                  "        simpleArrayList = in.createTypedArrayList(com.example.Simple.CREATOR);\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public void writeToParcel(android.os.Parcel dest, int flags) {\n" +
                  "        dest.writeParcelable(simple, flags);\n" +
                  "        dest.writeTypedArray(simples, flags);\n" +
                  "        dest.writeTypedList(simpleList);\n" +
                  "        dest.writeTypedList(simpleArrayList);\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public int describeContents() {\n" +
                  "        return 0;\n" +
                  "    }\n" +
                  "\n" +
                  "    public static final android.os.Parcelable.Creator<LongClassNames> CREATOR = new android.os.Parcelable.Creator<LongClassNames>() {\n" +
                  "        @Override\n" +
                  "        public LongClassNames createFromParcel(android.os.Parcel in) {\n" +
                  "            return new LongClassNames(in);\n" +
                  "        }\n" +
                  "\n" +
                  "        @Override\n" +
                  "        public LongClassNames[] newArray(int size) {\n" +
                  "            return new LongClassNames[size];\n" +
                  "        }\n" +
                  "    };\n" +
                  "}";

  public void testLongClassNames() throws Exception {
    myFixture.addFileToProject("src/com/example/Simple.java", SIMPLE_SOURCE);
    doTestApply(IMPLEMENT, LONG_CLASS_NAMES_SOURCE, LONG_CLASS_NAMES_EXPECTED, "LongClassNames");
  }

  // ------------------------------------------------------------------------------ //

  @Language("JAVA")
  private static final String REMOVAL_SOURCE =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Parcel;\n" +
                  "import android.os.Parcelable;\n" +
                  "\n" +
                  "public class Removal implements Parcelable {\n" +
                  "    private final String name;\n" +
                  "    private final int age;\n" +
                  "    private Removal manager;\n" +
                  "\n" +
                  "    public Removal(String name, int age) {\n" +
                  "        this.name = name;\n" +
                  "        this.age = age;\n" +
                  "    }\n" +
                  "\n" +
                  "    private Removal(Parcel in) {\n" +
                  "        name = in.readString();\n" +
                  "        age = in.readInt();\n" +
                  "        manager = Removal.CREATOR.createFromParcel(in);\n" +
                  "    }\n" +
                  "\n" +
                  "    public static final Creator<Removal> CREATOR = new Creator<Removal>() {\n" +
                  "        @Override\n" +
                  "        public Removal createFromParcel(Parcel in) {\n" +
                  "            Removal s = new Removal(in);\n" +
                  "        }\n" +
                  "\n" +
                  "        @Override\n" +
                  "        public Removal[] newArray(int size) {\n" +
                  "            return new Simple[size];\n" +
                  "        }\n" +
                  "    };\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public int describeContents() {\n" +
                  "        return 0;\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public void writeToParcel(Parcel dest, int flags) {\n" +
                  "        dest.writeString(name);\n" +
                  "        dest.writeInt(age);\n" +
                  "        manager.writeToParcel(dest, flags);\n" +
                  "    }\n" +
                  "}\n";

  @Language("JAVA")
  private static final String REMOVAL_EXPECTED =
          "package com.example;\n" +
                  "\n" +
                  "public class Removal {\n" +
                  "    private final String name;\n" +
                  "    private final int age;\n" +
                  "    private Removal manager;\n" +
                  "\n" +
                  "    public Removal(String name, int age) {\n" +
                  "        this.name = name;\n" +
                  "        this.age = age;\n" +
                  "    }\n" +
                  "\n" +
                  "}\n";

  public void testRemoval() throws Exception {
    doTestApply(REMOVE, REMOVAL_SOURCE, REMOVAL_EXPECTED, "Removal");
  }

  // ------------------------------------------------------------------------------ //

  @Language("JAVA")
  private static final String REDO_SOURCE =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Parcel;\n" +
                  "import android.os.Parcelable;\n" +
                  "\n" +
                  "public class Redo implements Parcelable {\n" +
                  "    private final String name;\n" +
                  "    private final int age;\n" +
                  "    private Redo manager;\n" +
                  "\n" +
                  "    public Redo(String name, int age) {\n" +
                  "        this.name = name;\n" +
                  "        this.age = age;\n" +
                  "    }\n" +
                  "\n" +
                  "    private Redo(Parcel in) {\n" +
                  "        // not used \n" +
                  "        name = \"Wrong\";\n" +
                  "        age = in.readInt();\n" +
                  "        manager = Redo.CREATOR.createFromParcel(in);\n" +
                  "    }\n" +
                  "\n" +
                  "    public static final Creator<Redo> CREATOR = new Creator<Redo>() {\n" +
                  "        public Redo createFromParcel(Parcel in) {\n" +
                  "            int age = in.readInt();\n" +
                  "            String name = in.readValue().toString();\n" +
                  "            Redo s = new Redo(name, age);\n" +
                  "        }\n" +
                  "\n" +
                  "        public Redo[] newArray(int size) {\n" +
                  "            return new Redo[size];\n" +
                  "        }\n" +
                  "    };\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public int describeContents() {\n" +
                  "        return 0;\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public void writeToParcel(Parcel dest, int flags) {\n" +
                  "        dest.writeString(name);\n" +
                  "        dest.writeInt(age);\n" +
                  "    }\n" +
                  "}\n";

  @Language("JAVA")
  private static final String REDO_EXPECTED =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Parcel;\n" +
                  "import android.os.Parcelable;\n" +
                  "\n" +
                  "public class Redo implements Parcelable {\n" +
                  "    private final String name;\n" +
                  "    private final int age;\n" +
                  "    private Redo manager;\n" +
                  "\n" +
                  "    public Redo(String name, int age) {\n" +
                  "        this.name = name;\n" +
                  "        this.age = age;\n" +
                  "    }\n" +
                  "\n" +
                  "    protected Redo(Parcel in) {\n" +
                  "        name = in.readString();\n" +
                  "        age = in.readInt();\n" +
                  "        manager = in.readParcelable(Redo.class.getClassLoader());\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public void writeToParcel(Parcel dest, int flags) {\n" +
                  "        dest.writeString(name);\n" +
                  "        dest.writeInt(age);\n" +
                  "        dest.writeParcelable(manager, flags);\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public int describeContents() {\n" +
                  "        return 0;\n" +
                  "    }\n" +
                  "\n" +
                  "    public static final Creator<Redo> CREATOR = new Creator<Redo>() {\n" +
                  "        @Override\n" +
                  "        public Redo createFromParcel(Parcel in) {\n" +
                  "            return new Redo(in);\n" +
                  "        }\n" +
                  "\n" +
                  "        @Override\n" +
                  "        public Redo[] newArray(int size) {\n" +
                  "            return new Redo[size];\n" +
                  "        }\n" +
                  "    };\n" +
                  "}\n";

  public void testRedo() throws Exception {
    doTestApply(REIMPLEMENT, REDO_SOURCE, REDO_EXPECTED, "Redo");
  }

  // ------------------------------------------------------------------------------ //

  @Language("JAVA")
  private static final String INHERIT_SOURCE =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Parcel;\n" +
                  "import android.os.Parcelable;\n" +
                  "\n" +
                  "public class Inherit extends Simple implements Parcelable {\n" +
                  "    private final int startYear;\n" +
                  "\n" +
                  "    public Inherit(String name, int age, int startYear) {\n" +
                  "        super(name, age);\n" +
                  "        this.startYear = startYear;\n" +
                  "    }\n" +
                  "}\n";

  @Language("JAVA")
  private static final String INHERIT_EXPECTED =
          "package com.example;\n" +
                  "\n" +
                  "import android.os.Parcel;\n" +
                  "import android.os.Parcelable;\n" +
                  "\n" +
                  "public class Inherit extends Simple implements Parcelable {\n" +
                  "    private final int startYear;\n" +
                  "\n" +
                  "    public Inherit(String name, int age, int startYear) {\n" +
                  "        super(name, age);\n" +
                  "        this.startYear = startYear;\n" +
                  "    }\n" +
                  "\n" +
                  "    protected Inherit(Parcel in) {\n" +
                  "        super(in);\n" +
                  "        startYear = in.readInt();\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public void writeToParcel(Parcel dest, int flags) {\n" +
                  "        super.writeToParcel(dest, flags);\n" +
                  "        dest.writeInt(startYear);\n" +
                  "    }\n" +
                  "\n" +
                  "    @Override\n" +
                  "    public int describeContents() {\n" +
                  "        return 0;\n" +
                  "    }\n" +
                  "\n" +
                  "    public static final Creator<Inherit> CREATOR = new Creator<Inherit>() {\n" +
                  "        @Override\n" +
                  "        public Inherit createFromParcel(Parcel in) {\n" +
                  "            return new Inherit(in);\n" +
                  "        }\n" +
                  "\n" +
                  "        @Override\n" +
                  "        public Inherit[] newArray(int size) {\n" +
                  "            return new Inherit[size];\n" +
                  "        }\n" +
                  "    };\n" +
                  "}\n";

  public void testInheritance() throws Exception {
    myFixture.addFileToProject("src/com/expected/Simple.java", SIMPLE_EXPECTED);
    doTestApply(IMPLEMENT, INHERIT_SOURCE, INHERIT_EXPECTED, "Inherit");
  }
}
