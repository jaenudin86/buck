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

package com.facebook.buck.apple;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;
import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.step.ExecutionContext;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeProjectFilesystem;
import com.facebook.buck.testutil.TestConsole;
import com.facebook.buck.testutil.integration.TemporaryPaths;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.HumanReadableException;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.concurrent.Future;

public class ProvisioningProfileCopyStepTest {
  private Path testdataDir;
  private Path tempOutputDir;
  private Path outputFile;
  private Path xcentFile;
  private Path entitlementsFile;
  private ProjectFilesystem projectFilesystem;
  private ExecutionContext executionContext;
  private CodeSignIdentityStore codeSignIdentityStore;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public final TemporaryPaths tmp = new TemporaryPaths();

  @Before
  public void setUp() throws IOException {
    testdataDir = TestDataHelper.getTestDataDirectory(this).resolve("provisioning_profiles");
    projectFilesystem = new FakeProjectFilesystem(testdataDir);
    Files.walkFileTree(
        testdataDir,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            projectFilesystem.writeBytesToPath(
                Files.readAllBytes(file),
                projectFilesystem.resolve(file));
            return FileVisitResult.CONTINUE;
          }
        });
    tempOutputDir = tmp.getRoot();
    outputFile = tempOutputDir.resolve("embedded.mobileprovision");
    xcentFile = Paths.get("test.xcent");
    executionContext = TestExecutionContext.newInstance();
    codeSignIdentityStore =
        CodeSignIdentityStore.fromIdentities(ImmutableList.of());
    entitlementsFile = testdataDir.resolve("Entitlements.plist");
  }

  @Test
  public void testFailsWithInvalidEntitlementsPlist() throws Exception {
    assumeTrue(Platform.detect() == Platform.MACOS);
    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(startsWith("Malformed entitlement .plist: "));

    ProvisioningProfileCopyStep step = new ProvisioningProfileCopyStep(
        projectFilesystem,
        testdataDir.resolve("Info.plist"),
        Optional.empty(),
        Optional.of(testdataDir.resolve("Invalid.plist")),
        ProvisioningProfileStore.fromSearchPath(
            new DefaultProcessExecutor(new TestConsole()),
            testdataDir),
        outputFile,
        xcentFile,
        codeSignIdentityStore
    );

    step.execute(executionContext);
  }

  @Test
  public void testFailsWithInvalidInfoPlist() throws Exception {
    assumeTrue(Platform.detect() == Platform.MACOS);
    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(startsWith("Unable to get bundle ID from info.plist"));

    ProvisioningProfileCopyStep step = new ProvisioningProfileCopyStep(
        projectFilesystem,
        testdataDir.resolve("Invalid.plist"),
        Optional.empty(),
        Optional.empty(),
        ProvisioningProfileStore.fromSearchPath(
            new DefaultProcessExecutor(new TestConsole()),
            testdataDir),
        outputFile,
        xcentFile,
        codeSignIdentityStore
    );

    step.execute(executionContext);
  }

  @Test
  public void testFailsWithNoSuitableProfilesFound() throws Exception {
    assumeTrue(Platform.detect() == Platform.MACOS);
    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(
        "No valid non-expired provisioning profiles match for *.com.example.TestApp");

    Path emptyDir =
        TestDataHelper.getTestDataDirectory(this).resolve("provisioning_profiles_empty");

    ProvisioningProfileCopyStep step = new ProvisioningProfileCopyStep(
        projectFilesystem,
        testdataDir.resolve("Info.plist"),
        Optional.empty(),
        Optional.empty(),
        ProvisioningProfileStore.fromSearchPath(
            new DefaultProcessExecutor(new TestConsole()),
            emptyDir),
        outputFile,
        xcentFile,
        codeSignIdentityStore
    );

    step.execute(executionContext);
  }

  @Test
  public void shouldSetProvisioningProfileFutureWhenStepIsRun() throws Exception {
    assumeTrue(Platform.detect() == Platform.MACOS);
    ProvisioningProfileCopyStep step = new ProvisioningProfileCopyStep(
        projectFilesystem,
        testdataDir.resolve("Info.plist"),
        Optional.empty(),
        Optional.empty(),
        ProvisioningProfileStore.fromSearchPath(
            new DefaultProcessExecutor(new TestConsole()),
            testdataDir),
        outputFile,
        xcentFile,
        codeSignIdentityStore
    );

    Future<ProvisioningProfileMetadata> profileFuture = step.getSelectedProvisioningProfileFuture();
    step.execute(executionContext);
    assertTrue(profileFuture.isDone());
    assertNotNull(profileFuture.get());
  }

  @Test
  public void testNoEntitlementsDoesNotMergeInvalidProfileKeys() throws Exception {
    assumeTrue(Platform.detect() == Platform.MACOS);
    ProvisioningProfileCopyStep step = new ProvisioningProfileCopyStep(
        projectFilesystem,
        testdataDir.resolve("Info.plist"),
        Optional.of("00000000-0000-0000-0000-000000000000"),
        Optional.empty(),
        ProvisioningProfileStore.fromSearchPath(
            new DefaultProcessExecutor(new TestConsole()),
            testdataDir),
        outputFile,
        xcentFile,
        codeSignIdentityStore
    );
    step.execute(executionContext);

    ProvisioningProfileMetadata selectedProfile = step.getSelectedProvisioningProfileFuture().get();
    ImmutableMap<String, NSObject> profileEntitlements = selectedProfile.getEntitlements();
    assertTrue(profileEntitlements.containsKey(
        "com.apple.developer.icloud-container-development-container-identifiers"));

    Optional<String> xcentContents = projectFilesystem.readFileIfItExists(xcentFile);
    assertTrue(xcentContents.isPresent());
    NSDictionary xcentPlist = (NSDictionary)
        PropertyListParser.parse(xcentContents.get().getBytes());
    assertFalse(xcentPlist.containsKey(
        "com.apple.developer.icloud-container-development-container-identifiers"));
    assertEquals(xcentPlist.get("com.apple.developer.team-identifier"),
        profileEntitlements.get("com.apple.developer.team-identifier"));
  }

  @Test
  public void testEntitlementsDoesNotMergeInvalidProfileKeys() throws Exception {
    assumeTrue(Platform.detect() == Platform.MACOS);
    ProvisioningProfileCopyStep step = new ProvisioningProfileCopyStep(
        projectFilesystem,
        testdataDir.resolve("Info.plist"),
        Optional.of("00000000-0000-0000-0000-000000000000"),
        Optional.of(entitlementsFile),
        ProvisioningProfileStore.fromSearchPath(
            new DefaultProcessExecutor(new TestConsole()),
            testdataDir),
        outputFile,
        xcentFile,
        codeSignIdentityStore
    );
    step.execute(executionContext);

    ProvisioningProfileMetadata selectedProfile = step.getSelectedProvisioningProfileFuture().get();
    ImmutableMap<String, NSObject> profileEntitlements = selectedProfile.getEntitlements();
    assertTrue(profileEntitlements.containsKey(
        "com.apple.developer.icloud-container-development-container-identifiers"));

    Optional<String> xcentContents = projectFilesystem.readFileIfItExists(xcentFile);
    assertTrue(xcentContents.isPresent());
    NSDictionary xcentPlist = (NSDictionary)
        PropertyListParser.parse(xcentContents.get().getBytes());
    assertFalse(xcentPlist.containsKey(
        "com.apple.developer.icloud-container-development-container-identifiers"));
    assertEquals(xcentPlist.get("com.apple.developer.team-identifier"),
        profileEntitlements.get("com.apple.developer.team-identifier"));
  }
}
