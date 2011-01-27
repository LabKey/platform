<%
/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
%><%@ page import="org.labkey.api.query.CreateJavaScriptModel" %><%@ page import="org.labkey.api.view.JspView" %><%@ page import="org.labkey.api.view.HttpView" %><%
    JspView<CreateJavaScriptModel> me = (JspView<CreateJavaScriptModel>) HttpView.currentView();
    CreateJavaScriptModel model = me.getModelBean();
    me.getViewContext().getResponse().setContentType("text/plain");
%><script type="text/javascript">
    LABKEY.Query.selectRows({
<%=model.getStandardJavaScriptParameters(8, true)%>
    });

    function onSuccess(results)
    {
        var data = "";
        var length = Math.min(10, results.rows.length);

        // Display first 10 rows in a popup dialog
        for (var idxRow = 0; idxRow < length; idxRow++)
        {
            var row = results.rows[idxRow];

            for (var col in row)
            {
                data = data + row[col].value + " ";
            }

            data = data + "\n";
        }

        alert(data);
    }

    function onError(errorInfo)
    {
        alert(errorInfo.exception);
    }
</script>
