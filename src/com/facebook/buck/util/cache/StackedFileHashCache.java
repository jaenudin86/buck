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

package com.facebook.buck.util.cache;

import com.facebook.buck.io.ArchiveMemberPath;
import com.facebook.buck.model.Pair;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.HashCode;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Presents a list of {@link FileHashCache}s as a single cache, implementing a Chain of
 * Responsibility to find items in the cache.
 */
public class StackedFileHashCache implements FileHashCache {

  private final ImmutableList<? extends FileHashCache> caches;

  public StackedFileHashCache(ImmutableList<? extends FileHashCache> caches) {
    this.caches = caches;
  }

  private Optional<Pair<FileHashCache, Path>> lookup(Path path) {
    Preconditions.checkArgument(path.isAbsolute());
    for (FileHashCache cache : caches) {
      if (cache.willGet(path)) {
        return Optional.of(new Pair<>(cache, path));
      }
    }
    return Optional.empty();
  }

  private Optional<Pair<FileHashCache, ArchiveMemberPath>> lookup(ArchiveMemberPath path) {
    Preconditions.checkArgument(path.isAbsolute());
    for (FileHashCache cache : caches) {
      if (cache.willGet(path)) {
        return Optional.of(new Pair<>(cache, path));
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean willGet(Path path) {
    return lookup(path).isPresent();
  }

  @Override
  public boolean willGet(ArchiveMemberPath archiveMemberPath) {
    return lookup(archiveMemberPath).isPresent();
  }

  @Override
  public void invalidate(Path path) {
    Optional<Pair<FileHashCache, Path>> found = lookup(path);
    if (found.isPresent()) {
      found.get().getFirst().invalidate(found.get().getSecond());
    }
  }

  @Override
  public void invalidateAll() {
    for (FileHashCache cache : caches) {
      cache.invalidateAll();
    }
  }

  @Override
  public HashCode get(Path path) throws IOException {
    Optional<Pair<FileHashCache, Path>> found = lookup(path);
    if (!found.isPresent()) {
      throw new NoSuchFileException(path.toString());
    }
    return found.get().getFirst().get(found.get().getSecond());
  }

  @Override
  public long getSize(Path path) throws IOException {
    Optional<Pair<FileHashCache, Path>> found = lookup(path);
    if (!found.isPresent()) {
      throw new NoSuchFileException(path.toString());
    }
    return found.get().getFirst().getSize(found.get().getSecond());
  }

  @Override
  public HashCode get(ArchiveMemberPath archiveMemberPath) throws IOException {
    Optional<Pair<FileHashCache, ArchiveMemberPath>> found = lookup(archiveMemberPath);
    if (!found.isPresent()) {
      throw new NoSuchFileException(archiveMemberPath.toString());
    }
    return found.get().getFirst().get(found.get().getSecond());
  }

  @Override
  public void set(Path path, HashCode hashCode) throws IOException {
    Optional<Pair<FileHashCache, Path>> found = lookup(path);
    if (found.isPresent()) {
      found.get().getFirst().set(found.get().getSecond(), hashCode);
    }
  }

}
