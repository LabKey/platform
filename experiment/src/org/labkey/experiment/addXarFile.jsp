<%
/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<labkey:errors />
<p>
<form name="upload" action="showAddXarFile.post" enctype="multipart/form-data" method="post">
    Local file: <input id="UploadFile" type="file" name="uploadFile" value="" size="60"> <input type=SUBMIT value="Upload" name="upload">
</form>
</p>
<p>To import a <em>.xar</em> or <em>.xar.xml</em> file that is already on the server's disk, please use the <a href="<%=urlProvider(PipelineUrls.class).urlSetup(HttpView.currentContext().getContainer())%>">Data Pipeline</a> instead.</p>

<script type="text/javascript">
    Ext.onReady(new function()
    {
        try {document.getElementById("UploadFile").focus();} catch(x){}
    });
</script>
