<%
/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.assay.nab.view.RunDetailOptions" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
%>

<table><tr>
<%
    ActionURL graphAction = bean.getGraphURL();
    graphAction.addParameter("rowId", bean.getRunId());
    if (bean.getFitType() != null)
        graphAction.addParameter("fitType", bean.getFitType());
    int maxSamplesPerGraph = bean.getMaxSamplesPerGraph();
    int sampleCount = bean.getSampleResults().size();
    graphAction.addParameter("width", bean.getGraphWidth());
    graphAction.addParameter("height", bean.getGraphHeight());

    if (bean.getDataIdentifier() != null)
        graphAction.addParameter(RunDetailOptions.DATA_IDENTIFIER_PARAM, bean.getDataIdentifier().name());

    int graphCount = 0;
    for (int firstSample = 0; firstSample < sampleCount; firstSample += maxSamplesPerGraph)
    {
        graphAction.replaceParameter("firstSample", "" + firstSample);
        graphAction.replaceParameter("maxSamples", "" + maxSamplesPerGraph);
        ActionURL zoomGraphURL = graphAction.clone();
        zoomGraphURL.replaceParameter("width", "" + 800);
        zoomGraphURL.replaceParameter("height", "" + 600);
%>
        <td><a href="<%= text(zoomGraphURL.getLocalURIString())%>" target="_blank">
            <img src="<%= text(graphAction.getLocalURIString()) %>" alt="Neutralization Graph">
        </a></td>
<%
        if (++graphCount % bean.getGraphsPerRow() == 0)
            out.write("</tr><tr>");
    }
%>
</tr></table>