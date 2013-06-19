/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.query.xml.DependencyType;
import org.labkey.query.xml.JavaScriptReportDescriptorType;
import org.labkey.query.xml.ReportDescriptorType;

import java.util.LinkedHashSet;

/**
 * User: Nick
 * Date: 8/20/12
 */
public class ModuleQueryJavaScriptReportDescriptor extends ModuleJavaScriptReportDescriptor
{
    private LinkedHashSet<ClientDependency> _dependencies;

    public ModuleQueryJavaScriptReportDescriptor(Module module, String reportKey, Resource sourceFile, Path reportPath, Container container, User user)
    {
        super(module, reportKey, sourceFile, reportPath, container, user);

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
    protected ReportDescriptorType loadMetaData(Container container, User user)
    {
        ReportDescriptorType d = super.loadMetaData(container, user);

        if (null != d)
        {
            try
            {
                if (d.getReportType() != null)
                {
                    if (d.getReportType().getJavaScript() == null)
                    {
                        throw new XmlException("Metadata associated with a JavaScript Report must have a ReportType of JavaScript.");
                    }

                    JavaScriptReportDescriptorType js = d.getReportType().getJavaScript();
                    _dependencies = new LinkedHashSet<>();
                    if (js.getDependencies() != null)
                    {
                        for (DependencyType depend : js.getDependencies().getDependencyArray())
                        {
                            if (null != depend.getPath())
                                _dependencies.add(ClientDependency.fromFilePath(depend.getPath()));
                        }
                    }
                }
            }
            catch(XmlException e)
            {
                Logger.getLogger(ModuleJavaScriptReportDescriptor.class).warn("Unable to load report metadata from file "
                        + _metaDataFile.getPath(), e);
            }
        }

        return d;
    }

    @Override
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        if (null != _dependencies)
            return _dependencies;
        return new LinkedHashSet<>();
    }
}
