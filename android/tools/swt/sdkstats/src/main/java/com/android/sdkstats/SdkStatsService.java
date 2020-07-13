/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.sdkstats;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class to send "ping" usage reports to the server. */
public class SdkStatsService {

    protected static final String SYS_PROP_OS_ARCH      = "os.arch";        //$NON-NLS-1$
    protected static final String SYS_PROP_JAVA_VERSION = "java.version";   //$NON-NLS-1$
    protected static final String SYS_PROP_OS_VERSION   = "os.version";     //$NON-NLS-1$
    protected static final String SYS_PROP_OS_NAME      = "os.name";        //$NON-NLS-1$

    /** Minimum interval between ping, in milliseconds. */
    private static final long PING_INTERVAL_MSEC = 86400 * 1000;  // 1 day

    private static final boolean DEBUG = System.getenv("ANDROID_DEBUG_PING") != null; //$NON-NLS-1$

    private DdmsPreferenceStore mStore = new DdmsPreferenceStore();

    public SdkStatsService() {
    }

    /**
     * Send a "ping" to the Google toolbar server, if enough time has
     * elapsed since the last ping, and if the user has not opted out.
     * <p/>
     * This is a simplified version of {@link #ping(String[])} that only
     * sends an "application" name and a "version" string. See the explanation
     * there for details.
     *
     * @param app The application name that reports the ping (e.g. "emulator" or "ddms".)
     *          Valid characters are a-zA-Z0-9 only.
     * @param version The version string (e.g. "12" or "1.2.3.4", 4 groups max.)
     * @see #ping(String[])
     */
    public void ping(String app, String version) {
        doPing(app, version, null);
    }

    /**
     * Send a "ping" to the Google toolbar server, if enough time has
     * elapsed since the last ping, and if the user has not opted out.
     * <p/>
     * The ping will not be sent if the user opt out dialog has not been shown yet.
     * Use {@link #checkUserPermissionForPing(Shell)} to display the dialog requesting
     * user permissions.
     * <p/>
     * Note: The actual ping (if any) is sent in a <i>non-daemon</i> background thread.
     * <p/>
     * The arguments are defined as follow:
     * <ul>
     * <li>Argument 0 is the "ping" command and is ignored.</li>
     * <li>Argument 1 is the application name that reports the ping (e.g. "emulator" or "ddms".)
     *          Valid characters are a-zA-Z0-9 only.</li>
     * <li>Argument 2 is the version string (e.g. "12" or "1.2.3.4", 4 groups max.)</li>
     * <li>Arguments 3+ are optional and depend on the application name.</li>
     * <li>"emulator" application currently has 3 optional arguments:
     *      <ul>
     *      <li>Arugment 3: android_gl_vendor</li>
     *      <li>Arugment 4: android_gl_renderer</li>
     *      <li>Arugment 5: android_gl_version</li>
     *      </ul>
     * </li>
     * </ul>
     *
     * @param arguments A non-empty non-null array of arguments to the ping as described above.
     */
    public void ping(String[] arguments) {
        if (arguments == null || arguments.length < 3) {
            throw new IllegalArgumentException(
                    "Invalid ping arguments: expected ['ping', app, version] but got " +
                    (arguments == null ? "null" : Arrays.toString(arguments)));
        }
        int len = arguments.length;
        String app = arguments[1];
        String version = arguments[2];

        Map<String, String> extras = new HashMap<String, String>();

        if ("emulator".equals(app)) {                                   //$NON-NLS-1$
            if (len > 3) {
                extras.put("glm", sanitizeGlArg(arguments[3])); //$NON-NLS-1$ vendor
            }
            if (len > 4) {
                extras.put("glr", sanitizeGlArg(arguments[4])); //$NON-NLS-1$ renderer
            }
            if (len > 5) {
                extras.put("glv", sanitizeGlArg(arguments[5])); //$NON-NLS-1$ version
            }
        }

        doPing(app, version, extras);
    }

    private String sanitizeGlArg(String arg) {
        if (arg == null) {
        arg = "";                                                   //$NON-NLS-1$
        } else {
            try {
                arg = arg.trim();
                arg = arg.replaceAll("[^A-Za-z0-9\\s_()./-]", " "); //$NON-NLS-1$ //$NON-NLS-2$
                arg = arg.replaceAll("\\s\\s+", " ");               //$NON-NLS-1$ //$NON-NLS-2$

                // Guard from arbitrarily long parameters
                if (arg.length() > 128) {
                    arg = arg.substring(0, 128);
                }

                arg = URLEncoder.encode(arg, "UTF-8");              //$NON-NLS-1$
            } catch (UnsupportedEncodingException e) {
                arg = "";                                           //$NON-NLS-1$
            }
        }

        return arg;
    }

