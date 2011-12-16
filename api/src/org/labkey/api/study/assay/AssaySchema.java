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
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

/**
 * User: jeckels
 * Date: May 25, 2009
 */
public abstract class AssaySchema extends UserSchema
{
    private final ExpProtocol _protocol;
    protected Container _targetStudy;

    public static final String ASSAY_LIST_TABLE_NAME = "AssayList";
    public static String NAME = "assay";
    public static final String DESCR = "Contains data about the set of defined assays and their associated batches and runs.";

    public AssaySchema(String name, User user, Container container, DbSchema dbSchema, ExpProtocol protocol)
    {
        super(name, DESCR, user, container, dbSchema);
        _protocol = protocol;
    }

    public void setTargetStudy(Container studyContainer)
    {
        _targetStudy = studyContainer;
    }

    public Container getTargetStudy()
    {
        return _targetStudy;
    }

    public ExpProtocol getProtocol()
    {
        return _protocol;
    }
    
    /** Make it public - protected in superclass */
    public abstract TableInfo createTable(String name);

    public static String getBatchesTableName(ExpProtocol protocol)
    {
        return getProviderTableName(protocol, "Batches");
    }

    public static String getRunsTableName(ExpProtocol protocol)
    {
        return getProviderTableName(protocol, "Runs");
    }

    public static String getResultsTableName(ExpProtocol protocol)
    {
        return getProviderTableName(protocol, "Data");
    }

    public static String getQCFlagTableName(ExpProtocol protocol)
    {
        return getProviderTableName(protocol, "QCFlags");
    }

    /** Tack the table type onto the protocol name to create the full table name */
    public static String getProviderTableName(ExpProtocol protocol, @NotNull String tableType)
    {
        return protocol.getName() + " " + tableType;
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
        return null;
    }
}
