/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;

/**
 * Convenience class for configuring a lookup to the user schema/query core.containers.
 * User: Karl Lum
 * Date: Nov 2, 2007
 */
public class ContainerForeignKey extends QueryForeignKey
{
    /**
     * @deprecated relying on ContainerIdColumnInfoTransformer is preferred()
     */
     @Deprecated
    static public <COL extends MutableColumnInfo> COL initColumn(COL column, UserSchema schema)
    {
        return initColumn(column, schema, null);
    }


    /**
     * @deprecated relying on ContainerIdColumnInfoTransformer is preferred()
     */
    @Deprecated
    static public <COL extends MutableColumnInfo> COL initColumn(@NotNull COL column, UserSchema schema, final ActionURL url)
    {
        column.setFk(new ContainerForeignKey(schema));
        column.setUserEditable(false);
        column.setShownInInsertView(false);
        column.setShownInUpdateView(false);
        column.setReadOnly(true);
        column.setDisplayColumnFactory(ContainerDisplayColumn.FACTORY);
        if (url != null)
            column.setURL(new DetailsURL(url));
        return column;
    }

    public ContainerForeignKey(UserSchema schema)
    {
        super(schema, new ContainerFilter.InternalNoContainerFilter(), "core", schema.getContainer(), null, schema.getUser(), "Containers", "EntityId", "DisplayName");
        setShowAsPublicDependency(false);
    }

    public ContainerForeignKey(UserSchema schema, ContainerFilter cf)
    {
        super(schema, cf, "core", schema.getContainer(), null, schema.getUser(), "Containers", "EntityId", "DisplayName");
        setShowAsPublicDependency(false);
    }
}
