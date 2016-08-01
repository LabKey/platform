<%
/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
<%@ page import="org.labkey.announcements.AnnouncementsController.RssView.RssBean" %>
<%@ page import="org.labkey.announcements.model.AnnouncementModel" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<RssBean> me = (HttpView<RssBean>) HttpView.currentView();
    RssBean bean = me.getModelBean();
    LookAndFeelProperties laf = LookAndFeelProperties.getInstance(getContainer());
%>
<rss version="2.0">
<channel>
    <title><%=h(laf.getShortName())%>: <%=h(laf.getDescription())%></title>
    <link><%=h(ActionURL.getBaseServerURL())%></link>
    <description><%=h(laf.getShortName())%>: <%=h(laf.getDescription())%></description>
<%
    for (AnnouncementModel ann : bean.announcementModels)
    {%>
    <item>
        <title><%=h(ann.getTitle())%></title>
        <link><%=h(bean.url)%><%=ann.getRowId()%>&amp;_print=1</link>
        <description><%=h(ann.getBody())%></description>
        <pubDate><%=ann.getCreated()%></pubDate>
    </item><%
    }%>
</channel>
</rss>
