/*
 * Copyright (c) 2006-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.query;

import org.labkey.api.query.AliasedColumn;
import org.labkey.study.StudySchema;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 2, 2009
 */
public class SpecimenVialCountTable extends BaseStudyTable
{
    public SpecimenVialCountTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoSpecimenVialCount());

        addWrapColumn(_rootTable.getColumn("Container")).setIsHidden(true);
        addWrapColumn(_rootTable.getColumn("SpecimenHash")).setIsHidden(true);

        addColumn(new AliasedColumn("Vials", _rootTable.getColumn("VialCount")));
        addColumn(new AliasedColumn("LockedInRequest", _rootTable.getColumn("LockedInRequestCount")));
        addColumn(new AliasedColumn("AtRepository", _rootTable.getColumn("AtRepositoryCount")));
        addColumn(new AliasedColumn("Available", _rootTable.getColumn("AvailableCount")));
    }
}
