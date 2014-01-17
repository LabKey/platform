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

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.query.xml.ReportDescriptorType;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * User: Nick
 * Date: August 17, 2012
 */
public class ModuleJavaScriptReportDescriptor extends JavaScriptReportDescriptor implements ModuleReportDescriptor
{
    public static final String TYPE = "moduleJSReportDescriptor";
    public static final String FILE_EXTENSION = ".js";

    protected Module _module;
    protected Path _reportPath;
    protected Resource _sourceFile;
    protected Resource _metaDataFile;
    protected long _sourceLastModified = 0;
    protected long _metaDataLastModified = 0;

    public ModuleJavaScriptReportDescriptor(Module module, String reportKey, Resource sourceFile, Path reportPath, Container container, User user)
    {
        _module = module;
        _sourceFile = sourceFile;
        _reportPath = reportPath;

        String name = sourceFile.getName().substring(0, sourceFile.getName().length() -
                FILE_EXTENSION.length());

        setReportKey(reportKey);
        setReportName(name);
        setDescriptorType(TYPE);
        setReportType(getDefaultReportType(reportKey));
        Resource dir = sourceFile.parent();
        _metaDataFile = dir.find(getReportName() + REPORT_METADATA_EXTENSION);
        loadMetaData(container, user);
    }

    public String getDefaultReportType(String reportKey)
    {
        return JavaScriptReport.TYPE;
    }

    @Override
    public boolean isStale()
    {
        //check if either source or meta-data files have changed
        //meta-data file is optional so make sure it exists before checking
        return (_sourceLastModified != 0 && _sourceFile.getLastModified() != _sourceLastModified)
                || (_metaDataLastModified != 0 && _metaDataFile.exists() && _metaDataFile.getLastModified() != _metaDataLastModified);
    }

    @Nullable
    protected ReportDescriptorType loadMetaData(Container container, User user)
    {
        ReportDescriptorType d = null;
        if (null != _metaDataFile && _metaDataFile.isFile())
        {
            try
            {
                String xml = getFileContents(_metaDataFile);
                d = setDescriptorFromXML(container, user, xml);

                _metaDataLastModified = _metaDataFile.getLastModified();
            }
            catch(IOException e)
            {
                Logger.getLogger(ModuleJavaScriptReportDescriptor.class).warn("Unable to load report metadata from file "
                        + _metaDataFile.getPath(), e);
            }
            catch(XmlException e)
            {
                Logger.getLogger(ModuleQueryReportDescriptor.class).warn("Unable to load query report metadata from file "
                        + _sourceFile.getPath(), e);
            }
        }
        return d;
    }

    @Override
    public String getProperty(ReportProperty prop)
    {
        //if the key = script, ensure we have it
        if (prop.equals(ScriptReportDescriptor.Prop.script))
            ensureScriptCurrent();

        return super.getProperty(prop);
    }

    @Override
    public String getProperty(String key)
    {
        //if the key = script, ensure we have it
        if (key.equalsIgnoreCase(ScriptReportDescriptor.Prop.script.name()))
            ensureScriptCurrent();

        return super.getProperty(key);
    }

    @Override
    public Map<String, Object> getProperties()
    {
        ensureScriptCurrent();
        return super.getProperties();
    }

    protected void ensureScriptCurrent()
    {
        if (_sourceFile.exists() && _sourceFile.getLastModified() != _sourceLastModified)
        {
            try
            {
                String script = getFileContents(_sourceFile);
                if (null != script)
                {
                    setProperty(ScriptReportDescriptor.Prop.script, script);
                    _sourceLastModified = _sourceFile.getLastModified();
                }
            }
            catch(IOException e)
            {
                Logger.getLogger(ModuleRReportDescriptor.class).warn("Unable to load report script from source file "
                        + _sourceFile.getPath(), e);
            }
        }
    }

    protected String getFileContents(Resource file) throws IOException
    {
        try (InputStream is = file.getInputStream())
        {
            return IOUtils.toString(is);
        }
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
        return _sourceFile;
    }

    @NotNull
    @Override
    public String getResourceId()
    {
        // default the module reports to container security, the report service guarantees that
        // all module reports added will have it's container set.
        return getContainerId();
    }

    @Override
    public String toString()
    {
        return "module:" + getModule().getName()  + "/" + getReportPath();
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

    @Override
    public Resource getMetaDataFile()
    {
        return _metaDataFile;
    }
}
