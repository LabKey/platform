/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.bigiron.oracle;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.Sets;

import java.util.Set;

/**
 * User: trent
 * Date: 6/28/11
 * Time: 12:46 PM
 */
public class Oracle11gR1Dialect extends OracleDialect
{

    /* Reserved and KeyWords for Oracle 11gR1
       See: http://download.oracle.com/docs/cd/B28359_01/server.111/b28286/ap_keywd.htm#SQLRF022

       On keywords:

       Oracle SQL keywords are not reserved. However, Oracle uses them internally in specific ways. Therefore, if you
       use these words as names for objects and object parts, then your SQL statements may be more difficult to read
       and may lead to unpredictable results.

       You can obtain a list of keywords by querying the V$RESERVED_WORDS data dictionary view. All keywords in the
       view that are not listed as always reserved or reserved for a specific use are Oracle SQL keywords. Refer to
       Oracle Database Reference for more information
    */
    @Override
    protected @NotNull Set<String> getReservedWords()
    {
        return Sets.newCaseInsensitiveHashSet(new CsvSet(
            "access, add, all, alter, and, any, as, asc, audit, between, by, char, check, cluster, column, " +
            "comment, compress, connect, create, current, date, decimal, default, delete, desc, distinct, drop, else, " +
            "exclusive, exists, file, float, for, from, grant, group, having, identified, immediate, in, increment, " +
            "index, initial, insert, integer, intersect, into, is, level, like, lock, long, maxextents, minus, mlslabel, " +
            "mode, modify, noaudit, nocompress, not, nowait, null, number, of, offline, on, online, " +
            "option, or, order, pctfree, prior, privileges, public, raw, rename, resource, revoke, row, rowid, rownum, " +
            "rows, select, session, set, share, size, smallint, start, successful, synonym, sysdate, table, then, to, " +
            "trigger, uid, union, unique, update, user, validate, values, varchar, varchar2, view, whenever, where, with"));
    }
}
