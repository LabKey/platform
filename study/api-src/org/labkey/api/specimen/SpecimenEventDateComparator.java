package org.labkey.api.specimen;

import java.util.Comparator;
import java.util.Date;

public class SpecimenEventDateComparator implements Comparator<SpecimenEvent>
{
    private static final SpecimenEventDateComparator INSTANCE = new SpecimenEventDateComparator();

    public static SpecimenEventDateComparator get()
    {
        return INSTANCE;
    }

    private SpecimenEventDateComparator()
    {
    }

    private Date getAnyDate(SpecimenEvent event)
    {
        if (event.getLabReceiptDate() != null)
            return event.getLabReceiptDate();
        else
        {
            Date storageDate = event.getStorageDate();
            if (storageDate != null)
                return storageDate;
            else
                return event.getShipDate();
        }
    }

    private int getTieBreakValue(SpecimenEvent event)
    {
        // our events have the same dates; in this case, we have to consider
        // the date type; a shipping date always comes after a storage date,
        // and a storage date always comes after a receipt date.
        if (event.getLabReceiptDate() != null)
            return 1;
        else if (event.getStorageDate() != null)
            return 2;
        else if (event.getShipDate() != null)
            return 3;
        throw new IllegalStateException("Can only tiebreak events with at least one date present.");
    }

    @Override
    public int compare(SpecimenEvent event1, SpecimenEvent event2)
    {
        // Obsolete always < non-obsolete
        if (event1.getObsolete() != event2.getObsolete())
            if (event1.getObsolete())
                return -1;
            else
                return 1;

        // we use any date in the event, since we assume that no two events can have
        // overlapping date ranges:
        Date date1 = getAnyDate(event1);
        Date date2 = getAnyDate(event2);
        if (date1 == null && date2 == null)
            return compareExternalIds(event1, event2);
        if (date1 == null)
            return -1;
        if (date2 == null)
            return 1;
        Long ms1 = date1.getTime();
        Long ms2 = date2.getTime();
        int comp = ms1.compareTo(ms2);
        if (comp != 0)
            return comp;
        comp = getTieBreakValue(event2) - getTieBreakValue(event1);
        if (comp != 0)
            return comp;
        return compareExternalIds(event1, event2);
    }

    private static int compareExternalIds(SpecimenEvent event1, SpecimenEvent event2)
    {
        long compExternalIds = event1.getExternalId() - event2.getExternalId();
        if (compExternalIds < 0)
            return -1;
        if (compExternalIds > 0)
            return 1;
        return 0;
    }
}
