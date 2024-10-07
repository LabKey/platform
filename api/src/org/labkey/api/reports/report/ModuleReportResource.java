/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.query.xml.ReportDescriptorType;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class ModuleReportResource
{
    private static final Logger LOG = LogHelper.getLogger(ModuleReportResource.class, "Module report loading and parsing problems");
    protected final Resource _sourceFile;
    protected final ReportDescriptor _reportDescriptor;
    protected final Resource _metaDataFile;

    public ModuleReportResource(ReportDescriptor reportDescriptor, Resource sourceFile)
    {
        _reportDescriptor = reportDescriptor;
        _sourceFile = sourceFile;
        Resource dir = sourceFile.parent();
        _metaDataFile = dir.find(Path.toPathPart(_reportDescriptor.getReportName() + ScriptReportDescriptor.REPORT_METADATA_EXTENSION));
    }

    public void loadScript()
    {
        try
        {
            String script = getFileContents(_sourceFile);
            if (null != script)
            {
                _reportDescriptor.setProperty(ScriptReportDescriptor.Prop.script, script);
            }
        }
        catch(IOException e)
        {
            LOG.warn("Unable to load report script from source file {}", _sourceFile, e);
        }
    }

    public ReportDescriptorType loadMetaData()
    {
        ReportDescriptorType d = null;
        if (null != _metaDataFile && _metaDataFile.isFile())
        {
            try
            {
                String xml = getFileContents(_metaDataFile);
                d = _reportDescriptor.setDescriptorFromXML(xml);
                String createdDateStr = _reportDescriptor.getProperty(ReportDescriptor.Prop.moduleReportCreatedDate.name());

                if (createdDateStr != null)
                {
                    try
                    {
                        Date createdDate = new Date(DateUtil.parseISODateTime(createdDateStr));
                        _reportDescriptor.setCreated(createdDate);
                    }
                    catch (Exception e)
                    {
                        LOG.warn("Unable to parse moduleReportCreatedDate \"{}\" from file {}", createdDateStr, _sourceFile, e);
                    }
                }
            }
            catch(IOException e)
            {
                LOG.warn("Unable to load report metadata from file {}", _metaDataFile, e);
            }
            catch(XmlException e)
            {
                LOG.warn("Unable to load query report metadata from file {}", _sourceFile, e);
            }
        }
        return d;
    }

    public String getFileContents(Resource file) throws IOException
    {
        try (InputStream is = file.getInputStream())
        {
            return PageFlowUtil.getStreamContentsAsString(is);
        }
    }

    public Resource getSourceFile()
    {
        return _sourceFile;
    }

    public Resource getMetaDataFile()
    {
        return _metaDataFile;
    }
}
