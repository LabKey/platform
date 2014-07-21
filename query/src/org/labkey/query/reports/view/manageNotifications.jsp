<%
    /*
     * Copyright (c) 2014 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.model.ViewCategoryManager.ViewCategoryTreeNode" %>
<%@ page import="org.labkey.api.reports.model.ViewCategory" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.query.reports.ReportsController.NotificationsForm" %>
<%@ page import="org.labkey.api.util.ReturnURLString" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("dataview/ManageNotifications.js"));
        return resources;
    }
%>
<%
    JspView<NotificationsForm> me = (JspView<NotificationsForm>) HttpView.currentView();
    List<ViewCategoryTreeNode> categories = me.getModelBean().getCategorySubcriptionTree();
    ReturnURLString returnURLString = me.getModelBean().getReturnUrl();
%>

<script type="text/javascript">
    Ext4.onReady(function()
    {
        var categories = getCategories();
        var returnUrl = null;
        <% if (null != returnURLString) {%>
            returnUrl = '<%=returnURLString%>';
        <%}%>

        var manager = Ext4.create('LABKEY.ext4.ManageReportNotifications');
        var panel = manager.getManageReportPanel({renderTo : 'manageNotificationsDiv'}, categories, returnUrl);
    });

</script>

<script type="text/javascript">
    function getCategories()
    {
        var categories = [];
        <%
        for (ViewCategoryTreeNode categoryNode : categories) {
            ViewCategory category = categoryNode.getViewCategory();
        %>
            categories.push({
                'label' : '<%=category.getLabel()%>',
                'rowid' : '<%=category.getRowId()%>',
                'subscribed' : <%=categoryNode.isUserSubscribed()%>
            });
        <%
            List<ViewCategoryTreeNode> subCategories = categoryNode.getChildren();
            for (ViewCategoryTreeNode subCategoryNode : subCategories) {
                ViewCategory subCategory = subCategoryNode.getViewCategory();
        %>
                categories.push({
                    'label' : '&nbsp;&nbsp;&nbsp;&nbsp;' + '<%=subCategory.getLabel()%>',
                    'rowid' : '<%=subCategory.getRowId()%>',
                    'subscribed' : <%=subCategoryNode.isUserSubscribed()%>
                });
        <%
            }
        }
        %>
        return categories;
    }

</script>

<div>
    Select the categories about which you want to be notified.
    You will receive a daily digest of any changes to reports and datasets in the categories you select.
    <br><br>
</div>
<div id="manageNotificationsDiv">
</div>
