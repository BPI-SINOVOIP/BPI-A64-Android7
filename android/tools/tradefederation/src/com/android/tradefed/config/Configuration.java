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
package com.android.tradefed.config;

import com.android.tradefed.build.IBuildProvider;
import com.android.tradefed.build.StubBuildProvider;
import com.android.tradefed.command.CommandOptions;
import com.android.tradefed.command.ICommandOptions;
import com.android.tradefed.config.ConfigurationDef.OptionDef;
import com.android.tradefed.config.OptionSetter.FieldDef;
import com.android.tradefed.device.DeviceSelectionOptions;
import com.android.tradefed.device.IDeviceRecovery;
import com.android.tradefed.device.IDeviceSelection;
import com.android.tradefed.device.TestDeviceOptions;
import com.android.tradefed.device.WaitDeviceRecovery;
import com.android.tradefed.log.ILeveledLogOutput;
import com.android.tradefed.log.StdoutLogger;
import com.android.tradefed.result.FileSystemLogSaver;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.TextResultReporter;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.targetprep.StubTargetPreparer;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.StubTest;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.QuotationAwareTokenizer;
import com.google.common.base.Joiner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.kxml2.io.KXmlSerializer;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A concrete {@link IConfiguration} implementation that stores the loaded config objects in a map
 */
public class Configuration implements IConfiguration {

    // type names for built in configuration objects
    public static final String BUILD_PROVIDER_TYPE_NAME = "build_provider";
    public static final String TARGET_PREPARER_TYPE_NAME = "target_preparer";
    public static final String TEST_TYPE_NAME = "test";
    public static final String DEVICE_RECOVERY_TYPE_NAME = "device_recovery";
    public static final String LOGGER_TYPE_NAME = "logger";
    public static final String LOG_SAVER_TYPE_NAME = "log_saver";
    public static final String RESULT_REPORTER_TYPE_NAME = "result_reporter";
    public static final String CMD_OPTIONS_TYPE_NAME = "cmd_options";
    public static final String DEVICE_REQUIREMENTS_TYPE_NAME = "device_requirements";
    public static final String DEVICE_OPTIONS_TYPE_NAME = "device_options";

    // additional element names used for emitting the configuration XML.
    private static final String CONFIGURATION_NAME = "configuration";
    private static final String OPTION_NAME = "option";
    private static final String CLASS_NAME = "class";
    private static final String NAME_NAME = "name";
    private static final String KEY_NAME = "key";
    private static final String VALUE_NAME = "value";

    private static Map<String, ObjTypeInfo> sObjTypeMap = null;

    /** Mapping of config object type name to config objects. */
    private Map<String, List<Object>> mConfigMap;
    private final String mName;
    private final String mDescription;
    // original command line used to create this given configuration.
    private String[] mCommandLine;

    // Used to track config names that were used to set field values
    private MultiMap<FieldDef, String> mFieldSources = new MultiMap<>();


    /**
     * Container struct for built-in config object type
     */
    private static class ObjTypeInfo {
        final Class<?> mExpectedType;
        /** true if a list (ie many objects in a single config) are supported for this type */
        final boolean mIsListSupported;

        ObjTypeInfo(Class<?> expectedType, boolean isList) {
            mExpectedType = expectedType;
            mIsListSupported = isList;
        }
    }

    /**
     * Determine if given config object type name is a built in object
     *
     * @param typeName the config object type name
     * @return <code>true</code> if name is a built in object type
     */
    static boolean isBuiltInObjType(String typeName) {
        return getObjTypeMap().containsKey(typeName);
    }

