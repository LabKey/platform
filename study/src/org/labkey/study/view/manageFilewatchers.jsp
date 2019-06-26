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
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.pipeline.TaskPipeline" %>
<%@ page import="org.labkey.api.pipeline.file.FileAnalysisTaskPipeline" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<labkey:panel>
    <table width="65%">
        <tr>
            <td>
                <p>A study can be configured to reload server objects automatically when specific files are updated.</p>
            </td>
        </tr>
        <%
            if (getContainer().hasPermission(getUser(), AdminPermission.class))
            {
                for (TaskPipeline taskPipeline : PipelineJobService.get().getTaskPipelines(getContainer()))
                {
                    if (taskPipeline instanceof FileAnalysisTaskPipeline)
                    {
                        FileAnalysisTaskPipeline fatp = (FileAnalysisTaskPipeline) taskPipeline;
                        if (fatp.isAllowForTriggerConfiguration())
                        {
            %>
                            <tr>
                                <td>
                                    <p><a href="<%=urlProvider(PipelineUrls.class).urlCreatePipelineTrigger(getContainer(), fatp.getId().getName(), getActionURL())%>">Create a trigger to <%=h(fatp.getDescription())%></a></p>
                                </td>
                            </tr>
            <%
                        }
                    }
                }
            }
        %>
        <tr>
            <td>
                <p>
                    <%=
                        button("Manage file watcher triggers").href(urlProvider(QueryUrls.class).urlExecuteQuery(getContainer(), "pipeline", "TriggerConfigurations")).build()
                    %>
                </p>
            </td>
        </tr>
    </table>
</labkey:panel>