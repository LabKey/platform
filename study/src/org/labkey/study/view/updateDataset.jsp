<%@ page import="org.labkey.api.data.ColumnInfo"%>
<%@ page import="org.labkey.api.data.TableInfo"%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.model.*" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.*" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<DataSetDefinition> me = (JspView<DataSetDefinition>)HttpView.currentView();
    DataSetDefinition dataset = me.getModelBean();

    Study study = StudyManager.getInstance().getStudy(HttpView.currentContext().getContainer());
    Cohort[] cohorts = StudyManager.getInstance().getCohorts(me.getViewContext().getContainer(), me.getViewContext().getUser());
    Map<Integer, String> cohortMap = new HashMap<Integer, String>();
    cohortMap.put(null, "All");
    if (cohorts != null)
    {
        for (Cohort cohort : cohorts)
            cohortMap.put(cohort.getRowId(), cohort.getLabel());
    }
%>
<table border=0 cellspacing=2 cellpadding=0>
<%
    BindException errors = (BindException)request.getAttribute("errors");
    if (errors != null)
    {
        for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
        {
            %><tr><td colspan=3><font color="red" class="error"><%=h(HttpView.currentContext().getMessage(e))%></font></td></tr><%
        }
    }
%>
</table>

<form action="updateDatasetForm.post" method="POST">
    <table class="normal">
        <tr>
            <td>
                <%= this.buttonImg("Save")%>&nbsp;<%= this.buttonLink("Cancel", "datasetDetails.view?id=" + dataset.getDataSetId())%>
            </td>
        </tr>
        <tr>
            <td class="ms-searchform">Id</td>
            <td>
                <input type="hidden" name="datasetId" value="<%= dataset.getDataSetId() %>">
                <%= dataset.getDataSetId() %>
            </td>
        </tr>
        <tr>
            <td class="ms-searchform">Dataset Name</td>
            <td><%= h(dataset.getName()) %></td>
        </tr>
        <tr>
            <td class="ms-searchform">Label</td>
            <td><%= h(dataset.getLabel()) %></td>
        </tr>
        <tr>
            <td class="ms-searchform">Category</td>
            <td><%= h(dataset.getCategory()) %></td>
        </tr>
        <tr>
           <td class="ms-searchform">Cohort</td><td><%=h(cohortMap.get(dataset.getCohortId()))%></td>
        </tr>
        <%
            if (!study.isDateBased()) //TODO: Allow date column to change even in date-based studies...
            {
        %>
        <tr>
            <td class="ms-searchform">Visit Date Column</td><td><%=h(dataset.getVisitDatePropertyName())%></td>
        </tr>
        <%
            }
        %>
        <tr><td class="ms-searchform">Demographic Data</td><td class=normal><%= dataset.isDemographicData() ? "true" : "false" %></td></tr>
        <tr>
            <td class="ms-searchform">Show In Overview</td><td><%= dataset.isShowByDefault() ? "true" : "false" %></td>
        </tr>
        <tr>
            <td class="ms-searchform">Description</td>
            <td><%= h(dataset.getDescription()) %></td>
        </tr>
        <tr>
            <td class="ms-searchform">Definition URI</td>
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
            <th valign="top">Associated Visits</th>
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
                                        <%= type == VisitDataSetType.NOT_ASSOCIATED ? "selected" : ""%>></option>
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
                <%= this.buttonImg("Save")%>&nbsp;<%= this.buttonLink("Cancel", "datasetDetails.view?id=" + dataset.getDataSetId())%>
            </td>
        </tr>
    </table>
</form>
