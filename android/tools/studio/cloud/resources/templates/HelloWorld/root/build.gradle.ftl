// If you would like more information on the gradle-appengine-plugin please refer to the github page
// https://github.com/GoogleCloudPlatform/gradle-appengine-plugin

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath 'com.google.appengine:gradle-appengine-plugin:${appEngineVersion}'
    }
}

repositories {
    mavenCentral();
}

apply plugin: 'java'
apply plugin: 'war'
apply plugin: 'appengine'

sourceCompatibility = 1.7
targetCompatibility = 1.7

dependencies {
  appengineSdk 'com.google.appengine:appengine-java-sdk:${appEngineVersion}'
  compile 'javax.servlet:servlet-api:2.5'
}

appengine {
  downloadSdk = true
  appcfg {
    oauth2 = true
  }
}
