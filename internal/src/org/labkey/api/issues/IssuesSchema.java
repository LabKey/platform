/*
 * Copyright (c) 2006-2016 LabKey Corporation
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

package org.labkey.api.issues;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.TableInfo;

/**
 * User: Tamra Myers
 * Date: Sep 29, 2006
 * Time: 12:44:32 PM
 */
public class IssuesSchema
{
    private static final IssuesSchema instance = new IssuesSchema();
    public static final String SCHEMA_NAME = "issues";
    public static final String ISSUE_DEF_SCHEMA_NAME = "IssueDef";
    public static final String ISSUE_LIST_DEF_TABLE_NAME = "IssueListDef";

    public static IssuesSchema getInstance()
    {
        return instance;
    }

    private IssuesSchema()
    {
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoComments()
    {
        return getSchema().getTable("Comments");
    }

    public TableInfo getTableInfoEmailPrefs()
    {
        return getSchema().getTable("EmailPrefs");
    }

    public TableInfo getTableInfoIssueKeywords()
    {
        return getSchema().getTable("IssueKeywords");
    }

    public TableInfo getTableInfoIssues()
    {
        return getSchema().getTable("Issues");
    }

    public TableInfo getTableInfoCustomColumns()
    {
        return getSchema().getTable("CustomColumns");
    }

    public TableInfo getTableInfoRelatedIssues()
    {
        return getSchema().getTable("RelatedIssues");
    }

    public TableInfo getTableInfoIssueListDef()
    {
        return getSchema().getTable(ISSUE_LIST_DEF_TABLE_NAME);
    }
}
