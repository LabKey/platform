<%
    /*
     * Copyright (c) 2014-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.model.ViewCategory"%>
<%@ page import="org.labkey.api.reports.model.ViewCategoryManager.ViewCategoryTreeNode"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.query.reports.ReportsController.NotificationsForm" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("dataview/ManageNotifications.js");
    }
%>
<%
    JspView<NotificationsForm> me = (JspView<NotificationsForm>) HttpView.currentView();
    List<ViewCategoryTreeNode> categories = me.getModelBean().getCategorySubcriptionTree();
    String returnURLString = me.getModelBean().getReturnUrl();
%>

<script type="text/javascript">
    Ext4.onReady(function()
    {
        var categories = getCategories();
        var returnUrl = null;
        <% if (null != returnURLString) {%>
            returnUrl = '<%=returnURLString%>';
        <%}%>
        var notifyOption = <%=q(me.getModelBean().getNotifyOption())%>

        var manager = Ext4.create('LABKEY.ext4.ManageReportNotifications');
        var panel = manager.getManageReportPanel({
                    renderTo : 'manageNotificationsDiv',
                    minWidth : 750
                },
                categories, returnUrl, notifyOption);
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
    Choose an option below to configure email notification of changes to reports and datasets in this study.
    You will receive a daily digest email listing changes to reports and datasets according to your selection.
    <br><br>
</div>
<div id="manageNotificationsDiv">
</div>
