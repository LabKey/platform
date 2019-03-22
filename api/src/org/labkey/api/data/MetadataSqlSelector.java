/*
 * Copyright (c) 2015 LabKey Corporation
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
 * User: tgaluhn
 * Date: 10/12/2015
 *
 * Simple extension to SqlSelector for places we use sql queries to retrieve database metadata
 * instead of relying on jdbc method calls. Allows query profiling/tracking to be aware
 * the query is metadata related and should, e.g., bypass sql logging.
 */
public class MetadataSqlSelector extends SqlSelector
{
    public MetadataSqlSelector(DbScope scope, SQLFragment sql)
    {
        super(scope, sql, QueryLogging.metadataQueryLogging());
    }

    public MetadataSqlSelector(DbScope scope, CharSequence sql)
    {
        super(scope, new SQLFragment(sql), QueryLogging.metadataQueryLogging());
    }
}
