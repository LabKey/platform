<%
/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page import="org.labkey.wiki.model.BaseWikiView" %>
<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<!--wiki-->
<%
    BaseWikiView view = (BaseWikiView)HttpView.currentView();
    ViewContext context = getViewContext();
    User user = getUser();
    Wiki wiki = view.wiki;
    Container c = (wiki != null && wiki.getContainerId() != null) ? ContainerManager.getForId(wiki.getContainerId()) : getContainer();

    if (null == c)
    {
        %><p><span class="labkey-error">This wiki page has an invalid parent container. Please delete this page.</span></p><%
        return;
    }

    if (!c.hasPermission(user, ReadPermission.class))
    {
        %><table width="100%"><tr><td align=left><%
        if (user.isGuest())
        {
            %>Please log in to see this data.<%
        }
        else
        {
            %>You do not have permission to see this data.<%
        }%></td></tr></table><%
        return;
    }

//if page has no content, write out message and add content command
if (!view.hasContent)
{
    //if this is a web part and user has update perms
    if (view.isWebPart())
    {
        if (view.hasAdminPermission && !view.isEmbedded())
        {%>
            The Wiki web part displays a single wiki page.
            <%=text(view.folderHasWikis ? "Currently there is no page selected to display. You can:<br>" : "This folder does not currently contain any wiki pages to display. You can:<br>")%>
            <ul>
                <li><a href="<%=h(view.customizeURL)%>">Choose an existing page to display</a> from this project or a different project.</li>
                <li><a href="<%=h(view.insertURL)%>">Create a new wiki page</a> to display in this web part.</li>
            </ul><%
        }
        else
        {
            %>This Wiki web part is not configured to display content.<%
        }
    }
    else
    {
        // No page here, so set the response code to 404
        context.getResponse().setStatus(HttpServletResponse.SC_NOT_FOUND);

        if (!view.folderHasWikis && null != view.insertURL)
        {%>
            This Wiki currently does not contain any pages.<br><br>
            <%=textLink("add a new page", view.insertURL)%>
        <%}
        else
        {
            %>
            This page has no content.<br><br>
            <%

            if (null != view.insertURL)
            {%>
                <%=textLink("add content", view.insertURL)%>
            <%}
        }
    }
}
else
{
    out.print(view.html);

    if (null != wiki.getAttachments() && wiki.getAttachments().size() > 0 && wiki.isShowAttachments())
    {
        %><p/><%
            if (null != wiki.getLatestVersion().getBody())
            {
        %>
            <table style="width:100%" cellspacing="0" class="lk-wiki-file-attachments-divider">
                <tr>
                    <td style="border-bottom: 1px solid #89A1B4; width:48%">&nbsp;</td>
                    <td rowspan="2" style="font-style:italic; width:2%;white-space:nowrap; color: #89A1B4;">Attached Files</td>
                    <td style="border-bottom: 1px solid #89A1B4; width:48%">&nbsp;</td>
                </tr>
                <tr>
                    <td>&nbsp;</td>
                    <td>&nbsp;</td>
                </tr>
            </table>
        <%
            }

        for (Attachment a : wiki.getAttachments())
        {
            ActionURL downloadURL = WikiController.getDownloadURL(getContainer(), wiki, a.getName());

        %><a href="<%=h(downloadURL)%>"><img src="<%=getContextPath()%><%=h(a.getFileIcon())%>">&nbsp;<%=h(a.getName())%></a><br><%
        }
    }
}
%>
<p/>
<%
if (view.hasContent && null != view.getView("discussion"))
{
    view.include(view.getView("discussion"), out);
}
%>
<!--/wiki-->