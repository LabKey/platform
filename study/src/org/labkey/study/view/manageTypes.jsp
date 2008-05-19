<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.WebPartView"%>
<%@ page import="org.labkey.study.model.DataSetDefinition"%>
<%@ page import="org.labkey.study.model.StudyManager" %>
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
    org.labkey.study.model.Study study = StudyManager.getInstance().getStudy(HttpView.currentContext().getContainer());

    DataSetDefinition[] datasets = study.getDataSets();
    int countUndefined = 0;
    for (DataSetDefinition def : datasets) {
        if (def.getTypeURI() == null)
            countUndefined++;
    }
    String dateFormat = StudyManager.getInstance().getDefaultDateFormatString(HttpView.currentContext().getContainer());
    String numberFormat = StudyManager.getInstance().getDefaultNumberFormatString(HttpView.currentContext().getContainer());
    String decimalFormatHelp = "The format string for numbers must be compatible with the format that the java class " +
            "<code>DecimalFormat</code> understands. A valid <code>DecimalFormat</code> is a pattern " +
            "specifying a prefix, numeric part, and suffix. For more information see the " +
            "<a href=\"http://java.sun.com/j2se/1.4.2/docs/api/java/text/DecimalFormat.html\" target=\"blank\">java&nbsp;documentation</a>. " +
            "The following table has an abbreviated guide to pattern symbols:<br/>" +
            "<table border=0 cellspacing=3 cellpadding=0>" +
            "<tr bgcolor=\"#ccccff\"><th align=left>Symbol<th align=left>Location<th align=left>Localized?<th align=left>Meaning</tr>" +
            "<tr valign=top><td><code>0</code><td>Number<td>Yes<td>Digit</tr>" +
            "<tr valign=top bgcolor=\"#eeeeff\"><td><code>#</code><td>Number<td>Yes<td>Digit, zero shows as absent</tr>" +
            "<tr valign=top><td><code>.</code><td>Number<td>Yes<td>Decimal separator or monetary decimal separator</tr>" +
            "<tr valign=top bgcolor=\"#eeeeff\"><td><code>-</code><td>Number<td>Yes<td>Minus sign</tr>" +
            "<tr valign=top><td><code>,</code><td>Number<td>Yes<td>Grouping separator</tr></table>";
    String dateFormatHelp = "The format string for dates must be compatible with the format that the java class " +
            "<code>SimpleDateFormat</code> understands. For more information see the " +
            "<a href=\"http://java.sun.com/j2se/1.4.2/docs/api/java/text/SimpleDateFormat.html\" target=\"blank\">java&nbsp;documentation</a>. " +
            "The following table has a partial guide to pattern symbols:<br/>" +
            "<table border=0 cellspacing=3 cellpadding=0>" +
            "<tr bgcolor=\"#ccccff\"><th align=left>Letter<th align=left>Date or Time Component<th align=left>Examples</tr>" +
            "<tr><td><code>G</code><td>Era designator<td><code>AD</code></tr>" +
            "<tr bgcolor=\"#eeeeff\"><td><code>y</code><td>Year<td><code>1996</code>; <code>96</code></tr>" +
            "<tr><td><code>M</code><td>Month in year<td><code>July</code>; <code>Jul</code>; <code>07</code></tr>" +
            "<tr bgcolor=\"#eeeeff\"><td><code>w</code><td>Week in year<td><code>27</code></td></tr>" +
            "<tr><td><code>W</code><td>Week in month<td><code>2</code></td></tr>" +
            "<tr bgcolor=\"#eeeeff\"><td><code>D</code><td>Day in year<td><code>189</code></td></tr>" +
            "<tr><td><code>d</code><td>Day in month<td><code>10</code></tr>" +
            "<tr bgcolor=\"#eeeeff\"><td><code>F</code><td>Day of week in month<td><code>2</code></tr>" +
            "<tr><td><code>E</code><td>Day in week<td><code>Tuesday</code>; <code>Tue</code></tr>" +
            "<tr bgcolor=\"#eeeeff\"><td><code>a</code><td>Am/pm marker<td><code>PM</code></tr>" +
            "<tr><td><code>H</code><td>Hour in day (0-23)<td><code>0</code></tr>" +
            "<tr bgcolor=\"#eeeeff\"><td><code>k</code><td>Hour in day (1-24)<td><code>24</code></tr>" +
            "<tr><td><code>K</code><td>Hour in am/pm (0-11)<td><code>0</code></tr>" +
            "<tr bgcolor=\"#eeeeff\"><td><code>h</code><td>Hour in am/pm (1-12)<td><code>12</code></tr></table>";

