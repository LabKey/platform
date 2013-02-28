<%
/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.study.DataSet"%>
<%@ page import="org.labkey.api.study.Study"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.DatasetController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController.*" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.study.controllers.security.SecurityController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<script type="text/javascript">
    function resetDefaultFormats()
    {
        var dateFormat = document.getElementById('dateFormat');
        if (dateFormat)
            dateFormat.value = "";
        var numberFormat = document.getElementById('numberFormat');
        if (numberFormat)
            numberFormat.value = "";

        document.getElementById('manageTypesForm').submit();
    }
</script>

<%
    Container c = HttpView.currentContext().getContainer();
    Study study = StudyManager.getInstance().getStudy(c);

    List<? extends DataSet> datasets = study.getDataSetsByType(new String[]{DataSet.TYPE_STANDARD, DataSet.TYPE_PLACEHOLDER});
    int countUndefined = 0;
    for (DataSet def : datasets)
    {
        Domain d = def.getDomain();
        if (null == d || 0 == d.getProperties().length)
            countUndefined++;
    }
    String dateFormat = StudyManager.getInstance().getDefaultDateFormatString(HttpView.currentContext().getContainer());
    String numberFormat = StudyManager.getInstance().getDefaultNumberFormatString(HttpView.currentContext().getContainer());
    String decimalFormatHelp = "The format string for numbers must be compatible with the format that the java class " +
            "<code>DecimalFormat</code> understands. A valid <code>DecimalFormat</code> is a pattern " +
            "specifying a prefix, numeric part, and suffix. For more information see the " +
            "<a href=\"http://java.sun.com/j2se/1.4.2/docs/api/java/text/DecimalFormat.html\" target=\"blank\">java&nbsp;documentation</a>. " +
            "The following table has an abbreviated guide to pattern symbols:<br/>" +
            "<table class=\"labkey-data-region labkey-show-borders\"><colgroup><col><col><col><col></colgroup>" +
            "<tr class=\"labkey-frame\"><th align=left>Symbol<th align=left>Location<th align=left>Localized?<th align=left>Meaning</tr>" +
            "<tr valign=top><td><code>0</code><td>Number<td>Yes<td>Digit</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>#</code><td>Number<td>Yes<td>Digit, zero shows as absent</tr>" +
            "<tr valign=top><td><code>.</code><td>Number<td>Yes<td>Decimal separator or monetary decimal separator</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>-</code><td>Number<td>Yes<td>Minus sign</tr>" +
            "<tr valign=top><td><code>,</code><td>Number<td>Yes<td>Grouping separator</tr></table>";
    String dateFormatHelp = "The format string for dates must be compatible with the format that the java class " +
            "<code>SimpleDateFormat</code> understands. For more information see the " +
            "<a href=\"http://java.sun.com/j2se/1.4.2/docs/api/java/text/SimpleDateFormat.html\" target=\"blank\">java&nbsp;documentation</a>. " +
            "The following table has a partial guide to pattern symbols:<br/>" +
            "<table class=\"labkey-data-region labkey-show-borders\"><colgroup><col><col><col></colgroup>" +
            "<tr class=\"labkey-frame\"><th align=left>Letter<th align=left>Date or Time Component<th align=left>Examples</tr>" +
            "<tr><td><code>G</code><td>Era designator<td><code>AD</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>y</code><td>Year<td><code>1996</code>; <code>96</code></tr>" +
            "<tr><td><code>M</code><td>Month in year<td><code>July</code>; <code>Jul</code>; <code>07</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>w</code><td>Week in year<td><code>27</code></td></tr>" +
            "<tr><td><code>W</code><td>Week in month<td><code>2</code></td></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>D</code><td>Day in year<td><code>189</code></td></tr>" +
            "<tr><td><code>d</code><td>Day in month<td><code>10</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>F</code><td>Day of week in month<td><code>2</code></tr>" +
            "<tr><td><code>E</code><td>Day in week<td><code>Tuesday</code>; <code>Tue</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>a</code><td>Am/pm marker<td><code>PM</code></tr>" +
            "<tr><td><code>H</code><td>Hour in day (0-23)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>k</code><td>Hour in day (1-24)<td><code>24</code></tr>" +
            "<tr><td><code>K</code><td>Hour in am/pm (0-11)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>h</code><td>Hour in am/pm (1-12)<td><code>12</code></tr></table>";

%>
<table>
    <tr>
        <td>The study schedule defines the data expected for each timepoint.</td>
        <td><%= textLink("Study Schedule", StudyController.StudyScheduleAction.class) %></td>
    </tr>
