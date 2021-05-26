package org.labkey.api.specimen.importer;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.specimen.model.SpecimenTablesProvider;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Methods extracted from SpecimenImporter to allow migrating AbstractSpecimenDomainKind, et al
// TODO: Consider combining this back with SpecimenImporter or move some of its other rollup methods here
public class RollupHelper
{
    public static class RollupMap<K extends Rollup> extends CaseInsensitiveHashMap<List<RollupInstance<K>>>
    {
    }

    private static final List<EventVialRollup> _eventVialRollups = Arrays.asList(EventVialRollup.values());
    private static final List<VialSpecimenRollup> _vialSpecimenRollups = Arrays.asList(VialSpecimenRollup.values());

    public static List<VialSpecimenRollup> getVialSpecimenRollups()
    {
        return _vialSpecimenRollups;
    }

    public static List<EventVialRollup> getEventVialRollups()
    {
        return _eventVialRollups;
    }

    private static final CaseInsensitiveHashSet _eventFieldNamesDisallowedForRollups = new CaseInsensitiveHashSet(
            "RowId", "VialId", "LabId", "PTID", "PrimaryTypeId", "AdditiveTypeId", "DerivativeTypeId", "DerivativeTypeId2", "OriginatingLocationId"
    );

    private static final CaseInsensitiveHashSet _vialFieldNamesDisallowedForRollups = new CaseInsensitiveHashSet(
            "RowId", "SpecimenId"
    );

    public static CaseInsensitiveHashSet getEventFieldNamesDisallowedForRollups()
    {
        return _eventFieldNamesDisallowedForRollups;
    }

    public static CaseInsensitiveHashSet getVialFieldNamesDisallowedForRollups()
    {
        return _vialFieldNamesDisallowedForRollups;
    }

    public static <K extends Rollup> void findRollups(RollupMap<K> resultRollups, PropertyDescriptor fromProperty,
                                                      List<PropertyDescriptor> toProperties, List<K> considerRollups, boolean allowTypeMismatch)
    {
        for (K rollup : considerRollups)
        {
            for (PropertyDescriptor toProperty : toProperties)
            {
                if (rollup.match(fromProperty, toProperty, allowTypeMismatch))
                {
                    List<RollupInstance<K>> matches = resultRollups.computeIfAbsent(fromProperty.getName(), k -> new ArrayList<>());
                    matches.add(new RollupInstance<>(toProperty.getName(), rollup, fromProperty.getJdbcType(), toProperty.getJdbcType()));
                }
            }
        }
    }

    public static <K extends Rollup> RollupMap<K> getRollups(Domain fromDomain, Domain toDomain, List<K> considerRollups)
    {
        RollupMap<K> matchedRollups = new RollupMap<>();
        List<PropertyDescriptor> toProperties = new ArrayList<>();

        for (DomainProperty domainProperty : toDomain.getNonBaseProperties())
            toProperties.add(domainProperty.getPropertyDescriptor());

        for (DomainProperty domainProperty : fromDomain.getProperties())
        {
            PropertyDescriptor property = domainProperty.getPropertyDescriptor();
            findRollups(matchedRollups, property, toProperties, considerRollups, false);
        }
        return matchedRollups;
    }

    public static Map<String, Pair<String, RollupInstance<EventVialRollup>>> getVialToEventNameMap(List<PropertyDescriptor> vialProps, List<PropertyDescriptor> eventProps)
    {
        return getRollupNameMap(vialProps, eventProps, getEventVialRollups());
    }

    public static Map<String, Pair<String, RollupInstance<VialSpecimenRollup>>> getSpecimenToVialNameMap(List<PropertyDescriptor> vialProps, List<PropertyDescriptor> eventProps)
    {
        return getRollupNameMap(vialProps, eventProps, getVialSpecimenRollups());
    }

    // Build a map that indicates for a property in Vial or Specimen, which property in Event or Vial, respectively will rollup to it
    private static <K extends Rollup> Map<String, Pair<String, RollupInstance<K>>> getRollupNameMap(List<PropertyDescriptor> toProps,
                                                                                                    List<PropertyDescriptor> fromProps, List<K> considerRollups)
    {
        RollupMap<K> matchedRollups = new RollupMap<>();
        for (PropertyDescriptor property : fromProps)
        {
            findRollups(matchedRollups, property, toProps, considerRollups, true);
        }

        Map<String, Pair<String, RollupInstance<K>>> resultMap = new HashMap<>();
        for (PropertyDescriptor fromProp : fromProps)
        {
            List<RollupInstance<K>> rollupInstances = matchedRollups.get(fromProp.getName());
            if (null != rollupInstances)
            {
                for (RollupInstance<K> rollupInstance : rollupInstances)
                {
                    resultMap.put(rollupInstance.getKey().toLowerCase(), new Pair<>(fromProp.getName(), rollupInstance));
                }
            }
        }
        return resultMap;
    }

    public static List<String> getRolledupDuplicateVialColumnNames(Container container, User user)
    {
        // Return names of columns where column is 2nd thru nth column rolled up on same Event column
        List<String> rolledupNames = new ArrayList<>();
        RollupMap<EventVialRollup> eventToVialRollups = getEventToVialRollups(container, user);
        for (List<RollupInstance<EventVialRollup>> rollupList : eventToVialRollups.values())
        {
            boolean duplicate = false;
            for (RollupInstance<EventVialRollup> rollupItem : rollupList)
            {
                if (duplicate)
                    rolledupNames.add(rollupItem.first.toLowerCase());
                duplicate = true;
            }
        }
        return rolledupNames;
    }

    public static RollupMap<EventVialRollup> getEventToVialRollups(Container container, User user)
    {
        List<EventVialRollup> rollups = RollupHelper.getEventVialRollups();
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, null);

        Domain fromDomain = specimenTablesProvider.getDomain("SpecimenEvent", true);
        if (null == fromDomain)
            throw new IllegalStateException("Expected SpecimenEvent table to already be created.");

        Domain toDomain = specimenTablesProvider.getDomain("Vial", true);
        if (null == toDomain)
            throw new IllegalStateException("Expected Vial table to already be created.");

        return RollupHelper.getRollups(fromDomain, toDomain, rollups);
    }

    public static List<String> getRolledupSpecimenColumnNames(Container container, User user)
    {
        List<String> rolledupNames = new ArrayList<>();
        RollupMap<VialSpecimenRollup> vialToSpecimenRollups = getVialToSpecimenRollups(container, user);
        for (List<RollupInstance<VialSpecimenRollup>> rollupList : vialToSpecimenRollups.values())
        {
            for (RollupInstance<VialSpecimenRollup> rollupItem : rollupList)
            {
                rolledupNames.add(rollupItem.first.toLowerCase());
            }
        }
        return rolledupNames;
    }

    public static RollupMap<VialSpecimenRollup> getVialToSpecimenRollups(Container container, User user)
    {
        List<VialSpecimenRollup> rollups = RollupHelper.getVialSpecimenRollups();
        SpecimenTablesProvider specimenTablesProvider = new SpecimenTablesProvider(container, user, null);

        Domain fromDomain = specimenTablesProvider.getDomain("Vial", true);
        if (null == fromDomain)
            throw new IllegalStateException("Expected Vial table to already be created.");

        Domain toDomain = specimenTablesProvider.getDomain("Specimen", true);
        if (null == toDomain)
            throw new IllegalStateException("Expected Specimen table to already be created.");

        return RollupHelper.getRollups(fromDomain, toDomain, rollups);
    }
}
