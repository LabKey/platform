<%
    /*
     * Copyright (c) 2008-2022 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<div>
    <labkey:panel title="Report Config Properties">
        When a Jupyter Reports is executed, a config file is generated and populated with properties that may be useful
        to report authors in their script code. The file is written in JSON to : <code>report_config.json.</code><p/>

        A helper utility : <code>ReportConfig.py</code> is included in the <code>nbconfig</code> image. The class contains
        functions that will parse the generated file and return configured properties to your script. An example of
        the code you could use in your script:<br/>
        <pre>
            from ReportConfig import get_report_api_wrapper, get_report_data, get_report_parameters
            print(get_report_data())
            print(get_report_parameters())
        </pre>

        This is an example of a configuration file and the properties that are included.<p/>
        <pre>
            {
              "baseUrl": "http://localhost:8080",
              "contextPath": "/labkey",
              "scriptName": "myReport.ipynb",
              "containerPath": "/my studies/demo",
              "parameters": [
                [
                  "pageId",
                  "study.DATA_ANALYSIS"
                ],
                [
                  "reportType",
                  "ReportService.ipynbReport"
                ],
                [
                  "redirectUrl",
                  "/labkey/my%20studies/demo/project-begin.view?pageId=study.DATA_ANALYSIS"
                ],
                [
                  "reportId",
                  "DB:155"
                ]
              ],
              "version": 1
            }
        </pre>

        <table id="configProperties" class="lk-fields-table">
            <tr>
                <td class="labkey-form-label">baseUrl</td>
                <td>The LabKey server base URL.</td>
            </tr>
            <tr>
                <td class="labkey-form-label">contextPath</td>
                <td>The web application context path (if any).</td>
            </tr>
            <tr>
                <td class="labkey-form-label">scriptName</td>
                <td>The script name that is being executed.</td>
            </tr>
            <tr>
                <td class="labkey-form-label">containerPath</td>
                <td>The LabKey container path that the report is being run from.</td>
            </tr>
            <tr>
                <td class="labkey-form-label">parameters</td>
                <td>An array of parameter names and values of all of the parameters on the current URL.</td>
            </tr>
        </table>
    </labkey:panel>
</div>

