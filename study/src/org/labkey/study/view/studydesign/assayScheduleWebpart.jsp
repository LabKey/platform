<%
/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.model.AssaySpecimenConfigImpl" %>
<%@ page import="org.labkey.study.model.LocationImpl" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("study/StudyVaccineDesign.css"));
        return resources;
    }
%>
<%
    Container c = getContainer();
    StudyImpl study = StudyManager.getInstance().getStudy(c);

    User user = getUser();
    boolean canEdit = c.hasPermission(user, UpdatePermission.class);

    String assayPlan = "";
    if (study != null && study.getAssayPlan() != null)
        assayPlan = h(study.getAssayPlan()).replaceAll("\n", "<br/>");
%>

<style type="text/css">

    table.study-vaccine-design .labkey-col-header {
        background-color: #<%= WebThemeManager.getTheme(c).getGridColor() %>;
    }

</style>

<%
    if (study != null)
    {
        List<AssaySpecimenConfigImpl> assaySpecimenConfigs = study.getAssaySpecimenConfigs("AssayName");
        List<VisitImpl> visits = study.getVisitsForAssaySchedule();

        if (assaySpecimenConfigs.size() == 0)
        {
            %>No assays have been scheduled.<br/><%
        }

        if (canEdit)
        {
%>
            To change the set of assays and edit the assay schedule, click the edit button below.<br/>
            <%= button("Edit").href(StudyDesignController.ManageAssayScheduleAction.class, getContainer()) %>
<%
        }

        %><p><%=assayPlan%></p><%

        if (assaySpecimenConfigs.size() > 0)
        {
%>
            <table class="labkey-read-only labkey-data-region labkey-show-borders study-vaccine-design" style="border: solid #ddd 1px;">
                <tr>
                    <td class="labkey-col-header" colspan="<%=visits.size()+3%>"><div class="study-vaccine-design-header">Assay Schedule</div></td>
                </tr>
                <tr>
                    <td class="labkey-col-header">Assay</td>
                    <td class="labkey-col-header">Lab</td>
                    <td class="labkey-col-header">Sample Type</td>
<%
            for (VisitImpl visit : visits)
            {
%>
                    <td class="labkey-col-header">
                        <%=h(visit.getDisplayString())%>
                        <%=(visit.getDescription() != null ? PageFlowUtil.helpPopup("Description", visit.getDescription()) : "")%>
                    </td>
<%
            }
%>
                </tr>
<%
            for (AssaySpecimenConfigImpl assaySpecimen : assaySpecimenConfigs)
            {
                // concatenate sample type (i.e. sample type | tube type)
                String sampleType = "";
                String sep = "";
                if (assaySpecimen.getSampleType() != null)
                {
                    sampleType += assaySpecimen.getSampleType();
                    sep = "|";
                }
                if (assaySpecimen.getTubeType() != null)
                {
                    sampleType += sep + assaySpecimen.getTubeType();
                }

                String locationLabel = "";
                if (assaySpecimen.getLab() != null)
                {
                    String labLabel = StudyManager.getInstance().getStudyDesignLabLabelByName(c, assaySpecimen.getLab());
                    locationLabel += (labLabel != null ? labLabel : assaySpecimen.getLab());
                }
                else if (assaySpecimen.getLocationId() != null)
                {
                    LocationImpl location = StudyManager.getInstance().getLocation(c, assaySpecimen.getLocationId());
                    locationLabel = location != null ? location.getLabel() : "";
                }

                String assayLabel = StudyManager.getInstance().getStudyDesignAssayLabelByName(c, assaySpecimen.getAssayName());
%>
                <tr>
                    <td class="assay-row-padded-view"><%=h(assayLabel != null ? assayLabel : assaySpecimen.getAssayName())%>
                        <%=(assaySpecimen.getDescription() != null ? PageFlowUtil.helpPopup("Description", assaySpecimen.getDescription()) : "")%>
                    </td>
                    <td class="assay-row-padded-view"><%=h(locationLabel)%></td>
                    <td class="assay-row-padded-view"><%=h(sampleType)%></td>
<%
                List<Integer> assaySpecimenVisits = StudyManager.getInstance().getAssaySpecimenVisitIds(c, assaySpecimen);
                for (VisitImpl visit : visits)
                {
                    %><td class="assay-row-padded-view" align="center"><%=assaySpecimenVisits.contains(visit.getRowId()) ? "&#x2713;" : " "%></td><%
                }
%>
                </tr>
<%
            }
%>
            </table>
<%
        }
    }
    else
    {
        %><p>The folder must contain a study in order to display an assay schedule.</p><%
    }
%>