/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class ChannelStatus implements Comparable<ChannelStatus> {
  @NonNls public static final String EAP_CODE = "eap";
  @NonNls public static final String RELEASE_CODE = "release";

  /**
   * EAP is our Canary Channel.
   * <p/>
   * From the updates.xml description: <br/>
   * Canary builds are the bleeding edge, released about weekly. While these builds do get tested,
   * they are still subject to bugs, as we want people to see what's new as soon as possible.
   * This is not recommended for production.
   */
  public static final ChannelStatus EAP = new ChannelStatus(0, EAP_CODE, "Canary Channel");
  /**
   * Milestone is our Dev Channel.
   * <p/>
   * From the updates.xml description: <br/>
   * Dev channel builds are hand-picked older canary builds that survived the test of time.
   * It's updated roughly every month.
   */
  public static final ChannelStatus MILESTONE = new ChannelStatus(1, "milestone", "Dev Channel");
  public static final ChannelStatus BETA = new ChannelStatus(2, "beta", "Beta Channel");
  public static final ChannelStatus RELEASE = new ChannelStatus(3, RELEASE_CODE, "Stable Channel");

  private static final List<ChannelStatus> ALL_TYPES = ContainerUtil.immutableList(RELEASE, BETA, MILESTONE, EAP);

  private final int myOrder;
  private final String myCode;
  private final String myDisplayName;

  private ChannelStatus(int order, String code, String displayName) {
    myOrder = order;
    myCode = code;
    myDisplayName = displayName;
  }

  public static ChannelStatus fromCode(String code) {
    if (EAP_CODE.equalsIgnoreCase(code)) return EAP;
    if ("milestone".equalsIgnoreCase(code)) return MILESTONE;
    if ("beta".equalsIgnoreCase(code)) return BETA;

    return RELEASE;
  }

  public int compareTo(ChannelStatus o) {
    return myOrder - o.myOrder;
  }

  public String getCode() {
    return myCode;
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public static List<ChannelStatus> all() {
    return ALL_TYPES;
  }

  @Override
  public String toString() {
    return myDisplayName;
  }
}
