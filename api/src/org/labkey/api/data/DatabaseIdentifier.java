/*
 * Copyright (c) 2025-2026 LabKey Corporation
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

/**
 * Historically we've stored Column.getAlias() as a String.  Unfortunately, setAlias() was often disconnected from
 * what a legal identifier looks like in the underlying database.  Also, the responsibility for deciding how to
 * format the identifier (e.g. quote/don't quote, truncate/don't truncate, etc) was left to the for SQL writer,
 * and we relied on everyone one making the same decision to get everything to match up.  Possible problems could be
 * one code path adding quotes and one not.  In postgres, this could create a case mismatch (unquoted identifiers are
 * silently converted to lower-case. We also rely heavily on case-insensitive maps to gloss over some sloppy-ness.
 * <br>
 * NOTE:
 *      Postgres91Dialect.getSelectNameFromMetaDataName() would quote names with uppercase chars
 *      Postgres91Dialect.shouldQuote() would not quote names with uppercase chars
 * While not exactly wrong, it can be hard to know which rules apply where, and things like this indirectly or directly
 * contribute to making the code fragile.
 * <br>
 * Anyway, the goal of this class is 1) to make sure any string intended as a database identifier is created once
 * in SqlDialect validated way 2) it is easy to use consistently 3) they are constructed by AliasManager or SqlDialect
 * (for ColumnInfo.getMetaDataName()) to help enforce best practice wrt creating unique aliases in a given scope.
 * <br>
 * NOTE: This should greatly reduce the need for the AliasManager.legalNameFromName() shenanigans.  However,
 * that can be dealt with after the initial refactor.
 */
public interface DatabaseIdentifier
{
    String getId();         // Unencoded identifier (e.g. how it is reported in schema or resultset metadata)
    SQLFragment getSql();   // Valid SQL reference for this identifier
}
