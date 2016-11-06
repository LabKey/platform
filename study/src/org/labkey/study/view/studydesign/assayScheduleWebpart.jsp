<%
/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("study/vaccineDesign/vaccineDesign.lib.xml");
        dependencies.add("study/vaccineDesign/VaccineDesign.css");
    }
%>
<%
    Container c = getContainer();
    boolean useAlternateLookupFields = getContainer().getActiveModules().contains(ModuleLoader.getInstance().getModule("rho"));

    StudyImpl study = StudyManager.getInstance().getStudy(c);

    User user = getUser();
    boolean canEdit = c.hasPermission(user, UpdatePermission.class);

    String assayPlan = "";
    if (study != null && study.getAssayPlan() != null)
        assayPlan = study.getAssayPlan();
%>

<style type="text/css">
    .study-vaccine-design tr.header-row td {
        background-color: #<%= WebThemeManager.getTheme(c).getGridColor() %> !important;
    }
</style>

<%
    if (study != null)
    {
        %>This section shows the assay schedule for this study.<br/><%

        if (canEdit)
        {
            ActionURL editUrl = new ActionURL(StudyDesignController.ManageAssayScheduleAction.class, getContainer());
            if (useAlternateLookupFields)
                editUrl.addParameter("useAlternateLookupFields", true);
            editUrl.addReturnURL(getActionURL());
%>
            <%=textLink("Manage Assay Schedule", editUrl)%><br/>
<%
        }

%>
        <p data-index="AssayPlan"><%=h(assayPlan).replaceAll("\n", "<br/>")%></p>
        <div id="assay-configurations-panel"></div>
<%
    }
    else
    {
%>
        <p>The folder must contain a study in order to display an assay schedule.</p>
<%
    }
%>

<script type="text/javascript">
    Ext4.onReady(function()
    {
        Ext4.create('LABKEY.VaccineDesign.AssaysGrid', {
            renderTo : 'assay-configurations-panel',
            disableEdit : true,
            useAlternateLookupFields : <%=useAlternateLookupFields%>
        });
    });
</script>