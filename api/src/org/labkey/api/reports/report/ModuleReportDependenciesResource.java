/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
import org.labkey.api.resource.Resource;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.clientLibrary.xml.DependencyType;
import org.labkey.query.xml.DependenciesType;
import org.labkey.query.xml.ReportDescriptorType;
import org.labkey.query.xml.ReportType;

import java.util.LinkedHashSet;

/**
 * User: nick
 * Date: 3/14/14
 */
public class ModuleReportDependenciesResource extends ModuleReportResource
{
    private static final Logger _log = Logger.getLogger(ModuleReportDependenciesResource.class);

    private LinkedHashSet<ClientDependency> _dependencies;

    public ModuleReportDependenciesResource(ReportDescriptor reportDescriptor, Resource sourceFile)
    {
        super(reportDescriptor, sourceFile);
    }

    protected DependenciesType getXmlDependencies(ReportType type) throws XmlException
    {
        if (type.isSetJavaScript())
            return type.getJavaScript().getDependencies();

        throw new XmlException("Metadata associated with a Report must have a ReportType of JavaScript");
    }

    @Override
    protected ReportDescriptorType loadMetaData()
    {
        ReportDescriptorType d = super.loadMetaData();

        if (null != d)
        {
            try
            {
                if (d.getReportType() != null)
                {
                    _dependencies = new LinkedHashSet<>();
                    DependenciesType xmlDependencies = getXmlDependencies(d.getReportType());
                    if (xmlDependencies != null)
                    {
                        for (DependencyType depend : xmlDependencies.getDependencyArray())
                        {
                            ClientDependency cd = ClientDependency.fromXML(depend);
                            if (cd != null)
                                _dependencies.add(cd);
                            else
                                _log.error("Unable to parse <dependency> tag for: " + getSourceFile().getName());
                        }
                    }
                }
            }
            catch(XmlException e)
            {
                Logger.getLogger(ModuleReportDependenciesResource.class).warn("Unable to load report metadata from file "
                        + _metaDataFile.getPath(), e);
            }
        }

        return d;
    }

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        if (_dependencies == null)
            return new LinkedHashSet<>();

        return _dependencies;
    }
}
