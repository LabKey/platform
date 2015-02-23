/*
 * Copyright (c) 2012-2015 LabKey Corporation
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

import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.study.StudyService;
import java.util.Map;

/**
 * User: klum
 * Date: Mar 9, 2012
 */
public class SpecimenPivotByDerivativeType extends BaseSpecimenPivotTable
{
    public static final String PIVOT_BY_DERIVATIVE_TYPE = "Primary/Derivative Type Vial Counts";
    private static final String COLUMN_DESCRIPTION_FORMAT = "Number of vials of primary & derivative type %s/%s";

    public SpecimenPivotByDerivativeType(final StudyQuerySchema schema)
    {
        super(SpecimenReportQuery.getPivotByDerivativeType(schema.getContainer(), schema.getUser()), schema);
        setDescription("Contains up to one row of Specimen Primary/Derivative Type totals for each " + StudyService.get().getSubjectNounSingular(getContainer()) +
            "/visit combination.");

        Container container = getContainer();
        Map<Integer, NameLabelPair> primaryTypeMap = getPrimaryTypeMap(container);
        Map<Integer, NameLabelPair> derivativeTypeMap = getDerivativeTypeMap(container);

        for (ColumnInfo col : getRealTable().getColumns())
        {
            // look for the primary/derivative pivot encoding
            String parts[] = col.getName().split(AGGREGATE_DELIM);

            if (parts != null && parts.length == 2)
            {
                String types[] = parts[0].split(TYPE_DELIM);

                if (types != null && types.length == 2)
                {
                    int primaryId = NumberUtils.toInt(types[0]);
                    int derivativeId = NumberUtils.toInt(types[1]);

                    if (primaryTypeMap.containsKey(primaryId) && derivativeTypeMap.containsKey(derivativeId))
                    {
                        wrapPivotColumn(col,
                                COLUMN_DESCRIPTION_FORMAT,
                                primaryTypeMap.get(primaryId),
                                derivativeTypeMap.get(derivativeId),
                                new NameLabelPair(parts[1], parts[1]));
                    }
                }
            }
        }
        setDefaultVisibleColumns(getDefaultVisibleColumns());
        addWrapColumn(_rootTable.getColumn("Container"));
    }
}
