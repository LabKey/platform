package org.labkey.api.specimen;

import java.util.List;

// Methods extracted from SpecimenManager to assist with migration. TODO: remove these or combine with SpecimenManager
public class SpecimenEventManager
{
    private static boolean skipAsProcessingLocation(SpecimenEvent event)
    {
        boolean allNullDates = event.getLabReceiptDate() == null && event.getStorageDate() == null && event.getShipDate() == null;
        //
        return allNullDates && !safeComp(event.getLabId(), event.getOriginatingLocationId());
    }

    public static SpecimenEvent getFirstEvent(List<SpecimenEvent> dateOrderedEvents)
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

    public static SpecimenEvent getLastEvent(List<SpecimenEvent> dateOrderedEvents)
    {
        if (dateOrderedEvents.isEmpty())
            return null;
        return dateOrderedEvents.get(dateOrderedEvents.size() - 1);
    }

    public Integer getProcessingLocationId(List<SpecimenEvent> dateOrderedEvents)
    {
        SpecimenEvent firstEvent = getFirstEvent(dateOrderedEvents);
        return firstEvent != null ? firstEvent.getLabId() : null;
    }

    public String getFirstProcessedByInitials(List<SpecimenEvent> dateOrderedEvents)
    {
        SpecimenEvent firstEvent = getFirstEvent(dateOrderedEvents);
        return firstEvent != null ? firstEvent.getProcessedByInitials() : null;
    }

    private static boolean safeComp(Object a, Object b)
    {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }
}
