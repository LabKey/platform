/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
import org.labkey.api.util.Pair;
import org.labkey.query.xml.QueryReportDescriptorType;
import org.labkey.query.xml.ReportDescriptorType;

import java.io.IOException;
import java.util.List;

/**
 * User: dax
 * Date: 3/14/14
 */
public class ModuleQueryReportResource extends ModuleReportResource
{
    public ModuleQueryReportResource(ReportDescriptor reportDescriptor, Resource sourceFile)
    {
        super(reportDescriptor, sourceFile);
    }

    @Override
    protected ReportDescriptorType loadMetaData()
    {
        ReportDescriptorType d = null;

        try
        {
            String xml = getFileContents(_sourceFile);
            d = _reportDescriptor.setDescriptorFromXML(xml);
            if (d.getReportType() != null)
            {
                if (d.getReportType().getQuery() == null)
                {
                    throw new XmlException("Metadata for a Query Report must have a ReportType of Query.");
                }

                List<Pair<String, String>> props = ReportDescriptor.createPropsFromXML(xml);

                // parse out the query report specific schema elements
                QueryReportDescriptorType queryReportDescriptorType = d.getReportType().getQuery();
                String queryName = queryReportDescriptorType.getQueryName();
                if (null != queryName)
                    props.add(new Pair<>(ReportDescriptor.Prop.queryName.name(), queryName));
                String schemaName = queryReportDescriptorType.getSchemaName();
                if (null != schemaName)
                    props.add(new Pair<>(ReportDescriptor.Prop.schemaName.name(), schemaName));
                String viewName = queryReportDescriptorType.getViewName();
                if (null != viewName)
                    props.add(new Pair<>(ReportDescriptor.Prop.viewName.name(), viewName));

                _reportDescriptor.setProperties(props);
            }
        }
        catch(IOException | XmlException e)
        {
            Logger.getLogger(ModuleQueryReportResource.class).warn("Unable to load query report metadata from file " + _sourceFile.getPath(), e);
        }

        return d;
    }
}
