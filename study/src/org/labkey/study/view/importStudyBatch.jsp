<%
/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page import="org.labkey.api.study.DataSet"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.pipeline.DatasetFileReader" %>
<%@ page import="org.labkey.study.pipeline.DatasetImportRunnable" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Date" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<div>
<labkey:errors/>
<%
    BaseStudyController.StudyJspView<StudyController.ImportStudyBatchBean> me = (BaseStudyController.StudyJspView<StudyController.ImportStudyBatchBean>)HttpView.currentView();
    StudyController.ImportStudyBatchBean bean = me.getModelBean();
    DatasetFileReader reader = bean.getReader();
    List<DatasetImportRunnable> runnables = reader.getRunnables();

    boolean hasError = me.getErrors().hasErrors();

%>
The dataset definition file <b><%= h(reader.getDefinitionFile().getName()) %></b> refers to the following dataset files:<br><br>

<table class="labkey-data-region labkey-show-borders">
    <tr><th>#</th><th align="left">Dataset Name</th><th align=left>File</th><th align=left>Action</th><th>Size</th><th>Modified</th></tr><%
int row = 0;
for (DatasetImportRunnable runnable : runnables)
{
    DataSet dataset = runnable.getDatasetDefinition();
    String message = runnable.validate();
    if (message == null)
        message = PageFlowUtil.filter(runnable.getFileName());
    else
    {
        hasError = true;
        message = "<font class=labkey-error>" + PageFlowUtil.filter(message) + "</font>";
    }
    %>
    <tr class="<%= row++ % 2 == 1 ? "labkey-row" : "labkey-alternate-row"%>">
        <td align=right><%= dataset != null ? dataset.getDataSetId() : ""%></td>
        <td><%=dataset != null ? dataset.getLabel() : "Unknown"%></td>
        <td><%=message%></td>
        <td><%=runnable.getAction()%></td>
        <td align="right"><%=runnable.getFile().length() == 0 ? "0" : Math.max(1, runnable.getFile().length() / 1000) %> kb</td>
        <td><%= h(formatDateTime(new Date(runnable.getFile().lastModified()))) %></td>
    </tr><%
}
%></table><%

if (!hasError)
{
    ActionURL submitURL = new ActionURL(StudyController.SubmitStudyBatchAction.class, me.getViewContext().getContainer());
%><form action="<%=submitURL.getLocalURIString()%>" method=POST>
    <input type=hidden name=path value="<%=PageFlowUtil.filter(bean.getPath())%>">
    <%=PageFlowUtil.generateSubmitButton("Start Import")%>
</form><%
}
%>
</div>
