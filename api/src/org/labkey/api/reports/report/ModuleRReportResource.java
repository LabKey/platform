/*
 * Copyright (c) 2014 LabKey Corporation
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
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.query.xml.FunctionType;
import org.labkey.query.xml.FunctionsType;
import org.labkey.query.xml.ReportDescriptorType;
import org.labkey.query.xml.ReportType;

import java.util.HashSet;

/**
 * User: dax
 * Date: 6/13/14
 *
 * Knitr R reports can declare client dependencies
 * Rserve R reports can declare functions that are callable from the Report.executeFunction method
 */
public class ModuleRReportResource extends ModuleReportDependenciesResource
{
    private HashSet<String> _functions;

    public ModuleRReportResource(ReportDescriptor reportDescriptor, Resource sourceFile)
    {
        super(reportDescriptor, sourceFile);
    }

    @Override
    protected org.labkey.query.xml.DependenciesType getXmlDependencies(ReportType type) throws XmlException
    {
        if (type.isSetR())
            return type.getR().getDependencies();

        throw new XmlException("Metadata associated with a Report must have a ReportType of R");
    }

    protected org.labkey.query.xml.FunctionsType getXmlFunctions(ReportType type) throws XmlException
    {
        if (type.isSetR())
            return type.getR().getFunctions();

        throw new XmlException("Metadata associated with a Report must have a ReportType of R");
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
                    _functions = new HashSet<>();
                    FunctionsType xmlFunctions = getXmlFunctions(d.getReportType());
                    if (xmlFunctions != null)
                    {
                        for (FunctionType function : xmlFunctions.getFunctionArray())
                        {
                            String name = function.getName();
                            if (null != name && !name.isEmpty())
                                _functions.add(name);
                        }
                    }
                }
            }
            catch (XmlException e)
            {
                Logger.getLogger(ModuleRReportResource.class).warn("Unable to load R report metadata from file "
                        + _sourceFile.getPath(), e);
            }
        }

        return d;
    }

    public HashSet<String> getCallableFunctions()
    {
        if (_functions == null)
            return new HashSet<>();

        return _functions;
    }
}
