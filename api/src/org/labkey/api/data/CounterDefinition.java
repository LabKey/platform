/*
 * Copyright (c) 2019 LabKey Corporation
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
import java.util.Set;

/**
 * Define DB Sequence that is unique in a container and on the values of the paired columns.
 * A Java customizer to a query describes this.
 */
public interface CounterDefinition
{
    /**
     * @return the name of the counter
     */
    String getCounterName();

    /**
     * @return list of names of paired columns
     */
    List<String> getPairedColumnNames();

    /**
     * @return set of names of columns to which the sequence is attached
     */
    Set<String> getAttachedColumnNames();

    /**
     * @param prefix A string to prepend to the sequence name
     * @param pairedValues values of paired columns
     * @return DB Sequence name constructed from pairedValues
     */
    String getDbSequenceName(String prefix, List<String> pairedValues);
}
