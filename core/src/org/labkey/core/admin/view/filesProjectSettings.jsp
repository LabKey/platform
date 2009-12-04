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
<%@ page import="org.labkey.api.attachments.AttachmentService"%>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.core.admin.AdminController" %>
<%@ page import="java.io.File" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    AdminController.ProjectSettingsForm bean = ((JspView<AdminController.ProjectSettingsForm>)HttpView.currentView()).getModelBean();

    // the default project file root based on the site default root
    String projectDefaultRoot = "";
    File siteRoot = AppProps.getInstance().getFileSystemRoot();
    if (siteRoot != null)
    {
        File projRoot = new File(siteRoot, getViewContext().getContainer().getProject().getName());
        if (projRoot != null)
        {
            projectDefaultRoot = projRoot.getCanonicalPath();
        }
    }
%>

<labkey:errors/>

<form action="" method="post">
    <table>
        <tr><td colspan=2>Configure file system</td></tr>
        <tr><td class="labkey-form-label">File root <%=PageFlowUtil.helpPopup("File root", "Set a project level file root. " +
                "When a project level file root is set, each folder for that project has a corresponding subdirectory in the file system.")%></td>
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
                            Use the site default for the file root</td>
                        <td><input type="text" id="rootPath" size="64" disabled="true" value="<%=h(projectDefaultRoot)%>"></td>
                    </tr>
                    <tr>
                        <td><input type="radio" name="fileRootOption" id="optionProjectSpecified" value="<%=AdminController.ProjectSettingsForm.FileRootProp.projectSpecified%>"
                                   <%=AdminController.ProjectSettingsForm.FileRootProp.projectSpecified.name().equals(bean.getFileRootOption()) ? " checked" : ""%>
                                   onclick="updateSelection();">
                            Use a project specific file root</td>
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

