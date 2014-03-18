/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.util.Path;
import org.labkey.query.xml.ReportDescriptorType;

import java.util.Map;

/*
* User: Dave
* Date: Dec 4, 2008
* Time: 4:04:35 PM
*/

/**
 * Represents an R report defined within a module. This will lazy-load the report script.
 */
public class ModuleRReportDescriptor extends RReportDescriptor implements ModuleReportDescriptor
{
    public static final String TYPE = "moduleRReportDescriptor";
    public static final String FILE_EXTENSION = ".r";
    public static final String KNITR_MD_EXTENSION = ".rmd";
    public static final String KNITR_HTML_EXTENSION = ".rhtml";

    private Module _module;
    private Path _reportPath;
    protected ModuleReportResource _resource;

    public ModuleRReportDescriptor(Module module, String reportKey, Resource sourceFile, Path reportPath, Container container, User user)
    {
        _module = module;
        _reportPath = reportPath;

        setReportKey(reportKey);
        setReportName(makeReportName(sourceFile));
        setDescriptorType(TYPE);
        setReportType(getDefaultReportType(reportKey));

        _resource = getModuleReportResource(sourceFile);
        loadMetaData(container, user);
    }

    public ModuleReportResource getModuleReportResource(Resource sourceFile)
    {
        return new ModuleReportResource(this, sourceFile);
    }

    public String getDefaultReportType(String reportKey)
    {
        return RReport.TYPE;
    }

    public static boolean accept(String name)
    {
        String lower = name.toLowerCase();
        return (lower.endsWith(FILE_EXTENSION) ||
                lower.endsWith(KNITR_MD_EXTENSION) ||
                lower.endsWith(KNITR_HTML_EXTENSION));
    }

    public String makeReportName(Resource sourceFile)
    {
        String ext = FILE_EXTENSION;
        String lname = sourceFile.getName().toLowerCase();

        if (lname.endsWith(KNITR_MD_EXTENSION))
        {
            setProperty(Prop.knitrFormat, KnitrFormat.Markdown.name());
            ext = KNITR_MD_EXTENSION;
        }
        else
        if (lname.endsWith(KNITR_HTML_EXTENSION))
        {
            setProperty(Prop.knitrFormat, KnitrFormat.Html.name());
            ext = KNITR_HTML_EXTENSION;
        }

        return sourceFile.getName().substring(0, lname.length() - ext.length());
    }

    @Override
    public boolean isStale()
    {
        return _resource.isStale();
    }

    @Nullable
    protected ReportDescriptorType loadMetaData(Container container, User user)
    {
        return _resource.loadMetaData(container, user);
    }

    @Override
    public String getProperty(ReportProperty prop)
    {
        //if the key = script, ensure we have it
        if (prop.equals(ScriptReportDescriptor.Prop.script))
            _resource.ensureScriptCurrent();

        return super.getProperty(prop);
    }

    @Override
    public String getProperty(String key)
    {
        //if the key = script, ensure we have it
        if (key.equalsIgnoreCase(ScriptReportDescriptor.Prop.script.name()))
            _resource.ensureScriptCurrent();

        return super.getProperty(key);
    }

    @Override
    public Map<String, Object> getProperties()
    {
        _resource.ensureScriptCurrent();
        return super.getProperties();
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
        return _resource.getSourceFile();
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

    @Override
    public Resource getMetaDataFile()
    {
        return _resource._metaDataFile;
    }
}
