/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.labkey.api.data.dialect.SqlDialect;

/**
 * Convenience methods to get to tables in the core DbSchema.
 * User: arauch
 * Date: Sep 24, 2005
 * Time: 10:46:35 PM
 */
public class CoreSchema
{
    private static final CoreSchema instance = new CoreSchema();
    private static final String SCHEMA_NAME = "core";

    public static CoreSchema getInstance()
    {
        return instance;
    }

    private CoreSchema()
    {
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbScope getScope()
    {
        return DbScope.getLabKeyScope(); // Don't reference getSchema() here -- it can lead to recursion in certain bootstrap scenarios
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoContainers()
    {
        return getSchema().getTable("Containers");
    }

    public TableInfo getTableInfoPrincipals()
    {
        return getSchema().getTable("Principals");
    }

    public TableInfo getTableInfoMembers()
    {
        return getSchema().getTable("Members");
    }

    public TableInfo getTableInfoPolicies()
    {
        return getSchema().getTable("Policies");
    }

    public TableInfo getTableInfoRoleAssignments()
    {
        return getSchema().getTable("RoleAssignments");
    }

    public TableInfo getTableInfoSqlScripts()
    {
        return getSchema().getTable("SqlScripts");
    }

    public TableInfo getTableInfoModules()
    {
        return getSchema().getTable("Modules");
    }

    public TableInfo getTableInfoUsersData()
    {
        return getSchema().getTable("UsersData");
    }

    public TableInfo getTableInfoLogins()
    {
        return getSchema().getTable("Logins");
    }

    public TableInfo getTableInfoDocuments()
    {
        return getSchema().getTable("Documents");
    }

    public TableInfo getTableInfoUsers()
    {
        return getSchema().getTable("Users");
    }

    public TableInfo getTableInfoActiveUsers()
    {
        return getSchema().getTable("ActiveUsers");
    }

    public TableInfo getTableInfoContacts()
    {
        return getSchema().getTable("Contacts");
    }

    public TableInfo getTableInfoContainerAliases()
    {
        return getSchema().getTable("ContainerAliases");
    }

    public TableInfo getMappedDirectories()
    {
        return getSchema().getTable("MappedDirectories");
    }

    public TableInfo getTableInfoMvIndicators()
    {
        return getSchema().getTable("MvIndicators");
    }

    public TableInfo getTableInfoDbSequences()
    {
        return getSchema().getTable("DbSequences");
    }

    public TableInfo getTableInfoShortURL()
    {
        return getSchema().getTable("ShortURL");
    }

    public TableInfo getTableInfoReport()
    {
        return getSchema().getTable("Report");
    }

    public TableInfo getTableInfoNotifications()
    {
        return getSchema().getTable("Notifications");
    }

    public TableInfo getTableInfoQCState()
    {
        return getSchema().getTable("QCState");
    }

    public TableInfo getTableAPIKeys()
    {
        return getSchema().getTable("APIKeys");
    }
}
