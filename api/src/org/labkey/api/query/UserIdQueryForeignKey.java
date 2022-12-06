/*
 * Copyright (c) 2010-2019 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
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

    public UserIdQueryForeignKey(QuerySchema sourceSchema)
    {
        this(sourceSchema, false);
    }

    /** @param includeAllUsers if true, don't filter to users who are members of the current project, etc. Useful for
     * automatically-populated columns like CreatedBy and ModifiedBy, where you want to see if the user even if they
     * no longer have permission to access the container */
    public UserIdQueryForeignKey(QuerySchema sourceSchema, boolean includeAllUsers)
    {
        super(sourceSchema, null, "core", sourceSchema.getContainer(), null, sourceSchema.getUser(), includeAllUsers ? "SiteUsers" : "Users", "UserId", "DisplayName");
        _includeAllUsers = includeAllUsers;
        setShowAsPublicDependency(false);
    }

    public UserIdQueryForeignKey(QuerySchema sourceSchema, ContainerFilter cf, User user, Container container, boolean includeAllUsers)
    {
        super (sourceSchema, cf, "core", container, null, user, includeAllUsers ? "SiteUsers" : "Users", "UserId", "DisplayName");
        _includeAllUsers = includeAllUsers;
    }

    @Deprecated // TODO ContainerFilter
    public UserIdQueryForeignKey(User user, Container container, boolean includeAllUsers)
    {
        this(DefaultSchema.get(user,container), null, user, container, includeAllUsers);
    }

    @Override
    public TableInfo getLookupTableInfo()
    {
        if (_table == null && getSchema() != null)
        {
            String cacheKey = this.getClass().getName() + "/" + _includeAllUsers;
            _table = ((UserSchema) getSchema()).getCachedLookupTableInfo(cacheKey, this::createLookupTableInfo);
        }
        return _table;
    }

    private TableInfo createLookupTableInfo()
    {
        TableInfo ret = ((UserSchema) getSchema()).getTable(_tableName, getLookupContainerFilter(), true, true);
        if (null == ret)
            return null;

        if (_includeAllUsers)
        {
            // Clear out the filter that might be preventing us from resolving the lookup if the user list is being filtered
            FilteredTable<UserSchema> table = (FilteredTable<UserSchema>) ret;
            table.clearConditions(FieldKey.fromParts("UserId"));
        }
        ret.setLocked(true);
        return ret;
    }

    /**
     * set foreign key and display column
     * Deprecated: relying on UserIdColumnInfoTransformer is preferred()

     */
    @Deprecated
    static public ColumnInfo initColumn(QuerySchema sourceSchema, MutableColumnInfo column, boolean guestAsBlank)
    {
        boolean showAllUsers = column.getName().equalsIgnoreCase("createdby") || column.getName().equalsIgnoreCase("modifiedby");
        column.setFk(new UserIdQueryForeignKey(sourceSchema, showAllUsers));
        column.setDisplayColumnFactory(guestAsBlank ? _factoryBlank : _factoryGuest);
        return column;
    }

    public static final DisplayColumnFactory _factoryBlank = colInfo -> new UserIdRenderer.GuestAsBlank(colInfo);
    public static final DisplayColumnFactory _factoryGuest = colInfo -> new UserIdRenderer(colInfo);
}
