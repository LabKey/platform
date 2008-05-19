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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    HttpView me = (org.labkey.api.view.HttpView) HttpView.currentView();
    GWTView.GWTViewBean bean = (GWTView.GWTViewBean) me.getModelBean();
%>
<div id="<%= PageFlowUtil.filter(bean.getModuleName()) %>-Root"></div>
<%
String contextPath = request.getContextPath();
String jsPath = bean.getModuleName() + "/" + bean.getModuleName() + ".nocache.js";

%>
<script type="text/javascript">
    LABKEY.requiresScript("<%=jsPath%>", <%= bean.isImmediateLoad()%>);
    
    <%= GWTView.PROPERTIES_OBJECT_NAME %> = new Object();
<%
    for (Map.Entry<String, String> entry : bean.getProperties().entrySet())
    {%>
    <%= GWTView.PROPERTIES_OBJECT_NAME %>[<%= PageFlowUtil.jsString(entry.getKey()) %>] = <%= PageFlowUtil.jsString(entry.getValue()) %>;<%
    }
%>
</script>
