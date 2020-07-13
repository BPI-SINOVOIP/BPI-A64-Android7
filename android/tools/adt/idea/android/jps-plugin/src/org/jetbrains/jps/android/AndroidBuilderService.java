package org.jetbrains.jps.android;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.builder.*;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.TargetBuilder;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuilderService extends BuilderService {
  @NotNull
  @Override
  public List<? extends BuildTargetType<?>> getTargetTypes() {
    return Arrays.asList(
      AndroidManifestMergingTarget.MyTargetType.INSTANCE,
      AndroidLibraryPackagingTarget.MyTargetType.INSTANCE,
      AndroidAarDepsBuildTarget.MyTargetType.INSTANCE,
      AndroidPreDexBuildTarget.MyTargetType.INSTANCE,
      AndroidDexBuildTarget.MyTargetType.INSTANCE,
      AndroidResourceCachingBuildTarget.MyTargetType.INSTANCE,
      AndroidResourcePackagingBuildTarget.MyTargetType.INSTANCE,
      AndroidPackagingBuildTarget.MyTargetType.INSTANCE);
  }

  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Arrays.asList(new AndroidSourceGeneratingBuilder());
  }

  @NotNull
  @Override
  public List<? extends TargetBuilder<?,?>> createBuilders() {
    return Arrays.asList(new AndroidManifestMergingBuilder(),
                         new AndroidLibraryPackagingBuilder(),
                         new AndroidAarDepsBuilder(),
                         new AndroidPreDexBuilder(),
                         new AndroidDexBuilder(),
                         new AndroidResourceCachingBuilder(),
                         new AndroidResourcePackagingBuilder(),
                         new AndroidPackagingBuilder());
  }
}
