/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.api.exp.api;

import org.labkey.api.data.*;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.exp.PropertyDescriptor;

import java.util.List;

abstract public interface ExpTable<C extends Enum> extends TableInfo
{
    static public final String COLUMN_DATAINPUT_DATAID = "exp.datainput.dataid";
    public Container getContainer();
    public void setContainerFilter(ContainerFilter filter);

    public ColumnInfo addColumn(C column);
    public ColumnInfo addColumn(String alias, C column);
    public ColumnInfo getColumn(C column);
    public ColumnInfo createColumn(String alias, C column);
    public ColumnInfo addColumn(ColumnInfo column);
    // Adds a column so long as there is not already one of that name.
    public boolean safeAddColumn(ColumnInfo column);
    public void addMethod(String name, MethodInfo method);
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
    public void setDefaultVisibleColumns(Iterable<FieldKey> columns);

    public void setEditHelper(TableEditHelper helper);

    public ColumnInfo addPropertyColumns(String domainDescription, PropertyDescriptor[] pds, QuerySchema schema);
}
