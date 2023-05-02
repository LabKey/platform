<%
/*
 * Copyright (c) 2013-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="org.labkey.query.controllers.QueryController.TestDataSourceConfirmAction" %>
<%@ page import="org.labkey.query.controllers.QueryController.TestDataSourceConfirmForm" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    DbScope scope = (DbScope)getModelBean();
    TestDataSourceConfirmForm form = QueryController.getTestDataSourceProperties(scope.getDataSourceName());
%>
<div style="max-width: 1000px;">
<p>
This will test data source <%=h(scope.getDataSourceName())%> by enumerating all schemas and tables in the data source,
querying the row count and all data in the first 100 rows of each table. This operation could take several minutes,
depending on the number of schemas and tables. The server log will show progress as each table is tested.
</p>
<p>
You can exclude specific schemas and tables from testing by adding names to the corresponding text boxes below. These
exclusions will be saved associated with the data source name.
</p>
</div>
<labkey:form action="<%=urlFor(TestDataSourceConfirmAction.class)%>" method="post">
    <input type="hidden" name="dataSource" value="<%=h(scope.getDataSourceName())%>"/>
    <table>
        <tr>
            <td style="padding-right:30px; max-width:150px;">Schemas to exclude, one per line. Appending * to the end of a name acts as a prefix filter.</td>
            <td style="max-width:150px;">Tables to exclude, one per line. Appending * to the end of a name acts as a prefix filter. Specific table names should be schema qualified; prefix filters should not.</td>
        </tr>
        <tr>
            <td style="padding-right:30px;"><textarea style="width:400px; height:150px;" name="excludeSchemas"><%=h(form.getExcludeSchemas())%></textarea></td>
            <td><textarea style="width:400px; height:150px;" name="excludeTables"><%=h(form.getExcludeTables())%></textarea></td>
        </tr>
    </table><br>
    <%=button("Test").submit(true)%>
    <%=button("Cancel").href(urlFor(QueryController.DataSourceAdminAction.class))%>
</labkey:form>