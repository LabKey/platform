<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.study.visitmanager.VisitManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DataSetDefinition> me = (JspView<DataSetDefinition>)HttpView.currentView();
    DataSetDefinition dataset = me.getModelBean();

    Container container = HttpView.currentContext().getContainer();
    Study study = StudyManager.getInstance().getStudy(container);
    VisitManager visitManager = StudyManager.getInstance().getVisitManager(study);
    Cohort[] cohorts = StudyManager.getInstance().getCohorts(container, me.getViewContext().getUser());
    Map<Integer, String> cohortMap = new HashMap<Integer, String>();
    cohortMap.put(null, "All");
    if (cohorts != null)
    {
        for (Cohort cohort : cohorts)
            cohortMap.put(cohort.getRowId(), cohort.getLabel());
    }
%>
<table>
<%
    BindException errors = (BindException)request.getAttribute("errors");
    if (errors != null)
    {
        for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
        {
            %><tr><td colspan=3><font class="labkey-error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
        }
    }
%>
</table>

<%
    ActionURL updateDatasetURL = new ActionURL(StudyController.UpdateDatasetFormAction.class, container);
%>

<form action="<%=updateDatasetURL.getLocalURIString()%>" method="POST">
    <table>
        <tr>
            <td>
                <%= this.generateSubmitButton("Save")%>&nbsp;<%= this.generateButton("Cancel", "datasetDetails.view?id=" + dataset.getDataSetId())%>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label">Id</td>
            <td>
                <input type="hidden" name="datasetId" value="<%= dataset.getDataSetId() %>">
                <%= dataset.getDataSetId() %>
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
            <td><%= h(dataset.getCategory()) %></td>
        </tr>
        <tr>
           <td class="labkey-form-label">Cohort</td><td><%=h(cohortMap.get(dataset.getCohortId()))%></td>
        </tr>
        <%
            if (!study.isDateBased()) //TODO: Allow date column to change even in date-based studies...
            {
        %>
        <tr>
            <td class="labkey-form-label">Visit Date Column</td><td><%=h(dataset.getVisitDatePropertyName())%></td>
        </tr>
        <%
            }
        %>
        <tr><td class="labkey-form-label">Demographic Data</td><td><%= dataset.isDemographicData() ? "true" : "false" %></td></tr>
        <tr>
            <td class="labkey-form-label">Show In Overview</td><td><%= dataset.isShowByDefault() ? "true" : "false" %></td>
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
                    %><a href="importDataType.view?<%=DataSetDefinition.DATASETKEY%>=<%= dataset.getDataSetId() %>">[Upload]</a><%
                }
                else
                {
                    %><%= dataset.getTypeURI() %><%
                }
                %>
            </td>
        </tr>
        <tr>
            <th valign="top">Associated <%=visitManager.getPluralLabel()%></th>
            <td>
                <table>
                <%
                    for (Visit visit : study.getVisits())
                    {
                        VisitDataSetType type = dataset.getVisitType(visit.getRowId());
                %>
                        <tr>
                            <td><%= visit.getDisplayString() %></td>
                            <td>
                                <input type="hidden" name="visitRowIds" value="<%= visit.getRowId() %>">
                                <select name="visitStatus">
                                    <option value="<%= VisitDataSetType.NOT_ASSOCIATED.name() %>"
                                        <%= type == VisitDataSetType.NOT_ASSOCIATED ? "selected" : ""%>/>
                                    <option value="<%= VisitDataSetType.OPTIONAL.name() %>"
                                        <%= type == VisitDataSetType.OPTIONAL ? "selected" : ""%>>Optional</option>
                                    <option value="<%= VisitDataSetType.REQUIRED.name() %>"
                                        <%= type == VisitDataSetType.REQUIRED ? "selected" : ""%>>Required</option>
                                </select>
                            </td>
                        </tr>
                <%
                    }
                %>
                </table>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td>
                <%= this.generateSubmitButton("Save")%>&nbsp;<%= this.generateButton("Cancel", "datasetDetails.view?id=" + dataset.getDataSetId())%>
            </td>
        </tr>
    </table>
</form>
