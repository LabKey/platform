package org.labkey.specimen;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenEvent;
import org.labkey.api.specimen.SpecimenEventManager;
import org.labkey.api.specimen.SpecimenManagerNew;
import org.labkey.api.specimen.SpecimenQuerySchema;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.PrimaryType;
import org.labkey.api.specimen.model.SpecimenComment;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.Visit;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Path;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.specimen.model.AdditiveType;
import org.labkey.specimen.model.DerivativeType;
import org.labkey.specimen.model.ExtendedSpecimenRequestView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SpecimenManager
{
    private final static SpecimenManager INSTANCE = new SpecimenManager();

    private SpecimenManager()
    {
    }

    public static SpecimenManager get()
    {
        return INSTANCE;
    }

    public List<? extends Visit> getVisitsWithSpecimens(Container container, User user, Cohort cohort)
    {
        Study study = StudyService.get().getStudy(container);
        UserSchema schema = SpecimenQuerySchema.get(study, user);
        TableInfo tinfo = schema.getTable(SpecimenQuerySchema.SIMPLE_SPECIMEN_TABLE_NAME);

        FieldKey visitKey = FieldKey.fromParts("Visit");
        Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(tinfo, Collections.singleton(visitKey));
        Collection<ColumnInfo> cols = new ArrayList<>();
        cols.add(colMap.get(visitKey));
        Set<FieldKey> unresolvedColumns = new HashSet<>();
        cols = QueryService.get().ensureRequiredColumns(tinfo, cols, null, null, unresolvedColumns);
        if (!unresolvedColumns.isEmpty())
            throw new IllegalStateException("Unable to resolve column(s): " + unresolvedColumns.toString());
        // generate our select SQL:
        SQLFragment specimenSql = Table.getSelectSQL(tinfo, cols, null, null);

        SQLFragment visitIdSQL = new SQLFragment("SELECT DISTINCT Visit FROM (" + specimenSql.getSQL() + ") SimpleSpecimenQuery");
        visitIdSQL.addAll(specimenSql.getParamsArray());

        List<Integer> visitIds = new SqlSelector(SpecimenSchema.get().getSchema(), visitIdSQL).getArrayList(Integer.class);

        // Get shared visit study
        Study visitStudy = StudyService.get().getStudyForVisits(study);

        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromParts("RowId"), visitIds);
        if (cohort != null)
            filter.addWhereClause("CohortId IS NULL OR CohortId = ?", new Object[] { cohort.getRowId() });
        return StudyService.get().getVisits(visitStudy, filter, new Sort("DisplayOrder,SequenceNumMin"));
    }

    public LocationImpl[] getSitesWithRequests(Container container)
    {
        TableInfo locationTableInfo = SpecimenSchema.get().getTableInfoLocation(container);
        SQLFragment sql = new SQLFragment("SELECT * FROM ").append(locationTableInfo).append(" WHERE rowid IN\n" +
                "(SELECT destinationsiteid FROM study.samplerequest WHERE container = ?)\n" +
                "AND container = ? ORDER BY label").addAll(container, container);

        return new SqlSelector(SpecimenSchema.get().getSchema(), sql).getArray(LocationImpl.class);
    }

    public Set<LocationImpl> getEnrollmentSitesWithRequests(Container container, User user)
    {
        UserSchema schema = SpecimenQuerySchema.get(StudyService.get().getStudy(container), user);
        TableInfo tableInfoSpecimenDetail = schema.getTable(SpecimenQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        if (null == tableInfoSpecimenDetail)
            throw new IllegalStateException("SpecimenDetail table not found.");
        String tableInfoAlias = "Specimen";
        SQLFragment sql = new SQLFragment("SELECT Participant.EnrollmentSiteId FROM ");
        sql.append(tableInfoSpecimenDetail.getFromSQL(tableInfoAlias)).append(", ")
                .append("study.SampleRequestSpecimen AS RequestSpecimen, \n" +
                        "study.SampleRequest AS Request, study.SampleRequestStatus AS Status,\n" +
                        "study.Participant AS Participant\n" +
                        "WHERE Request.Container = Status.Container AND\n" +
                        "\tRequest.StatusId = Status.RowId AND\n" +
                        "\tRequestSpecimen.SampleRequestId = Request.RowId AND\n" +
                        "\tRequestSpecimen.Container = Request.Container AND\n" +
                        "\tSpecimen.Container = RequestSpecimen.Container AND\n" +
                        "\tSpecimen.GlobalUniqueId = RequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                        "\tParticipant.EnrollmentSiteId IS NOT NULL AND\n" +
                        "\tParticipant.Container = Specimen.Container AND\n" +
                        "\tParticipant.ParticipantId = Specimen.Ptid AND\n" +
                        "\tStatus.SpecimensLocked = ? AND\n" +
                        "\tRequest.Container = ?");
        sql.add(Boolean.TRUE);
        sql.add(container);

        return getSitesWithIdSql(container, "EnrollmentSiteId", sql);
    }

    public Set<LocationImpl> getEnrollmentSitesWithSpecimens(Container container, User user)
    {
        UserSchema schema = SpecimenQuerySchema.get(StudyService.get().getStudy(container), user);
        TableInfo tableInfoSpecimenDetail = schema.getTable(SpecimenQuerySchema.SPECIMEN_WRAP_TABLE_NAME);
        if (null == tableInfoSpecimenDetail)
            throw new IllegalStateException("SpecimenDetail table not found.");
        String tableInfoAlias = "Specimen";
        SQLFragment sql = new SQLFragment("SELECT EnrollmentSiteId FROM ");
        sql.append(tableInfoSpecimenDetail.getFromSQL(tableInfoAlias)).append(", study.Participant AS Participant\n" +
                "WHERE Specimen.Ptid = Participant.ParticipantId AND\n" +
                "\tParticipant.EnrollmentSiteId IS NOT NULL AND\n" +
                "\tSpecimen.Container = Participant.Container AND\n" +
                "\tSpecimen.Container = ?\n" +
                "GROUP BY EnrollmentSiteId");
        sql.add(container);

        return getSitesWithIdSql(container, "EnrollmentSiteId", sql);
    }

    private Set<LocationImpl> getSitesWithIdSql(final Container container, final String idColumnName, SQLFragment sql)
    {
        final Set<LocationImpl> locations = new TreeSet<>((s1, s2) ->
        {
            if (s1 == null && s2 == null)
                return 0;
            if (s1 == null)
                return -1;
            if (s2 == null)
                return 1;
            return s1.getLabel().compareTo(s2.getLabel());
        });

        new SqlSelector(SpecimenSchema.get().getSchema(), sql).forEach(rs -> {
            // try getObject first to see if we have a value for our row; getInt will coerce the null to
            // zero, which could (theoretically) be a valid site ID.
            if (rs.getObject(idColumnName) == null)
                locations.add(null);
            else
                locations.add(LocationManager.get().getLocation(container, rs.getInt(idColumnName)));
        });

        return locations;
    }

    private static final int GET_COMMENT_BATCH_SIZE = 1000;

    public Map<Vial, SpecimenComment> getSpecimenComments(List<Vial> vials)
    {
        if (vials == null || vials.size() == 0)
            return Collections.emptyMap();

        Container container = vials.get(0).getContainer();
        final Map<Vial, SpecimenComment> result = new HashMap<>();
        int offset = 0;

        while (offset < vials.size())
        {
            final Map<String, Vial> idToVial = new HashMap<>();

            for (int current = offset; current < offset + GET_COMMENT_BATCH_SIZE && current < vials.size(); current++)
            {
                Vial vial = vials.get(current);
                idToVial.put(vial.getGlobalUniqueId(), vial);
                if (!container.equals(vial.getContainer()))
                    throw new IllegalArgumentException("All specimens must be from the same container");
            }

            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addInClause(FieldKey.fromParts("GlobalUniqueId"), idToVial.keySet());

            new TableSelector(SpecimenSchema.get().getTableInfoSpecimenComment(), filter, null).forEach(SpecimenComment.class, comment -> {
                Vial vial = idToVial.get(comment.getGlobalUniqueId());
                result.put(vial, comment);
            });

            offset += GET_COMMENT_BATCH_SIZE;
        }

        return result;
    }

    public SpecimenComment getSpecimenCommentForVial(Container container, String globalUniqueId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("GlobalUniqueId"), globalUniqueId);

        return new TableSelector(SpecimenSchema.get().getTableInfoSpecimenComment(), filter, null).getObject(SpecimenComment.class);
    }

    public SpecimenComment getSpecimenCommentForVial(Vial vial)
    {
        return getSpecimenCommentForVial(vial.getContainer(), vial.getGlobalUniqueId());
    }

    public SpecimenComment setSpecimenComment(User user, Vial vial, String commentText, boolean qualityControlFlag, boolean qualityControlFlagForced)
    {
        TableInfo commentTable = SpecimenSchema.get().getTableInfoSpecimenComment();
        DbScope scope = commentTable.getSchema().getScope();
        SpecimenComment comment = getSpecimenCommentForVial(vial);
        boolean clearComment = commentText == null && !qualityControlFlag && !qualityControlFlagForced;
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            SpecimenComment result;
            if (clearComment)
            {
                if (comment != null)
                {
                    Table.delete(commentTable, comment.getRowId());
                    auditSpecimenComment(user, vial, comment.getComment(), null, comment.isQualityControlFlag(), false);
                }
                result = null;
            }
            else
            {
                if (comment != null)
                {
                    String prevComment = comment.getComment();
                    boolean prevConflictState = comment.isQualityControlFlag();
                    comment.setComment(commentText);
                    comment.setQualityControlFlag(qualityControlFlag);
                    comment.setQualityControlFlagForced(qualityControlFlagForced);
                    comment.beforeUpdate(user);
                    result = Table.update(user, commentTable, comment, comment.getRowId());
                    auditSpecimenComment(user, vial, prevComment, result.getComment(), prevConflictState, result.isQualityControlFlag());
                }
                else
                {
                    comment = new SpecimenComment();
                    comment.setGlobalUniqueId(vial.getGlobalUniqueId());
                    comment.setSpecimenHash(vial.getSpecimenHash());
                    comment.setComment(commentText);
                    comment.setQualityControlFlag(qualityControlFlag);
                    comment.setQualityControlFlagForced(qualityControlFlagForced);
                    comment.beforeInsert(user, vial.getContainer().getId());
                    result = Table.insert(user, commentTable, comment);
                    auditSpecimenComment(user, vial, null, result.getComment(), false, comment.isQualityControlFlag());
                }
            }
            transaction.commit();
            return result;
        }
    }

    @Nullable
    public ExtendedSpecimenRequestView getExtendedSpecimenRequestView(ViewContext context)
    {
        if (context == null || context.getContainer() == null)
            return null;

        Path path = ModuleHtmlView.getStandardPath("extendedrequest");

        for (Module module : context.getContainer().getActiveModules())
        {
            if (ModuleHtmlView.exists(module, path))
            {
                ModuleHtmlView moduleView = ModuleHtmlView.get(module, path);
                assert null != moduleView;
                HtmlString html = moduleView.getHtml();
                String s = ModuleHtmlView.replaceTokens(html.toString(), context);
                return ExtendedSpecimenRequestView.createView(s);
            }
        }

        return null;
    }

    private boolean safeComp(Object a, Object b)
    {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }

    private void auditSpecimenComment(User user, Vial vial, String oldComment, String newComment, boolean prevConflictState, boolean newConflictState)
    {
        String verb = "updated";
        if (oldComment == null)
            verb = "added";
        else if (newComment == null)
            verb = "deleted";
        String message = "";
        if (!safeComp(oldComment, newComment))
        {
            message += "Comment " + verb + ".\n";
            if (oldComment != null)
                message += "Previous value: " + oldComment + "\n";
            if (newComment != null)
                message += "New value: " + newComment + "\n";
        }

        if (!safeComp(prevConflictState, newConflictState))
        {
            message = "QC alert flag changed.\n";
            if (oldComment != null)
                message += "Previous value: " + prevConflictState + "\n";
            if (newComment != null)
                message += "New value: " + newConflictState + "\n";
        }

        SpecimenCommentAuditEvent event = new SpecimenCommentAuditEvent(vial.getContainer().getId(), message);
        event.setVialId(vial.getGlobalUniqueId());

        AuditLogService.get().addEvent(user, event);
    }

    public List<Vial> getVials(Container container, User user, String participantId, Double visit)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addClause(new SimpleFilter.SQLClause("LOWER(ptid) = LOWER(?)", new Object[] {participantId}, FieldKey.fromParts("ptid")));
        filter.addCondition(FieldKey.fromParts("VisitValue"), visit);
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
        List<Vial> vials = getVials(container, user, filter);
        if (vials == null || vials.size() != ids.size())
            throw new SpecimenRequestException("Vial not found.");       // an id has no matching specimen, let caller determine what to report
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

    @Nullable
    public AdditiveType getAdditiveType(Container c, int rowId)
    {
        List<AdditiveType> additiveTypes = getAdditiveTypes(c, new SimpleFilter(FieldKey.fromParts("RowId"), rowId));
        if (!additiveTypes.isEmpty())
            return additiveTypes.get(0);
        return null;
    }

    private List<AdditiveType> getAdditiveTypes(final Container container, @Nullable SimpleFilter filter)
    {
        final List<AdditiveType> additiveTypes = new ArrayList<>();
        new TableSelector(SpecimenSchema.get().getTableInfoSpecimenAdditive(container), filter, null).
                forEachMap(map -> additiveTypes.add(new AdditiveType(container, map)));
        return additiveTypes;
    }

    public DerivativeType getDerivativeType(Container c, int rowId)
    {
        List<DerivativeType> derivativeTypes = getDerivativeTypes(c, new SimpleFilter(FieldKey.fromParts("RowId"), rowId));
        if (!derivativeTypes.isEmpty())
            return derivativeTypes.get(0);
        return null;
    }

    private List<DerivativeType> getDerivativeTypes(final Container container, @Nullable SimpleFilter filter)
    {
        final List<DerivativeType> derivativeTypes = new ArrayList<>();
        new TableSelector(SpecimenSchema.get().getTableInfoSpecimenDerivative(container), filter, null).
                forEachMap(map -> derivativeTypes.add(new DerivativeType(container, map)));
        return derivativeTypes;
    }

    public boolean isSpecimensEmpty(Container container, User user)
    {
        TableSelector selector = getSpecimensSelector(container, user, null);
        return !selector.exists();
    }

    public Vial getVial(Container container, User user, String globalUniqueId)
    {
        SimpleFilter filter = new SimpleFilter(new SimpleFilter.SQLClause("LOWER(GlobalUniqueId) = LOWER(?)", new Object[] { globalUniqueId }));
        List<Vial> matches = getVials(container, user, filter);
        if (matches == null || matches.isEmpty())
            return null;
        if (matches.size() > 1)
        {
            // we apparently have two specimens with IDs that differ only in case; do a case-sensitive check
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

    /** Looks for any specimens that have the given id as a globalUniqueId  */
    public PrimaryType getPrimaryType(Container c, int rowId)
    {
        List<PrimaryType> primaryTypes = SpecimenManagerNew.get().getPrimaryTypes(c, new SimpleFilter(FieldKey.fromParts("RowId"), rowId), null);
        if (!primaryTypes.isEmpty())
            return primaryTypes.get(0);
        return null;
    }

    public List<Vial> getRequestableVials(Container container, User user, Set<Long> vialRowIds)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addInClause(FieldKey.fromParts("RowId"), vialRowIds).addCondition(FieldKey.fromString("available"), true);
        return getVials(container, user, filter);
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
        List<Vial> vials = getVials(container, user, filter);
        if (vials.isEmpty())
            return null;
        return vials.get(0);
    }

    public void deleteSpecimen(@NotNull Vial vial, boolean clearCaches)
    {
        Container container = vial.getContainer();
        TableInfo tableInfoSpecimenEvent = SpecimenSchema.get().getTableInfoSpecimenEvent(container);
        TableInfo tableInfoVial = SpecimenSchema.get().getTableInfoVial(container);
        if (null == tableInfoSpecimenEvent || null == tableInfoVial)
            return;

        String tableInfoSpecimenEventSelectName = tableInfoSpecimenEvent.getSelectName();
        String tableInfoVialSelectName = tableInfoVial.getSelectName();

        SQLFragment sqlFragmentEvent = new SQLFragment("DELETE FROM ");
        sqlFragmentEvent.append(tableInfoSpecimenEventSelectName).append(" WHERE VialId = ?");
        sqlFragmentEvent.add(vial.getRowId());
        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(sqlFragmentEvent);

        SQLFragment sqlFragment = new SQLFragment("DELETE FROM ");
        sqlFragment.append(tableInfoVialSelectName).append(" WHERE RowId = ?");
        sqlFragment.add(vial.getRowId());
        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(sqlFragment);

        if (clearCaches)
        {
            SpecimenRequestManager.get().clearCaches(vial.getContainer());
        }
    }

    public long getMaxExternalId(Container container)
    {
        TableInfo tableInfo = SpecimenSchema.get().getTableInfoSpecimenEvent(container);
        SQLFragment sql = new SQLFragment("SELECT MAX(ExternalId) FROM ");
        sql.append(tableInfo);
        return new SqlSelector(tableInfo.getSchema(), sql).getArrayList(Long.class).get(0);
    }

    public String getFirstProcessedByInitials(List<SpecimenEvent> dateOrderedEvents)
    {
        SpecimenEvent firstEvent = SpecimenEventManager.get().getFirstEvent(dateOrderedEvents);
        return firstEvent != null ? firstEvent.getProcessedByInitials() : null;
    }

    public List<SpecimenEvent> getSpecimenEvents(List<Vial> vials, boolean includeObsolete)
    {
        if (vials == null || vials.isEmpty())
            return Collections.emptyList();
        Collection<Long> vialIds = new HashSet<>();
        Container container = null;
        for (Vial vial : vials)
        {
            vialIds.add(vial.getRowId());
            if (container == null)
                container = vial.getContainer();
            else if (!container.equals(vial.getContainer()))
                throw new IllegalArgumentException("All specimens must be from the same container");
        }
        SimpleFilter filter = new SimpleFilter();
        filter.addInClause(FieldKey.fromString("VialId"), vialIds);
        if (!includeObsolete)
            filter.addCondition(FieldKey.fromString("Obsolete"), false);
        return SpecimenEventManager.get().getSpecimenEvents(container, filter);
    }
}
