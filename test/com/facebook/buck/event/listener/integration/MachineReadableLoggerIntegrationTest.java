/*
 * Copyright 2016-present Facebook, Inc.
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

package com.facebook.buck.event.listener.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.BuckConstant;
import com.google.common.base.Charsets;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class MachineReadableLoggerIntegrationTest {

  @Rule
  public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  public void testOutputForParsingAndInvocationEvents() throws Exception {
    ProjectWorkspace workspace = TestDataHelper.createProjectWorkspaceForScenario(
        this, "just_build", tmp);
    workspace.setUp();
    workspace.runBuckBuild("--just-build", "//:bar", "//:foo").assertSuccess();

    // The folder should have only one command.
    Path logDir = workspace.resolve("buck-out/log/");
    File[] commandLogDirectoriesList =
        (logDir.toFile()).listFiles(File::isDirectory);
    assertEquals(commandLogDirectoriesList.length, 1);

    // The build folder should have only one machine-readable log.
    File[] logfiles = commandLogDirectoriesList[0].listFiles(
        pathname -> pathname.getName().equals(BuckConstant.BUCK_MACHINE_LOG_FILE_NAME));
    assertEquals(logfiles.length, 1);

    String data = new String(Files.readAllBytes(logfiles[0].toPath()), Charsets.UTF_8);

    assertTrue("log contains ParseStarted.", data.contains("ParseStarted"));
    assertTrue("log contains ParseFinished.", data.contains("ParseFinished"));
    assertTrue("log contains InvocationInfo.", data.contains("InvocationInfo"));
    assertTrue("log contains ExitCode.", data.contains("ExitCode"));
  }

}
