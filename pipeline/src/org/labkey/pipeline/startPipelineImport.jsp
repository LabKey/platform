<%
/*
 * Copyright (c) 2016-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.pipeline.PipelineController.StartFolderImportAction" %>
<%@ page import="org.labkey.pipeline.PipelineController.StartFolderImportForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("pipeline/importAdvancedOptions");
    }
%>
<%
    JspView<StartFolderImportForm> me = (JspView<StartFolderImportForm>) HttpView.currentView();
    StartFolderImportForm bean = me.getModelBean();

    Container c = getViewContext().getContainerNoTab();
    Container project = c.getProject();
    boolean isProjectAdmin = project != null && project.hasPermission(getUser(), AdminPermission.class);
    String importFormId = "pipelineImportForm";
    StudyService studyService = StudyService.get();

    boolean canCreateSharedDatasets = false;
    if (!c.isProject() && null != project && project != c)
    {
        if (project.hasPermission(getViewContext().getUser(), AdminPermission.class))
        {
            Study studyProject = studyService != null ? studyService.getStudy(project) : null;
            if (null != studyProject && studyProject.getShareDatasetDefinitions())
                canCreateSharedDatasets = true;
        }
    }

    Study study = studyService != null ? studyService.getStudy(getContainer()) : null;
    TimepointType timepointType = study != null ? study.getTimepointType() : null;
%>

<labkey:errors/>
<labkey:form id="<%=importFormId%>" action="<%=urlFor(StartFolderImportAction.class)%>" method="post">
    <input type="hidden" name="fromZip" value=<%=bean.isFromZip()%>>
    <input type="hidden" name="filePath" value="<%=h(bean.getFilePath())%>">
    <div id="startPipelineImportForm"></div>
</labkey:form>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
Ext4.onReady(function()
{
    LABKEY.Ajax.request({
        url: LABKEY.ActionURL.buildURL("core", "getRegisteredFolderImporters"),
        method: 'POST',
        jsonData: {
            sortAlpha: true,
            archiveFilePath: <%=q(bean.getFilePath())%>
        },
        scope: this,
        success: function (response)
        {
            var responseText = Ext4.decode(response.responseText);

            Ext4.create('LABKEY.import.OptionsPanel', {
                renderTo: 'startPipelineImportForm',
                formId: <%=q(importFormId)%>,
                importers: responseText.importers,
                isProjectAdmin: <%=isProjectAdmin%>,
                canCreateSharedDatasets: <%=canCreateSharedDatasets%>,
                isCreateSharedDatasets: <%=bean.isCreateSharedDatasets()%>,
                isValidateQueries: <%=bean.isValidateQueries()%>,
                isSpecificImportOptions: <%=bean.isSpecificImportOptions()%>,
                isApplyToMultipleFolders: <%=bean.isApplyToMultipleFolders()%>,
                isFailForUndefinedVisits: <%=bean.isFailForUndefinedVisits()%>,
                isCloudRoot: <%=bean.isCloudRoot()%>,   // Remove as part of Issue #43835
                showFailForUndefinedVisits: <%=(timepointType == null || timepointType == TimepointType.VISIT) && ((study == null) || !study.isFailForUndefinedTimepoints())%>
            });
        },
        failure: function(response)
        {
            LABKEY.Utils.alert('Error', 'Failed to get folder import info. Folder XML file may be invalid.');
        }
    });
});

</script>
