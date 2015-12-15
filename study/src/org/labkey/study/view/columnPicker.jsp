<%
/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
<%@ page import="org.labkey.api.exp.OntologyManager"%>
<%@ page import="org.labkey.api.exp.PropertyDescriptor"%>
<%@ page import="org.labkey.api.study.Visit"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.study.controllers.reports.ReportsController"%>
<%@ page import="org.labkey.study.model.DatasetDefinition"%>
<%@ page import="org.labkey.study.model.VisitDataset"%>
<%@ page import="org.labkey.study.model.VisitImpl"%>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
//    Study study;
//    ReportsController.ColumnPickerForm form;

ReportsController.DataPickerBean bean = (ReportsController.DataPickerBean)HttpView.currentModel();
DatasetDefinition selectedDataset = null;
List<PropertyDescriptor> pds = null;
String error = null;
%>
<p><%=h(bean.caption)%></p>
<labkey:form id="columnPickerForm" action="<%=h(buildURL(ReportsController.RenderConfigureEnrollmentReportAction.class))%>" method="POST">
<table>
<tr><td>Dataset</td><td><select name="<%=text(DatasetDefinition.DATASETKEY)%>" onchange="refreshForm(this.options[this.selectedIndex].value);">
    <option value="-1"></option>
<%
for (DatasetDefinition ds : bean.study.getDatasets())
{
    boolean selected = bean.form.getDatasetId().intValue() == ds.getDatasetId();
    if (selected)
        selectedDataset = ds;
    %><option<%=selected(selected)%> value="<%=ds.getDatasetId()%>"><%=h(ds.getDisplayString())%></option><%
}
%></select></td></tr>
<%
if (selectedDataset != null)
{
    HashMap<Integer, VisitImpl> visits = new HashMap<>();
    for (VisitImpl visit : bean.study.getVisits(Visit.Order.DISPLAY))
    {
        if (visit.getSequenceNumMin() == visit.getSequenceNumMax())
            visits.put(visit.getRowId(), visit);
    }
    List<VisitDataset> datasetVisits = selectedDataset.getVisitDatasets();
    if (null != selectedDataset.getTypeURI())
        pds = OntologyManager.getPropertiesForType(selectedDataset.getTypeURI(),bean.study.getContainer());
    if (null == pds || pds.size() == 0)
        error = "Dataset is not defined yet.";

    %><tr><td>Visit</td><td><select name="<%=text(VisitImpl.SEQUENCEKEY)%>"><%
    if (datasetVisits.size() == 0 && error == null)
        error = "No visits defined for this dataset.";
    for (VisitDataset vds : datasetVisits)
    {
        VisitImpl visit = visits.get(vds.getVisitRowId());
//        if (!visit.isRequired())            continue;
        if (null == visit)
            {%><!-- <%=vds.getVisitRowId()%> not found --><% continue;}
        %><option<%=selected(bean.form.getSequenceNum() == visit.getSequenceNumMin())%> value="<%=visit.getSequenceNumMin()%>"><%=h(visit.getDisplayString())%></option><%
    }
    %></select></td></tr><%
    %>
    <%
    if (bean.pickColumn)
    {
        %>
        <tr><td>Column</td><td><select name="propertyId"><%
        if (null != pds)
        {
            int count = 0;
            for (PropertyDescriptor pd : pds)
            {
                if (bean.propertyType != null && bean.propertyType != pd.getPropertyType())
                    continue;
                count++;
                %><option<%=selected((pd.getPropertyId() == bean.form.getPropertyId()))%> value="<%=h(pd.getPropertyId())%>"><%=h(pd.getLabel())%></option><%
            }
            if (count==0 && error==null)
                error = "No date fields found in this dataset.";
        }
        %></select></td></tr>
    <%
    }
    %><%
}
%>
</table>
<%= button("Submit").submit(true) %>
</labkey:form>
<span class=labkey-error><%=h(error==null ? "" : error)%></span>
<script type="text/javascript">
function refreshForm(value)
{
    document.location = "?datasetId=" + value;
}
</script>