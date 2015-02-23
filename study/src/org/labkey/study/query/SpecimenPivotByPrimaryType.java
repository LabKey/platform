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

import java.sql.SQLException;
import java.util.Map;

/**
 * User: klum
 * Date: Mar 5, 2012
 */
public class SpecimenPivotByPrimaryType extends BaseSpecimenPivotTable
{
    public static final String PIVOT_BY_PRIMARY_TYPE = "Primary Type Vial Counts";
    private static final String COLUMN_DESCRIPTION_FORMAT = "Number of vials of primary type %s";

    public SpecimenPivotByPrimaryType(final StudyQuerySchema schema)
    {
        super(SpecimenReportQuery.getPivotByPrimaryType(schema.getContainer(), schema.getUser()), schema);
        setDescription("Contains up to one row of Specimen Primary Type totals for each " + StudyService.get().getSubjectNounSingular(getContainer()) +
            "/visit combination.");

        try {
            Container container = getContainer();
            Map<Integer, NameLabelPair> primaryTypeMap = getPrimaryTypeMap(container);
            Map<Integer, NameLabelPair> allPrimaryTypes = getAllPrimaryTypesMap(getContainer());
            
            for (ColumnInfo col : getRealTable().getColumns())
            {
                // look for the primary/derivative pivot encoding
                String parts[] = col.getName().split(AGGREGATE_DELIM);

                if (parts != null && parts.length == 2)
                {
                    int primaryId = NumberUtils.toInt(parts[0]);

                    if (primaryTypeMap.containsKey(primaryId))
                    {
                        wrapPivotColumn(col, COLUMN_DESCRIPTION_FORMAT, primaryTypeMap.get(primaryId),
                                new NameLabelPair(parts[1], parts[1]));
                    }
                    else if (allPrimaryTypes.containsKey(primaryId))
                    {
                        ColumnInfo wrappedCol = wrapPivotColumn(col, COLUMN_DESCRIPTION_FORMAT, allPrimaryTypes.get(primaryId),
                                new NameLabelPair(parts[1], parts[1]));

                        wrappedCol.setHidden(true);
                    }
                }
            }

            setDefaultVisibleColumns(getDefaultVisibleColumns());

            addWrapColumn(_rootTable.getColumn("Container"));
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    /*
    private List<FieldKey> getDefaultColumns(StudyQuerySchema schema)
    {
        List<FieldKey> defaultColumns = new ArrayList<FieldKey>();

        defaultColumns.add(FieldKey.fromParts(StudyService.get().getSubjectColumnName(getContainer())));
        defaultColumns.add(FieldKey.fromParts("Visit"));

        Map<String, String> nonZeroPrimaryTypes = new HashMap<String, String>();

        for (String label : getPrimaryTypeMap(getContainer()).values())
            nonZeroPrimaryTypes.put(label, label);

        for (ColumnInfo col : getColumns())
        {
            String[] parts = col.getName().split("::");

            if (parts != null && parts.length > 1)
            {
                if (nonZeroPrimaryTypes.containsKey(parts[0]))
                    defaultColumns.add(col.getFieldKey());
                else
                    col.setHidden(true);
            }
        }
        return defaultColumns;
    }
    */
}
