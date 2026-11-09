/*
 * Copyright (c) 2020-2026 LabKey Corporation
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


import java.util.List;

/* This is a small subset of TableInfo that gives only basic information about a table, this might be provided by xml or a foreignkey.
 * Importantly, it does not require generating any column lists or ColumnInfo objects.
 */
public interface TableDescription
{
    boolean isPublic();

    String getPublicName();

    // CONSIDER replace with (or add) getPublicSchemaKey()
    /** @return The public (queryable) schema name in SchemaKey encoding. */
    String getPublicSchemaName();

    String getName();

    /** @return the default display value for this table if it's the target of a foreign key */
    String getTitleColumn();

    List<String> getPkColumnNames();
}
