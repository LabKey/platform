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
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.view.GWTView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%
    HttpView me = HttpView.currentView();
    GWTView.GWTViewBean bean = (GWTView.GWTViewBean) me.getModelBean();
%>
<div id="<%= h(bean.getModuleName()) %>-Root" class="<%=h(bean.getLoadingStyleName())%>"></div>
<%
String jsPath = bean.getModuleName() + "/" + bean.getModuleName() + ".nocache.js";
%>
<script id="__gwt_marker_<%=h(bean.getModuleName())%>"></script>
<%=getScriptTag(jsPath)%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    <!-- Pass through name/value property map to GWT app so it can initialize itself appropriately -->
<%=unsafe(GWTView.PROPERTIES_OBJECT_NAME)%> = <%=json(new JSONObject(bean.getProperties()), 3)%>;
</script>
