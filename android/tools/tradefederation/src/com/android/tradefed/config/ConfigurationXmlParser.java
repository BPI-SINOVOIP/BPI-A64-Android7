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

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Parses a configuration.xml file.
 * <p/>
 * See TODO for expected format
 */
class ConfigurationXmlParser {
    /**
     * SAX callback object. Handles parsing data from the xml tags.
     */
    static class ConfigHandler extends DefaultHandler {

        private static final String OBJECT_TAG = "object";
        private static final String OPTION_TAG = "option";
        private static final String INCLUDE_TAG = "include";
        private static final String TEMPLATE_INCLUDE_TAG = "template-include";
        private static final String CONFIG_TAG = "configuration";

        /**
         * A simple class to encapsulate a failure to resolve a &lt;template-include&gt;.  This
         * allows the error to be easily detected programmatically.
         */
        @SuppressWarnings("serial")
        private class TemplateResolutionError extends ConfigurationException {
            TemplateResolutionError(String templateName) {
                super(String.format(
                        "Failed to parse config xml '%s'. Reason: " +
                        "Couldn't resolve template-include named " +
                        "'%s': No 'default' attribute and no matching manual resolution. " +
                        "Try using argument --template:map %s (config path)",
                        mConfigDef.getName(), templateName, templateName));
            }
        }

        /** Note that this simply hasn't been implemented; it is not intentionally forbidden. */
        static final String INNER_TEMPLATE_INCLUDE_ERROR =
                "Configurations which contain a <template-include> tag, not having a 'default' " +
                "attribute, may not be the target of any <include> or <template-include> tag. " +
                "However, configuration '%s' attempted to include configuration '%s', which " +
                "contains a <template-include> tag without a 'default' attribute.";

        // Settings
        private final IConfigDefLoader mConfigDefLoader;
        private final ConfigurationDef mConfigDef;
        private final Map<String, String> mTemplateMap;
        private final String mName;

        // State-holding members
        private String mCurrentConfigObject;
        private Boolean isLocalConfig = null;

        ConfigHandler(ConfigurationDef def, String name, IConfigDefLoader loader,
                Map<String, String> templateMap) {
            mName = name;
            mConfigDef = def;
            mConfigDefLoader = loader;

            if (templateMap == null) {
                mTemplateMap = Collections.<String, String>emptyMap();
            } else {
                mTemplateMap = templateMap;
            }
        }

        @Override
        public void startElement(String uri, String localName, String name, Attributes attributes)
                throws SAXException {
            if (OBJECT_TAG.equals(localName)) {
                final String objectTypeName = attributes.getValue("type");
                addObject(objectTypeName, attributes);
            } else if (Configuration.isBuiltInObjType(localName)) {
                // tag is a built in local config object
                if (isLocalConfig == null) {
                    isLocalConfig = true;
                } else if (!isLocalConfig) {
                    throwException(String.format(
                            "Attempted to specify local object '%s' for global config!",
                            localName));
                }
                addObject(localName, attributes);
            } else if (GlobalConfiguration.isBuiltInObjType(localName)) {
                // tag is a built in global config object
                if (isLocalConfig == null) {
                    // FIXME: config type should be explicit rather than inferred
                    isLocalConfig = false;
                } else if (isLocalConfig) {
                    throwException(String.format(
                            "Attempted to specify global object '%s' for local config!",
                            localName));
                }
                addObject(localName, attributes);
            } else if (OPTION_TAG.equals(localName)) {
                String optionName = attributes.getValue("name");
                if (optionName == null) {
                    throwException("Missing 'name' attribute for option");
                }

                String optionKey = attributes.getValue("key");
                // Key is optional at this stage.  If it's actually required, another stage in the
                // configuration validation will throw an exception.

                String optionValue = attributes.getValue("value");
                if (optionValue == null) {
                    throwException("Missing 'value' attribute for option '" + optionName + "'");
                }
                if (mCurrentConfigObject != null) {
                    // option is declared within a config object - namespace it with object class
                    // name
                    optionName = String.format("%s%c%s", mCurrentConfigObject,
                            OptionSetter.NAMESPACE_SEPARATOR, optionName);
                }
                mConfigDef.addOptionDef(optionName, optionKey, optionValue, mName);
            } else if (CONFIG_TAG.equals(localName)) {
                String description = attributes.getValue("description");
                if (description != null) {
                    mConfigDef.setDescription(description);
                }
            } else if (INCLUDE_TAG.equals(localName)) {
                String includeName = attributes.getValue("name");
                if (includeName == null) {
                    throwException("Missing 'name' attribute for include");
                }
                try {
                    mConfigDefLoader.loadIncludedConfiguration(mConfigDef, mName, includeName);
                } catch (ConfigurationException e) {
                    if (e instanceof TemplateResolutionError) {
                        // The actual cause of this error is that recursive <template-include>
                        // invocations aren't currently supported.  So replace that exception
                        // with something more useful.
                        throwException(String.format(INNER_TEMPLATE_INCLUDE_ERROR,
                                mConfigDef.getName(), includeName));
                    }

                    throw new SAXException(e);
                }

            } else if (TEMPLATE_INCLUDE_TAG.equals(localName)) {
                final String templateName = attributes.getValue("name");
                if (templateName == null) {
                    throwException("Missing 'name' attribute for template-include");
                }

                String includeName = mTemplateMap.get(templateName);
                if (includeName == null) {
                    includeName = attributes.getValue("default");
                }
                if (includeName == null) {
                    throw new SAXException(new TemplateResolutionError(templateName));
                }

                try {
                    mConfigDefLoader.loadIncludedConfiguration(mConfigDef, mName, includeName);
                } catch (ConfigurationException e) {
                    if (e instanceof TemplateResolutionError) {
                        // The actual cause of this error is that recursive <template-include>
                        // invocations aren't currently supported.  So replace that exception
                        // with something more useful.
                        throwException(String.format(INNER_TEMPLATE_INCLUDE_ERROR,
                                mConfigDef.getName(), includeName));
                    }

                    throw new SAXException(e);
                }

            } else {
                throw new SAXException(String.format(
                        "Unrecognized tag '%s' in configuration", localName));
            }
        }

