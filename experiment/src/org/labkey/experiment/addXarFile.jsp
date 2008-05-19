<%
/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
