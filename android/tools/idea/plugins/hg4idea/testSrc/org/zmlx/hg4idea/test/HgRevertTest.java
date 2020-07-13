package org.zmlx.hg4idea.test;

import org.testng.annotations.Test;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.command.HgCatCommand;
import org.zmlx.hg4idea.command.HgRevertCommand;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collections;

import static org.testng.Assert.assertEquals;

public class HgRevertTest extends HgSingleUserTest {
  @Test
  public void testRevertToCurrentRevision() throws Exception {
    fillFile(myProjectDir, new String[]{"file.txt"}, "initial contents");
    runHgOnProjectRepo("add", ".");
    runHgOnProjectRepo("commit", "-m", "initial contents");

    fillFile(myProjectDir, new String[]{"file.txt"}, "new contents");

    HgRevertCommand revertCommand = new HgRevertCommand(myProject);
    revertCommand.execute(myRepo.getDir(), Collections.singleton(new File(myProjectDir, "file.txt").getPath()), null, false);

    HgCatCommand catCommand = new HgCatCommand(myProject);
    String content = catCommand.execute(getHgFile("file.txt"), null, Charset.defaultCharset());

    assertEquals(content, "initial contents");
  }


  @Test
  public void testRevertToGivenRevision() throws Exception {
    fillFile(myProjectDir, new String[]{"file.txt"}, "initial contents");
    runHgOnProjectRepo("add", ".");
    runHgOnProjectRepo("commit", "-m", "initial contents");

    fillFile(myProjectDir, new String[]{"file.txt"}, "new contents");
    runHgOnProjectRepo("commit", "-m", "new contents");

    HgRevertCommand revertCommand = new HgRevertCommand(myProject);
    revertCommand.execute(myRepo.getDir(), Collections.singleton(new File(myProjectDir, "file.txt").getPath()),
                          HgRevisionNumber.getLocalInstance("0"), false);

    HgCatCommand catCommand = new HgCatCommand(myProject);
    String content = catCommand.execute(getHgFile("file.txt"), HgRevisionNumber.getLocalInstance("0"), Charset.defaultCharset());

    assertEquals(content, "initial contents");
  }

}
