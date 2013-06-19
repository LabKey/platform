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
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.query.xml.QueryReportDescriptorType;
import org.labkey.query.xml.ReportDescriptorType;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: davebradlee
 * Date: 8/21/12
 * Time: 11:16 AM
 */
public class ModuleQueryReportDescriptor extends QueryReportDescriptor
{
    public static final String TYPE = "moduleQueryReportDescriptor";
    public static final String FILE_EXTENSION = ".xml";

    private Module _module;
    private Path _reportPath;
    private Resource _sourceFile;
    private long _sourceLastModified = 0;
    private String _schemaName = null;
    private String _queryName = null;

    public ModuleQueryReportDescriptor(Module module, String reportKey, Resource sourceFile, Path reportPath, Container container, User user)
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
        loadMetaData(container, user);
    }

    public String getDefaultReportType(String reportKey)
    {
        return QueryReport.TYPE;
    }

    public boolean isStale()
    {
        //check if either source or meta-data files have changed
        //meta-data file is optional so make sure it exists before checking
        return (_sourceLastModified != 0 && _sourceFile.getLastModified() != _sourceLastModified);
    }

    protected void loadMetaData(Container container, User user)
    {
        try
        {
            String xml = getFileContents(_sourceFile);
            ReportDescriptorType d = setDescriptorFromXML(container, user, xml);
            if (d.getReportType() != null)
            {
                if (d.getReportType().getQuery() == null)
                {
                    throw new XmlException("Metadata for a Query Report must have a ReportType of Query.");
                }

                List<Pair<String,String>> props = createPropsFromXML(xml);

                // parse out the query report specific schema elements
                QueryReportDescriptorType queryReportDescriptorType = d.getReportType().getQuery();
                String queryName = queryReportDescriptorType.getQueryName();
                if (null != queryName)
                    props.add(new Pair<>(Prop.queryName.name(), queryName));
                String schemaName = queryReportDescriptorType.getSchemaName();
                if (null != schemaName)
                    props.add(new Pair<>(Prop.schemaName.name(), schemaName));
                String viewName = queryReportDescriptorType.getViewName();
                if (null != viewName)
                    props.add(new Pair<>(Prop.viewName.name(), viewName));

                setProperties(props);
            }

            _sourceLastModified = _sourceFile.getLastModified();
        }
        catch(IOException e)
        {
            Logger.getLogger(ModuleQueryReportDescriptor.class).warn("Unable to load query report metadata from file "
                    + _sourceFile.getPath(), e);
        }
        catch(XmlException e)
        {
            Logger.getLogger(ModuleQueryReportDescriptor.class).warn("Unable to load query report metadata from file "
                    + _sourceFile.getPath(), e);
        }
        }

    @Override
    public String getProperty(ReportDescriptor.ReportProperty prop)
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
        InputStream is = file.getInputStream();
        try
        {
            return IOUtils.toString(file.getInputStream());
        }
        finally
        {
            IOUtils.closeQuietly(is);
        }
    }

    public Module getModule()
    {
        return _module;
    }

    public Path getReportPath()
    {
        return _reportPath;
    }

    public Resource getSourceFile()
    {
        return _sourceFile;
    }

    public long getSourceLastModified()
    {
        return _sourceLastModified;
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
        return "module:" + getModule().getName() + "/" + getReportPath();
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
}
