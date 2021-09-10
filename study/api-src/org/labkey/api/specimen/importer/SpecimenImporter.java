/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.specimen.importer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.dataiterator.Pump;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.list.ListImportProgress;
import org.labkey.api.iterator.MarkableIterator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.specimen.SpecimenColumns;
import org.labkey.api.specimen.SpecimenEvent;
import org.labkey.api.specimen.SpecimenEventDateComparator;
import org.labkey.api.specimen.SpecimenEventManager;
import org.labkey.api.specimen.SpecimenRequestManager;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.specimen.SpecimenTableManager;
import org.labkey.api.specimen.Vial;
import org.labkey.api.specimen.location.LocationCache;
import org.labkey.api.specimen.location.LocationManager;
import org.labkey.api.specimen.model.SpecimenComment;
import org.labkey.api.specimen.settings.SettingsManager;
import org.labkey.api.study.Location;
import org.labkey.api.study.SpecimenImportStrategy;
import org.labkey.api.study.SpecimenImportStrategyFactory;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyInternalService;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.StudyUtils;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.importer.ImportHelperService;
import org.labkey.api.study.importer.ImportHelperService.ParticipantIdTranslator;
import org.labkey.api.study.importer.ImportHelperService.SequenceNumTranslator;
import org.labkey.api.study.importer.SimpleStudyImportContext;
import org.labkey.api.study.model.VisitService;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.MultiPhaseCPUTimer;
import org.labkey.api.util.MultiPhaseCPUTimer.InvocationTimer.Order;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.writer.VirtualFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.labkey.api.specimen.SpecimenColumns.DRAW_TIMESTAMP;
import static org.labkey.api.specimen.SpecimenColumns.GLOBAL_UNIQUE_ID;
import static org.labkey.api.specimen.SpecimenColumns.GLOBAL_UNIQUE_ID_TSV_COL;
import static org.labkey.api.specimen.SpecimenColumns.LAB_ID;
import static org.labkey.api.specimen.SpecimenColumns.LAB_RECEIPT_DATE;
import static org.labkey.api.specimen.SpecimenColumns.SHIP_DATE;
import static org.labkey.api.specimen.SpecimenColumns.SITE_COLUMNS;
import static org.labkey.api.specimen.SpecimenColumns.SPEC_NUMBER_TSV_COL;
import static org.labkey.api.specimen.SpecimenColumns.STORAGE_DATE;
import static org.labkey.api.specimen.SpecimenColumns.VISIT_COL;
import static org.labkey.api.specimen.SpecimenColumns.VISIT_VALUE;

/**
 * User: brittp
 * Date: Mar 13, 2006
 * Time: 2:18:48
 */
@SuppressWarnings({"ConstantConditions"})
public class SpecimenImporter extends SpecimenTableManager
{
    private enum ImportPhases {UpdateCommentSpecimenHashes, MarkOrphanedRequestVials, SetLockedInRequest, VialUpdatePreLoopPrep,
        GetVialBatch, GetDateOrderedEvents, GetSpecimenComments, GetProcessingLocationId, GetFirstProcessedBy, GetCurrentLocationId,
        CalculateLocation, GetLastEvent, DetermineUpdateVial, SetUpdateParameters, HandleComments, UpdateVials, UpdateComments,
        UpdateSpecimenProcessingInfo, UpdateRequestability, UpdateVialCounts, ResyncStudy, SetLastSpecimenLoad, DropTempTable,
        UpdateAllStatistics, CommitTransaction, ClearCaches, PopulateMaterials, PopulateSpecimens, PopulateVials, PopulateSpecimenEvents,
        PopulateTempTable, PopulateLabs, SpecimenTypes, DeleteOldData, PrepareQcComments, NotifyChanged}

    private static final MultiPhaseCPUTimer<ImportPhases> TIMER = new MultiPhaseCPUTimer<>(ImportPhases.class, ImportPhases.values());

    private static class SpecimenLoadInfo
    {
        private final TempTableInfo _tempTableInfo;
        private final String _tempTableName;
        private final List<SpecimenColumn> _availableColumns;
        private final int _rowCount;
        private final Container _container;
        private final User _user;
        private final DbSchema _schema;

        public SpecimenLoadInfo(User user, Container container, DbSchema schema, List<SpecimenColumn> availableColumns, int rowCount, TempTableInfo tempTableInfo)
        {
            _user = user;
            _schema = schema;
            _container = container;
            _availableColumns = availableColumns;
            _rowCount = rowCount;
            _tempTableName = tempTableInfo.getSelectName();
            _tempTableInfo = tempTableInfo;
        }

        // Number of rows inserted into the temp table
        public int getRowCount()
        {
            return _rowCount;
        }

        public List<SpecimenColumn> getAvailableColumns()
        {
            return _availableColumns;
        }

        public String getTempTableName()
        {
            return _tempTableName;
        }

        public Container getContainer()
        {
            return _container;
        }

        public User getUser()
        {
            return _user;
        }

        public DbSchema getSchema()
        {
            return _schema;
        }
    }

    private static final String GENERAL_JOB_STATUS_MSG = "PROCESSING SPECIMENS";

    private static final SpecimenColumn DRAW_DATE = new SpecimenColumn("", "DrawDate", "DATE", TargetTable.SPECIMENS);
    private static final SpecimenColumn DRAW_TIME = new SpecimenColumn("", "DrawTime", "TIME", TargetTable.SPECIMENS);

    private List<SpecimenColumn> _specimenCols;
    private List<SpecimenColumn> _vialCols;
    private List<SpecimenColumn> _vialEventCols;
    private String _specimenColsSql;
    private String _vialColsSql;
    private String _vialEventColsSql;
    private Logger _logger;
    private PipelineJob _job;

    protected int _generateGlobalUniqueIds = 0;

    private static final int SQL_BATCH_SIZE = 100;

    protected final SpecimenTableType _labsTableType;
    protected final SpecimenTableType _additivesTableType;
    protected final SpecimenTableType _derivativesTableType;
    protected final SpecimenTableType _primaryTypesTableType;
    protected final SpecimenTableType _specimensTableType;

    public @Nullable SpecimenTableType getForName(String name)
    {
        if (_labsTableType.getName().equalsIgnoreCase(name)) return _labsTableType;
        if (_additivesTableType.getName().equalsIgnoreCase(name)) return _additivesTableType;
        if (_derivativesTableType.getName().equalsIgnoreCase(name)) return _derivativesTableType;
        if (_primaryTypesTableType.getName().equalsIgnoreCase(name)) return _primaryTypesTableType;
        if ("specimens".equalsIgnoreCase(name)) return _specimensTableType;
        return null;
    }

    private MultiPhaseCPUTimer.InvocationTimer<ImportPhases> _iTimer;

    /**
     * Constructor
     * @param container import location
     * @param user user whose permissions will be checked during import
     */
    public SpecimenImporter(Container container, User user)
    {
        super(container, user);

        // The following could move to SpecimenTableManager, but they're only used by the importers
        _specimensTableType = new SpecimenTableType("specimens", "study.Specimen", getSpecimenColumns());
        _labsTableType = new SpecimenTableType("labs",
                SpecimenSchema.get().getTableInfoLocation(getContainer()).getSelectName(), SITE_COLUMNS);
        _additivesTableType = new SpecimenTableType("additives",
                SpecimenSchema.get().getTableInfoSpecimenAdditive(getContainer()).getSelectName(), SpecimenColumns.ADDITIVE_COLUMNS);
        _derivativesTableType = new SpecimenTableType("derivatives",
                SpecimenSchema.get().getTableInfoSpecimenDerivative(getContainer()).getSelectName(), SpecimenColumns.DERIVATIVE_COLUMNS);
        _primaryTypesTableType = new SpecimenTableType("primary_types",
                SpecimenSchema.get().getTableInfoSpecimenPrimaryType(getContainer()).getSelectName(), SpecimenColumns.PRIMARYTYPE_COLUMNS);
    }

    private void resyncStudy(boolean syncParticipantVisit)
    {
        TableInfo tableParticipant = SpecimenSchema.get().getTableInfoParticipant();
        TableInfo tableSpecimen = getTableInfoSpecimen();

        executeSQL(tableParticipant.getSchema(), "INSERT INTO " + tableParticipant.getSelectName() + " (Container, ParticipantId)\n" +
            "SELECT DISTINCT ?, ptid AS ParticipantId\n" +
            "FROM " + tableSpecimen.getSelectName() + "\n" +
            "WHERE ptid IS NOT NULL AND " +
            "ptid NOT IN (SELECT ParticipantId FROM " + tableParticipant.getSelectName() + " WHERE Container = ?)", getContainer(), getContainer());

        if (syncParticipantVisit)
        {
            Study study = StudyService.get().getStudy(getContainer());
            info("Updating study-wide subject/visit information...");
            VisitService.get().updateParticipantVisitsWithCohortUpdate(study, getUser(), _logger);
            info("Subject/visit update complete.");
        }

        info("Updating locations in use...");
        LocationManager.updateLocationTableInUse(getTableInfoLocation(), getContainer());
    }

    private void updateAllStatistics()
    {
        updateStatistics(ExperimentService.get().getTinfoMaterial());
        updateStatistics(getTableInfoSpecimen());
        updateStatistics(getTableInfoVial());
        updateStatistics(getTableInfoSpecimenEvent());
    }

    private boolean updateStatistics(TableInfo tinfo)
    {
        info("Updating statistics for " + tinfo + "...");
        boolean updated = tinfo.getSqlDialect().updateStatistics(tinfo);
        if (updated)
            info("Statistics update " + tinfo + " complete.");
        else
            info("Statistics update not supported for this database type.");
        return updated;
    }

    public void process(VirtualFile specimensDir, boolean merge, SimpleStudyImportContext ctx, @Nullable PipelineJob job, boolean syncParticipantVisit)
            throws IOException, ValidationException
    {
        Map<SpecimenTableType, SpecimenImportFile> sifMap = populateFileMap(specimensDir, new HashMap<>());
        process(sifMap, merge, ctx.getLogger(), job, syncParticipantVisit, false, ctx.isFailForUndefinedVisits());
    }

    protected void process(Map<SpecimenTableType, SpecimenImportFile> sifMap, boolean merge, Logger logger, @Nullable PipelineJob job,
                           boolean syncParticipantVisit, boolean editingSpecimens)
            throws IOException, ValidationException
    {
        process(sifMap, merge, logger, job, syncParticipantVisit, editingSpecimens, false);
    }

