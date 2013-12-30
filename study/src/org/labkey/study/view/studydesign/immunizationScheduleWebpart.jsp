<%
    /*
     * Copyright (c) 2013 LabKey Corporation
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
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.security.permissions.ManageStudyPermission" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="org.labkey.study.model.TreatmentImpl" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("study/ImmunizationSchedule.js"));
        resources.add(ClientDependency.fromFilePath("study/StudyVaccineDesign.css"));
        return resources;
    }
%>
<%
    Container c = getContainer();
    StudyImpl study = StudyManager.getInstance().getStudy(c);

    User user = getUser();
    boolean canManageStudy  = c.hasPermission(user, ManageStudyPermission.class);

    WebTheme theme = WebThemeManager.getTheme(c);
    String link        = theme.getLinkColor();
    String grid        = theme.getGridColor();
%>

<style type="text/css">

    table.study-vaccine-design .labkey-col-header {
        background-color: #<%= grid %>;
    }

    table.study-vaccine-design .labkey-col-header-active {
        background-color: #<%= grid %>;
    }

    table.study-vaccine-design .labkey-col-header-active .gwt-Label {
        color: #<%= link %>;
    }

    table.study-vaccine-design .labkey-col-header-active .gwt-Label:hover {
        color: #<%= link %>;
    }

    table.study-vaccine-design .labkey-row-header {
        background-color: #<%= grid %>;
    }

    table.study-vaccine-design .labkey-row-active .gwt-Label, table.study-vaccine-design .labkey-row-header .gwt-Label {
        color: #<%= link %>;
    }

    table.study-vaccine-design .labkey-row-active .gwt-Label:hover, table.study-vaccine-design .labkey-row-header .gwt-Label:hover {
        color: #<%= link %>;
    }

    table.study-vaccine-design .assay-corner {
        background-color: #<%= grid %>;
    }

    a.labkey-button, a.labkey-button:visited, a.gwt-Anchor {
        color: #<%= link %>;
    }

</style>

<%
    if (study != null)
    {
%>
<table class='study-vaccine-design'>
    <tr><td><h2>Treatments</h2></td></tr>
    <%
        List<TreatmentImpl> treatments = study.getStudyTreatments(user);
        if (treatments.size() == 0)
        {
            %><tr><td>No treatments have been defined.</td></tr><%
        }
        else
        {
%>
            <tr><td>
                <table class="labkey-read-only labkey-data-region labkey-show-borders" style="border: solid #ddd 1px;">
                    <tr>
                        <td class="labkey-col-header">Label</td>
                        <td class="labkey-col-header">Description</td>
                        <td class="labkey-col-header">Study Products</td>
                    </tr>
                    <%
                for (TreatmentImpl treatment : treatments)
                {
                    String description = treatment.getDescription();
                    description = description != null ? h(description).replaceAll("\n", "<br/>") : "&nbsp;";

                    %>
                    <tr>
                        <td class="assay-row-padded-view"><%=h(treatment.getLabel())%></td>
                        <td class="assay-row-padded-view"><%=description%></td>
                        <td class="assay-row-padded-view"><div class="treatment-study-products" id="<%=treatment.getRowId()%>">...</div></td>
                    </tr>
                    <%
                }
                    %>
                </table>
            </td></tr>
            <%
        }
%>
    <tr><td><h2>Immunization Schedule</h2></td></tr>
    <tr><td>No cohort/treatment/timepoint mappings have been defined.</td></tr>
</table>
<%
    }
    else
    {
%>
        <p>The folder must contain a study in order to display a vaccine design.</p>
<%
    }

    if (canManageStudy)
    {
%><br/><%=textLink("Manage Immunizations", StudyController.ManageImmunizationsAction.class)%><%
    }
%>

<script type="text/javascript">
    Ext4.onReady(function(){

        // query and display the html table for each treatment's study product definition
        Ext4.each(Ext4.dom.Query.select('.treatment-study-products'), function(div){
            LABKEY.Query.selectRows({
                schemaName: 'study',
                queryName: 'TreatmentProductMap',
                columns: 'ProductId/Label,Dose,Route',
                filterArray: [LABKEY.Filter.create('TreatmentId', div.id)],
                sort: '-ProductId/Role,ProductId/RowId',
                success: function(data) {
                    div.innerHTML = new LABKEY.ext4.VaccineDesignDisplayHelper().getTreatmentProductDisplay(data.rows);
                }
            })
        });

    });
</script>