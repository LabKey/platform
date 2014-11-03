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

package org.labkey.api.reports.report.r;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * User: Karl Lum
 * Date: May 5, 2008
 */
public abstract class AbstractParamReplacement implements ParamReplacement
{
    protected String _id;
    protected String _name;
    protected File _file;
    protected Report _report;
    protected boolean _headerVisible = true;
    protected Map<String, String> _properties = Collections.emptyMap();
    protected boolean _isRemote = false;

    public AbstractParamReplacement(String id)
    {
        _id = id;
    }

    public String getId()
    {
        return _id;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public File getFile()
    {
        return _file;
    }

    public void setFile(File file)
    {
        _file = file;
    }

    public Report getReport()
    {
        return _report;
    }

    public void setReport(Report report)
    {
        _report = report;
    }

    public void setHeaderVisible(boolean headerVisible)
    {
        _headerVisible = headerVisible;
    }

    public boolean getHeaderVisible()
    {
        return _headerVisible;
    }

    public String toString()
    {
        return getName() + " (" + getId() + ")";
    }

    public Map<String, String> getProperties()
    {
        return _properties;
    }

    public void setProperties(Map<String, String> properties)
    {
        _properties = properties;
    }

    public boolean isRemote()
    {
        return _isRemote;
    }

    public void setRemote(boolean isRemote)
    {
        _isRemote = isRemote;
    }

    @Override
    public @Nullable Thumbnail renderThumbnail(ViewContext context) throws IOException
    {
        return null; // Subclasses that can should implement this
    }

    public abstract ScriptOutput renderAsScriptOutput() throws Exception;
}