    private void process(Map<SpecimenTableType, SpecimenImportFile> sifMap, boolean merge, Logger logger, @Nullable PipelineJob job,
                           boolean syncParticipantVisit, boolean editingSpecimens, boolean failForUndefinedVisits)
            throws IOException, ValidationException
    {
        DbSchema schema = SpecimenSchema.get().getSchema();
        _logger = logger;
        _job = job;

        DbScope scope = schema.getScope();
        boolean commitSuccessfully = false;
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            _iTimer = TIMER.getInvocationTimer();

            setStatus(GENERAL_JOB_STATUS_MSG);
            _iTimer.setPhase(ImportPhases.PopulateLabs);
            if (null != sifMap.get(_labsTableType))
            {
                try
                {
                    mergeTable(schema, sifMap.get(_labsTableType), getTableInfoLocation(), true, true);
                }
                finally
                {
                    LocationCache.clear(getContainer());
                }
            }

            _iTimer.setPhase(ImportPhases.SpecimenTypes);
            if (merge)
            {
                if (null != sifMap.get(_additivesTableType))
                    mergeTable(schema, sifMap.get(_additivesTableType), getTableInfoAdditive(), false, true);
                if (null != sifMap.get(_derivativesTableType))
                    mergeTable(schema, sifMap.get(_derivativesTableType), getTableInfoDerivative(), false, true);
                if (null != sifMap.get(_primaryTypesTableType))
                    mergeTable(schema, sifMap.get(_primaryTypesTableType), getTableInfoPrimaryType(), false, true);
            }
            else
            {
                if (null != sifMap.get(_additivesTableType))
                    replaceTable(schema, sifMap.get(_additivesTableType), getTableInfoAdditive(), false, true);
                if (null != sifMap.get(_derivativesTableType))
                    replaceTable(schema, sifMap.get(_derivativesTableType), getTableInfoDerivative(), false, true);
                if (null != sifMap.get(_primaryTypesTableType))
                    replaceTable(schema, sifMap.get(_primaryTypesTableType), getTableInfoPrimaryType(), false, true);
            }

            // Specimen temp table must be populated AFTER the types tables have been reloaded, since the SpecimenHash
            // calculated in the temp table relies on the new RowIds for the types:
            setStatus(GENERAL_JOB_STATUS_MSG + " (temp table)");
            _iTimer.setPhase(ImportPhases.PopulateTempTable);
            SpecimenImportFile specimenFile = sifMap.get(_specimensTableType);
            SpecimenLoadInfo loadInfo = populateTempSpecimensTable(specimenFile, merge);

            Study study = StudyService.get().getStudy(getContainer());
            if (loadInfo.getRowCount() > 0 && failForUndefinedVisits && study.getTimepointType() == TimepointType.VISIT)
                checkForUndefinedVisits(loadInfo, study);

            // NOTE: if no rows were loaded in the temp table, don't remove existing materials/specimens/vials/events.
            if (loadInfo.getRowCount() > 0)
                populateSpecimenTables(loadInfo, merge);
            else
                info("Specimens: 0 rows found in input");

            if (merge)
            {
                // Delete any orphaned specimen rows without vials
                _iTimer.setPhase(ImportPhases.DeleteOldData);
                executeSQL(SpecimenSchema.get().getSchema(), "DELETE FROM " + getTableInfoSpecimen().getSelectName() +
                                  " WHERE RowId NOT IN (SELECT SpecimenId FROM " + getTableInfoVial().getSelectName() + ")");
            }

            // No need to setPhase() here... method sets timer phases immediately
            updateCalculatedSpecimenData(merge, editingSpecimens);

            setStatus(GENERAL_JOB_STATUS_MSG + " (update study)");
            _iTimer.setPhase(ImportPhases.ResyncStudy);
            resyncStudy(syncParticipantVisit);

            ensureNotCanceled();
            _iTimer.setPhase(ImportPhases.SetLastSpecimenLoad);
            // Set LastSpecimenLoad to now... we'll check this before snapshot study specimen refresh
            StudyService.get().setLastSpecimenLoad( StudyService.get().getStudy(getContainer()), getUser(), new Date());

            _iTimer.setPhase(ImportPhases.DropTempTable);

            // Drop the temp table within the transaction; otherwise, we may get a different connection object,
            // where the table is no longer available.  Note that this means that the temp table will stick around
            // if an exception is thrown during loading, but this is probably okay- the DB will clean it up eventually.
            loadInfo._tempTableInfo.delete();

            _iTimer.setPhase(ImportPhases.UpdateAllStatistics);
            updateAllStatistics();

            // notify listeners that specimens have changed in this container
            setStatus(GENERAL_JOB_STATUS_MSG);
            _iTimer.setPhase(ImportPhases.NotifyChanged);
            SpecimenService.get().fireSpecimensChanged(getContainer(), getUser(), _logger);

            _iTimer.setPhase(ImportPhases.CommitTransaction);
            transaction.commit();
            commitSuccessfully = true;
        }
        finally
        {
            try
            {
                if (commitSuccessfully)
                {
                    _iTimer.setPhase(ImportPhases.ClearCaches);
                    StudyInternalService.get().clearCaches(getContainer());
                    SpecimenRequestManager.get().clearCaches(getContainer());

                    info(_iTimer.getTimings("Timings for each phase of this import are listed below:", Order.HighToLow, "|"));
                }
            }
            finally
            {
                TIMER.releaseInvocationTimer(_iTimer);
            }
        }
    }

    private SpecimenLoadInfo populateTempSpecimensTable(SpecimenImportFile file, boolean merge) throws IOException, ValidationException
    {
        TempTablesHolder tempTablesHolder = createTempTable();

        Pair<List<SpecimenColumn>, Integer> pair = populateTempTable(tempTablesHolder, file, merge);
        List<SpecimenColumn> columns = pair.first;
        Integer rowCount = pair.second;

        tempTablesHolder.getCreateIndexes().run();

        if (tempTablesHolder.getSelectInsertTempTableInfo().isTracking())      // If no specimens we will not have created this table
            tempTablesHolder.getSelectInsertTempTableInfo().delete();

        return new SpecimenLoadInfo(getUser(), getContainer(), DbSchema.getTemp(), columns, rowCount, tempTablesHolder.getTempTableInfo());
    }

    private void checkForUndefinedVisits(SpecimenLoadInfo info, Study study) throws ValidationException
    {
        SQLFragment sql = new SQLFragment()
            .append("SELECT DISTINCT VisitValue FROM ")
            .append(info.getTempTableName())
            .append(" tt ")
            .append("\nLEFT JOIN study.Visit v")
            .append("\nON tt.VisitValue >= v.SequenceNumMin AND tt.VisitValue <=v.SequenceNumMax AND v.Container = ?")
            .append("\nWHERE tt.VisitValue IS NOT NULL AND v.RowId IS NULL");

        // shared visit container
        Study visitStudy = StudyService.get().getStudyForVisits(study);
        sql.add(visitStudy.getContainer().getId());

        SqlSelector selector = new SqlSelector(SpecimenSchema.get().getSchema(), sql);
        List<Double> undefinedVisits = selector.getArrayList(Double.class);
        if (!undefinedVisits.isEmpty())
        {
            Collections.sort(undefinedVisits);
            throw new ValidationException("The following undefined visits exist in the specimen data: " + StringUtils.join(undefinedVisits, ", "));
        }
    }

    private void populateSpecimenTables(SpecimenLoadInfo info, boolean merge) throws ValidationException
    {
        setStatus(GENERAL_JOB_STATUS_MSG + " (populate tables)");
        _iTimer.setPhase(ImportPhases.DeleteOldData);
        if (!merge)
        {
//            SimpleFilter containerFilter = SimpleFilter.createContainerFilter(info.getContainer());
            info("Deleting old data from SpecimenEvent, Vial and Specimen tables...");
            if (getTableInfoSpecimen().getSchema().getSqlDialect().isPostgreSQL())
            {
                SQLFragment sql = new SQLFragment("TRUNCATE ")
                    .append(getTableInfoSpecimenEvent().getSelectName())
                    .append(", ")
                    .append(getTableInfoVial().getSelectName())
                    .append(", ")
                    .append(getTableInfoSpecimen().getSelectName());

                executeSQL(getTableInfoSpecimen().getSchema(), sql);
            }
            else
            {
                Table.delete(getTableInfoSpecimenEvent());
                ensureNotCanceled();
                Table.delete(getTableInfoVial());
                ensureNotCanceled();
                Table.delete(getTableInfoSpecimen());
            }
            info("Complete.");
        }

        boolean seenVisitValue = false;
        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if (VISIT_VALUE.getDbColumnName().equalsIgnoreCase(col.getDbColumnName()))
            {
                seenVisitValue = true;
                break;
            }
        }

        ensureNotCanceled();
        _iTimer.setPhase(ImportPhases.PopulateMaterials);
        populateMaterials(info, merge);
        ensureNotCanceled();
        _iTimer.setPhase(ImportPhases.PopulateSpecimens);
        populateSpecimens(info, merge, seenVisitValue);
        ensureNotCanceled();
        _iTimer.setPhase(ImportPhases.PopulateVials);
        populateVials(info, merge, seenVisitValue);
        ensureNotCanceled();
        _iTimer.setPhase(ImportPhases.PopulateSpecimenEvents);
        populateSpecimenEvents(info, merge);
    }

    private Set<String> getConflictingEventColumns(List<SpecimenEvent> events)
    {
        if (events.size() <= 1)
            return Collections.emptySet();
        Set<String> conflicts = new HashSet<>();

        for (SpecimenColumn col : _specimenColumns)
        {
            if (col.getAggregateEventFunction() == null && col.getTargetTable() == TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS)
            {
                // lower the case of the first character:
                String propName = col.getDbColumnName().substring(0, 1).toLowerCase() + col.getDbColumnName().substring(1);
                for (int i = 0; i < events.size() - 1; i++)
                {
                    SpecimenEvent event = events.get(i);
                    SpecimenEvent nextEvent = events.get(i + 1);
                    Object currentValue = event.get(propName);
                    Object nextValue = nextEvent.get(propName);
                    if (!Objects.equals(currentValue, nextValue))
                    {
                        if (propName.equalsIgnoreCase("drawtimestamp"))
                        {
                            Object currentDateOnlyValue = DateUtil.getDateOnly((Date) currentValue);
                            Object nextDateOnlyValue = DateUtil.getDateOnly((Date) nextValue);
                            if (!Objects.equals(currentDateOnlyValue, nextDateOnlyValue))
                                conflicts.add(DRAW_DATE.getDbColumnName());
                            Object currentTimeOnlyValue = DateUtil.getTimeOnly((Date) currentValue);
                            Object nextTimeOnlyValue = DateUtil.getTimeOnly((Date) nextValue);
                            if (!Objects.equals(currentTimeOnlyValue, nextTimeOnlyValue))
                                conflicts.add(DRAW_TIME.getDbColumnName());
                        }
                        else
                        {
                            conflicts.add(col.getDbColumnName());
                        }
                    }
                }
            }
        }

        return conflicts;
    }

    private void clearConflictingVialColumns(Vial vial, Set<String> conflicts)
    {
        SQLFragment sql = new SQLFragment()
            .append("UPDATE ")
            .append(getTableInfoVial().getSelectName())
            .append(" SET\n  ");

        boolean hasConflict = false;
        String sep = "";
        for (SpecimenColumn col : _specimenColumns)
        {
            if (col.getAggregateEventFunction() == null && col.getTargetTable().isVials() && !col.isUnique())
            {
                if (conflicts.contains(col.getDbColumnName()))
                {
                    hasConflict = true;
                    sql
                        .append(sep)
                        .append(col.getDbColumnName()).append(" = NULL");
                    sep = ",\n  ";
                }
            }
        }

        if (!hasConflict)
            return;

        sql
            .append("\nWHERE GlobalUniqueId = ?")
            .add(vial.getGlobalUniqueId());

        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(sql);
    }

    private void updateCommentSpecimenHashes()
    {
        TableInfo commentTable = SpecimenSchema.get().getTableInfoSpecimenComment();
        String commentTableSelectName = commentTable.getSelectName();
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();

        SQLFragment sql = new SQLFragment()
            .append("UPDATE ")
            .append(commentTableSelectName)
            .append(" SET SpecimenHash = (\n")
            .append("SELECT SpecimenHash FROM ")
            .append(vialTableSelectName).append(" WHERE ")
            .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName))
            .append(" = ")
            .append(commentTable.getColumn("GlobalUniqueId").getValueSql(commentTableSelectName))
            .append(")\nWHERE ")
            .append(commentTable.getColumn("Container").getValueSql(commentTableSelectName))
            .append(" = ?")
            .add(getContainer().getId());

        info("Updating hash codes for existing comments...");
        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(sql);
        info("Complete.");
    }

    private void prepareQCComments()
    {
        StringBuilder columnList = new StringBuilder();
        columnList.append("VialId");
        for (SpecimenColumn col : _specimenColumns)
        {
            if (col.getTargetTable() == TargetTable.SPECIMENS_AND_SPECIMEN_EVENTS && col.getAggregateEventFunction() == null)
            {
                columnList.append(",\n    ");
                columnList.append(col.getDbColumnName());
            }
        }

        // find the global unique ID for those vials with conflicts:
        TableInfo specimenEventTable = getTableInfoSpecimenEvent();
        SQLFragment conflictedGUIDs = new SQLFragment("SELECT GlobalUniqueId FROM ")
            .append(getTableInfoVial(), "vial")
            .append(" WHERE RowId IN (\n")
            .append("SELECT VialId FROM\n")
            .append("(SELECT DISTINCT\n")
            .append(columnList).append("\nFROM ")
            .append(specimenEventTable.getSelectName())
            .append("\nWHERE Obsolete = ")
            .append(specimenEventTable.getSqlDialect().getBooleanFALSE())
            .append("\nGROUP BY\n")
            .append(columnList)
            .append(") ")
            .append("AS DupCheckView\nGROUP BY VialId HAVING Count(VialId) > 1")
            .append("\n)");

        // Delete comments that were holding QC state (and nothing more) for vials that do not currently have any conflicts
        SQLFragment deleteClearedVials = new SQLFragment("DELETE FROM study.SpecimenComment WHERE Container = ? ")
            .add(getContainer().getId())
            .append("AND Comment IS NULL AND QualityControlFlag = ? ")
            .add(Boolean.TRUE)
            .append("AND QualityControlFlagForced = ? ")
            .add(Boolean.FALSE)
            .append("AND GlobalUniqueId NOT IN (")
            .append(conflictedGUIDs)
            .append(");");
        info("Clearing QC flags for vials that no longer have history conflicts...");
        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(deleteClearedVials);
        info("Complete.");


        // Insert placeholder comments for newly discovered QC problems; SpecimenHash will be updated within updateCalculatedSpecimenData, so this
        // doesn't have to be set here.
        SQLFragment insertPlaceholderQCComments = new SQLFragment("INSERT INTO study.SpecimenComment ")
            .append("(GlobalUniqueId, Container, QualityControlFlag, QualityControlFlagForced, Created, CreatedBy, Modified, ModifiedBy) ")
            .append("SELECT GlobalUniqueId, ?, ?, ?, ?, ?, ?, ? ")
            .add(getContainer().getId())
            .add(Boolean.TRUE)
            .add(Boolean.FALSE)
            .add(new Date())
            .add(getUser().getUserId())
            .add(new Date())
            .add(getUser().getUserId())
            .append(" FROM (\n").append(conflictedGUIDs).append(") ConflictedVials\n")
            .append("WHERE GlobalUniqueId NOT IN ")
            .append("(SELECT GlobalUniqueId FROM study.SpecimenComment WHERE Container = ?);")
            .add(getContainer().getId());
        info("Setting QC flags for vials that have new history conflicts...");
        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(insertPlaceholderQCComments);
        info("Complete.");
    }

    private void markOrphanedRequestVials()
    {
        // Mark those global unique IDs that are in requests but are no longer found in the vial table:
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();
        SQLFragment orphanMarkerSql = new SQLFragment()
            .append("UPDATE study.SampleRequestSpecimen SET Orphaned = ? WHERE RowId IN (\n")
            .add(Boolean.TRUE)
            .append("\tSELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen\n")
            .append("\tLEFT OUTER JOIN ")
            .append(vialTableSelectName)
            .append(" ON\n\t\t")
            .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName))
            .append(" = study.SampleRequestSpecimen.SpecimenGlobalUniqueId\n")
            .append("\tWHERE ")
            .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName))
            .append(" IS NULL AND\n")
            .append("\t\tstudy.SampleRequestSpecimen.Container = ?);")
            .add(getContainer().getId());
        info("Marking requested vials that have been orphaned...");

        SqlExecutor executor = new SqlExecutor(SpecimenSchema.get().getSchema());
        executor.execute(orphanMarkerSql);
        info("Complete.");

        // un-mark those global unique IDs that were previously marked as orphaned but are now found in the vial table:
        SQLFragment deorphanMarkerSql = new SQLFragment()
            .append("UPDATE study.SampleRequestSpecimen SET Orphaned = ? WHERE RowId IN (\n")
            .add(Boolean.FALSE)
            .append("\tSELECT study.SampleRequestSpecimen.RowId FROM study.SampleRequestSpecimen\n")
            .append("\tLEFT OUTER JOIN ")
            .append(vialTableSelectName)
            .append(" ON\n\t\t")
            .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName))
            .append(" = study.SampleRequestSpecimen.SpecimenGlobalUniqueId\n")
            .append("\tWHERE ")
            .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName))
            .append(" IS NOT NULL AND\n")
            .append("\t\tstudy.SampleRequestSpecimen.Orphaned = ? AND\n")
            .add(Boolean.TRUE)
            .append("\t\tstudy.SampleRequestSpecimen.Container = ?);")
            .add(getContainer().getId());
        info("Marking requested vials that have been de-orphaned...");
        executor.execute(deorphanMarkerSql);
        info("Complete.");
    }

    private void setLockedInRequestStatus()
    {
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();
        SQLFragment lockedInRequestSql = new SQLFragment("UPDATE ").append(vialTableSelectName).append(
                " SET LockedInRequest = ? WHERE RowId IN (SELECT ").append(vialTable.getColumn("RowId").getValueSql(vialTableSelectName))
                .append(" FROM ").append(vialTableSelectName).append(", study.LockedSpecimens " +
                "WHERE study.LockedSpecimens.Container = ? AND ")
                .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName)).append(" = study.LockedSpecimens.GlobalUniqueId)");

        lockedInRequestSql.add(Boolean.TRUE);
        lockedInRequestSql.add(getContainer().getId());

        info("Setting Specimen Locked in Request status...");
        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(lockedInRequestSql);
        info("Complete.");
    }

    private void updateSpecimenProcessingInfo()
    {
        TableInfo specimenTable = getTableInfoSpecimen();
        String specimenTableSelectName = specimenTable.getSelectName();
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();
        SQLFragment sql = new SQLFragment("UPDATE ").append(specimenTableSelectName).append(" SET ProcessingLocation = (\n" +
                "\tSELECT MAX(ProcessingLocation) AS ProcessingLocation FROM \n" +
                "\t\t(SELECT DISTINCT SpecimenId, ProcessingLocation FROM ").append(vialTableSelectName).append(
                " WHERE SpecimenId = ").append(specimenTable.getColumn("RowId").getValueSql(specimenTableSelectName)).append(") Locations\n" +
                "\tGROUP BY SpecimenId\n" +
                "\tHAVING COUNT(ProcessingLocation) = 1\n" +
                ")");
        info("Updating processing locations on the specimen table...");
        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(sql);
        info("Complete.");

        sql = new SQLFragment("UPDATE ").append(specimenTableSelectName).append(" SET FirstProcessedByInitials = (\n" +
                "\tSELECT MAX(FirstProcessedByInitials) AS FirstProcessedByInitials FROM \n" +
                "\t\t(SELECT DISTINCT SpecimenId, FirstProcessedByInitials FROM ").append(vialTableSelectName).append(
                " WHERE SpecimenId = ").append(specimenTable.getColumn("RowId").getValueSql(specimenTableSelectName)).append(") Locations\n" +
                "\tGROUP BY SpecimenId\n" +
                "\tHAVING COUNT(FirstProcessedByInitials) = 1\n" +
                ")");
        info("Updating first processed by initials on the specimen table...");
        new SqlExecutor(SpecimenSchema.get().getSchema()).execute(sql);
        info("Complete.");
    }

    private static final int CURRENT_SITE_UPDATE_SIZE = 1000;
    private static final int CURRENT_SITE_UPDATE_LOGGING_SIZE = 10000;   // Can choose to log at a less frequent rate than the update batch size

    // UNDONE: add vials in-clause to only update data for rows that changed
    private void updateCalculatedSpecimenData(final boolean merge, final boolean editingSpecimens)
    {
        setStatus(GENERAL_JOB_STATUS_MSG + " (update)");
        _iTimer.setPhase(ImportPhases.PrepareQcComments);
        // delete unnecessary comments and create placeholders for newly discovered errors:
        prepareQCComments();

        _iTimer.setPhase(ImportPhases.UpdateCommentSpecimenHashes);
        updateCommentSpecimenHashes();

        _iTimer.setPhase(ImportPhases.MarkOrphanedRequestVials);
        markOrphanedRequestVials();
        _iTimer.setPhase(ImportPhases.SetLockedInRequest);
        setLockedInRequestStatus();

        _iTimer.setPhase(ImportPhases.VialUpdatePreLoopPrep);
        // clear caches before determining current sites:
        SpecimenRequestManager.get().clearCaches(getContainer());
        final Map<Integer, Location> siteMap = new HashMap<>();

        TableInfo vialTable = getTableInfoVial();
        StringBuilder vialPropertiesSB = new StringBuilder("UPDATE ").append(vialTable.getSelectName())
            .append(" SET CurrentLocation = CAST(? AS INTEGER), ProcessingLocation = CAST(? AS INTEGER), FirstProcessedByInitials = ?, AtRepository = ?, LatestComments = ?, LatestQualityComments = ? ");

        for (List<RollupInstance<EventVialRollup>> rollupList : getEventToVialRollups().values())
        {
            for (RollupInstance<EventVialRollup> rollup : rollupList)
            {
                String colName = rollup.first;
                ColumnInfo column = vialTable.getColumn(colName);
                if (null == column)
                    throw new IllegalStateException("Expected Vial table column to exist.");
                vialPropertiesSB.append(", ").append(column.getSelectName()).append(" = ")
                    .append(JdbcType.VARCHAR.equals(column.getJdbcType()) ? "?" : "CAST(? AS " + vialTable.getSqlDialect().getSqlCastTypeName(column.getJdbcType()) + ")");
            }
        }

        vialPropertiesSB.append(" WHERE RowId = ?");

        final String vialPropertiesSql = vialPropertiesSB.toString();

        _iTimer.setPhase(ImportPhases.HandleComments);

        TableInfo commentTable = SpecimenSchema.get().getTableInfoSpecimenComment();
        final String updateCommentSql = "UPDATE " + commentTable + " SET QualityControlComments = ? WHERE GlobalUniqueId = ?";

        // Populate a GlobalUniqueId -> SpecimenEvent map containing all quality control vial comments in this container
        final Map<String, SpecimenComment> qcCommentMap = new HashMap<>();

        SQLFragment selectCommentsSql = new SQLFragment()
            .append("SELECT c.* FROM ")
            .append(commentTable, "c")
            .append(" INNER JOIN ")
            .append(getTableInfoVial(), "v")
            .append(" ON c.GlobalUniqueId = v.GlobalUniqueId WHERE Container = ? AND (QualityControlFlag = ? OR QualityControlFlagForced = ?)")
            .add(getContainer())
            .add(true)
            .add(true);

        new SqlSelector(SpecimenSchema.get().getSchema(), selectCommentsSql).forEach(SpecimenComment.class, comment -> qcCommentMap.put(comment.getGlobalUniqueId(), comment));

//        if (!merge)
//            new SpecimenTablesProvider(getContainer(), getUser(), null).dropTableIndices(SpecimenTablesProvider.VIAL_TABLENAME);

        // TODO: Select only required subset of Event and Vial columns?
        _iTimer.setPhase(ImportPhases.GetDateOrderedEvents);

        TableSelector eventSelector = new TableSelector(getTableInfoSpecimenEvent(), new SimpleFilter(FieldKey.fromString("Obsolete"), false), new Sort("VialId"));

        try (Results eventResults = eventSelector.getResults(false))
        {
            final MutableInt rowCount = new MutableInt();
            final MarkableIterator<Map<String, Object>> eventIterator = new MarkableIterator<>(eventResults.iterator());

            _iTimer.setPhase(ImportPhases.GetVialBatch);
            TableSelector vialSelector = new TableSelector(getTableInfoVial(), null, new Sort("RowId"));

            vialSelector.forEachMapBatch(CURRENT_SITE_UPDATE_SIZE, vialBatch -> {
                int count = rowCount.intValue();
                if (count % CURRENT_SITE_UPDATE_LOGGING_SIZE == 0)
                    info("Updating vial rows " + (count + 1) + " through " + (count + CURRENT_SITE_UPDATE_LOGGING_SIZE) + ".");

                setStatus(GENERAL_JOB_STATUS_MSG + " (update vials)");

                final List<Vial> vials = new ArrayList<>(CURRENT_SITE_UPDATE_SIZE);

                for (Map<String, Object> map : vialBatch)
                    vials.add(new Vial(getContainer(), map));

                List<List<?>> vialPropertiesParams = new ArrayList<>(CURRENT_SITE_UPDATE_SIZE);
                List<List<?>> commentParams = new ArrayList<>();

                for (Vial vial : vials)
                {
                    long vialId = vial.getRowId();

                    _iTimer.setPhase(ImportPhases.GetDateOrderedEvents);
                    List<SpecimenEvent> dateOrderedEvents = new ArrayList<>();
                    while (eventIterator.hasNext())
                    {
                        eventIterator.mark();
                        Map<String, Object> map = eventIterator.next();

                        if (vialId == (Long) map.get("VialId"))
                        {
                            dateOrderedEvents.add(new SpecimenEvent(getContainer(), map));
                        }
                        else
                        {
                            eventIterator.reset();
                            break;
                        }
                    }
                    dateOrderedEvents.sort(SpecimenEventDateComparator.get());

                    _iTimer.setPhase(ImportPhases.GetProcessingLocationId);
                    Integer processingLocation = LocationManager.get().getProcessingLocationId(dateOrderedEvents);
                    _iTimer.setPhase(ImportPhases.GetFirstProcessedBy);
                    String firstProcessedByInitials = SpecimenEventManager.get().getFirstProcessedByInitials(dateOrderedEvents);
                    _iTimer.setPhase(ImportPhases.GetCurrentLocationId);
                    Integer currentLocation = LocationManager.get().getCurrentLocationId(dateOrderedEvents);

                    _iTimer.setPhase(ImportPhases.CalculateLocation);
                    boolean atRepository = false;

                    if (currentLocation != null)
                    {
                        Location location;

                        if (!siteMap.containsKey(currentLocation))
                        {
                            location = LocationManager.get().getLocation(getContainer(), currentLocation);
                            if (location != null)
                                siteMap.put(currentLocation, location);
                        }
                        else
                        {
                            location = siteMap.get(currentLocation);
                        }

                        if (location != null)
                            atRepository = location.isRepository() != null && location.isRepository();
                    }

                    // All of the additional fields (deviationCodes, Concentration, Integrity, Yield, Ratio, QualityComments, Comments) always take the latest value
                    _iTimer.setPhase(ImportPhases.GetLastEvent);
                    SpecimenEvent lastEvent = SpecimenEventManager.get().getLastEvent(dateOrderedEvents);
                    if (null == lastEvent)
                        throw new IllegalStateException("There should always be at least 1 event.");

                    _iTimer.setPhase(ImportPhases.DetermineUpdateVial);
                    boolean updateVial = false;
                    List<Object> params = new ArrayList<>();

                    if (!Objects.equals(currentLocation, vial.getCurrentLocation()) ||
                            !Objects.equals(processingLocation, vial.getProcessingLocation()) ||
                            !Objects.equals(firstProcessedByInitials, vial.getFirstProcessedByInitials()) ||
                            atRepository != vial.isAtRepository() ||
                            !Objects.equals(vial.getLatestComments(), lastEvent.getComments()) ||
                            !Objects.equals(vial.getLatestQualityComments(), lastEvent.getQualityComments()))
                    {
                        updateVial = true;          // Something is different
                    }

                    if (!updateVial)
                    {
                        for (Map.Entry<String, List<RollupInstance<EventVialRollup>>> rollupEntry : getEventToVialRollups().entrySet())
                        {
                            String eventColName = rollupEntry.getKey();
                            ColumnInfo column = getTableInfoSpecimenEvent().getColumn(eventColName);
                            if (null == column)
                                throw new IllegalStateException("Expected Specimen Event table column to exist.");
                            String eventColSelectName = column.getSelectName();
                            for (RollupInstance<EventVialRollup> rollupItem : rollupEntry.getValue())
                            {
                                String vialColName = rollupItem.first;
                                Object rollupResult = rollupItem.second.getRollupResult(dateOrderedEvents, eventColSelectName,
                                        rollupItem.getFromType(), rollupItem.getToType());
                                if (!Objects.equals(vial.get(vialColName), rollupResult))
                                {
                                    updateVial = true;      // Something is different
                                    break;
                                }
                            }
                            if (updateVial)
                                break;
                        }
                    }

                    _iTimer.setPhase(ImportPhases.SetUpdateParameters);
                    if (updateVial)
                    {
                        // Something is different; update everything
                        params.add(currentLocation);
                        params.add(processingLocation);
                        params.add(firstProcessedByInitials);
                        params.add(atRepository);
                        params.add(lastEvent.getComments());
                        params.add(lastEvent.getQualityComments());

                        for (Map.Entry<String, List<RollupInstance<EventVialRollup>>> rollupEntry : getEventToVialRollups().entrySet())
                        {
                            String eventColName = rollupEntry.getKey();
                            ColumnInfo column = getTableInfoSpecimenEvent().getColumn(eventColName);
                            if (null == column)
                                throw new IllegalStateException("Expected Specimen Event table column to exist.");
                            String eventColAlias = column.getAlias();     // Use alias since we're looking up in the rowMap
                            for (RollupInstance<EventVialRollup> rollupItem : rollupEntry.getValue())
                            {
                                Object rollupResult = rollupItem.second.getRollupResult(dateOrderedEvents, eventColAlias,
                                        rollupItem.getFromType(), rollupItem.getToType());
                                params.add(rollupResult);
                            }
                        }

                        params.add(vial.getRowId());
                        vialPropertiesParams.add(params);
                    }

                    _iTimer.setPhase(ImportPhases.HandleComments);
                    SpecimenComment comment = qcCommentMap.get(vial.getGlobalUniqueId());

                    if (comment != null)
                    {
                        // if we have a comment, it may be because we're in a bad QC state. If so, we should update
                        // the reason for the QC problem.
                        String message = null;

                        Set<String> conflicts = getConflictingEventColumns(dateOrderedEvents);

                        if (!conflicts.isEmpty())
                        {
                            // Null out conflicting Vial columns
                            if (merge)
                            {
                                // NOTE: in checkForConflictingSpecimens() we check the imported specimen columns used
                                // to generate the specimen hash are not in conflict so we shouldn't need to clear any
                                // columns on the specimen table. Vial columns are not part of the specimen hash and
                                // can safely be cleared without compromising the specimen hash.
                                clearConflictingVialColumns(vial, conflicts);
                            }

                            String sep = "";
                            message = "Conflicts found: ";
                            for (String conflict : conflicts)
                            {
                                message += sep + conflict;
                                sep = ", ";
                            }
                        }

                        commentParams.add(Arrays.asList(message, vial.getGlobalUniqueId()));
                    }
                }

                _iTimer.setPhase(ImportPhases.UpdateVials);
                if (!vialPropertiesParams.isEmpty())
                    Table.batchExecute(SpecimenSchema.get().getSchema(), vialPropertiesSql, vialPropertiesParams);

                _iTimer.setPhase(ImportPhases.UpdateComments);
                if (!commentParams.isEmpty())
                    Table.batchExecute(SpecimenSchema.get().getSchema(), updateCommentSql, commentParams);

                rowCount.add(CURRENT_SITE_UPDATE_SIZE);
                _iTimer.setPhase(ImportPhases.GetVialBatch);
            });
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

//        if (!merge)
//            new SpecimenTablesProvider(getContainer(), getUser(), null).addTableIndices(SpecimenTablesProvider.VIAL_TABLENAME);

        // finally, after all other data has been updated, we can update our cached specimen counts and processing locations:
        setStatus(GENERAL_JOB_STATUS_MSG + " (update counts)");
        _iTimer.setPhase(ImportPhases.UpdateSpecimenProcessingInfo);
        updateSpecimenProcessingInfo();

        _iTimer.setPhase(ImportPhases.UpdateRequestability);
        try
        {
            RequestabilityManager.getInstance().updateRequestability(getContainer(), getUser(), false, editingSpecimens, _logger);
        }
        catch (RequestabilityManager.InvalidRuleException e)
        {
            throw new IllegalStateException("One or more requestability rules is invalid.  Please remove or correct the invalid rule.", e);
        }

        _iTimer.setPhase(ImportPhases.UpdateVialCounts);
        info("Updating cached vial counts...");

        SpecimenRequestManager.get().updateVialCounts(getContainer(), getUser());

        info("Vial count update complete.");
    }
    
    private Map<SpecimenTableType, SpecimenImportFile> populateFileMap(VirtualFile dir, Map<SpecimenTableType, SpecimenImportFile> fileNameMap) throws IOException
    {
        for (String dirName : dir.listDirs())
        {
            populateFileMap(dir.getDir(dirName), fileNameMap);
        }

        for (String fileName : dir.list())
        {
            if (!fileName.toLowerCase().endsWith(".tsv"))
                continue;

            try (BufferedReader reader = Readers.getReader(dir.getInputStream(fileName)))
            {
                String line = reader.readLine();
                if (null == line)
                    continue;
                line = StringUtils.trimToEmpty(line);
                if (!line.startsWith("#"))
                    throw new IllegalStateException("Import files are expected to start with a comment indicating table name");

                String canonicalName = line.substring(1).trim().toLowerCase();
                SpecimenTableType type = getForName(canonicalName);

                if (null != type)
                    fileNameMap.put(type, getSpecimenImportFile(getContainer(), dir, fileName, type));
            }
        }

        return fileNameMap;
    }

    // TODO: Pass in merge (or import strategy)?
    private SpecimenImportFile getSpecimenImportFile(Container c, VirtualFile dir, String fileName, SpecimenTableType type)
    {
        DbSchema schema = SpecimenSchema.get().getSchema();

        // Enumerate the import filter factories... first one to claim the file gets associated with it
        for (SpecimenImportStrategyFactory factory : SpecimenService.get().getSpecimenImportStrategyFactories())
        {
            SpecimenImportStrategy strategy = factory.get(schema, c, dir, fileName);

            if (null != strategy)
                return new FileSystemSpecimenImportFile(dir, fileName, strategy, type);
        }

        throw new IllegalStateException("No SpecimenImportStrategyFactory claimed this import!");
    }

    private void info(String message)
    {
        if (_logger != null)
            _logger.info(message);
    }

    private void debug(CharSequence message)
    {
        //noinspection PointlessBooleanExpression
        if (DEBUG && _logger != null)
            _logger.debug(message);
    }

    private static String _currentStatus = GENERAL_JOB_STATUS_MSG;
    private void setStatus(@Nullable String status)
    {
        if (null != _job)
        {
            _currentStatus = status;
            _job.setStatus(_currentStatus);
        }
    }

    private void ensureNotCanceled()
    {
        if (null != _job)
            _job.setStatus(_currentStatus);     // Will throw if job has been canceled
    }

    private List<SpecimenColumn> getSpecimenCols(List<SpecimenColumn> availableColumns)
    {
        if (_specimenCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<>(availableColumns.size());
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isSpecimens())
                    cols.add(col);
            }
            _specimenCols = cols;
        }
        return _specimenCols;
    }

    private String getSpecimenColsSql(List<SpecimenColumn> availableColumns, boolean seenVisitValue)
    {
        if (_specimenColsSql == null)
        {
            String sep = "";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : getSpecimenCols(availableColumns))
            {
                cols.append(sep).append(col.getLegalDbColumnName(getSqlDialect()));
                sep = ",\n   ";
            }
            if (!seenVisitValue)
                cols.append(sep).append(VISIT_VALUE.getDbColumnName());
            _specimenColsSql = cols.toString();
        }
        return _specimenColsSql;
    }

    private List<SpecimenColumn> getVialCols(List<SpecimenColumn> availableColumns)
    {
        if (_vialCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<>(availableColumns.size());
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isVials())
                    cols.add(col);
            }
            _vialCols = cols;
        }
        return _vialCols;
    }

    private String getVialColsSql(List<SpecimenColumn> availableColumns)
    {
        if (_vialColsSql == null)
        {
            String sep = "";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : getVialCols(availableColumns))
            {
                cols.append(sep).append(col.getLegalDbColumnName(getSqlDialect()));
                sep = ",\n   ";
            }
            _vialColsSql = cols.toString();
        }
        return _vialColsSql;
    }

    private List<SpecimenColumn> getSpecimenEventCols(List<SpecimenColumn> availableColumns)
    {
        if (_vialEventCols == null)
        {
            List<SpecimenColumn> cols = new ArrayList<>(availableColumns.size());
            for (SpecimenColumn col : availableColumns)
            {
                if (col.getTargetTable().isEvents())
                    cols.add(col);
            }
            _vialEventCols = cols;
        }
        return _vialEventCols;
    }

    private String getSpecimenEventColsSql(List<SpecimenColumn> availableColumns)
    {
        if (_vialEventColsSql == null)
        {
            String sep = "";
            StringBuilder cols = new StringBuilder();
            for (SpecimenColumn col : getSpecimenEventCols(availableColumns))
            {
                cols.append(sep).append(col.getLegalDbColumnName(getSqlDialect()));
                sep = ",\n    ";
            }
            _vialEventColsSql = cols.toString();
        }
        return _vialEventColsSql;
    }

    private void populateMaterials(SpecimenLoadInfo info, boolean merge)
    {
        String columnName = null;

        for (SpecimenColumn specimenColumn : info.getAvailableColumns())
        {
            if (GLOBAL_UNIQUE_ID_TSV_COL.equals(specimenColumn.getPrimaryTsvColumnName()))
            {
                columnName = specimenColumn.getDbColumnName();
                break;
            }
        }

        if (columnName == null)
        {
            for (SpecimenColumn specimenColumn : info.getAvailableColumns())
            {
                if (SPEC_NUMBER_TSV_COL.equals(specimenColumn.getPrimaryTsvColumnName()))
                {
                    columnName = specimenColumn.getDbColumnName();
                    break;
                }
            }
        }

        if (columnName == null)
        {
            throw new IllegalStateException("Could not find a unique specimen identifier column.  Either \"" + GLOBAL_UNIQUE_ID_TSV_COL
            + "\" or \"" + SPEC_NUMBER_TSV_COL + "\" must be present in the set of specimen columns.");
        }

        String insertObjectSQL = "INSERT INTO exp.Object (ObjectURI, Container)  \n" +
                "SELECT DISTINCT T.LSID AS ObjectURI, ? AS Container\n" +
                "FROM " + info.getTempTableName() + " T LEFT OUTER JOIN exp.Object O ON T.LSID = O.ObjectURI\n" +
                "WHERE O.ObjectURI IS NULL\n";

        String countMaterialToInsertSQL = "SELECT DISTINCT T.LSID\n" +
                "FROM " + info.getTempTableName() + " T LEFT OUTER JOIN exp.Material M ON T.LSID = M.LSID\n" +
                "WHERE M.LSID IS NULL\n";

        String insertMaterialSQL = "INSERT INTO exp.Material (RowId, LSID, Name, ObjectId, Container, CpasType, Created)  \n" +
                "SELECT ? + (ROW_NUMBER() OVER (ORDER BY ObjectId)), LSID, Name, ObjectId, Container, CpasType, Created FROM\n" +
                "(SELECT DISTINCT T.LSID, T." + columnName + " AS Name, (SELECT ObjectId FROM exp.Object O where O.ObjectURI=T.LSID) AS ObjectId, ? AS Container, ? AS CpasType, CAST(? AS " + getSqlDialect().getDefaultDateTimeDataType()+ ") AS Created\n" +
                " FROM " + info.getTempTableName() + " T LEFT OUTER JOIN exp.Material M ON T.LSID = M.LSID\n" +
                " WHERE M.LSID IS NULL) X\n";

        /* NOTE: Not really necessary to delete and recreate the object rows
        String deleteObjectSQL = "DELETE FROM exp.Object WHERE ObjectURI IN (SELECT M.LSID FROM exp.Material M\n" +
                "LEFT OUTER JOIN " + info.getTempTableName() + " T ON M.LSID = T.LSID\n" +
                "LEFT OUTER JOIN exp.MaterialInput MI ON M.RowId = MI.MaterialId\n" +
                "WHERE T.LSID IS NULL\n" +
                "AND MI.MaterialId IS NULL\n" +
                "AND M.CpasType IN (?, '" + StudyService.SPECIMEN_NAMESPACE_PREFIX + "') \n" +
                "AND M.Container = ?)";
         */

        String deleteMaterialSQL = "DELETE FROM exp.Material WHERE RowId IN (SELECT M.RowId FROM exp.Material M\n" +
                "LEFT OUTER JOIN " + info.getTempTableName() + " T ON M.LSID = T.LSID\n" +
                "LEFT OUTER JOIN exp.MaterialInput MI ON M.RowId = MI.MaterialId\n" +
                "WHERE T.LSID IS NULL\n" +
                "AND MI.MaterialId IS NULL\n" +
                "AND M.CpasType IN (?, '" + StudyService.SPECIMEN_NAMESPACE_PREFIX + "') \n" +
                "AND M.Container = ?)";

        String prefix = new Lsid(StudyService.SPECIMEN_NAMESPACE_PREFIX, "Folder-" + info.getContainer().getRowId(), "").toString();
        String cpasType;
        ExpSampleType sampleType = SampleTypeService.get().getSampleType(info.getContainer(), SpecimenService.SAMPLE_TYPE_NAME);

        if (sampleType == null)
        {
            ExpSampleType source = SampleTypeService.get().createSampleType();
            source.setContainer(info.getContainer());
            source.setMaterialLSIDPrefix(prefix);
            source.setName(SpecimenService.SAMPLE_TYPE_NAME);
            source.setLSID(SampleTypeService.get().getSampleTypeLsid(SpecimenService.SAMPLE_TYPE_NAME, info.getContainer()).toString());
            source.setDescription("Study specimens for " + info.getContainer().getPath());
            source.save(null);
            cpasType = source.getLSID();
        }
        else
        {
            cpasType = sampleType.getLSID();
        }

        var createdTimestamp = new Parameter.TypedValue(new Timestamp(System.currentTimeMillis()), JdbcType.TIMESTAMP);

        int affected;
        if (!merge)
        {
            info("exp.Material: Deleting entries for removed specimens...");
            SQLFragment deleteFragment = new SQLFragment(deleteMaterialSQL, cpasType, info.getContainer().getId());
            if (DEBUG)
                logSQLFragment(deleteFragment);
            affected = executeSQL(info.getSchema(), deleteFragment);
            if (affected >= 0)
                info("exp.Material: " + affected + " rows removed.");
        }

        // NOTE: No need to update existing Materials when merging -- just insert any new materials not found.
        info("exp.Material: Inserting new entries from temp table...");
        SQLFragment insertObjectFragment = new SQLFragment(insertObjectSQL, info.getContainer().getId());
        if (DEBUG)
            logSQLFragment(insertObjectFragment);
        executeSQL(info.getSchema(), insertObjectFragment);

        long count = new SqlSelector(info.getSchema(), countMaterialToInsertSQL).getRowCount();
        if (count > 0)
        {
            // reserve rowids for this insert
            // NOTE QuerySchema exp.Materials (plural) has dbsequence info, but DbSchema exp.Material (singular) does not
            TableInfo material = DefaultSchema.get(info.getUser(), info.getContainer(),"exp").getTable("Materials", null);
            ColumnInfo rowId = material.getColumn("RowId");
            DbSequence seq = DbSequenceManager.getPreallocatingSequence(rowId.getDbSequenceContainer(null), material.getDbSequenceName(rowId.getName()), 0, 1000);

            /* HACK, we don't do this anywhere else, but we need to get a block of continuous IDS to make this work */
            // row_number() starts at 1 so subtract 1
            long start = DbSequenceManager.reserveSequentialBlock(seq, (int)count);
            start -= 1;

            SQLFragment insertMaterialFragment = new SQLFragment(insertMaterialSQL, start, info.getContainer().getId(), cpasType, createdTimestamp);
            if (DEBUG)
                logSQLFragment(insertMaterialFragment);
            affected = executeSQL(info.getSchema(), insertMaterialFragment);
            if (affected >= 0)
                info("exp.Material: " + affected + " rows inserted.");
        }

        info("exp.Material: Update complete.");
    }

    private String getSpecimenEventTempTableColumns(SpecimenLoadInfo info)
    {
        StringBuilder columnList = new StringBuilder();
        String prefix = "";
        for (SpecimenColumn col : getSpecimenEventCols(info.getAvailableColumns()))
        {
            columnList.append(prefix);
            prefix = ", ";
            columnList.append("\n    ").append(info.getTempTableName()).append(".").append(col.getLegalDbColumnName(getSqlDialect()));
        }
        return columnList.toString();
    }

    // NOTE: In merge case, we've already checked the specimen hash columns are not in conflict.
    private void appendConflictResolvingSQL(SqlDialect dialect, SQLFragment sql, SpecimenColumn col, String tempTableName,
                                            @Nullable SpecimenColumn castColumn)
    {
        // If castColumn no null, then we still count col, but then cast col's value to castColumn's type and name it castColumn's name
        String selectCol = tempTableName + "." + col.getLegalDbColumnName(getSqlDialect());

        if (col.getAggregateEventFunction() != null)
        {
            sql.append(col.getAggregateEventFunction()).append("(").append(selectCol).append(")");
        }
        else
        {
            sql.append("CASE WHEN");
            if (col.getJavaClass().equals(Boolean.class))
            {
                // gross nested calls to cast the boolean to an int, get its min, then cast back to a boolean.
                // this is needed because most aggregates don't work on boolean values.
                sql.append(" COUNT(DISTINCT(").append(selectCol).append(")) = 1 THEN ");
                sql.append("CAST(MIN(CAST(").append(selectCol).append(" AS INTEGER)) AS ").append(dialect.getBooleanDataType()).append(")");
            }
            else
            {
                if (null != castColumn)
                {
                    sql.append(" COUNT(DISTINCT(").append(tempTableName).append(".").append(castColumn.getLegalDbColumnName(getSqlDialect())).append(")) = 1 THEN ");
                    sql.append("CAST(MIN(").append(selectCol).append(") AS ").append(castColumn.getDbType()).append(")");
                }
                else
                {
                    sql.append(" COUNT(DISTINCT(").append(selectCol).append(")) = 1 THEN ");
                    sql.append("MIN(").append(selectCol).append(")");
                }
            }
            sql.append(" ELSE NULL END");
        }
        sql.append(" AS ");

        if (null != castColumn)
            sql.append(castColumn.getLegalDbColumnName(getSqlDialect()));
        else
            sql.append(col.getLegalDbColumnName(getSqlDialect()));
    }


    private void populateSpecimens(SpecimenLoadInfo info, boolean merge, boolean seenVisitValue) throws ValidationException
    {
        String participantSequenceNumExpr = StudyUtils.getParticipantSequenceNumExpr(info._schema, "PTID", "VisitValue");

        SQLFragment insertSelectSql = new SQLFragment()
            .append("SELECT ")
            .append(participantSequenceNumExpr)
            .append(" AS ParticipantSequenceNum")
            .append(", SpecimenHash, ")
            .append(DRAW_DATE.getDbColumnName())
            .append(", ")
            .append(DRAW_TIME.getDbColumnName())
            .append(", ")
            .append(getSpecimenColsSql(info.getAvailableColumns(), seenVisitValue))
            .append(" FROM (\n")
            .append(getVialListFromTempTableSql(info, true, seenVisitValue))
            .append(") VialList\n")
            .append("GROUP BY ")
            .append("SpecimenHash, ")
            .append(getSpecimenColsSql(info.getAvailableColumns(), seenVisitValue))
            .append(", ")
            .append(DRAW_DATE.getDbColumnName())
            .append(", ")
            .append(DRAW_TIME.getDbColumnName());

        TableInfo specimenTable = getTableInfoSpecimen();
        String specimenTableSelectName = specimenTable.getSelectName();
        if (merge)
        {
            // Create list of specimen columns, including unique columns not found in SPECIMEN_COLUMNS.
            Set<SpecimenColumn> cols = new LinkedHashSet<>();
            cols.add(new SpecimenColumn("SpecimenHash", "SpecimenHash", "VARCHAR(256)", TargetTable.SPECIMENS, true));
            cols.add(new SpecimenColumn("ParticipantSequenceNum", "ParticipantSequenceNum", "VARCHAR(200)", TargetTable.SPECIMENS, false));
            cols.add(DRAW_DATE);
            cols.add(DRAW_TIME);
            cols.addAll(getSpecimenCols(info.getAvailableColumns()));

            // Insert or update the specimens from in the temp table
            try (TableResultSet rs = new SqlSelector(info.getSchema(), insertSelectSql).getResultSet())
            {
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                DataIteratorBuilder dib = new DataIteratorBuilder.Wrapper(new ResultsImpl(rs));
                mergeTable(info.getSchema(), specimenTableSelectName, specimenTable, cols, dib, false, false);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        else
        {
            // Insert all specimens from in the temp table.
            SQLFragment insertSql = new SQLFragment()
                .append("INSERT INTO ")
                .append(specimenTableSelectName)
                .append("\n(")
                .append("ParticipantSequenceNum, SpecimenHash, ")
                .append(DRAW_DATE.getDbColumnName())
                .append(", ")
                .append(DRAW_TIME.getDbColumnName())
                .append(", ")
                .append(getSpecimenColsSql(info.getAvailableColumns(), seenVisitValue))
                .append(")\n")
                .append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);

            info("Specimens: Inserting new rows...");
            executeSQL(info.getSchema(), insertSql);
            info("Specimens: Insert complete.");
        }
    }

    private SQLFragment getVialListFromTempTableSql(SpecimenLoadInfo info, boolean forSpecimenTable, boolean seenVisitValue)
    {
        String prefix = "";
        SQLFragment vialListSql = new SQLFragment()
            .append("SELECT ");
        if (!forSpecimenTable)
        {
            vialListSql
                .append(info.getTempTableName())
                .append(".LSID AS LSID");
            prefix = ",\n    ";
        }
        vialListSql
            .append(prefix)
            .append("SpecimenHash");
        prefix = ",\n    ";
        for (SpecimenColumn col : info.getAvailableColumns())
        {
            if ((col.getTargetTable().isVials() || col.getTargetTable().isSpecimens()) &&
                (!forSpecimenTable || !GLOBAL_UNIQUE_ID.getDbColumnName().equalsIgnoreCase(col.getDbColumnName())))
            {
                vialListSql.append(prefix);
                appendConflictResolvingSQL(info.getSchema().getSqlDialect(), vialListSql, col, info.getTempTableName(), null);
            }
        }
        if (!seenVisitValue)
        {
            vialListSql
                .append(prefix)
                .append("0.0 AS ")
                .append(VISIT_VALUE.getDbColumnName());
        }

        // DrawDate and DrawTime are a little different;
        // we need to do the conflict count on DrawTimeStamp and then cast to Date or Time
        vialListSql.append(prefix);
        appendConflictResolvingSQL(info.getSchema().getSqlDialect(), vialListSql, DRAW_TIMESTAMP, info.getTempTableName(), DRAW_DATE);
        vialListSql.append(prefix);
        appendConflictResolvingSQL(info.getSchema().getSqlDialect(), vialListSql, DRAW_TIMESTAMP, info.getTempTableName(), DRAW_TIME);

        vialListSql
            .append("\nFROM ")
            .append(info.getTempTableName())
            .append("\nGROUP BY\n");
        if (!forSpecimenTable)
            vialListSql.append(info.getTempTableName()).append(".LSID,\n    ");
        vialListSql.append(info.getTempTableName()).append(".SpecimenHash");
        if (!forSpecimenTable)
            vialListSql.append(",\n    ").append(info.getTempTableName()).append(".GlobalUniqueId");

        return vialListSql;
    }

    private void populateVials(SpecimenLoadInfo info, boolean merge, boolean seenVisitValue) throws ValidationException
    {
        TableInfo specimenTable = getTableInfoSpecimen();
        String specimenTableSelectName = specimenTable.getSelectName();
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();

        String prefix = ",\n    ";
        SQLFragment insertSelectSql = new SQLFragment()
            .append("SELECT exp.Material.RowId")
            .append(prefix)
            .append(specimenTable.getColumn("RowId").getValueSql(specimenTableSelectName))
            .append(" AS SpecimenId")
            .append(prefix)
            .append(specimenTable.getColumn("SpecimenHash").getValueSql(specimenTableSelectName))
            .append(prefix)
            // Set a default value of true for the 'Available' column:
            .append("? AS Available")
            .add(Boolean.TRUE);

        for (SpecimenColumn col : getVialCols(info.getAvailableColumns()))
            insertSelectSql.append(prefix).append("VialList.").append(col.getLegalDbColumnName(getSqlDialect()));

        insertSelectSql
            .append(" FROM (").append(getVialListFromTempTableSql(info, false, seenVisitValue))
            .append(") VialList")
            // join to material:
            .append("\n    JOIN exp.Material ON (")
            .append("VialList.LSID = exp.Material.LSID")
            .append(" AND exp.Material.Container = ?)")
            .add(info.getContainer().getId())
            // join to specimen:
            .append("\n    JOIN ")
            .append(specimenTableSelectName)
            .append(" ON ")
            .append(specimenTable.getColumn("SpecimenHash").getValueSql(specimenTableSelectName))
            .append(" = VialList.SpecimenHash");

        if (merge)
        {
            // Create list of vial columns, including unique columns not found in SPECIMEN_COLUMNS.
            Set<SpecimenColumn> cols = new LinkedHashSet<>();
            // NOTE: study.Vial.RowId is actually an FK to exp.Material.RowId
            cols.add(GLOBAL_UNIQUE_ID);
            cols.add(new SpecimenColumn("RowId", "RowId", "INT NOT NULL", TargetTable.VIALS, false));
            cols.add(new SpecimenColumn("SpecimenId", "SpecimenId", "INT NOT NULL", TargetTable.VIALS, false));
            cols.add(new SpecimenColumn("SpecimenHash", "SpecimenHash", "VARCHAR(256)", TargetTable.VIALS, false));
            cols.add(new SpecimenColumn("Available", "Available", ImportTypes.BOOLEAN_TYPE, TargetTable.VIALS, false));
            cols.addAll(getVialCols(info.getAvailableColumns()));

            // Insert or update the vials from in the temp table.
            try (TableResultSet rs = new SqlSelector(info.getSchema(), insertSelectSql).getResultSet())
            {
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                DataIteratorBuilder dib = new DataIteratorBuilder.Wrapper(new ResultsImpl(rs));
                mergeTable(info.getSchema(), vialTableSelectName, vialTable, cols, dib, false, false);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        else
        {
            // Insert all vials from in the temp table.
            SQLFragment insertSql = new SQLFragment()
                .append("INSERT INTO ")
                .append(vialTableSelectName)
                .append("\n(RowId, SpecimenId, SpecimenHash, Available, ")
                .append(getVialColsSql(info.getAvailableColumns()))
                .append(")\n")
                .append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);

            info("Vials: Inserting new rows...");
            executeSQL(info.getSchema(), insertSql);
            info("Vials: Insert complete.");
        }
    }

    private void logSQLFragment(SQLFragment sql)
    {
        info(sql.getSQL());
        info("Params: ");
        for (Object param : sql.getParams())
            info(param.toString());
    }

    private void populateSpecimenEvents(SpecimenLoadInfo info, boolean merge) throws ValidationException
    {
        TableInfo specimenEventTable = getTableInfoSpecimenEvent();
        String specimenEventTableSelectName = specimenEventTable.getSelectName();
        TableInfo vialTable = getTableInfoVial();
        String vialTableSelectName = vialTable.getSelectName();

        SQLFragment insertSelectSql = new SQLFragment()
            .append("SELECT ")
            .append(vialTable.getColumn("RowId").getValueSql(vialTableSelectName))
            .append(" AS VialId, \n")
            .append(getSpecimenEventTempTableColumns(info))
            .append(" FROM ")
            .append(info.getTempTableName())
            .append("\nJOIN ")
            .append(vialTableSelectName)
            .append(" ON ")
            .append(info.getTempTableName())
            .append(".GlobalUniqueId = ")
            .append(vialTable.getColumn("GlobalUniqueId").getValueSql(vialTableSelectName));

        if (merge)
        {
            // Create list of vial columns, including unique columns not found in SPECIMEN_COLUMNS.
            // Events are special in that we want to merge based on a pseudo-unique set of columns:
            //    Container, VialId (vial.GlobalUniqueId), LabId, StorageDate, ShipDate, LabReceiptDate
            // We need to always add these extra columns, even if they aren't in the list of available columns.
            Set<SpecimenColumn> cols = new LinkedHashSet<>();
            cols.add(new SpecimenColumn("VialId", "VialId", "INT NOT NULL", TargetTable.SPECIMEN_EVENTS, true));
            cols.add(LAB_ID);
            cols.add(SHIP_DATE);
            cols.add(STORAGE_DATE);
            cols.add(LAB_RECEIPT_DATE);

            cols.addAll(getSpecimenEventCols(info.getAvailableColumns()));

            // Insert or update the vials from in the temp table.
            try (TableResultSet rs = new SqlSelector(info.getSchema(), insertSelectSql).getResultSet())
            {
                if (VERBOSE_DEBUG)
                    ResultSetUtil.logData(rs, _logger);
                DataIteratorBuilder dib = new DataIteratorBuilder.Wrapper(new ResultsImpl(rs));
                mergeTable(info.getSchema(), specimenEventTableSelectName, specimenEventTable, cols, dib, false, false);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        else
        {
            // Insert all events from the temp table
            SQLFragment insertSql = new SQLFragment()
                .append("INSERT INTO ")
                .append(specimenEventTableSelectName)
                .append("\n")
                .append("(VialId, ")
                .append(getSpecimenEventColsSql(info.getAvailableColumns()))
                .append(")\n")
                .append(insertSelectSql);

            if (DEBUG)
                logSQLFragment(insertSql);

            info("Specimen Events: Inserting new rows.");
            executeSQL(info.getSchema(), insertSql);
            info("Specimen Events: Insert complete.");
        }
    }

    private interface ComputedColumn
    {
        String getName();
        Object getValue(Map<String, Object> row) throws ValidationException;
    }

    private static class EntityIdComputedColumn implements ComputedColumn
    {
        @Override
        public String getName() { return "EntityId"; }
        @Override
        public Object getValue(Map<String, Object> row) { return GUID.makeGUID(); }
    }

    private <T extends ImportableColumn> Pair<List<T>, Integer> mergeTable(
            DbSchema schema, String tableName, @Nullable TableInfo target,
            Collection<T> potentialColumns, DataIteratorBuilder values, boolean addEntityId, boolean hasContainerColumn)
            throws ValidationException
    {
        ComputedColumn entityIdCol = null;
        if (addEntityId)
        {
            entityIdCol = new EntityIdComputedColumn();
        }

        return mergeTable(schema, tableName, target, potentialColumns, values, entityIdCol, hasContainerColumn);
    }

    private void mergeTable(DbSchema schema, SpecimenImportFile file, TableInfo target, boolean addEntityId, boolean hasContainerColumn)
            throws ValidationException, IOException
    {
        SpecimenTableType type = file.getTableType();

        ComputedColumn entityIdCol = null;

        if (addEntityId)
        {
            entityIdCol = new EntityIdComputedColumn();
        }

        try (DataLoader loader = loadTsv(file))
        {
            mergeTable(schema, type.getTableName(), target, type.getColumns(), loader, entityIdCol, hasContainerColumn);
        }
        finally
        {
            file.getStrategy().close();
        }
    }

    /**
     * Insert or update rows on the target table using the unique columns of <code>potentialColumns</code>
     * to identify the existing row.
     *
     * NOTE: The idCol is used only during insert -- the value won't be updated if the row already exists.
     *
     * @param schema The dbschema.
     * @param idCol The computed column.
     * @return A pair of the columns actually found in the data values and a total row count.
     * @throws org.labkey.api.query.ValidationException
     */
    private <T extends ImportableColumn> Pair<List<T>, Integer> mergeTable(
            DbSchema schema, String tableName, @Nullable TableInfo target,
            Collection<T> potentialColumns, DataIteratorBuilder values,
            ComputedColumn idCol, boolean hasContainerColumn)
            throws ValidationException
    {
        // tests  SpecimenTest, LuminexUploadAndCopyTest, VaccineProtocolTest, FlowSpecimenTest, SpecimenImportTest, CreateVialsTest, ViabilityTest
        if (values == null)
        {
            info(tableName + ": No rows to merge");
            return new Pair<>(Collections.emptyList(), 0);
        }

        if (null == target)
        {
            target = schema.getTable(tableName.substring(tableName.indexOf('.') + 1));
            if (null == target)
                throw new IllegalArgumentException("tablename: " + tableName);
        }

        // get the iter 'early' so we can look at the columns

        DataIteratorContext dix = new DataIteratorContext();
        dix.setInsertOption(QueryUpdateService.InsertOption.MERGE);
        DataIterator iter = values.getDataIterator(dix);

        CaseInsensitiveHashSet tsvColumnNames = new CaseInsensitiveHashSet();
        for (int i=1 ; i<= iter.getColumnCount() ; i++)
            tsvColumnNames.add(iter.getColumnInfo(i).getName());

        List<T> availableColumns = new ArrayList<>();
        CaseInsensitiveHashSet skipColumns = new CaseInsensitiveHashSet(target.getColumnNameSet());
        CaseInsensitiveHashSet keyColumns = new CaseInsensitiveHashSet();

        CaseInsensitiveHashSet dontUpdate = new CaseInsensitiveHashSet();
        if (null != idCol)
            dontUpdate.add(idCol.getName());
        dontUpdate.add("entityid");
        skipColumns.remove("entityid");

        // NOTE entityid is handled by DataIterator so ignore EntityIdComputedColumn
        if (idCol instanceof EntityIdComputedColumn)
            idCol = null;

        for (T column : potentialColumns)
        {
            if (tsvColumnNames.contains(column.getPrimaryTsvColumnName()) || tsvColumnNames.contains(column.getDbColumnName()))
            {
                availableColumns.add(column);
                skipColumns.remove(column.getDbColumnName());
            }
        }

        for (T col : availableColumns)
        {
            if (col.isUnique())
                keyColumns.add(col.getDbColumnName());
        }
        if (hasContainerColumn)
        {
            keyColumns.add("Container");
            skipColumns.remove("Container");
        }
        if (idCol != null)
            skipColumns.remove(idCol.getName());
        if (keyColumns.isEmpty())
            keyColumns = null;

        DataIteratorBuilder specimenIter = new SpecimenImportBuilder(new DataIteratorBuilder.Wrapper(iter), potentialColumns, Collections.singletonList(idCol));
        DataIteratorBuilder std = StandardDataIteratorBuilder.forInsert(target, specimenIter, getContainer(), getUser(), dix);
        DataIteratorBuilder tableIter = new TableInsertDataIteratorBuilder(std, target, getContainer())
            .setKeyColumns(keyColumns)
            .setAddlSkipColumns(skipColumns)
            .setDontUpdate(dontUpdate);

        assert !_specimensTableType.getTableName().equalsIgnoreCase(tableName);
        info(tableName + ": Starting merge of data...");

        Pump pump = new Pump(tableIter, dix);
        pump.run();
        if (dix.getErrors().hasErrors())
            throw dix.getErrors().getLastRowError();
        int rowCount = pump.getRowCount();

        info(tableName + "updated or inserted " + rowCount + " rows of data");
        return new Pair<>(availableColumns, rowCount);
    }

    private void replaceTable(DbSchema schema, SpecimenImportFile file, TableInfo target, boolean addEntityId, boolean hasContainerColumn)
        throws IOException, ValidationException
    {
        ComputedColumn entityIdCol = null;
        if (addEntityId)
        {
            entityIdCol = new EntityIdComputedColumn();
        }

        replaceTable(schema, file, file.getTableType().getTableName(), target, false, hasContainerColumn, null, null, entityIdCol);
    }

    /**
     * Deletes the target table and inserts new rows.
     *
     * @param schema The dbschema
     * @param file SpecimenImportFile
     * @param tableName Fully qualified table name, e.g., "study.Vials"
     * @param generateGlobaluniqueIds Generate globalUniqueIds if any needed
     * @param hasContainerColumn
     * @param drawDate DrawDate column or null
     * @param drawTime DrawTime column or null
     * @return A pair of the columns actually found in the data values and a total row count.
     * @throws IOException
     */
    public <T extends ImportableColumn> Pair<List<T>, Integer> replaceTable(
            DbSchema schema, SpecimenImportFile file, String tableName, @Nullable TableInfo target,
            boolean generateGlobaluniqueIds, boolean hasContainerColumn, ComputedColumn drawDate, ComputedColumn drawTime,
            ComputedColumn... computedColumnsAddl)
            throws IOException, ValidationException
    {
        if (file == null)
        {
            info(tableName + ": No rows to replace");
            return new Pair<>(Collections.emptyList(), 0);
        }

        ensureNotCanceled();
        info(tableName + ": Starting replacement of all data...");

        assert !_specimensTableType.getTableName().equalsIgnoreCase(tableName);
        if (hasContainerColumn)
            executeSQL(schema, "DELETE FROM " + tableName + " WHERE Container = ?", getContainer().getId());
        else
            executeSQL(schema, "DELETE FROM " + tableName);

        ArrayList<ComputedColumn> computedColumns = new ArrayList<>();
        if (null != drawDate)
            computedColumns.add(drawDate);
        if (null != drawTime)
            computedColumns.add(drawTime);
        computedColumns.addAll(Arrays.asList(computedColumnsAddl));

        final List<String> newUniqueIds = (generateGlobaluniqueIds && _generateGlobalUniqueIds > 0) ?
                getValidGlobalUniqueIds(_generateGlobalUniqueIds) : null;

        if (null != newUniqueIds)
        {
            computedColumns.add(new ComputedColumn()
            {
                int idCount = 0;

                @Override
                public String getName()
                {
                    return GLOBAL_UNIQUE_ID_TSV_COL;
                }

                @Override
                public Object getValue(Map<String, Object> row)
                {
                    Object uniqueid = row.get(GLOBAL_UNIQUE_ID_TSV_COL);
                    if (null == uniqueid)
                        uniqueid = newUniqueIds.get(idCount++);
                    return uniqueid;
                }
            });
        }

        int rowCount;
        ColumnDescriptor[] tsvColumns;

        try
        {
            if (null == target)
            {
                String dbname = tableName;
                if (dbname.startsWith(schema.getName() + "."))
                    dbname = dbname.substring(schema.getName().length() + 1);
                target = schema.getTable(dbname);
            }
            if (null == target)
                throw new IllegalStateException("Could not resolve table: " + tableName);

            DataIteratorContext dix = new DataIteratorContext();
            dix.setInsertOption(QueryUpdateService.InsertOption.IMPORT);
            DataLoader tsv = loadTsv(file);
            tsvColumns = tsv.getColumns();

/*          // DEBUG: Dump data
            StringBuilder infoCol = new StringBuilder("");
            for (ColumnDescriptor cd : tsvColumns)
                infoCol.append(cd.getColumnName() + ", ");
            info(infoCol.toString());
            String[][] lines = tsv.getFirstNLines(15);
            for (String[] line : lines)
            {
                StringBuilder infoRow = new StringBuilder("");
                for (String item : line)
                    infoRow.append(item + ", ");
                info(infoRow.toString());
            }
            info("");
*/
            // CONSIDER turn off data conversion
            //for (ColumnDescriptor cd : tsvColumns) cd.clazz = String.class;
            // CONSIDER use AsyncDataIterator
            //DataIteratorBuilder asyncIn = new AsyncDataIterator.Builder(tsv);
            DataIteratorBuilder asyncIn = tsv;
            DataIteratorBuilder specimenWrapped = new SpecimenImportBuilder(asyncIn, file.getTableType().getColumns(), computedColumns);
            DataIteratorBuilder standardEtl = StandardDataIteratorBuilder.forInsert(target, specimenWrapped, getContainer(), getUser(), dix);
            DataIteratorBuilder persist = ((UpdateableTableInfo)target).persistRows(standardEtl, dix);
            Pump pump = new Pump(persist, dix);
            pump.setProgress(new ListImportProgress()
            {
                long heartBeat = HeartBeat.currentTimeMillis();

                @Override
                public void setTotalRows(int rows)
                {
                }

                @Override
                public void setCurrentRow(int currentRow)
                {
                    if (0 == currentRow % SQL_BATCH_SIZE)
                    {
                        if (0 == currentRow % (SQL_BATCH_SIZE*100))
                            info(currentRow + " rows loaded...");
                        long hb = HeartBeat.currentTimeMillis();
                        if (hb == heartBeat)
                            return;
                        ensureNotCanceled();
                        heartBeat = hb;
                    }
                }
            });
            pump.run();
            if (dix.getErrors().hasErrors())
            {
                throw new ValidationException(dix.getErrors().getLastRowError().getMessage() + " (File: " + file.getTableType().getName() + ")");
            }
            rowCount = pump.getRowCount();

            info(tableName + ": Replaced all data with " + rowCount + " new rows.");
        }
        finally
        {
            file.getStrategy().close();
        }

        // Note: this duplicates the logic in SpecimenImportIterator (just below). Keep these code paths in sync.
        Map<String,T> importMap = (Map<String,T>)createImportMap(file.getTableType().getColumns());
        final var seen = new HashSet<String>();
        List<T> availableColumns = Arrays.stream(tsvColumns).map(tsv -> importMap.get(tsv.getColumnName()))
                .filter(Objects::nonNull)
                .filter(cd -> seen.add(cd.getPrimaryTsvColumnName()))
                .collect(Collectors.toList());
        return new Pair<>(availableColumns, rowCount);
    }

    static <T extends ImportableColumn>  Map<String,T> createImportMap(Collection<T> importColumns)
    {
        var map = new CaseInsensitiveHashMap<T>();
        for (var c : importColumns)
            map.put(c.getDbColumnName(), c);
        for (var c : importColumns)
            c.getImportAliases().forEach(n -> map.put(n, c));
        for (var c : importColumns)
            map.put(c.getPrimaryTsvColumnName(), c);
        return map;
    }


    private class SpecimenImportBuilder implements DataIteratorBuilder
    {
        private final DataIteratorBuilder dib;
        private final Collection<? extends ImportableColumn> importColumns;
        private final List<ComputedColumn> computedColumns;

        SpecimenImportBuilder(DataIteratorBuilder in, Collection<? extends ImportableColumn> importColumns, List<ComputedColumn> computedColumns)
        {
            dib = in;
            this.importColumns = importColumns;
            this.computedColumns = computedColumns;
        }

        @Override
        public DataIterator getDataIterator(final DataIteratorContext context)
        {
            DataIterator in = dib.getDataIterator(context);
            DataIterator aliased = LoggingDataIterator.wrap(createAliasDataIterator(this, in, context));
            return LoggingDataIterator.wrap(new SpecimenImportIterator(this, DataIteratorUtil.wrapMap(aliased, false), context));
        }
    }

    // TODO We should consider trying to let the Standard DataIterator "import alias" replace some of this ImportableColumn behavior
    // TODO that might let us switch SpecimenImportBuilder to after StandardDataIteratorBuilder instead of before
    // TODO Also, this would allow us to _not_ have TsvLoader do type conversion. see loadTsv().
    DataIterator createAliasDataIterator(SpecimenImportBuilder sib, DataIterator in, DataIteratorContext context)
    {
        var importMap = createImportMap(sib.importColumns);
        ArrayList<String> names = new ArrayList<>(in.getColumnCount()+1);
        names.add(null);
        for (int i=1 ; i<=in.getColumnCount() ; i++)
        {
            ImportableColumn c = importMap.get(in.getColumnInfo(i).getName());
            names.add(c == null ? null : c.getPrimaryTsvColumnName());
        }
        return AliasDataIterator.wrap(in, context, names);
    }

    public static class AliasDataIterator extends SimpleTranslator
    {
        static DataIterator wrap(DataIterator in, DataIteratorContext context, List<String> names)
        {
            boolean hasAlias = false;
            for (int i=1 ; i<=in.getColumnCount() ; i++)
            {
                String name = i < names.size() ? names.get(i) : null;
                hasAlias |= name != null && !name.equals(in.getColumnInfo(i).getName());
            }
            if (!hasAlias)
                return in;
            return new AliasDataIterator(in, context, names);
        }

        /** names should be one based so they match the column indexes */
        AliasDataIterator(DataIterator in, DataIteratorContext context, List<String> names)
        {
            super(in, context);

            for (int i=1 ; i<=in.getColumnCount() ; i++)
            {
                String name = i < names.size()  ? names.get(i) : null;
                if (null != name)
                    addColumn(name, i);
                else
                    addColumn(i);
            }
        }
    }


    // TODO StandardDataIteratorBuilder should be enforcing max length
    private class SpecimenImportIterator extends SimpleTranslator
    {
        private Map<String, Object> _rowMap;

        SpecimenImportIterator(SpecimenImportBuilder sib, MapDataIterator in, DataIteratorContext context)
        {
            super(in, context);

            SqlDialect d = DbSchema.getTemp().getSqlDialect();

            CaseInsensitiveHashSet tsvColumnNames = new CaseInsensitiveHashSet();
            for (int i=1 ; i<= in.getColumnCount() ; i++)
                tsvColumnNames.add(in.getColumnInfo(i).getName());

            // deal with computedColumns that might mask importColumns
            CaseInsensitiveHashSet seen = new CaseInsensitiveHashSet();

            for (final ComputedColumn cc : sib.computedColumns)
            {
                if (null != cc && seen.add(cc.getName()))
                {
                    ColumnInfo col = new BaseColumnInfo(cc.getName(), JdbcType.OTHER);
                    Callable<Object> call = () -> {
                        Object computedValue = cc.getValue(_rowMap);
                        if (computedValue instanceof Parameter.TypedValue)
                            return ((Parameter.TypedValue) computedValue).getJdbcParameterValue();
                        else
                            return computedValue;
                    };
                    addColumn(col, call);
                }
            }

            for (final ImportableColumn ic : sib.importColumns)
            {
                if (seen.add(ic.getLegalDbColumnName(d)))
                {
                    String boundInputColumnName = null;
                    if (tsvColumnNames.contains(ic.getPrimaryTsvColumnName()))
                        boundInputColumnName = ic.getPrimaryTsvColumnName();
                    final String name = boundInputColumnName;
                    ColumnInfo col = new BaseColumnInfo(ic.getLegalDbColumnName(d), ic.getJdbcType());
                    Supplier<Object> call = () -> {
                        Object ret = null;
                        if (null != name)
                            ret = _rowMap.get(name);
                        if (null == ret)
                            ret = ic.getDefaultValue();

                        if (ic.getMaxSize() >= 0 && ret instanceof String)
                        {
                            if (((String)ret).length() > ic.getMaxSize())
                            {
                                @SuppressWarnings("ThrowableNotThrown")
                                var rowError = getRowError();
                                rowError.addFieldError(ic.getPrimaryTsvColumnName(), "Value \"" + ret + "\" is too long for column " +
                                        ic.getDbColumnName() + ". The maximum allowable length is " + ic.getMaxSize() + ".");
                            }
                        }

                        return ret;
                    };
                    addColumn(col, call);
                }
            }
        }

        @Override
        protected void processNextInput()
        {
            _rowMap = ((MapDataIterator)getInput()).getMap();
        }
    }

    private DataLoader loadTsv(@NotNull SpecimenImportFile importFile) throws IOException
    {
        assert null != importFile;

        SpecimenTableType type = importFile.getTableType();
        String tableName = type.getTableName();

        info(tableName + ": Parsing data file for table...");

        var expectedColumns = createImportMap(type.getColumns());

        DataLoader loader = importFile.getDataLoader();

        for (ColumnDescriptor column : loader.getColumns())
        {
            var expectedColumn = expectedColumns.get(column.name.toLowerCase());

            if (expectedColumn != null)
            {
                column.clazz = expectedColumn.getJavaClass();
                if (VISIT_COL.equals(column.name))
                    column.clazz = String.class;
            }
            else
            {
                column.load = false;
            }
        }

        return loader;
    }

    private Pair<List<SpecimenColumn>, Integer> populateTempTable(TempTablesHolder tempTablesHolder, SpecimenImportFile file, boolean merge)
            throws IOException, ValidationException
    {
        info("Populating specimen temp table...");
        TempTableInfo tempTableInfo = tempTablesHolder.getTempTableInfo();
        int rowCount;
        List<SpecimenColumn> loadedColumns = new ArrayList<>();

        ComputedColumn lsidCol = new ComputedColumn()
        {
            @Override
            public String getName() { return "LSID"; }
            @Override
            public Object getValue(Map<String, Object> row) throws ValidationException
            {
                String id = (String) row.get(GLOBAL_UNIQUE_ID_TSV_COL);
                if (id == null)
                    id = (String) row.get(SPEC_NUMBER_TSV_COL);

                if (id == null)
                {
                    throw new ValidationException("GlobalUniqueId is required but was not supplied");
                }

                Lsid lsid = SpecimenService.get().getSpecimenMaterialLsid(getContainer(), id);
                return lsid.toString();
            }
        };

        // remove VISIT_COL since that's a computed column
        // 1) should that be removed from SPECIMEN_COLUMNS?
        // 2) convert this to DataIterator?
        SpecimenColumn _visitCol = null;
        SpecimenColumn _participantIdCol = null;
        for (SpecimenColumn sc : _specimenColumns)
        {
            if (StringUtils.equals("VisitValue", sc.getDbColumnName()))
                _visitCol = sc;
            else if (StringUtils.equals("Ptid", sc.getDbColumnName()))
                _participantIdCol = sc;
        }

        final Study study = StudyService.get().getStudy(getContainer());
        final ImportHelperService ihs = ImportHelperService.get();
        final SequenceNumTranslator snt = ihs.getSequenceNumTranslator(study);
        final ParticipantIdTranslator pit = ihs.getParticipantIdTranslator(study, getUser());
        final SpecimenColumn visitCol = _visitCol;
        final SpecimenColumn dateCol = DRAW_TIMESTAMP;
        final SpecimenColumn participantIdCol = _participantIdCol;
        final Parameter.TypedValue nullDouble = Parameter.nullParameter(JdbcType.DOUBLE);

        ComputedColumn computedParticipantIdCol = new ComputedColumn()
        {
            @Override
            public String getName()
            {
                return participantIdCol.getDbColumnName();
            }

            @Override
            public Object getValue(Map<String, Object> row) throws ValidationException
            {
                Object p = SpecimenImporter.this.getValue(participantIdCol, row);
                return pit.translateParticipantId(p);
            }
        };

        ComputedColumn sequencenumCol = new ComputedColumn()
        {
            @Override
            public String getName()
            {
                return visitCol.getDbColumnName();
            }

            @Override
            public Object getValue(Map<String, Object> row)
            {
                Object s = SpecimenImporter.this.getValue(visitCol, row);
                Object d = SpecimenImporter.this.getValue(dateCol, row);
                Double sequencenum = snt.translateSequenceNum(s, d);
//                if (sequencenum == null)
//                    throw new org.apache.commons.beanutils.ConversionException("No visit_value provided: visit_value=" + String.valueOf(s) + " draw_timestamp=" + String.valueOf(d));
                if (null == sequencenum)
                    return nullDouble;
                return sequencenum;
            }
        };

        ComputedColumn drawDateCol = new ComputedColumn()
        {
            @Override
            public String getName()
            {
                return "DrawDate";
            }

            @Override
            public Object getValue(Map<String, Object> row)
            {
                Object d = SpecimenImporter.this.getValue(dateCol, row);
                return DateUtil.getDateOnly((Date) d);
            }
        };

        ComputedColumn drawTimeCol = new ComputedColumn()
        {
            @Override
            public String getName()
            {
                return "DrawTime";
            }

            @Override
            public Object getValue(Map<String, Object> row)
            {
                Object d = SpecimenImporter.this.getValue(dateCol, row);
                return DateUtil.getTimeOnly((Date)d);
            }
        };

        Pair<List<SpecimenColumn>, Integer> pair = new Pair<>(null, 0);
        boolean success = true;
        final int MAX_TRYS = 3;
        for (int tryCount = 0; tryCount < MAX_TRYS; tryCount += 1)
        {
            try
            {
                pair = replaceTable(tempTableInfo.getSchema(), file, tempTableInfo.getSelectName(), tempTableInfo, true, false, drawDateCol, drawTimeCol,
                        lsidCol, sequencenumCol, computedParticipantIdCol);

                loadedColumns = pair.first;
                rowCount = pair.second;

                if (rowCount == 0)
                {
                    info("Found no specimen columns to import. Temp table will not be loaded.");
                    return pair;
                }

                remapTempTableLookupIndexes(tempTableInfo.getSchema(), tempTableInfo.getSelectName(), loadedColumns);

                updateTempTableVisits(tempTableInfo.getSchema(), tempTableInfo.getSelectName());

                if (merge)
                {
                    checkForConflictingSpecimens(tempTableInfo.getSchema(), tempTableInfo.getSelectName(), loadedColumns);
                }
            }
            catch (OptimisticConflictException e)
            {
                if (tryCount + 1 < MAX_TRYS)
                    success = false;        // Try again
                else
                    throw e;
            }
            if (success)
                break;
        }

        updateTempTableSpecimenHash(tempTablesHolder, loadedColumns);

        info("Specimen temp table populated.");
        return pair;
    }

    protected void remapTempTableLookupIndexes(DbSchema schema, String tempTable, List<SpecimenColumn> loadedColumns)
    {
        String sep = "";
        SQLFragment remapExternalIdsSql = new SQLFragment("UPDATE ").append(tempTable).append(" SET ");
        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getFkTable() != null)
            {
                remapExternalIdsSql.append(sep).append(col.getLegalDbColumnName(getSqlDialect())).append(" = (SELECT RowId FROM ")
                        .append(getTableInfoFromFkTableName(col.getFkTable()).getSelectName()).append(" ").append(col.getFkTableAlias())
                        .append(" WHERE ").append("(").append(tempTable).append(".")
                        .append(col.getLegalDbColumnName(getSqlDialect())).append(" = ").append(col.getFkTableAlias()).append(".").append(col.getFkColumn())
                        .append("))");
                sep = ",\n\t";
            }
        }

        info("Remapping lookup indexes in temp table...");
        if (DEBUG)
            info(remapExternalIdsSql.toDebugString());
        if (!sep.isBlank())
            executeSQL(schema, remapExternalIdsSql);
        info("Update complete.");
    }

    private void updateTempTableVisits(DbSchema schema, String tempTable)
    {
        Study study = StudyService.get().getStudy(getContainer());
        if (study.getTimepointType() != TimepointType.VISIT)
        {
            info("Updating visit values to match draw timestamps (date-based studies only)...");
            SQLFragment visitValueSql = new SQLFragment()
                .append("UPDATE ")
                .append(tempTable)
                .append(" SET VisitValue = (")
                .append(StudyUtils.sequenceNumFromDateSQL("DrawTimestamp"))
                .append(");");
            if (DEBUG)
                info(visitValueSql.toDebugString());
            executeSQL(schema, visitValueSql);
            info("Update complete.");
        }
    }

    protected void checkForConflictingSpecimens(DbSchema schema, String tempTable, List<SpecimenColumn> loadedColumns)
    {
        if (!SettingsManager.get().getRepositorySettings(getContainer()).isSpecimenDataEditable())
        {
            info("Checking for conflicting specimens before merging...");

            // Columns used in the specimen hash
            StringBuilder hashCols = new StringBuilder();
            for (SpecimenColumn col : loadedColumns)
            {
                if (col.getTargetTable().isSpecimens() && col.getAggregateEventFunction() == null)
                {
                    hashCols.append(",\n\t");
                    hashCols.append(col.getLegalDbColumnName(getSqlDialect()));
                }
            }
            hashCols.append("\n");

            SQLFragment existingEvents = new SQLFragment("SELECT GlobalUniqueId")
                .append(hashCols)
                .append("FROM ")
                .append(getTableInfoVial(), "Vial")
                .append("\n")
                .append("JOIN ").append(getTableInfoSpecimen(), "Specimen")
                .append("\n")
                .append("ON Vial.SpecimenId = Specimen.RowId\n")
                .append("WHERE Vial.GlobalUniqueId IN (SELECT GlobalUniqueId FROM ")
                .append(tempTable)
                .append(")\n");

            SQLFragment tempTableEvents = new SQLFragment("SELECT GlobalUniqueId")
                .append(hashCols)
                .append("FROM ")
                .append(tempTable);

            // "UNION ALL" the temp and the existing tables and group by columns used in the specimen hash
            SQLFragment allEventsByHashCols = new SQLFragment("SELECT COUNT(*) AS Group_Count, * FROM (\n")
                .append("(\n").append(existingEvents).append("\n)\n")
                .append("UNION ALL /* SpecimenImporter.checkForConflictingSpecimens() */\n")
                .append("(\n").append(tempTableEvents).append("\n)\n")
                .append(") U\n")
                .append("GROUP BY GlobalUniqueId")
                .append(hashCols);

            Map<String, List<Map<String, Object>>> rowsByGUID = new HashMap<>();
            Set<String> duplicateGUIDs = new TreeSet<>();

            Map<String, Object>[] allEventsByHashColsResults = new SqlSelector(schema, allEventsByHashCols).getMapArray();

            for (Map<String, Object> row : allEventsByHashColsResults)
            {
                String guid = (String)row.get("GlobalUniqueId");
                if (guid != null)
                {
                    if (rowsByGUID.containsKey(guid))
                    {
                        // Found a duplicate
                        List<Map<String, Object>> dups = rowsByGUID.get(guid);
                        dups.add(row);
                        duplicateGUIDs.add(guid);
                    }
                    else
                    {
                        rowsByGUID.put(guid, new ArrayList<>(Arrays.asList(row)));
                    }
                }
            }

            if (duplicateGUIDs.size() == 0)
            {
                info("No conflicting specimens found");
            }
            else
            {
                StringBuilder sb = new StringBuilder();
                for (String guid : duplicateGUIDs)
                {
                    List<Map<String, Object>> dups = rowsByGUID.get(guid);
                    if (dups != null && dups.size() > 0)
                    {
                        if (sb.length() > 0)
                            sb.append("\n");
                        sb.append("Conflicting specimens found for GlobalUniqueId '").append(guid).append("':\n");

                        for (Map<String, Object> row : dups)
                        {
                            // CONSIDER: if we want to be really fancy, we could diff the columns to find the conflicting value.
                            for (SpecimenColumn col : loadedColumns)
                                if (col.getTargetTable().isSpecimens() && col.getAggregateEventFunction() == null)
                                    sb.append("  ").append(col.getDbColumnName()).append("=").append(row.get(col.getDbColumnName())).append("\n");
                            sb.append("\n");
                        }
                    }
                }

                _logger.error(sb.toString());

                // If conflicts are found, stop the import.
                throw new IllegalStateException(sb.toString());
            }
        }
        else
        {
            // Check if any incoming vial is already present in the vial table; this is not allowed
            info("Checking for conflicting specimens in editable repsoitory...");
            SQLFragment sql = new SQLFragment("SELECT COUNT(*) FROM ").append(getTableInfoVial().getSelectName());
            sql.append(" WHERE GlobalUniqueId IN " + "(SELECT GlobalUniqueId FROM ");
            sql.append(tempTable).append(")");
            ArrayList<Integer> counts = new SqlSelector(schema, sql).getArrayList(Integer.class);
            if (1 != counts.size())
            {
                throw new IllegalStateException("Expected one and only one count of rows.");
            }
            else if (0 != counts.get(0) && _generateGlobalUniqueIds > 0)
            {
                // We were trying to generate globalUniqueIds
                throw new OptimisticConflictException("Attempt to generate global unique ids failed.", null, 0);
            }
            else if (0 != counts.get(0))
            {
                throw new IllegalStateException("With an editable specimen repository, importing may not reference any existing specimen. " +
                        counts.get(0) + " imported specimen events refer to existing specimens.") ;
            }
            info("No conflicting specimens found");
        }
    }

    private void updateTempTableSpecimenHash(TempTablesHolder tempTablesHolder, List<SpecimenColumn> loadedColumns)
    {
        DbSchema schema = tempTablesHolder.getTempTableInfo().getSchema();
        String tempTableName = tempTablesHolder.getTempTableInfo().getSelectName();
        String selectInsertTempTableName = tempTablesHolder.getSelectInsertTempTableInfo().getSelectName();

        // NOTE: In merge case, we've already checked the specimen hash columns are not in conflict.
        SQLFragment conflictResolvingSubselect = new SQLFragment("SELECT GlobalUniqueId");
        for (SpecimenColumn col : loadedColumns)
        {
            if (col.getTargetTable().isSpecimens())
            {
                conflictResolvingSubselect.append(",\n\t");
                String selectCol = tempTableName + "." + col.getLegalDbColumnName(getSqlDialect());

                if (col.getAggregateEventFunction() != null)
                    conflictResolvingSubselect.append(col.getAggregateEventFunction()).append("(").append(selectCol).append(")");
                else
                {
                    String singletonAggregate;
                    if (col.getJavaClass().equals(Boolean.class))
                    {
                        // gross nested calls to cast the boolean to an int, get its min, then cast back to a boolean.
                        // this is needed because most aggregates don't work on boolean values.
                        singletonAggregate = "CAST(MIN(CAST(" + selectCol + " AS INTEGER)) AS " + schema.getSqlDialect().getBooleanDataType()  + ")";
                    }
                    else
                    {
                        singletonAggregate = "MIN(" + selectCol + ")";
                    }
                    conflictResolvingSubselect.append("CASE WHEN");
                    conflictResolvingSubselect.append(" COUNT(DISTINCT(").append(selectCol).append(")) = 1 THEN ");
                    conflictResolvingSubselect.append(singletonAggregate);
                    conflictResolvingSubselect.append(" ELSE NULL END");
                }
                conflictResolvingSubselect.append(" AS ").append(col.getLegalDbColumnName(getSqlDialect()));
            }
        }
        conflictResolvingSubselect.append("\nFROM ").append(tempTableName).append("\nGROUP BY GlobalUniqueId");

        SQLFragment updateHashSql = new SQLFragment("SELECT (");
        makeUpdateSpecimenHashSql(schema, getContainer(), loadedColumns, "InnerTable.", updateHashSql);
        updateHashSql.append(") AS SpecimenHash, ")
            .append("InnerTable.GlobalUniqueId")
            .append("\n\tINTO ")
            .append(selectInsertTempTableName)
            .append("\n\tFROM (")
            .append(conflictResolvingSubselect)
            .append(") InnerTable");

        info("Calculating specimen hash values into second temp table...");
        if (DEBUG)
            info(updateHashSql.toDebugString());
        executeSQL(schema, updateHashSql);
        info("Done calculating specimen hash values.");
        tempTablesHolder.getSelectInsertTempTableInfo().track();   // We've now created the second temp table

        SQLFragment setSpecimenHashSql = new SQLFragment("UPDATE ")
            .append(tempTableName)
            .append(" SET SpecimenHash = InnerTable.SpecimenHash\nFROM ")
            .append(selectInsertTempTableName)
            .append(" InnerTable\nWHERE ")
            .append(tempTableName)
            .append(".GlobalUniqueId = InnerTable.GlobalUniqueId");

        info("Updating specimen hash values in temp table...");
        if (DEBUG)
            info(setSpecimenHashSql.toDebugString());
        executeSQL(schema, setSpecimenHashSql);
        info("Update complete.");
        info("Temp table populated.");
    }

    public static void makeUpdateSpecimenHashSql(DbSchema schema, Container container, List<SpecimenColumn> loadedColumns, String innerTable, SQLFragment updateHashSql)
    {
        ArrayList<String> hash = new ArrayList<>();
        hash.add("?");
        updateHashSql.add("Fld-" + container.getRowId());
        String strType = schema.getSqlDialect().getSqlCastTypeName(JdbcType.VARCHAR);

        Map<String, SpecimenColumn> loadedColumnMap = new HashMap<>();
        loadedColumns.forEach(col -> loadedColumnMap.put(col.getPrimaryTsvColumnName(), col));
        SpecimenColumns.BASE_SPECIMEN_COLUMNS.forEach(col -> {
            if (col.getTargetTable().isSpecimens())
            {
                if (loadedColumnMap.isEmpty() || loadedColumnMap.containsKey(col.getPrimaryTsvColumnName()))
                {
                    String columnName = innerTable + col.getLegalDbColumnName(schema.getSqlDialect());
                    hash.add("'~'");
                    hash.add(" CASE WHEN " + columnName + " IS NOT NULL THEN CAST(" + columnName + " AS " + strType + ") ELSE '' END");
                }
                else
                {
                    hash.add("'~'");
                }
            }
        });

        updateHashSql.append(schema.getSqlDialect().concatenate(hash.toArray(new String[hash.size()])));
    }

    private Object getValue(ImportableColumn col, Map tsvRow)
    {
        Object value = null;
        if (tsvRow.containsKey(col.getPrimaryTsvColumnName()))
            value = tsvRow.get(col.getPrimaryTsvColumnName());
        else if (tsvRow.containsKey(col.getDbColumnName()))
            value = tsvRow.get(col.getDbColumnName());
        return value;
    }

    private static final boolean DEBUG = false;
    private static final boolean VERBOSE_DEBUG = false;

    private static class TempTablesHolder
    {
        private final TempTableInfo _tempTableInfo;             // main specimen temp table
        private final TempTableInfo _selectInsertTempTableInfo; // temp table used to Select Insert into while populating main temp table
        private final Runnable _createIndexes;

        public TempTablesHolder(TempTableInfo tempTableInfo, TempTableInfo selectInsertTempTableInfo, Runnable createIndexes)
        {
            _tempTableInfo = tempTableInfo;
            _selectInsertTempTableInfo = selectInsertTempTableInfo;
            _createIndexes = createIndexes;
        }

        public TempTableInfo getTempTableInfo() {
            return _tempTableInfo;
        }

        public TempTableInfo getSelectInsertTempTableInfo() {
            return _selectInsertTempTableInfo;
        }

        public Runnable getCreateIndexes() {
            return _createIndexes;
        }
    }

    private TempTablesHolder createTempTable()
    {
        info("Creating temp table to hold archive data...");
        SqlDialect dialect = DbSchema.getTemp().getSqlDialect();

        StringBuilder sql = new StringBuilder();
        String uniquifier = StringUtilsLabKey.getUniquifier(9);

        ArrayList<BaseColumnInfo> columns = new ArrayList<>();

        String strType = dialect.getSqlTypeName(JdbcType.VARCHAR);

        sql.append("\n(\n    RowId ").append(dialect.getUniqueIdentType()).append(", ");
        columns.add(new BaseColumnInfo("RowId", JdbcType.INTEGER, 0, false));
        columns.get(0).setAutoIncrement(true);

        sql.append("LSID ").append(strType).append("(300) NOT NULL, ");
        columns.add(new BaseColumnInfo("LSID", JdbcType.VARCHAR, 300, false));

        sql.append("SpecimenHash ").append(strType).append("(300), ");
        columns.add(new BaseColumnInfo("SpecimenHash", JdbcType.VARCHAR, 300, true));

        sql.append(DRAW_DATE.getDbColumnName()).append(" ").append(DRAW_DATE.getDbType()).append(", ");
        columns.add(new BaseColumnInfo(DRAW_DATE.getDbColumnName(), DRAW_DATE.getJdbcType(), 0, true));

        sql.append(DRAW_TIME.getDbColumnName()).append(" ").append(DRAW_TIME.getDbType());
        columns.add(new BaseColumnInfo(DRAW_TIME.getDbColumnName(), DRAW_TIME.getJdbcType(), 0, true));

        for (SpecimenColumn col : _specimenColumns)
        {
            String name = col.getLegalDbColumnName(getSqlDialect());
            sql.append(",\n    ").append(name).append(" ").append(col.getDbType());
            BaseColumnInfo colInfo = new BaseColumnInfo(name, col.getJdbcType(), col.getMaxSize(), true);
            Collection<String> aliases = col.getImportAliases();
            if (!aliases.isEmpty())
                colInfo.setImportAliasesSet(new HashSet<>(aliases));
            columns.add(colInfo);
        }
        sql.append("\n);");

        TempTableInfo tempTableInfo = new TempTableInfo("SpecimenUpload", (List<ColumnInfo>)(List)columns, Arrays.asList("RowId"));
        final String fullTableName = tempTableInfo.getSelectName();

        sql.insert(0, "CREATE TABLE " + fullTableName + " ");
        executeSQL(DbSchema.getTemp(), sql);
        tempTableInfo.track();

        // globalUniquId
        final String globalUniqueIdIndexSql = "CREATE INDEX IX_SpecimenUpload" + uniquifier + "_GlobalUniqueId ON " + fullTableName + "(GlobalUniqueId)";
        if (DEBUG)
            info(globalUniqueIdIndexSql);
        executeSQL(DbSchema.getTemp(), globalUniqueIdIndexSql);

        // We'll Insert Into this one with the calculated specimenHashes and then Update the temp table from there
        ArrayList<ColumnInfo> columns2 = new ArrayList<>();
        columns2.add(new BaseColumnInfo(GLOBAL_UNIQUE_ID.getDbColumnName(), GLOBAL_UNIQUE_ID.getJdbcType(), GLOBAL_UNIQUE_ID.getMaxSize(), true));
        columns2.add(new BaseColumnInfo("SpecimenHash", JdbcType.VARCHAR, 300, true));
        TempTableInfo selectInsertTempTableInfo = new TempTableInfo("SpecimenUpload2", columns2, Collections.singletonList("RowId"));

        final String rowIdIndexSql = "CREATE INDEX IX_SpecimenUpload" + uniquifier + "_RowId ON " + fullTableName + "(RowId)";
        final String lsidIndexSql = "CREATE INDEX IX_SpecimenUpload" + uniquifier + "_LSID ON " + fullTableName + "(LSID)";
        final String hashIndexSql = "CREATE INDEX IX_SpecimenUpload" + uniquifier + "_SpecimenHash ON " + fullTableName + "(SpecimenHash)";

        // delay remaining indexes
        Runnable createIndexes = () -> {
            if (DEBUG)
            {
                info(rowIdIndexSql);
                info(lsidIndexSql);
                info(hashIndexSql);
            }
            executeSQL(DbSchema.getTemp(), rowIdIndexSql);
            executeSQL(DbSchema.getTemp(), lsidIndexSql);
            executeSQL(DbSchema.getTemp(), hashIndexSql);
            info("Created indexes on table " + fullTableName);
        };

        info("Created temporary table " + fullTableName);
        return new TempTablesHolder(tempTableInfo, selectInsertTempTableInfo, createIndexes);
    }


    private static final String SPECIMEN_SEQUENCE_NAME = "org.labkey.study.samples";

    private List<String> getValidGlobalUniqueIds(int count)
    {
        List<String> uniqueIds = new ArrayList<>();
        DbSequence sequence = DbSequenceManager.get(getContainer(), SPECIMEN_SEQUENCE_NAME);
        sequence.ensureMinimum(70000);

        Sort sort = new Sort();
        sort.appendSortColumn(FieldKey.fromString("GlobalUniqueId"), Sort.SortDirection.DESC, false);
        Set<String> columns = new HashSet<>();
        columns.add("GlobalUniqueId");
        Set<String> currentIds = new HashSet<>(new TableSelector(getTableInfoVial(), columns, null, sort).getArrayList(String.class));

        for (int i = 0; i < count; i += 1)
        {
            while (true)
            {
                String id = String.valueOf(sequence.next());

                if (!currentIds.contains(id))
                {
                    uniqueIds.add(id);
                    break;
                }
            }
        }

        return uniqueIds;
    }


    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        private static final String TABLE = "SpecimenImporterTest";

        private TempTableInfo _simpleTable;

        @Before
        public void createTable()
        {
            List<BaseColumnInfo> columns = new ArrayList<>();
            columns.add(new BaseColumnInfo("Container", JdbcType.GUID, 0, false));
            columns.add(new BaseColumnInfo("id", JdbcType.VARCHAR, 0, false));
            columns.add(new BaseColumnInfo("s", JdbcType.VARCHAR, 30, true));
            columns.get(columns.size()-1).setKeyField(true);
            columns.add(new BaseColumnInfo("i", JdbcType.INTEGER, 0, true));
            columns.add(new BaseColumnInfo("entityid", JdbcType.GUID, 0, false));
            _simpleTable = new TempTableInfo(TABLE, (List<ColumnInfo>)(List)columns, Arrays.asList("s"));

            new SqlExecutor(_simpleTable.getSchema()).execute("CREATE TABLE " + _simpleTable.getSelectName() +
                    "(Container VARCHAR(255) NOT NULL, id VARCHAR(10) NOT NULL, s VARCHAR(32), i INTEGER, entityid VARCHAR(36))");
            _simpleTable.track();
        }

        @After
        public void dropTable()
        {
            if (null != _simpleTable)
                _simpleTable.delete();
        }

        private TableResultSet selectValues()
        {
            return new SqlSelector(_simpleTable.getSchema(), "SELECT Container, id, s, i, entityid FROM " + _simpleTable + " ORDER BY id").getResultSet();
        }

        private Map<String, Object> row(String s, Integer i)
        {
            Map<String, Object> map = new CaseInsensitiveHashMap<>();
            map.put("s", s);
            map.put("i", i);
            return map;
        }

        @Test
        public void mergeTest() throws Exception
        {
            Container c = JunitUtil.getTestContainer();

            Collection<ImportableColumn> cols = List.of(
                new ImportableColumn("s", "s", "VARCHAR(32)", true),
                new ImportableColumn("i", "i", "INTEGER", false)
            );

            ListofMapsDataIterator values = new ListofMapsDataIterator(
                new LinkedHashSet<>(List.of("s","i")),
                List.of(
                    row("Bob", 100),
                    row("Sally", 200),
                    row(null, 300))
            );

            SpecimenImporter importer = new SpecimenImporter(c, null);      // TODO: don't have user here
            final Integer[] counter = new Integer[] { 0 };
            ComputedColumn idCol = new ComputedColumn()
            {
                @Override
                public String getName() { return "id"; }
                @Override
                public Object getValue(Map<String, Object> row)
                {
                    return String.valueOf(++counter[0]);
                }
            };

            // Insert rows
            Pair<List<ImportableColumn>, Integer> pair = importer.mergeTable(_simpleTable.getSchema(), _simpleTable.getSelectName(), _simpleTable, cols, values, idCol, true);
            assertNotNull(pair);
            assertEquals(pair.first.size(), cols.size());
            assertEquals(3, pair.second.intValue());
            assertEquals(3, counter[0].intValue());


            String bobGUID, sallyGUID, nullGUID, jimmyGUID;
            int jimmyID;

            try (TableResultSet rs = selectValues())
            {
                Iterator<Map<String, Object>> iter = rs.iterator();
                Map<String, Object> row0 = iter.next();
                Map<String, Object> row1 = iter.next();
                Map<String, Object> row2 = iter.next();
                assertFalse(iter.hasNext());

                assertEquals("Bob", row0.get("s"));
                assertEquals(100, row0.get("i"));
                assertEquals("1", row0.get("id"));
                bobGUID = (String)row0.get("entityid");
//                assertNotNull(bobGUID);

                assertEquals("Sally", row1.get("s"));
                assertEquals(200, row1.get("i"));
                assertEquals("2", row1.get("id"));
                sallyGUID = (String)row1.get("entityid");
//                assertNotNull(sallyGUID);

                assertEquals(null, row2.get("s"));
                assertEquals(300, row2.get("i"));
                assertEquals("3", row2.get("id"));
                nullGUID = (String)row2.get("entityid");
//                assertNotNull(nullGUID);
            }

            // Add one new row, update one existing row.
            values = new ListofMapsDataIterator(
                new LinkedHashSet<>(List.of("s","i")),
                List.of(
                    row("Bob", 105),
                    row(null, 305),
                    row("Jimmy", 405)
                )
            );


            pair = importer.mergeTable(_simpleTable.getSchema(), _simpleTable.getSelectName(), _simpleTable, cols, values, idCol, true);
            assertEquals(pair.first.size(), cols.size());
            assertEquals(3, pair.second.intValue());
//            assertEquals(4, counter[0].intValue());

            try (TableResultSet rs = selectValues())
            {
                Iterator<Map<String, Object>> iter = rs.iterator();
                Map<String, Object> row0 = iter.next();
                Map<String, Object> row1 = iter.next();
                Map<String, Object> row2 = iter.next();
                Map<String, Object> row3 = iter.next();
                assertFalse(iter.hasNext());

                assertEquals("Bob", row0.get("s"));
                assertEquals(105, row0.get("i"));
                assertEquals("1", row0.get("id"));
                assertEquals(bobGUID, row0.get("entityid"));

                assertEquals("Sally", row1.get("s"));
                assertEquals(200, row1.get("i"));
                assertEquals("2", row1.get("id"));
                assertEquals(sallyGUID, row1.get("entityid"));

                assertEquals(null, row2.get("s"));
                assertEquals(305, row2.get("i"));
                assertEquals("3", row2.get("id"));
                assertEquals(nullGUID, row2.get("entityid"));

                assertEquals("Jimmy", row3.get("s"));
                assertEquals(405, row3.get("i"));

                jimmyID = Integer.valueOf((String) row3.get("id"));
                assertTrue(4 <= jimmyID);
                jimmyGUID = (String)row3.get("entityid");

                // HMM, the original mergeTable() fails this check (non DataIteratyor)
                // assertNotNull(jimmyGUID);
            }

            // let's really mix things up and try updating using a column that's not marked as the PK

            Collection<ImportableColumn> colsAlternate = List.of(
                new ImportableColumn("s", "s", "VARCHAR(32)", false),
                new ImportableColumn("i", "i", "INTEGER", true)
            );

            values = new ListofMapsDataIterator(
                new LinkedHashSet<>(List.of("s","i")),
                List.of(
                    row("John", 405)
                )
            );

            pair = importer.mergeTable(_simpleTable.getSchema(), _simpleTable.getSelectName(), _simpleTable, colsAlternate, values, idCol, true);
            assertEquals(pair.first.size(), cols.size());
            assertEquals(1, pair.second.intValue());

            try (TableResultSet rs = selectValues())
            {
                Iterator<Map<String, Object>> iter = rs.iterator();
                Map<String, Object> row0 = iter.next();
                Map<String, Object> row1 = iter.next();
                Map<String, Object> row2 = iter.next();
                Map<String, Object> row3 = iter.next();
                assertFalse(iter.hasNext());

                assertEquals("John", row3.get("s"));
                assertEquals(405, row3.get("i"));
                assertEquals(jimmyID, (int)Integer.valueOf((String)row3.get("id")));
                assertEquals(jimmyGUID, row3.get("entityid"));
            }
        }

        @Test
        public void tempTableConsistencyTest()
        {
            Container c = JunitUtil.getTestContainer();
            DbSchema schema = SpecimenSchema.get().getSchema();
            User user = TestContext.get().getUser();

            // Provisioned specimen tables need to be created in this order
            TableInfo specimenTableInfo = SpecimenSchema.get().getTableInfoSpecimen(c, user);
            TableInfo vialTableInfo = SpecimenSchema.get().getTableInfoVial(c, user);
            TableInfo specimenEventTableInfo = SpecimenSchema.get().getTableInfoSpecimenEvent(c, user);
            SpecimenImporter importer = new SpecimenImporter(c, user);

            for (SpecimenColumn specimenColumn : importer._specimenColumns)
            {
                TargetTable targetTable = specimenColumn.getTargetTable();
                List<String> tableNames = targetTable.getTableNames();
                for (String tableName : tableNames)
                {
                    TableInfo tableInfo = null;
                    if ("SpecimenEvent".equalsIgnoreCase(tableName))
                        tableInfo = specimenEventTableInfo;
                    else if ("Specimen".equalsIgnoreCase(tableName))
                        tableInfo = specimenTableInfo;
                    else if ("Vial".equalsIgnoreCase(tableName))
                        tableInfo = vialTableInfo;
                    if (null != tableInfo)
                        checkConsistency(tableInfo, tableName, specimenColumn);
                }
            }
            for (ImportableColumn importableColumn : SpecimenColumns.ADDITIVE_COLUMNS)
            {
                checkConsistency(schema, "SpecimenAdditive", importableColumn);
            }
            for (ImportableColumn importableColumn : SpecimenColumns.DERIVATIVE_COLUMNS)
            {
                checkConsistency(schema, "SpecimenDerivative", importableColumn);
            }
            for (ImportableColumn importableColumn : SpecimenColumns.PRIMARYTYPE_COLUMNS)
            {
                checkConsistency(schema, "SpecimenPrimaryType", importableColumn);
            }
            for (ImportableColumn importableColumn : SITE_COLUMNS)
            {
                checkConsistency(schema, "Site", importableColumn);
            }
        }

        private void checkConsistency(DbSchema schema, String tableName, ImportableColumn importableColumn)
        {
            TableInfo tableInfo = schema.getTable(tableName);
            checkConsistency(tableInfo, tableName, importableColumn);
        }

        private void checkConsistency(TableInfo tableInfo, String tableName, ImportableColumn importableColumn)
        {
            String columnName = importableColumn.getDbColumnName();
            ColumnInfo columnInfo = tableInfo.getColumn(columnName);
            JdbcType jdbcType = columnInfo.getJdbcType();

            if (jdbcType == JdbcType.VARCHAR)
            {
                assert importableColumn.getJdbcType() == JdbcType.VARCHAR:
                    "Column '" + columnName + "' in table '" + tableName + "' has inconsistent types in SQL and importer: varchar vs " + importableColumn.getJdbcType().name();
                assert columnInfo.getScale() == importableColumn.getMaxSize() :
                    "Column '" + columnName + "' in table '" + tableName + "' has inconsistent varchar lengths in importer and SQL: " + importableColumn.getMaxSize() + " vs " + columnInfo.getScale();
            }
            assert jdbcType == importableColumn.getJdbcType() ||
                (importableColumn.getJdbcType() == JdbcType.DOUBLE && (jdbcType == JdbcType.REAL || jdbcType == JdbcType.DECIMAL)) :
                "Column '" + columnName + "' in table '" + tableName + "' has inconsistent types in SQL and importer: " + columnInfo.getJdbcType() + " vs " + importableColumn.getJdbcType();
        }
    }

    private int executeSQL(DbSchema schema, CharSequence sql, Object... params)
    {
        return executeSQL(schema, new SQLFragment(sql, params));
    }

    private int executeSQL(DbSchema schema, SQLFragment sql)
    {
        debug(sql);
        return new SqlExecutor(schema).execute(sql);
    }
}
