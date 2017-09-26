/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

import java.util.Map;
import java.util.Set;

/**
 * Interface describing a ColumnInfo's foreign key relationship, which might be a "real" FK in the underlying
 * database of a "soft" FK, making it a lookup to the foreign key's target.
 */
public interface ForeignKey
{
    /**
     * Return a new lookup column with the specified displayField.  If displayField is null, then this method
     * should return the title column (default display field) of the foreign table.
     * It should return null if there is no default display field.
     * The ColumnInfo parent is a column which has the value of the foreign key.
     * The parentTable of the returned ColumnInfo must be the same as the parentTable of the passed in column.
     */
    @Nullable
    ColumnInfo createLookupColumn(ColumnInfo parent, String displayField);

    /**
     * Return the TableInfo for the foreign table.  This TableInfo can be used to discover the names of available
     * columns in a UI.  The returned TableInfo will not necessarily be one that can be used for querying (e.g. passing
     * to Table.select...
     */
    @Nullable
    TableInfo getLookupTableInfo();

    /**
     * Return an URL expression for what the hyperlink for this column should be.  The hyperlink must be able to be
     * constructed knowing only the foreign key value, as other columns may not be available in the ResultSet.
     */
    StringExpression getURL(ColumnInfo parent);

    /**
     * Convenience for getLookupTableInfo.getSelectList(getLookupColumnName())
     */
    NamedObjectList getSelectList(RenderContext ctx);

    /**
     * @return The container id of the foreign user schema table.  Null means current container.
     */
    Container getLookupContainer();

    /**
     * Just for introspection.
     * @return The name of the foreign user schema table.
     */
    String getLookupTableName();

    /**
     * Just for introspection.
     * @return The name of the foreign user schema table.
     */
    String getLookupSchemaName();

    /**
     * Just for introspection.
     * @return The name of the column in the foreign user schema table.
     */
    String getLookupColumnName();

    /**
     * Just for introspection.
     * @return The name of the display column in the foreign user schema table.
     */
    String getLookupDisplayName();

    /**
     * Fixup any references fo FieldKeys that may have been reparented or renamed by Query and
     * generate a new ForeignKey.  If fixup is not needed, return null.
     *
     * @param parent A new parent FieldKey to inject, e.g. "title" becomes "parent/title".
     * @param mapping Rename FieldKeys, e.g. "foo" becomes "bar".
     * @return Clone of original ForeignKey with updated FieldKey substitutions.
     */
    @Nullable
    ForeignKey remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> mapping);

    /**
     * Suggest a set of FieldKeys from the parent table that may be needed when resolving
     * the lookup or display URL.
     * @return A set of suggested columns
     */
    @Nullable
    Set<FieldKey> getSuggestedColumns();

    /**
     * Return true if this ForeignKey could be imported by alternate key value, meaning a unique display value or similar
     * instead of requiring the target table's primary key value.
     */
    default boolean allowImportByAlternateKey() { return false; }

    default void propagateContainerFilter(ColumnInfo parent, TableInfo table)
    {
        if (table.supportsContainerFilter() && parent.getParentTable().getContainerFilter() != null)
        {
            ContainerFilterable newTable = (ContainerFilterable)table;

            // Only override if the new table doesn't already have some special filter
            if (newTable.hasDefaultContainerFilter())
                newTable.setContainerFilter(new DelegatingContainerFilter(parent.getParentTable(), true));
        }
    }


}
