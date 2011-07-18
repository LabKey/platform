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

import java.util.Set;

/**
 * User: trent
 * Date: 6/28/11
 * Time: 12:28 PM
 */
public class Oracle11gR2Dialect extends Oracle11gR1Dialect
{
    /* Reserved and KeyWords for Oracle 11gR2
       See: http://download.oracle.com/docs/cd/E11882_01/server.112/e17118/ap_keywd.htm#SQLRF022

       2 extra reserved words since R1 - column_value and nested_table_id

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
        Set<String> reservedWords = super.getReservedWords();
        reservedWords.addAll(new CsvSet("column_value, nested_table_id"));

        return reservedWords;
    }
}
