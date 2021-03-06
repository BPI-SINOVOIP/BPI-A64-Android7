/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 *******************************************************************************
 * Copyright (C) 2004-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */

package android.icu.util;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import android.icu.impl.ICUCache;
import android.icu.impl.ICUResourceBundle;
import android.icu.impl.ICUResourceBundleReader;
import android.icu.impl.ResourceBundleWrapper;
import android.icu.impl.SimpleCache;

/**
 * <strong>[icu enhancement]</strong> ICU's replacement for {@link java.util.ResourceBundle}.&nbsp;Methods, fields, and other functionality specific to ICU are labeled '<strong>[icu]</strong>'.
 *
 * <p>A class representing a collection of resource information pertaining to a given
 * locale. A resource bundle provides a way of accessing locale- specific information in a
 * data file. You create a resource bundle that manages the resources for a given locale
 * and then ask it for individual resources.
 *
 * <p>In ResourceBundle, an object is created and the sub-items are fetched using the
 * getString and getObject methods.  In UResourceBundle, each individual element of a
 * resource is a resource by itself.
 *
 * <p>Resource bundles in ICU are currently defined using text files that conform to the
 * following <a
 * href="http://source.icu-project.org/repos/icu/icuhtml/trunk/design/bnf_rb.txt">BNF
 * definition</a>.  More on resource bundle concepts and syntax can be found in the <a
 * href="http://www.icu-project.org/userguide/ResourceManagement.html">Users Guide</a>.
 *
 * <p>The packaging of ICU *.res files can be of two types
 * ICU4C:
 * <pre>
 *       root.res
 *         |
 *      --------
 *     |        |
 *   fr.res  en.res
 *     |
 *   --------
 *  |        |
 * fr_CA.res fr_FR.res
 * </pre>
 * JAVA/JDK:
 * <pre>
 *    LocaleElements.res
 *         |
 *      -------------------
 *     |                   |
 * LocaleElements_fr.res  LocaleElements_en.res
 *     |
 *   ---------------------------
 *  |                            |
 * LocaleElements_fr_CA.res   LocaleElements_fr_FR.res
 * </pre>
 *
 * Depending on the organization of your resources, the syntax to getBundleInstance will
 * change.  To open ICU style organization use:
 *
 * <pre>
 *      UResourceBundle bundle = 
 *          UResourceBundle.getBundleInstance("com/mycompany/resources", 
 *                                            "en_US", myClassLoader);
 * </pre>
 * To open Java/JDK style organization use:
 * <pre>
 *      UResourceBundle bundle = 
 *          UResourceBundle.getBundleInstance("com.mycompany.resources.LocaleElements", 
 *                                            "en_US", myClassLoader);
 * </pre>
 *
 * <p>Note: Please use pass a class loader for loading non-ICU resources. Java security does not
 * allow loading of resources across jar files. You must provide your class loader
 * to load the resources

 * @author ram
 * @hide Only a subset of ICU is exposed in Android
 */
public abstract class UResourceBundle extends ResourceBundle {


    /**
     * <strong>[icu]</strong> Creates a resource bundle using the specified base name and locale.
     * ICU_DATA_CLASS is used as the default root.
     * @param baseName the base name of the resource bundle, a fully qualified class name
     * @param localeName the locale for which a resource bundle is desired
     * @throws MissingResourceException If no resource bundle for the specified base name
     * can be found
     * @return a resource bundle for the given base name and locale
     */
    public static UResourceBundle getBundleInstance(String baseName, String localeName){
        return getBundleInstance(baseName, localeName, ICUResourceBundle.ICU_DATA_CLASS_LOADER, 
                                 false);
    }

    /**
     * <strong>[icu]</strong> Creates a resource bundle using the specified base name, locale, and class root.
     *
     * @param baseName the base name of the resource bundle, a fully qualified class name
     * @param localeName the locale for which a resource bundle is desired
     * @param root the class object from which to load the resource bundle
     * @throws MissingResourceException If no resource bundle for the specified base name
     * can be found
     * @return a resource bundle for the given base name and locale
     */
    public static UResourceBundle getBundleInstance(String baseName, String localeName, 
                                                    ClassLoader root){
        return getBundleInstance(baseName, localeName, root, false);
    }

