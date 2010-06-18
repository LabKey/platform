<%
/*
 * Copyright (c) 2007-2010 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    HttpView me = HttpView.currentView();
    GWTView.GWTViewBean bean = (GWTView.GWTViewBean) me.getModelBean();
%>
<%--<textarea id="gwtDebug" cols="200" rows=20 style="overflow:auto;"></textarea>--%>
<div id="<%= PageFlowUtil.filter(bean.getModuleName()) %>-Root" class="extContainer <%=h(bean.getLoadingStyleName())%>"></div>
<%
String contextPath = request.getContextPath();
String jsPath = bean.getModuleName() + "/" + bean.getModuleName() + ".nocache.js";

%>
<%-- jgarms: The combination of yahoo ui and gwt javascript causes GWT to fail on IE.
  As a horrible, horrible hack, we insert a special pair of elements here that
  the GWT platform detection code uses to find its base directory.

  The ant build will insert an empty file "fake.js" into the directory of the
  GWT app.
  --%>
<script type="text/javascript" src="<%=contextPath%>/<%=bean.getModuleName()%>/fake.js"></script>
<script id="__gwt_marker_<%=bean.getModuleName()%>"></script>
<script type="text/javascript" src="<%=contextPath%>/<%=jsPath%>?<%=PageFlowUtil.getServerSessionHash()%>"></script>
<script type="text/javascript">
    <!-- Pass through name/value property map to GWT app so it can initialize itself appropriately -->
<%= GWTView.PROPERTIES_OBJECT_NAME %> = {<%
    String comma ="\n\t";
    for (Map.Entry<String, String> entry : bean.getProperties().entrySet())
    {
        %><%=comma%><%=PageFlowUtil.jsString(entry.getKey())%>:<%= PageFlowUtil.jsString(entry.getValue()) %><%
        comma=",\n\t";
    }
%>};
</script>
