<%
/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminBean" %>
<%@ page import="org.labkey.api.search.SearchService" %>
<%@ page import="org.labkey.api.security.permissions.AdminOperationsPermission" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.HtmlStringBuilder" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.search.SearchController.AdminAction" %>
<%@ page import="org.labkey.search.SearchController.AdminForm" %>
<%@ page import="org.labkey.search.model.LuceneSearchServiceImpl" %>
<%@ page import="org.labkey.search.model.SearchPropertyManager" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.io.File" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<AdminForm> me = (JspView<AdminForm>) HttpView.currentView();
AdminForm form = me.getModelBean();
SearchService ss = SearchService.get();
boolean hasAdminOpsPerms = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);
File indexDirectory = LuceneSearchServiceImpl.getIndexDirectory();
String indexDirectoryPath = null != indexDirectory ? indexDirectory.getPath() : "Invalid index directory. Path might include unknown substitution parameter(s).";
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
    HtmlString indexPathHelp = HtmlStringBuilder.of("The index path setting supports string substitution of specific server properties. For example, the value:")
        .unsafeAppend("<br><br><code>&nbsp;&nbsp;./temp/${serverGuid}/labkey_full_text_index</code><br><br>")
        .append("will currently result in this path:")
        .unsafeAppend("<br><br><code>&nbsp;&nbsp;")
        .append("./temp/" + AdminBean.serverGuid + "/labkey_full_text_index")
        .unsafeAppend("</code><br><br>")
        .append("The supported properties and their current values are listed in the table below. Note that illegal file system characters within the values are replaced with underscores.")
        .unsafeAppend("<br><br>")
        .append(AdminBean.getPropertyGridHtml(LuceneSearchServiceImpl.getEscapedSystemPropertyMap()))
        .getHtmlString();

    %><p><labkey:form method="POST" action="<%=urlFor(AdminAction.class)%>">
        <table>
            <%=getTroubleshooterWarning(hasAdminOpsPerms, HtmlString.unsafe("<br>"))%>
            <tr>
                <td>Path to full-text search index<%=helpPopup("Path to full-text search index", indexPathHelp, 350)%>:&nbsp;</td>
                <td><input name="indexPath" size="80" value="<%=h(SearchPropertyManager.getUnsubstitutedIndexDirectory())%>"></td>
            </tr>
            <tr>
                <td>Current path resolves to:</td><td><%=h(indexDirectoryPath   )%></td>
            </tr>
            <tr>
                <td colspan="2">&nbsp;</td>
            </tr><%
        if (hasAdminOpsPerms)
        {
            %><tr><td colspan="2">Note: Changing the index path requires re-indexing all data.</td></tr>
            <tr><td><input type="hidden" name="path" value="1"></td></tr>
            <tr><td colspan="2" width="500"><%= button("Set Path").submit(true) %></td></tr><%
        }
            %>
        </table>
    </labkey:form></p>

<p><table>
<tr>
    <td colspan="2">Current Index Properties:</td>
</tr>
    <%
        for (Map.Entry e : ss.getIndexFormatProperties().entrySet())
        {
    %>
    <tr>
        <td><%=h(e.getKey())%>:&nbsp;</td><td><%=h(e.getValue())%></td>
    </tr>
    <%
        }
    %>
</table>
</p>

    <p><labkey:form method="POST" action="<%=urlFor(AdminAction.class)%>">
        <table><%

    if (ss.isRunning())
    {
        %><tr><td>The document crawler is running.</td></tr><%

        if (hasAdminOpsPerms)
        {
        %>
        <tr><td><input type="hidden" name="pause" value="1"></td></tr>
        <tr><td><%= button("Pause Crawler").submit(true) %></td></tr><%
        }
    }
    else
    {
        %><tr><td>The document crawler is paused.</td></tr><%

        if (hasAdminOpsPerms)
        {
        %>
        <tr><td><input type="hidden" name="start" value="1"></td></tr>
        <tr><td><%= button("Start Crawler").submit(true) %></td></tr><%
        }
    }
    %>
        </table>
    </labkey:form></p><%
    if (hasAdminOpsPerms)
    {
    %>
    <p><labkey:form method="POST" action="<%=urlFor(AdminAction.class)%>">
        <table>
            <tr><td>Deleting the search index isn't usually necessary; it causes re-indexing of all data.</td></tr>
            <tr><td><input type="hidden" name="delete" value="1"></td></tr>
            <tr><td><%= button("Delete Index").submit(true) %></td></tr>
        </table>
    </labkey:form></p>
    <%
    }
    %>
    <p><labkey:form method="POST" action="<%=urlFor(AdminAction.class)%>">
        <table>
            <tr><td width="800">You can change the search indexing directory type below, but this is generally not recommended. Contact
                LabKey for assistance if full-text indexing or searching seems to have difficulty with the default setting.<br><br></td></tr>
            <tr><td>Directory Type:
                <select name="directoryType"><%
                    String currentDirectoryType = SearchPropertyManager.getDirectoryType();

                    for (Pair<String, String> pair : ss.getDirectoryTypes())
                    { %>
                    <option value="<%=h(pair.first)%>"<%=selected(pair.first.equals(currentDirectoryType))%>><%=h(pair.second)%></option><%
                    }
                    %>
                </select>
            </td></tr><%
            if (hasAdminOpsPerms)
            {
            %>
            <tr><td><input type="hidden" name="directory" value="1"></td></tr>
            <tr><td><%= button("Set").submit(true) %></td></tr><%
            }
            %>
        </table>
    </labkey:form></p>
    <p><labkey:form method="POST" action="<%=urlFor(AdminAction.class)%>">
        <table>
            <tr><td width="800">You can change the maximum file size limit below, but this is generally not recommended and will result in additional system memory usage. We further limit <b>xlsx</b> files at 1/5 the normal max, as they are compressed at rest.<br><br></td></tr>
            <tr><td>Indexed file size limit: <input type="number" name="fileLimitMB" value="<%=SearchPropertyManager.getFileSizeLimitMB()%>" /> MB</td></tr><%
            if (hasAdminOpsPerms)
            {
        %>
            <tr><td><input type="hidden" name="limit" value="1"></td></tr>
            <tr><td><%= button("Set").submit(true) %></td></tr><%
            }
        %>
        </table>
    </labkey:form></p><%
}
%>