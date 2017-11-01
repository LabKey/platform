<%
/*
 * Copyright (c) 2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.reports.Report" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.query.reports.ReportsController.ShareReportAction" %>
<%@ page import="org.labkey.query.reports.ReportsController.ShareReportForm" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.reports.report.ReportDescriptor" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ShareReportForm> me = (JspView<ShareReportForm>) HttpView.currentView();
    ShareReportForm bean = me.getModelBean();

    Report report = null;
    if (null != bean.getReportId())
        report = bean.getReportId().getReport(getViewContext());            
%>
<%
    if (null == report || !report.allowShareButton(getUser(), getContainer()))
    {
%>
        <div class="labkey-error">Invalid report identifier, unable to view share report options.</div>
<%
    }
    else
    {
        Container container = getContainer();
        String reportName = report.getDescriptor().getResourceName();
        ActionURL returnUrl = bean.getReturnActionURL(bean.getDefaultUrl(container));
        String completionUrl = urlProvider(SecurityUrls.class).getCompleteUserReadURLPrefix(container);
        ActionURL reportUrl = report.getRunReportURL(getViewContext());

        String messageSubject = bean.getMessageSubject() != null ? bean.getMessageSubject()
            : "A report has been sent: " + reportName;
        String messageBody = bean.getMessageBody() != null ? bean.getMessageBody()
            :  getUser().getDisplayName(getUser()) + " has sent the following report to you: \"" + reportName + "\"."
                + "\n\nNote: if the sender has different permissions levels than you, you may see a different set of data."
                + "\n\nClick the link below to view the sent report: ";
%>
        <h4><%=h(report.getDescriptor().getReportName())%></h4>
        <p>Send a copy of your report to one or more users to save and edit on their own.<p>
        <p>Note: if the recipient user has a different permissions level than you for the source data/container, that user may see a different set of data.
            <br/>Additionally, any further modifications to this report will be reflected in the link as viewed by the recipient.</p>

        <labkey:errors/>
        <labkey:form action="<%=h(urlFor(ShareReportAction.class))%>" method="POST" layout="horizontal">
            <b>Recipients (one per line):</b>
            <labkey:autoCompleteTextArea
                name="recipientList" id="recipientList" rows="6" cols="95"
                url="<%=h(completionUrl)%>" value="<%=h(bean.getRecipientList())%>"
            />
            <br/>

            <b>Message Subject:</b><br/>
            <labkey:input type="text" name="messageSubject" id="messageSubject" size="95" value="<%=h(messageSubject)%>" />
            <br/>

            <b>Message Body:</b><br/>
            <textarea name="messageBody" id="messageBody" rows="8" cols="97"><%=h(messageBody)%></textarea>
            <br/><br/>

            <b>Message link:</b>
            <div><%=h(reportUrl.getBaseServerURI() + PageFlowUtil.decode(reportUrl.toString()))%></div>
            <div><a class="labkey-text-link" target="_blank" href="<%=h(reportUrl)%>">Preview Link</a></div>
            <br/>

            <input type="hidden" name="<%=h(ReportDescriptor.Prop.reportId)%>" value="<%=h(bean.getReportId())%>">
            <input type="hidden" name="returnUrl" value="<%=h(bean.getReturnUrl())%>">
            <%= button("Submit").submit(true) %>
            <%= button("Cancel").href(returnUrl) %>
        </labkey:form>
<%
    }
%>