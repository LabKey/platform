/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.api.assay.plate;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.study.PropertySet;
import org.labkey.api.view.ActionURL;

import java.util.List;
import java.util.Set;

public interface WellGroup extends PropertySet, Identifiable, WellData
{
    enum Type
    {
        ANTIGEN("Antigen"),
        CONTROL("Control"),
        OTHER("Other"),
        NEGATIVE_CONTROL("Negative Control"),
        POSITIVE_CONTROL("Positive Control"),
        REPLICATE("Replicate"),
        SAMPLE("Sample"),
        SPECIMEN("Specimen"),
        VIRUS("Virus");

        private final String _label;

        Type(String label)
        {
            _label = label;
        }

        public String getLabel()
        {
            return _label;
        }
    }

    Integer getRowId();

    List<Position> getPositions();

    List<? extends WellData> getWellData(boolean combineReplicates);

    Set<WellGroup> getOverlappingGroups();

    Set<WellGroup> getOverlappingGroups(Type type);

    Double getMinDilution();

    Double getMaxDilution();

    default void setPositions(List<? extends Position> positions)
    {
        throw new UnsupportedOperationException();
    }

    Type getType();

    @Override
    String getName();

    boolean contains(Position position);

    String getPositionDescription();

    @Override
    default @Nullable ActionURL detailsURL()
    {
        return null;
    }

    default boolean isZone()
    {
        return StringUtils.isEmpty(getName());
    }
}
