/*
 * Copyright (c) 2016 LabKey Corporation
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
 * Created by adam on 7/9/2016.
 */
public class MySql57Dialect extends MySql56Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        // Add new reserved words in MySQL 5.7; see http://dev.mysql.com/doc/refman/5.7/en/keywords.html
        Set<String> words = super.getReservedWords();

        words.addAll(new CsvSet("generated, optimizer_costs, sql_buffer_result, sql_cache, sql_no_cache, stored, virtual"));

        return words;
    }
}
