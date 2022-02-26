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
