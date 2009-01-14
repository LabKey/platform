/*
 * Copyright (c) 2008 LabKey Corporation
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
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.view.ViewContext;
import org.labkey.common.util.Pair;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.List;

/*
* User: Dave
* Date: Dec 4, 2008
* Time: 4:04:35 PM
*/

/**
 * Represents an R report defined within a module. This will lazy-load the report script.
 */
public class ModuleRReportDescriptor extends RReportDescriptor
{
    public static final String FILE_EXTENSION = ".r";
    protected static final String REPORT_METADATA_EXTENSION = ".report.xml";

    private Module _module;
    private String _reportPath;
    private File _sourceFile;
    private long _lastModified = 0;

    public ModuleRReportDescriptor(Module module, String reportKey, File sourceFile)
    {
        String name = sourceFile.getName().substring(0, sourceFile.getName().length() -
                FILE_EXTENSION.length());

        _module = module;
        _sourceFile = sourceFile;
        _reportPath = reportKey + "/" + sourceFile.getName();
        setReportKey(reportKey);
        setReportName(name);
        setReportType(RReport.TYPE);
        loadMetaData(sourceFile);
    }

    protected void loadMetaData(File sourceFile)
    {
        //look for a file with the same base name but with an .report.xml extension
        File metaDataFile = new File(sourceFile.getParentFile(), getReportName() + REPORT_METADATA_EXTENSION);
        if(metaDataFile.exists() && metaDataFile.isFile())
        {
            try
            {
                String xml = getFileContents(metaDataFile);
                List<Pair<String,String>> props = createPropsFromXML(xml);

                if(null != props)
                    setProperties(props);
            }
            catch(IOException e)
            {
                Logger.getLogger(ModuleRReportDescriptor.class).warn("Unable to load report metadata from file "
                        + metaDataFile.getPath(), e);
            }
        }
    }

    @Override
    public String getProperty(ReportProperty prop)
    {
        //if the key = script, ensure we have it
        if(prop.equals(Prop.script))
            ensureScriptCurrent();

        return super.getProperty(prop);
    }

    @Override
    public String getProperty(String key)
    {
        //if the key = script, ensure we have it
        if(key.equalsIgnoreCase(Prop.script.name()))
            ensureScriptCurrent();

        return super.getProperty(key);
    }

    @Override
    public Map<String, Object> getProperties()
    {
        ensureScriptCurrent();
        return super.getProperties();    //To change body of overridden methods use File | Settings | File Templates.
    }

    protected void ensureScriptCurrent()
    {
        if(_sourceFile.exists() && _sourceFile.lastModified() > _lastModified)
        {
            try
            {
                String script = getFileContents(_sourceFile);
                if(null != script)
                {
                    setProperty(Prop.script, script);
                    _lastModified = _sourceFile.lastModified();
                }
            }
            catch(IOException e)
            {
                Logger.getLogger(ModuleRReportDescriptor.class).warn("Unable to load report script from source file "
                        + _sourceFile.getPath(), e);
            }
        }
    }

    protected String getFileContents(File file) throws IOException
    {
        return IOUtils.toString(new FileReader(file));
    }

    public Module getModule()
    {
        return _module;
    }

    public String getReportPath()
    {
        return _reportPath;
    }

    public File getSourceFile()
    {
        return _sourceFile;
    }

    public long getLastModified()
    {
        return _lastModified;
    }

    @Override
    public String toString()
    {
        return "module:" + getModule().getName() + "/" + getReportPath();
    }

    @Override
    public ReportIdentifier getReportId()
    {
        return new ModuleReportIdentifier(getModule(), getReportPath());
    }

    @Override
    public boolean canEdit(ViewContext context)
    {
        //module reports are always un-editable.
        return false;
    }
}