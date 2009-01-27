<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    String title = webPart.getPropertyMap().get("title");
%>
<% // Post to current action; URL includes pageId and index parameters %>
<form name="frmCustomize" method="post">
<table>
  <tr>
    <td>
      Enter title for the Issues Summary web part.
    </td>
    <td>
      <input name="title" size="25" type="text" value="<%=null == title ? "Issues Summary" : h(title) %>">
    </td>
  </tr>
  <tr>
    <td colspan=2 align="right">
      <%=PageFlowUtil.generateSubmitButton("Submit")%>
      <%=PageFlowUtil.generateButton("Cancel", "begin.view")%>
    </td>
  </tr>
</table>
</form>