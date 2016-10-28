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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="org.labkey.api.pipeline.PipelineStatusUrls" %>
<%@ page import="org.labkey.api.util.NetworkDrive" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.pipeline.api.PipelineStatusFileImpl" %>
<%@ page import="org.labkey.pipeline.api.PipelineStatusManager" %>
<%@ page import="org.labkey.pipeline.status.StatusController" %>
<%@ page import="org.labkey.pipeline.status.StatusController.ConfirmDeleteStatusForm" %>
<%@ page import="java.io.File" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%!
    public String renderStatusFile(PipeRoot root, PipelineStatusFileImpl file, Set<ExpRun> allRuns)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("<li style='padding-bottom:0.5em;'>");

        Container c = file.lookupContainer();
        PipelineStatusUrls provider = PageFlowUtil.urlProvider(PipelineStatusUrls.class);
        ActionURL detailsURL = null != provider ? provider.urlDetails(c, file.getRowId()) : null;
        sb.append("<span>job: <a href='").append(h(detailsURL)).append("'>").append(h(file.getDescription())).append("</a></span>");

        // Directory that will be deleted if they aren't any usages
        File statusFile = new File(file.getFilePath());
        File analysisDir = statusFile.getParentFile();
        if (root != null && root.isUnderRoot(analysisDir) && NetworkDrive.exists(analysisDir))
        {
            String relativePath = root.relativePath(analysisDir);
            if (relativePath != null)
            {
                sb.append("<br>");
                sb.append("&nbsp;&nbsp;");
                sb.append("<span>Analysis directory '").append(h(relativePath)).append("' will be deleted if no runs or datas are in the directory.");
            }
        }

        // Get any associated experiment runs
        List<? extends ExpRun> runs = ExperimentService.get().getExpRunsForJobId(file.getRowId());
        if (!runs.isEmpty())
        {
            for (ExpRun run : runs)
            {
                sb.append("<br>");
                sb.append("&nbsp;&nbsp;");
                sb.append("<span class='exp-run labkey-disabled'>run: <a href='").append(h(run.detailsURL())).append("'>").append(h(run.getName())).append("</a></span>");
            }
            allRuns.addAll(runs);
        }

        /*
        // Check if any datas exist under the analysis directory that are not owned by the runs already listed
        List<? extends ExpData> datas = ExperimentService.get().getExpDatasUnderPath(analysisDir, null);
        if (!datas.isEmpty())
        {
            for (ExpData data : datas)
            {
                // Skip datas created by runs already listed.  They will be deleted if the run is deleted.
                if (runs.contains(data.getRun()))
                    continue;

                sb.append("<br>");
                sb.append("&nbsp;&nbsp;");
                sb.append("<span class='exp-data'>data: ");
                URLHelper url = data.detailsURL();
                if (url != null)
                    sb.append("<a href='").append(h(url)).append("'>").append(h(data.getName())).append("</a>");
                else
                    sb.append(h(data.getName()));
                sb.append("</span>");
            }
        }
        */

        // Recurse into child jobs
        List<PipelineStatusFileImpl> children = PipelineStatusManager.getSplitStatusFiles(file.getJobId());
        if (children.size() > 0)
        {
            sb.append("<ul>");
            for (PipelineStatusFileImpl child : children)
            {
                sb.append(renderStatusFile(root, child, allRuns));
            }
            sb.append("</ul>");
        }

        sb.append("</li>");
        return sb.toString();
    }
%>
<labkey:errors />
<%
    ConfirmDeleteStatusForm form = (ConfirmDeleteStatusForm)HttpView.currentModel();

    PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());

    int[] rowIds = form.getRowIds();
    if (rowIds == null)
        rowIds = new int[0];
    List<PipelineStatusFileImpl> files = PipelineStatusManager.getStatusFiles(rowIds);

    Set<ExpRun> allRuns = new LinkedHashSet<>();
%>
<p>Delete selected pipeline jobs? Only inactive jobs (e.g. Complete, Canceled) may be deleted.</p>

<ul>
<% for (PipelineStatusFileImpl file : files) { %>
    <%=text(renderStatusFile(root, file, allRuns))%>
<% } %>
</ul>

<labkey:form action="<%= h(getViewContext().cloneActionURL().deleteParameters()) %>" method="post">

<% if (!allRuns.isEmpty()) { %>

<script>
    function onDeleteRunsChange(checkbox) {
        var enable = checkbox.checked;
        var runs = document.querySelectorAll(".exp-run");
        for (var i = 0, len = runs.length; i < len; i++)
        {
            var run = runs[i];
            if (enable)
                Ext4.get(run).removeCls("labkey-disabled");
            else
                Ext4.get(run).addCls("labkey-disabled");
        }
    }
</script>
<input type="checkbox" id="deleteRuns" name="deleteRuns" value="true" <%=checked(form.isDeleteRuns())%> onchange="onDeleteRunsChange(this);">
<label for="deleteRuns">Delete associated experiment runs when deleting jobs?<br>
<span style="margin-left:1.5em;">When checked, both the job and the associated experiment run will be deleted.</span>
</label>

<% } %>

<p>
    <input type="hidden" name="confirm" value="true">
<%
    if (getViewContext().getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME) != null)
    {
        for (String selectedValue : getViewContext().getRequest().getParameterValues(DataRegion.SELECT_CHECKBOX_NAME))
        {
    %><input type="hidden" name="<%= h(DataRegion.SELECT_CHECKBOX_NAME) %>" value="<%= h(selectedValue) %>" /><%
        }
    }
%>

<% if (form.getDataRegionSelectionKey() != null) { %>
    <input type="hidden" name="<%= h(DataRegionSelection.DATA_REGION_SELECTION_KEY) %>" value="<%= h(form.getDataRegionSelectionKey()) %>" />
<% } %>

<% if (form.getReturnUrl() != null) { %>
    <input type="hidden" name="returnURL" value="<%= h(form.getReturnUrl()) %>"/>
<% } %>

<%= button("Confirm Delete").submit(true) %>
<%= text(form.getReturnUrl() == null || form.getReturnUrl().isEmpty()
        ? button("Cancel").href(buildURL(StatusController.BeginAction.class)).toString()
        : button("Cancel").href(form.getReturnUrl()).toString()) %>
</labkey:form>
