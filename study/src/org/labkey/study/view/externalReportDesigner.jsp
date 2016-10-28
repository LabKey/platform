<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.study.Dataset"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.reports.ReportsController"%>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.reports.ExternalReport" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<ReportsController.ExternalReportBean> me = (JspView<ReportsController.ExternalReportBean>) HttpView.currentView();
    ReportsController.ExternalReportBean bean = me.getModelBean();
    ExternalReport report = bean.getExtReport();
%>
<labkey:form action="" method="GET" name="reportDesigner">
    Design external report. You can invoke any command line to generate the report. You can use the following
    substitution strings in your command line to identify the source data file and the output file to be generated.
    <ul>
        <li><%=h(ExternalReport.DATA_FILE_SUBST)%> This is the file where the data will be provided in tab delimited format. LabKey Server will generate this file name.</li>
        <li><%=h(ExternalReport.REPORT_FILE_SUBST)%> If your process returns data in a file, it should use the file name substituted here. For text and tab-delimited data,
            your process may return data via stdout instead of via a file. You must specify a file extension for your output file even if the result is returned via stdout.
            This allows LabKey to format the result properly.</li>
    </ul>

    Your code will be invoked by the user who is running the LabKey Server installation. The current directory will be determined by LabKey Server.
    <table>
        <tr>
            <td>Dataset/Query</td>
            <td colspan="3">
                <select name="queryName">
                <%
                    Map<String, DatasetDefinition> datasetMap = bean.getDatasetDefinitions();
                    for (String name : bean.getTableAndQueryNames())
                    {
                        String label = name;
                        Dataset def = datasetMap.get(name);
                        if (def != null)
                        {
                            label = !def.getLabel().equals(def.getName()) ? def.getName() + " (" + def.getLabel() + ")" : def.getLabel();
                        }
                %>
                <option value="<%= h(name) %>"<%=selected(name.equals(report.getQueryName()))%>><%= h(label) %></option>
                <%
                    }
                %>
                </select>
            </td>
        </tr>
        <tr>
            <td>Program</td>
            <td><input name="program" size="50" value="<%=h(report.getProgram())%>"></td>
            <td>&nbsp;Arguments&nbsp;</td>
            <td><input name="arguments" size="50" value="<%=h(report.getArguments())%>"></td>
        </tr>
        <tr>
            <td>Output File Type</td>
            <td >
                <%
                    String ext = report.getFileExtension();
                %>
                <select name="fileExtension">
                    <option value="txt"<%=selected("txt".equals(ext))%>>txt (Plain Text)</option>
                    <option value="tsv"<%=selected("tsv".equals(ext))%>>tsv (Tab Delimited)</option>
                    <option value="jpg"<%=selected("jpg".equals(ext))%>>jpg (JPEG Image)</option>
                    <option value="gif"<%=selected("gif".equals(ext))%>>gif (GIF Image)</option>
                    <option value="png"<%=selected("png".equals(ext))%>>png (PNG Image)</option>
                </select>
            </td>
        </tr>
    </table>

    <%= button("Submit").submit(true) %>
</labkey:form>