    /**
     * Display a dialog to the user providing information about the ping service,
     * and whether they'd like to opt-out of it.
     *
     * Once the dialog has been shown, it sets a preference internally indicating
     * that the user has viewed this dialog.
     */
    public void checkUserPermissionForPing(Shell parent) {
        if (!mStore.hasPingId()) {
            askUserPermissionForPing(parent);
            mStore.generateNewPingId();
        }
    }

    /**
     * Prompt the user for whether they want to opt out of reporting, and save the user
     * input in preferences.
     */
    private void askUserPermissionForPing(final Shell parent) {
        final Display display = parent.getDisplay();
        display.syncExec(new Runnable() {
            @Override
            public void run() {
                SdkStatsPermissionDialog dialog = new SdkStatsPermissionDialog(parent);
                dialog.open();
                mStore.setPingOptIn(dialog.getPingUserPreference());
            }
        });
    }

    // -------

    /**
     * Pings the usage stats server, as long as the prefs contain the opt-in boolean
     *
     * @param app The application name that reports the ping (e.g. "emulator" or "ddms".)
     *          Will be normalized.  Valid characters are a-zA-Z0-9 only.
     * @param version The version string (e.g. "12" or "1.2.3.4", 4 groups max.)
     * @param extras Extra key/value parameters to send. They are send as-is and must
     *  already be well suited and escaped using {@link URLEncoder#encode(String, String)}.
     */
    protected void doPing(String app, String version, final Map<String, String> extras) {
        // Note: if you change the implementation here, you also need to change
        // the overloaded SdkStatsServiceTest.doPing() used for testing.

        // Validate the application and version input.
        final String nApp = normalizeAppName(app);
        final String nVersion = normalizeVersion(version);

        // If the user has not opted in, do nothing and quietly return.
        if (!mStore.isPingOptIn()) {
            // user opted out.
            return;
        }

        // If the last ping *for this app* was too recent, do nothing.
        long now = System.currentTimeMillis();
        long then = mStore.getPingTime(app);
        if (now - then < PING_INTERVAL_MSEC) {
            // too soon after a ping.
            return;
        }

        // Record the time of the attempt, whether or not it succeeds.
        mStore.setPingTime(app, now);

        // Send the ping itself in the background (don't block if the
        // network is down or slow or confused).
        final long id = mStore.getPingId();
        new Thread() {
            @Override
            public void run() {
                try {
                    URL url = createPingUrl(nApp, nVersion, id, extras);
                    actuallySendPing(url);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }


    /**
     * Unconditionally send a "ping" request to the server.
     *
     * @param url The URL to send to the server.
     * * @throws IOException if the ping failed
     */
    private void actuallySendPing(URL url) throws IOException {
        assert url != null;

        if (DEBUG) {
            System.err.println("Ping: " + url.toString());          //$NON-NLS-1$
        }

        // Discard the actual response, but make sure it reads OK
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        // Believe it or not, a 404 response indicates success:
        // the ping was logged, but no update is configured.
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK &&
            conn.getResponseCode() != HttpURLConnection.HTTP_NOT_FOUND) {
            throw new IOException(
                conn.getResponseMessage() + ": " + url);            //$NON-NLS-1$
        }
    }

    /**
     * Compute the ping URL to send the data to the server.
     *
     * @param app The application name that reports the ping (e.g. "emulator" or "ddms".)
     *          Valid characters are a-zA-Z0-9 only.
     * @param version The version string already formatted as a 4 dotted group (e.g. "1.2.3.4".)
     * @param id of the local installation
     * @param extras Extra key/value parameters to send. They are send as-is and must
     *  already be well suited and escaped using {@link URLEncoder#encode(String, String)}.
     */
    protected URL createPingUrl(String app, String version, long id, Map<String, String> extras)
            throws UnsupportedEncodingException, MalformedURLException {

        String osName  = URLEncoder.encode(getOsName(),  "UTF-8");  //$NON-NLS-1$
        String osArch  = URLEncoder.encode(getOsArch(),  "UTF-8");  //$NON-NLS-1$
        String jvmArch = URLEncoder.encode(getJvmInfo(), "UTF-8");  //$NON-NLS-1$

        // Include the application's name as part of the as= value.
        // Share the user ID for all apps, to allow unified activity reports.

        String extraStr = "";                                       //$NON-NLS-1$
        if (extras != null && !extras.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : extras.entrySet()) {
                sb.append('&').append(entry.getKey()).append('=').append(entry.getValue());
            }
            extraStr = sb.toString();
        }

        URL url = new URL(
            "http",                                                 //$NON-NLS-1$
            "tools.google.com",                                     //$NON-NLS-1$
            "/service/update?as=androidsdk_" + app +                //$NON-NLS-1$
                "&id=" + Long.toHexString(id) +                     //$NON-NLS-1$
                "&version=" + version +                             //$NON-NLS-1$
                "&os=" + osName +                                   //$NON-NLS-1$
                "&osa=" + osArch +                                  //$NON-NLS-1$
                "&vma=" + jvmArch +                                 //$NON-NLS-1$
                extraStr);
        return url;
    }

    /**
     * Detects and reports the host OS: "linux", "win" or "mac".
     * For Windows and Mac also append the version, so for example
     * Win XP will return win-5.1.
     */
    protected String getOsName() {                   // made protected for testing
        String os = getSystemProperty(SYS_PROP_OS_NAME);

        if (os == null || os.length() == 0) {
            return "unknown";                               //$NON-NLS-1$
        }

        String os2 = os.toLowerCase(Locale.US);

        if (os2.startsWith("mac")) {                        //$NON-NLS-1$
            os = "mac";                                     //$NON-NLS-1$
            String osVers = getOsVersion();
            if (osVers != null) {
                os = os + '-' + osVers;
            }
        } else if (os2.startsWith("win")) {                 //$NON-NLS-1$
            os = "win";                                     //$NON-NLS-1$
            String osVers = getOsVersion();
            if (osVers != null) {
                os = os + '-' + osVers;
            }
        } else if (os2.startsWith("linux")) {               //$NON-NLS-1$
            os = "linux";                                   //$NON-NLS-1$

        } else if (os.length() > 32) {
            // Unknown -- send it verbatim so we can see it
            // but protect against arbitrarily long values
            os = os.substring(0, 32);
        }
        return os;
    }

    /**
     * Detects and returns the OS architecture: x86, x86_64, ppc.
     * This may differ or be equal to the JVM architecture in the sense that
     * a 64-bit OS can run a 32-bit JVM.
     */
    protected String getOsArch() {                   // made protected for testing
        String arch = getJvmArch();

        if ("x86_64".equals(arch)) {                                    //$NON-NLS-1$
            // This is a simple case: the JVM runs in 64-bit so the
            // OS must be a 64-bit one.
            return arch;

        } else if ("x86".equals(arch)) {                                //$NON-NLS-1$
            // This is the misleading case: the JVM is 32-bit but the OS
            // might be either 32 or 64. We can't tell just from this
            // property.
            // Macs are always on 64-bit, so we just need to figure it
            // out for Windows and Linux.

            String os = getOsName();
            if (os.startsWith("win")) {                                 //$NON-NLS-1$
                // When WOW64 emulates a 32-bit environment under a 64-bit OS,
                // it sets PROCESSOR_ARCHITEW6432 to AMD64 or IA64 accordingly.
                // Ref: http://msdn.microsoft.com/en-us/library/aa384274(v=vs.85).aspx

                String w6432 = getSystemEnv("PROCESSOR_ARCHITEW6432");  //$NON-NLS-1$
                if (w6432 != null && w6432.indexOf("64") != -1) {       //$NON-NLS-1$
                    return "x86_64";                                    //$NON-NLS-1$
                }
            } else if (os.startsWith("linux")) {                        //$NON-NLS-1$
                // Let's try the obvious. This works in Ubuntu and Debian
                String s = getSystemEnv("HOSTTYPE");                    //$NON-NLS-1$

                s = sanitizeOsArch(s);
                if (s.indexOf("86") != -1) {                            //$NON-NLS-1$
                    arch = s;
                }
            }
        }

        return arch;
    }

    /**
     * Returns the version of the OS version if it is defined as X.Y, or null otherwise.
     * <p/>
     * Example of returned versions can be found at http://lopica.sourceforge.net/os.html
     * <p/>
     * This method removes any exiting micro versions.
     * Returns null if the version doesn't match X.Y.Z.
     */
    protected String getOsVersion() {                           // made protected for testing
        Pattern p = Pattern.compile("(\\d+)\\.(\\d+).*");       //$NON-NLS-1$
        String osVers = getSystemProperty(SYS_PROP_OS_VERSION);
        if (osVers != null && osVers.length() > 0) {
            Matcher m = p.matcher(osVers);
            if (m.matches()) {
                return m.group(1) + '.' + m.group(2);
            }
        }
        return null;
    }

    /**
     * Detects and returns the JVM info: version + architecture.
     * Examples: 1.4-ppc, 1.6-x86, 1.7-x86_64
     */
    protected String getJvmInfo() {                      // made protected for testing
        return getJvmVersion() + '-' + getJvmArch();
    }

    /**
     * Returns the major.minor Java version.
     * <p/>
     * The "java.version" property returns something like "1.6.0_20"
     * of which we want to return "1.6".
     */
    protected String getJvmVersion() {                   // made protected for testing
        String version = getSystemProperty(SYS_PROP_JAVA_VERSION);

        if (version == null || version.length() == 0) {
            return "unknown";                                   //$NON-NLS-1$
        }

        Pattern p = Pattern.compile("(\\d+)\\.(\\d+).*");       //$NON-NLS-1$
        Matcher m = p.matcher(version);
        if (m.matches()) {
            return m.group(1) + '.' + m.group(2);
        }

        // Unknown version. Send it as-is within a reasonable size limit.
        if (version.length() > 8) {
            version = version.substring(0, 8);
        }
        return version;
    }

    /**
     * Detects and returns the JVM architecture.
     * <p/>
     * The HotSpot JVM has a private property for this, "sun.arch.data.model",
     * which returns either "32" or "64". However it's not in any kind of spec.
     * <p/>
     * What we want is to know whether the JVM is running in 32-bit or 64-bit and
     * the best indicator is to use the "os.arch" property.
     * - On a 32-bit system, only a 32-bit JVM can run so it will be x86 or ppc.<br/>
     * - On a 64-bit system, a 32-bit JVM will also return x86 since the OS needs
     *   to masquerade as a 32-bit OS for backward compatibility.<br/>
     * - On a 64-bit system, a 64-bit JVM will properly return x86_64.
     * <pre>
     * JVM:       Java 32-bit   Java 64-bit
     * Windows:   x86           x86_64
     * Linux:     x86           x86_64
     * Mac        untested      x86_64
     * </pre>
     */
    protected String getJvmArch() {                  // made protected for testing
        String arch = getSystemProperty(SYS_PROP_OS_ARCH);
        return sanitizeOsArch(arch);
    }

    private String sanitizeOsArch(String arch) {
        if (arch == null || arch.length() == 0) {
            return "unknown";                               //$NON-NLS-1$
        }

        if (arch.equalsIgnoreCase("x86_64") ||              //$NON-NLS-1$
                arch.equalsIgnoreCase("ia64") ||            //$NON-NLS-1$
                arch.equalsIgnoreCase("amd64")) {           //$NON-NLS-1$
            return "x86_64";                                //$NON-NLS-1$
        }

        if (arch.length() >= 4 && arch.charAt(0) == 'i' && arch.indexOf("86") == 2) { //$NON-NLS-1$
            // Any variation of iX86 counts as x86 (i386, i486, i686).
            return "x86";                                   //$NON-NLS-1$
        }

        if (arch.equalsIgnoreCase("PowerPC")) {             //$NON-NLS-1$
            return "ppc";                                   //$NON-NLS-1$
        }

        // Unknown arch. Send it as-is but protect against arbitrarily long values.
        if (arch.length() > 32) {
            arch = arch.substring(0, 32);
        }
        return arch;
    }

    /**
     * Normalize the supplied application name.
     *
     * @param app to report
     */
    protected String normalizeAppName(String app) {
        // Filter out \W , non-word character: [^a-zA-Z_0-9]
        String app2 = app.replaceAll("\\W", "");                  //$NON-NLS-1$ //$NON-NLS-2$

        if (app.length() == 0) {
            throw new IllegalArgumentException("Bad app name: " + app);         //$NON-NLS-1$
        }

        return app2;
    }

    /**
     * Validate the supplied application version, and normalize the version.
     *
     * @param version supplied by caller
     * @return normalized dotted quad version
     */
    protected String normalizeVersion(String version) {

        Pattern regex = Pattern.compile(
                //1=major     2=minor       3=micro       4=build |  5=rc
                "^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:\\.(\\d+)| +rc(\\d+))?"); //$NON-NLS-1$

        Matcher m = regex.matcher(version);
        if (m != null && m.lookingAt()) {
            StringBuilder normal = new StringBuilder();
            for (int i = 1; i <= 4; i++) {
                int v = 0;
                // If build is null but we have an rc, take that number instead as the 4th part.
                if (i == 4 &&
                        i < m.groupCount() &&
                        m.group(i) == null &&
                        m.group(i+1) != null) {
                    i++;
                }
                if (m.group(i) != null) {
                    try {
                        v = Integer.parseInt(m.group(i));
                    } catch (Exception ignore) {
                    }
                }
                if (i > 1) {
                    normal.append('.');
                }
                normal.append(v);
            }
            return normal.toString();
        }

        throw new IllegalArgumentException("Bad version: " + version);          //$NON-NLS-1$
    }

    /**
     * Calls {@link System#getProperty(String)}.
     * Allows unit-test to override the return value.
     * @see System#getProperty(String)
     */
    protected String getSystemProperty(String name) {
        return System.getProperty(name);
    }

    /**
     * Calls {@link System#getenv(String)}.
     * Allows unit-test to override the return value.
     * @see System#getenv(String)
     */
    protected String getSystemEnv(String name) {
        return System.getenv(name);
    }
}
