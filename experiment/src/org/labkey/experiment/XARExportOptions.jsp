<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.LSIDRelativizer" %>
<%@ page import="org.labkey.experiment.XarExportType" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.data.DataRegion" %>

<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<ExperimentController.ExportBean> me = (JspView<ExperimentController.ExportBean>) HttpView.currentView();
ExperimentController.ExportBean bean = me.getModelBean();
%>

<p class="labkey-error"><b><%= h(bean.getError()) %></b></p>

<form action="<%= bean.getPostURL().toString() %>" method="post">

<table>
    <tr>
        <td>LSID output type:</td>
        <td>
            <select name="lsidOutputType">
                <% for(LSIDRelativizer lsidOutputType : LSIDRelativizer.values()) { %>
                    <option value="<%= lsidOutputType %>" <% if (lsidOutputType == bean.getSelectedRelativizer()) { %>selected<% } %>><%= lsidOutputType.getDescription() %></option>
                <% } %>
            </select>
        </td>
    </tr>
    <tr>
        <td>Export type:</td>
        <td>
            <select name="exportType">
                <% for(XarExportType exportType : XarExportType.values()) { %>
                    <option value="<%= exportType %>" <% if (exportType == bean.getSelectedExportType()) { %>selected<% } %>><%= exportType.getDescription() %></option>
                <% } %>
            </select>
        </td>
    </tr>
    <tr>
        <td>Filename:</td>
        <td><input type="text" size="45" name="fileName" value="<%= h(bean.getFileName()) %>" /></td>
    </tr>
</table>

<input type="hidden" name="<%= DataRegionSelection.DATA_REGION_SELECTION_KEY %>" value="<%= bean.getDataRegionSelectionKey() %>" />
<% for (String selectIds : request.getParameterValues(DataRegion.SELECT_CHECKBOX_NAME) == null ? new String[0] : request.getParameterValues(DataRegion.SELECT_CHECKBOX_NAME))
{ %>
    <input type="hidden" name="<%= DataRegion.SELECT_CHECKBOX_NAME %>" value="<%= h(selectIds)%>" />
<% } %>
<input type="hidden" name="expRowId" value="<%= bean.getExpRowId() %>" />
<input type="hidden" name="protocolId" value="<%= bean.getProtocolId() == null ? "" : bean.getProtocolId() %>" />
<%= buttonImg("Export") %>
</form>
