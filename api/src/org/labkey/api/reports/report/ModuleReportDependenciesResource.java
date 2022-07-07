/*
 * Copyright (c) 2014-2018 LabKey Corporation
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

import org.apache.logging.log4j.LogManager;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.resource.Resource;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.query.xml.DependenciesType;
import org.labkey.query.xml.ReportDescriptorType;
import org.labkey.query.xml.ReportType;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

/**
 * User: nick
 * Date: 3/14/14
 */
public class ModuleReportDependenciesResource extends ModuleReportResource
{
    private final List<Supplier<ClientDependency>> _dependencySuppliers = new LinkedList<>();

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
    public ReportDescriptorType loadMetaData()
    {
        ReportDescriptorType d = super.loadMetaData();

        if (null != d)
        {
            try
            {
                if (d.isSetReportType())
                {
                    DependenciesType xmlDependencies = getXmlDependencies(d.getReportType());
                    if (xmlDependencies != null)
                        _dependencySuppliers.addAll(ClientDependency.getSuppliers(xmlDependencies.getDependencyArray(), getSourceFile().getName()));
                }
            }
            catch(XmlException e)
            {
                LogManager.getLogger(ModuleReportDependenciesResource.class).warn("Unable to load report metadata from file "
                        + _metaDataFile.getPath(), e);
            }
        }

        return d;
    }

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return ClientDependency.getClientDependencySet(_dependencySuppliers);
    }
}
