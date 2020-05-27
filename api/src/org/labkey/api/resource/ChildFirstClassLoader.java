/*
 * This java source file is placed into the public domain.
 *
 * The original author is Ceki Gulcu, QOS.ch
 *
 * THIS SOFTWARE IS PROVIDED AS-IS WITHOUT WARRANTY OF ANY KIND, NOT EVEN
 * THE IMPLIED WARRANTY OF MERCHANTABILITY. THE AUTHOR OF THIS SOFTWARE,
 * ASSUMES _NO_ RESPONSIBILITY FOR ANY CONSEQUENCE RESULTING FROM THE
 * USE, MODIFICATION, OR REDISTRIBUTION OF THIS SOFTWARE.
 */

package org.labkey.api.resource;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * https://articles.qos.ch/delegation/src/java/ch/qos/ChildFirstClassLoader.java
 *
 * An almost trivial no-fuss implementation of a class loader
 * following the child-first delegation model.
 *
 * @author <a href="http://www.qos.ch/log4j/">Ceki Gulcu</a>
 */
public class ChildFirstClassLoader extends URLClassLoader
{
    public ChildFirstClassLoader(URL[] urls)
    {
        super(urls);
    }

    public ChildFirstClassLoader(URL[] urls, ClassLoader parent)
    {
        super(urls, parent);
    }

    @Override
    public void addURL(URL url)
    {
        super.addURL(url);
    }

    @Override
    public Class loadClass(String name) throws ClassNotFoundException
    {
        return loadClass(name, false);
    }

    /**
     * We override the parent-first behavior established by
     * java.lang.Classloader.
     * <p>
     * The implementation is surprisingly straightforward.
     */
    @Override
    protected Class loadClass(String name, boolean resolve) throws ClassNotFoundException
    {
        // First, check if the class has already been loaded
        Class c = findLoadedClass(name);

        // if not loaded, search the local (child) resources
        if (c == null)
        {
            try
            {
                c = findClass(name);
            }
            catch(ClassNotFoundException cnfe)
            {
                // ignore
            }
        }

        // if we could not find it, delegate to parent
        // Note that we don't attempt to catch any ClassNotFoundException
        if (c == null)
        {
            if (getParent() != null)
            {
                c = getParent().loadClass(name);
            }
            else
            {
                c = getSystemClassLoader().loadClass(name);
            }
        }

        if (resolve)
        {
            resolveClass(c);
        }

        return c;
    }

    /**
     * Override the parent-first resource loading model established by
     * java.lang.Classloader with child-first behavior.
     */
    @Override
    public URL getResource(String name)
    {
        URL url = findResource(name);

        // if local search failed, delegate to parent
        if(url == null) {
            url = getParent().getResource(name);
        }
        return url;
    }
}