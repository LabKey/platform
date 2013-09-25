<%
/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.audit.AuditLogService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.list.ListDefinition" %>
<%@ page import="org.labkey.api.exp.list.ListService" %>
<%@ page import="org.labkey.api.lists.permissions.DesignListPermission" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.NavTree" %>
<%@ page import="org.labkey.api.view.PopupMenu" %>
<%@ page import="org.labkey.api.view.PopupMenuView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.list.controllers.ListController" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ViewContext> view = (JspView<ViewContext>) HttpView.currentView();
    ViewContext me = view.getModelBean();
    ViewContext ctx = HttpView.currentContext();
    Container c = ctx.getContainer();
    User user = ctx.getUser();
    Map<String, ListDefinition> lists = ListService.get().getLists(c);
    NavTree links;
    PopupMenuView pmw;
    boolean isBegin = true;

    if (me != null)
    {
        isBegin = false;
    }
%>
<table id="lists">
    <%
        if (lists.isEmpty())
        {
    %>
        <tr><td>There are no user-defined lists in this folder.</td></tr>
    <%
        }
        else
        {
            for (ListDefinition list : new TreeSet<>(lists.values()))
            {
                links = new NavTree("");
                links.addChild("View Data", list.urlShowData());
                if (c.hasPermission(user, DesignListPermission.class))
                {
                    links.addChild("View Design", list.urlShowDefinition());
                }
                if (AuditLogService.get().isViewable())
                {
                    links.addChild("View History", list.urlShowHistory());
                }
                if (c.hasPermission(user, DesignListPermission.class))
                {
                    links.addChild("Delete List", list.urlFor(ListController.DeleteListDefinitionAction.class));
                }
                %><tr><%
                if (!isBegin && links.getChildren().length > 1)
                {
                    pmw = new PopupMenuView(links);
                    pmw.setButtonStyle(PopupMenu.ButtonStyle.TEXT);
                    %><td><%
                    include(pmw, out);
                    %></td><%
                }
                %><td><a href="<%=PageFlowUtil.filter(list.urlShowData())%>"><%=PageFlowUtil.filter(list.getName())%></a></td><%
                if (isBegin)
                {
                    for (NavTree link : links.getChildren())
                    {
                        // the toLowerCase() is just for the ListTest, fix the test
                        %><td><labkey:link href="<%=text(link.getHref())%>" text="<%=text(link.getText().toLowerCase())%>" /></td><%
                    }
                    if(c.hasPermission(user, AdminPermission.class))
                    {
                        String onClick = "truncateTable("+hq(list.getName())+")";
                        %><td><%=textLink("Delete All Rows", "#", onClick, "")%></td><%
                    }
                }
                %></tr><%
            }
        }
    %>
</table>
<%
    if (c.hasPermission(user, DesignListPermission.class))
    {
        if (isBegin)
        {
%>
        <br/>
        <labkey:button text="Create New List" href="<%=h(urlFor(ListController.EditListDefinitionAction.class))%>"/>
        <labkey:button text="Import List Archive" href="<%=h(urlFor(ListController.ImportListArchiveAction.class))%>"/>
<%
            if (!lists.isEmpty())
            {
%>
                <labkey:button text="Export List Archive" href="<%=h(urlFor(ListController.ExportListArchiveAction.class))%>"/>
<%
            }
        }
        else
        {
%>
    <%=PageFlowUtil.textLink("manage lists", ListController.getBeginURL(c))%>
<%
        }
    }
%>

<script type="text/javascript">
    LABKEY.requiresExt4Sandbox(true);
</script>

<script type="text/javascript">
    function truncateTable(queryName)
    {
        Ext4.Msg.confirm("Confirm Deletion",
                "Are you sure you wish to delete all rows for this list?  This action cannot be undone.",
                function(button){
                    if (button === 'yes') {
                        truncate();
                    }
                }
        );

        function truncate()
        {
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('query', 'truncateTable'),
                method  : 'POST',
                success: function(response){
                    console.log(response);
                    var data = Ext4.JSON.decode(response.responseText);
                    Ext4.Msg.alert("Success", data.deletedRows + " rows deleted");
                },
                failure : function(response)
                {
                    var data = Ext4.JSON.decode(response.responseText);
                    Ext4.Msg.alert("Failed to Delete Rows", data.exception);
                },
                jsonData : {schemaName : 'lists', queryName : queryName},
                headers : {'Content-Type' : 'application/json'},
                scope : this
            });
        }

    }
</script>