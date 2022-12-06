<%
/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.query.reports.ReportsController.NotificationsForm" %>
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
    NotificationsForm form = me.getModelBean();
    String returnURLString = form.getReturnUrl();
%>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    Ext4.onReady(function()
    {
        var returnUrl = LABKEY.ActionURL.buildURL('project', 'begin', null, {'pageId' : 'study.DATA_ANALYSIS'});
        <% if (null != returnURLString) {%>
            returnUrl = <%=q(returnURLString)%>;
        <%}%>

        Ext4.create('LABKEY.ext4.ReportNotificationPanel', {
            title : 'Choose Notification Option',
            categories : <%=toJsonArray(form.getCategories())%>,
            datasets : <%=toJsonArray(form.getDatasets())%>,
            notifyOption : <%=q(form.getNotifyOption())%>,
            returnUrl : returnUrl,
            renderTo : 'manageNotificationsDiv',
            minWidth : 750,
            maxWidth : 726
        });
    });

</script>

<div>
    Choose an option below to configure email notification of changes to reports and datasets in this study.
    You will receive a daily digest email listing changes to reports and datasets according to your selection.
    <br><br>
</div>
<div id="manageNotificationsDiv">
</div>
