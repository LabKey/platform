package org.labkey.api.specimen.location;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;

import java.util.ArrayList;
import java.util.List;

public class LocationManager
{
    private static final LocationManager INSTANCE = new LocationManager();

    private LocationManager()
    {
    }

    public static LocationManager get()
    {
        return INSTANCE;
    }

    public Study getStudy(Container container)
    {
        return StudyService.get().getStudy(container);
    }

    public List<LocationImpl> getLocations(Container container)
    {
        return LocationCache.getLocations(container);
    }

    public List<LocationImpl> getValidRequestingLocations(Container container)
    {
        Study study = getStudy(container);
        List<LocationImpl> validLocations = new ArrayList<>();
        List<LocationImpl> locations = getLocations(container);
        for (LocationImpl location : locations)
        {
            if (isSiteValidRequestingLocation(study, location))
            {
                validLocations.add(location);
            }
        }
        return validLocations;
    }

    public boolean isSiteValidRequestingLocation(Container container, int id)
    {
        Study study = getStudy(container);
        LocationImpl location = getLocation(container, id);
        return isSiteValidRequestingLocation(study, location);
    }

    private boolean isSiteValidRequestingLocation(Study study, LocationImpl location)
    {
        if (null == location)
            return false;

        if (location.isRepository() && study.isAllowReqLocRepository())
        {
            return true;
        }
        if (location.isClinic() && study.isAllowReqLocClinic())
        {
            return true;
        }
        if (location.isSal() && study.isAllowReqLocSal())
        {
            return true;
        }
        if (location.isEndpoint() && study.isAllowReqLocEndpoint())
        {
            return true;
        }
        // If it has no location type, allow it
        return !location.isRepository() && !location.isClinic() && !location.isSal() && !location.isEndpoint();
    }

    @Nullable
    public LocationImpl getLocation(Container container, int id)
    {
        // If a default ID has been registered, just use that
        Integer defaultSiteId = SpecimenService.get().getRequestCustomizer().getDefaultDestinationSiteId();

        if (defaultSiteId != null && id == defaultSiteId.intValue())
        {
            LocationImpl location = new LocationImpl(container, "User Request");
            location.setRowId(defaultSiteId);
            location.setDescription("User requested location.");
            return location;
        }

        return LocationCache.getForRowId(container, id);
    }
}
