/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.actions;

import com.android.SdkConstants;
import com.android.tools.idea.rendering.ImageUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ConvertToNinePatchAction extends AnAction {
  public ConvertToNinePatchAction() {
    super(AndroidBundle.message("android.9patch.creator.title"));
  }

  @Override
  public void update(AnActionEvent e) {
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    final boolean isPng = isPngFile(file);
    e.getPresentation().setEnabled(isPng);
    e.getPresentation().setVisible(isPng);
  }

  @Contract("null -> false")
  private static boolean isPngFile(@Nullable VirtualFile file) {
    return file != null && SdkConstants.EXT_PNG.equalsIgnoreCase(file.getExtension())
                        && !StringUtil.endsWithIgnoreCase(file.getName(), SdkConstants.DOT_9PNG);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final VirtualFile pngFile = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (!isPngFile(pngFile)) {
      return;
    }

    FileSaverDescriptor descriptor =
      new FileSaverDescriptor(AndroidBundle.message("android.9patch.creator.save.title"), "", SdkConstants.EXT_PNG);
    FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, (Project)null);
    VirtualFileWrapper fileWrapper = saveFileDialog.save(pngFile.getParent(),
                                                         pngFile.getNameWithoutExtension().concat(SdkConstants.DOT_9PNG));
    if (fileWrapper == null) {
      return;
    }

    final File patchFile = fileWrapper.getFile();
    new Task.Modal(null, "Creating 9-Patch File", false) {
      private IOException myException;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        try {
          BufferedImage pngImage = ImageIO.read(VfsUtilCore.virtualToIoFile(pngFile));
          BufferedImage patchImage = ImageUtils.addMargin(pngImage, 1);
          ImageIO.write(patchImage, SdkConstants.EXT_PNG, patchFile);
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(patchFile);
        }
        catch (IOException e) {
          myException = e;
        }
      }

      @Override
      public void onSuccess() {
        if (myException != null) {
          Messages.showErrorDialog(AndroidBundle.message("android.9patch.creator.error", myException.getMessage()),
                                   AndroidBundle.message("android.9patch.creator.error.title"));
        }
      }
    }.queue();
  }
}
