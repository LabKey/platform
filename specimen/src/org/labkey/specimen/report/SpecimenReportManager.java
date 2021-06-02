package org.labkey.specimen.report;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.logging.log4j.LogManager;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenManager;
import org.labkey.api.specimen.SpecimenQuerySchema;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SpecimenReportManager
{
    private final static SpecimenReportManager INSTANCE = new SpecimenReportManager();

    private SpecimenReportManager()
    {
    }

    public static SpecimenReportManager get()
    {
        return INSTANCE;
    }

    private SpecimenDetailQueryHelper getSpecimenDetailQueryHelper(Container container, User user,
                                                                   CustomView baseView, SimpleFilter specimenDetailFilter,
                                                                   SpecimenTypeLevel level)
    {
        UserSchema schema = SpecimenQuerySchema.get(StudyService.get().getStudy(container), user);
        TableInfo tinfo = schema.getTable(SpecimenQuerySchema.SPECIMEN_DETAIL_TABLE_NAME);

        Map<String, SpecimenTypeBeanProperty> aliasToTypeProperty = new LinkedHashMap<>();

        Collection<FieldKey> columns = new HashSet<>();
        if (baseView != null)
        {
            // copy our saved view filter into our SimpleFilter via an ActionURL (yuck...)
            ActionURL url = new ActionURL();
            baseView.applyFilterAndSortToURL(url, "mockDataRegion");
            specimenDetailFilter.addUrlFilters(url, "mockDataRegion");
        }

        // Build a list fo FieldKeys for all the columns that we must select,
        // regardless of whether they're in the selected specimen view.  We need to ask the view which
        // columns are required in case there's a saved filter on a column outside the primary table:
        columns.add(FieldKey.fromParts("Container"));
        columns.add(FieldKey.fromParts("Visit"));
        columns.add(FieldKey.fromParts("SequenceNum"));
        columns.add(FieldKey.fromParts("LockedInRequest"));
        columns.add(FieldKey.fromParts("GlobalUniqueId"));
        columns.add(FieldKey.fromParts(StudyService.get().getSubjectColumnName(container)));
        if (StudyService.get().showCohorts(container, schema.getUser()))
            columns.add(FieldKey.fromParts("CollectionCohort"));
        columns.add(FieldKey.fromParts("Volume"));
        if (level != null)
        {
            for (SpecimenTypeBeanProperty typeProperty : level.getGroupingColumns())
                columns.add(typeProperty.getTypeKey());
        }

        // turn our fieldkeys into columns:
        Map<FieldKey, ColumnInfo> colMap = QueryService.get().getColumns(tinfo, columns);
        Set<FieldKey> unresolvedColumns = new HashSet<>();
        Collection<ColumnInfo> cols = new ArrayList<>(colMap.values());
        cols = QueryService.get().ensureRequiredColumns(tinfo, cols, specimenDetailFilter, null, unresolvedColumns);
        if (!unresolvedColumns.isEmpty())
            throw new IllegalStateException("Unable to resolve column(s): " + unresolvedColumns.toString());
        // generate our select SQL:
        SQLFragment viewSql = new SQLFragment();
        viewSql.appendComment("<getSpecimenDetailQueryHelper>", tinfo.getSqlDialect());
        viewSql.append(Table.getSelectSQL(tinfo, cols, specimenDetailFilter, null));
        viewSql.appendComment("</getSpecimenDetailQueryHelper>", tinfo.getSqlDialect());

        // save off the aliases for our grouping columns, so we can group by them later:
        String groupingColSql = null;
        if (level != null)
        {
            StringBuilder builder = new StringBuilder();
            String sep = "";
            for (SpecimenTypeBeanProperty typeProperty : level.getGroupingColumns())
            {
                ColumnInfo col = colMap.get(typeProperty.getTypeKey());
                builder.append(sep).append(col.getAlias());
                sep = ", ";
                aliasToTypeProperty.put(col.getAlias(), typeProperty);
            }
            groupingColSql = builder.toString();
        }
        return new SpecimenDetailQueryHelper(viewSql, groupingColSql, aliasToTypeProperty);
    }

    public SummaryByVisitType[] getSpecimenSummaryByVisitType(Container container, User user, SimpleFilter specimenDetailFilter,
                                                              boolean includeParticipantGroups, SpecimenTypeLevel level, CustomView baseView)
    {
        if (specimenDetailFilter == null)
            specimenDetailFilter = new SimpleFilter();
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }

        final SpecimenDetailQueryHelper viewSqlHelper = getSpecimenDetailQueryHelper(container, user, baseView, specimenDetailFilter, level);

        String perPtidSpecimenSQL = "\t-- Inner SELECT gets the number of vials per participant/visit/type:\n" +
                "\tSELECT InnerView.Container, InnerView.Visit, " + viewSqlHelper.getTypeGroupingColumns() + ",\n" +
                "\tInnerView." + StudyService.get().getSubjectColumnName(container) + ", COUNT(*) AS VialCount, SUM(InnerView.Volume) AS PtidVolume \n" +
                "FROM (\n" + viewSqlHelper.getViewSql().getSQL() + "\n) InnerView\n" +
                "\tGROUP BY InnerView.Container, InnerView." + StudyService.get().getSubjectColumnName(container) +
                ", InnerView.Visit, " + viewSqlHelper.getTypeGroupingColumns() + "\n";

        SQLFragment sql = new SQLFragment("-- Outer grouping allows us to count participants AND sum vial counts:\n" +
                "SELECT VialData.Visit AS Visit, " + viewSqlHelper.getTypeGroupingColumns() + ", COUNT(*) as ParticipantCount, \n" +
                "SUM(VialData.VialCount) AS VialCount, SUM(VialData.PtidVolume) AS TotalVolume FROM \n" +
                "(\n" + perPtidSpecimenSQL + ") AS VialData\n" +
                "GROUP BY Visit, " + viewSqlHelper.getTypeGroupingColumns() + "\n" +
                "ORDER BY " + viewSqlHelper.getTypeGroupingColumns() + ", Visit");
        sql.addAll(viewSqlHelper.getViewSql().getParamsArray());

        final List<SummaryByVisitType> ret = new ArrayList<>();

        new SqlSelector(SpecimenSchema.get().getSchema(), sql).forEach(rs -> {
            SummaryByVisitType summary = new SummaryByVisitType();
            if (rs.getObject("Visit") != null)
                summary.setVisit(rs.getInt("Visit"));
            summary.setTotalVolume(rs.getDouble("TotalVolume"));
            Double vialCount = rs.getDouble("VialCount");
            summary.setVialCount(vialCount.longValue());
            Double participantCount = rs.getDouble("ParticipantCount");
            summary.setParticipantCount(participantCount.longValue());

            for (Map.Entry<String, SpecimenTypeBeanProperty> typeProperty : viewSqlHelper.getAliasToTypePropertyMap().entrySet())
            {
                String value = rs.getString(typeProperty.getKey());
                try
                {
                    PropertyUtils.setProperty(summary, typeProperty.getValue().getBeanProperty(), value);
                }
                catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e)
                {
                    throw new RuntimeException(e);
                }
            }
            ret.add(summary);
        });

        SummaryByVisitType[] summaries = ret.toArray(new SummaryByVisitType[ret.size()]);

        if (includeParticipantGroups)
            setSummaryParticipantGroups(perPtidSpecimenSQL, viewSqlHelper.getViewSql().getParamsArray(),
                    viewSqlHelper.getAliasToTypePropertyMap(), summaries, StudyService.get().getSubjectColumnName(container), "Visit");

        return summaries;
    }

    private void setSummaryParticipantGroups(String sql, Object[] paramArray, final Map<String, SpecimenTypeBeanProperty> aliasToTypeProperty,
                                             SummaryByVisitType[] summaries, final String ptidColumnName, final String visitValueColumnName)
    {
        SQLFragment fragment = new SQLFragment(sql);
        fragment.addAll(paramArray);

        final Map<String, Set<String>> cellToPtidSet = new HashMap<>();

        new SqlSelector(SpecimenSchema.get().getSchema(), fragment).forEach(rs -> {
            String ptid = rs.getString(ptidColumnName);
            Integer visit = rs.getInt(visitValueColumnName);
            String primaryType = null;
            String derivative = null;
            String additive = null;

            for (Map.Entry<String, SpecimenTypeBeanProperty> entry : aliasToTypeProperty.entrySet())
            {
                switch (entry.getValue().getLevel())
                {
                    case PrimaryType:
                        primaryType = rs.getString(entry.getKey());
                        break;
                    case Derivative:
                        derivative = rs.getString(entry.getKey());
                        break;
                    case Additive:
                        additive = rs.getString(entry.getKey());
                        break;
                }
            }

            String key = getPtidListKey(visit, primaryType, derivative, additive);

            Set<String> ptids = cellToPtidSet.computeIfAbsent(key, k -> new TreeSet<>());
            ptids.add(ptid != null ? ptid : "[unknown]");
        });

        for (SummaryByVisitType summary : summaries)
        {
            Integer visit = summary.getVisit();
            String key = getPtidListKey(visit, summary.getPrimaryType(), summary.getDerivative(), summary.getAdditive());
            Set<String> ptids = cellToPtidSet.get(key);
            summary.setParticipantIds(ptids);
        }
    }

    private String getPtidListKey(Integer visit, String primaryType, String derivativeType, String additiveType)
    {
        return visit + "/" + primaryType + "/" +
                (derivativeType != null ? derivativeType : "all") +
                (additiveType != null ? additiveType : "all");
    }

    public RequestSummaryByVisitType[] getRequestSummaryBySite(Container container, User user, SimpleFilter specimenDetailFilter, boolean includeParticipantGroups, SpecimenTypeLevel level, CustomView baseView, boolean completeRequestsOnly)
    {
        if (specimenDetailFilter == null)
        {
            specimenDetailFilter = new SimpleFilter();
        }
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }

        final SpecimenDetailQueryHelper sqlHelper = getSpecimenDetailQueryHelper(container, user, baseView, specimenDetailFilter, level);

        TableInfo locationTableInfo = SpecimenSchema.get().getTableInfoLocation(container);
        String subjectCol = StudyService.get().getSubjectColumnName(container);
        String sql = "SELECT Specimen.Container,\n" +
                "Specimen." + subjectCol + ",\n" +
                "Request.DestinationSiteId,\n" +
                "Site.Label AS SiteLabel,\n" +
                "Visit AS Visit,\n" +
                sqlHelper.getTypeGroupingColumns() + ", COUNT(*) AS VialCount, SUM(Volume) AS TotalVolume\n" +
                "FROM (" + sqlHelper.getViewSql().getSQL() + ") AS Specimen\n" +
                "JOIN study.SampleRequestSpecimen AS RequestSpecimen ON \n" +
                "\tSpecimen.GlobalUniqueId = RequestSpecimen.SpecimenGlobalUniqueId AND\n" +
                "\tSpecimen.Container = RequestSpecimen.Container\n" +
                "JOIN study.SampleRequest AS Request ON\n" +
                "\tRequestSpecimen.SampleRequestId = Request.RowId AND\n" +
                "\tRequestSpecimen.Container = Request.Container\n" +
                "JOIN " + locationTableInfo.getSelectName() + " AS Site ON\n" +
                "\tSite.Container = Request.Container AND\n" +
                "\tSite.RowId = Request.DestinationSiteId\n" +
                "JOIN study.SampleRequestStatus AS Status ON\n" +
                "\tStatus.Container = Request.Container AND\n" +
                "\tStatus.RowId = Request.StatusId and Status.SpecimensLocked = ?\n" +
                (completeRequestsOnly ? "\tAND Status.FinalState = ?\n" : "") +
                "GROUP BY Specimen.Container, Specimen." + subjectCol + ", Site.Label, DestinationSiteId, " + sqlHelper.getTypeGroupingColumns() + ", Visit\n" +
                "ORDER BY Specimen.Container, Specimen." + subjectCol + ", Site.Label, DestinationSiteId, " + sqlHelper.getTypeGroupingColumns() + ", Visit";

        Object[] params = new Object[sqlHelper.getViewSql().getParamsArray().length + 1 + (completeRequestsOnly ? 1 : 0)];
        System.arraycopy(sqlHelper.getViewSql().getParamsArray(), 0, params, 0, sqlHelper.getViewSql().getParamsArray().length);
        params[params.length - 1] = Boolean.TRUE;
        if (completeRequestsOnly)
            params[params.length - 2] = Boolean.TRUE;

        SqlDialect d = SpecimenSchema.get().getSqlDialect();
        SQLFragment fragment = new SQLFragment();
        fragment.appendComment("<getRequestSummaryBySite>",d);
        fragment.append(sql);
        fragment.appendComment("</getRequestSummaryBySite>", d);
        fragment.addAll(params);

        final List<RequestSummaryByVisitType> ret = new ArrayList<>();

        new SqlSelector(SpecimenSchema.get().getSchema(), fragment).forEach(rs -> {
            RequestSummaryByVisitType summary = new RequestSummaryByVisitType();
            summary.setDestinationSiteId(rs.getInt("DestinationSiteId"));
            summary.setSiteLabel(rs.getString("SiteLabel"));
            summary.setVisit(rs.getInt("Visit"));
            summary.setTotalVolume(rs.getDouble("TotalVolume"));
            Double vialCount = rs.getDouble("VialCount");
            summary.setVialCount(vialCount.longValue());

            for (Map.Entry<String, SpecimenTypeBeanProperty> typeProperty : sqlHelper.getAliasToTypePropertyMap().entrySet())
            {
                String value = rs.getString(typeProperty.getKey());

                try
                {
                    PropertyUtils.setProperty(summary, typeProperty.getValue().getBeanProperty(), value);
                }
                catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e)
                {
                    LogManager.getLogger(SpecimenManager.class).error(e);
                }
            }
            ret.add(summary);
        });

        RequestSummaryByVisitType[] summaries = ret.toArray(new RequestSummaryByVisitType[ret.size()]);

        if (includeParticipantGroups)
            setSummaryParticipantGroups(sql, params, null, summaries, subjectCol, "Visit");

        return summaries;
    }
    public Collection<SummaryByVisitParticipant> getParticipantSummaryByVisitType(Container container, User user,
                                                                                  SimpleFilter specimenDetailFilter, CustomView baseView, CohortFilter.Type cohortType)
    {
        if (specimenDetailFilter == null)
            specimenDetailFilter = new SimpleFilter();
        else
        {
            SimpleFilter clone = new SimpleFilter();
            clone.addAllClauses(specimenDetailFilter);
            specimenDetailFilter = clone;
        }
        SpecimenDetailQueryHelper sqlHelper = getSpecimenDetailQueryHelper(container, user, baseView, specimenDetailFilter, null);
        String subjectCol = StudyService.get().getSubjectColumnName(container);
        SQLFragment cohortJoinClause = null;
        switch (cohortType)
        {
            case DATA_COLLECTION:
                cohortJoinClause = new SQLFragment(
                        "LEFT OUTER JOIN study.ParticipantVisit ON\n " +
                                "\tSpecimenQuery.SequenceNum = study.ParticipantVisit.SequenceNum AND\n" +
                                "\tSpecimenQuery." + subjectCol + " = study.ParticipantVisit.ParticipantId AND\n" +
                                "\tSpecimenQuery.Container = study.ParticipantVisit.Container\n" +
                                "LEFT OUTER JOIN study.Cohort ON \n" +
                                "\tstudy.ParticipantVisit.CohortId = study.Cohort.RowId AND\n" +
                                "\tstudy.ParticipantVisit.Container = study.Cohort.Container\n"
                );
                break;
            case PTID_CURRENT:
                cohortJoinClause = new SQLFragment(
                        "LEFT OUTER JOIN study.Cohort ON \n" +
                                "\tstudy.Participant.CurrentCohortId = study.Cohort.RowId AND\n" +
                                "\tstudy.Participant.Container = study.Cohort.Container\n"
                );
                break;
            case PTID_INITIAL:
                cohortJoinClause = new SQLFragment(
                        "LEFT OUTER JOIN study.Cohort ON \n" +
                                "\tstudy.Participant.InitialCohortId = study.Cohort.RowId AND\n" +
                                "\tstudy.Participant.Container = study.Cohort.Container\n"
                );
                break;
        }

        SQLFragment ptidSpecimenSQL = new SQLFragment();
        SqlDialect d = SpecimenSchema.get().getSqlDialect();
        ptidSpecimenSQL.appendComment("<getParticipantSummaryByVisitType>", d);
        ptidSpecimenSQL.append("SELECT SpecimenQuery.Visit AS Visit, SpecimenQuery.").append(subjectCol).append(" AS ParticipantId,\n")
                .append("COUNT(*) AS VialCount, study.Cohort.Label AS Cohort, SUM(SpecimenQuery.Volume) AS TotalVolume\n").append("FROM (");
        ptidSpecimenSQL.append(sqlHelper.getViewSql());
        ptidSpecimenSQL.append(") AS SpecimenQuery\n" + "LEFT OUTER JOIN study.Participant ON\n" + "\tSpecimenQuery.").append(subjectCol).append(" = study.Participant.ParticipantId AND\n").append("\tSpecimenQuery.Container = study.Participant.Container\n");
        ptidSpecimenSQL.append(cohortJoinClause);
        ptidSpecimenSQL.append("GROUP BY study.Cohort.Label, SpecimenQuery.").append(subjectCol).append(", Visit\n").append("ORDER BY study.Cohort.Label, SpecimenQuery.").append(subjectCol).append(", Visit");
        ptidSpecimenSQL.appendComment("</getParticipantSummaryByVisitType>", d);

        return new SqlSelector(SpecimenSchema.get().getSchema(), ptidSpecimenSQL).getCollection(SummaryByVisitParticipant.class);
    }
}
