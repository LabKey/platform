<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<%@ page import="java.util.List"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.pipeline.DatasetBatch" %>
<%@ page import="org.labkey.study.controllers.BaseStudyController" %>
<%@ page import="org.labkey.study.model.DataSetDefinition" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<div>
<labkey:errors/>
<%
    BaseStudyController.StudyJspView<StudyController.ImportStudyBatchBean> me = (BaseStudyController.StudyJspView<StudyController.ImportStudyBatchBean>)HttpView.currentView();
    StudyController.ImportStudyBatchBean bean = me.getModelBean();
    DatasetBatch studyBatch = bean.getBatch();
    List<DatasetBatch.DatasetImportJob> jobs = studyBatch.getJobs();

    boolean hasError = me.getErrors().hasErrors();

%><table><tr><th>&nbsp;</th><th align=left>action</th><th align=left>dataset</th><th align=left>file</th></tr><%
for (DatasetBatch.DatasetImportJob job : jobs)
{
    DataSetDefinition dataset = job.getDatasetDefinition();
    String message = job.validate();
    if (message == null)
        message = PageFlowUtil.filter(job.getFileName());
    else
    {
        hasError = true;
        message = "<font class=labkey-error>" + PageFlowUtil.filter(message) + "</font>";
    }
    %><tr>
    <td align=right><%= dataset != null ? dataset.getDataSetId() : ""%></td>
    <td><%=job.getAction()%></td>
    <td><%=dataset != null ? dataset.getLabel() : "Unknown"%></td>
    <td><%=message%></td>
    </tr><%
}
%></table><%

if (!hasError)
{
    ActionURL submitURL = new ActionURL(StudyController.SubmitStudyBatchAction.class, me.getViewContext().getContainer());
%><form action="<%=submitURL.getLocalURIString()%>" method=POST>
    <input type=hidden name=path value="<%=PageFlowUtil.filter(bean.getForm().getPath())%>">
    <%=PageFlowUtil.generateSubmitButton("Submit")%>
</form><%
}
%>
</div>
