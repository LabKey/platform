<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.FilesSiteSettingsAction" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    FilesSiteSettingsAction.FileSettingsForm bean = ((JspView<FilesSiteSettingsAction.FileSettingsForm>)HttpView.currentView()).getModelBean();
%>

<labkey:errors/>
<form action="filesSiteSettings.view" method="post">
    <table>
        <tr><td colspan=2>Configure file system</td></tr>
        <tr><td class="labkey-form-label">File root <%=PageFlowUtil.helpPopup("File root", "Set a site level file root. " +
                "When a site level file root is set, each folder for every project has a corresponding subdirectory in the file system." +
                " A site level file root may be overridden at the project level from 'Project Settings'.")%></td>
            <td><input type="text" name="rootPath" size="64" value="<%=h(bean.getRootPath())%>"></td>
        </tr>

        <tr><td>&nbsp;</td></tr>
        <tr>
            <td><%=PageFlowUtil.generateSubmitButton("Save")%>&nbsp;
                <%=PageFlowUtil.generateButton("Cancel", PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL())%>
            </td>
        </tr>
    </table>
</form>