<%

    if (countUndefined > 0)
    {
        %><tr>
            <td>A visit map can refer to datasets that do not have defined schemas.
                <%
                    if (countUndefined == 1)
                    {
                        %>One dataset in this study does not have a defined schema.<%
                    }
                    else if (countUndefined > 1)
                    {
                        %><%= countUndefined %> datasets in this study do not have defined schemas.<%
                    }
                    else
                    {
                        %>All datasets in this study have defined schemas.<%
                    }
                %>
            </td>
            <td><%= textLink("Define Dataset Schemas", ManageUndefinedTypesAction.class)%></td>
        </tr><%
    }
    if (!datasets.isEmpty())
    {
    %><tr>
        <td>Datasets can be displayed in any order.</td>
        <td><%= textLink("Change Display Order", DatasetDisplayOrderAction.class)%></td>
    </tr><%
    }

%>
    <tr>
        <td>Dataset visibility, label, and category can all be changed.</td>
        <td><%= textLink("Change Properties", DatasetVisibilityAction.class)%></td>
    </tr>
    <tr>
        <td>Datasets may be deleted by an administrator.</td>
        <td><%= textLink("Delete Multiple Datasets", DatasetController.BulkDatasetDeleteAction.class)%></td>
    </tr>
    <tr>
        <td>Security can be configured on a per-dataset level.</td>
        <td><%= textLink("Manage Dataset Security", SecurityController.BeginAction.class)%></td>
    </tr>

    <tr>
        <td>New Datasets can be added to this study at any time.</td>
        <%
            ActionURL createURL = new ActionURL(StudyController.DefineDatasetTypeAction.class, getViewContext().getContainer());
            createURL.addParameter("autoDatasetId", "true");
        %>
        <td><%= textLink("Create New Dataset", createURL)%></td>
    </tr>
</table>
<% WebPartView.startTitleFrame(out, "Default Time/Date, Number Formats", null, null, null); %>
<labkey:errors/>

<form id="manageTypesForm" action="<%=h(buildURL(ManageTypesAction.class))%>" method="POST">
    <table>
        <tr><td>Default Study Date format string:<%=PageFlowUtil.helpPopup("Date format string", dateFormatHelp, true)%></td>
            <td><input id="dateFormat" name="dateFormat" value="<%=StringUtils.trimToEmpty(dateFormat)%>"></td></tr>
        <tr><td>Default Study Number format string:<%=PageFlowUtil.helpPopup("Number format string", decimalFormatHelp, true)%></td>
            <td><input id="numberFormat" name="numberFormat" value="<%=StringUtils.trimToEmpty(numberFormat)%>"></td></tr>
        <tr><td><%=generateSubmitButton("Submit")%>
            &nbsp;<%=generateButton("Reset to Default", "javascript:resetDefaultFormats()")%>

        </td></tr>
    </table>
</form>
<% WebPartView.endTitleFrame(out); %>

<% WebPartView.startTitleFrame(out, "Datasets", null, null, "datasets"); %>
<table>
    <tr>
        <th align="left">ID</th>
        <th align="left">Name</th>
        <th align="left">Label</th>
        <th align="left">Category</th>
        <th align="left">Type</th>
        <th align="left">Cohort</th>
        <th align="left">Shown</th>
        <th align="left">Demographic</th>
        <th align="left">Keys</th>
        <th align="left">Source Assay</th>
    </tr><%

    ActionURL details = new ActionURL(DatasetDetailsAction.class, c);
    for (DataSet def : datasets)
    {
        details.replaceParameter("id",String.valueOf(def.getDataSetId()));
    %><tr>
        <td align=right><a href="<%=h(details)%>"><%=def.getDataSetId()%></a></td>
        <td><a href="<%=h(details)%>"><%= h(def.getName()) %></a></td>
        <td><% if (!def.getName().equals(def.getLabel())) {%><a href="<%=h(details)%>"><%= h(def.getLabel()) %></a><%}%>&nbsp;</td>
        <td><%= h(def.getViewCategory() != null ? def.getViewCategory().getLabel() : null) %>&nbsp;</td>
        <td><%= def.getType()%>&nbsp;</td>
        <td><%= def.getCohort() != null ? h(def.getCohort().getLabel()) : "All" %></td>
        <td><%= def.isShowByDefault() ? "" : "false" %></td>
        <td><%= def.isDemographicData() ? "true" : "" %></td>
        <td><%= def.getKeyTypeDescription() %></td>
        <td><%= def.getAssayProtocol() != null ? def.getAssayProtocol().getName() : "" %></td>
    </tr><%
    }
%></table>
<%= textLink("Create New Dataset", new ActionURL(DefineDatasetTypeAction.class,c).addParameter("autoDatasetId","true"))%>
<% WebPartView.endTitleFrame(out); %>