    /**
     * <strong>[icu]</strong> Creates a resource bundle using the specified base name, locale, and class
     * root.
     *
     * @param baseName the base name of the resource bundle, a fully qualified class name
     * @param localeName the locale for which a resource bundle is desired
     * @param root the class object from which to load the resource bundle
     * @param disableFallback Option to disable locale inheritence.
     *                          If true the fallback chain will not be built.
     * @throws MissingResourceException
     *     if no resource bundle for the specified base name can be found
     * @return a resource bundle for the given base name and locale
     *
     */
    protected static UResourceBundle getBundleInstance(String baseName, String localeName, 
                                                       ClassLoader root, boolean disableFallback) {
        return instantiateBundle(baseName, localeName, root, disableFallback);
    }

    /**
     * <strong>[icu]</strong> Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)  This is public for compatibility with Java, whose compiler
     * will generate public default constructors for an abstract class.
     */
    public UResourceBundle() {
    }

    /**
     * <strong>[icu]</strong> Creates a UResourceBundle for the locale specified, from which users can extract
     * resources by using their corresponding keys.
     * @param locale  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @return a resource bundle for the given locale
     */
    public static UResourceBundle getBundleInstance(ULocale locale) {
        if (locale==null) {
            locale = ULocale.getDefault();
        }
        return getBundleInstance(ICUResourceBundle.ICU_BASE_NAME, locale.toString(), 
                                 ICUResourceBundle.ICU_DATA_CLASS_LOADER, false);
    }

    /**
     * <strong>[icu]</strong> Creates a UResourceBundle for the default locale and specified base name,
     * from which users can extract resources by using their corresponding keys.
     * @param baseName  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @return a resource bundle for the given base name and default locale
     */
    public static UResourceBundle getBundleInstance(String baseName) {
        if (baseName == null) {
            baseName = ICUResourceBundle.ICU_BASE_NAME;
        }
        ULocale uloc = ULocale.getDefault();
        return getBundleInstance(baseName, uloc.toString(), ICUResourceBundle.ICU_DATA_CLASS_LOADER, 
                                 false);
    }

    /**
     * <strong>[icu]</strong> Creates a UResourceBundle for the specified locale and specified base name,
     * from which users can extract resources by using their corresponding keys.
     * @param baseName  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @param locale  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @return a resource bundle for the given base name and locale
     */

    public static UResourceBundle getBundleInstance(String baseName, Locale locale) {
        if (baseName == null) {
            baseName = ICUResourceBundle.ICU_BASE_NAME;
        }
        ULocale uloc = locale == null ? ULocale.getDefault() : ULocale.forLocale(locale);

        return getBundleInstance(baseName, uloc.toString(), ICUResourceBundle.ICU_DATA_CLASS_LOADER, 
                                 false);
    }

    /**
     * <strong>[icu]</strong> Creates a UResourceBundle, from which users can extract resources by using
     * their corresponding keys.
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @param locale  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @return a resource bundle for the given base name and locale
     */
    public static UResourceBundle getBundleInstance(String baseName, ULocale locale) {
        if (baseName == null) {
            baseName = ICUResourceBundle.ICU_BASE_NAME;
        }
        if (locale == null) {
            locale = ULocale.getDefault();
        }
        return getBundleInstance(baseName, locale.toString(), 
                                 ICUResourceBundle.ICU_DATA_CLASS_LOADER, false);
    }

    /**
     * <strong>[icu]</strong> Creates a UResourceBundle for the specified locale and specified base name,
     * from which users can extract resources by using their corresponding keys.
     * @param baseName  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @param locale  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @param loader  the loader to use
     * @return a resource bundle for the given base name and locale
     */
    public static UResourceBundle getBundleInstance(String baseName, Locale locale, 
                                                    ClassLoader loader) {
        if (baseName == null) {
            baseName = ICUResourceBundle.ICU_BASE_NAME;
        }
        ULocale uloc = locale == null ? ULocale.getDefault() : ULocale.forLocale(locale);
        return getBundleInstance(baseName, uloc.toString(), loader, false);
    }

