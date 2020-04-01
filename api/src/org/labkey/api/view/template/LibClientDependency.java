/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.view.template;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.clientLibrary.xml.DependenciesType;
import org.labkey.clientLibrary.xml.DependencyType;
import org.labkey.clientLibrary.xml.LibrariesDocument;
import org.labkey.clientLibrary.xml.LibraryType;
import org.labkey.clientLibrary.xml.ModeTypeEnum;
import org.labkey.clientLibrary.xml.RequiredModuleType;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parses and holds dependencies from a LabKey client library (lib.xml) file, which typically reference multiple JS and/or CSS files.
 */
public class LibClientDependency extends FilePathClientDependency
{
    private static final Logger _log = Logger.getLogger(LibClientDependency.class);

    private final Resource _resource;
    private final LinkedHashSet<ClientDependency> _children = new LinkedHashSet<>();

    public LibClientDependency(Path filePath, ModeTypeEnum.Enum mode, Resource r)
    {
        super(filePath, mode, TYPE.lib);
        _resource = r;
    }

    @NotNull
    @Override
    protected Set<ClientDependency> getUniqueDependencySet(Container c)
    {
        return _children;
    }

    @Override
    protected void handleScript(Path filePath)
    {
        try
        {
            XmlOptions xmlOptions = new XmlOptions();
            Map<String,String> namespaceMap = new HashMap<>();
            namespaceMap.put("", "http://labkey.org/clientLibrary/xml/");
            xmlOptions.setLoadSubstituteNamespaces(namespaceMap);
            LibrariesDocument libDoc = LibrariesDocument.Factory.parse(_resource.getInputStream(), xmlOptions);

            if (libDoc != null && libDoc.getLibraries() != null)
            {
                //dependencies first
                DependenciesType dependencies = libDoc.getLibraries().getDependencies();
                if (dependencies != null)
                {
                    for (DependencyType s : dependencies.getDependencyArray())
                    {
                        ClientDependency cd = fromXML(s);
                        if (cd != null)
                            _children.add(cd);
                        else
                            _log.error("Unable to load <dependencies> in library " + _filePath.getName());
                    }
                }

                //module contexts
                if (libDoc.getLibraries().isSetRequiredModuleContext())
                {
                    for (RequiredModuleType mt : libDoc.getLibraries().getRequiredModuleContext().getModuleArray())
                    {
                        Module m = ModuleLoader.getInstance().getModule(mt.getName());
                        if (m == null)
                            _log.error("Unable to find module: '" + mt.getName() + "' in library " + _filePath.getName());
                        else
                            _children.add(ClientDependency.fromModule(m));
                    }
                }

                LibraryType library = libDoc.getLibraries().getLibrary();

                // <library> is an optional parameter
                if (library != null)
                {
                    boolean hasJsToCompile = false;
                    boolean hasCssToCompile = false;
                    boolean compileInProductionMode = !library.isSetCompileInProductionMode() || library.getCompileInProductionMode();

                    for (DependencyType s : library.getScriptArray())
                    {
                        ModeTypeEnum.Enum mode = s.isSetMode() ? s.getMode() : compileInProductionMode ? ModeTypeEnum.DEV : ModeTypeEnum.BOTH;
                        ClientDependency cr = fromPath(s.getPath(), mode);

                        if (!TYPE.lib.equals(cr.getPrimaryType()))
                            _children.add(cr);
                        else
                            _log.warn("Libraries cannot include other libraries: " + _filePath);

                        if (compileInProductionMode && mode != ModeTypeEnum.PRODUCTION)
                        {
                            if (TYPE.js.equals(cr.getPrimaryType()))
                                hasJsToCompile = true;
                            if (TYPE.css.equals(cr.getPrimaryType()))
                                hasCssToCompile = true;
                        }
                    }

                    //add paths to the compiled scripts we expect to have created in the build. these are production mode only
                    if (hasJsToCompile)
                    {
                        String path = filePath.toString().replaceAll(TYPE.lib.getExtension() + "$", ".min" + TYPE.js.getExtension());
                        _children.add(fromCache(path, ModeTypeEnum.PRODUCTION));
                    }

                    if (hasCssToCompile)
                    {
                        String path = filePath.toString().replaceAll(TYPE.lib.getExtension() + "$", ".min" + TYPE.css.getExtension());
                        _children.add(fromCache(path, ModeTypeEnum.PRODUCTION));
                    }
                }
            }
        }
        catch (Exception e)
        {
            _log.error("Invalid client library XML file: " + _filePath + ". " + e.getMessage());
        }
    }
}
