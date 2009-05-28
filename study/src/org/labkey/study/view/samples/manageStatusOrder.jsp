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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.model.StudyImpl"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.study.model.SampleRequestStatus"%>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<StudyImpl> me = (JspView<StudyImpl>) HttpView.currentView();
    StudyImpl study = me.getModelBean();
    SampleRequestStatus[] statuses = study.getSampleRequestStatuses(me.getViewContext().getUser());
%>
<%=PageFlowUtil.getStrutsError(request, "main")%>
<script type="text/javascript">
function saveList(listName)
{
    var itemList = "";
    var itemSelect = document.reorder.items;
    for (var i = 0; i < itemSelect.length; i++)
    {
        itemList += itemSelect.item(i).value;
        if (i < itemSelect.length - 1)
            itemList += ",";
    }
    document.reorder.order.value = itemList;
}

function orderModule(down)
{
    var itemSelect = document.reorder.items;
    var selIndex = itemSelect.selectedIndex;
    if (selIndex != -1)
    {
        var swapItem = null;
        if (selIndex > 0 && down == 0)
        {
            swapItem = itemSelect.item(selIndex - 1);
            itemSelect.selectedIndex--;
        }
        else if (selIndex < itemSelect.length-1 && down == 1)
        {
            swapItem = itemSelect.item(selIndex + 1);
            itemSelect.selectedIndex++;
        }
        if (swapItem != null)
        {
            var selItem = itemSelect.item(selIndex);
            var selText = selItem.text;
            var selValue = selItem.value;
            selItem.text = swapItem.text;
            selItem.value = swapItem.value;
            swapItem.text = selText;
            swapItem.value = selValue;
            saveList();
        }
    }
    else
    {
        alert("Please select a status first.");
    }
    return false;
}
</script>

<form action="manageStatusOrder.post" name="reorder" method="POST">
<table>
        <tr>
            <td>
                <%
                %>
                <select name="items" size="<%= statuses.length %>">
                <%
                for (SampleRequestStatus status : statuses)
                {
                    if (!status.isSystemStatus())
                    {
                    %>
                    <option value="<%= status.getRowId() %>"><%= status.getLabel() != null ? h(status.getLabel()) : "" %></option>
                    <%
                    }
                }
                %>
                </select>
            </td>
            <td align="center" valign="center">
                <%=PageFlowUtil.generateSubmitButton("Move Up", "return orderModule(0)")%><br><br>
                <%=PageFlowUtil.generateSubmitButton("Move Down", "return orderModule(1)")%>
            </td>
        </tr>
    </table>
    <input type="hidden" name="order" value="">
    <%= generateSubmitButton("Save") %>&nbsp;<%= generateButton("Cancel", "manageStatuses.view") %>
</form>
