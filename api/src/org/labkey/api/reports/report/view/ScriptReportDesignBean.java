/*
 * Copyright (c) 2019 LabKey Corporation
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
import org.labkey.api.collections.Sets;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.report.r.RReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ScriptReport;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.writer.ContainerUser;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.api.reports.report.r.RScriptEngine.PANDOC_DEFAULT_OUTPUT_OPTIONS_LIST;

public class ScriptReportDesignBean extends ScriptReportBean
{
    private String _script;
    private boolean _sourceTabVisible;
    private List<String> _includedReports = Collections.emptyList();
    private boolean _isReadOnly;
    private ActionURL _renderURL;
    private boolean _inherited;
    private AjaxScriptReportView.Mode _mode = AjaxScriptReportView.Mode.create;  // TODO: setting value for backward compatibility -- remove
    private String _thumbnailType;
    private String _knitrFormat;
    private Boolean _useDefaultOutputFormat = null; // pandoc only
    private String _rmarkdownOutputOptions = null; // pandoc only
    private Boolean _useGetDataApi;
    private LinkedHashSet<ClientDependency> _clientDependencies;

    private final ArrayList<HtmlString> _warnings = new ArrayList<>();

    // Bean has been populated and is about to be used in a view... initialize unset values and properties.
    // This is redundant with RunScriptReportView.populateReportForm(), but we're trying to move all this handling into
    // the bean.
    public void init(ContainerUser cu, AjaxScriptReportView.Mode mode) throws Exception
    {
        setMode(mode);
        ScriptReport report = getReport(cu);

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

    public boolean isSourceTabVisible()
    {
        return _sourceTabVisible;
    }

    public void setSourceTabVisible(boolean sourceTabVisible)
    {
        _sourceTabVisible = sourceTabVisible;
    }

    public void setIncludedReports(List<String> includedReports)
    {
        _includedReports = includedReports;
    }

    public List<String> getIncludedReports()
    {
        return _includedReports;
    }

    @Override
    public ScriptReport getReport(ContainerUser cu) throws Exception
    {
        Report report = super.getReport(cu);

        // This check fails if CreateScriptReportAction is called with a non-script report ID. The crawler enjoys doing this.
        //noinspection ConstantConditions
        if (report instanceof ScriptReport)
        {
            ScriptReport scriptReport = (ScriptReport)report.clone();
            ReportDescriptor reportDescriptor = scriptReport.getDescriptor();
            ScriptReportDescriptor scriptReportDescriptor = (ScriptReportDescriptor) reportDescriptor;

            if (getScript() != null)
                scriptReportDescriptor.setProperty(ScriptReportDescriptor.Prop.script, getScript());

            if (getScriptExtension() != null)
                scriptReportDescriptor.setProperty(ScriptReportDescriptor.Prop.scriptExtension, getScriptExtension());

            scriptReportDescriptor.setProperty(ScriptReportDescriptor.Prop.sourceTabVisible, isSourceTabVisible());

            if (!isShareReport() && !scriptReportDescriptor.hasCustomAccess())
                scriptReportDescriptor.setOwner(getUser().getUserId());
            else
                scriptReportDescriptor.setOwner(null);

            if (getRedirectUrl() != null)
                scriptReportDescriptor.setProperty(ReportDescriptor.Prop.redirectUrl, getRedirectUrl());

            scriptReportDescriptor.setProperty(ScriptReportDescriptor.Prop.runInBackground, _runInBackground);

            if (getKnitrFormat() != null)
                scriptReportDescriptor.setProperty(ScriptReportDescriptor.Prop.knitrFormat, getKnitrFormat());

            scriptReportDescriptor.setProperty(ScriptReportDescriptor.Prop.useDefaultOutputFormat, isUseDefaultOutputFormat());
            scriptReportDescriptor.setProperty(ScriptReportDescriptor.Prop.rmarkdownOutputOptions, getRmarkdownOutputOptions());

            if (isUseGetDataApi() != null)
                scriptReportDescriptor.setProperty(ScriptReportDescriptor.Prop.useGetDataApi, isUseGetDataApi());

            scriptReportDescriptor.setIncludedReports(_includedReports);
            scriptReportDescriptor.setScriptDependencies(getScriptDependencies());

            return scriptReport;
        }
        else
        {
            throw new NotFoundException("Specified report is not a script report");
        }
    }

    @Override
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
        if (getRmarkdownOutputOptions() != null)
            list.add(new Pair<>(ScriptReportDescriptor.Prop.rmarkdownOutputOptions.toString(), getRmarkdownOutputOptions()));

        return list;
    }

    @Override
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
        setUseDefaultOutputFormat(null==v ? true : (Boolean) JdbcType.BOOLEAN.convert(v));
        setRmarkdownOutputOptions(descriptor.getProperty(ScriptReportDescriptor.Prop.rmarkdownOutputOptions));

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

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        return _clientDependencies;
    }

    public void setClientDependencies(LinkedHashSet<ClientDependency> clientDependencies)
    {
        _clientDependencies = clientDependencies;
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

    public AjaxScriptReportView.Mode getMode()
    {
        return _mode;
    }

    public void setMode(AjaxScriptReportView.Mode mode)
    {
        _mode = mode;
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

    public String getRmarkdownOutputOptions()
    {
        if (!isUseDefaultOutputFormat())
            return StringUtils.isEmpty(_rmarkdownOutputOptions) ? PANDOC_DEFAULT_OUTPUT_OPTIONS_LIST : _rmarkdownOutputOptions;
        return null;
    }

    public void setRmarkdownOutputOptions(String rmarkdownOutputOptions)
    {
        _rmarkdownOutputOptions = rmarkdownOutputOptions;
    }

    public void addParameters(ActionURL url, List<Pair<String, String>> params)
    {
        Set<String> set = Sets.newCaseInsensitiveHashSet();

        Arrays.stream(getClass().getDeclaredMethods()).map(Method::getName).forEach(n->{
            if (n.startsWith("is"))
                set.add(n.substring(2));
            else if (n.startsWith("get"))
                set.add(n.substring(3));
        });

        params.forEach(p->{
            if (!set.contains(p.first))
                url.addParameter(p.first, p.second);
        });
    }

    public List<HtmlString> getWarnings()
    {
        return _warnings;
    }

    public void addWarning(String warning)
    {
        _warnings.add(HtmlString.of(warning, true));
    }

    /* expect only simple formatting (e.g. <br>) */
    public void addWarning(HtmlString warning)
    {
        _warnings.add(warning);
    }
}