%>
<table class="normal">
<%
//    if (countUndefined > 0)
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
            <td><%= textLink("Define Dataset Schemas", "manageUndefinedTypes.view")%></td>
        </tr><%
    }
    if (datasets.length > 0)
    {
    %><tr>
        <td>Datasets can be displayed in any order.</td>
        <td><%= textLink("Change Display Order", "dataSetDisplayOrder.view")%></td>
    </tr>
    <tr>
        <td>Dataset visibility, label, and category can all be changed.</td>
        <td><%= textLink("Change Properties", "dataSetVisibility.view")%></td>
    </tr><%
    }

%><tr>
        <td>New Datasets can be added to this study at any time.</td>
        <td><%= textLink("Create New Dataset", "defineDatasetType.view?create=true&autoDatasetId=true")%></td>
    </tr>
</table>
<% WebPartView.startTitleFrame(out, "Default Time/Date, Number Formats", null, null, null); %>
<labkey:errors/>

<form id="manageTypesForm" action="manageTypes.view" method="POST">
    <table class="normal">
        <tr><td>Default Study Date format string:<%=PageFlowUtil.helpPopup("Date format string", dateFormatHelp, true)%></td>
            <td><input id="dateFormat" name="dateFormat" value="<%=StringUtils.trimToEmpty(dateFormat)%>"></td></tr>
        <tr><td>Default Study Number format string:<%=PageFlowUtil.helpPopup("Number format string", decimalFormatHelp, true)%></td>
            <td><input id="numberFormat" name="numberFormat" value="<%=StringUtils.trimToEmpty(numberFormat)%>"></td></tr>
        <tr><td><input type="image" src="<%=PageFlowUtil.submitSrc()%>">
            &nbsp;<a href="javascript:resetDefaultFormats()"><%=PageFlowUtil.buttonImg("Reset to Default")%></a>

        </td></tr>
    </table>
</form>
<% WebPartView.endTitleFrame(out); %>

<% WebPartView.startTitleFrame(out, "Datasets", null, null, null); %>
<table class="normal">
    <tr>
        <th align="left">ID</th>
        <th align="left">Label</th>
        <th align="left">Category</th>
        <th align="left">Cohort</th>
        <th align="left">Show By Default</th>
        <th>&nbsp;</th>
        <th>&nbsp;</th>
    </tr><%

    for (DataSetDefinition def : datasets)
    {
    %><tr>
        <td align=right><a href="<%="datasetDetails.view?id=" + def.getDataSetId()%>"><%=def.getDataSetId()%></a></td>
        <td><a href="<%="datasetDetails.view?id=" + def.getDataSetId()%>"><%= h(def.getLabel()) %></a></td>
        <td><%= def.getCategory() != null ? h(def.getCategory()) : "&nbsp;" %></td>
        <td><%= def.getCohort() != null ? h(def.getCohort().getLabel()) : "All" %></td>
        <td><%= def.isShowByDefault() %></td>
    </tr><%
    }
%></table>
<%= textLink("Create New Dataset", "defineDatasetType.view?create=true&autoDatasetId=true")%>
<% WebPartView.endTitleFrame(out); %>