<%
/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.timeline.TimelineSettings" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("timeline");
    }
%>
<%
    JspView<TimelineSettings> me = (JspView<TimelineSettings>) HttpView.currentView();
    TimelineSettings bean = me.getModelBean();
%>
<div class="ms-form" style="border:1px solid black;width:100%;height:<%=bean.getPixelHeight()%>px" id="<%=bean.getDivId()%>"></div>
<script type="text/javascript">
    LABKEY.Utils.onReady(function() {
        LABKEY.Timeline.create({
            renderTo:<%=q(bean.getDivId())%>,
            start:<%=q(bean.getStartField())%>,
            title:<%=q(bean.getTitleField())%>,
            description:<%=nq(bean.getDescriptionField())%>,
            end:<%=nq(bean.getEndField())%>,
            query:{schemaName:<%=nq(bean.getSchemaName())%>, queryName:<%=nq(bean.getQueryName())%>, viewName:<%=nq(bean.getViewName())%>
        }});
    });
</script>
<%!
    String nq(String str)
    {
        return str == null ? "null" : q(str);
    }
%>