    /**
     * <strong>[icu]</strong> Creates a UResourceBundle, from which users can extract resources by using
     * their corresponding keys.<br><br>
     * Note: Please use this API for loading non-ICU resources. Java security does not
     * allow loading of resources across jar files. You must provide your class loader
     * to load the resources
     * @param baseName string containing the name of the data package.
     *                    If null the default ICU package name is used.
     * @param locale  specifies the locale for which we want to open the resource.
     *                If null the bundle for default locale is opened.
     * @param loader  the loader to use
     * @return a resource bundle for the given base name and locale
     */
    public static UResourceBundle getBundleInstance(String baseName, ULocale locale, 
                                                    ClassLoader loader) {
        if (baseName == null) {
            baseName = ICUResourceBundle.ICU_BASE_NAME;
        }
        if (locale == null) {
            locale = ULocale.getDefault();
        }
        return getBundleInstance(baseName, locale.toString(), loader, false);
    }

    /**
     * <strong>[icu]</strong> Returns the RFC 3066 conformant locale id of this resource bundle.
     * This method can be used after a call to getBundleInstance() to
     * determine whether the resource bundle returned really
     * corresponds to the requested locale or is a fallback.
     *
     * @return the locale of this resource bundle
     */
    public abstract ULocale getULocale();

    /**
     * <strong>[icu]</strong> Returns the localeID
     * @return The string representation of the localeID
     */
    protected abstract String getLocaleID();

    /**
     * <strong>[icu]</strong> Returns the base name of the resource bundle
     * @return The string representation of the base name
     */
    protected abstract String getBaseName();

    /**
     * <strong>[icu]</strong> Returns the parent bundle
     * @return The parent bundle
     */
    protected abstract UResourceBundle getParent();


    /**
     * Returns the locale of this bundle
     * @return the locale of this resource bundle
     */
    public Locale getLocale(){
        return getULocale().toLocale();
    }

    // Cache for ResourceBundle instantiation
    private static ICUCache<ResourceCacheKey, UResourceBundle> BUNDLE_CACHE =
        new SimpleCache<ResourceCacheKey, UResourceBundle>();

