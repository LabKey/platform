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
package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

/**
 * User: jeckels
 * Date: May 25, 2009
 */
public abstract class AssaySchema extends UserSchema
{
    public static final String ASSAY_LIST_TABLE_NAME = "AssayList";
    public static String NAME = "assay";
    private static final String DESCR = "Contains data about the set of defined assays and their associated batches and runs.";

    @Nullable
    protected Container _targetStudy;

    public AssaySchema(String name, User user, Container container, DbSchema dbSchema, @Nullable Container targetStudy)
    {
        this(SchemaKey.fromParts(name), DESCR, user, container, dbSchema, targetStudy);
    }

    protected AssaySchema(SchemaKey path, String description, User user, Container container, DbSchema dbSchema, @Nullable Container targetStudy)
    {
        super(path, description, user, container, dbSchema);
        _targetStudy = targetStudy;
    }

    @Nullable
    public Container getTargetStudy()
    {
        return _targetStudy;
    }

    /** Make it public - protected in superclass */
    public abstract TableInfo createTable(String name);

    public static String getBatchesTableName(ExpProtocol protocol, boolean protocolPrefixed)
    {
        return getProviderTableName(protocol, "Batches", protocolPrefixed);
    }

    public static String getRunsTableName(ExpProtocol protocol, boolean protocolPrefixed)
    {
        return getProviderTableName(protocol, "Runs", protocolPrefixed);
    }

    public static String getResultsTableName(ExpProtocol protocol, boolean protocolPrefixed)
    {
        return getProviderTableName(protocol, "Data", protocolPrefixed);
    }

    public static String getQCFlagTableName(ExpProtocol protocol, boolean protocolPrefixed)
    {
        return getProviderTableName(protocol, "QCFlags", protocolPrefixed);
    }

    /** Tack the table type onto the protocol name to create the full table name */
    public static String getProviderTableName(ExpProtocol protocol, @NotNull String tableType, boolean protocolPrefixed)
    {
        if (protocolPrefixed)
            return protocol.getName() + " " + tableType;
        else
            return tableType;
    }

    public static String getProviderTableName(ExpProtocol protocol, @NotNull String tableType)
    {
        return getProviderTableName(protocol, tableType, false);
    }

    /** Strip off the provider name to determine the specific assay table type (runs, batches, etc) */
    public static String getProviderTableType(ExpProtocol protocol, @NotNull String tableName)
    {
        String lname = tableName.toLowerCase();
        String protocolPrefix = protocol.getName().toLowerCase() + " ";
        if (lname.startsWith(protocolPrefix))
        {
            return tableName.substring(protocolPrefix.length());
        }
        
        // This wasn't a table associated with the given protocol
        // XXX: maybe?
        return null;
    }

        // UNDONE: need to check permissions here 8449
    @Override
    protected boolean canReadSchema()
    {
        return true;
    }
}
