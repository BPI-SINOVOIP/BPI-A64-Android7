package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.android.AndroidFindUsagesTest;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.inspections.AndroidDomInspection;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.android.AndroidTestCase.getDefaultPlatformDir;
import static org.jetbrains.android.AndroidTestCase.getDefaultTestSdkPath;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class AndroidLibraryProjectTest extends UsefulTestCase {
  @NonNls private static final String BASE_PATH = "libModule/";

  private Module myAppModule;
  private AndroidFacet myAppFacet;

  private Module myLibModule;
  private Module myLibGenModule;
  private AndroidFacet myLibFacet;

  protected JavaCodeInsightTestFixture myFixture;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public AndroidLibraryProjectTest() {
    IdeaTestCase.initPlatformPrefix();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder =
      IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
    myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.enableInspections(AndroidDomInspection.class);

    final JavaModuleFixtureBuilder appModuleBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    String appModuleDir = myFixture.getTempDirPath() + "/app";
    new File(appModuleDir).mkdir();
    AndroidTestCase.tuneModule(appModuleBuilder, appModuleDir);

    final JavaModuleFixtureBuilder libModuleBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    String libModuleDir = myFixture.getTempDirPath() + "/lib";
    new File(libModuleDir).mkdir();

    libModuleBuilder.addContentRoot(libModuleDir);
    new File(libModuleDir + "/src/").mkdir();
    libModuleBuilder.addSourceRoot("src");

    final JavaModuleFixtureBuilder libGenModuleBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    String libGenModule = myFixture.getTempDirPath() + "/lib/gen";
    new File(libGenModule).mkdir();
    libGenModuleBuilder.addSourceContentRoot(libGenModule);

    myFixture.setUp();
    myFixture.setTestDataPath(AndroidTestCase.getAbsoluteTestDataPath());

    myAppModule = appModuleBuilder.getFixture().getModule();
    myLibModule = libModuleBuilder.getFixture().getModule();
    myLibGenModule = libGenModuleBuilder.getFixture().getModule();

    // Manifest must exist before facet is added
    final VirtualFile manifest =
      myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
    final VirtualFile manifest2 =
      myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, "app/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    final VirtualFile manifest3 =
      myFixture.copyFileToProject(BASE_PATH + "LibAndroidManifest.xml", "lib/" + SdkConstants.FN_ANDROID_MANIFEST_XML);

    myAppFacet = AndroidTestCase.addAndroidFacet(myAppModule, getDefaultTestSdkPath(), getDefaultPlatformDir());
    myLibFacet = AndroidTestCase.addAndroidFacet(myLibModule, getDefaultTestSdkPath(), getDefaultPlatformDir());
    myLibFacet.setLibraryProject(true);

    ModuleRootModificationUtil.addDependency(myAppModule, myLibModule);
    ModuleRootModificationUtil.addDependency(myLibModule, myLibGenModule);

    // Manifest files will be recreated using createInitialStructure or other custom code in each test case

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          manifest.delete(this);
          manifest2.delete(this);
          manifest3.delete(this);
        }
        catch (IOException e) {
          fail("Could not delete default manifest");
        }
      }
    });
  }

  private void createInitialStructure() {
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, "app/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(BASE_PATH + "LibAndroidManifest.xml", "lib/" + SdkConstants.FN_ANDROID_MANIFEST_XML);

    myFixture.copyDirectoryToProject("res", "app/res");
    myFixture.copyDirectoryToProject(BASE_PATH + "res", "lib/res");
  }

  @Override
  protected void tearDown() throws Exception {
    myAppModule = null;
    myLibModule = null;
    myLibGenModule = null;

    myAppFacet = null;
    myLibFacet = null;

    myFixture.tearDown();
    myFixture = null;

    super.tearDown();
  }

  public void testHighlighting() {
    createInitialStructure();
    String to = "app/res/layout/" + getTestName(true) + ".xml";
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", to);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, true);
  }

  public void testHighlighting1() {
    createInitialStructure();
    String to = "app/res/layout/" + getTestName(true) + ".xml";
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", to);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, true);
  }

  public void testHighlighting2() {
    myFixture.copyFileToProject(BASE_PATH + "LibAndroidManifest.xml", "lib/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    final VirtualFile manifestFile =
      myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", "app/" + SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyDirectoryToProject(BASE_PATH + "res", "lib/res");
    myFixture.configureFromExistingVirtualFile(manifestFile);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, false, true);
  }

  public void testJavaHighlighting() {
    createInitialStructure();
    myFixture.copyFileToProject(BASE_PATH + "LibR.java", "lib/src/p1/p2/lib/R.java");
    String to = "lib/src/p1/p2/lib" + getTestName(true) + ".java";
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", to);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.doHighlighting();
    myFixture.checkHighlighting(true, true, true);
  }

  private void doRename(final VirtualFile file, final String newName) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        try {
          file.rename(myFixture.getProject(), newName);
        }
        catch (IOException e) {
          new RuntimeException(e);
        }
      }
    });
  }

  public void testCompletion() {
    createInitialStructure();
    doTestCompletion();
  }

  public void testCompletion1() {
    createInitialStructure();
    doTestCompletion();
  }

  public void testCustomAttrCompletion() {
    createInitialStructure();
    myFixture.copyFileToProject(BASE_PATH + "LibView.java", "lib/src/p1/p2/lib/LibView.java");
    myFixture.copyFileToProject(BASE_PATH + "lib_attrs.xml", "lib/res/values/lib_attrs.xml");
    doTestCompletion();
  }

  private void doTestCompletion() {
    String to = "app/res/layout/" + getTestName(true) + ".xml";
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(true) + ".xml", to);
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(BASE_PATH + getTestName(true) + "_after.xml");
  }

  public void testJavaNavigation() throws Exception {
    createInitialStructure();
    myFixture.copyFileToProject("R.java", "app/src/p1/p2/R.java");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + getTestName(false) + ".java", "/app/src/p1/p2/Java.java");
    myFixture.configureFromExistingVirtualFile(file);

    PsiElement[] targets =
      GotoDeclarationAction.findAllTargetElements(myFixture.getProject(), myFixture.getEditor(), myFixture.getCaretOffset());
    assertNotNull(targets);
    assertEquals(1, targets.length);
    PsiElement targetElement = targets[0];
    assertInstanceOf(targetElement, PsiFile.class);
    assertEquals("main.xml", ((PsiFile)targetElement).getName());
  }

  public void testFileResourceFindUsages() throws Throwable {
    doFindUsagesTest("xml", "lib/res/layout/");
  }

  public void testFileResourceFindUsages1() throws Throwable {
    doFindUsagesTest("xml", "app/res/layout/");
  }

  public void testFileResourceFindUsagesFromJava() throws Throwable {
    doFindUsagesTest("java", "app/src/p1/p2/");
  }

  public void testFileResourceFindUsagesFromJava1() throws Throwable {
    boolean temp = myLibFacet.isLibraryProject();
    try {
      myLibFacet.setLibraryProject(true);
      doFindUsagesTest("java", "app/src/p1/p2/lib/");
    }
    finally {
      myLibFacet.setLibraryProject(temp);
    }
  }

  public void testFileResourceFindUsagesFromJava2() throws Throwable {
    doFindUsagesTest("java", "lib/src/p1/p2/lib/");
  }

  public void testValueResourceFindUsages() throws Throwable {
    doFindUsagesTest("xml", "lib/res/layout/");
  }

  public void testValueResourceFindUsages1() throws Throwable {
    doFindUsagesTest("xml", "app/res/layout/");
  }

  private void doFindUsagesTest(String extension, String dir) throws Throwable {
    createInitialStructure();
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass.java", "app/src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass1.java", "app/src/p1/p2/lib/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesClass1.java", "lib/src/p1/p2/lib/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "FindUsagesStyles.xml", "app/res/values/styles.xml");
    myFixture.copyFileToProject(BASE_PATH + "picture1.png", "lib/res/drawable/picture1.png");
    myFixture.copyFileToProject("R.java", "app/src/p1/p2/R.java");
    myFixture.copyFileToProject(BASE_PATH + "LibR.java", "lib/src/p1/p2/lib/R.java");
    Collection<UsageInfo> references = findCodeUsages(getTestName(false) + "." + extension, dir);
    assertEquals(buildFileList(references), 5, references.size());
  }

  private List<UsageInfo> findCodeUsages(String path, String dir) throws Throwable {
    String newFilePath = dir + path;
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + path, newFilePath);
    Collection<UsageInfo> usages = AndroidFindUsagesTest.findUsages(file, myFixture);
    List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (UsageInfo usage : usages) {
      if (!usage.isNonCodeUsage) {
        result.add(usage);
      }
    }
    return result;
  }

  private static String buildFileList(Collection<UsageInfo> infos) {
    final StringBuilder result = new StringBuilder();

    for (UsageInfo info : infos) {
      final PsiFile file = info.getFile();
      final VirtualFile vFile = file != null ? file.getVirtualFile() : null;
      final String path = vFile != null ? vFile.getPath() : "null";
      result.append(path).append('\n');
    }
    return result.toString();
  }
}
