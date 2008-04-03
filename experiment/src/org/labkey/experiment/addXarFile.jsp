<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ExperimentController.AddXarFileForm> me = (JspView<ExperimentController.AddXarFileForm>) HttpView.currentView();
    ExperimentController.AddXarFileForm form = me.getModelBean();

%>


<p class="labkey-error"><b><%= h(form.getError()) %></b></p>
<p>
<form name="upload" action="uploadXarFile.post" enctype="multipart/form-data" method="post">
    Local file: <input id="UploadFile" type="file" name="uploadFile" value="" size="60"> <input type=SUBMIT value="Upload" name="upload">
</form>
</p>
<p>To import a <i>.xar</i> or <i>.xar.xml</i> file that is already on the server's disk, please use the <a href="<%=urlProvider(PipelineUrls.class).urlSetup(me.getViewContext().getContainer())%>">Data Pipeline</a> instead.</p>
<script for=window event=onload>
try {document.getElementById("uploadFile").focus();} catch(x){}
</script>
