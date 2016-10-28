<%
/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
%>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.reports.ReportService" %>
<%@ page import="org.labkey.api.reports.report.ReportIdentifier" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    JspView<StudyController.ViewPrefsBean> me = (JspView<StudyController.ViewPrefsBean>) HttpView.currentView();
    StudyController.ViewPrefsBean bean = me.getModelBean();

    ViewContext context = getViewContext();
    int datasetId = bean.getDatasetDefinition().getDatasetId();
    String defaultView = StudyController.getDefaultView(context, datasetId);
%>

<table>
    <tr class="labkey-wp-header">
        <th colspan="3" align="left">Default<%=PageFlowUtil.helpPopup("Default", "Select the default Chart or Report that will display from the Study Datasets Web Part")%></th>
    </tr>
<%
    if (bean.getViews().size() > 1)
    {
        for (Pair<String, String> view : bean.getViews())
        {
%>
            <tr><td><%=text(getLabel(view, defaultView))%></td>
                <td>&nbsp;</td>
                <td><%=textLink("select", StudyController.getViewPreferencesURL(getContainer(), datasetId, view.getValue()))%></td>
            </tr>
<%
        }
    }
    else
    {
%>
        <tr><td>There is only a single view for this dataset.</td></tr>
<%
    }

    ActionURL doneUrl = context.cloneActionURL();
    doneUrl.setAction(StudyController.DatasetReportAction.class);
    doneUrl.deleteParameter("defaultView");
    doneUrl.deleteParameter(StudyController.DATASET_REPORT_ID_PARAMETER_NAME);
    doneUrl.deleteParameter(StudyController.DATASET_VIEW_NAME_PARAMETER_NAME);

    ReportIdentifier reportId = ReportService.get().getReportIdentifier(defaultView);

    if (reportId != null)
        doneUrl.addParameter(StudyController.DATASET_REPORT_ID_PARAMETER_NAME, defaultView);
    else
        doneUrl.addParameter(StudyController.DATASET_VIEW_NAME_PARAMETER_NAME, defaultView);
%>
        <tr><td>&nbsp;</td></tr>
        <tr><td><%= button("Done").href(doneUrl) %></td></tr>
</table>

<%!
    String getLabel(Pair<String, String> view, String defaultView)
    {
        if (StringUtils.equals(view.getValue(), defaultView))
            return "<b>" + h(view.getKey()) + "</b>";

        return h(view.getKey());
    }
%>


