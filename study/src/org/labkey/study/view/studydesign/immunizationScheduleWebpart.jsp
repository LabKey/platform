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
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.study.controllers.StudyDesignController" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.model.TreatmentImpl" %>
<%@ page import="org.labkey.study.model.TreatmentVisitMapImpl" %>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.labkey.study.model.ProductImpl" %>
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
    boolean canEdit  = c.hasPermission(user, UpdatePermission.class);

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
<%
        List<CohortImpl> cohorts = study.getCohorts(user);
        if (cohorts.size() == 0)
        {
            %><tr><td>No cohort/treatment/timepoint mappings have been defined.</td></tr><%
        }
        else
        {
            List<VisitImpl> visits = study.getVisitsForImmunizationSchedule();
%>
            <tr><td>
                <table class="labkey-read-only labkey-data-region labkey-show-borders" style="border: solid #ddd 1px;">
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
                            treatment = StudyManager.getInstance().getStudyTreatmentByRowId(c, user, treatmentId);

                        // show the list of study products for the treatment as a hover
                        // Example display:
                        //     Immunogens: ABC, DEF
                        //     Adjuvants: GHI, JKL
                        String productHover = "";
                        String sep = "";
                        if (treatment != null && treatment.getProducts() != null)
                        {
                            String prevRole = null;
                            for (ProductImpl product : treatment.getProducts())
                            {
                                if (prevRole == null || !prevRole.equals(product.getRole()))
                                {
                                    prevRole = product.getRole();
                                    productHover += (productHover.length() > 0 ? "<br/>" : "");
                                    productHover += "<b>" + h(product.getRole()) + "s:</b> ";
                                    sep = "";
                                }

                                productHover += sep + h(product.getLabel());
                                sep = ", ";
                            }
                        }
%>
                        <td class="assay-row-padded-view">
                            <%=h(treatment != null ? treatment.getLabel() : "")%>
                            <%=(productHover.length() > 0 ? PageFlowUtil.helpPopup("Study Products", productHover, true, 300) : "")%>
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
<%
        }
%>
</table>
<%
    }
    else
    {
%>
        <p>The folder must contain a study in order to display a vaccine design.</p>
<%
    }

    if (canEdit)
    {
        %><br/><%=textLink("Edit", StudyDesignController.ManageImmunizationsAction.class)%><%
    }
%>
