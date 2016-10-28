<%
/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.survey.SurveyController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("codemirror");
        dependencies.add("sqv");
        dependencies.add("survey");
    }
%>
<%
    JspView<SurveyController.SurveyDesignForm> me = (JspView<SurveyController.SurveyDesignForm>) HttpView.currentView();
    SurveyController.SurveyDesignForm form = me.getModelBean();

    String allSchemas = getActionURL().getParameter("allSchemas");
    String renderId = "survey-design-panel-" + UniqueID.getRequestScopedUID(HttpView.currentRequest());
%>
<div id="<%= h(renderId)%>" class="dvc"></div>
<script type="text/javascript">

    Ext4.onReady(function(){

        Ext4.QuickTips.init();

        var panel = Ext4.create('LABKEY.ext4.SurveyDesignPanel', {
            height          : 650,
            surveyId        : <%=form.getRowId()%>,
            renderTo        : <%=q(renderId)%>,
            schemaName      : <%=q(form.getSchemaName())%>,
            queryName       : <%=q(form.getQueryName())%>,
            returnUrl       : <%=q(form.getReturnActionURL().getLocalURIString())%>,
            allSchemas      : <%=q(allSchemas)%>
        });

        var _resize = function() {
            if (panel && panel.doLayout) { panel.doLayout(); }
        };

        Ext4.EventManager.onWindowResize(_resize);
    });
</script>

