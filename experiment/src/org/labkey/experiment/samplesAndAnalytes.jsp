<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleSet" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.experiment.api.ExperimentServiceImpl" %>
<%@ page import="org.labkey.api.data.Table" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.experiment.api.ExpSampleSetImpl" %>
<%@ page import="org.labkey.experiment.api.MaterialSource" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView me = (JspView) HttpView.currentView();
    ViewContext ctx = me.getViewContext();
    Container proj = ctx.getContainer().getProject();

    SimpleFilter filter = new SimpleFilter();
    Object[] params = { proj.getId(), ContainerManager.getSharedContainer().getProject().getId(), ContainerManager.getSharedContainer().getId() };
    filter.addWhereClause("(Project = ? OR Project = ? OR Container = ?)", params, "Project");

    ExpSampleSet[] sampleSets = ExpSampleSetImpl.fromMaterialSources(Table.select(ExperimentServiceImpl.get().getTinfoMaterialSourceWithProject(), Table.ALL_COLUMNS, filter, null, MaterialSource.class));
    
    int i = 0;
%> <div style="vertical-align:top;display:inline-block;margin-right:1em" width="<%=sampleSets.length > 1 ? "50%" : "100%"%>"><%
    for (ExpSampleSet sampleSet : sampleSets)
    {
        ActionURL url;
        boolean isStudySample = "Study Specimens".equals(sampleSet.getName());
        if (isStudySample)
            url = new ActionURL("study-samples", "samples.view", sampleSet.getContainer());
        else
            url = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, sampleSet.getContainer()).replaceParameter("rowId", "" + sampleSet.getRowId());
        %>
<span class="highlightregion"></span><b><a href="<%=url%>"><%=h(isStudySample ? sampleSet.getContainer().getName() : sampleSet.getName())%></a></b>
            <br><%=sampleSet.getDescription() != null ? h(sampleSet.getDescription()) : h(sampleSet.getContainer().getPath())%>
        <br><span class="highlightregion">
<%
        if (sampleSets.length > 1 && ++i == sampleSets.length / 2)
        { %>
            </div><div style="vertical-align:top;display:inline-block" width="50%">
    <%  }
    }
%></div>
