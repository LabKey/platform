<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.api.security.SecurityUrls"%>
<%@ page import="org.labkey.api.util.HelpTopic"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.study.model.SecurityType" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    HttpView<StudyImpl> me = (HttpView<StudyImpl>) HttpView.currentView();
    StudyImpl study = me.getModelBean();
%>
<p>Before setting up security for your study please read the documentation on LabKey security and
    on setting up study security.</p>
<ul>
    <li><a href="<%=new HelpTopic("security", HelpTopic.Area.SERVER).getHelpTopicLink()%>" target="_blank">LabKey Security Documentation</a></li>
    <li><a href="<%=new HelpTopic("studySecurity", HelpTopic.Area.STUDY).getHelpTopicLink()%>" target="_blank">Study Security Documentation</a></li>
</ul>

<p>All users must have READ permissions on this folder to access anything in this study. You can configure
    groups and folder security here [&nbsp;<a href="<%=h(urlProvider(SecurityUrls.class).getBeginURL(getViewContext().getContainer()))%>">Folder&nbsp;Security</a>&nbsp;].</p>

<p>If you want to set permissions on individual datasets within the study, you must select one of the custom study security options below.</p>

<form action="studySecurity.post" method="post" name="studySecurityForm">
    <p>Study Security Type<%=PageFlowUtil.helpPopup("Study Security", SecurityType.getHTMLDescription(), true, 400)%>:
    <select name="securityString" onchange="document.studySecurityForm.submit();">
        <%
            for (SecurityType securityType : SecurityType.values())
            {
                String selected = (study.getSecurityType() == securityType ? "selected" : "");
                %>
                <option value="<%= securityType.name() %>" <%= selected %>><%= securityType.getLabel() %></option>
                <%
            }
        %>
    </select>
    </p>
    <%=PageFlowUtil.generateSubmitButton("Update")%>


</form>