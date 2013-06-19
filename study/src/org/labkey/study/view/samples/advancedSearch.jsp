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
<%@ page import="org.labkey.api.data.CompareType"%>
<%@ page import="org.labkey.api.data.DataColumn"%>
<%@ page import="org.labkey.api.data.DisplayColumn"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.samples.ShowSearchAction" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.data.RenderContext" %>
<%@ page import="org.labkey.study.samples.SampleSearchBean" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.query.PtidObfuscatingDisplayColumn" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SampleSearchBean> me = (JspView<SampleSearchBean>) HttpView.currentView();
    SampleSearchBean bean = me.getModelBean();

    String hideExtraColumnsLabel = "Hide extra columns";
    String showExtraColumnsLabel = "Show all columns";

    Set<String> availableColumns = new HashSet<>();
    for (DisplayColumn dc : bean.getDisplayColumns())
        availableColumns.add(dc.getColumnInfo().getName());
%>
<script type="text/javascript">
    var inputRequiredMap = {
        <%
        boolean first = true;
        for (CompareType type : CompareType.values())
        {
            if (first)
                first = false;
            else
            {
                %>,<%
            }
        %>"<%= h(type.name()) %>" : <%= type.isDataValueRequired() ? "true" : "false" %>
        <%
        }
        %>
    };

    function updateVisibility(elemPrefix)
    {
        var inputElement = document.getElementById(elemPrefix + ".value");
        var compareElement = document.getElementById(elemPrefix + ".compareType");
        var selectedCompare = compareElement.options[compareElement.selectedIndex].value;
        var inputValueRequired;
        if (!selectedCompare)
            inputValueRequired = false;
        else
            inputValueRequired = inputRequiredMap[selectedCompare];
        if (!inputValueRequired)
        {
            if (!inputElement.disabled)
                inputElement.value = "";
            inputElement.disabled = true;
        }
        else
            inputElement.disabled = false;

        return true;
    }

    function showOrHideAdditionalColumns()
    {
        var additionalColumnsTable = document.getElementById('additionalColumnsTable');
        var additionalColumnsLink = document.getElementById('additionalColumnsLink');
        if (additionalColumnsTable.style.display == "none")
        {
            additionalColumnsTable.style.display = "block";
            additionalColumnsLink.innerHTML = "<%= hideExtraColumnsLabel %>";
        }
        else
        {
            additionalColumnsTable.style.display = "none";
            additionalColumnsLink.innerHTML = "<%= showExtraColumnsLabel %>";
        }
    }

</script>
<%
    if (!bean.isInWebPart())
    {
%>
This page may be used to search for <%= bean.isDetailsView() ? " individual vials" : " vials grouped by " + StudyService.get().getSubjectNounSingular(me.getViewContext().getContainer()).toLowerCase() + ", time point, and type" %>.<br>
<%= textLink("Search " + (!bean.isDetailsView() ? " individual vials" : "grouped vials"), buildURL(ShowSearchAction.class, "showVials=" + !bean.isDetailsView()))%><br><br>
<%
    }
    int paramNumber = 0;
%>
<form action="<%= new ActionURL(ShowSearchAction.class, getViewContext().getContainer()).getLocalURIString() %>" id="searchForm" method="POST">
    <input type="hidden" name="showVials" value="<%= bean.isDetailsView() ? "true" : "false" %>" >
    <%
        for (int pass = 0; pass < (bean.isInWebPart() ? 1 : 2); pass++)
        {
    %>
    <table id="<%= pass == 0 ? "specialColumnsTable" : "additionalColumnsTable" %>" style="display:<%= (pass == 0 || bean.isAdvancedExpanded()) ? "block" : "none" %>">
    <%
            RenderContext renderContext = new RenderContext(me.getViewContext());

            for (DisplayColumn col : bean.getDisplayColumns())
            {
                boolean display;

                if (pass == 0)
                    display = col.isVisible(renderContext) && bean.isDefaultColumn(col.getColumnInfo());
                else
                    display = col.isVisible(renderContext) && availableColumns.contains(col.getColumnInfo().getName());

                if (display)
                {
                    String columnName = bean.getDataRegionName() + "." + (col instanceof DataColumn ?
                            ((DataColumn) col).getDisplayColumn().getName() : col.getColumnInfo().getName());

                    if (bean.isPickListColumn(col.getColumnInfo()))
                    {
                        List<String> pickList = bean.getPickListValues(col.getColumnInfo());
                        boolean obfuscate = bean.shouldObfuscate(col.getColumnInfo());
        %>
                    <tr>
                        <td><%= h(col.getCaption()) %></td>
                        <td colspan="2">
                            <input type="hidden" name="searchParams[<%= paramNumber %>].columnName" value="<%= h(columnName) %>">
                            <input type="hidden" name="searchParams[<%= paramNumber %>].compareType" value="<%= CompareType.EQUAL.name() %>">
                            <select name="searchParams[<%= paramNumber %>].value" id="searchParams[<%= paramNumber %>].value">
                                <option value="">&lt;has any value&gt;</option><%
                                    for (Object value : pickList)
                                    {
                                        String keyValue = (value != null ? h(value.toString()) : "");
                                        String displayValue = obfuscate ? id(keyValue) : keyValue;
                                %>
                                <option value="<%= keyValue %>"><%= displayValue %></option><%
                                    }
                                %>
                            </select>
                        </td>
                    </tr>
                    <%
                }
                else
                {
                    %>
                        <tr>
                            <td><%= h(col.getCaption()) %></td>
                            <td>
                                <input type="hidden" name="searchParams[<%= paramNumber %>].columnName" value="<%= h(columnName) %>">
                                <select name="searchParams[<%= paramNumber %>].compareType"
                                        id="searchParams[<%= paramNumber %>].compareType"
                                        onChange="updateVisibility('searchParams[<%= paramNumber %>]')">
                                    <option value="">&lt;has any value&gt;</option><%
                                        for (CompareType type : CompareType.getValidCompareSet(col.getColumnInfo()))
                                        { %>
                                    <option value="<%= h(type.name()) %>"><%= h(type.getDisplayValue()) %></option><%
                                        }
                                    %>
                                </select>
                            </td>
                            <td><input type="text"
                                       name="searchParams[<%= paramNumber %>].value"
                                       id="searchParams[<%= paramNumber %>].value"
                                       size="30"
                                       DISABLED></td>
                        </tr>
                    <%
                }
                availableColumns.remove(col.getColumnInfo().getName());
                paramNumber++;
            }
        }
        %>
    </table><br>
    <%
            if (pass == 0 && !bean.isInWebPart())
            {
            %>
    <%= textLink(bean.isAdvancedExpanded() ? hideExtraColumnsLabel: showExtraColumnsLabel, "#", "showOrHideAdditionalColumns()", "additionalColumnsLink") %><br>
            <%
            }
            if (bean.isInWebPart())
            {
                ActionURL advancedVialSearch = new ActionURL(ShowSearchAction.class, getViewContext().getContainer());
                advancedVialSearch.addParameter("showAdvanced", "true");
                advancedVialSearch.addParameter("showVials", "true");
            %>
    <%= textLink("More options", advancedVialSearch) %><br><br>
            <%
            }
        }
    %>
<%= generateSubmitButton("Search") %>
<%
    if (!bean.isInWebPart())
    {
%>
    <%= generateButton("Cancel", buildURL(StudyController.SamplesAction.class, "_lastFilter=1&showVials=" + bean.isDetailsView()))%>
<%
    }
%>
</form>