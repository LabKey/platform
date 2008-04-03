package org.labkey.api.reports.report.view;

import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.RReportDescriptor;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.common.util.Pair;
import org.apache.commons.lang.StringUtils;

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
                descriptor.setProperty(RReportDescriptor.Prop.runInBackground, Boolean.toString(isRunInBackground()));
                //_includedReports = getViewContext().getList(RReportDescriptor.Prop.includedReports.toString());
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
            list.add(new Pair<String, String>(RReportDescriptor.Prop.runInBackground.toString(), Boolean.toString(_runInBackground)));
        if (_isDirty)
            list.add(new Pair<String, String>("isDirty", Boolean.toString(_isDirty)));
        for (String report : getIncludedReports())
            list.add(new Pair<String, String>(RReportDescriptor.Prop.includedReports.toString(), report));

        return list;
    }
}
