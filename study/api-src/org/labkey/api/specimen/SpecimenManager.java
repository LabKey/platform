/*
 * Copyright (c) 2014-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.specimen;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.specimen.location.LocationCache;
import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.ExtendedSpecimenRequestView;
import org.labkey.api.specimen.model.SpecimenComment;
import org.labkey.api.specimen.model.SpecimenTablesProvider;
import org.labkey.api.specimen.report.RequestSummaryByVisitType;
import org.labkey.api.specimen.report.SummaryByVisitParticipant;
import org.labkey.api.specimen.report.SummaryByVisitType;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.study.Cohort;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    public boolean isSpecimenModuleActive(Container c)
    {
        Module specimenModule = ModuleLoader.getInstance().getModule("Specimen");
        return null != specimenModule && c.getActiveModules().contains(specimenModule);
    }

    public long getMaxExternalId(Container container)
    {
        TableInfo tableInfo = SpecimenSchema.get().getTableInfoSpecimenEvent(container);
        SQLFragment sql = new SQLFragment("SELECT MAX(ExternalId) FROM ");
        sql.append(tableInfo.getSelectName());
        return new SqlSelector(tableInfo.getSchema(), sql).getArrayList(Long.class).get(0);
    }

    public Map<String, Integer> getSpecimenCounts(Container container, Collection<String> specimenHashes)
    {
        TableInfo tableInfoSpecimen = SpecimenSchema.get().getTableInfoSpecimen(container);

        SQLFragment extraClause = null;
        if (specimenHashes != null)
        {
            extraClause = new SQLFragment(" WHERE SpecimenHash ");
            tableInfoSpecimen.getSqlDialect().appendInClauseSql(extraClause, specimenHashes);
        }

        final Map<String, Integer> map = new HashMap<>();

        SQLFragment sql = new SQLFragment("SELECT SpecimenHash, CAST(AvailableCount AS Integer) AS AvailableCount FROM ");
        sql.append(tableInfoSpecimen.getFromSQL(""));
        if (extraClause != null)
        {
            sql.append(extraClause);
        }
        new SqlSelector(SpecimenSchema.get().getSchema(), sql).forEach(rs -> {
            String specimenHash = rs.getString("SpecimenHash");
            map.put(specimenHash, rs.getInt("AvailableCount"));
        });

        return map;
    }

    public int getSpecimenCountForVisit(Visit visit)
    {
        Container container = visit.getContainer();
        TableInfo tableInfoSpecimen = SpecimenSchema.get().getTableInfoSpecimen(container);
        TableInfo tableInfoVial = SpecimenSchema.get().getTableInfoVial(container);

        String tableInfoSpecimenAlias = "Specimen";
        String tableInfoVialAlias = "Vial";
        SQLFragment visitRangeSql = getVisitRangeSql(visit, tableInfoSpecimen, tableInfoSpecimenAlias);
        if (null == visitRangeSql)
            return 0;

        SQLFragment sql = new SQLFragment("SELECT COUNT(*) AS NumVials FROM ");
        sql.append(tableInfoVial.getFromSQL(tableInfoVialAlias)).append(" \n")
                .append("LEFT OUTER JOIN ").append(tableInfoSpecimen.getFromSQL(tableInfoSpecimenAlias)).append(" ON\n\t")
                .append(tableInfoVial.getColumn("SpecimenId").getValueSql(tableInfoVialAlias)).append(" = ")
                .append(tableInfoSpecimen.getColumn("RowId").getValueSql(tableInfoSpecimenAlias))
                .append("\n WHERE ").append(visitRangeSql);
        List<Integer> results = new SqlSelector(SpecimenSchema.get().getSchema(), sql).getArrayList(Integer.class);
        if (1 != results.size())
            throw new IllegalStateException("Expected value from Select Count(*)");
        return results.get(0);
    }

    public void deleteSpecimensForVisit(Visit visit)
    {
        Container container = visit.getContainer();
        TableInfo tableInfoSpecimen = SpecimenSchema.get().getTableInfoSpecimen(container);
        TableInfo tableInfoSpecimenEvent = SpecimenSchema.get().getTableInfoSpecimenEvent(container);
        TableInfo tableInfoVial = SpecimenSchema.get().getTableInfoVial(container);

        String tableInfoSpecimenSelectName = tableInfoSpecimen.getSelectName();
        String tableInfoSpecimenEventSelectName = tableInfoSpecimenEvent.getSelectName();
        String tableInfoVialSelectName = tableInfoVial.getSelectName();

        // Fix 19048 (dave); handle "no visit" case better; would be better to call once and get both variations
        SQLFragment visitRangeSql1 = getVisitRangeSql(visit, tableInfoSpecimen, tableInfoSpecimenSelectName);
        SQLFragment visitRangeSql2 = getVisitRangeSql(visit, tableInfoSpecimen, "Specimen");
        if (null == visitRangeSql1 || null == visitRangeSql2)
            return;

        SQLFragment specimenRowIdWhereSql = new SQLFragment(" WHERE ").append(visitRangeSql2);

        SQLFragment deleteEventSql = new SQLFragment("DELETE FROM ");
        deleteEventSql.append(tableInfoSpecimenEventSelectName)
                .append(" WHERE RowId IN (\n")
                .append("SELECT Event.RowId FROM ")
                .append(tableInfoSpecimenEventSelectName).append(" AS Event\n")
                .append("LEFT OUTER JOIN ").append(tableInfoVialSelectName).append(" AS Vial ON\n")
                .append("\tEvent.VialId = Vial.RowId\n")
                .append("LEFT OUTER JOIN ").append(tableInfoSpecimenSelectName).append(" AS Specimen ON\n")
                .append("\tVial.SpecimenId = Specimen.RowId\n")
                .append(specimenRowIdWhereSql).append(")");
        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(deleteEventSql);

        SQLFragment deleteVialSql = new SQLFragment("DELETE FROM ");
        deleteVialSql.append(tableInfoVialSelectName)
                .append(" WHERE RowId IN (\n")
                .append("SELECT Vial.RowId FROM ")
                .append(tableInfoVialSelectName).append(" AS Vial\n")
                .append("LEFT OUTER JOIN ").append(tableInfoSpecimenSelectName).append(" AS Specimen ON\n")
                .append("\tVial.SpecimenId = Specimen.RowId\n")
                .append(specimenRowIdWhereSql).append(")");

        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(deleteVialSql);

        SQLFragment specimenRowIdSelectSql = new SQLFragment("FROM " + tableInfoSpecimen.getSelectName() + " WHERE ").append(visitRangeSql1);
        SQLFragment deleteSpecimenSql = new SQLFragment("DELETE ");
        deleteSpecimenSql.append(specimenRowIdSelectSql);

        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(deleteSpecimenSql);

        SpecimenRequestManager.get().clearCaches(visit.getContainer());
    }

    @Nullable
    private SQLFragment getVisitRangeSql(Visit visit, TableInfo tinfoSpecimen, String specimenAlias)
    {
        // Return null only in the case where there are 0 participant visits for the given visit
        Study study = StudyService.get().getStudy(visit.getContainer());
        if (null == study)
            throw new IllegalStateException("No study found.");

        SQLFragment sqlVisitRange = new SQLFragment();
        sqlVisitRange.append(tinfoSpecimen.getColumn("VisitValue").getValueSql(specimenAlias)).append(" >= ? AND ")
                .append(tinfoSpecimen.getColumn("VisitValue").getValueSql(specimenAlias)).append(" <= ?");

        SQLFragment sql = new SQLFragment();
        if (TimepointType.VISIT == study.getTimepointType())
        {
            sql.append(sqlVisitRange);
            sql.add(visit.getSequenceNumMin());
            sql.add(visit.getSequenceNumMax());
        }
        else
        {
            // For date-based we need to get the range from ParticipantVisit
            ColumnInfo columnInfo = SpecimenSchema.get().getTableInfoParticipantVisit().getColumn("SequenceNum");
            Filter filter = new SimpleFilter(FieldKey.fromString("VisitRowId"), visit.getId());
            Sort sort = new Sort();
            sort.insertSortColumn(FieldKey.fromString("SequenceNum"), Sort.SortDirection.ASC);
            ArrayList<Double> visitValues = new TableSelector(columnInfo, filter, sort).getArrayList(Double.class);
            if (0 == visitValues.size())
            {
                // No participant visits for this timepoint; return False
                return null;
            }
            else
            {
                sql.append(sqlVisitRange);
                sql.add(visitValues.get(0));
                sql.add(visitValues.get(visitValues.size() - 1));
            }
        }
        return sql;
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
            SpecimenRequestManager.get().clearCaches(vial.getContainer());
    }

    public void deleteAllSpecimenData(Container c, Set<TableInfo> set, User user)
    {
        // UNDONE: use transaction?
        SimpleFilter containerFilter = SimpleFilter.createContainerFilter(c);

        Table.delete(SpecimenSchema.get().getTableInfoSampleRequestSpecimen(), containerFilter);
        assert set.add(SpecimenSchema.get().getTableInfoSampleRequestSpecimen());
        Table.delete(SpecimenSchema.get().getTableInfoSampleRequestEvent(), containerFilter);
        assert set.add(SpecimenSchema.get().getTableInfoSampleRequestEvent());
        Table.delete(SpecimenSchema.get().getTableInfoSampleRequest(), containerFilter);
        assert set.add(SpecimenSchema.get().getTableInfoSampleRequest());
        Table.delete(SpecimenSchema.get().getTableInfoSampleRequestStatus(), containerFilter);
        assert set.add(SpecimenSchema.get().getTableInfoSampleRequestStatus());

        new SpecimenTablesProvider(c, null, null).deleteTables();
        LocationCache.clear(c);

        Table.delete(SpecimenSchema.get().getTableInfoSampleAvailabilityRule(), containerFilter);
        assert set.add(SpecimenSchema.get().getTableInfoSampleAvailabilityRule());

        SpecimenRequestRequirementProvider.get().purgeContainer(c);
        assert set.add(SpecimenSchema.get().getTableInfoSampleRequestRequirement());
        assert set.add(SpecimenSchema.get().getTableInfoSampleRequestActor());

        DbSchema expSchema = ExperimentService.get().getSchema();
        TableInfo tinfoMaterial = expSchema.getTable("Material");

        ExpSampleType sampleType = SampleTypeService.get().getSampleType(c, SpecimenService.SAMPLE_TYPE_NAME);

        if (sampleType != null)
        {
            // Check if any of the samples are referenced in an experiment run
            SQLFragment sql = new SQLFragment("SELECT m.RowId FROM ");
            sql.append(ExperimentService.get().getTinfoMaterial(), "m");
            sql.append(" INNER JOIN ");
            sql.append(ExperimentService.get().getTinfoMaterialInput(), "mi");
            sql.append(" ON m.RowId = mi.MaterialId AND m.CpasType = ?");
            sql.add(sampleType.getLSID());

            if (new SqlSelector(ExperimentService.get().getSchema(), sql).exists())
            {
                // If so, do the slow version of the delete that tears down runs
                sampleType.delete(user);
            }
            else
            {
                // If not, do the quick version that just kills the samples themselves in the exp.Material table
                SimpleFilter materialFilter = new SimpleFilter(containerFilter);
                materialFilter.addCondition(FieldKey.fromParts("CpasType"), sampleType.getLSID());
                Table.delete(tinfoMaterial, materialFilter);
            }
        }

        // VIEW: if this view gets removed, remove this line
        assert set.add(SpecimenSchema.get().getSchema().getTable("LockedSpecimens"));

        SpecimenRequestManager.get().clearGroupedValuesForColumn(c);
    }

    public List<? extends Visit> getVisitsWithSpecimens(Container container, User user)
    {
        return getVisitsWithSpecimens(container, user, null);
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

    private String getPtidListKey(Integer visit, String primaryType, String derivativeType, String additiveType)
    {
        return visit + "/" + primaryType + "/" +
            (derivativeType != null ? derivativeType : "all") +
            (additiveType != null ? additiveType : "all");
    }

    public LocationImpl[] getSitesWithRequests(Container container)
    {
        TableInfo locationTableInfo = SpecimenSchema.get().getTableInfoLocation(container);
        SQLFragment sql = new SQLFragment("SELECT * FROM " + locationTableInfo.getSelectName() + " WHERE rowid IN\n" +
                "(SELECT destinationsiteid FROM study.samplerequest WHERE container = ?)\n" +
                "AND container = ? ORDER BY label", container.getId(), container.getId());

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

    public SpecimenComment[] getSpecimenCommentForSpecimen(Container container, String specimenHash)
    {
        return getSpecimenCommentForSpecimens(container, Collections.singleton(specimenHash));
    }

    public SpecimenComment[] getSpecimenCommentForSpecimens(Container container, Collection<String> specimenHashes)
    {
        SimpleFilter hashFilter = SimpleFilter.createContainerFilter(container);
        hashFilter.addInClause(FieldKey.fromParts("SpecimenHash"), specimenHashes);

        return new TableSelector(SpecimenSchema.get().getTableInfoSpecimenComment(), hashFilter, new Sort("GlobalUniqueId")).getArray(SpecimenComment.class);
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
}
