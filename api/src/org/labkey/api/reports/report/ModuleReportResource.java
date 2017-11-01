/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.query.xml.ReportDescriptorType;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * User: dax
 * Date: 3/14/14
 */
public class ModuleReportResource
{
    protected final Resource _sourceFile;
    protected final ReportDescriptor _reportDescriptor;
    protected final Resource _metaDataFile;

    public ModuleReportResource(ReportDescriptor reportDescriptor, Resource sourceFile)
    {
        _reportDescriptor = reportDescriptor;
        _sourceFile = sourceFile;
        Resource dir = sourceFile.parent();
        _metaDataFile = dir.find(_reportDescriptor.getReportName() + ScriptReportDescriptor.REPORT_METADATA_EXTENSION);
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
            Logger.getLogger(ModuleReportResource.class).warn("Unable to load report script from source file " + _sourceFile.getPath(), e);
        }
    }

    protected ReportDescriptorType loadMetaData()
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
                    DateFormat format = new SimpleDateFormat(DateUtil.getDateFormatString(ContainerManager.getRoot()));
                    try
                    {
                        Date createdDate = format.parse(createdDateStr);
                        _reportDescriptor.setCreated(createdDate);
                    }
                    catch (ParseException e)
                    {
                        Logger.getLogger(ModuleReportResource.class).warn("Unable to parse moduleReportCreatedDate \"" + createdDateStr + "\" from file " + _sourceFile.getPath(), e);
                    }
                }
            }
            catch(IOException e)
            {
                Logger.getLogger(ModuleReportResource.class).warn("Unable to load report metadata from file " + _metaDataFile.getPath(), e);
            }
            catch(XmlException e)
            {
                Logger.getLogger(ModuleReportResource.class).warn("Unable to load query report metadata from file " + _sourceFile.getPath(), e);
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
