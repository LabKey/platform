<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Dataset"%>
<%@ page import="org.labkey.study.controllers.StudyController"%>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
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

function submitReset()
{
    var form = document.reorder;
    var itemSelect = form.resetOrder.value = true;
    form.submit();
    return false;
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
        alert("Please select a dataset first.");
    }
    return false;
}
</script>
<labkey:form method="post" name="reorder" action="<%=h(buildURL(StudyController.DatasetDisplayOrderAction.class))%>" enctype="multipart/form-data">
    <input type="hidden" name="resetOrder" value="false">
    <table>
        <tr>
            <td>
                <%
                    List<DatasetDefinition> defs = getDatasets();
                    boolean first = true;
                %>
                <select name="items" size="<%= defs.size() %>">
                <%
                for (Dataset def: defs)
                {
                    StringBuilder desc = new StringBuilder();
                    desc.append(def.getDatasetId());
                    if (def.getLabel() != null)
                        desc.append(": ").append(h(def.getLabel()));
                    if (def.getViewCategory() != null)
                        desc.append(" (").append(h(def.getViewCategory().getLabel())).append(")");
                    StringBuilder padding = new StringBuilder();
                    if (first)
                    {
                        // we'll pad the first entry to give our select box reasonable width
                        int padSize = 30 - desc.length();
                        while (padSize-- > 0)
                            padding.append("&nbsp;");
                        first = false;
                    }
                    %>
                    <option value="<%= def.getDatasetId() %>"><%=h(desc.toString()) + text(padding.toString())%></option>
                    <%
                }
                %>
                </select>
            </td>
            <td align="center" valign="center">
                <%= button("Move Up").submit(true).onClick("return orderModule(0)") %><br><br>
                <%= button("Move Down").submit(true).onClick("return orderModule(1)") %>
            </td>
        </tr>
    </table>
    <input type="hidden" name="order" value="">
    <%= button("Save").submit(true) %>
    <%= button("Cancel").href(StudyController.ManageTypesAction.class, getContainer()) %>
    <%= button("Reset Order").href("#").onClick("if (confirm('Resetting will order the datasets by category, and then by their ID numbers within each category.  This cannot be undone.  Continue?')) return submitReset(); else return false;") %>
</labkey:form>
