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
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    String title = webPart.getPropertyMap().get("title");
    Container currentContainer = getViewContext().getContainer();
    List<Container> containerList = WikiController.populateWikiContainerList(getViewContext());
%>
<% // Post to current action; URL includes pageId and index parameters %>
<form name="frmCustomize" method="post">
<table>
    <tr>
        <td>
        Enter the title for the Wiki TOC web part.
        </td>
        <td>
        <input name="title" type="text" value="<%=title == null ? "Pages" : h(title) %>">
        </td>
     </tr>
    <tr>
        <td>
        Select the path to the folder containing the wiki to display.
        </td>
        <td>
        <select name="webPartContainer">
            <%
            for (Container c : containerList)
            {
                if (c.equals(currentContainer) && webPart.getPropertyMap().get("webPartContainer") == null)
                {%>
                    <option selected value="<%=c.getId()%>"><%=h(c.getPath())%></option>
                <%}
                else
                {%>
                    <option <%=c.getId().equals(webPart.getPropertyMap().get("webPartContainer")) ? "selected" : "" %> value="<%=c.getId()%>"><%=h(c.getPath())%></option>
                <%}
            }
            %>
        </select>
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