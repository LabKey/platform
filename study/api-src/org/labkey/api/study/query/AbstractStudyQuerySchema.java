/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.study.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;

/**
 * The study schema as seen by tables implemented outside the study module (e.g., specimen tables)
 */
public abstract class AbstractStudyQuerySchema extends UserSchema implements UserSchema.HasContextualRoles
{
    protected AbstractStudyQuerySchema(@NotNull SchemaKey path, @Nullable String description, @NotNull User user, @NotNull Container c, DbSchema dbSchema)
    {
        super(path, description, user, c, dbSchema, null);
    }

    public abstract @Nullable Study getStudy();

    /** for tables that support container filter, should they turn on support or not */
    public boolean allowSetContainerFilter()
    {
        return true;
    }

    public abstract TableInfo createLocationLookupTable(@Nullable ContainerFilter cf);

    /**
     * @param rootTableColumnName PrimaryTypeId, DerivativeTypeId, DerivativeTypeId2, or AdditiveTypeId
     */
    public abstract TableInfo createSpecimenTypeLookupTable(String rootTableColumnName, ContainerFilter cf);

    public abstract TableInfo createSpecimenDetailLookupTable(@Nullable ContainerFilter cf);
}
