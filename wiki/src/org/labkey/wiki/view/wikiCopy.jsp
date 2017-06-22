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
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<WikiController.CopyBean> me = (JspView<WikiController.CopyBean>) HttpView.currentView();
    WikiController.CopyBean bean = me.getModelBean();
%>
<labkey:form name="copy" action="<%=h(buildURL(WikiController.CopyWikiAction.class))%>" method="POST">

<input type="hidden" name="sourceContainer" value="<%=h(bean.sourceContainer)%>">
<input type="hidden" name="destContainer" value="<%=h(bean.destContainer)%>">

<table class="labkey-data-region">
<tr><td style="padding-left:0">Select a destination folder. Click the Copy Pages button to copy all wiki pages in this folder to the destination folder.<br>
Note that only the latest version of each wiki page is copied.    
</td></tr>
<tr><td>&nbsp;</td></tr>
<%=bean.folderList%>
</table><br>

<table>
    <tr>
        <td><labkey:checkbox id="isCopyingHistory" name="isCopyingHistory" value="true" checked="false"/> Copy Histories Also</td>
    </tr>
    <tr><td><br></td></tr>
</table>
<table>
    <tr>
        <td><%= button("Copy Pages").submit(true) %></td>
        <td><%= button("Cancel").href(bean.cancelURL) %></td>
    </tr>
</table>
</labkey:form>
