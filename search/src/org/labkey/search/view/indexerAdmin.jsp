<%
/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.search.SearchController" %>
<%@ page import="org.labkey.search.model.SearchPropertyManager" %>
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
        <table><tr><td><span style="color:green;"><br><%=h(form.getMessage())%><br></span></td></tr></table><%
    }

if (null == ss)
{
    %>Indexing service is not configured.<%
}
else
{
    %><p><form method="POST" action="<%=h(buildURL(SearchController.AdminAction.class))%>">
        <table>
            <tr>
                <td colspan="2">Index Format: <%=h(ss.getIndexFormatDescription())%></td>
            </tr>
            <tr>
                <td>Path to primary full-text search index:</td>
                <td><input name="primaryIndexPath" size="80" value="<%=h(SearchPropertyManager.getPrimaryIndexDirectory().getPath())%>"></td>
            </tr>
            <tr><td colspan="2">Note: Changing the primary index path requires re-indexing all data, which can be very expensive.</td></tr>
            <tr><td><input type="hidden" name="path" value="1"></td></tr>
            <tr><td colspan="2" width="500"><%=generateSubmitButton("Set Path")%></td></tr>
        </table>
    </form></p>

    <p><form method="POST" action="<%=h(buildURL(SearchController.AdminAction.class))%>">
        <table><%

    if (ss.isRunning())
    {
        %><tr><td>The document crawler is running.</td></tr><%

        if (user.isAdministrator())
        {
        %>
        <tr><td><input type="hidden" name="pause" value="1"></td></tr>
        <tr><td><%=generateSubmitButton("Pause Crawler")%></td></tr><%
        }
    }
    else
    {
        %><tr><td>The document crawler is paused.</td></tr><%

        if (user.isAdministrator())
        {
        %>
        <tr><td><input type="hidden" name="start" value="1"></td></tr>
        <tr><td><%=generateSubmitButton("Start Crawler")%></td></tr><%
        }
    }
    %>
        </table>
    </form></p><%
    if (user.isAdministrator())
    {
    %>
    <p><form method="POST" action="<%=h(buildURL(SearchController.AdminAction.class))%>">
        <table>
            <tr><td>Deleting the search index isn't usually necessary; it causes re-indexing of all data, which can be very expensive.</td></tr>
            <tr><td><input type="hidden" name="delete" value="1"></td></tr>
            <tr><td><%=generateSubmitButton("Delete Index")%></td></tr>
        </table>
    </form></p><%
    }
}
%>