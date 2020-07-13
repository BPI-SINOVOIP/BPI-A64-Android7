<?xml version="1.0"?>
<recipe>

    <dependency mavenUrl="com.android.support:appcompat-v7:${targetApi}.+"/>
    <dependency mavenUrl="com.squareup.picasso:picasso:2.3.2"/>

    <merge from="AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <merge from="res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <merge from="res/values/colors.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/colors.xml" />

    <merge from="res/values/themes.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/themes.xml" />

    <copy from="res/drawable"
                to="${escapeXmlAttribute(resOut)}/drawable" />
    <copy from="res/drawable-hdpi"
                to="${escapeXmlAttribute(resOut)}/drawable-hdpi" />
    <copy from="res/drawable-mdpi"
                to="${escapeXmlAttribute(resOut)}/drawable-mdpi" />
    <copy from="res/drawable-xhdpi"
                to="${escapeXmlAttribute(resOut)}/drawable-xhdpi" />
    <copy from="res/drawable-xxhdpi"
                to="${escapeXmlAttribute(resOut)}/drawable-xxhdpi" />

    <instantiate from="res/layout/activity_main.xml.ftl"
                  to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <instantiate from="res/layout/activity_details.xml.ftl"
                  to="${escapeXmlAttribute(resOut)}/layout/${detailsLayoutName}.xml" />

    <instantiate from="res/layout/playback_controls.xml.ftl"
                  to="${escapeXmlAttribute(resOut)}/layout/playback_controls.xml" />

    <instantiate from="src/app_package/MainActivity.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <instantiate from="src/app_package/MainFragment.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/${mainFragment}.java" />

    <instantiate from="src/app_package/DetailsActivity.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/${detailsActivity}.java" />

    <instantiate from="src/app_package/VideoDetailsFragment.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/${detailsFragment}.java" />

    <instantiate from="src/app_package/Movie.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/Movie.java" />

    <instantiate from="src/app_package/MovieList.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/MovieList.java" />

    <instantiate from="src/app_package/PicassoBackgroundManagerTarget.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/PicassoBackgroundManagerTarget.java" />

    <instantiate from="src/app_package/CardPresenter.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/CardPresenter.java" />

    <instantiate from="src/app_package/DetailsDescriptionPresenter.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/DetailsDescriptionPresenter.java" />

    <instantiate from="src/app_package/PlaybackOverlayActivity.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/PlaybackOverlayActivity.java" />

    <instantiate from="src/app_package/PlaybackOverlayFragment.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/PlaybackOverlayFragment.java" />

    <instantiate from="src/app_package/Utils.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/Utils.java" />

    <instantiate from="src/app_package/ErrorFragment.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/ErrorFragment.java" />

    <instantiate from="src/app_package/BrowseErrorActivity.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/BrowseErrorActivity.java" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
    <open file="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
</recipe>
