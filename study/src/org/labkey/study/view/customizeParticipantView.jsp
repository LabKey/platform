<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.module.ModuleHtmlView"%>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.reports.report.ReportUrls" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.StudyModule" %>
<%@ page import="org.labkey.study.controllers.StudyController.CustomizeParticipantViewAction" %>
<%@ page import="org.labkey.study.controllers.StudyController.CustomizeParticipantViewForm" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<CustomizeParticipantViewForm> me = (JspView<CustomizeParticipantViewForm>) HttpView.currentView();
    CustomizeParticipantViewForm bean = me.getModelBean();
    boolean useCustomView = bean.isUseCustomView();
    String subjectNoun = StudyService.get().getSubjectNounSingular(getContainer());
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    window.onbeforeunload = LABKEY.beforeunload();

    var DEFAULT_SCRIPT_VALUE = <%= q(bean.getDefaultScript()) %>;

    function setCustomScriptState(disabled)
    {
        document.getElementById('customScript').disabled = disabled;
        return true;
    }
</script>
<labkey:form action="<%=urlFor(CustomizeParticipantViewAction.class)%>" name="editorForm" method="POST">
    <%=generateReturnUrlFormField(bean)%>
    <input type="hidden" name="reshow" value="false">
    <input type="hidden" name="participantId" value="<%= h(bean.getParticipantId()) %>">
    <table>
        <tr class="labkey-wp-header">
            <th><%= h(subjectNoun) %> View Contents</th>
        </tr>
<%
    if (bean.isEditable())
    {
%>
        <tr>
            <td>
                <% addHandler("radioUseCustomViewFalse", "change", "return setCustomScriptState(this.checked);"); %>
                <input id="radioUseCustomViewFalse" type="radio" name="useCustomView" value="false"<%=checked(!useCustomView)%>>
                    Use standard <%= h(subjectNoun.toLowerCase()) %> view<br>
            </td>
        </tr>
        <tr>
            <td>
                <% addHandler("radioUseCustomViewTrue", "change", "return setCustomScriptState(!this.checked);"); %>
                <input id="radioUseCustomViewTrue" type="radio" name="useCustomView" value="true"<%=checked(useCustomView)%>>
                    Use customized <%= h(subjectNoun.toLowerCase()) %> view<br>
            </td>
        </tr>
        <tr>
            <td>
                <% addHandler("customScript", "change", "LABKEY.setDirty(true); return true;"); %>
                <textarea rows="30" cols="150" name="customScript" id="customScript" style="width:100%; font-family: monospace;"<%=disabled(!useCustomView)%>><%= h(bean.getCustomScript() == null ? bean.getDefaultScript() : bean.getCustomScript())%>
                </textarea><br>
            </td>
        </tr>
        <tr>
            <td>
                <%= button("Save").submit(true).onClick("document.forms['editorForm'].customScript.disabled = false; LABKEY.setSubmit(true); document.forms['editorForm'].reshow.value = true; return true;") %>
                <%= button("Save and Finish").submit(true).onClick("document.forms['editorForm'].customScript.disabled = false; LABKEY.setSubmit(true); return true;") %>
                <%= unsafe(bean.getReturnUrl() != null && !bean.getReturnUrl().isEmpty() ?
                        button("Cancel").href(bean.getReturnUrl()).toString() :
                        button("Cancel").href(urlProvider(ReportUrls.class).urlManageViews(getContainer())).toString() ) %>
                <%= button("Restore default script").submit(true).onClick("if (confirm('Restore default script?  You will lose any changes made to this page.')) document.getElementById('customScript').value = DEFAULT_SCRIPT_VALUE; return false;") %>
            </td>
        </tr>
<%
    }
    else
    {
%>
        <tr>
            <td>
                This custom participant view is defined in an active module. It cannot be edited via this interface.
            </td>
        </tr>
        <tr>
            <td>
                <textarea rows="30" cols="150" style="width:100%" disabled><%= h(bean.getCustomScript()) %></textarea><br>
            </td>
        </tr>
<%
    }
%>
    </table>
</labkey:form>
<%
    if (bean.getParticipantId() != null)
    {
        ModuleHtmlView view = new ModuleHtmlView(ModuleLoader.getInstance().getModule(StudyModule.MODULE_NAME), "Custom Participant View", unsafe(useCustomView ? bean.getCustomScript() : bean.getDefaultScript()));
%>
<table width="100%">
    <tr class="labkey-wp-header">
        <th><%= h(subjectNoun) %> View Preview <%= h(bean.isEditable() ? "(Save to refresh)" : "") %></th>
    </tr>
    <tr>
        <td>
            <%= view.getHtml() %>
        </td>
    </tr>
</table>
<%
    }
%>