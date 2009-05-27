<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.wiki.model.Wiki" %>
<%@ page import="javax.servlet.http.HttpServletResponse" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<!--wiki-->
<%
    HttpView me = HttpView.currentView();
    ViewContext context = me.getViewContext();
    User user = context.getUser();
    Wiki wiki = (Wiki) context.get("wiki");
    String formattedHtml = (String) context.get("formattedHtml");
    Container c = (wiki != null && wiki.getContainerId() != null) ? ContainerManager.getForId(wiki.getContainerId()) : context.getContainer();
    ActionURL printBranchUrl = new ActionURL(WikiController.PrintBranchAction.class, c);
    printBranchUrl.addParameter("name", wiki.getName());
    if(null == c)
    {
        %><p><span class="labkey-error">This wiki page has an invalid parent container. Please delete this page.</span></p><%
        return;
    }
    boolean hasContent = (Boolean) context.get("hasContent");
    boolean includeLinks = (Boolean)context.get("includeLinks");
    boolean isEmbedded = (Boolean)context.get("isEmbedded");
    int wikiPageCount = ((Integer)context.get("wikiPageCount")).intValue();

if (!c.hasPermission(user, ACL.PERM_READ))
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

//if page has content, write out commands
if (hasContent && includeLinks)
{
    %><table class="labkey-wp-link-panel"><tr><td align="right"><%

    if (null != context.get("updateContentLink"))
    {
        %>[<a href="<%=PageFlowUtil.filter(context.get("updateContentLink"))%>">edit</a>]<%
    }

    //user must have update perms
    if (null != context.get("manageLink"))
    {
        %>&nbsp;[<a href="<%=PageFlowUtil.filter(context.get("manageLink"))%>">manage</a>]<%
    }

    //user must have update perms
    if (null != context.get("versionsLink"))
    {
        %>&nbsp;[<a href="<%=PageFlowUtil.filter(context.get("versionsLink"))%>">history</a>]<%
    }

    if (null != context.get("printLink"))
    {
        %>&nbsp;[<a target="_blank" href="<%= PageFlowUtil.filter(context.get("printLink")) %>">print</a>]<%
    }

    if (null != context.get("printLink") && null != wiki.getChildren() && wiki.getChildren().size() > 0)
    {
        %>&nbsp;[<a target="_blank" href="<%= PageFlowUtil.filter(printBranchUrl.getLocalURIString()) %>">print branch</a>]<%
    }
    %>
    </td></tr></table><%
}



//if page has no content, write out message and add content command
if (!hasContent)
{
    //if this is a web part and user has update perms
    if (Boolean.TRUE.equals(context.get("isInWebPart")))
    {%>
        <%
        if (wikiPageCount == 0 && Boolean.TRUE.equals(context.get("hasInsertPermission")) && !isEmbedded)
        {%>
            The Wiki web part displays a single wiki page.
            This folder does not currently contain any wiki pages to display. You can:<br>
            <ul>
                <li><a href="<%=PageFlowUtil.filter(context.get("insertLink"))%>">Create a new wiki page</a> to display in this web part.</li>
            </ul>
        <%}
        else if (wikiPageCount > 0 && Boolean.TRUE.equals(context.get("hasAdminPermission")) && !isEmbedded)
        {%>
            The Wiki web part displays a single wiki page.
            Currently there is no page selected to display. You can:<br>
            <ul>
                <li><a href="<%=PageFlowUtil.filter(context.get("customizeLink"))%>">Choose an existing page to display</a> from this project or a different project.</li>
                <li><a href="<%=PageFlowUtil.filter(context.get("insertLink"))%>">Create a new wiki page</a> to display in this web part.</li>
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

        if (wikiPageCount == 0 && null != context.get("insertLink"))
        {%>
            This Wiki currently does not contain any pages.<br><br>
            [<a href="<%=PageFlowUtil.filter(context.get("insertLink"))%>">add a new page</a>]
        <%}
        else
        {
            %>
            This page has no content.<br><br>
            <%

            if (null != context.get("insertLink"))
            {%>
                [<a href="<%=PageFlowUtil.filter(context.get("insertLink"))%>">add content</a>]
            <%}
        }
    }
}
else
{
    out.print(formattedHtml);

    if (null != wiki.getAttachments() && wiki.getAttachments().size() > 0 && wiki.isShowAttachments())
    {
        %><p/>
            <table style="width:100%" cellspacing="0">
                <tr>
                    <td style="border-bottom: 1px solid #89A1B4; width:48%">&nbsp;</td>
                    <td rowspan="2" style="font-style:italic; width:2%;white-space:nowrap; color: #89A1B4;">Files attached to this page</td>
                    <td style="border-bottom: 1px solid #89A1B4; width:48%">&nbsp;</td>
                </tr>
                <tr>
                    <td>&nbsp;</td>
                    <td>&nbsp;</td>
                </tr>
            </table>
        <%

        for (Attachment a : wiki.getAttachments())
        {
            %><a href="<%=PageFlowUtil.filter(a.getDownloadUrl("Wiki"))%>"><img src="<%=request.getContextPath()%><%=PageFlowUtil.filter(a.getFileIcon())%>">&nbsp;<%=PageFlowUtil.filter(a.getName())%></a><br><%
        }
    }
}
%>
<br>
<%
if (hasContent && null != me.getView("discussion"))
{
    me.include(me.getView("discussion"), out);
}
%>
<!--/wiki-->