<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
%><%@ page import="org.labkey.api.query.CreateJavaScriptModel" %><%@ page import="org.labkey.api.view.JspView" %><%@ page import="org.labkey.api.view.HttpView" %><%@ page import="org.labkey.api.util.PageFlowUtil" %><%
    JspView<CreateJavaScriptModel> me = (JspView<CreateJavaScriptModel>) HttpView.currentView();
    CreateJavaScriptModel model = me.getModelBean();
    me.getViewContext().getResponse().setContentType("text/plain");
%><script type="text/javascript">
    LABKEY.Query.selectRows({
        schemaName: <%=PageFlowUtil.jsString(model.getSchemaName())%>,
        queryName: <%=PageFlowUtil.jsString(model.getQueryName())%>,
        columns: <%=PageFlowUtil.jsString(model.getColumns())%>,
        successCallback: onSuccess,
        errorCallback: onError,
        requiredVersion: 9.1,  //remove to get the 8.3 response format
        sort: <%=PageFlowUtil.jsString(model.getSort())%>,
        filterArray: <%=model.getFilters()%>
    });

    function onSuccess(results)
    {
        //process the rows
        for(var idxRow = 0; idxRow < results.rows.length; ++idxRow)
        {
            var row = results.rows[idxRow];
            for(var col in row)
            {
                //for the 9.1 extended format,
                //column value is in row[col].value;
                //for the older 8.3 format,
                //column value is in row[col];
            }
        }
    }

    function onError(errorInfo)
    {
        alert(errorInfo.exception);
    }
</script>
