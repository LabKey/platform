/*
 * Copyright (c) 2012-2026 LabKey Corporation
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
package org.labkey.specimen.query;

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import java.util.Map;

public class SpecimenPivotByPrimaryType extends BaseSpecimenPivotTable
{
    public static final String PIVOT_BY_PRIMARY_TYPE = "Primary Type Vial Counts";
    private static final String COLUMN_DESCRIPTION_FORMAT = "Number of vials of primary type %s";

    public SpecimenPivotByPrimaryType(final UserSchema schema, Study study, ContainerFilter cf)
    {
        super(SpecimenReportQuery.getPivotByPrimaryType(schema, study, cf), schema);
        Container container = getContainer();
        setName(PIVOT_BY_PRIMARY_TYPE);
        setDescription("Contains up to one row of Specimen Primary Type totals for each " + StudyService.get().getSubjectNounSingular(container) +
            "/visit combination.");

        Map<Integer, NameLabelPair> primaryTypeMap = getPrimaryTypeMap(container);
        Map<Integer, NameLabelPair> allPrimaryTypes = getAllPrimaryTypesMap(container);

        for (ColumnInfo col : getRealTable().getColumns())
        {
            // look for the primary/derivative pivot encoding
            String[] parts = col.getName().split(AGGREGATE_DELIM);

            if (parts.length == 2)
            {
                int primaryId = NumberUtils.toInt(parts[0]);

                if (primaryTypeMap.containsKey(primaryId))
                {
                    wrapPivotColumn(col, COLUMN_DESCRIPTION_FORMAT, primaryTypeMap.get(primaryId),
                            new NameLabelPair(parts[1], parts[1]));
                }
                else if (allPrimaryTypes.containsKey(primaryId))
                {
                    var wrappedCol = wrapPivotColumn(col, COLUMN_DESCRIPTION_FORMAT, allPrimaryTypes.get(primaryId),
                            new NameLabelPair(parts[1], parts[1]));

                    wrappedCol.setHidden(true);
                }
            }
        }

        setDefaultVisibleColumns(getDefaultVisibleColumns());

        addWrapColumn(_rootTable.getColumn("Container"));
    }
}
