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
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.specimen.model.SpecimenComment;
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
        sql.append(tableInfo);
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

        SQLFragment specimenRowIdSelectSql = new SQLFragment("FROM " ).append( tableInfoSpecimen ).append( " WHERE ").append(visitRangeSql1);
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

        if (TimepointType.VISIT == study.getTimepointType())
        {
            sqlVisitRange.add(visit.getSequenceNumMin());
            sqlVisitRange.add(visit.getSequenceNumMax());
        }
        else
        {
            // For date-based we need to get the range from ParticipantVisit
            ColumnInfo columnInfo = SpecimenSchema.get().getTableInfoParticipantVisit().getColumn("SequenceNum");
            Filter filter = new SimpleFilter(FieldKey.fromString("VisitRowId"), visit.getId());
            Sort sort = new Sort();
            sort.insertSortColumn(FieldKey.fromString("SequenceNum"), Sort.SortDirection.ASC);
            ArrayList<Double> visitValues = new TableSelector(columnInfo, filter, sort).getArrayList(Double.class);
            if (visitValues.isEmpty())
            {
                // No participant visits for this timepoint; return False
                return null;
            }
            else
            {
                sqlVisitRange.add(visitValues.get(0));
                sqlVisitRange.add(visitValues.get(visitValues.size() - 1));
            }
        }
        return sqlVisitRange;
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
