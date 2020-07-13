/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.util;

import com.intellij.testFramework.CompositeException;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.util.net.HttpConfigurable;

import java.io.File;

/**
 * Tests for {@link GradleProperties}.
 */
public class GradlePropertiesTest extends IdeaTestCase {
  private GradleProperties myProperties;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    File propertiesFilePath = createTempFile("gradle.properties", "");
    myProperties = new GradleProperties(propertiesFilePath);
  }

  @Override
  protected CompositeException checkForSettingsDamage() throws Exception {
    // For this test we don't care for this check. This method does nothing.
    return new CompositeException();
  }

  public void testCopyProxySettingsFromIde() {
    String host = "myproxy.test.com";
    int port = 443;

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = host;
    ideSettings.PROXY_PORT = port;

    GradleProperties.ProxySettings proxySettings = myProperties.getProxySettings();
    proxySettings.copyFrom(ideSettings);

    assertEquals(host, proxySettings.getHost());
    assertEquals(port, proxySettings.getPort());
  }

  public void testSetProxySettings() {
    String host = "myproxy.test.com";
    int port = 443;
    String user = "johndoe";
    String password = "123456";

    HttpConfigurable ideSettings = HttpConfigurable.getInstance();
    ideSettings.USE_HTTP_PROXY = true;
    ideSettings.PROXY_HOST = host;
    ideSettings.PROXY_PORT = port;
    ideSettings.PROXY_AUTHENTICATION = true;
    ideSettings.PROXY_LOGIN = user;
    ideSettings.setPlainProxyPassword(password);

    GradleProperties.ProxySettings proxySettings = myProperties.getProxySettings();
    proxySettings.copyFrom(ideSettings);

    // Verify that the proxy settings are stored properly in the actual properties file.
    myProperties.setProxySettings(proxySettings);

    assertEquals(host, myProperties.getProperty("systemProp.http.proxyHost"));
    assertEquals(String.valueOf(port), myProperties.getProperty("systemProp.http.proxyPort"));
    assertEquals(user, myProperties.getProperty("systemProp.http.proxyUser"));
    assertEquals(password, myProperties.getProperty("systemProp.http.proxyPassword"));

    proxySettings = myProperties.getProxySettings();
    assertEquals(host, proxySettings.getHost());
    assertEquals(port, proxySettings.getPort());

    // Verify that username and password are removed from properties file, if authentication is disabled in IDE settings.
    ideSettings.PROXY_AUTHENTICATION = false;

    proxySettings.copyFrom(ideSettings);
    myProperties.setProxySettings(proxySettings);

    assertNull(myProperties.getProperty("systemProp.http.proxyUser"));
    assertNull(myProperties.getProperty("systemProp.http.proxyPassword"));
  }
}