/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.reports.report.view;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.query.QuerySettings;
import org.labkey.common.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.BooleanUtils;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
* User: Karl Lum
* Date: Dec 4, 2007
*/
public class RReportBean extends ReportDesignBean
{
    protected String _script;
    protected boolean _runInBackground;
    protected List<String> _includedReports;
    protected boolean _isDirty;

    public RReportBean(){}
    public RReportBean(QuerySettings settings)
    {
        super(settings);
    }
    
    public String getScript()
    {
        return _script;
    }

    public void setScript(String script)
    {
        _script = script;
    }

    public boolean isRunInBackground()
    {
        return _runInBackground;
    }

    public void setRunInBackground(boolean runInBackground)
    {
        _runInBackground = runInBackground;
    }

    public void setIncludedReports(List<String> includedReports)
    {
        _includedReports = includedReports;
    }

    public List<String> getIncludedReports()
    {
        return _includedReports != null ? _includedReports : Collections.EMPTY_LIST;
    }

    public boolean getIsDirty()
    {
        return _isDirty;
    }

    public void setIsDirty(boolean dirty)
    {
        _isDirty = dirty;
    }

    public Report getReport() throws Exception
    {
        Report report = super.getReport();

        if (report != null)
        {
            ReportDescriptor descriptor = report.getDescriptor();
            if (RReportDescriptor.class.isAssignableFrom(descriptor.getClass()))
            {
                if (getScript() != null) descriptor.setProperty(RReportDescriptor.Prop.script, getScript());
                descriptor.setProperty(RReportDescriptor.Prop.runInBackground, _runInBackground);
                ((RReportDescriptor)descriptor).setIncludedReports(_includedReports);
                if (!isShareReport())
                    descriptor.setOwner(getUser().getUserId());
                else
                    descriptor.setOwner(null);

                if (getRedirectUrl() != null)
                    descriptor.setProperty("redirectUrl", getRedirectUrl());
            }
            else
                return null;
        }
        return report;
    }

    public List<Pair<String, String>> getParameters()
    {
        List<Pair<String, String>> list = super.getParameters();

        if (!StringUtils.isEmpty(_script))
            list.add(new Pair<String, String>(RReportDescriptor.Prop.script.toString(), _script));
        if (_runInBackground)
            list.add(new Pair<String, String>(RReportDescriptor.Prop.runInBackground.toString(), String.valueOf(_runInBackground)));
        if (_isDirty)
            list.add(new Pair<String, String>("isDirty", String.valueOf(_isDirty)));
        for (String report : getIncludedReports())
            list.add(new Pair<String, String>(RReportDescriptor.Prop.includedReports.toString(), report));

        return list;
    }
}
