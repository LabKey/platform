<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.study.Visit" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitDatasetType" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="java.io.Writer" %>
<%@ page import="org.labkey.api.view.template.FrameFactoryClassic" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DatasetDefinition> me = (JspView<DatasetDefinition>)HttpView.currentView();
    DatasetDefinition dataset = me.getModelBean();

    Container container = getContainer();
    StudyImpl study = StudyManager.getInstance().getStudy(container);
    VisitManager visitManager = StudyManager.getInstance().getVisitManager(study);
    List<CohortImpl> cohorts = StudyManager.getInstance().getCohorts(container, getUser());
    Map<Integer, String> cohortMap = new HashMap<>();
    cohortMap.put(null, "All");

    if (cohorts != null)
    {
        for (CohortImpl cohort : cohorts)
            cohortMap.put(cohort.getRowId(), cohort.getLabel());
    }
%>
<labkey:errors/>
<%
    ActionURL updateDatasetURL = new ActionURL(StudyController.UpdateDatasetVisitMappingAction.class, container);
%>

<labkey:form action="<%=h(updateDatasetURL.getLocalURIString())%>" method="POST">
<%= button("Save").submit(true) %>&nbsp;<%= text(button("Cancel").href(buildURL(StudyController.DatasetDetailsAction.class, "id=" + dataset.getDatasetId())).toString()) %>
<%
    FrameFactoryClassic.startTitleFrame(out, "Dataset Properties", null, "100%", null);
%>
    <table>
        <tr>
            <td class="labkey-form-label">Id</td>
            <td>
                <input type="hidden" name="datasetId" value="<%= dataset.getDatasetId() %>">
                <%= dataset.getDatasetId() %>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Dataset Name</td>
            <td><%= h(dataset.getName()) %></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Label</td>
            <td><%= h(dataset.getLabel()) %></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Category</td>
            <td><%= h(dataset.getViewCategory() != null ? dataset.getViewCategory().getLabel() : null) %></td>
        </tr>
        <tr>
           <td class="labkey-form-label">Cohort</td><td><%=h(cohortMap.get(dataset.getCohortId()))%></td>
        </tr>
        <%
            if (study.getTimepointType() == TimepointType.VISIT) //TODO: Allow date column to change even in date-based studies...
            {
        %>
        <tr>
            <td class="labkey-form-label">Visit Date Column</td><td><%=h(dataset.getVisitDateColumnName())%></td>
        </tr>
        <%
            }
        %>
        <tr><td class="labkey-form-label">Demographic Data</td><td><%= text(dataset.isDemographicData() ? "true" : "false") %></td></tr>
        <tr>
            <td class="labkey-form-label">Show In Overview</td><td><%= text(dataset.isShowByDefault() ? "true" : "false") %></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Description</td>
            <td><%= h(dataset.getDescription()) %></td>
        </tr>
        <tr>
            <td class="labkey-form-label">Definition URI</td>
            <td>
                <%
                if (dataset.getTypeURI() == null)
                {
                    %><a href="importDataType.view?<%=h(DatasetDefinition.DATASETKEY)%>=<%= dataset.getDatasetId() %>">[Upload]</a><%
                }
                else
                {
                    %><%= h(dataset.getTypeURI()) %><%
                }
                %>
            </td>
        </tr>
        </table>
<%
    FrameFactoryClassic.endTitleFrame(out);
%>
<%
    FrameFactoryClassic.startTitleFrame(out, "Associated " + visitManager.getPluralLabel(), null, "100%", null);
%>
<table>
        <tr>
            <td>
                <table>
                <%
                    for (VisitImpl visit : study.getVisits(Visit.Order.DISPLAY))
                    {
                        VisitDatasetType type = dataset.getVisitType(visit.getRowId());
                %>
                        <tr>
                            <td><%= h(visit.getDisplayString()) %></td>
                            <td>
                                <input type="hidden" name="visitRowIds" value="<%= visit.getRowId() %>">
                                <select name="visitStatus">
                                    <option value="<%= h(VisitDatasetType.NOT_ASSOCIATED.name()) %>"
                                        <%=selected(type == VisitDatasetType.NOT_ASSOCIATED)%>/>
                                    <option value="<%= h(VisitDatasetType.OPTIONAL.name()) %>"
                                        <%=selected(type == VisitDatasetType.OPTIONAL)%>>Optional</option>
                                    <option value="<%= h(VisitDatasetType.REQUIRED.name()) %>"
                                        <%=selected(type == VisitDatasetType.REQUIRED)%>>Required</option>
                                </select>
                            </td>
                        </tr>
                <%
                    }
                %>
                </table>
            </td>
        </tr>
    </table>
<%
    FrameFactoryClassic.endTitleFrame(out);
%>
<%= button("Save").submit(true) %>&nbsp;<%= button("Cancel").href(buildURL(StudyController.DatasetDetailsAction.class, "id=" + dataset.getDatasetId())) %>
</labkey:form>
