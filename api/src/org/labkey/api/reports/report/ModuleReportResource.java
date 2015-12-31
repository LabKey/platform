/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.Container;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.query.xml.ReportDescriptorType;

import java.io.IOException;
import java.io.InputStream;

/**
 * User: dax
 * Date: 3/14/14
 */
public class ModuleReportResource
{
    protected Resource _sourceFile;
    protected long _sourceLastModified = 0;
    protected ReportDescriptor _reportDescriptor;
    protected Resource _metaDataFile;
    private long _metaDataLastModified = 0;

    public ModuleReportResource(ReportDescriptor reportDescriptor, Resource sourceFile)
    {
        _reportDescriptor = reportDescriptor;
        _sourceFile = sourceFile;
        Resource dir = sourceFile.parent();
        _metaDataFile = dir.find(_reportDescriptor.getReportName() + ScriptReportDescriptor.REPORT_METADATA_EXTENSION);
    }

    public void ensureScriptCurrent()
    {
        if (_sourceFile.exists() && _sourceFile.getLastModified() != _sourceLastModified)
        {
            try
            {
                String script = getFileContents(_sourceFile);
                if (null != script)
                {
                    _reportDescriptor.setProperty(ScriptReportDescriptor.Prop.script, script);
                    _sourceLastModified = _sourceFile.getLastModified();
                }
            }
            catch(IOException e)
            {
                Logger.getLogger(ModuleReportResource.class).warn("Unable to load report script from source file "
                        + _sourceFile.getPath(), e);
            }
        }
    }

    public boolean isStale()
    {
        //check if either source or meta-data files have changed
        //meta-data file is optional so make sure it exists before checking
        return (_sourceLastModified != 0 && _sourceFile.getLastModified() != _sourceLastModified)
                || (_metaDataLastModified != 0 && _metaDataFile.exists() && _metaDataFile.getLastModified() != _metaDataLastModified);

    }

    protected ReportDescriptorType loadMetaData(Container container, User user)
    {
        ReportDescriptorType d = null;
        if (null != _metaDataFile && _metaDataFile.isFile())
        {
            try
            {
                String xml = getFileContents(_metaDataFile);
                d = _reportDescriptor.setDescriptorFromXML(container, user, xml);

                _metaDataLastModified = _metaDataFile.getLastModified();
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