    private static synchronized Map<String, ObjTypeInfo> getObjTypeMap() {
        if (sObjTypeMap == null) {
            sObjTypeMap = new HashMap<String, ObjTypeInfo>();
            sObjTypeMap.put(BUILD_PROVIDER_TYPE_NAME, new ObjTypeInfo(IBuildProvider.class, false));
            sObjTypeMap.put(TARGET_PREPARER_TYPE_NAME, new ObjTypeInfo(ITargetPreparer.class, true));
            sObjTypeMap.put(TEST_TYPE_NAME, new ObjTypeInfo(IRemoteTest.class, true));
            sObjTypeMap.put(DEVICE_RECOVERY_TYPE_NAME, new ObjTypeInfo(IDeviceRecovery.class, false));
            sObjTypeMap.put(LOGGER_TYPE_NAME, new ObjTypeInfo(ILeveledLogOutput.class, false));
            sObjTypeMap.put(LOG_SAVER_TYPE_NAME, new ObjTypeInfo(ILogSaver.class, false));
            sObjTypeMap.put(RESULT_REPORTER_TYPE_NAME, new ObjTypeInfo(ITestInvocationListener.class,
                    true));
            sObjTypeMap.put(CMD_OPTIONS_TYPE_NAME, new ObjTypeInfo(ICommandOptions.class,
                    false));
            sObjTypeMap.put(DEVICE_REQUIREMENTS_TYPE_NAME, new ObjTypeInfo(IDeviceSelection.class,
                    false));
            sObjTypeMap.put(DEVICE_OPTIONS_TYPE_NAME, new ObjTypeInfo(TestDeviceOptions.class,
                    false));
        }
        return sObjTypeMap;
    }

    /**
     * Creates an {@link Configuration} with default config objects.
     */
    public Configuration(String name, String description) {
        mName = name;
        mDescription = description;
        mConfigMap = new LinkedHashMap<String, List<Object>>();
        setCommandOptions(new CommandOptions());
        setDeviceRequirements(new DeviceSelectionOptions());
        setDeviceOptions(new TestDeviceOptions());
        setBuildProvider(new StubBuildProvider());
        setTargetPreparer(new StubTargetPreparer());
        setTest(new StubTest());
        setDeviceRecovery(new WaitDeviceRecovery());
        setLogOutput(new StdoutLogger());
        setLogSaver(new FileSystemLogSaver());  // FileSystemLogSaver saves to tmp by default.
        setTestInvocationListener(new TextResultReporter());
    }

    /**
     * @return the name of this {@link Configuration}
     */
    public String getName() {
        return mName;
    }

