package org.labkey.api.reports.report;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.Path;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * User: Nick
 * Date: August 17, 2012
 */
public class ModuleJavaScriptReportDescriptor extends JavaScriptReportDescriptor
{
    public static final String TYPE = "moduleJSReportDescriptor";
    public static final String FILE_EXTENSION = ".js";
    protected static final String REPORT_METADATA_EXTENSION = ".report.xml";

    private Module _module;
    private Path _reportPath;
    private Resource _sourceFile;
    // TODO: support xml metadata
//    private Resource _metaDataFile;
    private long _sourceLastModified = 0;

    public ModuleJavaScriptReportDescriptor(Module module, String reportKey, Resource sourceFile, Path reportPath)
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
        return (_sourceLastModified != 0 && _sourceFile.getLastModified() != _sourceLastModified);
//                || (_metaDataLastModified != 0 && _metaDataFile.exists() && _metaDataFile.getLastModified() != _metaDataLastModified);
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
}
