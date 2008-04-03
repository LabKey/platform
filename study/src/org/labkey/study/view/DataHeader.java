package org.labkey.study.view;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.report.view.ChartDesignerBean;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.PopupMenuView;
import org.labkey.api.view.ActionURL;
import org.labkey.common.util.Pair;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.StudyManager;
import org.labkey.study.reports.ReportManager;
import org.labkey.study.reports.StudyChartQueryReport;
import org.labkey.study.reports.StudyRReport;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2006 Labkey Software. All Rights Reserved.
 * User: migra
 * Date: Apr 6, 2006
 * Time: 11:36:46 AM
 */
public class DataHeader extends HttpView
{
    private ActionURL _currentUrl;
    private ActionURL _customizeURL;
    private DataSetDefinition _datasetDef;
    private boolean _gridView;
    private Cohort _selectedCohort = null;
    private boolean _showCohortSelector;

    public DataHeader(ActionURL currentUrl, ActionURL customizeURL, DataSetDefinition dataset, boolean gridView)
    {
        _currentUrl = currentUrl;
        _customizeURL = customizeURL;
        _datasetDef = dataset;
        _gridView = gridView;
    }

    public boolean isShowCohortSelector()
    {
        return StudyManager.getInstance().showCohorts(getViewContext().getContainer(), getViewContext().getUser()) && _showCohortSelector;
    }

    public void setShowCohortSelector(boolean showCohortSelector)
    {
        _showCohortSelector = showCohortSelector;
    }

    public Cohort getSelectedCohort()
    {
        return _selectedCohort;
    }

    public void setSelectedCohort(Cohort selectedCohort)
    {
        if (selectedCohort != null && !StudyManager.getInstance().showCohorts(getViewContext().getContainer(), getViewContext().getUser()))
            throw new IllegalStateException("User does not have permissions to view cohort information.");
        _selectedCohort = selectedCohort;
    }

    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
        if (null != StringUtils.trimToNull(_datasetDef.getDescription()))
        {
            out.print("<div class=normal style=\"width:640px\">");
            out.print(PageFlowUtil.filter(_datasetDef.getDescription()));
            out.print("</div>");
        }

        out.print("<br/>");
        out.print("<form method=GET action=\"" + PageFlowUtil.filter(_currentUrl.relativeUrl("datasetReport", null, "Study", true)) + "\">");

        // output all URL parameters that are on the current URL as hidden fields.  we may not need them all,
        // but this guarantees that we get all sort and filter parameters.
        ActionURL sortFilterURL = PageFlowUtil.expandLastFilter(_currentUrl);
        for (Pair<String, String> parameter : sortFilterURL.getParameters())
        {
            if (!"Dataset.viewName".equalsIgnoreCase(parameter.getKey()) &&
                !"prevView".equalsIgnoreCase(parameter.getKey()) &&
                !"cohortId".equalsIgnoreCase(parameter.getKey()) &&
                !"participantId".equalsIgnoreCase(parameter.getKey()) &&
                !"tabId".equalsIgnoreCase(parameter.getKey()) &&
                !"reportId".equalsIgnoreCase(parameter.getKey()) &&
                !"cacheKey".equalsIgnoreCase(parameter.getKey()))
            {
                out.print("\n<input type=\"hidden\" name=\"" + PageFlowUtil.filter(parameter.getKey()) +
                        "\" value=\"" + PageFlowUtil.filter(parameter.getValue()) + "\">");
            }
        }

        String viewName = getViewContext().getRequest().getParameter("Dataset.viewName");

        // make sure dataset id is on the url
        if (getViewContext().getActionURL().getParameter(DataSetDefinition.DATASETKEY) == null)
            out.printf("<input type=\"hidden\" name=\"%s\" value=\"%s\">", DataSetDefinition.DATASETKEY, _datasetDef.getRowId());

/*
        if (viewName != null)
            out.printf("<input type=hidden name=\"prevView\" value=\"%s\">", viewName);
*/
        out.print("View&nbsp;");

        List<Pair<String, String>> reportLabels = ReportManager.get().getReportLabelsForDataset(getViewContext(), _datasetDef);
        out.print("<select name=\"Dataset.viewName\" onchange=\"this.form.submit()\">");
        for (Pair<String, String> label : reportLabels)
        {
            if (label == null)
                continue;

            out.printf("<option %s value=\"%s\">%s</option>", StringUtils.equals(label.getValue(), viewName) ? "selected" : "",
                    PageFlowUtil.filter(label.getValue()), PageFlowUtil.filter(label.getKey()));
        }
        out.print("</select>");

        if (isShowCohortSelector())
        {
            Cohort[] cohorts = StudyManager.getInstance().getCohorts(getViewContext().getContainer(), getViewContext().getUser());
            out.print(" Cohort <select name=\"cohortId\" onchange=\"this.form.submit()\">");
            out.print("<option value=\"\">All</option>");
            for (Cohort cohort : cohorts)
            {
                boolean selected = _selectedCohort != null && _selectedCohort.getRowId() == cohort.getRowId();
                out.printf("<option %s value=\"%s\">%s</option>",
                        selected ? "selected" : "", cohort.getRowId(), PageFlowUtil.filter(cohort.getLabel()));
            }
            out.print("</select>");
        }

        if (_gridView)
        {
            if (!getViewContext().getUser().isGuest() && getViewContext().hasPermission(ACL.PERM_READ))
            {
                out.printf("&nbsp;[<a href=\"%s\">Customize View</a>] ", _customizeURL);
            }

            // make sure there is a participant id available
            //String participantId = getViewContext().getRequest().getParameter("participantId");
            Map params = new HashMap();
            UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), "study");
            params.put(QueryParam.schemaName.toString(), schema.getSchemaName());
            params.put(QueryParam.queryName.toString(), _datasetDef.getLabel());
            params.put(QueryParam.queryName.toString(), _datasetDef.getLabel());
            if (!StringUtils.isEmpty(viewName))
                params.put(QueryParam.viewName.toString(), viewName);
/*
            if (participantId == null)
            {
                List<String> cachedList = StudyController.getParticipantListFromCache(getViewContext(), _datasetDef.getDataSetId(), viewName, _selectedCohort);
                if (cachedList.size() > 0)
                    params.put("participantId", cachedList.get(0));
            }
*/
        }
        else
        {
            boolean showDelete = false;
            if (getViewContext().hasPermission(ACL.PERM_ADMIN))
                showDelete = true;
            else if (NumberUtils.isDigits(viewName))
                showDelete = ReportManager.get().canDeleteReport(getViewContext().getUser(),
                        getViewContext().getContainer(), NumberUtils.toInt(viewName));

            if (showDelete)
            {
                ActionURL url = new ActionURL(StudyController.DeleteDatasetReportAction.class, getViewContext().getContainer()).
                        addParameter(DataSetDefinition.DATASETKEY, _datasetDef.getDataSetId()).
                        addParameter("Dataset.viewName", viewName);
                out.printf("&nbsp;[<a href=\"%s\">Remove View</a>] ", url);
            }
        }
        if (!getViewContext().getUser().isGuest())
            out.printf("[<a href=\"%s\">Set Default View</a>] ", _currentUrl.relativeUrl("viewPreferences.view",
                    Collections.singletonMap(DataSetDefinition.DATASETKEY, String.valueOf(_datasetDef.getDataSetId())),"Study", false));

        out.print("</form>");
    }
}


