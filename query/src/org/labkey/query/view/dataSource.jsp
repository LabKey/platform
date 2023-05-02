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
<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.DbSchemaType" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.data.Results" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.util.StringUtilsLabKey" %>
<%@ page import="org.labkey.api.util.logging.LogHelper" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    private static final Logger LOG = LogHelper.getLogger(QueryController.TestDataSourceAction.class, "Output from data source test");

    // TODO: This is temporary... planning to move configuration of these to datasource-specific properties that can be edited & saved via the form
    Set<String> skipSchemas = Set.of("SYS");
    Set<String> skipSchemaPrefixes = Set.of();
    Set<String> skipTables = Set.of("MDSYS.ALL_ANNOTATION_TEXT_METADATA", "MDSYS.ALL_SDO_CSW_SERVICE_INFO", "DVSYS.DBA_DV_USER_PRIVS_ALL", "DVSYS.DBA_DV_USER_PRIVS", "XDB.XDB$STATS");
    Set<String> skipTablePrefixes = Set.of("SYS_IOT_OVER_");
%>
<%
    DbScope scope = (DbScope)getModelBean();
    LOG.info("Started test of data source " + scope.getDataSourceName());
    Collection<String> schemaNames = scope.getSchemaNames();

    for (String schemaName : schemaNames)
    {
        if (skipSchemas.contains(schemaName) || skipSchemaPrefixes.stream().anyMatch(schemaName::startsWith))
        {
            LOG.info("Schema " + schemaName + " SKIPPED");
            %>Schema <%=h(schemaName)%> SKIPPED<br><%
        }
        else
        {
            LOG.info("Schema " + schemaName + ":");
            DbSchema schema = scope.getSchema(schemaName, DbSchemaType.Bare);
            %>Schema <%=h(schemaName)%> has <%=h(StringUtilsLabKey.pluralize(schema.getTableNames().size(), "table"))%><br><%
            List<String> tableNames = new ArrayList<>(schema.getTableNames());
            tableNames.sort(String.CASE_INSENSITIVE_ORDER);

            for (String tableName : tableNames)
            {
                String fullName = schemaName + "." + tableName;
                if (skipTables.contains(fullName) || skipTablePrefixes.stream().anyMatch(tableName::startsWith))
                {
                    LOG.info("Table " + fullName + " SKIPPED");
                    %>&nbsp;&nbsp;<%=h(tableName)%> SKIPPED<br><%
                    continue;
                }
                TableInfo table = schema.getTable(tableName);
                %>&nbsp;&nbsp;<%=h(tableName)%><%

                LOG.info("Table " + fullName + ":");
                TableSelector selector = new TableSelector(table);
                long count;
                try
                {
                    count = selector.getRowCount();
                }
                catch (Exception e)
                {
                    %> COUNT FAILED: <%=h(e.getMessage())%><br><%
                    continue;
                }

                LOG.info(StringUtilsLabKey.pluralize(count, "row"));
                %> has <%=h(StringUtilsLabKey.pluralize(count, "row"))%><%
                selector.setMaxRows(100);
                try
                {
                    int rowCount = 0;
                    try (Results results = selector.getResults())
                    {
                        while (results.next())
                            rowCount++;
                    }
                    LOG.info(StringUtilsLabKey.pluralize(rowCount, "row") + " read");
                    %><br><%
                }
                catch (Throwable t)
                {
                    %> RESULTS FAILED: <%=h(t.getMessage())%><br><%
                }
            }
        }
    }

    LOG.info("Completed test of data source " + scope.getDataSourceName());
%>
