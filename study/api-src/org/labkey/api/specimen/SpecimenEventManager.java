package org.labkey.api.specimen;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.List;

// Methods extracted from SpecimenManager to assist with migration. TODO: remove these or recombine with SpecimenManager
public class SpecimenEventManager
{
    private static final SpecimenEventManager INSTANCE = new SpecimenEventManager();

    private SpecimenEventManager()
    {
    }

    public static SpecimenEventManager get()
    {
        return INSTANCE;
    }

    public SpecimenEvent getFirstEvent(List<SpecimenEvent> dateOrderedEvents)
    {
        if (!dateOrderedEvents.isEmpty())
        {
            SpecimenEvent firstEvent = dateOrderedEvents.get(0);
            // walk backwards through the events until we find an event with at least one date field filled in that isn't
            // the first event.  Leaving all specimen event dates blank shouldn't make an event the processing location.
            for (int i = 1; i < dateOrderedEvents.size() - 1 && skipAsProcessingLocation(firstEvent); i++)
                firstEvent = dateOrderedEvents.get(i);
            return firstEvent;
        }
        return null;
    }

    private boolean skipAsProcessingLocation(SpecimenEvent event)
    {
        boolean allNullDates = event.getLabReceiptDate() == null && event.getStorageDate() == null && event.getShipDate() == null;
        //
        return allNullDates && !safeComp(event.getLabId(), event.getOriginatingLocationId());
    }

    private static boolean safeComp(Object a, Object b)
    {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }

    public SpecimenEvent getLastEvent(List<SpecimenEvent> dateOrderedEvents)
    {
        if (dateOrderedEvents.isEmpty())
            return null;
        return dateOrderedEvents.get(dateOrderedEvents.size() - 1);
    }

    public List<SpecimenEvent> getSpecimenEvents(final Container container, Filter filter)
    {
        final List<SpecimenEvent> specimenEvents = new ArrayList<>();
        TableInfo tableInfo = SpecimenSchema.get().getTableInfoSpecimenEvent(container);

        new TableSelector(tableInfo, filter, null).forEachMap(map -> specimenEvents.add(new SpecimenEvent(container, map)));

        return specimenEvents;
    }

    public List<SpecimenEvent> getDateOrderedEventList(Vial vial)
    {
        List<SpecimenEvent> eventList = new ArrayList<>();
        List<SpecimenEvent> events = getSpecimenEvents(vial);
        if (events == null || events.isEmpty())
            return eventList;
        eventList.addAll(events);
        eventList.sort(SpecimenEventDateComparator.get());
        return eventList;
    }

    private List<SpecimenEvent> getSpecimenEvents(@NotNull Vial vial)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("VialId"), vial.getRowId());
        return getSpecimenEvents(vial.getContainer(), filter);
    }
}
