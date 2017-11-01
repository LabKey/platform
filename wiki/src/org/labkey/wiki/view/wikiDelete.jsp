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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Wiki> me = (JspView<Wiki>) HttpView.currentView();
    Wiki wiki = me.getModelBean();
%>

Are you sure you want to delete this page?
<p/>
<b>name: <%=h(wiki.getName())%></b><br/>
<b>title: <%=h(wiki.getLatestVersion().getTitle())%></b><br/>
<br/><labkey:checkbox id="isDeletingSubtree" name="isDeletingSubtree" value="true" checked="false"/> Delete Entire Wiki Subtree
