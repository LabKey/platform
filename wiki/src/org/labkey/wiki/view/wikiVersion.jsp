<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.MenuButton"%>
<%@ page import="org.labkey.api.data.RenderContext" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.UserManager" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.wiki.WikiController.VersionBean" %>
<%@ page import="org.labkey.wiki.WikiSelectManager" %>
<%@ page import="org.labkey.wiki.model.WikiVersion" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<VersionBean> me = (JspView<VersionBean>) HttpView.currentView();
    VersionBean bean = me.getModelBean();
    User user = getUser();
    Container c = getContainer();
%>
<!--wiki-->
<table width="100%">
    <tr><td align=left colspan="2">
<%
if (!bean.hasReadPermission)
{
    if (user.isGuest())
    {
        %>Please log in to see this data.<%
    }
    else
    {
        %>You do not have permission to see this data.<%
    }%>
    </td></tr></table>

<%}
else
{
    String formattedHtml = (null != bean.wikiVersion ? bean.wikiVersion.getHtml(c, bean.wiki) : null);
    if (null == formattedHtml)
    {
        %>This page has no content.<%
    }
    else
    {
        out.print(text(formattedHtml));

        %><br><br></td></tr> <%

        int nCurVersion = bean.wikiVersion.getVersion();
        int nLatestVersion = bean.wiki.getLatestVersion().getVersion();
        boolean fOutputMakeCurrent = bean.hasSetCurVersionPermission && (nCurVersion != nLatestVersion);

        if (fOutputMakeCurrent)
        {
            %><tr><td align=right colspan="2"><labkey:form method="POST" action="<%=h(bean.makeCurrentURL)%>">
                <%= button("Make Current").submit(true) %></labkey:form></td></tr><%
        }%>

       <tr><td colspan=2 class="labkey-title-area-line"></td></tr>
       <tr>
           <td align=left>
               <i>created by:</i> <%=h(bean.createdBy)%><br>
               <i>date:</i> <%=formatDateTime(bean.created)%><br>
           </td>
           <td align=right>
               <%=textLink("page", bean.pageURL)%>&nbsp;<%=textLink("history", bean.versionsURL)%>&nbsp;<%=textLink("source", bean.sourceURL)%>&nbsp;<%

        WikiVersion[] versions = WikiSelectManager.getAllVersions(bean.wiki);

        if (versions.length > 1)
        {
            MenuButton compare = new MenuButton("Compare With...");
            String baseCompareLink = bean.compareLink + "&version1=" + nCurVersion;

            for (int i = versions.length - 1; i >= 0; i--)
            {
                WikiVersion v = versions[i];
                int n = v.getVersion();
                User author = UserManager.getUser(v.getCreatedBy());

                if (n != bean.wikiVersion.getVersion())
                {
                    // Show the author who created the version if available, or skip if that user's been deleted
                    compare.addMenuItem((n == nLatestVersion ? "Latest Version" : "Version " + n) + (author == null ? "" : " (" + author.getDisplayName(user) + ")"), baseCompareLink + "&version2=" + n);
                }
            }

            compare.render(new RenderContext(getViewContext()), out);
        }

        out.print(text("[versions:"));

        for (WikiVersion v : versions)
        {
            int n = v.getVersion();
            if (n == bean.wikiVersion.getVersion())
            {%>
                <b><%=n%></b><%
            }
            else
            {%>
                <a href="<%=h(bean.versionLink + "&version=" + n)%>"><%=n%></a><%
            }
        }
        out.print(text("]"));
    }%>
    </td></tr></table>
    <%
}%>
<!--/wiki-->
