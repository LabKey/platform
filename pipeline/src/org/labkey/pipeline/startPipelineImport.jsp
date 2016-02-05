<%
/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.pipeline.PipelineController.StartFolderImportForm" %>
<%@ page import="org.labkey.pipeline.PipelineController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("Ext4"));
        resources.add(ClientDependency.fromPath("pipeline/ImportAdvancedOptions.js"));
        return resources;
    }
%>
<%
    JspView<StartFolderImportForm> me = (JspView<StartFolderImportForm>) HttpView.currentView();
    StartFolderImportForm bean = me.getModelBean();

    Container c = getViewContext().getContainerNoTab();
    Container project = c.getProject();

    boolean canCreateSharedDatasets = false;
    if (bean.isAsStudy() && !c.isProject() && null != project && project != c)
    {
        if (project.hasPermission(getViewContext().getUser(), AdminPermission.class))
        {
            Study studyProject = StudyService.get().getStudy(project);
            if (null != studyProject && studyProject.getShareDatasetDefinitions())
                canCreateSharedDatasets = true;
        }
    }
%>

<labkey:errors/>
<labkey:form id="pipelineImportForm" action="<%=h(buildURL(PipelineController.StartFolderImportAction.class))%>" method="post">
    <input type="hidden" name="fromZip" value=<%=bean.isFromZip()%>>
    <input type="hidden" name="asStudy" value=<%=bean.isAsStudy()%>>
    <input type="hidden" name="filePath" value=<%=q(bean.getFilePath())%>>
    <div id="startPipelineImportForm"></div>
    <div id="advancedImportOptionsForm"></div>
</labkey:form>

<style>
    .main-form-btn {
        margin-top: 30px;
    }

    .main-form-cell {
        padding-top: 5px;
    }

    .import-option-panel {
        padding-top: 10px;
    }

    .import-option-panel .x4-panel-body {
        padding: 10px;
    }

    .import-option-header {
        padding-bottom: 5px;
    }

    .import-option-title {
        font-weight: bold;
    }

    .import-option-input {
        padding-top: 5px;
    }

    .import-option-hide {
        display: none;
    }
</style>

<script type="text/javascript">
Ext4.onReady(function()
{
    LABKEY.Ajax.request({
        url: LABKEY.ActionURL.buildURL("core", "getRegisteredFolderImporters"),
        method: 'POST',
        jsonData: {
            sortAlpha: true
        },
        scope: this,
        success: function (response)
        {
            var responseText = Ext4.decode(response.responseText);

            Ext4.create('LABKEY.import.OptionsPanel', {
                renderTo: 'startPipelineImportForm',
                advancedImportOptionId: 'advancedImportOptionsForm',
                importers: responseText.importers,
                canCreateSharedDatasets: <%=canCreateSharedDatasets%>,
                isCreateSharedDatasets: <%=bean.isCreateSharedDatasets()%>,
                isValidateQueries: <%=bean.isValidateQueries()%>,
                isAdvancedImportOptions: <%=bean.isAdvancedImportOptions()%>
            });
        }
    });
});

</script>