    /**
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    public static void resetBundleCache() {
        /*
         * A HACK!!!!!
         * Currently if a resourcebundle with fallback turned ON is added to the cache
         * and then a getBundleInstance() is called for a bundle with fallback turned OFF
         * it will actually search the cache for any bundle of the same locale
         * regaurdless of fallback status. This method has been created so that if
         * The calling method KNOWS that instances of the other fallback state may be in the
         * cache, the calling method may call this method to clear out the cache.
         *
         */
        //TODO figure a way around this method(see method comment)
        BUNDLE_CACHE = new SimpleCache<ResourceCacheKey, UResourceBundle>();
    }

    /**
     * Method used by subclasses to add a resource bundle object to the managed
     * cache.  Works like a putIfAbsent(): If the cache already contains a matching
     * bundle, that one will be retained and returned.
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    protected static UResourceBundle addToCache(String fullName, ULocale defaultLocale, UResourceBundle b) {
        synchronized(cacheKey){
            cacheKey.setKeyValues(fullName, defaultLocale);
            UResourceBundle cachedBundle = BUNDLE_CACHE.get(cacheKey);
            if (cachedBundle != null) {
                return cachedBundle;
            }
            BUNDLE_CACHE.put((ResourceCacheKey)cacheKey.clone(), b);
            return b;
        }
    }

    /**
     * Method used by sub classes to load a resource bundle object from the managed cache
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    protected static UResourceBundle loadFromCache(String fullName, ULocale defaultLocale) {
        synchronized(cacheKey){
            cacheKey.setKeyValues(fullName, defaultLocale);
            return BUNDLE_CACHE.get(cacheKey);
        }
    }

    /**
     * Key used for cached resource bundles.  The key checks
     * the resource name, the class root, and the default
     * locale to determine if the resource is a match to the
     * requested one. The root may be null, but the
     * searchName and the default locale must have a non-null value.
     * Note that the default locale may change over time, and
     * lookup should always be based on the current default
     * locale (if at all).
     */
    private static final class ResourceCacheKey implements Cloneable {
        private String searchName;
        private ULocale defaultLocale;
        private int hashCodeCache;
        ///CLOVER:OFF
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (this == other) {
                return true;
            }
            try {
                final ResourceCacheKey otherEntry = (ResourceCacheKey) other;
                //quick check to see if they are not equal
                if (hashCodeCache != otherEntry.hashCodeCache) {
                    return false;
                }
                //are the names the same?
                if (!searchName.equals(otherEntry.searchName)) {
                    return false;
                }
                // are the default locales the same?
                if (defaultLocale == null) {
                    if (otherEntry.defaultLocale != null) {
                        return false;
                    }
                } else {
                    if (!defaultLocale.equals(otherEntry.defaultLocale)) {
                        return false;
                    }
                }
                return true;
            } catch (NullPointerException e) {
                return false;
            } catch (ClassCastException e) {
                return false;
            }
        }

        public int hashCode() {
            return hashCodeCache;
        }

        public Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException e) {
                //this should never happen
                throw new ICUCloneNotSupportedException(e);
            }
        }

        ///CLOVER:ON
        private synchronized void setKeyValues(String searchName, ULocale defaultLocale) {
            this.searchName = searchName;
            hashCodeCache = searchName.hashCode();
            this.defaultLocale = defaultLocale;
            if (defaultLocale != null) {
                hashCodeCache ^= defaultLocale.hashCode();
            }
        }
        /*private void clear() {
            setKeyValues(null, "", null);
        }*/
    }

    private static final ResourceCacheKey cacheKey = new ResourceCacheKey();

    private static final int ROOT_MISSING = 0;
    private static final int ROOT_ICU = 1;
    private static final int ROOT_JAVA = 2;

    private static SoftReference<ConcurrentHashMap<String, Integer>> ROOT_CACHE =
            new SoftReference<ConcurrentHashMap<String, Integer>>(new ConcurrentHashMap<String, Integer>());

    private static int getRootType(String baseName, ClassLoader root) {
        ConcurrentHashMap<String, Integer> m = null;
        Integer rootType;

        m = ROOT_CACHE.get();
        if (m == null) {
            synchronized(UResourceBundle.class) {
                m = ROOT_CACHE.get();
                if (m == null) {
                    m = new ConcurrentHashMap<String, Integer>();
                    ROOT_CACHE = new SoftReference<ConcurrentHashMap<String, Integer>>(m);
                }
            }
        }

        rootType = m.get(baseName);

        if (rootType == null) {
            String rootLocale = (baseName.indexOf('.')==-1) ? "root" : "";
            int rt = ROOT_MISSING; // value set on success
            try{
                ICUResourceBundle.getBundleInstance(baseName, rootLocale, root, true);
                rt = ROOT_ICU;
            }catch(MissingResourceException ex){
                try{
                    ResourceBundleWrapper.getBundleInstance(baseName, rootLocale, root, true);
                    rt = ROOT_JAVA;
                }catch(MissingResourceException e){
                    //throw away the exception
                }
            }

            rootType = Integer.valueOf(rt);
            m.putIfAbsent(baseName, rootType);
        }

        return rootType.intValue();
    }

    private static void setRootType(String baseName, int rootType) {
        Integer rt = Integer.valueOf(rootType);
        ConcurrentHashMap<String, Integer> m = null;

        m = ROOT_CACHE.get();
        if (m == null) {
            synchronized(UResourceBundle.class) {
                m = ROOT_CACHE.get();
                if (m == null) {
                    m = new ConcurrentHashMap<String, Integer>();
                    ROOT_CACHE = new SoftReference<ConcurrentHashMap<String, Integer>>(m);
                }
            }
        }

        m.put(baseName, rt);
    }

    /**
     * <strong>[icu]</strong> Loads a new resource bundle for the given base name, locale and class loader.
     * Optionally will disable loading of fallback bundles.
     * @param baseName the base name of the resource bundle, a fully qualified class name
     * @param localeName the locale for which a resource bundle is desired
     * @param root the class object from which to load the resource bundle
     * @param disableFallback disables loading of fallback lookup chain
     * @throws MissingResourceException If no resource bundle for the specified base name
     * can be found
     * @return a resource bundle for the given base name and locale
     */
    protected static UResourceBundle instantiateBundle(String baseName, String localeName,
                                                       ClassLoader root, boolean disableFallback) {
        UResourceBundle b = null;
        int rootType = getRootType(baseName, root);

        ULocale defaultLocale = ULocale.getDefault();

        switch (rootType)
        {
        case ROOT_ICU:
            if(disableFallback) {
                String fullName = ICUResourceBundleReader.getFullName(baseName, localeName);
                b = loadFromCache(fullName, defaultLocale);
                if (b == null) {
                    b = ICUResourceBundle.getBundleInstance(baseName, localeName, root, 
                                                            disableFallback);
                }
            } else {
                b = ICUResourceBundle.getBundleInstance(baseName, localeName, root, 
                                                        disableFallback);
            }

            return b;

        case ROOT_JAVA:
            return ResourceBundleWrapper.getBundleInstance(baseName, localeName, root, 
                                                           disableFallback);

        default:
            try{
                b = ICUResourceBundle.getBundleInstance(baseName, localeName, root, 
                                                        disableFallback);
                setRootType(baseName, ROOT_ICU);
            }catch(MissingResourceException ex){
                b = ResourceBundleWrapper.getBundleInstance(baseName, localeName, root, 
                                                            disableFallback);
                setRootType(baseName, ROOT_JAVA);
            }
            return b;
        }
    }

    /**
     * <strong>[icu]</strong> Returns a binary data item from a binary resource, as a read-only ByteBuffer.
     *
     * @return a pointer to a chunk of unsigned bytes which live in a memory mapped/DLL
     * file.
     * @see #getIntVector
     * @see #getInt
     * @throws MissingResourceException If no resource bundle can be found.
     * @throws UResourceTypeMismatchException If the resource has a type mismatch.
     */
    public ByteBuffer getBinary() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * Returns a string from a string resource type
     *
     * @return a string
     * @see #getBinary()
     * @see #getIntVector
     * @see #getInt
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle has a type mismatch.
     */
    public String getString() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * Returns a string array from a array resource type
     *
     * @return a string
     * @see #getString()
     * @see #getIntVector
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle has a type mismatch.
     */
    public String[] getStringArray() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * <strong>[icu]</strong> Returns a binary data from a binary resource, as a byte array with a copy
     * of the bytes from the resource bundle.
     *
     * @param ba  The byte array to write the bytes to. A null variable is OK.
     * @return an array of bytes containing the binary data from the resource.
     * @see #getIntVector
     * @see #getInt
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle has a type mismatch.
     */
    public byte[] getBinary(byte[] ba) {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * <strong>[icu]</strong> Returns a 32 bit integer array from a resource.
     *
     * @return a pointer to a chunk of unsigned bytes which live in a memory mapped/DLL file.
     * @see #getBinary()
     * @see #getInt
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle has a type mismatch.
     */
    public int[] getIntVector() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * <strong>[icu]</strong> Returns a signed integer from a resource.
     *
     * @return an integer value
     * @see #getIntVector
     * @see #getBinary()
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle type mismatch.
     */
    public int getInt() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * <strong>[icu]</strong> Returns a unsigned integer from a resource.
     * This integer is originally 28 bit and the sign gets propagated.
     *
     * @return an integer value
     * @see #getIntVector
     * @see #getBinary()
     * @throws MissingResourceException If resource bundle is missing.
     * @throws UResourceTypeMismatchException If resource bundle type mismatch.
     */
    public int getUInt() {
        throw new UResourceTypeMismatchException("");
    }

    /**
     * <strong>[icu]</strong> Returns a resource in a given resource that has a given key.
     *
     * @param aKey               a key associated with the wanted resource
     * @return                  a resource bundle object representing the resource
     * @throws MissingResourceException If resource bundle is missing.
     */
    public UResourceBundle get(String aKey) {
        UResourceBundle obj = findTopLevel(aKey);
        if (obj == null) {
            String fullName = ICUResourceBundleReader.getFullName(getBaseName(), getLocaleID());
            throw new MissingResourceException(
                    "Can't find resource for bundle " + fullName + ", key "
                    + aKey, this.getClass().getName(), aKey);
        }
        return obj;
    }

    /**
     * Returns a resource in a given resource that has a given key, or null if the
     * resource is not found.
     *
     * @param aKey the key associated with the wanted resource
     * @return the resource, or null
     * @see #get(String)
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    protected UResourceBundle findTopLevel(String aKey) {
        // NOTE: this only works for top-level resources.  For resources at lower
        // levels, it fails when you fall back to the parent, since you're now
        // looking at root resources, not at the corresponding nested resource.
        for (UResourceBundle res = this; res != null; res = res.getParent()) {
            UResourceBundle obj = res.handleGet(aKey, null, this);
            if (obj != null) {
                ((ICUResourceBundle) obj).setLoadingStatus(getLocaleID());
                return obj;
            }
        }
        return null;
    }

    /**
     * Returns the string in a given resource at the specified index.
     *
     * @param index an index to the wanted string.
     * @return a string which lives in the resource.
     * @throws IndexOutOfBoundsException If the index value is out of bounds of accepted values.
     * @throws UResourceTypeMismatchException If resource bundle type mismatch.
     */
    public String getString(int index) {
        ICUResourceBundle temp = (ICUResourceBundle)get(index);
        if (temp.getType() == STRING) {
            return temp.getString();
        }
        throw new UResourceTypeMismatchException("");
    }

    /**
     * <strong>[icu]</strong> Returns the resource in a given resource at the specified index.
     *
     * @param index an index to the wanted resource.
     * @return the sub resource UResourceBundle object
     * @throws IndexOutOfBoundsException If the index value is out of bounds of accepted values.
     * @throws MissingResourceException If the resource bundle is missing.
     */
    public UResourceBundle get(int index) {
        UResourceBundle obj = handleGet(index, null, this);
        if (obj == null) {
            obj = (ICUResourceBundle) getParent();
            if (obj != null) {
                obj = obj.get(index);
            }
            if (obj == null)
                throw new MissingResourceException(
                        "Can't find resource for bundle "
                                + this.getClass().getName() + ", key "
                                + getKey(), this.getClass().getName(), getKey());
        }
        ((ICUResourceBundle)obj).setLoadingStatus(getLocaleID());
        return obj;
    }

    /**
     * Returns a resource in a given resource that has a given index, or null if the
     * resource is not found.
     *
     * @param index the index of the resource
     * @return the resource, or null
     * @see #get(int)
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    protected UResourceBundle findTopLevel(int index) {
        // NOTE: this _barely_ works for top-level resources.  For resources at lower
        // levels, it fails when you fall back to the parent, since you're now
        // looking at root resources, not at the corresponding nested resource.
        // Not only that, but unless the indices correspond 1-to-1, the index will
        // lose meaning.  Essentially this only works if the child resource arrays
        // are prefixes of their parent arrays.
        for (UResourceBundle res = this; res != null; res = res.getParent()) {
            UResourceBundle obj = res.handleGet(index, null, this);
            if (obj != null) {
                ((ICUResourceBundle) obj).setLoadingStatus(getLocaleID());
                return obj;
            }
        }
        return null;
    }

    /**
     * Returns the keys in this bundle as an enumeration
     * @return an enumeration containing key strings,
     *         which is empty if this is not a bundle or a table resource
     */
    public Enumeration<String> getKeys() {
        return Collections.enumeration(keySet());
    }

    /**
     * Returns a Set of all keys contained in this ResourceBundle and its parent bundles.
     * @return a Set of all keys contained in this ResourceBundle and its parent bundles,
     *         which is empty if this is not a bundle or a table resource
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    public Set<String> keySet() {
        // TODO: Java 6 ResourceBundle has keySet() which calls handleKeySet()
        // and caches the results.
        // When we upgrade to Java 6, we still need to check for isTopLevelResource().
        // Keep the else branch as is. The if body should just return super.keySet().
        // Remove then-redundant caching of the keys.
        Set<String> keys = null;
        ICUResourceBundle icurb = null;
        if(isTopLevelResource() && this instanceof ICUResourceBundle) {
            // We do not cache the top-level keys in this base class so that
            // not every string/int/binary... resource has to have a keys cache field.
            icurb = (ICUResourceBundle)this;
            keys = icurb.getTopLevelKeySet();
        }
        if(keys == null) {
            if(isTopLevelResource()) {
                TreeSet<String> newKeySet;
                if(parent == null) {
                    newKeySet = new TreeSet<String>();
                } else if(parent instanceof UResourceBundle) {
                    newKeySet = new TreeSet<String>(((UResourceBundle)parent).keySet());
                } else {
                    // TODO: Java 6 ResourceBundle has keySet(); use it when we upgrade to Java 6
                    // and remove this else branch.
                    newKeySet = new TreeSet<String>();
                    Enumeration<String> parentKeys = parent.getKeys();
                    while(parentKeys.hasMoreElements()) {
                        newKeySet.add(parentKeys.nextElement());
                    }
                }
                newKeySet.addAll(handleKeySet());
                keys = Collections.unmodifiableSet(newKeySet);
                if(icurb != null) {
                    icurb.setTopLevelKeySet(keys);
                }
            } else {
                return handleKeySet();
            }
        }
        return keys;
    }

    /**
     * Returns a Set of the keys contained <i>only</i> in this ResourceBundle.
     * This does not include further keys from parent bundles.
     * @return a Set of the keys contained only in this ResourceBundle,
     *         which is empty if this is not a bundle or a table resource
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    protected Set<String> handleKeySet() {
        return Collections.emptySet();
    }

    /**
     * <strong>[icu]</strong> Returns the size of a resource. Size for scalar types is always 1, and for
     * vector/table types is the number of child resources.
     * 
     * <br><b>Note:</b> Integer array is treated as a scalar type. There are no APIs to
     * access individual members of an integer array. It is always returned as a whole.
     * @return number of resources in a given resource.
     */
    public int getSize() {
        return 1;
    }

    /**
     * <strong>[icu]</strong> Returns the type of a resource.
     * Available types are {@link #INT INT}, {@link #ARRAY ARRAY},
     * {@link #BINARY BINARY}, {@link #INT_VECTOR INT_VECTOR},
     * {@link #STRING STRING}, {@link #TABLE TABLE}.
     *
     * @return type of the given resource.
     */
    public int getType() {
        return NONE;
    }

    /**
     * <strong>[icu]</strong> Return the version number associated with this UResourceBundle as an
     * VersionInfo object.
     * @return VersionInfo object containing the version of the bundle
     */
    public VersionInfo getVersion() {
        return null;
    }

    /**
     * <strong>[icu]</strong> Returns the iterator which iterates over this
     * resource bundle
     * @return UResourceBundleIterator that iterates over the resources in the bundle
     */
    public UResourceBundleIterator getIterator() {
        return new UResourceBundleIterator(this);
    }

    /**
     * <strong>[icu]</strong> Returns the key associated with a given resource. Not all the resources have
     * a key - only those that are members of a table.
     * @return a key associated to this resource, or null if it doesn't have a key
     */
    public String getKey() {
        return null;
    }

    /**
     * <strong>[icu]</strong> Resource type constant for "no resource".
     */
    public static final int NONE = -1;

    /**
     * <strong>[icu]</strong> Resource type constant for strings.
     */
    public static final int STRING = 0;

    /**
     * <strong>[icu]</strong> Resource type constant for binary data.
     */
    public static final int BINARY = 1;

    /**
     * <strong>[icu]</strong> Resource type constant for tables of key-value pairs.
     */
    public static final int TABLE = 2;

    /**
     * <strong>[icu]</strong> Resource type constant for a single 28-bit integer, interpreted as
     * signed or unsigned by the getInt() function.
     * @see #getInt
     */
    public static final int INT = 7;

    /**
     * <strong>[icu]</strong> Resource type constant for arrays of resources.
     */
    public static final int ARRAY = 8;

    /**
     * Resource type constant for vectors of 32-bit integers.
     * @see #getIntVector
     */
    public static final int INT_VECTOR = 14;

    //====== protected members ==============

    /**
     * <strong>[icu]</strong> Actual worker method for fetching a resource based on the given key.
     * Sub classes must override this method if they support resources with keys.
     * @param aKey the key string of the resource to be fetched
     * @param aliasesVisited hashtable object to hold references of resources already seen
     * @param requested the original resource bundle object on which the get method was invoked.
     *                  The requested bundle and the bundle on which this method is invoked
     *                  are the same, except in the cases where aliases are involved.
     * @return UResourceBundle a resource associated with the key
     */
    protected UResourceBundle handleGet(String aKey, HashMap<String, String> aliasesVisited, 
                                        UResourceBundle requested) {
        return null;
    }

    /**
     * <strong>[icu]</strong> Actual worker method for fetching a resource based on the given index.
     * Sub classes must override this method if they support arrays of resources.
     * @param index the index of the resource to be fetched
     * @param aliasesVisited hashtable object to hold references of resources already seen
     * @param requested the original resource bundle object on which the get method was invoked.
     *                  The requested bundle and the bundle on which this method is invoked
     *                  are the same, except in the cases where aliases are involved.
     * @return UResourceBundle a resource associated with the index
     */
    protected UResourceBundle handleGet(int index, HashMap<String, String> aliasesVisited, 
                                        UResourceBundle requested) {
        return null;
    }

    /**
     * <strong>[icu]</strong> Actual worker method for fetching the array of strings in a resource.
     * Sub classes must override this method if they support arrays of strings.
     * @return String[] An array of strings containing strings
     */
    protected String[] handleGetStringArray() {
        return null;
    }

    /**
     * <strong>[icu]</strong> Actual worker method for fetching the keys of resources contained in the resource.
     * Sub classes must override this method if they support keys and associated resources.
     *
     * @return Enumeration An enumeration of all the keys in this resource.
     */
    protected Enumeration<String> handleGetKeys(){
        return null;
    }

    /**
     * {@inheritDoc}
     */
    // this method is declared in ResourceBundle class
    // so cannot change the signature
    // Override this method
    protected Object handleGetObject(String aKey) {
        return handleGetObjectImpl(aKey, this);
    }

    /**
     * Override the superclass method
     */
    // To facilitate XPath style aliases we need a way to pass the reference
    // to requested locale. The only way I could figure out is to implement
    // the look up logic here. This has a disadvantage that if the client
    // loads an ICUResourceBundle, calls ResourceBundle.getObject method
    // with a key that does not exist in the bundle then the lookup is
    // done twice before throwing a MissingResourceExpection.
    private Object handleGetObjectImpl(String aKey, UResourceBundle requested) {
        Object obj = resolveObject(aKey, requested);
        if (obj == null) {
            UResourceBundle parentBundle = getParent();
            if (parentBundle != null) {
                obj = parentBundle.handleGetObjectImpl(aKey, requested);
            }
            if (obj == null)
                throw new MissingResourceException(
                    "Can't find resource for bundle "
                    + this.getClass().getName() + ", key " + aKey,
                    this.getClass().getName(), aKey);
        }
        return obj;
    }

    // Routine for figuring out the type of object to be returned
    // string or string array
    private Object resolveObject(String aKey, UResourceBundle requested) {
        if (getType() == STRING) {
            return getString();
        }
        UResourceBundle obj = handleGet(aKey, null, requested);
        if (obj != null) {
            if (obj.getType() == STRING) {
                return obj.getString();
            }
            try {
                if (obj.getType() == ARRAY) {
                    return obj.handleGetStringArray();
                }
            } catch (UResourceTypeMismatchException ex) {
                return obj;
            }
        }
        return obj;
    }

    /**
     * This method is for setting the loading status of the resource.
     * The status is analogous to the warning status in ICU4C.
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    protected abstract void setLoadingStatus(int newStatus);

    /**
     * Is this a top-level resource, that is, a whole bundle?
     * @return true if this is a top-level resource
     * @deprecated This API is ICU internal only.
     * @hide draft / provisional / internal are hidden on Android
     */
    @Deprecated
    protected boolean isTopLevelResource() {
        return true;
    }
}
