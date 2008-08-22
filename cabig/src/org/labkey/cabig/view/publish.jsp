<%
/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.HelpTopic"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.cabig.caBIGController" %>
<%@ page import="org.labkey.cabig.caBIGController.*" %>
<%@ page import="org.labkey.cabig.caBIGManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    ViewContext ctx = HttpView.currentContext();
    boolean isPublished = caBIGManager.get().isPublished(ctx.getContainer());
    String publishButton = PageFlowUtil.generateButton(isPublished ? "Unpublish" : "Publish", caBIGController.getCaBigURL(isPublished ? UnpublishAction.class : PublishAction.class, ctx.getContainer(), ctx.getActionURL()));
    ActionURL adminUrl = caBIGController.getCaBigURL(AdminAction.class, ctx.getContainer(), ctx.getActionURL());

    if (isPublished)
    {
%>
This folder is published to the caBIG&trade; (cancer Biomedical Informatics Grid&trade;) interface.  If your caBIG&trade;
web application is running then all experiment data in this folder is publicly visible.<br><br>
<%
    }
    else
    {
%>
This folder is not published to the caBIG&trade; (cancer Biomedical Informatics Grid&trade;) interface.  Click the button below to publish
this folder to caBIG&trade;.  If you do this then all experiment data in this folder will be publicly visible via the caBIG&trade; web application.<br><br>
<%  }  %>

<%=publishButton%>&nbsp;<%=PageFlowUtil.generateButton("Admin", adminUrl)%>
<br><br>For more information about publishing to caBIG&trade;, <a href="<%=h(new HelpTopic("cabig", HelpTopic.Area.CPAS).getHelpTopicLink())%>" target="cabig">click here</a>.

<%
    if (isPublished)
    {
%>
<br><br>If you've followed the default configuration and installed the caBIG&trade; web application (named "publish") on the same server as LabKey, then you can <a href="<%=h(ctx.getActionURL().getBaseServerURI())%>/publish/Happy.jsp">click here</a> to test it.
<%
    }
%>