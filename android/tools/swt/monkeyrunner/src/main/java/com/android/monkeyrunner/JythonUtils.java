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

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.BreakIterator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.python.core.ArgParser;
import org.python.core.ClassDictInit;
import org.python.core.Py;
import org.python.core.PyBoolean;
import org.python.core.PyDictionary;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.core.PyReflectedField;
import org.python.core.PyReflectedFunction;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.core.PyTuple;

import com.android.monkeyrunner.doc.MonkeyRunnerExported;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableMap.Builder;

/**
 * Collection of useful utilities function for interacting with the Jython interpreter.
 */
public final class JythonUtils {
    private static final Logger LOG = Logger.getLogger(JythonUtils.class.getCanonicalName());
    private JythonUtils() { }

    /**
     * Mapping of PyObject classes to the java class we want to convert them to.
     */
    private static final Map<Class<? extends PyObject>, Class<?>> PYOBJECT_TO_JAVA_OBJECT_MAP;
    static {
        Builder<Class<? extends PyObject>, Class<?>> builder = ImmutableMap.builder();

        builder.put(PyString.class, String.class);
        // What python calls float, most people call double
        builder.put(PyFloat.class, Double.class);
        builder.put(PyInteger.class, Integer.class);
        builder.put(PyBoolean.class, Boolean.class);

        PYOBJECT_TO_JAVA_OBJECT_MAP = builder.build();
    }

    /**
     * Utility method to be called from Jython bindings to give proper handling of keyword and
     * positional arguments.
     *
     * @param args the PyObject arguments from the binding
     * @param kws the keyword arguments from the binding
     * @return an ArgParser for this binding, or null on error
     */
    public static ArgParser createArgParser(PyObject[] args, String[] kws) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // Up 2 levels in the current stack to give us the calling function
        StackTraceElement element = stackTrace[2];

        String methodName = element.getMethodName();
        String className = element.getClassName();

        Class<?> clz;
        try {
            clz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOG.log(Level.SEVERE, "Got exception: ", e);
            return null;
        }

        Method m;

        try {
            m = clz.getMethod(methodName, PyObject[].class, String[].class);
        } catch (SecurityException e) {
            LOG.log(Level.SEVERE, "Got exception: ", e);
            return null;
        } catch (NoSuchMethodException e) {
            LOG.log(Level.SEVERE, "Got exception: ", e);
            return null;
        }

