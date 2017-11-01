<%
/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.study.controllers.StudyController.SendParticipantGroupForm" %>
<%@ page import="org.labkey.study.controllers.StudyController.SendParticipantGroupAction" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SendParticipantGroupForm> me = (JspView<SendParticipantGroupForm>) HttpView.currentView();
    SendParticipantGroupForm bean = me.getModelBean();

    Container container = getContainer();
    Study s = StudyManager.getInstance().getStudy(container);
    String subjectNounSingular = s.getSubjectNounSingular();
    String subjectNounPlural = s.getSubjectNounPlural();

    ActionURL returnUrl = bean.getReturnActionURL(bean.getDefaultUrl(container));
    String completionUrl = urlProvider(SecurityUrls.class).getCompleteUserReadURLPrefix(container);
    ActionURL sendGroupUrl = bean.getSendGroupUrl(container);

    String messageSubject = bean.getMessageSubject() != null ? bean.getMessageSubject()
            : "A " + subjectNounSingular.toLowerCase() + " group has been sent: " + bean.getLabel();
    String messageBody = bean.getMessageBody() != null ? bean.getMessageBody()
            :  getUser().getDisplayName(getUser()) + " has sent the following " + subjectNounSingular.toLowerCase()
                + " group to you: \"" + bean.getLabel() + "\".\n\nNote: if the sender has different permissions levels than you,"
                + " you may see a different set of " + subjectNounPlural.toLowerCase() + "."
                + "\n\nClick the link below to view the sent " + subjectNounSingular.toLowerCase() + " group: ";
%>

<p>Send a copy of your <%=h(subjectNounSingular.toLowerCase())%> group to one or more users to save and edit on their own.<p>
<p>Note: if the other user has different permissions levels than you, that user may see a different set of <%=h(subjectNounPlural.toLowerCase())%>.
    <br/>Additionally, any further modifications to this saved group will be reflected in the sent group.</p>

<labkey:errors/>
<labkey:form action="<%=h(urlFor(SendParticipantGroupAction.class))%>" method="POST" layout="horizontal" >

    <b>Recipients (one per line):</b>
    <labkey:autoCompleteTextArea
        name="recipientList" id="recipientList" rows="6" cols="95"
        url="<%=h(completionUrl)%>" value="<%=h(bean.getRecipientList())%>"
    />
    <br/>

    <b>Message Subject:</b><br/>
    <labkey:input type="text" name="messageSubject" id="messageSubject" size="95" value="<%=h(messageSubject)%>"/>
    <br/>

    <b>Message Body:</b><br/>
    <textarea name="messageBody" id="messageBody" rows="8" cols="97"><%=h(messageBody)%></textarea>
    <br/><br/>

    <b>Message link:</b>
    <div><%=h(sendGroupUrl.getBaseServerURI() + PageFlowUtil.decode(sendGroupUrl.toString()))%></div>
    <div><a class="labkey-text-link" target="_blank" href="<%=h(sendGroupUrl)%>">Preview Link</a></div>
    <br/>

    <input type="hidden" name="rowId" value="<%=h(bean.getRowId())%>">
    <input type="hidden" name="label" value="<%=h(bean.getLabel())%>">
    <input type="hidden" name="returnUrl" value="<%=h(bean.getReturnUrl())%>">
    
    <%= button("Submit").submit(true) %>
    <%= button("Cancel").href(returnUrl) %>
</labkey:form>