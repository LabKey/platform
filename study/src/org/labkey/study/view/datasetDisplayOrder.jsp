<%
/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
<%@ page import="org.labkey.study.controllers.StudyController.DatasetDisplayOrderAction"%>
<%@ page import="org.labkey.study.controllers.StudyController.ManageTypesAction" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<style>
    .button-reordering {
        width: 115px;
        margin-bottom: 5px;
    }
</style>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
function saveList() {
    var itemList = "";
    var itemSelect = document.reorder.items;
    for (var i = 0; i < itemSelect.length; i++) {
        itemList += itemSelect.item(i).value;
        if (i < itemSelect.length - 1)
            itemList += ",";
    }
    document.reorder.order.value = itemList;
}

function submitReset() {
    var form = document.reorder;
    var itemSelect = form.resetOrder.value = true;
    form.submit();
    return false;
}

function moveDatasetItem(action) {
    var itemSelect = document.reorder.items;
    var selIndex = itemSelect.selectedIndex;
    if (selIndex !== -1) {
        var selItem = itemSelect.item(selIndex);
        var isFirst = selIndex === 0;
        var isLast = selIndex === itemSelect.length - 1;

        if (action === 'top' && !isFirst) {
            document.reorder.items.insertBefore(selItem, itemSelect.item(0));
        } else if (action === 'bottom' && !isLast) {
            document.reorder.items.appendChild(selItem);
        } else if (action === 'up' && !isFirst) {
            document.reorder.items.insertBefore(selItem, itemSelect.item(selIndex - 1));
        } else if (action === 'down' && !isLast) {
            document.reorder.items.insertBefore(selItem, itemSelect.item(selIndex + 2));
        }
        saveList();
    } else {
        alert("Please select a dataset first.");
    }
    return false;
}
</script>
<labkey:form method="post" name="reorder" action="<%=urlFor(DatasetDisplayOrderAction.class)%>" enctype="multipart/form-data">
    <input type="hidden" name="resetOrder" value="false">
    <table>
        <tr>
            <td>
                <%
                    List<DatasetDefinition> defs = getDatasets();
                    boolean first = true;
                %>
                <select name="items" style="width: 400px;" size="<%=Math.min(Math.max(defs.size(), 10), 25)%>">
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
                    <option value="<%= def.getDatasetId() %>"><%=h(desc.toString())%><%=text(padding.toString())%></option>
                    <%
                }
                %>
                </select>
            </td>
            <td align="center" valign="center" style="padding-left: 10px;">
                <%= button("Move Up").addClass("button-reordering").onClick("return moveDatasetItem('up')") %><br>
                <%= button("Move Down").addClass("button-reordering").onClick("return moveDatasetItem('down')") %><br><br>
                <%= button("Move to Top").addClass("button-reordering").onClick("return moveDatasetItem('top')") %><br>
                <%= button("Move to Bottom").addClass("button-reordering").onClick("return moveDatasetItem('bottom')") %>
            </td>
        </tr>
    </table>
    <input type="hidden" name="order" value="">
    <%= button("Save").submit(true) %>
    <%= button("Cancel").href(urlFor(ManageTypesAction.class)) %>
    <%= button("Reset Order").href("#").onClick("if (confirm('Resetting will order the datasets by category, and then by their ID numbers within each category.  This cannot be undone.  Continue?')) return submitReset(); else return false;") %>
</labkey:form>
