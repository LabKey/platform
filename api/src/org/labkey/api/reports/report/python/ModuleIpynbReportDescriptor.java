/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.reports.report.python;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.reports.report.ModuleReportDescriptor;
import org.labkey.api.reports.report.ModuleReportIdentifier;
import org.labkey.api.reports.report.ModuleReportResource;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.query.xml.ReportDescriptorType;

/**
 * Represents an .ipynb report defined within a module.
 */
public class ModuleIpynbReportDescriptor extends IpynbReportDescriptor implements ModuleReportDescriptor
{
    public static final String TYPE = "moduleIpymbReportDescriptor";
    public static final String FILE_EXTENSION = ".ipynb";

    private final Module _module;
    private final Path _reportPath;

    protected final ModuleReportResource _resource;

    public ModuleIpynbReportDescriptor(Module module, String reportKey, Resource sourceFile, Path reportPath)
    {
        super(TYPE);
        _module = module;
        _reportPath = reportPath;

        setReportKey(reportKey);
        setReportName(makeReportName(sourceFile));

        _resource = getModuleReportResource(sourceFile);
        loadMetaData();
        _resource.loadScript();
    }


    public ModuleReportResource getModuleReportResource(Resource sourceFile)
    {
        return new ModuleReportResource(this, sourceFile);
    }

    public String getDefaultReportType(String reportKey)
    {
        return IpynbReport.REPORT_TYPE;
    }

    public static boolean accept(String name)
    {
        return name.toLowerCase().endsWith(FILE_EXTENSION);
    }

    public String makeReportName(Resource sourceFile)
    {
        String name = sourceFile.getName();
        return name.substring(0, name.length() - FILE_EXTENSION.length());
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
        return "module:" + getModule().getName() + "/" + getReportPath();
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
