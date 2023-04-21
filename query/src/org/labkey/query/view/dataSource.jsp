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
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.DbSchemaType" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.data.Results" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.util.StringUtilsLabKey" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    DbScope scope = (DbScope)getModelBean();
    Collection<String> schemaNames = scope.getSchemaNames();

    for (String schemaName : schemaNames)
    {
        if ("SYS".equals(schemaName)) // Bad schema in Oracle -- querying its table names never returns
            continue;
        DbSchema schema = scope.getSchema(schemaName, DbSchemaType.Bare);
        %><%=h(schemaName)%> has <%=h(StringUtilsLabKey.pluralize(schema.getTableNames().size(), "table"))%><br><%
        for (String tableName : schema.getTableNames())
        {
            TableInfo table = schema.getTable(tableName);
            %>&nbsp;&nbsp;<%=h(tableName)%><%

            if (tableName.startsWith("SYS_IOT_OVER_")) // Bad tables in Oracle
            {
                %> SKIPPED (Appears to be an overflow table of an index-organized table)<br><%
            }
            else
            {
                System.out.print(schemaName + "." + tableName + ": ");
                TableSelector selector = new TableSelector(table);
                long count;
                try
                {
                    count = selector.getRowCount();
                }
                catch (Exception e)
                {
                    %> COUNT FAILED: <%=h(e.getMessage())%><%
                    continue;
                }

                System.out.println(StringUtilsLabKey.pluralize(count, "row"));
                %> has <%=h(StringUtilsLabKey.pluralize(count, "row"))%><%
                selector.setMaxRows(100);
                try
                {
                    try (Results results = selector.getResults())
                    {
                        while (results.next());
                    }
                    %><br><%
                }
                catch (Exception e)
                {
                    %> RESULTS FAILED: <%=h(e.getMessage())%><br><%
                }
            }
        }
    }
%>
