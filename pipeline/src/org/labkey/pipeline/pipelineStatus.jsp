<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.pipeline.PipelineJob" %>
<%@ page import="org.labkey.api.pipeline.PipelineJobData" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.DeletePermission" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.pipeline.PipelineController.CancelJobAction" %>
<%@ page import="org.labkey.pipeline.PipelineController.StatusModel" %>
<%@ page import="org.labkey.pipeline.PipelineController.StatusParams" %>
<%@ page import="org.labkey.pipeline.status.StatusController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
private HtmlString outputJob(String status, PipelineJob job,
                             boolean isAllContainers, boolean canCancel)
{
    StringBuilder ret = new StringBuilder("<tr><td>");
    URLHelper href = null;
    if (!status.equals("pending"))
        href = job.getStatusHref();

    if (href == null)
    {
        ret.append(h(status));
    }
    else
    {
        ret.append("<a href=\"").append(h(href)).append("\">")
            .append(h(status)).append("</a>");
    }
    ret.append("</td><td>");
    ret.append(h(job.getUser().getName())).append("</td>\n<td>");
    ret.append(h(job.getDescription())).append("</td>");
    if (isAllContainers)
        ret.append("<td>").append(h(job.getContainer().getPath())).append("</td>");

    if (status.equals("pending") && canCancel)
    {
        ret.append("<td>");
        ActionURL cancelUrl = urlFor(CancelJobAction.class).addParameter("jobId", job.getJobGUID());
        if (isAllContainers)
            cancelUrl.addParameter("allcontainers", "1");
        ret.append(button("cancel").href(cancelUrl));
        ret.append("</td>");
    }
    return HtmlString.unsafe(ret.toString());
}

%>
<%
    JspView<StatusModel> me = (JspView<StatusModel>) HttpView.currentView();
    StatusModel bean = me.getModelBean();
    PipelineJobData jobData = bean.getJobData();

    Container c = getContainer();
    User user = getUser();

    boolean canCancel = c.hasPermission(user, DeletePermission.class);

    boolean isAllContainers = request.getParameter(StatusParams.allcontainers.toString()) != null;

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
    <%= button("Grid").href(StatusController.urlShowList(ContainerManager.getRoot(), false)) %>
