package org.labkey.api.specimen;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.specimen.model.PrimaryType;
import org.labkey.api.specimen.model.SpecimenTypeSummary;
import org.labkey.api.specimen.model.SpecimenTypeSummaryRow;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.NotFoundException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public List<Vial> getVials(final Container container, final User user, SimpleFilter filter)
    {
        // TODO: LinkedList?
        final List<Vial> vials = new ArrayList<>();

        getSpecimensSelector(container, user, filter)
                .forEachMap(map -> vials.add(new Vial(container, map)));

        return vials;
    }

    private TableSelector getSpecimensSelector(final Container container, final User user, SimpleFilter filter)
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
        List<Vial> vials = getVials(container, user, filter);
        if (vials.isEmpty())
            return null;
        return vials.get(0);
    }

    /** Looks for any specimens that have the given id as a globalUniqueId  */
    public Vial getVial(Container container, User user, String globalUniqueId)
    {
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.SQLClause("LOWER(GlobalUniqueId) = LOWER(?)", new Object[] { globalUniqueId }));
        List<Vial> matches = getVials(container, user, filter);
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

    public PrimaryType getPrimaryType(Container c, int rowId)
    {
        List<PrimaryType> primaryTypes = getPrimaryTypes(c, new SimpleFilter(FieldKey.fromParts("RowId"), rowId), null);
        if (!primaryTypes.isEmpty())
            return primaryTypes.get(0);
        return null;
    }

    public List<PrimaryType> getPrimaryTypes(Container c)
    {
        return getPrimaryTypes(c, null, new Sort("ExternalId"));
    }

    private List<PrimaryType> getPrimaryTypes(final Container container, @Nullable SimpleFilter filter, @Nullable Sort sort)
    {
        final List<PrimaryType> primaryTypes = new ArrayList<>();
        new TableSelector(SpecimenSchema.get().getTableInfoSpecimenPrimaryType(container), filter, sort).
                forEachMap(map -> primaryTypes.add(new PrimaryType(container, map)));
        return primaryTypes;
    }

    public List<Vial> getRequestableVials(Container container, User user, Set<Long> vialRowIds)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addInClause(FieldKey.fromParts("RowId"), vialRowIds).addCondition(FieldKey.fromString("available"), true);
        return getVials(container, user, filter);
    }

    public SpecimenTypeSummary getSpecimenTypeSummary(Container container, @NotNull User user)
    {
        UserSchema querySchema = SpecimenQuerySchema.get(StudyService.get().getStudy(container), user);
        TableInfo tableInfoSpecimenWrap = querySchema.getTable(SpecimenQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        if (null == tableInfoSpecimenWrap)
            throw new IllegalStateException("SpecimenDetail table not found.");

        TableInfo additiveTableInfo = querySchema.getTable(SpecimenQuerySchema.SPECIMEN_ADDITIVE_TABLE_NAME);
        TableInfo derivativeTableInfo = querySchema.getTable(SpecimenQuerySchema.SPECIMEN_DERIVATIVE_TABLE_NAME);
        TableInfo primaryTypeTableInfo = querySchema.getTable(SpecimenQuerySchema.SPECIMEN_PRIMARY_TYPE_TABLE_NAME);
        String tableInfoSelectName = "SpecimenWrap";

        // TODO: consider caching

        Study study = StudyService.get().getStudy(container);
        if (study == null)
            return null;

        SQLFragment specimenTypeSummarySQL = new SQLFragment("SELECT\n" +
                "\tPrimaryType,\n" +
                "\tPrimaryTypeId,\n" +
                "\tDerivative,\n" +
                "\tDerivativeTypeId,\n" +
                "\tAdditive,\n" +
                "\tAdditiveTypeId,\n" +
                "\tSUM(VialCount) AS VialCount\n" +
                "FROM (\n" +
                "\tSELECT\n" +
                "\tPT.PrimaryType AS PrimaryType,\n" +
                "\tPrimaryTypeId,\n" +
                "\tDT.Derivative AS Derivative,\n" +
                "\tDerivativeTypeId,\n" +
                "\tAT.Additive AS Additive,\n" +
                "\tAdditiveTypeId,\n" +
                "\tSpecimens.VialCount\n" +
                "\tFROM\n");

        SQLFragment sqlPtidFilter = new SQLFragment();
        if (study.isAncillaryStudy())
        {
/*            StudyQuerySchema sourceStudySchema = StudyQuerySchema.createSchema(study.getSourceStudy());
            SpecimenWrapTable sourceStudyTableInfo = (SpecimenWrapTable)sourceStudySchema.getTable(StudyQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
            tableInfoSpecimenWrap.setUnionTable(sourceStudyTableInfo);

            String[] ptids = StudyManager.getInstance().getParticipantIds(study);
            sqlPtidFilter.append("\t\t\tWHERE ").append(tableInfoSpecimenWrap.getColumn("PTID").getValueSql(tableInfoSelectName)).append(" IN (");
            if (ptids == null || ptids.length == 0)
                sqlPtidFilter.append("NULL");
            else
            {
                String comma = "";
                for (String ptid : ptids)
                {
                    sqlPtidFilter.append(comma).append("?");
                    sqlPtidFilter.add(ptid);
                    comma = ", ";
                }
            }
            sqlPtidFilter.append(")\n");  */
        }

        specimenTypeSummarySQL.append("\t\t(SELECT ")
                .append(tableInfoSpecimenWrap.getColumn("PrimaryTypeId").getValueSql(tableInfoSelectName)).append(",")
                .append(tableInfoSpecimenWrap.getColumn("DerivativeTypeId").getValueSql(tableInfoSelectName)).append(",")
                .append(tableInfoSpecimenWrap.getColumn("AdditiveTypeId").getValueSql(tableInfoSelectName)).append(",")
                .append("\n\t\t\tSUM(").append(tableInfoSpecimenWrap.getColumn("VialCount").getValueSql(tableInfoSelectName))
                .append(") AS VialCount\n")
                .append("\n\t\tFROM ").append(tableInfoSpecimenWrap.getFromSQL(tableInfoSelectName)).append("\n");
        specimenTypeSummarySQL.append(sqlPtidFilter);
        specimenTypeSummarySQL.append("\t\tGROUP BY ")
                .append(tableInfoSpecimenWrap.getColumn("PrimaryTypeId").getValueSql(tableInfoSelectName)).append(",")
                .append(tableInfoSpecimenWrap.getColumn("DerivativeTypeId").getValueSql(tableInfoSelectName)).append(",")
                .append(tableInfoSpecimenWrap.getColumn("AdditiveTypeId").getValueSql(tableInfoSelectName))
                .append("\t\t\t) Specimens\n").append(
                "\tLEFT OUTER JOIN ").append(primaryTypeTableInfo.getFromSQL("PT")).append(  " ON\n" +
                "\t\tPT.RowId = Specimens.PrimaryTypeId\n" +
                "\tLEFT OUTER JOIN ").append(derivativeTableInfo.getFromSQL("DT")).append(" ON\n" +
                "\t\tDT.RowId = Specimens.DerivativeTypeId\n" +
                "\tLEFT OUTER JOIN ").append(additiveTableInfo.getFromSQL("AT")).append(" ON\n" +
                "\t\tAT.RowId = Specimens.AdditiveTypeId\n" +
                ") ContainerTotals\n" +
                "GROUP BY PrimaryType, PrimaryTypeId, Derivative, DerivativeTypeId, Additive, AdditiveTypeId\n" +
                "ORDER BY PrimaryType, Derivative, Additive"
        );

        SpecimenTypeSummaryRow[] rows = new SqlSelector(SpecimenSchema.get().getSchema(), specimenTypeSummarySQL).getArray(SpecimenTypeSummaryRow.class);

        return new SpecimenTypeSummary(container, rows);
    }

    public Map<String,List<Vial>> getVialsForSpecimenHashes(Container container, User user, Collection<String> hashes, boolean onlyAvailable)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addInClause(FieldKey.fromParts("SpecimenHash"), hashes);
        if (onlyAvailable)
            filter.addCondition(FieldKey.fromParts("Available"), true);
        List<Vial> vials = getVials(container, user, filter);
        Map<String, List<Vial>> map = new HashMap<>();
        for (Vial vial : vials)
        {
            String hash = vial.getSpecimenHash();
            List<Vial> keyVials = map.computeIfAbsent(hash, k -> new ArrayList<>());
            keyVials.add(vial);
        }

        return map;
    }
}