        MonkeyRunnerExported annotation = m.getAnnotation(MonkeyRunnerExported.class);
        return new ArgParser(methodName, args, kws,
                annotation.args());
    }

    /**
     * Get a python floating point value from an ArgParser.
     *
     * @param ap the ArgParser to get the value from.
     * @param position the position in the parser
     * @return the double value
     */
    public static double getFloat(ArgParser ap, int position) {
        PyObject arg = ap.getPyObject(position);

        if (Py.isInstance(arg, PyFloat.TYPE)) {
            return ((PyFloat) arg).asDouble();
        }
        if (Py.isInstance(arg, PyInteger.TYPE)) {
            return ((PyInteger) arg).asDouble();
        }
        throw Py.TypeError("Unable to parse argument: " + position);
    }

    /**
     * Get a python floating point value from an ArgParser.
     *
     * @param ap the ArgParser to get the value from.
     * @param position the position in the parser
     * @param defaultValue the default value to return if the arg isn't specified.
     * @return the double value
     */
    public static double getFloat(ArgParser ap, int position, double defaultValue) {
        PyObject arg = ap.getPyObject(position, new PyFloat(defaultValue));

        if (Py.isInstance(arg, PyFloat.TYPE)) {
            return ((PyFloat) arg).asDouble();
        }
        if (Py.isInstance(arg, PyInteger.TYPE)) {
            return ((PyInteger) arg).asDouble();
        }
        throw Py.TypeError("Unable to parse argument: " + position);
    }

    /**
     * Get a list of arguments from an ArgParser.
     *
     * @param ap the ArgParser
     * @param position the position in the parser to get the argument from
     * @return a list of those items
     */
    @SuppressWarnings("unchecked")
    public static List<Object> getList(ArgParser ap, int position) {
        PyObject arg = ap.getPyObject(position, Py.None);
        if (Py.isInstance(arg, PyNone.TYPE)) {
            return Collections.emptyList();
        }

        List<Object> ret = Lists.newArrayList();
        PyList array = (PyList) arg;
        for (int x = 0; x < array.__len__(); x++) {
            PyObject item = array.__getitem__(x);

            Class<?> javaClass = PYOBJECT_TO_JAVA_OBJECT_MAP.get(item.getClass());
            if (javaClass != null) {
                ret.add(item.__tojava__(javaClass));
            }
        }
        return ret;
    }

    /**
     * Get a dictionary from an ArgParser.  For ease of use, key types are always coerced to
     * strings.  If key type cannot be coeraced to string, an exception is raised.
     *
     * @param ap the ArgParser to work with
     * @param position the position in the parser to get.
     * @return a Map mapping the String key to the value
     */
    public static Map<String, Object> getMap(ArgParser ap, int position) {
        PyObject arg = ap.getPyObject(position, Py.None);
        if (Py.isInstance(arg, PyNone.TYPE)) {
            return Collections.emptyMap();
        }

        Map<String, Object> ret = Maps.newHashMap();
        // cast is safe as getPyObjectbyType ensures it
        PyDictionary dict = (PyDictionary) arg;
        PyList items = dict.items();
        for (int x = 0; x < items.__len__(); x++) {
            // It's a list of tuples
            PyTuple item = (PyTuple) items.__getitem__(x);
            // We call str(key) on the key to get the string and then convert it to the java string.
            String key = (String) item.__getitem__(0).__str__().__tojava__(String.class);
            PyObject value = item.__getitem__(1);

            // Look up the conversion type and convert the value
            Class<?> javaClass = PYOBJECT_TO_JAVA_OBJECT_MAP.get(value.getClass());
            if (javaClass != null) {
                ret.put(key, value.__tojava__(javaClass));
            }
        }
        return ret;
    }

    private static PyObject convertObject(Object o) {
        if (o instanceof String) {
            return new PyString((String) o);
        } else if (o instanceof Double) {
            return new PyFloat((Double) o);
        } else if (o instanceof Integer) {
            return new PyInteger((Integer) o);
        } else if (o instanceof Float) {
            float f = (Float) o;
            return new PyFloat(f);
        } else if (o instanceof Boolean) {
            return new PyBoolean((Boolean) o);
        }
        return Py.None;
    }

    /**
     * Convert the given Java Map into a PyDictionary.
     *
     * @param map the map to convert
     * @return the python dictionary
     */
    public static PyDictionary convertMapToDict(Map<String, Object> map) {
        Map<PyObject, PyObject> resultMap = Maps.newHashMap();

        for (Entry<String, Object> entry : map.entrySet()) {
            resultMap.put(new PyString(entry.getKey()),
                    convertObject(entry.getValue()));
        }
        return new PyDictionary(resultMap);
    }

    /**
     * This function should be called from classDictInit for any classes that are being exported
     * to jython.  This jython converts all the MonkeyRunnerExported annotations for the given class
     * into the proper python form.  It also removes any functions listed in the dictionary that
     * aren't specifically annotated in the java class.
     *
     * NOTE: Make sure the calling class implements {@link ClassDictInit} to ensure that
     * classDictInit gets called.
     *
     * @param clz the class to examine.
     * @param dict the dictionary to update.
     */
    public static void convertDocAnnotationsForClass(Class<?> clz, PyObject dict) {
      Preconditions.checkNotNull(dict);
      Preconditions.checkArgument(dict instanceof PyStringMap);

      // See if the class has the annotation
      if (clz.isAnnotationPresent(MonkeyRunnerExported.class)) {
        MonkeyRunnerExported doc = clz.getAnnotation(MonkeyRunnerExported.class);
        String fullDoc = buildClassDoc(doc, clz);
        dict.__setitem__("__doc__", new PyString(fullDoc));
      }

      // Get all the keys from the dict and put them into a set.  As we visit the annotated methods,
      // we will remove them from this set.  At the end, these are the "hidden" methods that
      // should be removed from the dict
      Collection<String> functions = Sets.newHashSet();
      for (PyObject item : dict.asIterable()) {
        functions.add(item.toString());
      }

      // And remove anything that starts with __, as those are pretty important to retain
      functions = Collections2.filter(functions, new Predicate<String>() {
        @Override
        public boolean apply(String value) {
          return !value.startsWith("__");
        }
      });

      // Look at all the methods in the class and find the one's that have the
      // @MonkeyRunnerExported annotation.
      for (Method m : clz.getMethods()) {
        if (m.isAnnotationPresent(MonkeyRunnerExported.class)) {
          String methodName = m.getName();
          PyObject pyFunc = dict.__finditem__(methodName);
          if (pyFunc != null && pyFunc instanceof PyReflectedFunction) {
            PyReflectedFunction realPyFunc = (PyReflectedFunction) pyFunc;
            MonkeyRunnerExported doc = m.getAnnotation(MonkeyRunnerExported.class);

            realPyFunc.__doc__ = new PyString(buildDoc(doc));
            functions.remove(methodName);
          }
        }
      }

      // Also look at all the fields (both static and instance).
      for (Field f : clz.getFields()) {
          if (f.isAnnotationPresent(MonkeyRunnerExported.class)) {
              String fieldName = f.getName();
              PyObject pyField = dict.__finditem__(fieldName);
              if (pyField != null && pyField instanceof PyReflectedField) {
                  PyReflectedField realPyfield = (PyReflectedField) pyField;
                MonkeyRunnerExported doc = f.getAnnotation(MonkeyRunnerExported.class);

                // TODO: figure out how to set field documentation.  __doc__ is Read Only
                // in this context.
                // realPyfield.__setattr__("__doc__", new PyString(buildDoc(doc)));
                functions.remove(fieldName);
              }
            }
      }

      // Now remove any elements left from the functions collection
      for (String name : functions) {
          dict.__delitem__(name);
      }
    }

    private static final Predicate<AccessibleObject> SHOULD_BE_DOCUMENTED = new Predicate<AccessibleObject>() {
         @Override
         public boolean apply(AccessibleObject ao) {
             return ao.isAnnotationPresent(MonkeyRunnerExported.class);
         }
    };
    private static final Predicate<Field> IS_FIELD_STATIC = new Predicate<Field>() {
        @Override
        public boolean apply(Field f) {
            return (f.getModifiers() & Modifier.STATIC) != 0;
        }
    };

    /**
     * build a jython doc-string for a class from the annotation and the fields
     * contained within the class
     *
     * @param doc the annotation
     * @param clz the class to be documented
     * @return the doc-string
     */
    private static String buildClassDoc(MonkeyRunnerExported doc, Class<?> clz) {
        // Below the class doc, we need to document all the documented field this class contains
        Collection<Field> annotatedFields = Collections2.filter(Arrays.asList(clz.getFields()), SHOULD_BE_DOCUMENTED);
        Collection<Field> staticFields = Collections2.filter(annotatedFields, IS_FIELD_STATIC);
        Collection<Field> nonStaticFields = Collections2.filter(annotatedFields, Predicates.not(IS_FIELD_STATIC));

        StringBuilder sb = new StringBuilder();
        for (String line : splitString(doc.doc(), 80)) {
            sb.append(line).append("\n");
        }

        if (staticFields.size() > 0) {
            sb.append("\nClass Fields: \n");
            for (Field f : staticFields) {
                sb.append(buildFieldDoc(f));
            }
        }

        if (nonStaticFields.size() > 0) {
            sb.append("\n\nFields: \n");
            for (Field f : nonStaticFields) {
                sb.append(buildFieldDoc(f));
            }
        }

        return sb.toString();
    }

    /**
     * Build a doc-string for the annotated field.
     *
     * @param f the field.
     * @return the doc-string.
     */
    private static String buildFieldDoc(Field f) {
       MonkeyRunnerExported annotation = f.getAnnotation(MonkeyRunnerExported.class);
       StringBuilder sb = new StringBuilder();
       int indentOffset = 2 + 3 + f.getName().length();
       String indent = makeIndent(indentOffset);

       sb.append("  ").append(f.getName()).append(" - ");

       boolean first = true;
       for (String line : splitString(annotation.doc(), 80 - indentOffset)) {
           if (first) {
               first = false;
               sb.append(line).append("\n");
           } else {
               sb.append(indent).append(line).append("\n");
           }
       }


       return sb.toString();
    }

    /**
     * Build a jython doc-string from the MonkeyRunnerExported annotation.
     *
     * @param doc the annotation to build from
     * @return a jython doc-string
     */
    private static String buildDoc(MonkeyRunnerExported doc) {
        Collection<String> docs = splitString(doc.doc(), 80);
        StringBuilder sb = new StringBuilder();
        for (String d : docs) {
            sb.append(d).append("\n");
        }

        if (doc.args() != null && doc.args().length > 0) {
            String[] args = doc.args();
            String[] argDocs = doc.argDocs();

            sb.append("\n  Args:\n");
            for (int x = 0; x < doc.args().length; x++) {
                sb.append("    ").append(args[x]);
                if (argDocs != null && argDocs.length > x) {
                    sb.append(" - ");
                    int indentOffset = args[x].length() + 3 + 4;
                    Collection<String> lines = splitString(argDocs[x], 80 - indentOffset);
                    boolean first = true;
                    String indent = makeIndent(indentOffset);
                    for (String line : lines) {
                        if (first) {
                            first = false;
                            sb.append(line).append("\n");
                        } else {
                            sb.append(indent).append(line).append("\n");
                        }
                    }
                }
            }
        }

        return sb.toString();
    }

    private static String makeIndent(int indentOffset) {
        if (indentOffset == 0) {
            return "";
        }
        StringBuffer sb = new StringBuffer();
        while (indentOffset > 0) {
            sb.append(' ');
            indentOffset--;
        }
        return sb.toString();
    }

    private static Collection<String> splitString(String source, int offset) {
        BreakIterator boundary = BreakIterator.getLineInstance();
        boundary.setText(source);

        List<String> lines = Lists.newArrayList();
        StringBuilder currentLine = new StringBuilder();
        int start = boundary.first();

        for (int end = boundary.next();
                end != BreakIterator.DONE;
                start = end, end = boundary.next()) {
            String b = source.substring(start, end);
            if (currentLine.length() + b.length() < offset) {
                currentLine.append(b);
            } else {
                // emit the old line
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(b);
            }
        }
        lines.add(currentLine.toString());
        return lines;
    }

    /**
     * Obtain the set of method names available from Python.
     *
     * @param clazz Class to inspect.
     * @return set of method names annotated with {@code MonkeyRunnerExported}.
     */
    public static Set<String> getMethodNames(Class<?> clazz) {
        HashSet<String> methodNames = new HashSet<String>();
        for (Method m: clazz.getMethods()) {
            if (m.isAnnotationPresent(MonkeyRunnerExported.class)) {
                methodNames.add(m.getName());
            }
        }
        return methodNames;
    }
}