        @Override
        public void endElement (String uri, String localName, String qName) throws SAXException {
            if (OBJECT_TAG.equals(localName) || Configuration.isBuiltInObjType(localName)) {
                mCurrentConfigObject = null;
            }
        }

        void addObject(String objectTypeName, Attributes attributes) throws SAXException {
            String className = attributes.getValue("class");
            if (className == null) {
                throwException(String.format("Missing class attribute for object %s",
                        objectTypeName));
            }
            int classCount = mConfigDef.addConfigObjectDef(objectTypeName, className);
            mCurrentConfigObject = String.format("%s%c%d", className,
                    OptionSetter.NAMESPACE_SEPARATOR, classCount);
        }

        private void throwException(String reason) throws SAXException {
            throw new SAXException(new ConfigurationException(String.format(
                    "Failed to parse config xml '%s'. Reason: %s", mConfigDef.getName(), reason)));
        }
    }

    private final IConfigDefLoader mConfigDefLoader;

    ConfigurationXmlParser(IConfigDefLoader loader) {
        mConfigDefLoader = loader;
    }

    /**
     * Parses out configuration data contained in given input into the given configdef.
     * <p/>
     * Currently performs limited error checking.
     *
     * @param configDef the {@link ConfigurationDef} to load data into
     * @param name the name of the configuration currently being loaded. Used for logging only.
     * Can be different than configDef.getName in cases of included configs
     * @param xmlInput the configuration xml to parse
     * @throws ConfigurationException if input could not be parsed or had invalid format
     */
    void parse(ConfigurationDef configDef, String name, InputStream xmlInput,
            Map<String, String> templateMap) throws ConfigurationException {
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            SAXParser parser = parserFactory.newSAXParser();
            ConfigHandler configHandler = new ConfigHandler(configDef, name, mConfigDefLoader,
                    templateMap);
            parser.parse(new InputSource(xmlInput), configHandler);
        } catch (ParserConfigurationException e) {
            throwConfigException(name, e);
        } catch (SAXException e) {
            throwConfigException(name, e);
        } catch (IOException e) {
            throwConfigException(name, e);
        }
    }

    private void throwConfigException(String configName, Throwable e)
            throws ConfigurationException {
        if (e.getCause() instanceof ConfigurationException) {
            throw (ConfigurationException)e.getCause();
        }
        throw new ConfigurationException(String.format("Failed to parse config xml '%s' due to '%s'",
                configName, e), e);
    }
}
