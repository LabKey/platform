/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.assay.plate.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.labkey.api.assay.plate.PlateSet;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PlateSetAssays
{
    // A map of Assay Protocol ID to Plate Set IDs
    private Map<Long, List<Long>> _protocolPlateSets = Collections.emptyMap();
    // A map of Plate Set ID to Plate Set
    private Map<Long, PlateSet> _plateSets = Collections.emptyMap();

    public PlateSetAssays()
    {
    }

    public Map<Long, List<Long>> getProtocolPlateSets()
    {
        return _protocolPlateSets;
    }

    public void setProtocolPlateSets(Map<Long, List<Long>> protocolPlateSets)
    {
        _protocolPlateSets = protocolPlateSets;
    }

    public Map<Long, PlateSet> getPlateSets()
    {
        return _plateSets;
    }

    public void setPlateSets(Map<Long, PlateSet> plateSets)
    {
        _plateSets = plateSets;
    }
}
