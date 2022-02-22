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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.specimen.location.LocationCache;
import org.labkey.api.specimen.model.SpecimenComment;
import org.labkey.api.specimen.model.SpecimenTablesProvider;
import org.labkey.api.specimen.requirements.SpecimenRequestRequirementProvider;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.Visit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        SpecimenMigrationService SMS = SpecimenMigrationService.get();
        if (null != SMS)
            SMS.clearRequestCaches(visit.getContainer());
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
        {
            SpecimenMigrationService SMS = SpecimenMigrationService.get();
            if (null != SMS)
                SMS.clearRequestCaches(vial.getContainer());
        }
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

        SpecimenMigrationService SMS = SpecimenMigrationService.get();
        if (null != SMS)
            SMS.clearGroupedValuesForColumn(c);
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
}
