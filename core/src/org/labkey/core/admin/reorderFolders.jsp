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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ViewContext> me = (JspView<ViewContext>) HttpView.currentView();
    Container current = getContainer();
    List<Container> containers;
    boolean reorderingProjects = current.isRoot() || current.getParent().isRoot();
    if (current.isRoot())
        containers = current.getChildren();
    else
        containers = current.getParent().getChildren();

    boolean isCustomOrder = false;
    for (Container container : containers)
    {
        if (container.getSortOrder() > 0)
            isCustomOrder = true;
    }
%>
<script type="text/javascript">
function saveList()
{
    var itemList = "";
    var itemSelect = document.reorder.items;
    for (var i = 0; i < itemSelect.length; i++)
    {
        itemList += itemSelect.item(i).value;
        if (i < itemSelect.length - 1)
            itemList += ";";
    }
    document.reorder.order.value = itemList;
}

function orderModule(down)
{
    if (reorderDisabled())
        return false;

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
        alert("Please select a folder first.");
    }
    return false;
}

function reorderDisabled()
{
    var radios = document.reorder.resetToAlphabetical;

    for (var i = 0; i < radios.length; i++)
    {
		if (radios[i].checked)
			return radios[i].value == "true";
	}

    return false;
}

function toggleItemSelector()
{
    var disabled = reorderDisabled();
    if (disabled)
        document.reorder.items.selectedIndex = -1;
    document.reorder.items.disabled = disabled;
}

</script>
<labkey:form action="<%=h(buildURL(AdminController.ReorderFoldersAction.class))%>" name="reorder" method="POST" onsubmit="saveList()">
<p>
    <input type="radio" name="resetToAlphabetical" value="true"<%=checked(!isCustomOrder)%> onChange="toggleItemSelector();"/> Sort <%= reorderingProjects ? "projects" : "folders" %> alphabetically<br>
    <input type="radio" name="resetToAlphabetical" value="false"<%=checked(isCustomOrder)%> onChange="toggleItemSelector();" /> Use custom <%= reorderingProjects ? "project" : "folder" %> order
</p>
<p>
    <table>
        <tr>
            <td>
                <select name="items" size="<%=Math.min(containers.size(), 25)%>"<%=disabled(!isCustomOrder)%>>
                <%
                for (Container container : containers)
                {
                    %>
                    <option value="<%= h(container.getName()) %>"><%= h(container.getName()) %></option>
                    <%
                }
                %>
                </select>
            </td>
            <td>
                <%= button("Move Up").submit(true).onClick("return orderModule(0)") %><br><br>
                <%= button("Move Down").submit(true).onClick("return orderModule(1)") %>
            </td>
        </tr>
    </table>
</p>
    <input type="hidden" name="order" value="">
    <%= button("Save").submit(true) %>&nbsp;<%= button("Cancel").href(reorderingProjects ? urlProvider(AdminUrls.class).getAdminConsoleURL() : urlProvider(AdminUrls.class).getManageFoldersURL(getContainer())) %>
</labkey:form>
