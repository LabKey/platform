/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

package org.labkey.api.exp.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.security.permissions.Permission;

abstract public interface ExpTable<C extends Enum> extends ContainerFilterable
{
    public Container getContainer();

    public ColumnInfo addColumn(C column);
    public ColumnInfo addColumn(String alias, C column);
    public ColumnInfo getColumn(C column);
    public ColumnInfo createColumn(String alias, C column);
    public ColumnInfo addColumn(ColumnInfo column);
    // Adds a column so long as there is not already one of that name.
    public boolean safeAddColumn(ColumnInfo column);
    public void setTitleColumn(String titleColumn);

    /**
     * Add a column which can later have its ForeignKey set to be such that the column displays a property value.
     * The returned could will have the integer value of the ObjectId.
     */
    public ColumnInfo createPropertyColumn(String alias);

    public void addCondition(SQLFragment condition, String... columnNames);
    public void addRowIdCondition(SQLFragment rowidCondition);
    public void addLSIDCondition(SQLFragment lsidCondition);

    public void setDetailsURL(DetailsURL detailsURL);

    /** Add the standard set of columns to the table */
    public void populate();

    /** By default, only delete is allowed. Allows specific usages to enable other actions like update */
    public void addAllowablePermission(Class<? extends Permission> permission);

    /**
     * Add columns directly to the table itself, and optionally also as a single column that is a FK to the full set of properties
     * @param domain the domain from which to add all of the properties
     * @param legacyName if non-null, the name of a hidden node to be added as a FK for backwards compatibility
     * @return if a legacyName is specified, the ColumnInfo for the hidden node. Otherwise, null 
     */
    ColumnInfo addColumns(Domain domain, String legacyName);

    public void setDescription(String description);
}
