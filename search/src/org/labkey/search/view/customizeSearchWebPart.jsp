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
<%@ page import="org.labkey.api.action.SpringActionController"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.search.view.SearchWebPartFactory" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>)HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    boolean includeSubfolders = SearchWebPartFactory.includeSubfolders(webPart);
%>
<labkey:form name="frmCustomize" method="post">
<table>
  <tr>
    <td><input type="hidden" name="<%=SpringActionController.FIELD_MARKER%>includeSubfolders"><input type="checkbox" name="includeSubfolders" value="1"<%=checked(includeSubfolders)%>></td>
    <td>Search subfolders</td>
  </tr>
  <tr>
    <td colspan=2>
      <%= button("Submit").submit(true) %>
      <%= button("Cancel").href(getContainer().getStartURL(getUser())) %>
    </td>
  </tr>
</table>
</labkey:form>
