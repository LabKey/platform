<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.assay.nab.view.GraphSelectedBean"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@page extends="org.labkey.api.jsp.JspBase"%>

<labkey:errors/>
<%
    JspView<GraphSelectedBean> me = (JspView<GraphSelectedBean>) HttpView.currentView();
    GraphSelectedBean bean = me.getModelBean();
    ActionURL chartURL = bean.getGraphRenderURL();

    for (int dataId : bean.getGraphableObjectIds())
    {
        chartURL.addParameter("id", dataId);
    }
    chartURL.addParameter("protocolId", bean.getProtocol().getRowId());
    if (bean.getCaptionColumn() != null)
        chartURL.addParameter("captionColumn", bean.getCaptionColumn());
    if (bean.getChartTitle() != null)
        chartURL.addParameter("chartTitle", bean.getChartTitle());
%>
<img src="<%= chartURL %>">
<br>
<% me.include(bean.getQueryView(), out); %>
