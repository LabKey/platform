/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.study.SpecimenTablesTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultSpecimenTablesTemplate implements SpecimenTablesTemplate
{
    private static final String LATESTDEVIATIONCODE1 = "LatestDeviationCode1";
    private static final String LATESTDEVIATIONCODE2 = "LatestDeviationCode2";
    private static final String LATESTDEVIATIONCODE3 = "LatestDeviationCode3";
    private static final String LATESTINTEGRITY = "LatestIntegrity";
    private static final String LATESTRATIO = "LatestRatio";
    private static final String LATESTYIELD = "LatestYield";
    private static final String LATESTCONCENTRATION = "LatestConcentration";
    private static final String FREEZER = "Freezer";
    private static final String FR_CONTAINER = "Fr_Container";
    private static final String FR_POSITION = "Fr_Position";
    private static final String FR_LEVEL1 = "Fr_Level1";
    private static final String FR_LEVEL2 = "Fr_Level2";

    private static final String DEVIATIONCODE1 = "DeviationCode1";
    private static final String DEVIATIONCODE2 = "DeviationCode2";
    private static final String DEVIATIONCODE3 = "DeviationCode3";
    private static final String CONCENTRATION = "Concentration";
    private static final String INTEGRITY = "Integrity";
    private static final String RATIO = "Ratio";
    private static final String YIELD = "Yield";

    private static final List<PropertyStorageSpec> EXTRAVIAL_PROPERTIES;
    private static final List<PropertyStorageSpec> EXTRASPECIMENEVENT_PROPERTIES;
    static
    {
        PropertyStorageSpec[] vialProps =
        {
            new PropertyStorageSpec(LATESTDEVIATIONCODE1, JdbcType.VARCHAR, 50, "Site-defined deviation code from latest event."),
            new PropertyStorageSpec(LATESTDEVIATIONCODE2, JdbcType.VARCHAR, 50, "Site-defined deviation code from latest event."),
            new PropertyStorageSpec(LATESTDEVIATIONCODE3, JdbcType.VARCHAR, 50, "Site-defined deviation code from latest event."),
            new PropertyStorageSpec(LATESTCONCENTRATION, JdbcType.REAL, 0, "Processing concentration value from latest event."),
            new PropertyStorageSpec(LATESTINTEGRITY, JdbcType.REAL, 0, "Processing integrity value from latest event."),
            new PropertyStorageSpec(LATESTRATIO, JdbcType.REAL, 0, "Processing ratio value from latest event."),
            new PropertyStorageSpec(LATESTYIELD, JdbcType.REAL, 0, "Processing yield value from latest event."),
            new PropertyStorageSpec(FREEZER, JdbcType.VARCHAR, 200, "The ID of the storage freezer."),
            new PropertyStorageSpec(FR_CONTAINER, JdbcType.VARCHAR, 200, "The container location within the storage freezer."),
            new PropertyStorageSpec(FR_POSITION, JdbcType.VARCHAR, 200, "The storage position within the storage freezer."),
            new PropertyStorageSpec(FR_LEVEL1, JdbcType.VARCHAR, 200, "The level 1 location within the storage freezer."),
            new PropertyStorageSpec(FR_LEVEL2, JdbcType.VARCHAR, 200, "The level 2 location within the storage freezer."),
        };
        EXTRAVIAL_PROPERTIES = Arrays.asList(vialProps);

        PropertyStorageSpec[] specimenEventProps =
        {
            new PropertyStorageSpec(DEVIATIONCODE1, JdbcType.VARCHAR, 50, "Site-defined deviation code.", "deviation_code1"),
            new PropertyStorageSpec(DEVIATIONCODE2, JdbcType.VARCHAR, 50, "Site-defined deviation code.", "deviation_code2"),
            new PropertyStorageSpec(DEVIATIONCODE3, JdbcType.VARCHAR, 50, "Site-defined deviation code.", "deviation_code3"),
            new PropertyStorageSpec(CONCENTRATION, JdbcType.REAL, 0, "Processing concentration value."),
            new PropertyStorageSpec(INTEGRITY, JdbcType.REAL, 0, "Processing integrity value."),
            new PropertyStorageSpec(RATIO, JdbcType.REAL, 0, "Processing ratio value."),
            new PropertyStorageSpec(YIELD, JdbcType.REAL, 0, "Processing yield value."),
            new PropertyStorageSpec(FREEZER, JdbcType.VARCHAR, 200, "The ID of the storage freezer."),
            new PropertyStorageSpec(FR_CONTAINER, JdbcType.VARCHAR, 200, "The container location within the storage freezer."),
            new PropertyStorageSpec(FR_POSITION, JdbcType.VARCHAR, 200, "The storage position within the storage freezer."),
            new PropertyStorageSpec(FR_LEVEL1, JdbcType.VARCHAR, 200, "The level 1 location within the storage freezer."),
            new PropertyStorageSpec(FR_LEVEL2, JdbcType.VARCHAR, 200, "The level 2 location within the storage freezer."),
        };
        EXTRASPECIMENEVENT_PROPERTIES = Arrays.asList(specimenEventProps);
    }

    public Set<PropertyStorageSpec> getExtraSpecimenEventProperties()
    {
        return new LinkedHashSet<>(EXTRASPECIMENEVENT_PROPERTIES);
    }

    public Set<PropertyStorageSpec> getExtraVialProperties()
    {
        return new LinkedHashSet<>(EXTRAVIAL_PROPERTIES);
    }

    public Set<PropertyStorageSpec> getExtraSpecimenProperties()
    {
        return Collections.emptySet();
    }
}
