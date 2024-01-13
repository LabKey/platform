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

import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User: klum
 * Date: Jun 13, 2012
 */
public abstract class AbstractPlateLayoutHandler implements PlateLayoutHandler
{
    Map<Pair<Integer, Integer>, PlateType> _plateTypeMap;

    @Override
    public void validateTemplate(Container container, User user, Plate template) throws ValidationException
    {
    }

    @Override
    public Map<String, List<String>> getDefaultGroupsForTypes()
    {
        return Collections.emptyMap();
    }

    abstract protected List<Pair<Integer, Integer>> getSupportedPlateSizes();

    @Override
    public List<PlateType> getSupportedPlateTypes()
    {
        if (_plateTypeMap == null)
        {
            _plateTypeMap = new HashMap<>();
            for (PlateType type : PlateService.get().getPlateTypes())
            {
                _plateTypeMap.put(new Pair<>(type.getRows(), type.getColumns()), type);
            }
        }
        return getSupportedPlateSizes().stream().filter(size -> _plateTypeMap.containsKey(size)).map(size -> _plateTypeMap.get(size)).collect(Collectors.toList());
    }

    @Override
    public boolean showEditorWarningPanel()
    {
        return true;
    }

    @Override
    public boolean canCreateNewGroups(WellGroup.Type type)
    {
        return true;
    }
}
