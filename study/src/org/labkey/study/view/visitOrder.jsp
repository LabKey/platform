<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.study.StudyService"%>
<%@ page import="org.labkey.api.study.Visit"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    JspView<StudyController.VisitReorderForm> me = (JspView<StudyController.VisitReorderForm>) HttpView.currentView();

    ActionURL returnURL;
    if (getActionURL().getParameter("returnUrl") != null)
        returnURL = new ActionURL(getActionURL().getParameter("returnUrl"));
    else
        returnURL = new ActionURL(StudyController.ManageVisitsAction.class, getContainer());
%>
<script type="text/javascript">
function saveList(listName, hiddenElName)
{
    var itemList = "";
    var itemSelect = document.reorder[listName];
    for (var i = 0; i < itemSelect.length; i++)
    {
        itemList += itemSelect.item(i).value;
        if (i < itemSelect.length - 1)
            itemList += ",";
    }
    document.reorder[hiddenElName].value = itemList;
}

function orderModule(listName, hiddenElName, down)
{
    var itemSelect = document.reorder[listName];
    var increment = down ? -1 : 1;

    var foundSelection = false;
    // if we're moving items down, start from the bottom of the list and work up:
    for (var i = (down ? itemSelect.options.length - 1 : 0) ;
            (down && i >= 0) || (!down && i < itemSelect.options.length);
            i += increment)
    {
        var selItem = itemSelect.item(i);
        if (selItem.selected)
        {
            foundSelection = true;
            var swapItem = itemSelect.item(i - increment);
            if (!swapItem)
                break;
            var selText = selItem.text;
            var selValue = selItem.value;
            selItem.text = swapItem.text;
            selItem.value = swapItem.value;
            selItem.selected = false;
            swapItem.text = selText;
            swapItem.value = selValue;
            swapItem.selected = true;
        }
    }

    if (foundSelection)
        saveList(listName, hiddenElName);
    else
        alert("Please select a visit first.");
    return false;
}
</script>
<labkey:form method="post" name="reorder" action="<%=h(buildURL(StudyController.VisitOrderAction.class))%>" enctype="multipart/form-data">
    <table class="lk-fields-table">
        <tr>
            <th style="font-weight: bold;" colspan="2">Display Order<%= helpPopup("Display Order", "Display order determines the order in which visits appear in reports and views for all " +
                    "study and specimen data.  By default, visits are displayed in order of increasing visit ID for visit-based studies, and in date " +
                    "order for date-based studies.")%></th>
            <th style="font-weight: bold;" colspan="2">Chronological Order<%= helpPopup("Chronological Order", "Chronological visit order is used to determine which visits occurred before " +
                    "or after others.  Visits are chronologically ordered when all participants move only downward through the visit list.  Any given " +
                    StudyService.get().getSubjectNounSingular(getContainer()).toLowerCase() + " may skip some visits, depending on " +
                    "cohort assignment or other factors.  It is generally not useful to set a chronological order for date-based studies.")%></th>
        </tr>
        <%
            List<VisitImpl> visits = getVisits(Visit.Order.DISPLAY);
            boolean displayEnabled = false;
            boolean chronologicalEnabled = false;
            for (VisitImpl visit : visits)
            {
                if (visit.getDisplayOrder() > 0)
                    displayEnabled = true;
                if (visit.getChronologicalOrder() > 0)
                    chronologicalEnabled = true;
            }
        %>
        <tr>
            <td colspan="2" style="padding-right: 50px;">
                <input type="checkbox"
                       name="explicitDisplayOrder"
                       value="true"<%=checked(displayEnabled)%>
                       onClick="document.reorder.displayOrderItems.disabled = !this.checked;">
                Explicitly set display order
            </td>
            <td colspan="2">
                <input type="checkbox"
                       name="explicitChronologicalOrder"
                       value="true"<%=checked(chronologicalEnabled) %>
                       onClick="document.reorder.chronologicalOrderItems.disabled = !this.checked;">
                Explicitly set chronological order
            </td>
        </tr>
        <tr>
            <td>
                <select multiple name="displayOrderItems" size="<%= Math.min(visits.size(), 25) %>"<%=disabled(!displayEnabled)%>>
                <%
                    boolean first = true;
                    StringBuilder orderedList = new StringBuilder();
                    for (VisitImpl visit : visits)
                    {
                        orderedList.append(first ? "" : ",").append(visit.getRowId());
                        StringBuilder desc = new StringBuilder();
                        desc.append(visit.getDisplayString());
                        if (first)
                        {
                            // we'll pad the first entry to give our select box reasonable width
                            while (desc.length() < 30)
                                desc.append(" ");
                            first = false;
                        }

                        %>
                        <option value="<%= visit.getRowId() %>"><%= h(desc) %></option>
                        <%
                    }
                %>
                </select>
                <input type="hidden" name="displayOrder" value="<%= orderedList %>">
            </td>
            <td align="center" valign="center">
                <%= PageFlowUtil.button("Move Up").href("#").onClick("return orderModule('displayOrderItems', 'displayOrder', 0);") %><br><br>
                <%= PageFlowUtil.button("Move Down").href("#").onClick("return orderModule('displayOrderItems', 'displayOrder', 1);") %>
            </td>

            <td>
                <select multiple name="chronologicalOrderItems" size="<%= Math.min(visits.size(), 25) %>"<%=disabled(!chronologicalEnabled)%>>
                <%
                    visits = getVisits(Visit.Order.CHRONOLOGICAL);
                    first = true;
                    orderedList = new StringBuilder();
                    for (VisitImpl visit : visits)
                    {
                        orderedList.append(first ? "" : ",").append(visit.getRowId());
                        StringBuilder desc = new StringBuilder();
                        desc.append(visit.getDisplayString());
                        if (first)
                        {
                            // we'll pad the first entry to give our select box reasonable width
                            while (desc.length() < 30)
                                desc.append(" ");
                            first = false;
                        }

                        %>
                        <option value="<%= visit.getRowId() %>"><%= h(desc.toString()) %></option>
                        <%
                    }
                %>
                </select>
                <input type="hidden" name="chronologicalOrder" value="<%= orderedList %>">
                <input type="hidden" name="returnUrl" value="<%= returnURL %>">
            </td>
            <td align="center" valign="center">
                <%= PageFlowUtil.button("Move Up").href("#").onClick("return orderModule('chronologicalOrderItems', 'chronologicalOrder', 0)") %><br><br>
                <%= PageFlowUtil.button("Move Down").href("#").onClick("return orderModule('chronologicalOrderItems', 'chronologicalOrder', 1)") %>
            </td>
        </tr>
    </table>
    <br/>
    <%= button("Save").submit(true) %>
    <%= button("Cancel").href(returnURL) %>
</labkey:form>
