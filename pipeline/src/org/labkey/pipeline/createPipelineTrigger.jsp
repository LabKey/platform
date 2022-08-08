<%
/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.pipeline.PipelineJobService" %>
<%@ page import="org.labkey.api.pipeline.file.FileAnalysisTaskPipeline" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.pipeline.PipelineController.PipelineTriggerForm" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.function.Function" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page import="org.labkey.api.formSchema.FormSchema" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.pipeline.trigger.PipelineTriggerRegistry" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("gen/createPipelineTrigger");
    }
%>
<%
    HttpView<PipelineTriggerForm> me = (HttpView<PipelineTriggerForm>) HttpView.currentView();
    PipelineTriggerForm bean = me.getModelBean();
    String docLink = new HelpTopic("fileWatcher").getHelpTopicHref();

    Map<String, FileAnalysisTaskPipeline> triggerConfigTasks = PipelineJobService.get().getTaskPipelines(getContainer())
        .stream()
        .filter(FileAnalysisTaskPipeline.class::isInstance)
        .map(FileAnalysisTaskPipeline.class::cast)
        .filter(FileAnalysisTaskPipeline::isAllowForTriggerConfiguration)
        .collect(Collectors.toMap(FileAnalysisTaskPipeline -> FileAnalysisTaskPipeline.getId().getName(), Function.identity()));

    // would appear on the URL param
    if (bean.getPipelineTask() != null && triggerConfigTasks.containsKey(bean.getPipelineTask()))
    {
        bean.setPipelineId(triggerConfigTasks.get(bean.getPipelineTask()).getId().toString());
    }

    String uniqueId = "" + UniqueID.getServerSessionScopedUID();
    String appId = "create-pipeline-trigger-" + uniqueId;
    ObjectMapper jsonMapper = new ObjectMapper();
    FormSchema detailsFormSchema = PipelineJobService.get().getFormSchema(getContainer());
    Map<String, FormSchema> taskFormSchemas = new HashMap<>();
    Map<String, FormSchema> customFieldFormSchemas = new HashMap<>();
    Map<String, String> tasksHelpText = new HashMap<>();

    for (FileAnalysisTaskPipeline pipeline : triggerConfigTasks.values())
    {
        String pipelineId = pipeline.getId().toString();
        taskFormSchemas.put(pipelineId, pipeline.getFormSchema());
        tasksHelpText.put(pipelineId, pipeline.getHelpText());
        customFieldFormSchemas.put(pipelineId, pipeline.getCustomFieldsFormSchema());
    }
%>

<%
    if (PipelineTriggerRegistry.get().getTypes().isEmpty()) { // PREMIUM UPSELL
%>
    <div class="alert alert-info">
        <h3>There are no pipeline trigger types available on this server.</h3>
        <hr>
        <p>Premium edition subscribers have access to powerful <a class="alert-link" href="<%=h(docLink)%>">file watcher</a>
            triggers that can automatically initiate pipeline tasks.</p>
        <p>In addition to this feature, premium editions of LabKey Server provide professional support and advanced functionality to help teams maximize the value of the platform.</p>
        <br>
        <p><a class="alert-link" href="https://www.labkey.com/platform/go-premium/" target="_blank" rel="noopener noreferrer">Go Premium <i class="fa fa-external-link"></i></a></p>
    </div>
<%
    } else {
%>
    <div id="<%=h(appId)%>"></div>

    <script type="application/javascript">
        const detailsFormSchema = JSON.parse(<%=q(jsonMapper.writeValueAsString(detailsFormSchema))%>);
        const taskFormSchemas = JSON.parse(<%=q(jsonMapper.writeValueAsString(taskFormSchemas))%>);
        const customFieldFormSchemas = JSON.parse(<%=q(jsonMapper.writeValueAsString(customFieldFormSchemas))%>);
        const tasksHelpText = JSON.parse(<%=q(jsonMapper.writeValueAsString(tasksHelpText))%>);
        const rowId = <%=bean.getRowId()%>;
        const details = {
            "assay provider": <%=q(bean.getAssayProvider())%> || undefined,
            description: <%=q(bean.getDescription())%>,
            enabled: <%=bean.isEnabled()%>,
            name: <%=q(bean.getName())%>,
            pipelineId: <%=q(bean.getPipelineId())%>,
            type: <%=q(bean.getType())%>,
            username: <%=q(bean.getUsername())%> || <%=q(getUser().getDisplayName(getUser()))%>
        }
        const triggerConfig = JSON.parse(<%=q(bean.getConfiguration())%>);
        delete triggerConfig.parameters; // The CreatePipelineTrigger component does not expect this parameter.
        const customConfig = JSON.parse(<%=q(bean.getCustomConfiguration())%>);
        const docsHref = <%=q(docLink)%>;
        const returnUrl = <%=q(bean.getReturnActionURL().toString())%>;

        LABKEY.App.loadApp('createPipelineTrigger', <%=q(appId)%>, {
            customConfig,
            customFieldFormSchemas,
            details,
            detailsFormSchema,
            docsHref,
            returnUrl,
            rowId,
            taskFormSchemas,
            tasksHelpText,
            triggerConfig,
        });
    </script>
<%
    }
%>