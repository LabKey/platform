/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ScriptReport;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.view.AjaxScriptReportView.Mode;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.writer.ContainerUser;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
    private Mode _mode = Mode.create;  // TODO: setting value for backward compatibility -- remove
    private boolean _sourceTabVisible;
    private String _thumbnailType;
    private String _knitrFormat;
    private Boolean _useDefaultOutputFormat = null; // pandoc only
    private Boolean _useGetDataApi;
    private LinkedHashSet<ClientDependency> _clientDependencies;
    private String _scriptDependencies;

    public ScriptReportBean()
    {
    }

    public ScriptReportBean(QuerySettings settings)
    {
        super(settings);
    }

    // Bean has been populated and is about to be used in a view... initialize unset values and properties.
    // This is redundant with RunScriptReportView.populateReportForm(), but we're trying to move all this handling into
    // the bean.
    public void init(ContainerUser cu, Mode mode) throws Exception
    {
        setMode(mode);
        ScriptReport report = (ScriptReport)getReport(cu); // TODO: Should use generics (ScriptReportBean<ScriptReport>)

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

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return _clientDependencies;
    }

    public void setClientDependencies(LinkedHashSet<ClientDependency> clientDependencies)
    {
        _clientDependencies = clientDependencies;
    }

    public String getScriptDependencies()
    {
        return _scriptDependencies;
    }

    public void setScriptDependencies(String scriptDependencies)
    {
        _scriptDependencies = scriptDependencies;
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

    public Report getReport(ContainerUser cu) throws Exception
    {
        Report report = super.getReport(cu);

        if (report != null)
        {
            report = report.clone();
            ReportDescriptor descriptor = report.getDescriptor();

            if (getScript() != null)
                descriptor.setProperty(ScriptReportDescriptor.Prop.script, getScript());

            if (getScriptExtension() != null)
                descriptor.setProperty(ScriptReportDescriptor.Prop.scriptExtension, getScriptExtension());

            descriptor.setProperty(ScriptReportDescriptor.Prop.sourceTabVisible, isSourceTabVisible());

            if (!isShareReport() && !descriptor.hasCustomAccess())
                descriptor.setOwner(getUser().getUserId());
            else
                descriptor.setOwner(null);

            if (getRedirectUrl() != null)
                descriptor.setProperty(ReportDescriptor.Prop.redirectUrl, getRedirectUrl());

            descriptor.setProperty(ScriptReportDescriptor.Prop.runInBackground, _runInBackground);

            if (getKnitrFormat() != null)
                descriptor.setProperty(ScriptReportDescriptor.Prop.knitrFormat, getKnitrFormat());

            descriptor.setProperty(ScriptReportDescriptor.Prop.useDefaultOutputFormat, isUseDefaultOutputFormat());

            if (isUseGetDataApi() != null)
                descriptor.setProperty(ScriptReportDescriptor.Prop.useGetDataApi, isUseGetDataApi());

            assert descriptor instanceof ScriptReportDescriptor;

            ((ScriptReportDescriptor)descriptor).setIncludedReports(_includedReports);

            ((ScriptReportDescriptor)descriptor).setScriptDependencies(getScriptDependencies());
        }

        return report;
    }

    public List<Pair<String, String>> getParameters()
    {
        List<Pair<String, String>> list = super.getParameters();

        if (!StringUtils.isEmpty(_script))
            list.add(new Pair<>(ScriptReportDescriptor.Prop.script.toString(), _script));
        if (_runInBackground)
            list.add(new Pair<>(ScriptReportDescriptor.Prop.runInBackground.toString(), String.valueOf(_runInBackground)));
        if (_isDirty)
            list.add(new Pair<>("isDirty", String.valueOf(_isDirty)));
        if (_sourceTabVisible)
            list.add(new Pair<>(ScriptReportDescriptor.Prop.sourceTabVisible.toString(), String.valueOf(_sourceTabVisible)));
        if (!(getKnitrFormat().equalsIgnoreCase(RReportDescriptor.KnitrFormat.None.name())))
            list.add(new Pair<>(ScriptReportDescriptor.Prop.knitrFormat.toString(), getKnitrFormat()));

        list.add(new Pair<>(ScriptReportDescriptor.Prop.scriptExtension.toString(), _scriptExtension));

        for (String report : getIncludedReports())
            list.add(new Pair<>(ScriptReportDescriptor.Prop.includedReports.toString(), report));

        list.add(new Pair<>(ScriptReportDescriptor.Prop.useDefaultOutputFormat.toString(), isUseDefaultOutputFormat()?"true":"false"));

        return list;
    }

    void populateFromDescriptor(ReportDescriptor descriptor)
    {
        super.populateFromDescriptor(descriptor);

        setScriptExtension(descriptor.getProperty(ScriptReportDescriptor.Prop.scriptExtension));
        setScript(descriptor.getProperty(ScriptReportDescriptor.Prop.script));
        setSourceTabVisible(BooleanUtils.toBoolean(descriptor.getProperty(ScriptReportDescriptor.Prop.sourceTabVisible)));

        ScriptReportDescriptor srDescriptor = (ScriptReportDescriptor)descriptor;

        setRunInBackground(BooleanUtils.toBoolean(descriptor.getProperty(ScriptReportDescriptor.Prop.runInBackground)));
        setKnitrFormat(descriptor.getProperty(ScriptReportDescriptor.Prop.knitrFormat));
        String v = descriptor.getProperty(ScriptReportDescriptor.Prop.useDefaultOutputFormat);
        setUseDefaultOutputFormat(null==v ? true : (Boolean)JdbcType.BOOLEAN.convert(v));

        if (descriptor.getProperty(ScriptReportDescriptor.Prop.useGetDataApi) != null && descriptor.getProperty(ScriptReportDescriptor.Prop.useGetDataApi).equals("true"))
        {
            setUseGetDataApi(true);
        }
        else
        {
            setUseGetDataApi(false);
        }

        setIncludedReports(srDescriptor.getIncludedReports());
        setClientDependencies(srDescriptor.getClientDependencies());
        setScriptDependencies(srDescriptor.getScriptDependencies());

        // Module-based report won't have a thumbnail (nor a container)
        if (!descriptor.isModuleBased())
        {
            Container c = descriptor.getResourceContainer();
            if (ReportPropsManager.get().getPropertyValue(descriptor.getEntityId(), c, "thumbnailType") != null)
                setThumbnailType(ReportPropsManager.get().getPropertyValue(descriptor.getEntityId(), c, "thumbnailType").toString());
        }
    }

    Map<String, Object> getCacheableMap()
    {
        // saves report editing state in session
        Map<String, Object> map = new HashMap<>();

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
        return _isReadOnly || _mode.isReadOnly();
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

    public Mode getMode()
    {
        return _mode;
    }

    public void setMode(Mode mode)
    {
        _mode = mode;
    }

    public boolean isSourceTabVisible()
    {
        return _sourceTabVisible;
    }

    public void setSourceTabVisible(boolean sourceTabVisible)
    {
        _sourceTabVisible = sourceTabVisible;
    }

    @Nullable
    public String getThumbnailType()
    {
        return _thumbnailType;
    }

    public void setThumbnailType(String thumbnailType)
    {
        _thumbnailType = thumbnailType;
    }

    public String getKnitrFormat()
    {
        return _knitrFormat;
    }

    public void setKnitrFormat(String knitrFormat)
    {
        _knitrFormat = knitrFormat;
    }

    public boolean isUseDefaultOutputFormat()
    {
        return null==_useDefaultOutputFormat?true:_useDefaultOutputFormat;
    }

    public void setUseDefaultOutputFormat(boolean useDefaultOutputFormat)
    {
        _useDefaultOutputFormat = useDefaultOutputFormat;
    }

    public Boolean isUseGetDataApi()
    {
        return _useGetDataApi;
    }

    public void setUseGetDataApi(Boolean useGetDataApi)
    {
        _useGetDataApi = useGetDataApi;
    }
}
