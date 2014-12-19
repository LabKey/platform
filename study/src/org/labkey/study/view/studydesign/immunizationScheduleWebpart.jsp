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
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.ProductImpl" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.TreatmentImpl" %>
<%@ page import="org.labkey.study.model.TreatmentManager" %>
<%@ page import="org.labkey.study.model.TreatmentVisitMapImpl" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%!
    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromPath("study/StudyVaccineDesign.js"));
        resources.add(ClientDependency.fromPath("study/StudyVaccineDesign.css"));
        return resources;
    }
%>
<%
    Container c = getContainer();
    StudyImpl study = StudyManager.getInstance().getStudy(c);

    User user = getUser();
    boolean canEdit  = c.hasPermission(user, UpdatePermission.class);
%>

<style type="text/css">

    table.study-vaccine-design .labkey-col-header {
        background-color: #<%= WebThemeManager.getTheme(c).getGridColor() %>;
    }

</style>

<%
    if (study != null)
    {
      if (!StudyManager.getInstance().showCohorts(c, user))
      {
            %><p>You do not have permissions to see this data.</p><%
      }
      else
      {
        List<CohortImpl> cohorts = study.getCohorts(user);
        if (cohorts.size() == 0)
        {
            %>No cohort/treatment/timepoint mappings have been defined.<br/><%
        }
        else
        {
            %>This section shows the immunization schedule. Each treatment may consist of several immunogens and adjuvants.<br/><%
        }

        if (canEdit)
        {
%>
            To change the set of groups/cohorts and edit the immunization schedule, click the edit button below.<br/>
            <%= button("Edit").href(StudyDesignController.ManageTreatmentsAction.class, getContainer()) %>
<%
        }

        if (cohorts.size() > 0)
        {
            List<VisitImpl> visits = study.getVisitsForTreatmentSchedule();
%>
        <table class='study-vaccine-design'>
            <tr><td>
                <table class="labkey-read-only labkey-data-region labkey-show-borders" style="border: solid #ddd 1px;">
                    <tr>
                        <td class="labkey-col-header" colspan="<%=visits.size()+2%>"><div class="study-vaccine-design-header">Immunization Schedule</div></td>
                    </tr>
                    <tr>
                        <td class="labkey-col-header">Group / Cohort</td>
                        <td class="labkey-col-header">Count</td>
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
                for (CohortImpl cohort : cohorts)
                {
                    List<TreatmentVisitMapImpl> mapping = study.getStudyTreatmentVisitMap(c, cohort.getRowId());
                    Map<Integer, Integer> visitTreatments = new HashMap<>();
                    for (TreatmentVisitMapImpl treatmentVisitMap : mapping)
                    {
                        visitTreatments.put(treatmentVisitMap.getVisitId(), treatmentVisitMap.getTreatmentId());
                    }
%>
                    <tr>
                        <td class="assay-row-padded-view"><%=h(cohort.getLabel())%></td>
                        <td class="assay-row-padded-view"><%=cohort.getSubjectCount() != null ? cohort.getSubjectCount() : ""%></td>
<%
                    for (VisitImpl visit : visits)
                    {
                        Integer treatmentId = visitTreatments.get(visit.getRowId());
                        TreatmentImpl treatment = null;
                        if (treatmentId != null)
                            treatment = TreatmentManager.getInstance().getStudyTreatmentByRowId(c, user, treatmentId);

                        // show the list of study products for the treatment as a hover
                        String productHover = "";
                        if (treatment != null && treatment.getProducts() != null)
                        {
                            productHover += "<table class='study-vaccine-design'><tr>"
                                    + "<td class='labkey-col-header'>Label</td>"
                                    + "<td class='labkey-col-header'>Dose and units</td>"
                                    + "<td class='labkey-col-header'>Route</td></tr>";

                            for (ProductImpl product : treatment.getProducts())
                            {
                                String routeLabel = TreatmentManager.getInstance().getStudyDesignRouteLabelByName(c, product.getRoute());

                                productHover += "<tr><td class='assay-row-padded-view'>" + h(product.getLabel()) + "</td>"
                                        + "<td class='assay-row-padded-view'>" + h(product.getDose()) + "</td>"
                                        + "<td class='assay-row-padded-view'>" + h(routeLabel != null ? routeLabel : product.getRoute()) + "</td></tr>";
                            }

                            productHover += "</table>";
                        }
%>
                        <td class="assay-row-padded-view">
                            <%=h(treatment != null ? treatment.getLabel() : "")%>
                            <%=(productHover.length() > 0 ? PageFlowUtil.helpPopup("Treatment Products", productHover, true, 500) : "")%>
                        </td>
<%
                    }
%>
                    </tr>
<%
                }
%>
                </table>
            </td></tr>
        </table>
<%
        }
      }
    }
    else
    {
        %><p>The folder must contain a study in order to display an immunization schedule.</p><%
    }
%>
