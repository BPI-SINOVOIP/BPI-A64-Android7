/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.monkeyrunner;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;

import com.android.chimpchat.ChimpChat;

import org.python.util.PythonInterpreter;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 *  MonkeyRunner is a host side application to control a monkey instance on a
 *  device. MonkeyRunner provides some useful helper functions to control the
 *  device as well as various other methods to help script tests.  This class bootstraps
 *  MonkeyRunner.
 */
public class MonkeyRunnerStarter {
    private static final Logger LOG = Logger.getLogger(MonkeyRunnerStarter.class.getName());
    private static final String MONKEY_RUNNER_MAIN_MANIFEST_NAME = "MonkeyRunnerStartupRunner";

    private final ChimpChat chimp;
    private final MonkeyRunnerOptions options;

    public MonkeyRunnerStarter(MonkeyRunnerOptions options) {
        Map<String, String> chimp_options = new TreeMap<String, String>();
        chimp_options.put("backend", options.getBackendName());
        this.options = options;
        this.chimp = ChimpChat.getInstance(chimp_options);
        MonkeyRunner.setChimpChat(chimp);
    }



    private int run() {
        // This system property gets set by the included starter script
        String monkeyRunnerPath = System.getProperty("com.android.monkeyrunner.bindir") +
                File.separator + "monkeyrunner";

        Map<String, Predicate<PythonInterpreter>> plugins = handlePlugins();
        if (options.getScriptFile() == null) {
            ScriptRunner.console(monkeyRunnerPath);
            chimp.shutdown();
            return 0;
        } else {
            int error = ScriptRunner.run(monkeyRunnerPath, options.getScriptFile().getAbsolutePath(),
                    options.getArguments(), plugins);
            chimp.shutdown();
            return error;
        }
    }

    private Predicate<PythonInterpreter> handlePlugin(File f) {
        JarFile jarFile;
        try {
            jarFile = new JarFile(f);
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Unable to open plugin file.  Is it a jar file? " +
                    f.getAbsolutePath(), e);
            return Predicates.alwaysFalse();
        }
        Manifest manifest;
        try {
            manifest = jarFile.getManifest();
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Unable to get manifest file from jar: " +
                    f.getAbsolutePath(), e);
            return Predicates.alwaysFalse();
        }
        Attributes mainAttributes = manifest.getMainAttributes();
        String pluginClass = mainAttributes.getValue(MONKEY_RUNNER_MAIN_MANIFEST_NAME);
        if (pluginClass == null) {
            // No main in this plugin, so it always succeeds.
            return Predicates.alwaysTrue();
        }
        URL url;
        try {
            url =  f.toURI().toURL();
        } catch (MalformedURLException e) {
            LOG.log(Level.SEVERE, "Unable to convert file to url " + f.getAbsolutePath(),
                    e);
            return Predicates.alwaysFalse();
        }
        URLClassLoader classLoader = new URLClassLoader(new URL[] { url },
                ClassLoader.getSystemClassLoader());
        Class<?> clz;
        try {
            clz = Class.forName(pluginClass, true, classLoader);
        } catch (ClassNotFoundException e) {
            LOG.log(Level.SEVERE, "Unable to load the specified plugin: " + pluginClass, e);
            return Predicates.alwaysFalse();
        }
        Object loadedObject;
        try {
            loadedObject = clz.newInstance();
        } catch (InstantiationException e) {
            LOG.log(Level.SEVERE, "Unable to load the specified plugin: " + pluginClass, e);
            return Predicates.alwaysFalse();
        } catch (IllegalAccessException e) {
            LOG.log(Level.SEVERE, "Unable to load the specified plugin " +
                    "(did you make it public?): " + pluginClass, e);
            return Predicates.alwaysFalse();
        }
        // Cast it to the right type
        if (loadedObject instanceof Runnable) {
            final Runnable run = (Runnable) loadedObject;
            return new Predicate<PythonInterpreter>() {
                public boolean apply(PythonInterpreter i) {
                    run.run();
                    return true;
                }
            };
        } else if (loadedObject instanceof Predicate<?>) {
            return (Predicate<PythonInterpreter>) loadedObject;
        } else {
            LOG.severe("Unable to coerce object into correct type: " + pluginClass);
            return Predicates.alwaysFalse();
        }
    }

    private Map<String, Predicate<PythonInterpreter>> handlePlugins() {
        ImmutableMap.Builder<String, Predicate<PythonInterpreter>> builder = ImmutableMap.builder();
        for (File f : options.getPlugins()) {
            builder.put(f.getAbsolutePath(), handlePlugin(f));
        }
        return builder.build();
    }

        /* Similar to above, when this fails, it no longer throws a
         * runtime exception, but merely will log the failure.
         */


    private static final void replaceAllLogFormatters(Formatter form, Level level) {
        LogManager mgr = LogManager.getLogManager();
        Enumeration<String> loggerNames = mgr.getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            String loggerName = loggerNames.nextElement();
            Logger logger = mgr.getLogger(loggerName);
            for (Handler handler : logger.getHandlers()) {
                handler.setFormatter(form);
                handler.setLevel(level);
            }
        }
    }

    public static void main(String[] args) {
        MonkeyRunnerOptions options = MonkeyRunnerOptions.processOptions(args);

        if (options == null) {
            return;
        }

        // logging property files are difficult
        replaceAllLogFormatters(MonkeyFormatter.DEFAULT_INSTANCE, options.getLogLevel());

        MonkeyRunnerStarter runner = new MonkeyRunnerStarter(options);
        int error = runner.run();

        // This will kill any background threads as well.
        System.exit(error);
    }
}
