/*
 * Copyright 2015-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.intellij.ideabuck.file;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.stubs.BinaryFileStubBuilder;
import com.intellij.psi.stubs.BinaryFileStubBuilders;
import com.intellij.psi.stubs.Stub;
import com.intellij.util.indexing.FileContent;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BuckFileUtil {

  private static final String DEFAULT_BUILD_FILE = "BUCK";
  private static final String SAMPLE_BUCK_FILE =
      "# Thanks for installing Buck Plugin for IDEA!\n" +
      "android_library(\n" +
      "  name = 'bar',\n" +
      "  srcs = glob(['**/*.java']),\n" +
      "  deps = [\n" +
      "    '//android_res/com/foo/interfaces:res',\n" +
      "    '//android_res/com/foo/common/strings:res',\n" +
      "    '//android_res/com/foo/custom:res,'\n" +
      "  ],\n" +
      "  visibility = [\n" +
      "    'PUBLIC',\n" +
      "  ],\n" +
      ")\n" +
      "\n" +
      "project_config(\n" +
      "  src_target = ':bar',\n" +
      ")\n";

  private BuckFileUtil() {
  }

  public static String getBuildFileName() {
    // TODO(#7908500): Read from ".buckconfig".
    return DEFAULT_BUILD_FILE;
  }

  public static String getSampleBuckFile() {
    return SAMPLE_BUCK_FILE;
  }

  public static VirtualFile getBuckFile(VirtualFile virtualFile) {

    if (virtualFile == null) {
      return null;
    }

    VirtualFile parent = virtualFile.getParent();
    if (parent == null) {
      return null;
    }
    VirtualFile buckFile = parent.findChild(BuckFileUtil.getBuildFileName());
    while ((buckFile == null && parent != null) || (buckFile != null && buckFile.isDirectory())) {
      buckFile = parent.findChild(BuckFileUtil.getBuildFileName());
      parent = parent.getParent();
    }
    return buckFile;
  }

  public static void setBuckFileType() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        FileTypeManager fileTypeManager = FileTypeManagerImpl.getInstance();

        FileType fileType = fileTypeManager
            .getFileTypeByFileName(BuckFileType.INSTANCE.getDefaultExtension());

        // Remove all FileType associations for BUCK files that are not BuckFileType
        while (!(fileType instanceof  BuckFileType || fileType instanceof UnknownFileType)) {
          List<FileNameMatcher> fileNameMatchers = fileTypeManager.getAssociations(fileType);

          for (FileNameMatcher fileNameMatcher : fileNameMatchers) {
            if (fileNameMatcher.accept(BuckFileType.INSTANCE.getDefaultExtension())) {
              fileTypeManager.removeAssociation(fileType, fileNameMatcher);
            }
          }

          fileType = fileTypeManager
              .getFileTypeByFileName(BuckFileType.INSTANCE.getDefaultExtension());
        }

        // Use a simple BinaryFileStubBuilder, that doesn't offer stubbing
        BinaryFileStubBuilders.INSTANCE.addExplicitExtension(
            fileType,
            new BinaryFileStubBuilder() {
              @Override
              public boolean acceptsFile(VirtualFile virtualFile) {
                return false;
              }

              @Nullable
              @Override
              public Stub buildStubTree(FileContent fileContent) {
                return null;
              }

              @Override
              public int getStubVersion() {
                return 0;
              }
            });
      }
    });
  }
}
