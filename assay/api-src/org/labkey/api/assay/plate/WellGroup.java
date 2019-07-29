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

import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:18:16 AM
 */
public interface WellGroup extends WellData, WellGroupTemplate
{
    enum Type
    {
        CONTROL,
        SPECIMEN,
        REPLICATE,
        ANTIGEN,
        OTHER,
        VIRUS
    }

    List<? extends WellData> getWellData(boolean combineReplicates);

    Type getType();

    boolean contains(Position position);

    Set<WellGroup> getOverlappingGroups();

    Set<WellGroup> getOverlappingGroups(Type type);

    List<Position> getPositions();

    Double getMinDilution();

    Double getMaxDilution();
}
