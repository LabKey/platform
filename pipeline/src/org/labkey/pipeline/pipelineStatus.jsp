<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.pipeline.PipelineJob" %>
<%@ page import="org.labkey.api.pipeline.PipelineJobData" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.pipeline.PipelineController" %>
<%@ page import="org.labkey.pipeline.status.StatusController" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
private Object outputJob(String status, PipelineJob job,
                         boolean isAllContainers, boolean canCancel)
{
    StringBuffer ret = new StringBuffer("<tr><td>");
    ActionURL href = null;
    if (!status.equals("pending"))
        href = job.getStatusHref();

    if (href == null)
    {
        ret.append(status);
    }
    else
    {
        ret.append("<a href=\"").append(PageFlowUtil.filter(href)).append("\">")
                .append(status).append("</a>");
    }
    ret.append("</td><td>");
    ret.append(job.getUser().getName()).append("</td>\n<td>");
    ret.append(job.getDescription()).append("</td>");
    if (isAllContainers)
        ret.append("<td>").append(job.getContainer().getPath()).append("</td>");

    if (status.equals("pending") && canCancel)
    {
        ret.append("<td>" + PageFlowUtil.generateButton("cancel", "cancelJob.view?jobId=" + job.getJobGUID() +
                (isAllContainers ? "&allcontainers=1" : "")));
    }
    return ret;
}

%>
<%
    JspView<PipelineController.StatusModel> me =
            (JspView<PipelineController.StatusModel>) HttpView.currentView();
    PipelineController.StatusModel bean = me.getModelBean();
    PipelineJobData jobData = bean.getJobData();

    Container c = getViewContext().getContainer();
    User user = getViewContext().getUser();

    boolean canCancel = c.hasPermission(user, ACL.PERM_DELETE);
    boolean canClear = canCancel;
    boolean isAdmin = user.isAdministrator();

    boolean isAllContainers = request.getParameter(PipelineController.StatusParams.allcontainers.toString()) != null;

    if (jobData.getRunningJobs().size() == 0 && jobData.getPendingJobs().size() == 0)
    { %>
        <br/>
        <p>There are no jobs to display.</p><%
    }
    else
    { %>
        <table border="1">
        <tr><th>status</th><th>user</th><th>description</th><%

        if (isAllContainers)
        { %>
            <th>folder</th><%
        } %>
        </tr><%

        for (PipelineJob job : jobData.getRunningJobs())
        { %>
            <%=outputJob("running", job, isAllContainers, canCancel)%><%
        }
        for (PipelineJob job : jobData.getPendingJobs())
        { %>
            <%=outputJob("pending", job, isAllContainers, canCancel)%><%
        } %>
        </table><%
    } %>
    <%=PageFlowUtil.generateButton("Grid", StatusController.urlShowList(ContainerManager.getRoot(), false))%>
