/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.study.query;

import org.labkey.api.data.CoreSchema;
import org.labkey.api.query.FilteredTable;

/**
 * User: brittp
 * Created: July 15, 2008 11:13:43 AM
 */
public class QCStateTable extends FilteredTable<StudyQuerySchema>
{
    public QCStateTable(StudyQuerySchema schema)
    {
        super(CoreSchema.getInstance().getTableInfoQCState(), schema);
        wrapAllColumns(true);
    }
}