<%@ page import="org.labkey.experiment.MoveRunsBean" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<MoveRunsBean> me = (JspView<MoveRunsBean>) HttpView.currentView();
    MoveRunsBean bean = me.getModelBean();
%>
<script type="text/javascript">
    function moveTo(targetContainerId)
    {
        document.forms["moveForm"].targetContainerId.value = targetContainerId;
        document.forms["moveForm"].submit();
    }
</script>
<form name="moveForm" action="moveRuns.post" method="POST">
    <%
        for (String id : DataRegionSelection.getSelected(HttpView.currentContext(), false))
        { %>
            <input type="hidden" name="<%= DataRegion.SELECT_CHECKBOX_NAME%>" value="<%= h(id) %>" /><%
        }
    %>
    <input type="hidden" name="<%= DataRegionSelection.DATA_REGION_SELECTION_KEY %>" value="<%= bean.getDataRegionSelectionKey() %>" />
    <input type="hidden" name="targetContainerId" />
<table class="dataRegion">
<tr><td style="padding-left:0">Please select the destination folder. Folders that are not configured with a pipeline root are not valid destinations. They are shown in the list, but are not linked.</td></tr>
<tr><td>&nbsp;</td></tr>
<%=bean.getContainerTree().render()%>
</table>

</form>
