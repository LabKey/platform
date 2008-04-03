<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.study.controllers.samples.SamplesController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SamplesController.UploadSpecimensForm> me = (JspView<SamplesController.UploadSpecimensForm>) HttpView.currentView();
    SamplesController.UploadSpecimensForm bean = me.getModelBean();
%>
<%=PageFlowUtil.getStrutsError(request, "main")%>
Use this form to <b>replace all specimens</b> in the repository with a new list of specimens.<br>
[<a href="getSpecimenExcel.view">Download a template workbook</a>]<br><br>
Paste data in the area below
<form action="handleUploadSpecimens.post" method="post" enctype="multipart/form-data">
    <textarea name=tsv id="tsv" rows=20 cols="70"><%=h(bean.getTsv())%></textarea><br>
    <%=buttonImg("Submit")%> <%=buttonImg("Cancel", "window.history.back();return false;")%>
</form>

<b>This will REPLACE all specimens in the repository</b>
