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
package org.labkey.api.announcements;

import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.TableInfo;

/**
 * User: arauch
 * Date: Sep 24, 2005
 * Time: 10:46:35 PM
 */
public class CommSchema
{
    private static final CommSchema instance = new CommSchema();
    private static final String SCHEMA_NAME = "comm";

    public static CommSchema getInstance()
    {
        return instance;
    }

    private CommSchema()
    {
    }

    public String getSchemaName()
    {
        return SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME, DbSchemaType.Module);
    }

    public SqlDialect getSqlDialect()
    {
        return getSchema().getSqlDialect();
    }

    public TableInfo getTableInfoAnnouncements()
    {
        return getSchema().getTable("Announcements");
    }

    public TableInfo getTableInfoThreads()
    {
        return getSchema().getTable("Threads");
    }

    public TableInfo getTableInfoMemberList()
    {
        return getSchema().getTable("UserList");  // TODO: Change table name to MemberList?
    }

    public TableInfo getTableInfoPages()
    {
        return getSchema().getTable("Pages");
    }

    public TableInfo getTableInfoPageVersions()
    {
        return getSchema().getTable("PageVersions");
    }

    public TableInfo getTableInfoEmailPrefs()
    {
        return getSchema().getTable("EmailPrefs");
    }

    public TableInfo getTableInfoEmailOptions()
    {
        return getSchema().getTable("EmailOptions");
    }

    public TableInfo getTableInfoEmailFormats()
    {
        return getSchema().getTable("EmailFormats");
    }

    public TableInfo getTableInfoUserList()
    {
        return getSchema().getTable("UserList");
    }

    public TableInfo getTableInfoRSSFeeds()
    {
        return getSchema().getTable("RSSFeeds");
    }

    public TableInfo getTableInfoTours()
    {
        return getSchema().getTable("Tours");
    }
}

