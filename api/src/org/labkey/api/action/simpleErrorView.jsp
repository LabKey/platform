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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<link href="<%= request.getContextPath() %>/stylesheet.css" type="text/css" rel="stylesheet"/>
<%=formatMissedErrors("form")%><br><br>
<%=PageFlowUtil.generateSubmitButton("Back", "window.history.back(); return false;")%>
<%=PageFlowUtil.generateButton("Home", AppProps.getInstance().getHomePageActionURL())%>
