<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.files.FileContentService"%>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.io.File" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    AdminController.ProjectSettingsForm bean = ((JspView<AdminController.ProjectSettingsForm>)HttpView.currentView()).getModelBean();

    // the default project file root based on the site default root
    String projectDefaultRoot = "";
    File siteRoot = ServiceRegistry.get().getService(FileContentService.class).getSiteDefaultRoot();
    if (siteRoot != null)
    {
        File projRoot = new File(siteRoot, getViewContext().getContainer().getProject().getName());
        if (projRoot != null)
        {
            projectDefaultRoot = projRoot.getCanonicalPath();
        }
    }
%>

<%  if (bean.getConfirmMessage() != null) { %>
        <p class="labkey-message"><%= PageFlowUtil.filter(bean.getConfirmMessage()) %></p>
<%  } %>

<labkey:errors/>

<form action="" method="post">
    <%
        WebPartView.startTitleFrame(out, "Configure File Root ");
    %>

    <table>
        <tr><td></td></tr>
        <tr><td colspan="10">LabKey Server allows you to upload and process your data files, including flow, proteomics and study-related
            files. By default, LabKey stores your files in a standard directory structure. You can override this location for each
            project if you wish.
        </td></tr>
        <tr><td></td></tr>
        <tr>
<%--
            <td class="labkey-form-label">File&nbsp;root&nbsp;<%=PageFlowUtil.helpPopup("File root", "Set a project-level file root. " +
                "When a project-level file root is set, each folder for that project has a corresponding subdirectory in the file system.")%></td>
--%>
            <td>
                <table>
                    <tr><td><input type="radio" name="fileRootOption" id="optionDisable" value="<%=AdminController.ProjectSettingsForm.FileRootProp.disable%>"
                                   <%=AdminController.ProjectSettingsForm.FileRootProp.disable.name().equals(bean.getFileRootOption()) ? " checked" : ""%>
                                   onclick="updateSelection();">
                        Disable file sharing for this project</td></tr>
                    <tr>
                        <td><input type="radio" name="fileRootOption" id="optionSiteDefault" value="<%=AdminController.ProjectSettingsForm.FileRootProp.siteDefault%>"
                                   <%=AdminController.ProjectSettingsForm.FileRootProp.siteDefault.name().equals(bean.getFileRootOption()) ? " checked" : ""%>
                                   onclick="updateSelection();">
                            Use a default based on the site-level root</td>
                        <td><input type="text" id="rootPath" size="64" disabled="true" value="<%=h(projectDefaultRoot)%>"></td>
                    </tr>
                    <tr>
                        <td><input type="radio" name="fileRootOption" id="optionProjectSpecified" value="<%=AdminController.ProjectSettingsForm.FileRootProp.projectSpecified%>"
                                   <%=AdminController.ProjectSettingsForm.FileRootProp.projectSpecified.name().equals(bean.getFileRootOption()) ? " checked" : ""%>
                                   onclick="updateSelection();">
                            Use a project-level file root</td>
                        <td><input type="text" id="projectRootPath" name="projectRootPath" size="64" value="<%=h(bean.getProjectRootPath())%>"></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td><%=PageFlowUtil.generateSubmitButton("Save")%></td>
        </tr>
    </table>
    <%
        WebPartView.endTitleFrame(out);
    %>
</form>

<script type="text/javascript">

    Ext.onReady(function()
    {
        updateSelection();
    });

    function updateSelection()
    {
        if (document.getElementById('optionDisable').checked)
        {
            document.getElementById('projectRootPath').style.display = 'none';
            document.getElementById('rootPath').style.display = 'none';
        }
        if (document.getElementById('optionSiteDefault').checked)
        {
            document.getElementById('projectRootPath').style.display = 'none';
            document.getElementById('rootPath').style.display = '';
        }
        if (document.getElementById('optionProjectSpecified').checked)
        {
            document.getElementById('projectRootPath').style.display = '';
            document.getElementById('rootPath').style.display = 'none';
        }
    }
</script>

