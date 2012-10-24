/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

package org.labkey.api.exp.query;

/**
 * The RunGroupMap table is a junction table used to join Runs and RunGroups.
 * A Run may belong to more than one RunGroup and are unordered in the group.
 * The TableInfo will be filtered to Runs in the current Container.
 */
public interface ExpRunGroupMapTable extends ExpTable<ExpRunGroupMapTable.Column>
{
    enum Column
    {
        RunGroup,
        Run,
        Created,
        CreatedBy,
    }
}

