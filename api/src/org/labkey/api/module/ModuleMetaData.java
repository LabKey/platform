/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.module;

import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.util.CaseInsensitiveHashSet;
import org.apache.log4j.Logger;
import org.apache.commons.collections15.MultiMap;

import java.util.*;
import java.util.jar.Manifest;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;

/**
 * Captures the data from a module's manifest.mf, which the build generates by grabbing data from module.properties
 * User: jeckels
 * Date: Aug 6, 2008
 */
public class ModuleMetaData
{
    private static Logger _log = Logger.getLogger(ModuleMetaData.class);

    private String _name;
    private Set<String> _moduleDependencies = new CaseInsensitiveHashSet();
    private Map<String, String> _properties;

    /**
     * For unit testing only
     */
    ModuleMetaData(String name, String... dependencies)
    {
        _name = name;
        _moduleDependencies = new HashSet<String>(Arrays.asList(dependencies));
    }

    public ModuleMetaData(File file) throws IOException
    {
        _properties = new CaseInsensitiveHashMap<String>();

        String moduleName = file.getName();
        if (moduleName.endsWith(".module"))
        {
            moduleName = moduleName.substring(0, moduleName.length() - ".module".length());
        }
        _name = moduleName;

        JarFile jarFile = null;
        try
        {
            jarFile = new JarFile(file);

            JarEntry moduleEntry = jarFile.getJarEntry("META-INF/MANIFEST.MF");
            if (moduleEntry == null)
            {
                throw new IllegalArgumentException(file + " is not a valid module file - it does not contain META-INF/MANIFEST.MF");
            }

            InputStream in = null;
            try
            {
                in = jarFile.getInputStream(moduleEntry);

                // Need to use a Manifest object because doing a java.util.Properties.load(InputStream) wraps long paths
                // and we will get wrong value
                Manifest manifest = new Manifest(in);
                for(Map.Entry<Object, Object> entry : manifest.getMainAttributes().entrySet())
                {
                    _properties.put(entry.getKey().toString(),entry.getValue().toString());
                }
            }
            finally
            {
                if (in != null) { try { in.close(); } catch (IOException e) {} }
            }

            String dependenciesString = _properties.get("ModuleDependencies");
            if (dependenciesString != null)
            {
                String[] dependencies = dependenciesString.split(",");
                for (String dependency : dependencies)
                {
                    dependency = dependency.trim();
                    if (dependency.length() > 0)
                    {
                        _moduleDependencies.add(dependency);
                    }
                }
            }
        }
        finally
        {
            if (jarFile != null) { try { jarFile.close(); } catch (IOException e) {} }
        }
    }

    private String getModuleClassName()
    {
        return _properties.get("ModuleClass");
    }

    public String getName()
    {
        return _name;
    }

    public Module createModule(ClassLoader classLoader) throws ClassNotFoundException, IllegalAccessException, InstantiationException
    {
        String moduleClassName = getModuleClassName();
        if (moduleClassName == null || moduleClassName.length() == 0)
        {
            throw new IllegalArgumentException("No ModuleClass specified for " + _name);
        }

        Class<Module> clazz = (Class<Module>)classLoader.loadClass(moduleClassName);

        Module result = clazz.newInstance();
        result.setMetaData(this);
        return result;
    }

    public Set<String> getModuleDependencies()
    {
        return Collections.unmodifiableSet(_moduleDependencies);
    }

    public Map<String, String> getAllProperties()
    {
        return _properties;
    }

    public String getBuildPath()
    {
        String buildPath = _properties.get("BuildPath");

        if (null != buildPath)
        {
            return buildPath.replaceAll("\\\\\\\\","\\\\");
        }
        return null;
    }

    public String toString()
    {
        return _name.toString();
    }
}