    /**
     * @return a short user readable description this {@link Configuration}
     */
    public String getDescription() {
        return mDescription;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommandLine(String[] arrayArgs) {
        mCommandLine = arrayArgs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommandLine() {
        //FIXME: obfuscated passwords from command line.
        if (mCommandLine != null && mCommandLine.length != 0) {
            return QuotationAwareTokenizer.combineTokens(mCommandLine);
        }
        // If no args were available return null.
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBuildProvider getBuildProvider() {
        return (IBuildProvider)getConfigurationObject(BUILD_PROVIDER_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<ITargetPreparer> getTargetPreparers() {
        return (List<ITargetPreparer>)getConfigurationObjectList(TARGET_PREPARER_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<IRemoteTest> getTests() {
        return (List<IRemoteTest>)getConfigurationObjectList(TEST_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDeviceRecovery getDeviceRecovery() {
        return (IDeviceRecovery)getConfigurationObject(DEVICE_RECOVERY_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ILeveledLogOutput getLogOutput() {
        return (ILeveledLogOutput)getConfigurationObject(LOGGER_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ILogSaver getLogSaver() {
        return (ILogSaver)getConfigurationObject(LOG_SAVER_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public List<ITestInvocationListener> getTestInvocationListeners() {
        return (List<ITestInvocationListener>)getConfigurationObjectList(RESULT_REPORTER_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ICommandOptions getCommandOptions() {
        return (ICommandOptions)getConfigurationObject(CMD_OPTIONS_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IDeviceSelection getDeviceRequirements() {
        return (IDeviceSelection)getConfigurationObject(DEVICE_REQUIREMENTS_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestDeviceOptions getDeviceOptions() {
        return (TestDeviceOptions)getConfigurationObject(DEVICE_OPTIONS_TYPE_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<?> getConfigurationObjectList(String typeName) {
        return mConfigMap.get(typeName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getConfigurationObject(String typeName) {
        List<?> configObjects = getConfigurationObjectList(typeName);
        if (configObjects == null) {
            return null;
        }
        ObjTypeInfo typeInfo = getObjTypeMap().get(typeName);
        if (typeInfo != null && typeInfo.mIsListSupported) {
            throw new IllegalStateException(String.format("Wrong method call. " +
                    "Used getConfigurationObject() for a config object that is stored as a list",
                        typeName));
        }
        if (configObjects.size() != 1) {
            throw new IllegalStateException(String.format(
                    "Attempted to retrieve single object for %s, but %d are present",
                    typeName, configObjects.size()));
        }
        return configObjects.get(0);
    }

    /**
     * Return a copy of all config objects
     */
    private Collection<Object> getAllConfigurationObjects() {
        Collection<Object> objectsCopy = new ArrayList<Object>();
        for (List<Object> objectList : mConfigMap.values()) {
            objectsCopy.addAll(objectList);
        }
        return objectsCopy;
    }

    /**
     * Creates an OptionSetter which is appropriate for setting options on all objects which
     * will be returned by {@link getAllConfigurationObjects()}.
     */
    private OptionSetter createOptionSetter() throws ConfigurationException {
        return new OptionSetter(getAllConfigurationObjects());
    }

    private void internalInjectOptionValue(OptionSetter optionSetter, String optionName,
            String optionKey, String optionValue, String source) throws ConfigurationException {
        if (optionSetter == null) {
            throw new IllegalArgumentException("optionSetter cannot be null");
        }

        // Set all fields that match this option name / key
        List<FieldDef> affectedFields = optionSetter.setOptionValue(
                optionName, optionKey, optionValue);

        if (source != null) {
            // Update the source for each affected field
            for (FieldDef field : affectedFields) {
                // Unless the field is a Collection or MultiMap entry, it can only have one source
                if (!Collection.class.isAssignableFrom(field.field.getType()) &&
                        !MultiMap.class.isAssignableFrom(field.field.getType())) {
                    mFieldSources.remove(field);
                }
                mFieldSources.put(field, source);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void injectOptionValue(String optionName, String optionValue)
            throws ConfigurationException {
        internalInjectOptionValue(createOptionSetter(), optionName, null, optionValue, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void injectOptionValue(String optionName, String optionKey, String optionValue)
            throws ConfigurationException {
        internalInjectOptionValue(createOptionSetter(), optionName, optionKey, optionValue, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void injectOptionValueWithSource(String optionName, String optionKey, String optionValue,
            String source) throws ConfigurationException {
        internalInjectOptionValue(createOptionSetter(), optionName, optionKey, optionValue, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void injectOptionValues(List<OptionDef> optionDefs) throws ConfigurationException {
        OptionSetter optionSetter = createOptionSetter();
        for (OptionDef optionDef : optionDefs) {
            internalInjectOptionValue(optionSetter, optionDef.name, optionDef.key, optionDef.value,
                    optionDef.source);
        }
    }


    /**
     * Creates a shallow copy of this object.
     */
    @Override
    public Configuration clone() {
        Configuration clone = new Configuration(getName(), getDescription());
        for (Map.Entry<String, List<Object>> entry : mConfigMap.entrySet()) {
            clone.setConfigurationObjectListNoThrow(entry.getKey(), entry.getValue());
        }
        return clone;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuildProvider(IBuildProvider provider) {
        setConfigurationObjectNoThrow(BUILD_PROVIDER_TYPE_NAME, provider);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestInvocationListeners(List<ITestInvocationListener> listeners) {
        setConfigurationObjectListNoThrow(RESULT_REPORTER_TYPE_NAME, listeners);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestInvocationListener(ITestInvocationListener listener) {
        setConfigurationObjectNoThrow(RESULT_REPORTER_TYPE_NAME, listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTest(IRemoteTest test) {
        setConfigurationObjectNoThrow(TEST_TYPE_NAME, test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTests(List<IRemoteTest> tests) {
        setConfigurationObjectListNoThrow(TEST_TYPE_NAME, tests);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogOutput(ILeveledLogOutput logger) {
        setConfigurationObjectNoThrow(LOGGER_TYPE_NAME, logger);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogSaver(ILogSaver logSaver) {
        setConfigurationObjectNoThrow(LOG_SAVER_TYPE_NAME, logSaver);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceRecovery(IDeviceRecovery recovery) {
        setConfigurationObjectNoThrow(DEVICE_RECOVERY_TYPE_NAME, recovery);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTargetPreparer(ITargetPreparer preparer) {
        setConfigurationObjectNoThrow(TARGET_PREPARER_TYPE_NAME, preparer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommandOptions(ICommandOptions cmdOptions) {
        setConfigurationObjectNoThrow(CMD_OPTIONS_TYPE_NAME, cmdOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceRequirements(IDeviceSelection devRequirements) {
        setConfigurationObjectNoThrow(DEVICE_REQUIREMENTS_TYPE_NAME, devRequirements);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDeviceOptions(TestDeviceOptions devOptions) {
        setConfigurationObjectNoThrow(DEVICE_OPTIONS_TYPE_NAME, devOptions);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setConfigurationObject(String typeName, Object configObject)
            throws ConfigurationException {
        if (configObject == null) {
            throw new IllegalArgumentException("configObject cannot be null");
        }
        mConfigMap.remove(typeName);
        addObject(typeName, configObject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void setConfigurationObjectList(String typeName, List<?> configList)
            throws ConfigurationException {
        if (configList == null) {
            throw new IllegalArgumentException("configList cannot be null");
        }
        mConfigMap.remove(typeName);
        for (Object configObject : configList) {
            addObject(typeName, configObject);
        }
    }

    /**
     * Adds a loaded object to this configuration.
     *
     * @param typeName the unique object type name of the configuration object
     * @param configObject the configuration object
     * @throws ConfigurationException if object was not the correct type
     */
    private synchronized void addObject(String typeName, Object configObject) throws ConfigurationException {
        List<Object> objList = mConfigMap.get(typeName);
        if (objList == null) {
            objList = new ArrayList<Object>(1);
            mConfigMap.put(typeName, objList);
        }
        ObjTypeInfo typeInfo = getObjTypeMap().get(typeName);
        if (typeInfo != null && !typeInfo.mExpectedType.isInstance(configObject)) {
            throw new ConfigurationException(String.format(
                    "The config object %s is not the correct type. Expected %s, received %s",
                    typeName, typeInfo.mExpectedType.getCanonicalName(),
                    configObject.getClass().getCanonicalName()));
        }
        if (typeInfo != null && !typeInfo.mIsListSupported && objList.size() > 0) {
            throw new ConfigurationException(String.format(
                    "Only one config object allowed for %s, but multiple were specified.",
                    typeName));
        }
        objList.add(configObject);
        if (configObject instanceof IConfigurationReceiver) {
            ((IConfigurationReceiver)configObject).setConfiguration(this);
        }
    }

    /**
     * A wrapper around {@link #setConfigurationObject(String, Object)} that will not throw
     * {@link ConfigurationException}.
     * <p/>
     * Intended to be used in cases where its guaranteed that <var>configObject</var> is the
     * correct type.
     *
     * @param typeName
     * @param configObject
     */
    private void setConfigurationObjectNoThrow(String typeName, Object configObject) {
        try {
            setConfigurationObject(typeName, configObject);
        } catch (ConfigurationException e) {
            // should never happen
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * A wrapper around {@link #setConfigurationObjectList(String, List)} that will not throw
     * {@link ConfigurationException}.
     * <p/>
     * Intended to be used in cases where its guaranteed that <var>configObject</var> is the
     * correct type
     *
     * @param typeName
     * @param configObject
     */
    private void setConfigurationObjectListNoThrow(String typeName, List<?> configList) {
        try {
            setConfigurationObjectList(typeName, configList);
        } catch (ConfigurationException e) {
            // should never happen
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> setOptionsFromCommandLineArgs(List<String> listArgs)
            throws ConfigurationException {
        ArgsOptionParser parser = new ArgsOptionParser(getAllConfigurationObjects());
        return parser.parse(listArgs);
    }

    /**
     * Outputs a command line usage help text for this configuration to given printStream.
     *
     * @param out the {@link PrintStream} to use.
     * @throws {@link ConfigurationException}
     */
    @Override
    public void printCommandUsage(boolean importantOnly, PrintStream out)
            throws ConfigurationException {
        out.println(String.format("'%s' configuration: %s", getName(), getDescription()));
        out.println();
        if (importantOnly) {
            out.println("Printing help for only the important options. " +
                    "To see help for all options, use the --help-all flag");
            out.println();
        }
        for (Map.Entry<String, List<Object>> configObjectsEntry : mConfigMap.entrySet()) {
            for (Object configObject : configObjectsEntry.getValue()) {
                String optionHelp = printOptionsForObject(importantOnly,
                        configObjectsEntry.getKey(), configObject);
                // only print help for object if optionHelp is non zero length
                if (optionHelp.length() > 0) {
                    String classAlias = "";
                    if (configObject.getClass().isAnnotationPresent(OptionClass.class)) {
                        final OptionClass classAnnotation = configObject.getClass().getAnnotation(
                                OptionClass.class);
                        classAlias = String.format("'%s' ", classAnnotation.alias());
                    }
                    out.printf("  %s%s options:", classAlias, configObjectsEntry.getKey());
                    out.println();
                    out.print(optionHelp);
                    out.println();
                }
            }
        }
    }

    /**
     * Get the JSON representation of a single {@link Option} field.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private JSONObject getOptionJson(Object optionObject, Field field) throws JSONException {
        // Build a JSON representation of the option
        JSONObject jsonOption = new JSONObject();

        // Store values from the @Option annotation
        Option option = field.getAnnotation(Option.class);
        jsonOption.put("name", option.name());
        if (option.shortName() != Option.NO_SHORT_NAME) {
            jsonOption.put("shortName", option.shortName());
        }
        jsonOption.put("description", option.description());
        jsonOption.put("importance", option.importance());
        jsonOption.put("mandatory", option.mandatory());
        jsonOption.put("isTimeVal", option.isTimeVal());
        jsonOption.put("updateRule", option.updateRule().name());

        // Store the field's class
        Type fieldType = field.getGenericType();
        if (fieldType instanceof ParameterizedType) {
            // Resolve paramaterized type arguments
            Type[] paramTypes = ((ParameterizedType)fieldType).getActualTypeArguments();
            String[] paramStrings = new String[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                paramStrings[i] = ((Class<?>)paramTypes[i]).getName();
            }

            jsonOption.put("javaClass", String.format("%s<%s>",
                    field.getType().getName(), Joiner.on(", ").join(paramStrings)));
        } else {
            jsonOption.put("javaClass", field.getType().getName());
        }

        // Store the field's value
        Object value = null;
        try {
            field.setAccessible(true);
            value = field.get(optionObject);

            // Convert nulls to JSONObject.NULL
            if (value == null) {
                jsonOption.put("value", JSONObject.NULL);
            // Convert MuliMap values to a JSON representation
            } else if (value instanceof MultiMap) {
                MultiMap multimap = (MultiMap)value;
                JSONObject jsonValue = new JSONObject();
                for (Object keyObj : multimap.keySet()) {
                    jsonValue.put(keyObj.toString(), multimap.get(keyObj));
                }
                jsonOption.put("value", jsonValue);
            // Convert Map values to JSON
            } else if (value instanceof Map) {
                jsonOption.put("value", new JSONObject((Map)value));
            // For everything else, just use the default representation
            } else {
                jsonOption.put("value", value);
            }
        } catch (IllegalAccessException e) {
            // Shouldn't happen
            throw new RuntimeException(e);
        }

        // Store the field's source
        // Maps and MultiMaps track sources per key, so use a JSONObject to represent their sources
        if (Map.class.isAssignableFrom(field.getType())) {
            JSONObject jsonSourcesMap = new JSONObject();
            if (value != null) {
                // For each entry in the map, store the source as a JSONArray
                for (Object key : ((Map)value).keySet()) {
                    List<String> source = mFieldSources.get(new FieldDef(optionObject, field, key));
                    jsonSourcesMap.put(key.toString(), source == null ? new JSONArray() : source);
                }
            }
            jsonOption.put("source", jsonSourcesMap);

        } else if (MultiMap.class.isAssignableFrom(field.getType())) {
            JSONObject jsonSourcesMap = new JSONObject();
            if (value != null) {
                // For each entry in the map, store the sources as a JSONArray
                for (Object key : ((MultiMap)value).keySet()) {
                    List<String> source = mFieldSources.get(new FieldDef(optionObject, field, key));
                    jsonSourcesMap.put(key.toString(), source == null ? new JSONArray() : source);
                }
            }
            jsonOption.put("source", jsonSourcesMap);

        // Collections and regular objects only have one set of sources for the whole field, so use
        // a JSONArray
        } else {
            List<String> source = mFieldSources.get(new FieldDef(optionObject, field, null));
            jsonOption.put("source", source == null ? new JSONArray() : source);
        }

        return jsonOption;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public JSONArray getJsonCommandUsage() throws JSONException {
        JSONArray ret = new JSONArray();
        for (Map.Entry<String, List<Object>> configObjectsEntry : mConfigMap.entrySet()) {
            for (Object optionObject : configObjectsEntry.getValue()) {

                // Build a JSON representation of the current class
                JSONObject jsonClass = new JSONObject();
                jsonClass.put("name", configObjectsEntry.getKey());
                String alias = null;
                if (optionObject.getClass().isAnnotationPresent(OptionClass.class)) {
                    OptionClass optionClass =
                            optionObject.getClass().getAnnotation(OptionClass.class);
                    alias = optionClass.alias();
                }
                jsonClass.put("alias", alias == null ? JSONObject.NULL : alias);
                jsonClass.put("class", optionObject.getClass().getName());

                // For each of the @Option annotated fields
                Collection<Field> optionFields =
                        OptionSetter.getOptionFieldsForClass(optionObject.getClass());
                JSONArray jsonOptions = new JSONArray();
                for (Field field : optionFields) {
                    // Add the JSON field representation to the JSON class representation
                    jsonOptions.put(getOptionJson(optionObject, field));
                }
                jsonClass.put("options", jsonOptions);

                // Add the JSON class representation to the list
                ret.put(jsonClass);
            }
        }

        return ret;
    }

    /**
     * Prints out the available config options for given configuration object.
     *
     * @param importantOnly print only the important options
     * @param objectTypeName the config object type name. Used to generate more descriptive error
     *            messages
     * @param configObject the config object
     * @return a {@link String} of option help text
     * @throws ConfigurationException
     */
    private String printOptionsForObject(boolean importantOnly, String objectTypeName,
            Object configObject) throws ConfigurationException {
        return ArgsOptionParser.getOptionHelp(importantOnly, configObject);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateOptions() throws ConfigurationException {
        new ArgsOptionParser(getAllConfigurationObjects()).validateMandatoryOptions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void dumpXml(PrintWriter output) throws IOException {
        KXmlSerializer serializer = new KXmlSerializer();
        serializer.setOutput(output);
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        serializer.startDocument("UTF-8", null);
        serializer.startTag(null, CONFIGURATION_NAME);

        dumpClassToXml(serializer, BUILD_PROVIDER_TYPE_NAME, getBuildProvider());
        for (ITargetPreparer preparer : getTargetPreparers()) {
            dumpClassToXml(serializer, TARGET_PREPARER_TYPE_NAME, preparer);
        }
        for (IRemoteTest test : getTests()) {
            dumpClassToXml(serializer, TEST_TYPE_NAME, test);
        }
        dumpClassToXml(serializer, DEVICE_RECOVERY_TYPE_NAME, getDeviceRecovery());
        dumpClassToXml(serializer, LOGGER_TYPE_NAME, getLogOutput());
        dumpClassToXml(serializer, LOG_SAVER_TYPE_NAME, getLogSaver());
        for (ITestInvocationListener listener : getTestInvocationListeners()) {
            dumpClassToXml(serializer, RESULT_REPORTER_TYPE_NAME, listener);
        }
        dumpClassToXml(serializer, CMD_OPTIONS_TYPE_NAME, getCommandOptions());
        dumpClassToXml(serializer, DEVICE_REQUIREMENTS_TYPE_NAME, getDeviceRequirements());
        dumpClassToXml(serializer, DEVICE_OPTIONS_TYPE_NAME, getDeviceOptions());

        serializer.endTag(null, CONFIGURATION_NAME);
        serializer.endDocument();
    }

    /**
     * Add a class to the command XML dump.
     */
    private void dumpClassToXml(KXmlSerializer serializer, String classTypeName, Object obj)
            throws IOException {
        serializer.startTag(null, classTypeName);
        serializer.attribute(null, CLASS_NAME, obj.getClass().getName());
        dumpOptionsToXml(serializer, obj);
        serializer.endTag(null, classTypeName);
    }

    /**
     * Add all the options of class to the command XML dump.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void dumpOptionsToXml(KXmlSerializer serializer, Object obj) throws IOException {
        for (Field field : OptionSetter.getOptionFieldsForClass(obj.getClass())) {
            Option option = field.getAnnotation(Option.class);
            Object fieldVal = OptionSetter.getFieldValue(field, obj);
            if (fieldVal == null) {
                continue;
            } else if (fieldVal instanceof Collection) {
                for (Object entry : (Collection) fieldVal) {
                    dumpOptionToXml(serializer, option.name(), null, entry.toString());
                }
            } else if (fieldVal instanceof Map) {
                Map map = (Map) fieldVal;
                for (Object entryObj : map.entrySet()) {
                    Map.Entry entry = (Entry) entryObj;
                    dumpOptionToXml(serializer, option.name(), entry.getKey().toString(),
                            entry.getValue().toString());
                }
            } else if (fieldVal instanceof MultiMap) {
                MultiMap multimap = (MultiMap) fieldVal;
                for (Object keyObj : multimap.keySet()) {
                    for (Object valueObj : multimap.get(keyObj)) {
                        dumpOptionToXml(serializer, option.name(), keyObj.toString(),
                                valueObj.toString());
                    }
                }
            } else {
                dumpOptionToXml(serializer, option.name(), null, fieldVal.toString());
            }
        }
    }

    /**
     * Add a single option to the command XML dump.
     */
    private void dumpOptionToXml(KXmlSerializer serializer, String name, String key, String value)
            throws IOException {
        serializer.startTag(null, OPTION_NAME);
        serializer.attribute(null, NAME_NAME, name);
        if (key != null) {
            serializer.attribute(null, KEY_NAME, key);
        }
        serializer.attribute(null, VALUE_NAME, value);
        serializer.endTag(null, OPTION_NAME);
    }
}
