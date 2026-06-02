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
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.study.Location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class RequestedSpecimens
{
    private final Collection<Integer> _providingLocationIds;
    private final List<Vial> _vials;

    private List<Location> _providingLocations;

    public RequestedSpecimens(List<Vial> vials, Collection<Integer> providingLocationIds)
    {
        _vials = vials;
        _providingLocationIds = providingLocationIds;
    }

    public RequestedSpecimens(List<Vial> vials)
    {
        _vials = vials;
        _providingLocationIds = new HashSet<>();
        if (vials != null)
        {
            for (Vial vial : vials)
                _providingLocationIds.add(vial.getCurrentLocation());
        }
    }

    public List<Location> getProvidingLocations()
    {
        if (_providingLocations == null)
        {
            if (_vials == null || _vials.isEmpty())
                _providingLocations = Collections.emptyList();
            else
            {
                Container container = _vials.getFirst().getContainer();
                _providingLocations = new ArrayList<>(_providingLocationIds.size());

                for (Integer locationId : _providingLocationIds)
                    _providingLocations.add(LocationManager.get().getLocation(container, locationId.intValue()));
            }
        }
        return _providingLocations;
    }

    public List<Vial> getVials()
    {
        return _vials;
    }
}
