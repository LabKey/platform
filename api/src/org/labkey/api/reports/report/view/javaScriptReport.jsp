<%
/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
%><%@ page import="org.labkey.api.reports.report.JavaScriptReport.JavaScriptReportBean"
%><%@ page extends="org.labkey.api.jsp.JspBase"
%>
<%
    JavaScriptReportBean bean = (JavaScriptReportBean)getModelBean();
    String uniqueDivName = "div_" + getRequestScopedUID();  // Unique div name to support multiple reports per page
%>
<div id="<%=uniqueDivName%>"></div>
<script language="javascript" type="text/javascript">
    (function()
    {
        if (typeof render == 'function')
        {
            var query = {
<%=bean.model.getStandardJavaScriptParameters(16, false)%>
            };

            render(query, document.getElementById("<%=uniqueDivName%>"));
        }
        else
        {
            alert("Your script must define a function called 'render'");
        }

// ========== Start of report writer's script ==========
<%=bean.script%>
// ========== End of report writer's script ==========
    })();
</script>
