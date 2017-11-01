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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page import="org.labkey.wiki.WikiSelectManager" %>
<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page import="org.labkey.wiki.model.WikiTree" %>
<%@ page import="org.labkey.wiki.model.WikiVersion" %>
<%@ page import="java.util.Date" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<WikiController.PrintRawBean> me = (JspView<WikiController.PrintRawBean>) HttpView.currentView();
    WikiController.PrintRawBean bean = me.getModelBean();
    Container c = getContainer();
%>
<div style="padding:10px;">

<table width="100%">
    <tr>
        <td align=left><h3 class="labkey-header-large">Table of Contents</h3></td>
        <td align=right><%=h(bean.displayName)%><br><%=formatDate(new Date())%></td>
    </tr>
</table>

<%
    //print each page's content with title
    for (WikiTree tree : bean.wikis)
    {
        Wiki wiki = WikiSelectManager.getWiki(c, tree.getName());
        WikiVersion version = wiki.getLatestVersion();
        %>
        <hr size=1>
        <h3><a name="<%=h(tree.getName())%>"></a><%=h(tree.getTitle())%></h3><br>
        <pre><%=h(version.getBody())%></pre><br><br>
    <%}
%>
</div>