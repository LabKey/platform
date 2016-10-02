/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.data.*;
import org.labkey.api.security.User;

/**
 * Foreign key class for use with Query and the 'core'
 * User Schema. Use this when setting FKs on AbstractTables
 * User: Dave
 * Date: Jul 28, 2008
 */
public class UserIdQueryForeignKey extends QueryForeignKey
{
    private final boolean _includeAllUsers;

    public UserIdQueryForeignKey(User user, Container container)
    {
        this(user, container, false);
    }

    /** @param includeAllUsers if true, don't filter to users who are members of the current project, etc. Useful for
     * automatically-populated columns like CreatedBy and ModifiedBy, where you want to see if the user even if they
     * no longer have permission to access the container */
    public UserIdQueryForeignKey(User user, Container container, boolean includeAllUsers)
    {
        super("core", container, null, user, includeAllUsers ? "SiteUsers" : "Users", "UserId", "DisplayName");
        _includeAllUsers = includeAllUsers;
    }

    @Override
    public TableInfo getLookupTableInfo()
    {
        TableInfo result = super.getLookupTableInfo();
        if (_includeAllUsers)
        {
            // Clear out the filter that might be preventing us from resolving the lookup if the user list is being filtered
            FilteredTable table = (FilteredTable)result;
            if (table == null)
            {
                // Exception 23740
                throw new IllegalStateException("Failed to find lookup target " + getLookupSchemaName() + "." + getLookupTableName() + " in container " + getLookupContainer());
            }
            table.clearConditions(FieldKey.fromParts("UserId"));
        }
        return result;
    }

    /* set foreign key and display column */
    static public ColumnInfo initColumn(User user, Container container, ColumnInfo column, boolean guestAsBlank)
    {
        boolean showAllUsers = column.getName().equalsIgnoreCase("createdby") || column.getName().equalsIgnoreCase("modifiedby");
        column.setFk(new UserIdQueryForeignKey(user, container, showAllUsers));
        column.setDisplayColumnFactory(guestAsBlank ? _factoryBlank : _factoryGuest);
        return column;
    }

    public static final DisplayColumnFactory _factoryBlank = colInfo -> new UserIdRenderer.GuestAsBlank(colInfo);
    public static final DisplayColumnFactory _factoryGuest = colInfo -> new UserIdRenderer(colInfo);
}
