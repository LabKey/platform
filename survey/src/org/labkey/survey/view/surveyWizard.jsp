<%
/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.survey.model.Survey" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.survey.SurveyForm" %>
<%@ page import="org.labkey.survey.SurveyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("survey");
    }
%>
<%
    JspView<SurveyForm> me = (JspView<SurveyForm>) HttpView.currentView();
    SurveyForm bean = me.getModelBean();

    Integer rowId = 0;
    Integer surveyDesignId = null;
    String responsesPk = null;
    String surveyLabel = null;
    boolean submitted = false;
    String returnURL = null;
    if (bean != null)
    {
        if (bean.getRowId() != null)
            rowId = bean.getRowId();

        surveyDesignId = bean.getSurveyDesignId();
        responsesPk = bean.getResponsesPk();
        surveyLabel = bean.getLabel();
        submitted = bean.isSubmitted();
        returnURL = bean.getReturnActionURL() != null ? bean.getReturnActionURL().getLocalURIString() : null;
    }

    Survey survey = SurveyManager.get().getSurvey(getContainer(), getUser(), rowId);
    boolean locked = survey != null && SurveyManager.get().getSurveyLockedStates().indexOf(survey.getStatus()) > -1;

    // we allow editing for 1) non-submitted surveys 2) submitted surveys (that are not locked) if the user is a project or site/app admin
    Container project = getContainer().getProject();
    boolean isAdmin = (project != null && project.hasPermission(getUser(), AdminPermission.class)) || getUser().hasRootAdminPermission();
    boolean canEdit = !locked && ((!submitted && getContainer().hasPermission(getUser(), InsertPermission.class)) || isAdmin);

    String headerRenderId = "survey-header-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String formRenderId = "survey-form-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
    String footerRenderId = "survey-footer-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>

<%
    if (getErrors("form").hasErrors())
    {
        %><%=formatMissedErrors("form")%><%
    }
    else
    {
%>
<div id=<%=q(headerRenderId)%>></div>
<div id=<%=q(formRenderId)%>></div>
<div id=<%=q(footerRenderId)%>></div>
<script type="text/javascript">

    Ext4.onReady(function(){

        var panel = Ext4.create('LABKEY.ext4.SurveyDisplayPanel', {
            cls             : 'lk-survey-panel themed-panel',
            rowId           : <%=rowId%>,
            surveyDesignId  : <%=surveyDesignId%>,
            responsesPk     : <%=q(responsesPk)%>,
            surveyLabel     : <%=q(surveyLabel)%>,
            isSubmitted     : <%=submitted%>,
            canEdit         : <%=canEdit%>,
            renderTo        : <%=q(formRenderId)%>,
            headerRenderTo  : <%=q(headerRenderId)%>,
            footerRenderTo  : <%=q(footerRenderId)%>,
            returnURL       : <%=q(returnURL)%>,
            autosaveInterval: 60000
        });

    });

</script>
<%
    }
%>
