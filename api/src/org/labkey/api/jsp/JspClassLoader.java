/*
 * Copyright (c) 2008-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.jsp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.ConfigurationException;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production JSP loader -- loads pre-compiled classes from JSP JAR files.  No reloading or auto-recompiling.
 *
 * User: adam
 * Date: Oct 25, 2008
 */
public class JspClassLoader
{
    private static final Logger _log = LogManager.getLogger(JspClassLoader.class);
    protected static final String JSP_PACKAGE = "org.labkey.jsp.compiled";

    private final transient AtomicReference<ClassLoader> _loader = new AtomicReference<>();
    private final Set<File> jspJars = Collections.synchronizedSet(new LinkedHashSet<>());

    public JspClassLoader()
    {
        scanForJspJars();
    }

    private boolean scanForJspJars()
    {
        ServletContext context = Objects.requireNonNull(ModuleLoader.getServletContext());
        boolean added = false;

        // look in "old" location in labkey webapp
        Set<String> webinfPaths = context.getResourcePaths("/WEB-INF/jsp/");
        if (null != webinfPaths)
        {
            for (var s : webinfPaths)
                added |= jspJars.add(new File(context.getRealPath(s)));
        }

        // look for jsp jars in the {module}/lib
        for (var m : ModuleLoader.getInstance().getModules())
        {
            // use m.getExplodedPath() instead of getModuleResource() otherwise, we will look for the
            // jars in the source directory on dev machines
            var libDir = new File(m.getExplodedPath(), "lib");
            if (!libDir.exists())
                continue;
            var listing = libDir.listFiles((dir, name) -> name.contains("_jsp") && name.endsWith(".jar"));
            if (null == listing)
                continue;
            for (var file : listing)
            {
                if (file.isFile())
                    added |= jspJars.add(file);
            }
        }
        return added;
    }

    private @NotNull ClassLoader getClassLoader()
    {
        ClassLoader cl = _loader.get();
        if (null != cl)
            return cl;
        synchronized (this)
        {
            List<URL> urls = new ArrayList<>();
            for (File file : jspJars)
            {
                try
                {
                    urls.add(file.toURI().toURL());
                }
                catch (MalformedURLException mURLe)
                {
                    _log.error("initLoader exception", mURLe);
                }
            }
            cl = new URLClassLoader(urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader());
            _loader.set(cl);
        }
        return cl;
    }

    public void resetClassLoader()
    {
        if (scanForJspJars())
            _loader.set(null);
    }

    public Class loadClass(ServletContext context, String jspFile) throws ClassNotFoundException
    {
        String className = getJspClassName(jspFile);
        ClassLoader loader = getClassLoader();
        Class c = loader.loadClass(className);
        if (c == null)
            throw new ConfigurationException("Failed to load jsp class '" + className + "'; server classpath is misconfigured.");
        return c;
    }

    protected String getJspClassName(String jspFile)
    {
        return JSP_PACKAGE + getCompiledJspPath(jspFile).replaceAll("/", "\\.");
    }

    protected String getCompiledJspPath(String jspFile)
    {
        //NOTE: jasper encodes underscores and dashes in the filepath, so we account for this here
        jspFile = jspFile.replaceAll("_", "_005f");
        jspFile = jspFile.replaceAll("-", "_002d");
        return getSourceJspPath(jspFile.replaceAll("\\.", "_"));
    }

    protected String getSourceJspPath(String jspFile)
    {
        if (!jspFile.startsWith("/"))
            throw new IllegalArgumentException("Path must start with '/'");
        return jspFile;
    }
}
