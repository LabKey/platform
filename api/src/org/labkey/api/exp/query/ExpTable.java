/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.permissions.Permission;

import java.util.Set;

public interface ExpTable<C extends Enum> extends ContainerFilterable, TableInfo
{
    Container getContainer();

    MutableColumnInfo addColumn(C column);
    MutableColumnInfo addColumn(String alias, C column);
    ColumnInfo getColumn(C column);
    MutableColumnInfo createColumn(String alias, C column);
    MutableColumnInfo addColumn(MutableColumnInfo column);
    // Adds a column so long as there is not already one of that name.
    boolean safeAddColumn(MutableColumnInfo column);
    void setTitleColumn(String titleColumn);

    /**
     * Add a column which can later have its ForeignKey set to be such that the column displays a property value.
     * The returned could will have the integer value of the ObjectId.
     */
    ColumnInfo createPropertyColumn(String alias);

    /**
     * Add an unselectable column for each of the Vocabulary domains that are
     * in scope (current container, project, and shared.)
     */
    void addVocabularyDomains();

    void addCondition(SQLFragment condition, FieldKey... fieldKeys);
    void addRowIdCondition(SQLFragment rowidCondition);
    void addLSIDCondition(SQLFragment lsidCondition);

    void setDetailsURL(DetailsURL detailsURL);
    void setInsertURL(DetailsURL insertURL);
    void setUpdateURL(DetailsURL updateURL);
    void setImportURL(DetailsURL importURL);

    /**
     * Add the standard set of columns to the table
     */
    void populate();
    void markPopulated();

    /**
     * By default, only delete is allowed. Allows specific usages to enable other actions like update
     */
    void addAllowablePermission(Class<? extends Permission> permission);

    /**
     * Add columns directly to the table itself, and optionally also as a single column that is a FK to the full set of properties
     *
     * @param domain     the domain from which to add all of the properties
     * @param legacyName if non-null, the name of a hidden node to be added as a FK for backwards compatibility
     * @return if a legacyName is specified, the ColumnInfo for the hidden node. Otherwise, null
     */
    default MutableColumnInfo addColumns(Domain domain, @Nullable String legacyName)
    {
        return addColumns(domain, legacyName, null);
    }

    MutableColumnInfo addColumns(Domain domain, @Nullable String legacyName,@Nullable ContainerFilter cf);


    void setTitle(String title);

    void setDescription(String description);

    void setDomain(Domain domain);

    /**
     * Set the SchemaKey encoded name for this schema.
     * Allows experiment-based tables to be exposed in other schemas, such as samples sets being exposed in the "samples" schema.
     */
    void setPublicSchemaName(String schemaName);

    void checkLocked();

    /* returns null or MutableColumnInfo, will throw if column exists and is locked */
    default MutableColumnInfo getMutableColumn(@NotNull C c)
    {
        checkLocked();
        ColumnInfo col = getColumn(c);
        if (null == col)
            return null;
        ColumnInfo.checkIsMutable(col);
        return (MutableColumnInfo) col;
    }

    default void setFilterPatterns(String columnName, String... patterns)
    {
        // by default we do nothing
    }


    /** returns a column that wraps objectid, this is only required to support the expObject() table method */
    default ColumnInfo getExpObjectColumn()
    {
        return null;
    }

    @Nullable default Set<String> getAltMergeKeys(DataIteratorContext context)
    {
        return null;
    }
}
