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
<%@ page import="org.labkey.study.model.Visit"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<script>
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
        alert("Please select a visit first.");
    }
    return false;
}
</script>
<form method="post" name="reorder" action="visitDisplayOrder.post" enctype="multipart/form-data">
    <table>
        <tr>
            <td>
                <%
                    Visit[] visits = getVisits();
                    boolean first = true;
                %>
                <select name="items" size="<%= visits.length %>">
                <%
                for (Visit visit : visits)
                {
                    StringBuilder desc = new StringBuilder();
                    desc.append(visit.getRowId());
                    if (visit.getLabel() != null)
                        desc.append(": ").append(h(visit.getLabel()));
                    if (first)
                    {
                        // we'll pad the first entry to give our select box reasonable width
                        while (desc.length() < 30)
                            desc.append(" ");
                        first = false;
                    }

                    %>
                    <option value="<%= visit.getRowId() %>"><%= desc.toString() %></option>
                    <%
                }
                %>
                </select>
            </td>
            <td align="center" valign="center">
                <input type='image' src="<%= PageFlowUtil.buttonSrc("Move Up")%>" value='Move Up' onclick="return orderModule(0)"><br><br>
                <input type='image' src="<%= PageFlowUtil.buttonSrc("Move Down")%>" value='Move Down' onclick="return orderModule(1)">
            </td>
        </tr>
    </table>
    <input type="hidden" name="order" value="">
    <%= buttonImg("Save") %>&nbsp;<%= buttonLink("Cancel", "manageVisits.view") %>
</form>
