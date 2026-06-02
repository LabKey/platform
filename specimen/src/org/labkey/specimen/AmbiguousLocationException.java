/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen;

import org.labkey.api.data.Container;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;

import java.util.Collection;

public class AmbiguousLocationException extends Exception
{
    private final Container _container;
    private final Collection<Integer> _possibleLocationIds;

    private LocationImpl[] _possibleLocations = null;

    public AmbiguousLocationException(Container container, Collection<Integer> possibleLocationIds)
    {
        _container = container;
        _possibleLocationIds = possibleLocationIds;
    }

    public Collection<Integer> getPossibleLocationIds()
    {
        return _possibleLocationIds;
    }

    public LocationImpl[] getPossibleLocations()
    {
        if (_possibleLocations == null)
        {
            _possibleLocations = new LocationImpl[_possibleLocationIds.size()];
            int idx = 0;

            for (Integer id : _possibleLocationIds)
                _possibleLocations[idx++] = LocationManager.get().getLocation(_container, id.intValue());
        }
        return _possibleLocations;
    }
}
