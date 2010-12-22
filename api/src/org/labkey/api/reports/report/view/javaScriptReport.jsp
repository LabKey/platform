<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.api.reports.report.JavaScriptReport.JavaScriptReportBean" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    HttpView<JavaScriptReportBean> me = (HttpView<JavaScriptReportBean>) HttpView.currentView();
    JavaScriptReportBean bean = me.getModelBean();
    String uniqueName = "foo";
    String containingFunctionName = "cf_" + uniqueName;
%>
<div id="<%=uniqueName%>"></div>    <% // TODO: Make uniqueName unique to support multiple JavaScript reports on a page %>
<script language="javascript" type="text/javascript">
    <%=containingFunctionName%>(document.getElementById("<%=uniqueName%>"));

    function <%=containingFunctionName%>(div)
    {
        if (typeof render == 'function')
        {
            var query = {
<%=bean.model.getStandardJavaScriptParameters(16)%>
            };

            render(query, div);
        }
        else
        {
            alert("Your script must define a function called 'render'");
        }

        <%=bean.script%>
    }
</script>
