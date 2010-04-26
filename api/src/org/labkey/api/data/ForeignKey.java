/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.util.StringExpression;

/**
 * Interface describing a ColumnInfo's foreign key relationship.
 *
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
    ColumnInfo createLookupColumn(ColumnInfo parent, String displayField);

    /**
     * Return the TableInfo for the foreign table.  This TableInfo can be used to discover the names of available
     * columns in a UI.  The returned TableInfo will not necessarily be one that can be used for querying (e.g. passing
     * to Table.select...
     */
    TableInfo getLookupTableInfo();

    /**
     * Return an URL expression for what the hyperlink for this column should be.  The hyperlink must be able to be
     * constructed knowing only the foreign key value, as other columns may not be available in the ResultSet.
     */
    StringExpression getURL(ColumnInfo parent);

    /** Convenience for getLookupTableInfo.getSelectList(getLookupColumnName()) */
    NamedObjectList getSelectList();

    /**
     * @return The container id of the foreign user schema table.  Null means current container.
     */
    String getLookupContainerId();

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
}
