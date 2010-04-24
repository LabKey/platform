<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.search.SearchController" %>
<%@ page import="org.labkey.search.model.AbstractSearchService" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Formats" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.search.model.DavCrawler" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<SearchController.AdminForm> me = (JspView<SearchController.AdminForm>) HttpView.currentView();
User user = me.getViewContext().getUser();
SearchController.AdminForm form = me.getModelBean();
SearchService ss = ServiceRegistry.get().getService(SearchService.class);

%><labkey:errors /><%
    if (!StringUtils.isEmpty(form.getMessage()))
    { %>
        <p><table><tr><td><span style="color:green;"><%=form.getMessage()%></span></td></tr></table></p><%
    }

if (null == ss)
{
    %>Indexing service is not configured.<%
}
else
{
    %><p><form method="POST" action="admin.view">
        <table>
            <tr>
                <td>Path to main index:</td>
                <td><input name="mainIndexPath" size="80" value="<%=h(ss.getIndexPath())%>"></td>
            </tr>
            <tr><td colspan="2" width="500"><input type="hidden" name="path" value="1"><%=generateSubmitButton("Set Path")%></td></tr>
        </table>
    </form></p>

    <p><form method="POST" action="admin.view">
        <table><%

    if (ss.isRunning())
    {
        %><tr><td>The document crawler is running.</td></tr><%

        if (user.isAdministrator())
        {
        %>
        <tr><td><input type="hidden" name="pause" value="1"></td></tr>
        <tr><td><%=PageFlowUtil.generateSubmitButton("Pause Crawler")%></td></tr><%
        }
    }
    else
    {
        %><tr><td>The document crawler is paused.</td></tr><%

        if (user.isAdministrator())
        {
        %>
        <tr><td><input type="hidden" name="start" value="1"></td></tr>
        <tr><td><%=PageFlowUtil.generateSubmitButton("Start Crawler")%></td></tr><%
        }
    }
    %>
        </table>
    </form></p><%
    if (user.isAdministrator())
    {
    %>
    <p><form method="POST" action="admin.view">
        <table>
            <tr><td>Deleting the search index isn't usually necessary.  Note that re-indexing can be very expensive.</td></tr>
            <tr><td><input type="hidden" name="delete" value="1"></td></tr>
            <tr><td><%=PageFlowUtil.generateSubmitButton("Delete Index")%></td></tr>
        </table>
    </form></p><%
    }
}
%>