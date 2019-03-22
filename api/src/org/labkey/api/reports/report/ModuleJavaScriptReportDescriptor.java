/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.query.xml.ReportDescriptorType;

/**
 * User: Nick
 * Date: August 17, 2012
 */
public class ModuleJavaScriptReportDescriptor extends JavaScriptReportDescriptor implements ModuleReportDescriptor
{
    public static final String TYPE = "moduleJSReportDescriptor";
    public static final String FILE_EXTENSION = ".js";

    private final Module _module;
    private final Path _reportPath;
    protected final ModuleReportResource _resource;

    public ModuleJavaScriptReportDescriptor(Module module, String reportKey, Resource sourceFile, Path reportPath)
    {
        _module = module;
        _reportPath = reportPath;

        String name = sourceFile.getName().substring(0, sourceFile.getName().length() - FILE_EXTENSION.length());

        setReportKey(reportKey);
        setReportName(name);
        setDescriptorType(TYPE);
        setReportType(JavaScriptReport.TYPE);
        _resource = getModuleReportResource(sourceFile);
        loadMetaData();
        _resource.loadScript();
    }

    public ModuleReportResource getModuleReportResource(Resource sourceFile)
    {
        return new ModuleReportResource(this, sourceFile);
    }

    @Nullable
    protected ReportDescriptorType loadMetaData()
    {
        return _resource.loadMetaData();
    }

    @Override
    public Module getModule()
    {
        return _module;
    }

    @Override
    public Path getReportPath()
    {
        return _reportPath;
    }

    @Override
    public Resource getSourceFile()
    {
        return _resource.getSourceFile();
    }

    @NotNull
    @Override
    public String getResourceId()
    {
        // default the module reports to container security, the report service guarantees that
        // all module reports added will have its container set.
        return getContainerId();
    }

    @Override
    public String toString()
    {
        return "module:" + getModule().getName()  + "/" + getReportPath();
    }

    @Override
    public ReportIdentifier getReportId()
    {
        return new ModuleReportIdentifier(getModule(), getReportPath());
    }

    @Override
    public boolean isModuleBased()
    {
        return true;
    }

    @Override
    public Resource getMetaDataFile()
    {
        return _resource.getMetaDataFile();
    }
}
