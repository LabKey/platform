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
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.action.ReturnUrlForm" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.portal.ProjectUrls" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    Portal.AddWebParts bean = (Portal.AddWebParts)HttpView.currentModel();
%>
<table width="100%">
<tr>
    <td align="left">
		<form action="<%=PageFlowUtil.urlProvider(ProjectUrls.class).getAddWebPartURL(getViewContext().getContainer())%>">
		<table><tr><td>
		<input type="hidden" name="pageId" value="<%=bean.pageId%>"/>
		<input type="hidden" name="location" value="<%=bean.location%>"/>
        <input type="hidden" name="<%=ReturnUrlForm.Params.returnUrl%>" value="<%=h(getViewContext().getActionURL())%>">
        <select name="name">
            <option value="">&lt;Select Part&gt;</option>
<%          for ( String name : bean.webPartNames)
            {
                %><option value="<%=h(name)%>"><%=h(name)%></option> <%
            } %>
        </select>
        </td><td>
        <%=PageFlowUtil.generateSubmitButton("Add Web Part")%>    
        </td></tr></table>
       </form>
    </td>
<% if (bean.rightWebPartNames != null && !bean.rightWebPartNames.isEmpty())
    { %>
    <td align="right">
        <form action="<%=PageFlowUtil.urlProvider(ProjectUrls.class).getAddWebPartURL(getViewContext().getContainer())%>">
        <table><tr><td>
        <input type="hidden" name="pageId" value="<%=bean.pageId%>"/>
        <input type="hidden"name="location"value="right"/>
        <input type="hidden" name="<%=ReturnUrlForm.Params.returnUrl%>" value="<%=h(getViewContext().getActionURL())%>">
        <select name="name">
            <option value="">&lt;Select Part&gt;</option>
<%          for (String name : bean.rightWebPartNames)
            {
                %><option value="<%=h(name)%>"><%=h(name)%></option> <%
            } %>
        </select>
        </td><td>
            <%=PageFlowUtil.generateSubmitButton("Add Web Part")%>    
        </td></tr></table>
        </form>
    </td>
<%  } %>
</tr>
</table>
