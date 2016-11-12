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
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

/**
 * User: davebradlee
 * Date: 8/21/12
 * Time: 11:16 AM
 */
public class ModuleQueryReportDescriptor extends QueryReportDescriptor implements ModuleReportDescriptor
{
    public static final String TYPE = "moduleQueryReportDescriptor";
    public static final String FILE_EXTENSION = ".xml";

    private final Module _module;
    private final Path _reportPath;
    private final ModuleQueryReportResource _resource;

    public ModuleQueryReportDescriptor(Module module, String reportKey, Resource sourceFile, Path reportPath)
    {
        _module = module;
        _reportPath = reportPath;

        String name = sourceFile.getName().substring(0, sourceFile.getName().length() - FILE_EXTENSION.length());

        setReportKey(reportKey);
        setReportName(name);
        setDescriptorType(TYPE);
        setReportType(getDefaultReportType(reportKey));
        _resource = new ModuleQueryReportResource(this, sourceFile);
        loadMetaData();
    }

    public String getDefaultReportType(String reportKey)
    {
        return QueryReport.TYPE;
    }

    protected void loadMetaData()
    {
        _resource.loadMetaData();
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
}
