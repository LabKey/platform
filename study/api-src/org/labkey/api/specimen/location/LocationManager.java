package org.labkey.api.specimen.location;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenEvent;
import org.labkey.api.specimen.SpecimenEventManager;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.Vial;
import org.labkey.api.study.Location;
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

    public void createLocation(User user, LocationImpl location)
    {
        Table.insert(user, getTableInfoLocations(location.getContainer()), location);
        LocationCache.clear(location.getContainer());
    }

    public void updateLocation(User user, LocationImpl location)
    {
        Table.update(user, getTableInfoLocations(location.getContainer()), location, location.getRowId());
        LocationCache.clear(location.getContainer());
    }

    private TableInfo getTableInfoLocations(Container container)
    {
        return SpecimenSchema.get().getTableInfoLocation(container);
    }

    public boolean isLocationInUse(Location loc, TableInfo table, String... columnNames)
    {
        List<Object> params = new ArrayList<>();
        params.add(loc.getContainer().getId());

        StringBuilder cols = new StringBuilder("(");
        String or = "";
        for (String columnName : columnNames)
        {
            cols.append(or).append(columnName).append(" = ?");
            params.add(loc.getRowId());
            or = " OR ";
        }
        cols.append(")");

        String containerColumn = " = ? AND ";
        if (table.getName().contains("_vial") || table.getName().equals("_specimenevent"))
        {
            //vials and events use a column called fr_container instead of normal container.
            containerColumn = "FR_Container" + containerColumn;
        }
        else if (table.getName().contains("_specimen"))
        {
            params.remove(0);
            containerColumn = "";
        }
        else
        {
            containerColumn = "Container" + containerColumn;
        }
        return new SqlSelector(table.getSchema(), new SQLFragment("SELECT * FROM " +
                table + " WHERE " + containerColumn + cols.toString(), params)).exists();
    }

    public boolean isLocationInUse(LocationImpl loc)
    {
        return
            isLocationInUse(loc, SpecimenSchema.get().getTableInfoSampleRequest(), "DestinationSiteId") ||
            isLocationInUse(loc, SpecimenSchema.get().getTableInfoSampleRequestRequirement(), "SiteId") ||
            isLocationInUse(loc, SpecimenSchema.get().getTableInfoVial(loc.getContainer()), "CurrentLocation", "ProcessingLocation") ||
            //vials and events use a column called fr_container instead of normal container.
            isLocationInUse(loc, SpecimenSchema.get().getTableInfoSpecimen(loc.getContainer()), "originatinglocationid", "ProcessingLocation") ||
            //Doesn't have a container or fr_container column
            isLocationInUse(loc, SpecimenSchema.get().getTableInfoSpecimenEvent(loc.getContainer()), "LabId", "OriginatingLocationId") ||
            //vials and events use a column called fr_container instead of normal container.

            // Do any of the tables that study manages reference this location?
            StudyService.get().isLocationInUse(loc);
    }

    public void deleteLocation(LocationImpl location) throws ValidationException
    {
        if (!isLocationInUse(location))
        {
            try (DbScope.Transaction transaction = SpecimenSchema.get().getScope().ensureTransaction())
            {
                Container container = location.getContainer();

                Table.delete(getTableInfoLocations(container), new SimpleFilter(FieldKey.fromString("RowId"), location.getRowId()));
                LocationCache.clear(container);

                transaction.commit();
            }
        }
        else
        {
            throw new ValidationException("Locations currently in use cannot be deleted");
        }
    }

    public LocationImpl getOriginatingLocation(Vial vial)
    {
        if (vial.getOriginatingLocationId() != null)
        {
            LocationImpl location = LocationManager.get().getLocation(vial.getContainer(), vial.getOriginatingLocationId());
            if (location != null)
                return location;
        }

        List<SpecimenEvent> events = SpecimenEventManager.get().getDateOrderedEventList(vial);
        Integer firstLabId = getProcessingLocationId(events);
        if (firstLabId != null)
            return LocationManager.get().getLocation(vial.getContainer(), firstLabId);
        else
            return null;
    }

    public LocationImpl getCurrentLocation(Vial vial)
    {
        Integer locationId = getCurrentLocationId(vial);
        if (locationId != null)
            return LocationManager.get().getLocation(vial.getContainer(), locationId);
        return null;
    }

    private Integer getCurrentLocationId(Vial vial)
    {
        List<SpecimenEvent> events = SpecimenEventManager.get().getDateOrderedEventList(vial);
        return getCurrentLocationId(events);
    }

    public Integer getCurrentLocationId(List<SpecimenEvent> dateOrderedEvents)
    {
        if (!dateOrderedEvents.isEmpty())
        {
            SpecimenEvent lastEvent = dateOrderedEvents.get(dateOrderedEvents.size() - 1);

            if (lastEvent.getShipDate() == null &&
                    (lastEvent.getShipBatchNumber() == null || lastEvent.getShipBatchNumber() == 0) &&
                    (lastEvent.getShipFlag() == null || lastEvent.getShipFlag() == 0))
            {
                return lastEvent.getLabId();
            }
        }
        return null;
    }

    public Integer getProcessingLocationId(List<SpecimenEvent> dateOrderedEvents)
    {
        SpecimenEvent firstEvent = SpecimenEventManager.get().getFirstEvent(dateOrderedEvents);
        return firstEvent != null ? firstEvent.getLabId() : null;
    }
}
