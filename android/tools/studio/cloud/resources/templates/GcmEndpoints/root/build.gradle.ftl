// If you would like more information on the gradle-appengine-plugin please refer to the github page
// https://github.com/GoogleCloudPlatform/gradle-appengine-plugin

buildscript {
    repositories {
        mavenLocal()
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
  compile 'com.google.appengine:appengine-endpoints:${appEngineVersion}'
  compile 'com.google.appengine:appengine-endpoints-deps:${appEngineVersion}'
  compile 'javax.servlet:servlet-api:2.5'
  compile 'com.googlecode.objectify:objectify:4.0b3'
  compile 'com.ganyo:gcm-server:1.0.2'
}

appengine {
  downloadSdk = true
  appcfg {
    oauth2 = true
  }
  endpoints {
    getClientLibsOnBuild = true
    getDiscoveryDocsOnBuild = true
  }
}
