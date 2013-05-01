/*
 * Copyright (c) 2009 LabKey Corporation
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
import org.labkey.study.SampleManager;
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

        addWrapColumn(_rootTable.getColumn("Container")).setHidden(true);
        addWrapColumn(_rootTable.getColumn("SpecimenHash")).setHidden(true);

        addColumn(new AliasedColumn(this, "TotalCount", _rootTable.getColumn("VialCount")));

        boolean enableSpecimenRequest = SampleManager.getInstance().getRepositorySettings(getContainer()).isEnableRequests();

        addColumn(new AliasedColumn(this, "LockedInRequest", _rootTable.getColumn("LockedInRequestCount"))).setHidden(!enableSpecimenRequest);
        addColumn(new AliasedColumn(this, "AtRepository", _rootTable.getColumn("AtRepositoryCount")));
        addColumn(new AliasedColumn(this, "Available", _rootTable.getColumn("AvailableCount"))).setHidden(!enableSpecimenRequest);
        addColumn(new AliasedColumn(this, "ExpectedAvailable", _rootTable.getColumn("ExpectedAvailableCount"))).setHidden(!enableSpecimenRequest);
    }
}
