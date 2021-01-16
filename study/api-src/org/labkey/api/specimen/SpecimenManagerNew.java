package org.labkey.api.specimen;

import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.NotFoundException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SpecimenManagerNew
{
    private final static SpecimenManagerNew INSTANCE = new SpecimenManagerNew();

    private SpecimenManagerNew()
    {
    }

    public static SpecimenManagerNew get()
    {
        return INSTANCE;
    }

    public boolean isSpecimensEmpty(Container container, User user)
    {
        TableSelector selector = getSpecimensSelector(container, user, null);
        return !selector.exists();
    }

    public List<Vial> getVials(Container container, User user, String participantId, Double visit)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addClause(new SimpleFilter.SQLClause("LOWER(ptid) = LOWER(?)", new Object[] {participantId}, FieldKey.fromParts("ptid")));
        filter.addCondition(FieldKey.fromParts("VisitValue"), visit);
        return getVials(container, user, filter);
    }

    public List<Vial> getVials(Container container, User user, Set<Long> vialRowIds)
    {
        // Take a set to eliminate dups - issue 26940

        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addInClause(FieldKey.fromParts("RowId"), vialRowIds);
        List<Vial> vials = getVials(container, user, filter);
        if (vials.size() != vialRowIds.size())
        {
            List<Long> unmatchedRowIds = new ArrayList<>(vialRowIds);
            for (Vial vial : vials)
            {
                unmatchedRowIds.remove(vial.getRowId());
            }
            throw new SpecimenRequestException("One or more specimen RowIds had no matching specimen: " + unmatchedRowIds);
        }
        return vials;
    }

    public List<Vial> getVials(Container container, User user, String participantId, Date date)
    {
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.SQLClause("LOWER(ptid) = LOWER(?)", new Object[] {participantId}, FieldKey.fromParts("ptid")));
        Calendar endCal = DateUtil.newCalendar(date.getTime());
        endCal.add(Calendar.DATE, 1);
        filter.addClause(new SimpleFilter.SQLClause("DrawTimestamp >= ? AND DrawTimestamp < ?", new Object[] {date, endCal.getTime()}));
        return getVials(container, user, filter);
    }

    public List<Vial> getVials(Container container, User user, int[] vialsRowIds)
    {
        Set<Long> uniqueRowIds = new HashSet<>(vialsRowIds.length);
        for (int vialRowId : vialsRowIds)
            uniqueRowIds.add((long)vialRowId);
        return getVials(container, user, uniqueRowIds);
    }

    public List<Vial> getVials(Container container, User user, String[] globalUniqueIds) throws SpecimenRequestException
    {
        SimpleFilter filter = new SimpleFilter();
        Set<String> uniqueRowIds = new HashSet<>(globalUniqueIds.length);
        Collections.addAll(uniqueRowIds, globalUniqueIds);
        List<String> ids = new ArrayList<>(uniqueRowIds);
        filter.addInClause(FieldKey.fromParts("GlobalUniqueId"), ids);
        List<Vial> vials = SpecimenManagerNew.get().getVials(container, user, filter);
        if (vials == null || vials.size() != ids.size())
            throw new SpecimenRequestException("Vial not found.");       // an id has no matching specimen, let caller determine what to report
        return vials;
    }

    public List<Vial> getVials(final Container container, final User user, SimpleFilter filter)
    {
        // TODO: LinkedList?
        final List<Vial> vials = new ArrayList<>();

        getSpecimensSelector(container, user, filter)
                .forEachMap(map -> vials.add(new Vial(container, map)));

        return vials;
    }

    public TableSelector getSpecimensSelector(final Container container, final User user, SimpleFilter filter)
    {
        Study study = StudyService.get().getStudy(container);
        if (study == null)
        {
            throw new NotFoundException("No study in container " + container.getPath());
        }
        UserSchema schema = SpecimenQuerySchema.get(study, user);
        TableInfo specimenTable = schema.getTable(SpecimenQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        return new TableSelector(specimenTable, filter, null);
    }

    public Vial getVial(Container container, User user, long rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowId);
        List<Vial> vials = SpecimenManagerNew.get().getVials(container, user, filter);
        if (vials.isEmpty())
            return null;
        return vials.get(0);
    }

    /** Looks for any specimens that have the given id as a globalUniqueId  */
    public Vial getVial(Container container, User user, String globalUniqueId)
    {
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.SQLClause("LOWER(GlobalUniqueId) = LOWER(?)", new Object[] { globalUniqueId }));
        List<Vial> matches = SpecimenManagerNew.get().getVials(container, user, filter);
        if (matches == null || matches.isEmpty())
            return null;
        if (matches.size() > 1)
        {
            // we apparently have two specimens with IDs that differ only in case; do a case sensitive check
            // here to find the right one:
            for (Vial vial : matches)
            {
                if (vial.getGlobalUniqueId().equals(globalUniqueId))
                    return vial;
            }
            throw new IllegalStateException("Expected at least one vial to exactly match the specified global unique ID: " + globalUniqueId);
        }
        else
            return matches.get(0);
    }
}


