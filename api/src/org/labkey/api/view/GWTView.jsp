<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.GWTView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("clientapi/ext3")); // This is required for our list/dataset/etc designers
        return resources;
    }
%>
<%
    HttpView me = HttpView.currentView();
    GWTView.GWTViewBean bean = (GWTView.GWTViewBean) me.getModelBean();
%>
<%--<textarea id="gwtDebug" cols="200" rows=20 style="overflow:auto;"></textarea>--%>
<div id="<%= h(bean.getModuleName()) %>-Root" class="extContainer <%=h(bean.getLoadingStyleName())%>"></div>
<%
String contextPath = request.getContextPath();
String jsPath = bean.getModuleName() + "/" + bean.getModuleName() + ".nocache.js";
String hashedPath = contextPath + "/" + jsPath + "?" + PageFlowUtil.getServerSessionHash();
%>
<script id="__gwt_marker_<%=h(bean.getModuleName())%>"></script>
<script type="text/javascript" src="<%=h(hashedPath)%>"></script>
<%-- 14005: ExtJS popup menus broken on GWT pages -- removed gwt specific stylesheets --%>
<%--<script type="text/javascript">--%>
    <%--LABKEY.requiresCss("gxt/css/gxt-all.css");--%>
    <%--LABKEY.requiresCss("gxt/css/gxt-gray.css");--%>
<%--</script>--%>
<script type="text/javascript">
    <!-- Pass through name/value property map to GWT app so it can initialize itself appropriately -->
<%= text(GWTView.PROPERTIES_OBJECT_NAME) %> = {<%
    String comma ="\n\t";
    for (Map.Entry<String, String> entry : bean.getProperties().entrySet())
    {
        %><%=text(comma)%><%=PageFlowUtil.jsString(entry.getKey())%>:<%= PageFlowUtil.jsString(entry.getValue()) %><%
        comma=",\n\t";
    }
%>}
</script>
