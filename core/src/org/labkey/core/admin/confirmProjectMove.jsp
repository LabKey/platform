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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController.ManageFoldersForm" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ManageFoldersForm> view = (JspView<ManageFoldersForm>)HttpView.currentView();
    ManageFoldersForm f = view.getModelBean();
    Container c = getContainer();
    ActionURL cancelURL = PageFlowUtil.urlProvider(AdminUrls.class).getManageFoldersURL(c);
%>

<labkey:form action="<%=h(buildURL(AdminController.MoveFolderAction.class))%>" method="post">
<p>
You are moving folder '<%=h(c.getName())%>' from one project into another.
After the move is complete, you will need to reconfigure permissions settings for this folder, any subfolders, and other secured resources.
</p>
<p>
This action cannot be undone.
</p>
    <input type="hidden" name="addAlias" value="<%=h(f.isAddAlias())%>">
    <input type="hidden" name="target" value="<%=h(f.getTarget())%>">
    <input type="hidden" name="confirmed" value="1">
    <%= button("Confirm Move").submit(true) %>
    <%= button("Cancel").href(cancelURL) %>
</labkey:form>