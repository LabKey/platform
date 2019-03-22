/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

package org.labkey.api.qc;

import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.Position;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.WellGroupTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Apr 20, 2009
 */
public class PlateBasedDataExchangeHandler extends TsvDataExchangeHandler
{
    public static final String GROUP_COLUMN_NAME = "Wellgroup";
    public static final String ROW_PROP_NAME = "Row";
    public static final String COL_PROP_NAME = "Column";

    /**
     * Adds sample properties for plate based assays. Specimen information is flattened so that a row will be created
     * for each well location.
     *
     * @param propertyName - The name to store this file location in the run properties map
     * @param groupColumnName - The name of the column which contains well group name
     * @param propertySet - The unflattened specimen data from the upload form
     * @param plate - The plate template
     * @param wellType - The well type to create rows for.
     */
    public void addSampleProperties(String propertyName, String groupColumnName, Map<String, Map<DomainProperty, String>> propertySet,
                                    PlateTemplate plate, WellGroup.Type wellType)
    {
        List<Map<String, Object>> rows = new ArrayList<>();

        if (plate != null)
        {
            for (WellGroupTemplate group : plate.getWellGroups())
            {
                if (group.getType() != wellType)
                    continue;

                Map<DomainProperty, String> entry = propertySet.get(group.getName());
                if (entry != null)
                {
                    for (Position pos : group.getPositions())
                    {
                        Map<String, Object> row = new HashMap<>();
                        row.put(groupColumnName, group.getName());
                        row.put(ROW_PROP_NAME, pos.getRow());
                        row.put(COL_PROP_NAME, pos.getColumn());

                        for (Map.Entry<DomainProperty, String> colEntry : entry.entrySet())
                        {
                            row.put(colEntry.getKey().getLabel(), colEntry.getValue());
                        }
                        rows.add(row);
                    }
                }
            }
        }
        addSampleProperties(propertyName, rows);
    }

    /**
     * Create fake wellgroup data for debugging and testing of programmatic QC scripts.
     */
    protected Map<String, Map<DomainProperty, String>> createTestSampleProperties(List<? extends DomainProperty> properties, PlateTemplate template, WellGroup.Type type)
    {
        Map<String, Map<DomainProperty, String>> specimens = new HashMap<>();
        for (WellGroupTemplate wellGroup : template.getWellGroups())
        {
            if (wellGroup.getType() != type)
                continue;

            Map<DomainProperty, String> row = new HashMap<>();

            for (DomainProperty dp : properties)
            {
                row.put(dp, getSampleValue(dp));
            }
            specimens.put(wellGroup.getName(), row);
        }
        return specimens;
    }

}
