<%@ page import="org.labkey.study.model.DataSetDefinition"%>
<%@ page import="java.util.List"%>
<%@ page import="java.util.ArrayList"%>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    List<DataSetDefinition> undefined = new ArrayList<DataSetDefinition>();
    for (DataSetDefinition def : getDataSets())
    {
        if (def.getTypeURI() == null)
            undefined.add(def);
    }
%>
<p>This study references <%= undefined.size() %> datasets without defined schemas.</p>
<%
    if (!undefined.isEmpty())
    {
%>
<p>A schema definition should be imported for each dataset in this study.</p>
<%= textLink("Bulk Import Schemas", "bulkImportDataTypes.view")%>&nbsp;

<h3>Undefined datasets:</h3>
<table class="normal">
    <tr>
        <th>ID</th>
        <th>Label</th>
    </tr>
<%
    for (DataSetDefinition def : undefined)
    {
%>
    <tr>
        <td><%= def.getDataSetId() %></td>
        <td><%= def.getLabel() != null ? def.getLabel() : "" %></td>
    </tr>
<%
    }
%>
</table>
<%
    }
%>