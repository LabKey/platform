/*
 * Copyright (c) 2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.message;

import org.labkey.api.announcements.CommSchema;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.message.query.MessagePrefsTable;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jan 17, 2011
 * Time: 1:22:34 PM
 */
public class MessageSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "messages";
    public static String MESSAGE_PREFS_TABLE = "MessagePreferencesTable";
    private static final Set<String> TABLE_NAMES;

    static
    {
        Set<String> names = new TreeSet<String>();
        names.add(MESSAGE_PREFS_TABLE);

        TABLE_NAMES = Collections.unmodifiableSet(names);
    }

    static public void register()
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new MessageSchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    public MessageSchema(User user, Container container)
    {
        super(SCHEMA_NAME, "Contains tables for messaging services", user, container, CommSchema.getInstance().getSchema());
    }

    @Override
    protected TableInfo createTable(String name)
    {
        if (MESSAGE_PREFS_TABLE.equalsIgnoreCase(name))
        {
            return new MessagePrefsTable(CoreSchema.getInstance().getTableInfoUsers(), getContainer());
        }
        return null;
    }

    @Override
    public Set<String> getTableNames()
    {
        return TABLE_NAMES;
    }
}
