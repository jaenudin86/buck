/*
 * Copyright 2014-present Facebook, Inc.
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

package com.facebook.buck.ocaml;

import com.facebook.buck.cxx.Compiler;
import com.facebook.buck.rules.AbstractBuildRule;
import com.facebook.buck.rules.AddToRuleKey;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRuleParams;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.nio.file.Path;

/**
 * A build rule which preprocesses, compiles, and assembles an OCaml source.
 */
public class OCamlBuild extends AbstractBuildRule {

  @AddToRuleKey
  private final OCamlBuildContext ocamlContext;
  @AddToRuleKey
  private final Compiler cCompiler;
  @AddToRuleKey
  private final Compiler cxxCompiler;
  @AddToRuleKey
  private final boolean bytecodeOnly;

  public OCamlBuild(
      BuildRuleParams params,
      SourcePathResolver resolver,
      OCamlBuildContext ocamlContext,
      Compiler cCompiler,
      Compiler cxxCompiler,
      boolean bytecodeOnly) {
    super(params, resolver);
    this.ocamlContext = ocamlContext;
    this.cCompiler = cCompiler;
    this.cxxCompiler = cxxCompiler;
    this.bytecodeOnly = bytecodeOnly;

    Preconditions.checkNotNull(ocamlContext.getInput());
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context,
      BuildableContext buildableContext) {
    Path baseArtifactDir = ocamlContext.getNativeOutput().getParent();
    buildableContext.recordArtifact(baseArtifactDir);
    if (!bytecodeOnly) {
      buildableContext.recordArtifact(
          baseArtifactDir.resolve(OCamlBuildContext.OCAML_COMPILED_DIR));
    }
    buildableContext.recordArtifact(
        baseArtifactDir.resolve(OCamlBuildContext.OCAML_COMPILED_BYTECODE_DIR));
    return ImmutableList.of(
        new MakeCleanDirectoryStep(
            getProjectFilesystem(),
            ocamlContext.getNativeOutput().getParent()),
        new OCamlBuildStep(
            getResolver(),
            getProjectFilesystem(),
            ocamlContext,
            cCompiler.getEnvironment(getResolver()),
            cCompiler.getCommandPrefix(getResolver()),
            cxxCompiler.getEnvironment(getResolver()),
            cxxCompiler.getCommandPrefix(getResolver()),
            bytecodeOnly));
  }

  @Override
  public Path getPathToOutput() {
    return bytecodeOnly ? ocamlContext.getBytecodeOutput() : ocamlContext.getNativeOutput();
  }

  @Override
  public boolean isCacheable() {
    // Intermediate OCaml rules are not cacheable because the compiler is not deterministic.
    return false;
  }
}
