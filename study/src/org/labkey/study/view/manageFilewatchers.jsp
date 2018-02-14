<%@ page import="org.labkey.api.pipeline.PipelineJobService" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.pipeline.TaskPipeline" %>
<%@ page import="org.labkey.api.pipeline.file.FileAnalysisTaskPipeline" %>
<%@ page import="org.labkey.api.query.QueryUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
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
                                    <p><a href="<%=PageFlowUtil.urlProvider(PipelineUrls.class).urlCreatePipelineTrigger(getContainer(), fatp.getId().getName(), getActionURL())%>">Create a trigger to <%=h(fatp.getDescription())%></a></p>
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
                        button("Manage file watcher triggers").href(PageFlowUtil.urlProvider(QueryUrls.class).urlExecuteQuery(getContainer(), "pipeline", "TriggerConfigurations")).build()
                    %>
                </p>
            </td>
        </tr>
    </table>
</labkey:panel>