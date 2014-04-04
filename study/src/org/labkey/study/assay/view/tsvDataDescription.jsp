<%
/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ColumnRenderProperties"%>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.study.actions.AssayRunUploadForm" %>
<%@ page import="org.labkey.api.study.actions.TemplateAction" %>
<%@ page import="org.labkey.api.study.assay.AssayProvider" %>
<%@ page import="org.labkey.api.study.assay.AssayUrls" %>
<%@ page import="org.labkey.api.study.assay.PipelineDataCollector" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<AssayRunUploadForm> me = (JspView<AssayRunUploadForm>) HttpView.currentView();
    AssayRunUploadForm<AssayProvider> bean = me.getModelBean();
%>
<div id="showExpectedDataFieldsDiv"><%= textLink("Show Expected Data Fields", (URLHelper)null, "document.getElementById('expectedDataFields').style.display = 'block'; document.getElementById('showExpectedDataFieldsDiv').style.display = 'none'; return false;", "showExpectedDataFieldsLink") %></div>
<div id="expectedDataFields" style="display: none">
    <strong>Expected Data Fields</strong>
    <table class="labkey-show-borders" cellpadding="3" cellspacing="0">
        <tr>
            <td><strong>Name</strong></td>
            <td><strong>Type</strong></td>
            <td><strong>Required</strong></td>
            <td><strong>Description</strong></td>
        </tr>
    <%
    for (DomainProperty pd : bean.getRunDataProperties()) { %>
    <tr>
        <td><%= h(pd.getName()) %></td>
        <td><%= h(ColumnRenderProperties.getFriendlyTypeName(pd.getPropertyDescriptor().getPropertyType().getJavaType())) %></td>
        <td><%= text(pd.isRequired() ? "yes" : "no") %></td>
        <td><%=h(pd.getDescription())%></td></tr>
    <% } %>
    </table>
</div>
<% if (PipelineDataCollector.getFileQueue(bean).isEmpty())
{ %>
    <%= textLink("download spreadsheet template",
        urlProvider(AssayUrls.class).getProtocolURL(bean.getContainer(), bean.getProtocol(), TemplateAction.class))%>
    <br>After downloading and editing the spreadsheet template, paste it into the text area below or save the spreadsheet and upload it as a file.
<% }%>
<p></p>
