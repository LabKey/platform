<%
/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.core.attachment.AttachmentServiceImpl" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    AttachmentServiceImpl.ConfirmDeleteView me = (AttachmentServiceImpl.ConfirmDeleteView) HttpView.currentView();
%>
<table>
  <tr>
    <td>Delete <%=me.name%>?</td>
  </tr>
  <tr>
    <td>
      <a href="<%=h(me.deleteURL)%>"><img src="<%=PageFlowUtil.buttonSrc("OK")%>"></a>
      <a href="#cancel"><img onClick="window.close()" src="<%=PageFlowUtil.buttonSrc("Cancel")%>"></a>
    </td>
  </tr>
</table>
