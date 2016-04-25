
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
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
<%!
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
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
            : "A " + subjectNounSingular.toLowerCase() + " group has been sent";
    String messageBody = bean.getMessageBody() != null ? bean.getMessageBody()
            :  getUser().getDisplayName(getUser()) + " has sent the following " + subjectNounSingular.toLowerCase()
                + " group to you: \"" + bean.getLabel() + "\".\n\nNote: if they have different permissions levels from you,"
                + " you may see a different set of " + subjectNounPlural.toLowerCase() + "."
                + "\n\nClick the link below to view the sent " + subjectNounSingular.toLowerCase() + " group: ";
%>

<labkey:errors/>

<p>Send a copy of your <%=h(subjectNounSingular.toLowerCase())%> group to another user to save and edit on their own.<p>
<p>Note: if they have different permissions levels from you, they may see a different set of <%=h(subjectNounPlural.toLowerCase())%>.
    <br/>Additionally, any further modifications to this saved group will show up in the send group.</p>

<labkey:form action="<%=h(urlFor(SendParticipantGroupAction.class))%>" method="POST">

    <b>Recipients (one per line):</b>
    <labkey:autoCompleteTextArea
        name="recipientList" id="recipientList" rows="6" cols="35"
        url="<%=h(completionUrl)%>" value="<%=h(bean.getRecipientList())%>"
    />
    <br/>

    <b>Message Subject:</b><br/>
    <input type="text" name="messageSubject" id="messageSubject" size="93" value="<%=h(messageSubject)%>"/>
    <br/><br/>

    <b>Message Body:</b><br/>
    <textarea name="messageBody" id="messageBody" rows="8" cols="94"><%=h(messageBody)%></textarea>
    <br/><br/>

    <b>Message link:</b>
    <div><%=h(sendGroupUrl.getBaseServerURI() + PageFlowUtil.decode(sendGroupUrl.toString()))%></div>
    <div><a class="labkey-text-link" target="_blank" href="<%=h(sendGroupUrl)%>">Preview Link</a></div>
    <br/>

    <input type="hidden" name="rowId" value="<%=h(bean.getRowId())%>">
    <input type="hidden" name="returnUrl" value="<%=h(bean.getReturnUrl())%>">
    <%= button("Submit").submit(true) %>
    <%= button("Cancel").href(returnUrl) %>
</labkey:form>