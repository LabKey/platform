<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.ExperimentRunType"%>
<%@ page import="org.labkey.api.util.Pair"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.ChooseExperimentTypeBean" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ChooseExperimentTypeBean> me = (JspView<ChooseExperimentTypeBean>) HttpView.currentView();
    ChooseExperimentTypeBean bean = me.getModelBean();
    ActionURL baseURL = bean.getUrl().clone().deleteParameters();
%>
<labkey:form method="get" action="<%= baseURL %>">
    <% for (Pair<String, String> params : bean.getUrl().getParameters())
    {
        if (!"experimentRunFilter".equals(params.getKey()))
        { %>
            <input type="hidden" name="<%=h(params.getKey())%>" value="<%=h(params.getValue())%>" />
    <%  }
    } %>
    <p>
        Filter by run type:
        <select id="experimentRunFilter" name="experimentRunFilter" onchange="form.submit()">
            <% for (ExperimentRunType type : bean.getFilters()) { %>
                <option <% if (type == bean.getSelectedFilter()) { %>selected <% } %> value="<%=h(type.getDescription())%>"><%=h(type.getDescription())%></option>
            <% } %>
        </select>
    </p>
</labkey:form>
