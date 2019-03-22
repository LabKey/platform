/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
import org.labkey.api.module.Module;
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
    public static final String ASSAY_PROVIDERS_TABLE_NAME = "AssayProviders";
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
        super(path, description, user, container, dbSchema, null);
        _targetStudy = targetStudy;
    }

    @Nullable
    public Container getTargetStudy()
    {
        return _targetStudy;
    }

    /** Make it public - protected in superclass */
    public abstract @Nullable TableInfo createTable(String name);

    /** Tack the table type onto the protocol name to create the full table name */
    public static String getLegacyProtocolTableName(ExpProtocol protocol, @NotNull String tableType)
    {
        return protocol.getName() + " " + tableType;
    }

        // UNDONE: need to check permissions here 8449
    @Override
    protected boolean canReadSchema()
    {
        return true;
    }
}
