<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.UniqueID" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JavaScriptReportBean bean = (JavaScriptReportBean)getModelBean();

    String uniqueDivName = "div_" + UniqueID.getServerSessionScopedUID();  // Unique div name to support multiple reports per page
%>
<div id="<%=text(uniqueDivName)%>"></div>
<script type="text/javascript">
    (function()
    {
        // ========== Begin report writer's script ==========
        <%=text(bean.script)%>
        // ========== End report writer's script ==========
        if (render && (typeof render === 'function'))
        {
            <%
                if (bean.useGetDataApi)
                {
            %>
            var filterArray = <%=text(bean.model.getJSONFilters())%>;
            var columnArray = <%=text(bean.model.getJSONColumns())%>;

            var getDataConfig = {
                source: {
                    <% if (bean.model.hasContainerFilter()) { %>
                        containerFilter: <%=PageFlowUtil.jsString(bean.model.getContainerFilterTypeName())%>,
                    <% } %>
                    schemaName: LABKEY.SchemaKey.fromString(<%=PageFlowUtil.jsString(bean.model.getSchemaName())%>),
                    queryName: <%=PageFlowUtil.jsString(bean.model.getQueryName())%>
                },
                transforms: []
            };

            if (columnArray != null && columnArray.length > 0) {
                getDataConfig.columns = columnArray;
            }

            if (filterArray != null && filterArray.length > 0) {
                getDataConfig.transforms.push({
                    type: 'aggregate',
                    filters: filterArray
                });
            }
            <%

            if (bean.model.hasQueryParameters())
            {
                // TODO: Add config for query parameters here... but LABKEY.Query.GetData.getRawData() doesn't support query parameters!? #29094
            }

            %>
            render(getDataConfig, document.getElementById("<%=text(uniqueDivName)%>"));
            <%
                }
                else
                {
            %>
            render({
                <%=text(bean.model.getStandardJavaScriptParameters(16, false))%>
            }, document.getElementById("<%=text(uniqueDivName)%>"));
            <%
                }
            %>
        }
        else
        {
            alert("Your script must define a function called 'render'");
        }
    })();
</script>
