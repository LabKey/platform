/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
package org.labkey.bigiron.mysql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CsvSet;

import java.util.Set;

/**
 * User: adam
 * Date: 3/19/2014
 * Time: 7:49 AM
 */
public class MySql56Dialect extends MySqlDialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        // Add new reserved words in MySQL 5.6; see http://dev.mysql.com/doc/refman/5.6/en/keywords.html
        Set<String> words = super.getReservedWords();

        // NOTE: ONE_SHOT, SQL_AFTER_GTIDS, and SQL_BEFORE_GTIDS are listed as reserved words in the docs, but don't
        // seem to behave like reserved words... so leave them out for now.
        words.addAll(new CsvSet("get, io_after_gtids, io_before_gtids, master_bind, partition"));

        return words;
    }
}
