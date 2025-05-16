<%
/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.studydesign.StudyDesignUrls" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.Cohort" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.studydesign.model.TreatmentVisitMap" %>
<%@ page import="org.labkey.studydesign.model.TreatmentManager" %>
<%@ page import="org.labkey.studydesign.model.TreatmentImpl" %>
<%@ page import="org.labkey.studydesign.model.ProductImpl" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("study/vaccineDesign/VaccineDesign.css");
    }
%>
<%
    Container c = getContainer();
    Study study = StudyService.get().getStudy(c);

    User user = getUser();
    boolean canEdit  = c.hasPermission(user, UpdatePermission.class);

    String subjectNoun = "Subject";
    if (study != null)
        subjectNoun = study.getSubjectNounSingular();
%>

<style type="text/css">
    .study-vaccine-design td.cell-display {
        height: auto;
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
        Collection<? extends Cohort> cohorts = study.getCohorts(user);
        %>This section shows the immunization schedule for this study. Each treatment may consist of  one or more study products.<br/><%

        if (canEdit)
        {
            ActionURL editUrl = urlProvider(StudyDesignUrls.class).getManageTreatmentsURL(c, c.hasActiveModuleByName("viscstudies"));
            editUrl.addReturnUrl(getActionURL());
%>
            <%=link("Manage Treatments", editUrl)%><br/>
<%
        }

        List<? extends Visit> visits = TreatmentManager.getInstance().getVisitsForTreatmentSchedule(getContainer());
%>
        <br/>
        <div class="study-vaccine-design immunization-schedule-cohorts">
            <div class="main-title">Immunization Schedule</div>
            <table class="outer">
                <tr class="header-row">
                    <td class="cell-display">Group / Cohort</td>
                    <td class="cell-display"><%=h(subjectNoun)%> Count</td>
<%
                    for (Visit visit : visits)
                    {
%>
                        <td class="cell-display">
                            <%=h(visit.getDisplayString())%>
                            <%=(visit.getDescription() != null ? helpPopup("Description", visit.getDescription()) : HtmlString.EMPTY_STRING)%>
                        </td>
<%
                    }
%>
                </tr>
<%
                if (cohorts.size() == 0)
                {
                    %><tr><td class="cell-display empty" colspan="2">No data to show.</td></tr><%
                }

                int index = 0;
                for (Cohort cohort : cohorts)
                {
                    index++;
                    List<? extends TreatmentVisitMap> mapping = TreatmentManager.getInstance().getStudyTreatmentVisitMap(c, cohort.getRowId());
                    Map<Integer, Integer> visitTreatments = new HashMap<>();
                    for (TreatmentVisitMap treatmentVisitMap : mapping)
                    {
                        visitTreatments.put(treatmentVisitMap.getVisitId(), treatmentVisitMap.getTreatmentId());
                    }
%>
                    <tr class="row-outer <%=h(index % 2 == 0 ? "alternate-row" : "")%>" outer-index="<%=index-1%>">
                        <td class="cell-display " data-index="Label"><%=h(cohort.getLabel())%></td>
                        <td class="cell-display " data-index="SubjectCount"><%=h(cohort.getSubjectCount())%></td>
<%
                    for (Visit visit : visits)
                    {
                        Integer treatmentId = visitTreatments.get(visit.getId());
                        TreatmentImpl treatment = null;
                        if (treatmentId != null)
                            treatment = TreatmentManager.getInstance().getStudyTreatmentByRowId(c, user, treatmentId);

                        // show the list of study products for the treatment as a hover
                        String productHover = "";
                        if (treatment != null && treatment.getProducts() != null)
                        {
                            productHover += "<div class='study-vaccine-design'><table class='outer'><tr class='header-row'>"
                                    + "<td class='cell-display'>Label</td>"
                                    + "<td class='cell-display'>Dose and units</td>"
                                    + "<td class='cell-display'>Route</td></tr>";

                            for (ProductImpl product : treatment.getProducts())
                            {
                                String routeLabel = TreatmentManager.getInstance().getStudyDesignRouteLabelByName(c, product.getRoute());

                                productHover += "<tr><td class='cell-display '>" + h(product.getLabel()) + "</td>"
                                        + "<td class='cell-display '>" + h(product.getDose()) + "</td>"
                                        + "<td class='cell-display '>" + h(routeLabel != null ? routeLabel : product.getRoute()) + "</td></tr>";
                            }

                            productHover += "</table></div>";
                        }
%>
                        <td class="cell-display " data-index="<%=h(visit.getLabel())%>">
                            <%=h(treatment != null ? treatment.getLabel() : "")%>
                            <%=(!productHover.isEmpty() ? helpPopup("Treatment Products", HtmlString.unsafe(productHover), 500) : HtmlString.EMPTY_STRING)%>
                        </td>
<%
                    }
%>
                    </tr>
<%
                }
%>
            </table>
        </div>
<%
      }
    }
    else
    {
        %><p>The folder must contain a study in order to display an immunization schedule.</p><%
    }
%>
