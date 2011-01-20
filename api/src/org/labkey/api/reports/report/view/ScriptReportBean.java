/*
 * Copyright (c) 2011 LabKey Corporation
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

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ScriptReport;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
* User: Karl Lum
* Date: Dec 29, 2008
* Time: 3:35:00 PM
*/
public class ScriptReportBean extends ReportDesignBean
{
    private String _script;
    protected boolean _runInBackground;
    protected boolean _isDirty;
    protected String _scriptExtension;
    private boolean _isReadOnly;
    private ActionURL _renderURL;
    private boolean _inherited;
    private List<String> _includedReports = Collections.emptyList();

    public ScriptReportBean(){}

    public ScriptReportBean(QuerySettings settings)
    {
        super(settings);
    }

    // Bean has been populated and is about to be used in a view... initialize unset values and properties.
    // This is redundant with RunScriptReportView.populateReportForm(), but we're trying to move all this handling into
    // the bean.
    public void init() throws Exception
    {
        ScriptReport report = (ScriptReport)getReport(); // TODO: Should use generics (ScriptReportBean<ScriptReport>)

        if (null == _script)
        {
            _script = report.getDefaultScript();
        }
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

            if (getScript() != null)
                descriptor.setProperty(ScriptReportDescriptor.Prop.script, getScript());

            descriptor.setProperty(ScriptReportDescriptor.Prop.scriptExtension, _scriptExtension);

            if (!isShareReport())
                descriptor.setOwner(getUser().getUserId());
            else
                descriptor.setOwner(null);

            if (getRedirectUrl() != null)
                descriptor.setProperty("redirectUrl", getRedirectUrl());

            descriptor.setProperty(ScriptReportDescriptor.Prop.runInBackground, _runInBackground);

            assert descriptor instanceof ScriptReportDescriptor;

            ((ScriptReportDescriptor)descriptor).setIncludedReports(_includedReports);
        }

        return report;
    }

    public List<Pair<String, String>> getParameters()
    {
        List<Pair<String, String>> list = super.getParameters();

        if (!StringUtils.isEmpty(_script))
            list.add(new Pair<String, String>(ScriptReportDescriptor.Prop.script.toString(), _script));
        if (_runInBackground)
            list.add(new Pair<String, String>(ScriptReportDescriptor.Prop.runInBackground.toString(), String.valueOf(_runInBackground)));
        if (_isDirty)
            list.add(new Pair<String, String>("isDirty", String.valueOf(_isDirty)));

        list.add(new Pair<String, String>(ScriptReportDescriptor.Prop.scriptExtension.toString(), _scriptExtension));

        for (String report : getIncludedReports())
            list.add(new Pair<String, String>(ScriptReportDescriptor.Prop.includedReports.toString(), report));

        return list;
    }

    void populateFromDescriptor(ReportDescriptor descriptor)
    {
        super.populateFromDescriptor(descriptor);

        setScriptExtension(descriptor.getProperty(ScriptReportDescriptor.Prop.scriptExtension));
        setScript(descriptor.getProperty(ScriptReportDescriptor.Prop.script));

        ScriptReportDescriptor srDescriptor = (ScriptReportDescriptor)descriptor;

        setRunInBackground(BooleanUtils.toBoolean(descriptor.getProperty(ScriptReportDescriptor.Prop.runInBackground)));
        setIncludedReports(srDescriptor.getIncludedReports());
    }

    Map<String, Object> getCacheableMap()
    {
        // saves report editing state in session
        Map<String, Object> map = new HashMap<String, Object>();

        for (Pair<String, String> param : getParameters())
            map.put(param.getKey(), param.getValue());

        // bad, need a better way to handle the bean type mismatch
        List<String> includedReports = getIncludedReports();

        if (!includedReports.isEmpty())
            map.put(ScriptReportDescriptor.Prop.includedReports.name(), includedReports);

        return map;
    }

    public String getScriptExtension()
    {
        return _scriptExtension;
    }

    public void setScriptExtension(String scriptExtension)
    {
        _scriptExtension = scriptExtension;
    }

    public boolean isReadOnly()
    {
        return _isReadOnly;
    }

    public void setReadOnly(boolean readOnly)
    {
        _isReadOnly = readOnly;
    }

    public ActionURL getRenderURL()
    {
        return _renderURL;
    }

    public void setRenderURL(ActionURL renderURL)
    {
        _renderURL = renderURL;
    }

    public boolean isInherited()
    {
        return _inherited;
    }

    public void setInherited(boolean inherited)
    {
        _inherited = inherited;
    }

    public void setIncludedReports(List<String> includedReports)
    {
        _includedReports = includedReports;
    }

    public List<String> getIncludedReports()
    {
        return _includedReports;
    }
}