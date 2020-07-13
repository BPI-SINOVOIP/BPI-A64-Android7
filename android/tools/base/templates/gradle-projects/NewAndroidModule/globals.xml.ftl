<?xml version="1.0"?>
<globals>
    <global id="topOut" value="." />
    <global id="projectOut" value="." />
    <!-- Use appcompat if compiling with Lollipop and supporting pre-Lollipop versions -->
    <global id="appCompat" type="boolean" value="${(hasDependency('com.android.support:appcompat-v7'))?string}" />
    <global id="manifestOut" value="${manifestDir}" />
    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="testOut" value="androidTest/${slashedPackageName(packageName)}" />
    <global id="unitTestOut" value="${escapeXmlAttribute(projectOut)}/src/test/java/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="mavenUrl" value="mavenCentral" />
    <global id="buildToolsVersion" value="18.0.1" />
    <global id="gradlePluginVersion" value="0.6.+" />
    <global id="junitVersion" value="4.12" />
    <global id="unitTestsSupported" type="boolean" value="${(compareVersions(gradlePluginVersion, '1.1.0') >= 0)?string}" />
</globals>
