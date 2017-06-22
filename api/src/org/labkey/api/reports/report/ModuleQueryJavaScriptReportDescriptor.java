/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;
import org.labkey.api.view.template.ClientDependency;

import java.util.LinkedHashSet;

/**
 * User: Nick
 * Date: 8/20/12
 */
public class ModuleQueryJavaScriptReportDescriptor extends ModuleJavaScriptReportDescriptor
{
    public ModuleQueryJavaScriptReportDescriptor(Module module, String reportKey, Resource sourceFile, Path reportPath)
    {
        super(module, reportKey, sourceFile, reportPath);

        if (null == getProperty(ReportDescriptor.Prop.schemaName))
        {
            //key is <schema-name>/<query-name>
            String[] keyParts = reportKey.split("/");
            if (keyParts.length >= 2)
            {
                setProperty(ReportDescriptor.Prop.schemaName, keyParts[keyParts.length-2]);
                setProperty(ReportDescriptor.Prop.queryName, keyParts[keyParts.length-1]);
            }
        }
    }

    @Override
    public ModuleReportResource getModuleReportResource(Resource sourceFile)
    {
        return new ModuleReportDependenciesResource(this, sourceFile);
    }

    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return ((ModuleReportDependenciesResource) _resource).getClientDependencies();
    }
}
