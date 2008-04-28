<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.StudyController"%>
<%@ page extends="org.labkey.study.view.BaseStudyPage" %>
<%
    JspView<StudyController.DataSetForm> me = (JspView<StudyController.DataSetForm>) HttpView.currentView();
    StudyController.DataSetForm form = me.getModelBean();
    String errors = PageFlowUtil.getStrutsError(request, "main");
%>
<%=errors%>
<form action="createDataSet.post" method="POST">
    <input type="hidden" name="action" value="create">
    <table class="normal">
        <tr>
            <th align="right">Dataset Id (Integer)</th>
            <td>
                <input type="text" name="dataSetIdStr" value="<%=form.getDatasetIdStr()%>">
            </td>
        </tr>
        <tr>
            <th align="right">Dataset Label</th>
            <td><input type="text" name="label" value="<%=h(form.getLabel())%>"></td>
        </tr>
        <tr>
            <th align="right">Category</th>
            <td><input type="text" name="category" value="<%=h(form.getCategory())%>"></td>
        </tr>
        <tr>
            <th align="right">Show By Default</th>
            <td>
                <input type="checkbox" name="showByDefault" <%=form.isShowByDefault()?"checked":""%>>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
            <td>
                <%= this.buttonImg("Save")%>&nbsp;<%= this.buttonLink("Cancel", "manageTypes.view")%>
            </td>
        </tr>
    </table>
</form>