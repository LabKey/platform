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

package org.labkey.assay.plate;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayListener;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.assay.plate.AbstractPlateLayoutHandler;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateCustomField;
import org.labkey.api.assay.plate.PlateDataStateManager;
import org.labkey.api.assay.plate.PlateLayoutHandler;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetEdge;
import org.labkey.api.assay.plate.PlateSetType;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.assay.plate.WellCustomField;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.collections.LongArrayList;
import org.labkey.api.collections.LongHashMap;
import org.labkey.api.collections.LongHashSet;
import org.labkey.api.data.ArrayExcelWriter;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TSVArrayWriter;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.qc.DataState;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.sql.LabKeySql;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.assay.AssayManager;
import org.labkey.assay.PlateController;
import org.labkey.assay.TsvAssayProvider;
import org.labkey.assay.plate.audit.PlateAuditEvent;
import org.labkey.assay.plate.audit.PlateAuditProvider;
import org.labkey.assay.plate.audit.PlateSetAuditEvent;
import org.labkey.assay.plate.audit.PlateSetAuditProvider;
import org.labkey.assay.plate.data.PlateMapExcelWriter;
import org.labkey.assay.plate.data.WellData;
import org.labkey.assay.plate.layout.LayoutEngine;
import org.labkey.assay.plate.layout.LayoutOperation;
import org.labkey.assay.plate.layout.WellLayout;
import org.labkey.assay.plate.model.CreatePlateSetOptions;
import org.labkey.assay.plate.model.PlateBean;
import org.labkey.assay.plate.model.PlateSetAssays;
import org.labkey.assay.plate.model.PlateSetLineage;
import org.labkey.assay.plate.model.PlateTypeBean;
import org.labkey.assay.plate.model.ReformatOptions;
import org.labkey.assay.plate.model.WellGroupBean;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.PlateSetTable;
import org.labkey.assay.plate.query.PlateTable;
import org.labkey.assay.plate.query.WellTable;
import org.labkey.assay.query.AssayDbSchema;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableList;
import static org.labkey.api.assay.plate.PlateSet.MAX_PLATES;
import static org.labkey.api.assay.plate.WellGroup.Type.SAMPLE;
import static org.labkey.api.dataiterator.DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior;
import static org.labkey.api.util.IntegerUtils.asInteger;
import static org.labkey.api.util.IntegerUtils.asLong;
import static org.labkey.assay.plate.query.WellTable.WELL_LOCATION;

public class PlateManager implements PlateService, AssayListener, ExperimentListener
{
    private static final Logger LOG = LogManager.getLogger(PlateManager.class);
    private static final String LSID_CLASS_OBJECT_ID = "objectType";

    private final List<PlateDetailsResolver> _detailsLinkResolvers = new ArrayList<>();
    private boolean _lsidHandlersRegistered = false;
    private final Map<String, PlateLayoutHandler> _plateLayoutHandlers = new HashMap<>();

    // name expressions, currently not configurable
    private static final String PLATE_SET_NAME_EXPRESSION = "PLS-${now:date('yyyyMMdd')}-${RowId}";
    private static final String PLATE_NAME_EXPRESSION = "${${PlateSet/PlateSetId}-:withCounter}";

    private final Map<Container, Set<Long>> _plateIndexMap = new ConcurrentHashMap<>();
    private final AtomicBoolean _pausePlateIndex = new AtomicBoolean(false);
    private static final Object PLATE_INDEX_LOCK = new Object();

    // This flag is applied to the extraScriptContext of query mutating calls (e.g., insertRows, updateRows, etc.)
    // when those calls are being made for a plate copy operation.
    public static final String PLATE_COPY_FLAG = ".plateCopy";

    // This flag is applied to the extraScriptContext of query mutating calls (e.g., insertRows, updateRows, etc.)
    // when those calls are being made for a plate save operation.
    public static final String PLATE_SAVE_FLAG = ".plateSave";

    public SearchService.SearchCategory PLATE_CATEGORY = new SearchService.SearchCategory("plate", "Assay Plates", false) {
        @Override
        public Set<String> getPermittedContainerIds(User user, Map<String, Container> containers)
        {
            return getPermittedContainerIds(user, containers, ReadPermission.class);
        }
    };

    public SearchService.SearchCategory PLATE_SET_CATEGORY = new SearchService.SearchCategory("plateSet", "Assay Plate Sets", false) {
        @Override
        public Set<String> getPermittedContainerIds(User user, Map<String, Container> containers)
        {
            return getPermittedContainerIds(user, containers, ReadPermission.class);
        }
    };

    public static PlateManager get()
    {
        return (PlateManager) PlateService.get();
    }

    public PlateManager()
    {
        registerPlateLayoutHandler(new AbstractPlateLayoutHandler()
        {
            @Override
            public Plate createPlate(@Nullable String plateName, Container container, @NotNull PlateType plateType)
            {
                validatePlateType(plateType);
                return PlateManager.get().createPlate(container, getAssayType(), plateType);
            }

            @Override
            @NotNull
            public String getAssayType()
            {
                return "blank";
            }

            @Override
            @NotNull
            public List<String> getLayoutTypes(PlateType plateType)
            {
                return new ArrayList<>();
            }

            @Override
            protected List<Pair<Integer, Integer>> getSupportedPlateSizes()
            {
                return List.of(Pair.of(8, 12));
            }

            @Override
            public List<WellGroup.Type> getWellGroupTypes()
            {
                return Arrays.asList(WellGroup.Type.CONTROL, WellGroup.Type.SPECIMEN,
                        WellGroup.Type.REPLICATE, WellGroup.Type.OTHER);
            }
        });
    }

    @Override
    public @Nullable Plate createPlate(Plate plate, double[][] wellValues, boolean[][] excluded)
    {
        return createPlate(plate, wellValues, excluded, PlateService.NO_RUNID, 1);
    }

    @Override
    public @Nullable Plate createPlate(Plate plate, double[][] wellValues, boolean[][] excluded, long runId, int plateNumber)
    {
        if (plate == null)
            return null;

        if (plate instanceof PlateImpl plateImpl)
            return new PlateImpl(plateImpl, wellValues, excluded, runId, plateNumber);

        throw new IllegalArgumentException("Only plates retrieved from the plate service can be used to create plate instances.");
    }

    @Override
    public @NotNull Plate createPlate(Container container, String assayType, @NotNull PlateType plateType)
    {
        return new PlateImpl(container, null, null, assayType, plateType);
    }

    public @NotNull Plate createAndSavePlate(
        @NotNull Container container,
        @NotNull User user,
        @NotNull Plate plate,
        @Nullable Long plateSetId,
        @Nullable List<Map<String, Object>> data
    ) throws Exception
    {
        return createAndSavePlate(container, user, plate, plateSetId, data, false);
    }

    private @NotNull Plate createAndSavePlate(
        @NotNull Container container,
        @NotNull User user,
        @NotNull Plate plate,
        @Nullable Long plateSetId,
        @Nullable List<Map<String, Object>> data,
        boolean skipAudit
    ) throws Exception
    {
        if (!plate.isNew())
            throw new ValidationException(String.format("Failed to create plate. The provided plate already exists with rowId (%d).", plate.getRowId()));

        if (plate.isTemplate() && isDuplicatePlateTemplateName(container, plate.getName()))
            throw new ValidationException(String.format("Failed to create plate template. A plate template already exists with the name \"%s\".", plate.getName()));

        try (DbScope.Transaction tx = ensureTransaction())
        {
            ensureTransactionAuditId(tx, container, user, QueryService.AuditAction.INSERT);

            PlateSet plateSet = null;

            if (plateSetId != null)
            {
                plateSet = getPlateSet(container, plateSetId);
                if (plateSet == null)
                    throw new ValidationException(String.format("Failed to create plate. Plate set with rowId (%d) is not available in %s.", plateSetId, container.getPath()));
                if (plate.isTemplate() && !plateSet.isTemplate())
                    throw new ValidationException(String.format("Failed to create plate. Plate set \"%s\" is not a template plate set.", plateSet.getName()));
                if (!plate.isTemplate() && plateSet.isTemplate())
                    throw new ValidationException(String.format("Failed to create plate. Plate set \"%s\" is a template plate set.", plateSet.getName()));
                ((PlateImpl) plate).setPlateSet(plateSet);
            }

            // Intentionally passing skipAudit=true, and not the passed in value for skipAudit,
            // as this method does its own creation of audit events.
            long plateRowId = save(container, user, plate, data, true);
            plate = getPlate(container, plateRowId);
            if (plate == null)
                throw new IllegalStateException("Unexpected failure. Failed to retrieve plate after save (pre-commit).");

            deriveCustomFieldsFromWellData(container, user, plate, data, plateSet);

            if (!skipAudit)
                addPlateCreatedAuditEvents(container, user, tx, List.of(plate), null);

            tx.commit();

            // re-fetch the plate to get updated well data
            plate = getPlate(container, plateRowId);
            if (plate == null)
                throw new IllegalStateException("Unexpected failure. Failed to retrieve plate after save (post-commit).");

            return plate;
        }
        catch (Exception e)
        {
            // perhaps a better way to handle this
            if (plate != null && plate.getRowId() != null)
                PlateCache.uncache(container, plate);
            throw e;
        }
    }

    private List<PlateCustomField> getDefaultFieldsForPlateSet(@NotNull Plate plate, @Nullable PlateSet plateSet)
    {
        // Different plate set types display different default columns
        List<PlateCustomField> fields = new ArrayList<>();

        List<PlateCustomField> templateFields = new ArrayList<>();
        templateFields.add(new PlateCustomField(WellTable.Column.Type.fieldKey()));
        templateFields.add(new PlateCustomField(WellTable.Column.WellGroup.fieldKey()));
        templateFields.add(new PlateCustomField(WellTable.Column.ReplicateGroup.fieldKey()));

        if (plateSet == null || plateSet.isAssay())
        {
            // If the plate set is null, then check the plate to see if it is a template
            if (plateSet == null && plate.isTemplate())
                fields = templateFields;
            else
            {
                fields.add(new PlateCustomField(WellTable.Column.Type.fieldKey()));
                fields.add(new PlateCustomField(WellTable.Column.WellGroup.fieldKey()));
                fields.add(new PlateCustomField(WellTable.Column.ReplicateGroup.fieldKey()));
                fields.add(new PlateCustomField(WellTable.Column.SampleID.fieldKey()));
            }
        }
        else if (plateSet.isPrimary())
            fields.add(new PlateCustomField(WellTable.Column.SampleID.fieldKey()));
        else if (plateSet.isTemplate())
            fields = templateFields;

        return fields;
    }

    private void deriveCustomFieldsFromWellData(
        @NotNull Container container,
        @NotNull User user,
        @NotNull Plate plate,
        @Nullable List<Map<String, Object>> data,
        @Nullable PlateSet plateSet
    ) throws Exception
    {
        assert requireActiveTransaction();

        Set<PlateCustomField> customFields = new LinkedHashSet<>(getDefaultFieldsForPlateSet(plate, plateSet));

        // resolve columns and set any custom fields associated with the plate
        if (data != null)
        {
            TableInfo wellTable = getWellTable(container, user);
            TableInfo metadataTable = getPlateMetadataTable(container, user);
            List<ColumnInfo> metadataColumns = emptyList();
            if (metadataTable != null)
                metadataColumns = metadataTable.getColumns();

            for (Map<String, Object> dataMap : data)
            {
                var dataRow = new CaseInsensitiveHashMap<>(dataMap);

                if (dataRow.containsKey(WELL_LOCATION))
                {
                    for (ColumnInfo metadataColumn : metadataColumns)
                    {
                        // Issue 53017: well dataMap/dataRow is expected to be keyed by column name
                        ColumnInfo col = wellTable.getColumn(metadataColumn.getFieldKey());
                        if (col != null && dataRow.get(col.getName()) != null)
                        {
                            customFields.add(new PlateCustomField(col.getPropertyURI()));
                        }
                    }
                }
            }
        }

        // add custom fields to the plate
        if (!customFields.isEmpty())
            addFields(container, user, plate.getRowId(), customFields.stream().toList());
    }

    @Override
    public WellGroup createWellGroup(Plate plate, String name, WellGroup.Type type, List<Position> positions)
    {
        return new WellGroupImpl((PlateImpl)plate, name, type, positions);
    }

    @Override
    public Position createPosition(Container container, int row, int column)
    {
        return new PositionImpl(container, row, column);
    }

    /**
     * Use the rowId or lsid variants instead.
     */
    @Deprecated
    public @Nullable Plate getPlateByName(Container container, String plateName)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(PlateTable.Column.Name.fieldKey(), plateName);

        List<PlateBean> plates = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getArrayList(PlateBean.class);
        // this should be 1 or 0, but don't blow up if there are more than one
        if (!plates.isEmpty())
            return populatePlate(plates.get(0));

        return null;
    }

    public List<FieldKey> getMetadataColumns(@NotNull PlateSet plateSet, Container c, User user, ContainerFilter cf)
    {
        // Using a LinkedHashSet to retain plate ordering of custom fields
        Set<FieldKey> includedMetadataCols = new LinkedHashSet<>();
        for (Plate plate : plateSet.getPlates())
        {
            QueryView plateQueryView = getPlateQueryView(c, user, cf, plate, false);
            Map<String, FieldKey> displayColumns = getPlateDisplayColumns(plateQueryView)
                    .stream()
                    .filter(col -> col.getFilterKey() != null)
                    .collect(Collectors.toMap(col -> col.getColumnInfo().getPropertyURI(), DisplayColumn::getFilterKey));

            for (PlateCustomField field : plate.getCustomFields())
            {
                if (WellTable.Column.SampleID.fieldKey().equals(field.getFieldKey()))
                    continue;
                FieldKey lookupFk = displayColumns.get(field.getPropertyURI());
                if (lookupFk != null)
                    includedMetadataCols.add(lookupFk);
                else
                    includedMetadataCols.add(FieldKey.fromParts(field.getName()));
            }
        }

        return List.copyOf(includedMetadataCols);
    }

    @NotNull
    public List<Plate> getPlateTemplates(Container container)
    {
        return PlateCache.getPlateTemplates(container);
    }

    @Override
    public @Nullable PlateSet getPlateSet(Container container, long rowId)
    {
        return PlateSetCache.getPlateSet(container, rowId);
    }

    @Override
    public @Nullable PlateSet getPlateSet(ContainerFilter cf, long rowId)
    {
        return PlateSetCache.getPlateSet(cf, rowId);
    }

    @Override
    public List<? extends ExpRun> getRunsUsingPlate(@NotNull Container c, @NotNull User user, @NotNull Plate plate)
    {
        SqlSelector se = selectRunUsingPlateTemplate(c, user, plate);
        if (se == null)
            return emptyList();

        Collection<Long> runIds = se.getCollection(Long.class);
        return ExperimentService.get().getExpRuns(runIds);
    }

    @Override
    public int getRunCountUsingPlate(@NotNull Container c, @NotNull User user, @NotNull Plate plate)
    {
        int count = 0;
        SqlSelector se = selectRunUsingPlateTemplate(c, user, plate);
        if (se != null)
            count += (int) se.getRowCount();

        count += getRunCountUsingPlateInResults(c, user, plate);

        return count;
    }

    /**
     * @return A map of plate rowId to total number of runs across all plate-based assay runs in the
     * container/user scope for the specified plates.
     */
    public Map<Long, Long> getPlateRunCounts(@NotNull Container c, @NotNull User user, @NotNull Collection<Plate> plates)
    {
        if (plates.isEmpty())
            return emptyMap();

        Map<Long, Long> resultMap = new LongHashMap<>();
        for (Plate plate : plates)
        {
            if (plate.getRowId() != null)
                resultMap.put(plate.getRowId(), 0L);
        }

        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);
        if (provider == null)
            return resultMap;

        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c, provider)
                .stream().filter(provider::isPlateMetadataEnabled).toList();

        // get the runIds for each protocol, query against its assay results table
        List<SQLFragment> fragments = new ArrayList<>();
        TableInfo runTable = ExperimentService.get().getTinfoExperimentRun();
        TableInfo dataTable = ExperimentService.get().getTinfoData();
        Set<Long> plateRowIds = resultMap.keySet();

        for (ExpProtocol protocol : protocols)
        {
            AssayProtocolSchema assayProtocolSchema = provider.createProtocolSchema(user, protocol.getContainer(), protocol, null);
            TableInfo assayDataTable = assayProtocolSchema.createDataTable(ContainerFilter.getUnsafeEverythingFilter(), false);
            if (assayDataTable != null)
            {
                ColumnInfo dataIdCol = assayDataTable.getColumn("DataId");
                if (dataIdCol != null)
                {
                    SQLFragment dataTableSql = assayDataTable.getFromSQL("AD", Set.of(FieldKey.fromParts("DataId"), FieldKey.fromParts("Plate")));
                    SQLFragment sql = new SQLFragment("SELECT AD.Plate, COUNT(DISTINCT D.RunId) AS RunCount\n")
                            .append(" FROM ").append(dataTable, "D\n")
                            .append(" INNER JOIN ").append(runTable, "R").append(" ON D.RunId = R.RowId\n")
                            .append(" INNER JOIN ").append(dataTableSql).append(" ON AD.DataId = D.RowId\n")
                            .append(" WHERE R.ReplacedByRunId IS NULL AND AD.Plate").appendInClause(plateRowIds, dataTable.getSqlDialect()).append("\n")
                            .append(" GROUP BY AD.Plate\n");
                    fragments.add(sql);
                }
            }
        }

        if (fragments.isEmpty())
            return resultMap;

        SQLFragment sql = new SQLFragment();
        String union = null;
        for (SQLFragment fragment : fragments)
        {
            if (union == null)
                union = "UNION\n";
            else
                sql.append(union);
            sql.append(fragment);
        }

        try (ResultSet rs = new SqlSelector(ExperimentService.get().getSchema(), sql).getResultSet())
        {
            while (rs.next())
            {
                Long plateRowId = rs.getLong("Plate");
                Long runCount = rs.getLong("RunCount");
                resultMap.put(plateRowId, resultMap.get(plateRowId) + runCount);
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        return resultMap;
    }

    private int getRunCountUsingPlateInResults(@NotNull Container c, @NotNull User user, @NotNull Plate plate)
    {
        // first, get the list of GPAT protocols in the container
        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);
        if (provider == null)
            return 0;

        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c, provider)
                .stream().filter(provider::isPlateMetadataEnabled).toList();

        // get the runIds for each protocol, query against its assay results table
        List<SQLFragment> fragments = new ArrayList<>();
        Set<FieldKey> requiredFields = Set.of(FieldKey.fromParts("DataId"), FieldKey.fromParts("Plate"));

        protocolLoop:
        for (ExpProtocol protocol : protocols)
        {
            AssayProtocolSchema assayProtocolSchema = provider.createProtocolSchema(user, protocol.getContainer(), protocol, null);
            TableInfo assayDataTable = assayProtocolSchema.createDataTable(ContainerFilter.getUnsafeEverythingFilter(), false);
            if (assayDataTable != null)
            {
                // Issue 53446: A misconfigured assay design could be missing required fields.
                // This is not expected. Don't let that stop the run counting but do log an error with more context.
                for (FieldKey requiredFieldKey : requiredFields)
                {
                    if (assayDataTable.getColumn(requiredFieldKey) == null)
                    {
                        LOG.error("Required field \"{}\" not found in plate-based assay results domain for protocol \"{}\" in {}.", requiredFieldKey.getName(), protocol.getName(), protocol.getContainer().getPath());
                        continue protocolLoop;
                    }
                }

                SQLFragment subSelectSql = new SQLFragment("SELECT DISTINCT AD.DataId FROM ")
                        .append(assayDataTable.getFromSQL("AD", requiredFields))
                        .append(" WHERE AD.Plate = ?")
                        .add(plate.getRowId());

                SQLFragment sql = new SQLFragment("SELECT COUNT(DISTINCT D.RunId) AS RunCount FROM\n")
                        .append(ExperimentService.get().getTinfoData(), "D")
                        .append(" INNER JOIN ")
                        .append(ExperimentService.get().getTinfoExperimentRun(), "R")
                        .append(" ON D.RunId = R.RowId\n")
                        .append(" WHERE R.ReplacedByRunId IS NULL AND D.RowId IN (").append(subSelectSql).append(")\n");

                fragments.add(sql);
            }
        }

        if (fragments.isEmpty())
            return 0;

        SQLFragment unionSql = new SQLFragment();
        String union = null;
        for (SQLFragment fragment : fragments)
        {
            if (union == null)
                union = "UNION\n";
            else
                unionSql.append(union);
            unionSql.append(fragment);
        }

        SQLFragment sql = new SQLFragment("SELECT SUM(RunCount) AS RunCountSum FROM (").append(unionSql).append(") AS RunCountTable");

        return ((BigDecimal) new SqlSelector(ExperimentService.get().getSchema(), sql).getMap().get("RunCountSum")).intValueExact();
    }

    private @Nullable SqlSelector selectRunUsingPlateTemplate(@NotNull Container c, @NotNull User user, @NotNull Plate plate)
    {
        // first, get the list of GPAT protocols in the container
        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);
        if (provider == null)
            return null;

        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c, provider);

        // next, for the plate metadata enabled assays,
        // get the set of "PlateTemplate" PropertyDescriptors from the RunDomains of those assays
        List<PropertyDescriptor> plateTemplateProps = protocols.stream()
                .filter(provider::isPlateMetadataEnabled)
                .map(provider::getRunDomain)
                .filter(Objects::nonNull)
                .map(r -> r.getPropertyByName(TsvAssayProvider.PLATE_TEMPLATE_PROPERTY_NAME))
                .filter(Objects::nonNull)
                .map(DomainProperty::getPropertyDescriptor)
                .toList();

        if (plateTemplateProps.isEmpty())
            return null;

        List<Integer> plateTemplatePropIds = plateTemplateProps.stream().map(PropertyDescriptor::getPropertyId).toList();

        // query for runs with that property that point to the plate by LSID
        ContainerFilter cf = getPlateContainerFilter(null, c, user);
        SQLFragment sql = new SQLFragment()
                .append("SELECT r.rowId\n")
                .append("FROM ").append(ExperimentService.get().getTinfoExperimentRun(), "r").append("\n")
                .append("INNER JOIN ").append(OntologyManager.getTinfoObject(), "o").append(" ON o.objectUri = r.lsid\n")
                .append("INNER JOIN ").append(OntologyManager.getTinfoObjectProperty(), "op").append(" ON op.objectId = o.objectId\n")
                .append("WHERE ")
                .append(cf.getSQLFragment(AssayDbSchema.getInstance().getSchema(), new SQLFragment("r.container"))).append("\n")
                .append("AND op.propertyId ").appendInClause(plateTemplatePropIds, AssayDbSchema.getInstance().getSchema().getSqlDialect()).append("\n")
                .append("AND op.stringvalue = ?").add(plate.getLSID());

        return new SqlSelector(ExperimentService.get().getSchema(), sql);
    }

    @Override
    public @Nullable Plate getPlate(Container container, long rowId)
    {
        return PlateCache.getPlate(container, rowId);
    }

    @Override
    public @Nullable Plate getPlate(ContainerFilter cf, long rowId)
    {
        return PlateCache.getPlate(cf, rowId);
    }

    @Override
    public @Nullable Plate getPlate(ContainerFilter cf, Lsid lsid)
    {
        return PlateCache.getPlate(cf, lsid);
    }

    @Override
    public @Nullable Plate getPlate(Container container, String plateId)
    {
        return PlateCache.getPlate(container, plateId);
    }

    @Override
    public @Nullable Plate getPlate(ContainerFilter cf, String plateId)
    {
        return PlateCache.getPlate(cf, plateId);
    }

    @Override
    public @Nullable Plate getPlate(ContainerFilter cf, Long plateSetId, Object plateIdentifier)
    {
        if (plateSetId == null)
            throw new IllegalArgumentException("Plate set is required.");

        SimpleFilter filterPlateSet = new SimpleFilter(FieldKey.fromParts("RowId"), plateSetId);
        Container c = getContainerWithPlateSetIdentifier(cf, filterPlateSet);
        PlateSet plateSet = getPlateSet(c, plateSetId);
        if (plateSet == null)
            throw new IllegalArgumentException("Plate set " + plateSetId + " not found.");

        Plate plate = null;
        if (plateIdentifier != null)
        {
            List<Plate> plates = getPlatesForPlateSet(plateSet);
            List<Plate> matchingPlates = plates.stream().filter(p -> p.isIdentifierMatch(plateIdentifier.toString())).toList();
            if (matchingPlates.size() == 1)
                plate = matchingPlates.get(0);
            else if (matchingPlates.isEmpty())
                throw new IllegalArgumentException("The plate identifier \"" + plateIdentifier + "\" does not match any plate in the plate set \"" + plateSet.getName() + "\".");
            else
                throw new IllegalArgumentException("More than one plate found with name \"" + plateIdentifier + "\" in plate set " + plateSet.getName() + ". Please use the \"Plate ID\" to identify the plate instead.");
        }

        if (plate != null && plate.getPlateSet() != null && !plate.getPlateSet().getRowId().equals(plateSetId))
            throw new IllegalArgumentException("Plate " + plateIdentifier + " is not part of plate set " + plateSet.getName() + ".");
        return plate;
    }

    public static @Nullable Container getContainerWithPlateIdentifier(ContainerFilter cf, SimpleFilter filter)
    {
        return _getContainerWithIdentifier(AssayDbSchema.getInstance().getTableInfoPlate(), cf, filter);
    }

    public static @Nullable Container getContainerWithPlateSetIdentifier(ContainerFilter cf, SimpleFilter filter)
    {
        return _getContainerWithIdentifier(AssayDbSchema.getInstance().getTableInfoPlateSet(), cf, filter);
    }

    private static @Nullable Container _getContainerWithIdentifier(TableInfo tableInfo, ContainerFilter cf, SimpleFilter filter)
    {
        filter.addClause(cf.createFilterClause(AssayDbSchema.getInstance().getSchema(), FieldKey.fromParts("Container")));
        List<String> containers = new TableSelector(tableInfo, Collections.singleton("Container"), filter, null).getArrayList(String.class);

        if (containers.size() > 1)
            throw new IllegalStateException("More than one " + tableInfo.getName() + " found that matches the filter.");

        if (containers.size() == 1)
            return ContainerManager.getForId(containers.get(0));

        return null;
    }

    /**
     * Helper to create container filters to support assay import using cross folder
     * plates
     */
    public ContainerFilter getPlateContainerFilter(@Nullable ExpProtocol protocol, Container container, User user)
    {
        ContainerFilter containerFilter = QueryService.get().getContainerFilterForLookups(container, user);
        if (containerFilter == null)
            containerFilter = ContainerFilter.Type.Current.create(protocol != null ? protocol.getContainer() : container, user);
        return containerFilter;
    }

    @Override
    public @Nullable Plate getPlate(Container container, Lsid lsid)
    {
        return PlateCache.getPlate(container, lsid);
    }

    public @Nullable Plate getPlate(Lsid lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("lsid"), lsid);
        String container = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), Collections.singleton("Container"), filter, null).getObject(String.class);
        if (container != null)
        {
            Container c = ContainerManager.getForId(container);
            if (c != null)
                return PlateCache.getPlate(c, lsid);
        }
        return null;
    }

    private Object require(Object object, @NotNull String error, @Nullable String errorPrefix) throws ValidationException
    {
        if (object != null) return object;
        String errorPrefix_ = StringUtils.trimToEmpty(errorPrefix);
        if (!errorPrefix_.isEmpty())
            error = errorPrefix_ + " " + error;
        throw new ValidationException(error);
    }

    private @NotNull Plate requirePlate(Container container, long plateRowId, @Nullable String errorPrefix) throws ValidationException
    {
        return (Plate) require(getPlate(container, plateRowId), "Plate id \"" + plateRowId + "\" not found.", errorPrefix);
    }

    public @NotNull PlateSet requirePlateSet(Container container, long plateSetRowId, @Nullable String errorPrefix) throws ValidationException
    {
        return (PlateSet) require(
            getPlateSet(container, plateSetRowId),
            String.format("Plate set with rowId (%d) is not available in %s.", plateSetRowId, container.getPath()),
            errorPrefix
        );
    }

    public @NotNull PlateSet requirePlateSet(Container container, ContainerFilter cf, long plateSetRowId, @Nullable String errorPrefix) throws ValidationException
    {
        return (PlateSet) require(
            getPlateSet(cf, plateSetRowId),
            String.format("Plate set with rowId (%d) is not available in %s.", plateSetRowId, container.getPath()),
            errorPrefix
        );
    }

    private @NotNull PlateSet requirePlateSet(@NotNull Plate plate, @Nullable String errorPrefix) throws ValidationException
    {
        return (PlateSet) require(
            plate.getPlateSet(),
            String.format("Plate \"%s\" in %s is not in a plate set.", plate.getName(), plate.getContainer().getPath()),
            errorPrefix
        );
    }

    private @NotNull PlateType requirePlateType(long plateTypeRowId, @Nullable String errorPrefix) throws ValidationException
    {
        return (PlateType) require(
            getPlateType(plateTypeRowId),
            String.format("Unable to resolve plate type (%d).", plateTypeRowId),
            errorPrefix
        );
    }

    /**
     * Issue 49665 : Checks to see if there is a plate with the same name in the folder, or for
     * Biologics folders if there is a duplicate plate name in the plate set.
     */
    public boolean isDuplicatePlateName(Container c, User user, String name, @Nullable PlateSet plateSet)
    {
        // Identifying the "Biologics" folder type as the logic we pivot this behavior on is not intended to be
        // a long-term solution. We will be looking to introduce plating as a ProductFeature which we can then
        // leverage instead.
        if (plateSet != null && AssayPlateMetadataService.isBiologicsFolder(c))
        {
            for (Plate plate : plateSet.getPlates())
            {
                if (plate.getName() != null && plate.getName().equalsIgnoreCase(name))
                    return true;
            }
            return false;
        }

        Plate plate = getPlateByName(c, name);
        return plate != null && plate.getName() != null && plate.getName().equals(name);
    }

    public boolean isDuplicatePlateTemplateName(Container container, String name)
    {
        if (StringUtils.trimToNull(name) == null)
            return false;

        SimpleFilter filter = new SimpleFilter(PlateTable.Column.Name.fieldKey(), name);
        filter.addCondition(PlateTable.Column.Template.fieldKey(), true);

        ContainerFilter cf = getPlateLookupContainerFilter(container, User.getAdminServiceUser());
        filter.addCondition(cf.createFilterClause(AssayDbSchema.getInstance().getSchema(), PlateTable.Column.Container.fieldKey()));

        return new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), Set.of(PlateTable.Column.RowId.name()), filter, null).exists();
    }

    private @NotNull ContainerFilter getPlateLookupContainerFilter(Container container, User user)
    {
        ContainerFilter cf = QueryService.get().getContainerFilterForLookups(container, user);
        if (cf != null)
            return cf;
        return ContainerFilter.current(container, user);
    }

    @Override
    public @NotNull List<Plate> getPlates(Container c)
    {
        return PlateCache.getPlates(c);
    }

    public @NotNull List<PlateSet> getPlateSets(Container c)
    {
        return PlateSetCache.getPlateSets(c);
    }

    public List<Plate> getPlatesForPlateSet(PlateSet plateSet)
    {
        return PlateCache.getPlatesForPlateSet(plateSet.getContainer(), plateSet.getRowId());
    }

    @Override
    public WellGroup getWellGroup(Container container, int rowId)
    {
        WellGroupImpl unboundWellGroup = new TableSelector(AssayDbSchema.getInstance().getTableInfoWellGroup()).getObject(rowId, WellGroupImpl.class);
        if (unboundWellGroup == null || !unboundWellGroup.getContainer().equals(container))
            return null;
        Plate plate = getPlate(container, unboundWellGroup.getPlateId());
        for (WellGroup wellgroup : plate.getWellGroups())
        {
            if (wellgroup.getRowId().intValue() == rowId)
                return wellgroup;
        }
        assert false : "Unbound well group was found: bound group should always be present.";
        return null;
    }

    private WellGroup getWellGroup(String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("lsid"), lsid);
        WellGroupImpl unboundWellGroup = new TableSelector(AssayDbSchema.getInstance().getTableInfoWellGroup(), filter, null).getObject(WellGroupImpl.class);
        if (unboundWellGroup == null)
            return null;
        Plate plate = getPlate(unboundWellGroup.getContainer(), unboundWellGroup.getPlateId());
        for (WellGroup wellgroup : plate.getWellGroups())
        {
            if (wellgroup.getRowId().intValue() == unboundWellGroup.getRowId().intValue())
                return wellgroup;
        }
        assert false : "Unbound well group was not found: bound group should always be present.";
        return null;
    }

    private WellGroupImpl[] getWellGroups(Plate plate)
    {
        SimpleFilter plateFilter = new SimpleFilter(FieldKey.fromParts("PlateId"), plate.getRowId());
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoWellGroup(), plateFilter, null).getArray(WellGroupImpl.class);
    }

    private void setProperties(Container container, PropertySetImpl propertySet)
    {
        Map<String, ObjectProperty> props = OntologyManager.getPropertyObjects(container, propertySet.getLSID());
        for (ObjectProperty prop : props.values())
            propertySet.setProperty(prop.getName(), prop.value());
    }

    @Override
    public long save(Container container, User user, Plate plate) throws Exception
    {
        return save(container, user, plate, null, false);
    }

    private long save(Container container, User user, Plate plate, @Nullable List<Map<String, Object>> wellData, boolean skipAudit) throws Exception
    {
        if (plate instanceof PlateImpl plateTemplate)
            return savePlateImpl(container, user, plateTemplate, false, wellData, skipAudit);
        throw new IllegalArgumentException("Only plate instances created by the plate service can be saved.");
    }

    /**
     * Creates a plate instance from a database row.
     */
    protected Plate populatePlate(PlateBean bean)
    {
        PlateImpl plate = PlateImpl.from(bean);

        // set plate properties:
        setProperties(plate.getContainer(), plate);

        // query for all well to well group mappings on the plate
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT wgp.wellId, wgp.wellGroupId FROM ")
                .append(AssayDbSchema.getInstance().getTableInfoWellGroupPositions(), "wgp")
                .append(" INNER JOIN ")
                .append(AssayDbSchema.getInstance().getTableInfoWell(), "w")
                .append(" ON w.rowId = wgp.wellId")
                .append(" WHERE w.plateId = ?").add(plate.getRowId())
                .append(" ORDER BY wgp.wellId");
        SqlSelector ss = new SqlSelector(AssayDbSchema.getInstance().getScope(), sql);
        Collection<Map<String, Object>> allGroupPositions = ss.getMapCollection();

        // construct wellToWellGroups: map of wellId -> Set of wellGroupId
        Map<Integer, Set<Integer>> wellToWellGroups = new IntHashMap<>();
        for (Map<String, Object> groupPosition : allGroupPositions)
        {
            Integer wellId = asInteger(groupPosition.get("wellId"));
            Integer wellGroupId = asInteger(groupPosition.get("wellGroupId"));
            Set<Integer> wellGroupIds = wellToWellGroups.computeIfAbsent(wellId, k -> new HashSet<>());
            wellGroupIds.add(wellGroupId);
        }

        // construct groupIdToPositions: map of wellGroupId -> List of PositionImpl
        Map<Integer, List<PositionImpl>> groupIdToPositions = new IntHashMap<>();
        WellImpl[] wells = getWells(plate);
        WellImpl[][] wellArray = new WellImpl[plate.getRows()][plate.getColumns()];
        for (WellImpl well : wells)
        {
            wellArray[well.getRow()][well.getColumn()] = well;

            Set<Integer> wellGroupIds = wellToWellGroups.get(well.getRowId());
            if (wellGroupIds != null)
            {
                for (Integer wellGroupId : wellGroupIds)
                {
                    List<PositionImpl> groupPositions = groupIdToPositions.computeIfAbsent(wellGroupId, k -> new ArrayList<>());
                    groupPositions.add(well);
                }
            }
        }
        // add the wells to the plate
        plate.setWells(wellArray);

        // populate well groups: assign all positions to the well group object
        WellGroupImpl[] wellGroups = getWellGroups(plate);
        List<WellGroupImpl> sortedGroups = new ArrayList<>();
        for (WellGroupImpl wellGroup : wellGroups)
        {
            setProperties(plate.getContainer(), wellGroup);
            List<PositionImpl> groupPositions = groupIdToPositions.get(wellGroup.getRowId());

            wellGroup.setPositions(groupPositions != null ? groupPositions : emptyList());
            sortedGroups.add(wellGroup);
        }

        sortedGroups.sort(new WellGroupComparator());

        for (WellGroupImpl group : sortedGroups)
            plate.addWellGroup(group);

        // custom plate properties
        Domain domain = getPlateMetadataDomain(plate.getContainer(), null);
        if (domain != null)
        {
            plate.setMetadataDomainId(domain.getTypeId());
            plate.setCustomFields(getCustomFields(plate.getContainer(), domain, plate.getRowId()));
        }
        return plate;
    }

    private WellImpl[] getWells(Plate plate)
    {
        SimpleFilter plateFilter = new SimpleFilter(WellTable.Column.PlateId.fieldKey(), plate.getRowId());
        Sort sort = new Sort("Col,Row");
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), plateFilter, sort).getArray(WellImpl.class);
    }

    public Lsid getLsid(Class<?> type, Container container)
    {
        String nameSpace;
        if (type == Plate.class)
            nameSpace = "Plate";
        else if (type == PlateSet.class)
            nameSpace = "PlateSet";
        else if (type == WellGroup.class)
            nameSpace = "WellGroup";
        else if (type == Well.class)
            nameSpace = "Well";
        else
            throw new IllegalArgumentException("Unknown type " + type);

        return new Lsid(nameSpace, "Folder-" + container.getRowId(), GUID.makeGUID());
    }

    public DbScope.Transaction ensureTransaction(Lock... locks)
    {
        return AssayDbSchema.getInstance().getSchema().getScope().ensureTransaction(locks);
    }

    private long savePlateImpl(Container container, User user, @NotNull PlateImpl plate) throws Exception
    {
        return savePlateImpl(container, user, plate, false);
    }

    private long savePlateImpl(Container container, User user, @NotNull PlateImpl plate, boolean isCopy) throws Exception
    {
        return savePlateImpl(container, user, plate, isCopy, null, false);
    }

    private long savePlateImpl(
        Container container,
        User user,
        @NotNull PlateImpl plate,
        boolean isCopy,
        @Nullable List<Map<String, Object>> wellData,
        boolean skipAudit
    ) throws Exception
    {
        boolean updateExisting = plate.getRowId() != null;

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            ensureTransactionAuditId(transaction, container, user, updateExisting ? QueryService.AuditAction.UPDATE : QueryService.AuditAction.INSERT);

            Long plateId = plate.getRowId();

            if (!updateExisting && plate.getPlateSet() == null)
            {
                // ensure a plate set for each new plate
                PlateSetImpl plateSet = new PlateSetImpl();
                plateSet.setTemplate(plate.isTemplate());

                plate.setPlateSet(createPlateSet(container, user, plateSet, null, null, null));
            }

            Map<String, Object> plateRow = ObjectFactory.Registry.getFactory(PlateBean.class).toMap(PlateBean.from(plate, false), new ArrayListMap<>());
            QueryUpdateService qus = getPlateUpdateService(container, user);
            BatchValidationException errors = new BatchValidationException();

            Map<String, Object> extraScriptContext = new CaseInsensitiveHashMap<>();
            extraScriptContext.put(PLATE_COPY_FLAG, isCopy);
            extraScriptContext.put(PLATE_SAVE_FLAG, true);

            if (updateExisting)
            {
                qus.updateRows(user, container, Collections.singletonList(plateRow), null, errors, null, extraScriptContext);
                if (errors.hasErrors())
                    throw errors;
            }
            else
            {
                List<Map<String, Object>> insertedRows = qus.insertRows(user, container, Collections.singletonList(plateRow), errors, null, extraScriptContext);
                if (errors.hasErrors())
                    throw errors;
                Map<String, Object> row = insertedRows.get(0);
                plateId = MapUtils.getLong(row,PlateTable.Column.RowId.name());
                plate.setRowId(plateId);
                plate.setLsid((String) row.get(PlateTable.Column.Lsid.name()));
                plate.setName((String) row.get(PlateTable.Column.Name.name()));
                plate.setPlateId((String) row.get(PlateTable.Column.PlateId.name()));
                plate.setBarcode((String) row.get(PlateTable.Column.Barcode.name()));
            }
            savePropertyBag(container, user, plate.getLSID(), plate.getProperties(), updateExisting);

            // delete well groups first
            List<WellGroupImpl> deletedWellGroups = plate.getDeletedWellGroups();
            List<Integer> deletedWellGroupIds = new ArrayList<>();
            for (WellGroupImpl deletedWellGroup : deletedWellGroups)
            {
                assert deletedWellGroup.getRowId() != null && deletedWellGroup.getRowId() > 0;
                deletedWellGroupIds.add(deletedWellGroup.getRowId());
            }

            if (!deletedWellGroupIds.isEmpty())
                deleteWellGroups(container, user, deletedWellGroupIds);

            // create/update well groups
            QueryUpdateService wellGroupQus = getWellGroupUpdateService(container, user);
            for (WellGroup group : plate.getWellGroups())
            {
                WellGroupImpl wellgroup = (WellGroupImpl) group;
                assert !wellgroup._deleted;
                String wellGroupInstanceLsid = wellgroup.getLSID();
                Map<String, Object> wellGroupRow;
                BatchValidationException wellGroupErrors = new BatchValidationException();

                if (wellgroup.getRowId() != null && wellgroup.getRowId() > 0)
                {
                    wellGroupRow = ObjectFactory.Registry.getFactory(WellGroupBean.class).toMap(WellGroupBean.from(wellgroup), new ArrayListMap<>());
                    wellGroupQus.updateRows(user, container, Collections.singletonList(wellGroupRow), null, wellGroupErrors, null, extraScriptContext);
                    if (wellGroupErrors.hasErrors())
                        throw wellGroupErrors;

                    savePropertyBag(container, user, wellGroupInstanceLsid, wellgroup.getProperties(), true);
                }
                else
                {
                    wellgroup.setPlateId(plateId);
                    wellGroupRow = ObjectFactory.Registry.getFactory(WellGroupBean.class).toMap(WellGroupBean.from(wellgroup), new ArrayListMap<>());

                    List<Map<String, Object>> insertedRows = wellGroupQus.insertRows(user, container, Collections.singletonList(wellGroupRow), wellGroupErrors, null, extraScriptContext);
                    if (wellGroupErrors.hasErrors())
                        throw wellGroupErrors;

                    wellGroupInstanceLsid = (String) insertedRows.get(0).get(WellTable.Column.Lsid.name());
                    wellgroup = ObjectFactory.Registry.getFactory(WellGroupImpl.class).fromMap(wellgroup, insertedRows.get(0));
                    savePropertyBag(container, user, wellGroupInstanceLsid, wellgroup.getProperties(), false);
                }
            }
            List<List<Integer>> wellGroupPositions = new LinkedList<>();
            List<Map<String, Object>> insertedRows = emptyList();

            // create new wells for this plate
            ObjectFactory<PositionImpl> factory = ObjectFactory.Registry.getFactory(PositionImpl.class);
            if (!updateExisting)
            {
                QueryUpdateService wellQus = getWellUpdateService(container, user);
                List<Map<String, Object>> wellRows = new ArrayList<>();
                Map<String, Map<String, Object>> wellDataMap = getWellDataMap(plate, wellData);

                for (int row = 0; row < plate.getRows(); row++)
                {
                    for (int col = 0; col < plate.getColumns(); col++)
                    {
                        PositionImpl position;
                        position = plate.getPosition(row, col);
                        if (position.getRowId() != null)
                            throw new IllegalStateException("Attempting to create a new plate but there are existing wells associated with it.");

                        position.setPlateId(plateId);
                        Map<String, Object> wellRow = factory.toMap(position, new CaseInsensitiveHashMap<>());

                        // Issue 51658: Do not serialize the position "description" to the row as this can collide
                        // with user furnished plate metadata.
                        wellRow.remove("Description");

                        if (wellDataMap.containsKey(position.getDescription()))
                        {
                            wellDataMap.get(position.getDescription()).forEach(
                                (key, value) -> wellRow.merge(key, value, (v1, v2) -> v1)
                            );
                        }

                        wellRows.add(wellRow);
                    }
                }

                Map<Enum, Object> configParameters = Map.of(AuditBehavior, AuditBehaviorType.DETAILED);
                BatchValidationException wellErrors = new BatchValidationException();
                insertedRows = wellQus.insertRows(user, container, wellRows, wellErrors, configParameters, extraScriptContext);
                if (wellErrors.hasErrors())
                    throw wellErrors;
            }

            // insert/update well to well group mappings
            if (!plate.getWellGroups().isEmpty())
            {
                if (updateExisting)
                {
                    for (Well well : plate.getWells())
                        wellGroupPositions.addAll(getWellGroupPositions(plate, well));

                    // delete all existing well group positions
                    deleteWellGroupPositions(plate);
                }
                else
                {
                    for (Map<String, Object> row : insertedRows)
                    {
                        PositionImpl position = factory.fromMap(row);
                        wellGroupPositions.addAll(getWellGroupPositions(plate, position));
                    }
                }

                // save well to well group positions
                String insertSql = "INSERT INTO " + AssayDbSchema.getInstance().getTableInfoWellGroupPositions() +
                        " (wellId, wellGroupId) VALUES (?, ?)";
                Table.batchExecute(AssayDbSchema.getInstance().getSchema(), insertSql, wellGroupPositions);
            }

            if (!updateExisting && !plate.getCustomFields().isEmpty())
                setFields(container, user, plate.getRowId(), plate.getCustomFields());

            final Long plateRowId = plateId;
            transaction.addCommitTask(() -> {
                clearCache(container, plate);
                indexPlate(container, plateRowId, false);
                if (plate.getPlateSet() != null)
                    indexPlateSet(SearchService.get().defaultTask().getQueue(container, SearchService.PRIORITY.modified), plate.getPlateSet());
            }, DbScope.CommitTaskOption.POSTCOMMIT);

            if (!skipAudit && !updateExisting)
            {
                var auditPlate = getPlate(container, plateRowId);
                if (auditPlate == null)
                    throw new IllegalStateException("Unable to audit plate after save. Plate not found.");

                addPlateCreatedAuditEvents(container, user, transaction, List.of(auditPlate), null);
            }

            transaction.commit();

            return plateId;
        }
    }

    private @NotNull Map<String, Map<String, Object>> getWellDataMap(
        @NotNull Plate plate,
        @Nullable List<Map<String, Object>> rawWellData
    ) throws ValidationException
    {
        if (rawWellData == null || rawWellData.isEmpty())
            return emptyMap();

        Set<String> keywords = CaseInsensitiveHashSet.of(
            WellTable.Column.Col.name(),
            WellTable.Column.Container.name(),
            WellTable.Column.Lsid.name(),
            WellTable.Column.PlateId.name(),
            WellTable.Column.Position.name(),
            WellTable.Column.Row.name(),
            WellTable.Column.RowId.name(),
            WELL_LOCATION
        );

        Map<String, Map<String, Object>> wellDataMap = new HashMap<>();
        int rowIdx = 0;

        for (var wellData : rawWellData)
        {
            rowIdx++;
            var wellDataRow = new CaseInsensitiveHashMap<>(wellData);
            var wellLocation = StringUtils.trimToNull(String.valueOf(wellDataRow.get(WELL_LOCATION)));
            if (wellLocation == null)
            {
                throw new ValidationException(String.format(
                    "Failed to resolve \"%s\" for row index (%d) on plate \"%s\". All well data must provide a \"%s\".", WELL_LOCATION, rowIdx, plate.getName(), rowIdx
                ));
            }

            var safeWellRow = new CaseInsensitiveHashMap<>();
            for (var entry : wellDataRow.entrySet())
            {
                if (StringUtils.trimToNull(entry.getKey()) == null || entry.getValue() == null)
                    continue;

                var key = entry.getKey();
                if (StringUtils.trimToNull(key) != null && keywords.contains(key))
                    continue;

                safeWellRow.put(key, entry.getValue());
            }

            if (!safeWellRow.isEmpty())
                wellDataMap.put(wellLocation, safeWellRow);
        }

        return wellDataMap;
    }

    // return a list of wellId and wellGroupId pairs
    private List<List<Integer>> getWellGroupPositions(Plate plate, Position position)
    {
        List<WellGroup> groups = plate.getWellGroups(position);
        List<List<Integer>> wellGroupPositions = new ArrayList<>(groups.size());

        for (WellGroup group : groups)
        {
            if (group.contains(position))
            {
                if (position.getRowId() == null)
                    throw new IllegalArgumentException("The specified well has not been saved to the database.");
                if (group.getRowId() == null)
                    throw new IllegalStateException("The well group : " + group.getName() + " has not been saved to the database.");
                Integer wellId = position.getRowId();
                Integer wellGroupId = group.getRowId();
                wellGroupPositions.add(List.of(wellId, wellGroupId));
            }
        }

        return wellGroupPositions;
    }

    private void savePropertyBag(
        Container container,
        User user,
        String ownerLsid,
        Map<String, Object> props,
        boolean updateExisting
    ) throws SQLException
    {
        // construct the LSID to associate with the property objects
        String classLsid = Lsid.parse(ownerLsid).edit().setObjectId(LSID_CLASS_OBJECT_ID).toString();

        if (updateExisting)
        {
            // delete any existing properties
            OntologyObject oo = OntologyManager.getOntologyObject(container, ownerLsid);
            if (oo != null)
            {
                OntologyManager.deleteProperties(container, oo.getObjectId());
            }
        }
        else
        {
            Map<String, ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, ownerLsid);
            if (resourceProperties != null && !resourceProperties.isEmpty())
                throw new IllegalStateException("Did not expect to find property set for new plate.");
        }

        ObjectProperty[] objectProperties = new ObjectProperty[props.size()];
        int idx = 0;
        for (Map.Entry<String, Object> entry : props.entrySet())
        {
            String propertyURI = Lsid.isLsid(entry.getKey()) ? entry.getKey() : classLsid + "#" + entry.getKey();
            if (entry.getValue() != null)
                objectProperties[idx++] = new ObjectProperty(ownerLsid, container, propertyURI, entry.getValue());
            else
                objectProperties[idx++] = new ObjectProperty(ownerLsid, container, propertyURI, entry.getValue(), PropertyType.STRING);
        }

        try
        {
            if (objectProperties.length > 0)
                OntologyManager.insertProperties(container, user, ownerLsid, objectProperties);
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
    }

    @Override
    public void deletePlate(Container container, User user, long rowId) throws Exception
    {
        Map<String, Object> key = Collections.singletonMap(PlateTable.Column.RowId.name(), rowId);
        QueryUpdateService qus = getPlateUpdateService(container, user);
        qus.deleteRows(user, container, Collections.singletonList(key), null, null);
    }

    // Called by the Plate Query Update Service after deleting a plate (post-commit)
    public void afterPlateDelete(Container container, Plate plate)
    {
        clearCache(container, plate);
        deindexPlates(List.of(Lsid.parse(plate.getLSID())));
    }

    // Called by the Plate Query Update Service before deleting a plate
    public void beforePlateDelete(Container container, Integer plateId)
    {
        assert requireActiveTransaction();

        Plate plate = PlateCache.getPlate(container, plateId);
        List<String> lsids = new ArrayList<>();
        lsids.add(plate.getLSID());
        for (WellGroup wellgroup : plate.getWellGroups())
            lsids.add(wellgroup.getLSID());

        SimpleFilter plateIdFilter = SimpleFilter.createContainerFilter(container);
        plateIdFilter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());

        OntologyManager.deleteOntologyObjects(container, lsids.toArray(new String[0]));
        deleteWellGroupPositions(plate);

        // delete any plate metadata values from the provisioned table
        AssayDbSchema schema = AssayDbSchema.getInstance();
        TableInfo provisionedTable = getPlateMetadataTable(container, User.getAdminServiceUser());
        if (provisionedTable != null)
        {
            SQLFragment sql2 = new SQLFragment("DELETE FROM ").append(provisionedTable, "")
                    .append(" WHERE Lsid IN (")
                    .append(" SELECT Lsid FROM ").append(AssayDbSchema.getInstance().getTableInfoWell(), "")
                    .append(" WHERE PlateId = ?)")
                    .add(plateId);
            new SqlExecutor(schema.getSchema()).execute(sql2);
        }

        Table.delete(schema.getTableInfoWell(), plateIdFilter);
        Table.delete(schema.getTableInfoWellGroup(), plateIdFilter);
    }

    // Called by the Plate Set Query Update Service before deleting a plate set
    public void beforePlateSetDelete(Container container, User user, Long rowId)
    {
        beforePlateSetsDelete(List.of(rowId), container);
    }

    private void beforePlateSetsDelete(Collection<Long> plateSetIds, Container container)
    {
        assert requireActiveTransaction();

        if (plateSetIds.isEmpty())
            return;

        final AssayDbSchema schema = AssayDbSchema.getInstance();
        final SqlDialect sqlDialect = schema.getSchema().getSqlDialect();

        // delete PlateSetEdge relationships
        {
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(schema.getTableInfoPlateSetEdge())
                    .append(" WHERE FromPlateSetId ").appendInClause(plateSetIds, sqlDialect)
                    .append(" OR ToPlateSetId ").appendInClause(plateSetIds, sqlDialect)
                    .append(" OR RootPlateSetId ").appendInClause(plateSetIds, sqlDialect);
            new SqlExecutor(schema.getSchema()).execute(sql);
        }

        // unmark as a primary plate set
        {
            SQLFragment sql = new SQLFragment("UPDATE ").append(schema.getTableInfoPlateSet())
                    .append(" SET PrimaryPlateSetId = NULL WHERE PrimaryPlateSetId ").appendInClause(plateSetIds, sqlDialect);
            new SqlExecutor(schema.getSchema()).execute(sql);
        }

        // unmark as a root plate set
        {
            SQLFragment sql = new SQLFragment("UPDATE ").append(schema.getTableInfoPlateSet())
                    .append(" SET RootPlateSetId = NULL WHERE RootPlateSetId ").appendInClause(plateSetIds, sqlDialect);
            new SqlExecutor(schema.getSchema()).execute(sql);
        }

        // The following tables are cleaned up via ON DELETE CASCADE when a plate set is deleted:
        // - assay.PlateSetProperty

        // Plate set documents in the search index are cleaned up via the search service container listener.
    }

    private void deleteWellGroups(Container container, User user, List<Integer> wellGroupRowIds) throws Exception
    {
        List<Map<String, Object>> rows = new ArrayList<>();

        wellGroupRowIds.forEach(rowId -> {
            if (rowId != null)
                rows.add(CaseInsensitiveHashMap.of("RowId", rowId));
        });

        if (rows.isEmpty())
            return;

        getWellGroupUpdateService(container, user).deleteRows(user, container, rows, null, null);
    }

    // Called by the WellGroup Query Update Service prior to deleting a well group
    public void beforeDeleteWellGroup(Container container, Integer wellGroupId)
    {
        final AssayDbSchema schema = AssayDbSchema.getInstance();
        DbScope scope = schema.getSchema().getScope();
        assert scope.isTransactionActive();

        new SqlExecutor(scope).execute("" +
                "DELETE FROM " + schema.getTableInfoWellGroupPositions() +
                " WHERE wellGroupId = ?", wellGroupId);
    }

    private void deleteWellGroupPositions(Plate plate)
    {
        final AssayDbSchema schema = AssayDbSchema.getInstance();
        DbScope scope = schema.getSchema().getScope();
        assert scope.isTransactionActive();

        new SqlExecutor(scope).execute("" +
                "DELETE FROM " + schema.getTableInfoWellGroupPositions() +
                " WHERE wellId IN (SELECT rowId FROM " + schema.getTableInfoWell() + " WHERE plateId=?)", plate.getRowId());
    }

    public void deleteAllPlateData(Container container)
    {
        try (DbScope.Transaction tx = ensureTransaction())
        {
            final AssayDbSchema schema = AssayDbSchema.getInstance();
            // delete plate hits
            {
                SQLFragment sql = new SQLFragment("DELETE FROM ").append(schema.getTableInfoHit())
                        .append(" WHERE wellLsid IN (SELECT lsid FROM ").append(schema.getTableInfoWell())
                        .append(" WHERE container = ?)").add(container);
                new SqlExecutor(schema.getSchema()).execute(sql);
            }

            // delete well group positions
            {
                SQLFragment sql = new SQLFragment("DELETE FROM ").append(schema.getTableInfoWellGroupPositions())
                        .append(" WHERE wellId IN (SELECT rowId FROM ").append(schema.getTableInfoWell())
                        .append(" WHERE container = ?)").add(container);
                new SqlExecutor(schema.getSchema()).execute(sql);
            }

            // delete PlateSetProperty mappings
            {
                SQLFragment sql = new SQLFragment("DELETE FROM ")
                        .append(schema.getTableInfoPlateSetProperty(), "")
                        .append(" WHERE PlateSetId IN (SELECT RowId FROM ").append(schema.getTableInfoPlateSet())
                        .append(" WHERE Container = ?)").add(container);
                new SqlExecutor(schema.getSchema()).execute(sql);
            }

            // delete plate metadata values from the provisioned table
            TableInfo provisionedTable = getPlateMetadataTable(container, User.getAdminServiceUser());
            if (provisionedTable != null)
            {
                SQLFragment sql = new SQLFragment("DELETE FROM ").append(provisionedTable)
                        .append(" WHERE Lsid IN (")
                        .append(" SELECT Lsid FROM ").append(schema.getTableInfoWell())
                        .append(" WHERE Container = ?)").add(container);
                new SqlExecutor(schema.getSchema()).execute(sql);
            }

            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            Table.delete(schema.getTableInfoWell(), filter);
            Table.delete(schema.getTableInfoWellGroup(), filter);
            Table.delete(schema.getTableInfoPlate(), filter);

            // delete empty plate sets in this container
            {
                SQLFragment emptyPlateSetsSql = new SQLFragment("SELECT RowId FROM ").append(schema.getTableInfoPlateSet())
                        .append(" WHERE RowId NOT IN (SELECT DISTINCT PlateSet FROM ").append(schema.getTableInfoPlate()).append(")")
                        .append(" AND Container = ?").add(container);

                ArrayList<Long> emptyPlateSetIds = new SqlSelector(schema.getSchema(), emptyPlateSetsSql).getArrayList(Long.class);

                if (!emptyPlateSetIds.isEmpty())
                {
                    beforePlateSetsDelete(emptyPlateSetIds, container);
                    tx.addCommitTask(() -> clearPlateSetCache(container, emptyPlateSetIds), DbScope.CommitTaskOption.POSTCOMMIT);

                    SQLFragment sql = new SQLFragment("DELETE FROM ").append(schema.getTableInfoPlateSet())
                            .append(" WHERE RowId ").appendInClause(emptyPlateSetIds, schema.getSchema().getSqlDialect());
                    new SqlExecutor(schema.getSchema()).execute(sql);
                }
            }

            tx.commit();
        }

        clearCache(container);
    }

    @Override
    public void registerDetailsLinkResolver(PlateDetailsResolver resolver)
    {
        _detailsLinkResolvers.add(resolver);
    }

    public ActionURL getDetailsURL(Plate plate)
    {
        for (PlateDetailsResolver resolver : _detailsLinkResolvers)
        {
            ActionURL detailsURL = resolver.getDetailsURL(plate);
            if (detailsURL != null)
                return detailsURL;
        }
        return null;
    }

    public List<PlateLayoutHandler> getPlateLayoutHandlers()
    {
        List<PlateLayoutHandler> result = new ArrayList<>(_plateLayoutHandlers.values());
        result.sort(Comparator.comparing(PlateLayoutHandler::getAssayType, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Nullable
    public PlateLayoutHandler getPlateLayoutHandler(String plateTypeName)
    {
        return _plateLayoutHandlers.get(plateTypeName);
    }
    
    public UserSchema getPlateUserSchema(Container container, User user)
    {
        return QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME);
    }

    @Override
    public TableInfo getPlateTableInfo()
    {
        return AssayDbSchema.getInstance().getTableInfoPlate();
    }

    public @NotNull TableInfo getPlateTable(Container container, User user)
    {
        return getPlateTable(container, user, null);
    }

    private @NotNull TableInfo getPlateTable(Container container, User user, @Nullable ContainerFilter cf)
    {
        return getPlateUserSchema(container, user).getTableOrThrow(PlateTable.NAME, cf);
    }

    private @NotNull TableInfo getWellTable(Container container, User user)
    {
        return getWellTable(container, user, null);
    }

    private @NotNull TableInfo getWellTable(Container container, User user, @Nullable ContainerFilter cf)
    {
        return getPlateUserSchema(container, user).getTableOrThrow(WellTable.NAME, cf);
    }

    private @NotNull QueryUpdateService requiredUpdateService(@NotNull TableInfo table)
    {
        QueryUpdateService qus = table.getUpdateService();
        if (qus == null)
            throw new IllegalStateException(String.format("Unable to resolve QueryUpdateService for %s.", table.getName()));
        return qus;
    }

    private @NotNull QueryUpdateService getPlateUpdateService(Container container, User user)
    {
        return requiredUpdateService(PlateSchema.getPlateTable(container, user, null));
    }

    private @NotNull QueryUpdateService getPlateSetUpdateService(Container container, User user)
    {
        return requiredUpdateService(PlateSchema.getPlateSetTable(container, user, null));
    }

    private @NotNull QueryUpdateService getWellGroupUpdateService(Container container, User user)
    {
        return requiredUpdateService(PlateSchema.getWellGroupTable(container, user, null));
    }

    private @NotNull QueryUpdateService getWellUpdateService(Container container, User user)
    {
        return requiredUpdateService(PlateSchema.getWellTable(container, user, null));
    }

    private static class PlateLsidHandler implements LsidManager.LsidHandler<Plate>
    {
        @Override
        @Nullable
        public ActionURL getDisplayURL(Lsid lsid)
        {
            Plate plate = getObject(lsid);
            if (plate == null)
                return null;
            return plate.detailsURL();
        }

        @Override
        public Plate getObject(Lsid lsid)
        {
            if (lsid == null)
                return null;

            return PlateManager.get().getPlate(lsid);
        }

        @Override
        public Container getContainer(Lsid lsid)
        {
            Plate plate = getObject(lsid);
            if (plate == null)
                return null;
            return plate.getContainer();
        }

        @Override
        public boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm)
        {
            Container c = getContainer(lsid);
            if (c != null)
                return c.hasPermission(user, perm);
            return false;
        }
    }

    private static class WellGroupLsidHandler implements LsidManager.LsidHandler<WellGroup>
    {
        @Override
        @Nullable
        public ActionURL getDisplayURL(Lsid lsid)
        {
            WellGroup wellGroup = getObject(lsid);
            if (wellGroup == null)
                return null;
            return wellGroup.detailsURL();
        }

        @Override
        public WellGroup getObject(Lsid lsid)
        {
            if (lsid == null)
                return null;
            return PlateManager.get().getWellGroup(lsid.toString());
        }

        @Override
        public Container getContainer(Lsid lsid)
        {
            WellGroup wellGroup = getObject(lsid);
            if (wellGroup == null)
                return null;
            return wellGroup.getContainer();
        }

        @Override
        public boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm)
        {
            Container c = getContainer(lsid);
            if (c != null)
                return c.hasPermission(user, perm);
            return false;
        }
    }
    
    public void registerLsidHandlers()
    {
        if (_lsidHandlersRegistered)
            throw new IllegalStateException("Cannot register lsid handlers twice.");

        LsidManager.get().registerHandler("Plate", new PlateLsidHandler());
        LsidManager.get().registerHandler("WellGroup", new WellGroupLsidHandler());

        _lsidHandlersRegistered = true;
    }

    private void copyProperties(@NotNull Plate source, @NotNull Plate copy)
    {
        for (String property : source.getPropertyNames())
            copy.setProperty(property, source.getProperty(property));
    }

    private void copyWellData(User user, @NotNull Plate source, @NotNull Plate copy, boolean copySample) throws Exception
    {
        assert requireActiveTransaction();

        var container = source.getContainer();
        var wellTable = getWellTable(container, user);

        var lsidColumn = WellTable.Column.Lsid.name();
        var lsidFieldKey = WellTable.Column.Lsid.fieldKey();
        var rowIdColumn = WellTable.Column.RowId.name();
        var sampleIdColumn = WellTable.Column.SampleID.name();

        var sourceWellData = new TableSelector(wellTable, Set.of(rowIdColumn, lsidColumn, sampleIdColumn), new SimpleFilter(WellTable.Column.PlateId.fieldKey(), source.getRowId()), new Sort(rowIdColumn)).getMapArray();
        var copyWellData = new TableSelector(wellTable, Set.of(rowIdColumn, lsidColumn), new SimpleFilter(WellTable.Column.PlateId.fieldKey(), copy.getRowId()), new Sort(rowIdColumn)).getMapArray();

        if (sourceWellData.length != copyWellData.length)
        {
            String msg = "Failed to copy well data. Source plate \"%s\" contains %d rows of well data which does not match %d in copied plate.";
            throw new ValidationException(String.format(msg, source.getName(), sourceWellData.length, copyWellData.length));
        }

        var sourceWellLsids = Arrays.stream(sourceWellData).map(data -> data.get(lsidColumn)).toList();
        var sourceFilter = new SimpleFilter(lsidFieldKey, sourceWellLsids, CompareType.IN);

        final List<ColumnInfo> wellMetadataFields;
        final Map<String, Map<String, Object>> sourceMetaData;

        var metadataTable = getPlateMetadataTable(container, user);
        if (metadataTable != null)
        {
            wellMetadataFields = metadataTable.getColumns()
                    .stream().filter(c -> !lsidFieldKey.equals(c.getFieldKey()))
                    .toList();

            var metaDataRows = new TableSelector(metadataTable, sourceFilter, null).getMapCollection(); // note that row map keys here are column.getAlias()
            sourceMetaData = new CaseInsensitiveHashMap<>();
            for (var row : metaDataRows)
                sourceMetaData.put((String) row.get(lsidColumn), row);
        }
        else
        {
            wellMetadataFields = emptyList();
            sourceMetaData = emptyMap();
        }

        List<Map<String, Object>> newWellData = new ArrayList<>();

        for (int i = 0; i < sourceWellData.length; i++)
        {
            var sourceRow = sourceWellData[i];
            String sourceWellLSID = (String) sourceRow.get(lsidColumn);
            var copyRow = copyWellData[i];

            var updateCopyRow = new CaseInsensitiveHashMap<>();
            if (copySample && sourceRow.get(sampleIdColumn) != null)
                updateCopyRow.put(sampleIdColumn, sourceRow.get(sampleIdColumn));

            if (sourceMetaData.containsKey(sourceWellLSID))
            {
                var sourceMetaDataRow = sourceMetaData.get(sourceWellLSID);

                for (ColumnInfo col : wellMetadataFields)
                {
                    // Issue 53017: get row value based on ColumnInfo, and put in updateCopyRow using column name as key
                    var value = col.getValue(sourceMetaDataRow);
                    if (value != null)
                        updateCopyRow.put(col.getName(), value);
                }
            }

            if (!updateCopyRow.isEmpty())
            {
                updateCopyRow.put(rowIdColumn, copyRow.get(rowIdColumn));
                newWellData.add(updateCopyRow);
            }
        }

        if (newWellData.isEmpty())
            return;

        var errors = new BatchValidationException();
        Map<Enum, Object> configParameters = Map.of(AuditBehavior, AuditBehaviorType.DETAILED);
        Map<String, Object> extraScriptContext = CaseInsensitiveHashMap.of(PLATE_COPY_FLAG, true);
        getWellUpdateService(container, user).updateRows(user, container, newWellData, null, errors, configParameters, extraScriptContext);
        if (errors.hasErrors())
            throw errors;
    }

    private void copyWellGroups(@NotNull Plate source, @NotNull Plate copy)
    {
        for (WellGroup originalGroup : source.getWellGroups())
        {
            List<Position> positions = new ArrayList<>();
            for (Position position : originalGroup.getPositions())
                positions.add(copy.getPosition(position.getRow(), position.getColumn()));
            WellGroup copyGroup = copy.addWellGroup(originalGroup.getName(), originalGroup.getType(), positions);
            for (String property : originalGroup.getPropertyNames())
                copyGroup.setProperty(property, originalGroup.getProperty(property));
        }
    }

    public Plate copyPlate(
        Container container,
        User user,
        Long sourcePlateRowId,
        boolean copyAsTemplate,
        @Nullable Long destinationPlateSetRowId,
        @Nullable String name,
        @Nullable String description,
        @Nullable Boolean copySamples
    ) throws Exception
    {
        if (!container.hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("Failed to copy plate. Insufficient permissions.");

        ContainerFilter cf = getPlateLookupContainerFilter(container, user);
        PlateImpl sourcePlate = (PlateImpl) getPlate(cf, sourcePlateRowId);
        if (sourcePlate == null)
            throw new ValidationException(String.format("Failed to copy plate. Unable to resolve source plate with RowId (%d).", sourcePlateRowId));

        if (destinationPlateSetRowId == null)
            destinationPlateSetRowId = sourcePlate.getPlateSetId();
        PlateSet destinationPlateSet = requirePlateSet(container, destinationPlateSetRowId, "Failed to copy plate.");

        if (!container.equals(destinationPlateSet.getContainer()))
            throw new ValidationException(String.format("Failed to copy plate. The destination folder \"%s\" does not match the plate set folder \"%s\".", container.getPath(), destinationPlateSet.getContainer().getPath()));

        boolean hasName = StringUtils.trimToNull(name) != null;

        if (copyAsTemplate && !hasName)
            throw new ValidationException("Failed to copy plate template. A \"name\" is required.");

        if (!copyAsTemplate && ((PlateSetImpl) destinationPlateSet).isFull())
            throw new ValidationException(String.format("Failed to copy plate. The plate set \"%s\" is full.", destinationPlateSet.getName()));

        if (hasName)
        {
            if (copyAsTemplate)
            {
                if (isDuplicatePlateTemplateName(container, name))
                    throw new ValidationException(String.format("Failed to copy plate template. A plate template already exists with the name \"%s\".", name));
            }
            else if (isDuplicatePlateName(container, user, name, destinationPlateSet))
                throw new ValidationException(String.format("Failed to copy plate. A plate already exists with the name \"%s\".", name));
        }

        try (DbScope.Transaction tx = ensureTransaction())
        {
            ensureTransactionAuditId(tx, container, user, QueryService.AuditAction.INSERT);

            // Copy the plate
            PlateImpl newPlate = new PlateImpl(container, name, null, sourcePlate.getAssayType(), sourcePlate.getPlateType());
            List<PlateCustomField> newFields = new ArrayList<>(sourcePlate.getCustomFields());

            if (copyAsTemplate)
            {
                newPlate.setTemplate(true);
                newFields.removeIf((f) -> WellTable.Column.SampleID.fieldKey().equals(f.getFieldKey()));
            }
            else
                newPlate.setPlateSet(destinationPlateSet);

            newPlate.setCustomFields(newFields);
            newPlate.setDescription(description);

            copyProperties(sourcePlate, newPlate);
            copyWellGroups(sourcePlate, newPlate);

            // Save the plate
            long plateId = savePlateImpl(container, user, newPlate, true, null, true);
            newPlate = (PlateImpl) getPlate(container, plateId);
            if (newPlate == null)
                throw new IllegalStateException("Unexpected failure. Failed to retrieve plate after save (pre-commit).");

            // Copy plate metadata
            if (copySamples == null)
                copySamples = true;
            copyWellData(user, sourcePlate, newPlate, !newPlate.isTemplate() && copySamples);

            // Specify the source plate for auditing
            newPlate.setSourcePlateRowId(sourcePlate.getRowId());
            String auditComment = String.format("Copied from %s \"%s\".", sourcePlate.isTemplate() ? "plate template" : "plate", sourcePlate.getName());
            addPlateCreatedAuditEvents(container, user, tx, List.of(newPlate), auditComment);

            tx.commit();

            return newPlate;
        }
    }

    /**
     * @deprecated Use {@link #copyPlate(Container, User, Long, boolean, Long, String, String, Boolean)}
     */
    @Deprecated
    public Plate copyPlateDeprecated(Plate source, User user, Container destContainer)
            throws Exception
    {
        if (isDuplicatePlateName(destContainer, user, source.getName(), null))
            throw new NameConflictException(source.getName());
        Plate newPlate = createPlate(destContainer, source.getAssayType(), source.getPlateType());
        newPlate.setName(source.getName());

        copyProperties(source, newPlate);
        copyWellGroups(source, newPlate);

        long plateId = save(destContainer, user, newPlate);
        return getPlate(destContainer, plateId);
    }

    @Override
    public void registerPlateLayoutHandler(PlateLayoutHandler handler)
    {
        if (_plateLayoutHandlers.containsKey(handler.getAssayType()))
        {
            throw new IllegalArgumentException(handler.getAssayType());
        }
        _plateLayoutHandlers.put(handler.getAssayType(), handler);
    }

    public void clearCache(Container c, Plate plate)
    {
        PlateCache.uncache(c, plate);
    }

    private void clearCache(Container c)
    {
        PlateCache.uncache(c);
    }

    /**
     * Clear the plate cache for an arbitrary collection of plates where only the rowIds are known.
     */
    private void clearCache(Collection<Long> plateRowIds)
    {
        var table = AssayDbSchema.getInstance().getTableInfoPlate();
        SQLFragment sql = new SQLFragment("SELECT rowId, container FROM ").append(table, "")
                .append("WHERE rowId ").appendInClause(plateRowIds, table.getSqlDialect());
        Collection<Map<String, Object>> plateData = new SqlSelector(table.getSchema(), sql).getMapCollection();

        for (Map<String, Object> data : plateData)
        {
            Long rowId = asLong(data.get("rowId"));
            String containerId = (String) data.get("container");
            if (StringUtils.trimToNull(containerId) == null)
            {
                LOG.warn(String.format("clearCache: failed to resolve containerId for plate with rowId %d", rowId));
                continue;
            }

            Container c = ContainerManager.getForId(containerId);
            if (c == null)
            {
                LOG.warn(String.format("clearCache: failed to resolve container for plate with rowId %d with containerId %s.", rowId, containerId));
                continue;
            }
            PlateCache.uncache(c, rowId);
        }
    }

    private void clearPlateSetCache(Container container, Collection<Long> plateSetRowIds)
    {
        for (Long plateSetId : plateSetRowIds)
            PlateSetCache.uncache(container, plateSetId);
    }

    @Override
    public DilutionCurve getDilutionCurve(List<WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, StatsService.CurveFitType type) throws FitFailedException
    {
        return CurveFitFactory.getCurveImpl(wellGroups, assumeDecreasing, percentCalculator, type);
    }

    @Override
    @NotNull
    public List<? extends PlateType> getPlateTypes()
    {
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateType()).getArrayList(PlateTypeBean.class);
    }

    @Override
    @Nullable
    public PlateType getPlateType(int rows, int columns)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Rows"), rows);
        filter.addCondition(FieldKey.fromParts("Columns"), columns);

        return new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateType(), filter, null).getObject(PlateTypeBean.class);
    }

    public record PlateLayout(String name, PlateType type, String assayType, String description){}

    @NotNull
    public List<PlateLayout> getPlateLayouts()
    {
        List<PlateLayout> layouts = new ArrayList<>();
        for (PlateLayoutHandler handler : getPlateLayoutHandlers())
        {
            if (TsvPlateLayoutHandler.TYPE.equalsIgnoreCase(handler.getAssayType()))
                continue;

            for (PlateType type : handler.getSupportedPlateTypes())
            {
                int wellCount = type.getRows() * type.getColumns();
                String sizeDescription = wellCount + " well (" + type.getRows() + "x" + type.getColumns() + ") ";

                List<String> layoutTypes = handler.getLayoutTypes(type);
                if (layoutTypes.isEmpty())
                {
                    String description = sizeDescription + handler.getAssayType();
                    layouts.add(new PlateLayout(null, type, handler.getAssayType(), description));
                }
                else
                {
                    for (String layoutName : layoutTypes)
                    {
                        String description = sizeDescription + handler.getAssayType() + " " + layoutName;
                        layouts.add(new PlateLayout(layoutName, type, handler.getAssayType(), description));
                    }
                }
            }
        }
        return layouts;
    }

    public PlateType getPlateType(Long plateTypeId)
    {
        if (plateTypeId == null) return null;
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateType()).getObject(plateTypeId, PlateTypeBean.class);
    }

    public @NotNull Map<String, List<Map<String, Object>>> getPlateOperationConfirmationData(
        @NotNull Container container,
        @NotNull User user,
        @NotNull Set<Long> plateRowIds
    )
    {
        Set<Long> permittedIds = new HashSet<>(plateRowIds);
        Set<Long> notPermittedIds = new HashSet<>();

        ExperimentService.get().getObjectReferencers().forEach(referencer ->
                notPermittedIds.addAll(referencer.getItemsWithReferences(permittedIds, "plate")));
        permittedIds.removeAll(notPermittedIds);

        Map<Long, Plate> plates = new LongHashMap<>();
        plateRowIds.forEach(rowId -> {
            // TODO: This is really expensive. Find a way to consolidate this check into a single query.
            if (rowId != null)
                plates.put(rowId, getPlate(container, rowId));
        });

        permittedIds.forEach(plateRowId -> {
            Plate plate = plates.get(plateRowId);
            if (plate == null || getRunCountUsingPlate(container, user, plate) > 0)
                notPermittedIds.add(plateRowId);
        });
        permittedIds.removeAll(notPermittedIds);

        List<Map<String, Object>> allowedRows = new ArrayList<>();
        permittedIds.forEach(rowId -> {
            Plate plate = plates.get(rowId);
            Map<String, Object> allowedRow = new HashMap<>();
            allowedRow.put("RowId", rowId);
            if (plate.getContainer().hasPermission(user, ReadPermission.class))
                allowedRow.put("ContainerPath", plate.getContainer().getPath());
            allowedRows.add(allowedRow);
        });

        List<Map<String, Object>> notAllowedRows = new ArrayList<>();
        notPermittedIds.forEach(rowId -> {
            Plate plate = plates.get(rowId);
            Map<String, Object> rowMap = new CaseInsensitiveHashMap<>();
            rowMap.put("RowId", rowId);

            if (plate != null && plate.getContainer().hasPermission(user, ReadPermission.class))
            {
                rowMap.put("Name", plate.getName());
                rowMap.put("ContainerPath", plate.getContainer().getPath());
            }

            notAllowedRows.add(rowMap);
        });

        return Map.of("allowed", allowedRows, "notAllowed", notAllowedRows);
    }

    private void deindexPlates(Collection<Lsid> plateLsids)
    {
        Set<String> documentIds = new HashSet<>();
        for (Lsid lsid : plateLsids)
            documentIds.add(PlateDocumentProvider.getDocumentId(lsid));
        SearchService.get().deleteResources(documentIds);
    }

    private void pausePlateIndexing()
    {
        _pausePlateIndex.set(true);
    }

    private void resumePlateIndexing()
    {
        _pausePlateIndex.set(false);
        if (!_plateIndexMap.isEmpty())
        {
            synchronized (PLATE_INDEX_LOCK)
            {
                LOG.debug("Resume indexing");
                BulkPlateIndexer indexer = new BulkPlateIndexer(new HashMap<>(_plateIndexMap));
                _plateIndexMap.clear();
                indexer.start();
            }
        }
    }

    private void indexPlate(Container c, Long plateRowId, boolean ignorePauseFlag)
    {
        if (_pausePlateIndex.get() && !ignorePauseFlag)
        {
            _plateIndexMap.computeIfAbsent(c, k -> new HashSet<>()).add(plateRowId);
        }
        else
        {
            Plate plate = getPlate(c, plateRowId);

            if (plate == null)
                return;

            indexPlate(SearchService.get().defaultTask().getQueue(c, SearchService.PRIORITY.modified), plate);
        }
    }

    private void indexPlate(SearchService.TaskIndexingQueue queue, @NotNull Plate plate)
    {
        WebdavResource resource = PlateDocumentProvider.createDocument(plate);
        queue.addResource(resource);
    }

    public void indexPlates(SearchService.TaskIndexingQueue queue, @Nullable Date modifiedSince)
    {
        Container c = queue.getContainer();
        for (Plate plate : getPlates(c))
        {
            if (modifiedSince == null || modifiedSince.before(((PlateImpl) plate).getModified()))
                indexPlate(queue, plate);
        }
    }

    public void indexPlateSet(Container container, Long plateSetRowId)
    {
        PlateSet plateSet = getPlateSet(container, plateSetRowId);
        if (plateSet == null)
            return;

        indexPlateSet(plateSet);
    }

    private void indexPlateSet(@NotNull PlateSet plateSet)
    {
        indexPlateSet(SearchService.get().defaultTask().getQueue(plateSet.getContainer(), SearchService.PRIORITY.modified), plateSet);
    }

    private void indexPlateSet(SearchService.TaskIndexingQueue queue, @NotNull PlateSet plateSet)
    {
        queue.addResource(PlateSetDocumentProvider.createDocument(plateSet));
    }

    public void indexPlateSets(SearchService.TaskIndexingQueue queue, @Nullable Date modifiedSince)
    {
        for (PlateSet plateSet : getPlateSets(queue.getContainer()))
        {
            if (modifiedSince == null || modifiedSince.before(((PlateSetImpl) plateSet).getModified()))
                indexPlateSet(queue, plateSet);
        }
    }

    public static void deindexPlateSet(Container container, Long plateSetRowId)
    {
        if (plateSetRowId == null)
            return;

        SearchService.get().deleteResources(Set.of(PlateSetDocumentProvider.getDocumentId(container, plateSetRowId)));
    }

    /**
     * Returns the domain attached to the Well table,
     */
    public @Nullable Domain getPlateMetadataDomain(Container container, User user)
    {
        return getPlateMetadataDomain(container, user, false);
    }

    /**
     * Returns the domain attached to the Well table,
     */
    public @Nullable Domain getPlateMetadataDomain(Container container, User user, boolean forUpdate)
    {
        // the domain is scoped at the project level (project and subfolder scoping)
        String domainURI = PlateMetadataDomainKind.generateDomainURI(getPlateMetadataDomainContainer(container));
        return PropertyService.get().getDomain(container, domainURI, forUpdate);
    }

    public @Nullable TableInfo getPlateMetadataTable(Container container, User user)
    {
        Domain domain = getPlateMetadataDomain(container, user);
        if (domain != null)
            return StorageProvisioner.createTableInfo(domain);
        return null;
    }

    public Container getPlateMetadataDomainContainer(Container container)
    {
        // scope the metadata container to the project
        if (container.isRoot())
            return container;
        return container.isProject() ? container : container.getProject();
    }

    @Override
    public @NotNull Domain ensurePlateMetadataDomain(Container container, User user, boolean forUpdate) throws ValidationException
    {
        Domain metadataDomain = getPlateMetadataDomain(container, user, forUpdate);

        if (metadataDomain == null)
        {
            DomainKind<?> domainKind = PropertyService.get().getDomainKindByName(PlateMetadataDomainKind.KIND_NAME);
            Container domainContainer = getPlateMetadataDomainContainer(container);

            if (!domainKind.canCreateDefinition(user, domainContainer))
                throw new IllegalArgumentException("Unable to create the plate well domain in folder: " + domainContainer.getPath() + "\". Insufficient permissions.");

            metadataDomain = DomainUtil.createDomain(PlateMetadataDomainKind.KIND_NAME, new GWTDomain(), null, domainContainer, user, PlateMetadataDomainKind.DOMAiN_NAME, null, forUpdate);
        }
        return metadataDomain;
    }

    /**
     * Adds custom fields to the well domain
     */
    public @NotNull List<PlateCustomField> createPlateMetadataFields(Container container, User user, List<GWTPropertyDescriptor> fields) throws Exception
    {
        Domain metadataDomain = ensurePlateMetadataDomain(container, user, true);
        DomainKind<?> domainKind = metadataDomain.getDomainKind();

        if (!domainKind.canEditDefinition(user, metadataDomain))
            throw new IllegalArgumentException("Unable to create field on domain \"" + metadataDomain.getTypeURI() + "\". Insufficient permissions.");

        if (!fields.isEmpty())
        {
            try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
            {
                Set<String> existingProperties = metadataDomain.getProperties().stream().map(ImportAliasable::getName).collect(Collectors.toSet());
                for (GWTPropertyDescriptor pd : fields)
                {
                    if (existingProperties.contains(pd.getName()))
                        throw new IllegalStateException(String.format("Unable to create field: %s on domain: %s. The field already exists.", pd.getName(), metadataDomain.getTypeURI()));

                    DomainUtil.addProperty(metadataDomain, pd, new HashMap<>(), new CaseInsensitiveHashSet(), null);
                }
                metadataDomain.save(user);
                tx.commit();
            }
        }
        return getPlateMetadataFields(container, user);
    }

    public @NotNull List<PlateCustomField> deletePlateMetadataFields(Container container, User user, List<PlateCustomField> fields) throws Exception
    {
        Domain metadataDomain = getPlateMetadataDomain(container, user, true);

        if (metadataDomain == null)
            throw new IllegalArgumentException("Unable to remove fields from the domain, the domain was not found.");

        if (!metadataDomain.getDomainKind().canEditDefinition(user, metadataDomain))
            throw new IllegalArgumentException("Unable to remove fields on domain \"" + metadataDomain.getTypeURI() + "\". Insufficient permissions.");

        if (!fields.isEmpty())
        {
            List<String> propertyURIs = new ArrayList<>();
            for (PlateCustomField field : fields)
            {
                if (field.getPropertyURI() == null)
                    throw new IllegalStateException("Unable to remove fields, the property URI must be specified.");

                propertyURIs.add(field.getPropertyURI());
            }

            // validate in use fields
            SQLFragment sql = new SQLFragment("SELECT COUNT(DISTINCT PlateSetId) FROM ").append(AssayDbSchema.getInstance().getTableInfoPlateSetProperty(), "PP")
                    .append(" WHERE PropertyURI ").appendInClause(propertyURIs, AssayDbSchema.getInstance().getSchema().getSqlDialect());
            int inUsePlateSets = new SqlSelector(AssayDbSchema.getInstance().getSchema(), sql).getObject(Integer.class);
            if (inUsePlateSets > 0)
                throw new IllegalArgumentException(String.format("Unable to remove fields from domain, there are %d plate sets that are referencing these fields. Fields need to be removed from the plate sets first.", inUsePlateSets));

            try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
            {
                Set<String> existingProperties = metadataDomain.getProperties().stream().map(ImportAliasable::getPropertyURI).collect(Collectors.toSet());
                for (PlateCustomField field : fields)
                {
                    if (!existingProperties.contains(field.getPropertyURI()))
                        throw new IllegalStateException(String.format("Unable to remove field: %s on domain: %s. The field does not exist.", field.getName(), metadataDomain.getTypeURI()));

                    DomainProperty dp = metadataDomain.getPropertyByURI(field.getPropertyURI());
                    if (dp != null)
                        dp.delete();
                }
                metadataDomain.save(user);
                tx.commit();
            }
        }
        return getPlateMetadataFields(container, user);
    }

    public @NotNull List<PlateCustomField> getPlateMetadataFields(Container container, User user)
    {
        return getPlateMetadataFields(container, user, false);
    }

    private @NotNull List<PlateCustomField> getPlateMetadataFields(Container container, User user, boolean includeBuiltIn)
    {
        List<PlateCustomField> fields = new ArrayList<>();
        if (includeBuiltIn)
        {
            TableInfo wellTable = getWellTable(container, user);
            fields.add(new PlateCustomField(wellTable.getColumn(WellTable.Column.Type.fieldKey())));
            fields.add(new PlateCustomField(wellTable.getColumn(WellTable.Column.WellGroup.fieldKey())));
            fields.add(new PlateCustomField(wellTable.getColumn(WellTable.Column.ReplicateGroup.fieldKey())));
            fields.add(new PlateCustomField(wellTable.getColumn(WellTable.Column.SampleID.fieldKey())));
        }

        Domain metadataDomain = getPlateMetadataDomain(container, user);
        if (metadataDomain != null)
        {
            List<PlateCustomField> customFields = metadataDomain.getProperties()
                    .stream()
                    .map(PlateCustomField::new)
                    .sorted(Comparator.comparing(k -> k.getName().toLowerCase()))
                    .toList();
            fields.addAll(customFields);
        }

        return unmodifiableList(fields);
    }

    public @NotNull List<PlateCustomField> addFields(
        Container container,
        User user,
        Long plateId,
        List<PlateCustomField> fields
    ) throws SQLException, ValidationException
    {
        if (plateId == null)
            throw new IllegalArgumentException("Failed to add plate custom fields. Invalid plateId provided.");

        if (fields == null || fields.isEmpty())
            throw new IllegalArgumentException("Failed to add plate custom fields. No fields specified.");

        Plate plate = requirePlate(container, plateId, "Failed to add plate custom fields.");
        PlateSet plateSet = requirePlateSet(plate, "Failed to add plate custom fields.");

        List<Map<String, Object>> fieldsToAdd = new ArrayList<>();
        Map<Boolean, List<PlateCustomField>> fieldsPartition = fields.stream().collect(Collectors.partitioningBy(PlateCustomField::isBuiltIn));

        // Process metadata fields
        if (!fieldsPartition.get(false).isEmpty())
        {
            Domain domain = getPlateMetadataDomain(container, user);
            if (domain == null)
                throw new IllegalArgumentException("Failed to add plate custom fields. Custom fields domain does not exist. Try creating fields first.");

            Set<String> existingPropsMetadata = plate.getCustomFields().stream().map(PlateCustomField::getPropertyURI).collect(Collectors.toSet());
            for (PlateCustomField field : fieldsPartition.get(false))
            {
                DomainProperty dp = domain.getPropertyByURI(field.getPropertyURI());
                if (dp == null)
                    throw new IllegalArgumentException("Failed to add plate custom field. \"" + field.getPropertyURI() + "\" does not exist on domain.");
                if (!existingPropsMetadata.contains(field.getPropertyURI()))
                {
                    fieldsToAdd.add(Map.of(
                        "rowId", plateSet.getRowId(),
                        "propertyId", dp.getPropertyId(),
                        "propertyURI", dp.getPropertyURI()
                    ));
                }
            }
        }

        // Process built-in fields
        if (!fieldsPartition.get(true).isEmpty())
        {
            Set<FieldKey> existingPropsBuiltIn = plate.getCustomFields().stream()
                    .filter(PlateCustomField::isBuiltIn)
                    .map(PlateCustomField::getFieldKey)
                    .collect(Collectors.toSet());

            for (PlateCustomField field : fieldsPartition.get(true))
            {
                if (!existingPropsBuiltIn.contains(field.getFieldKey()))
                {
                    fieldsToAdd.add(Map.of(
                        "rowId", plateSet.getRowId(),
                        "fieldKey", field.getFieldKey().getName()
                    ));
                }
            }
        }

        if (!fieldsToAdd.isEmpty())
        {
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                List<List<?>> insertedValues = new LinkedList<>();
                for (Map<String, Object> m : fieldsToAdd)
                    insertedValues.add(Arrays.asList(plateSet.getRowId(), m.get("propertyId"), m.get("propertyURI"), m.get("fieldKey")));

                String insertSql = "INSERT INTO " + AssayDbSchema.getInstance().getTableInfoPlateSetProperty() +
                        " (plateSetId, propertyId, propertyURI, FieldKey)" +
                        " VALUES (?, CAST(? AS INT), " +
                        (DbScope.getLabKeyScope().getSqlDialect().isSqlServer() ? "CAST(? AS VARCHAR(300))" : "CAST(? AS VARCHAR)") +
                        ", CAST(? AS VARCHAR))";
                Table.batchExecute(AssayDbSchema.getInstance().getSchema(), insertSql, insertedValues);

                transaction.addCommitTask(() -> PlateCache.uncache(container, plateSet), DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
        }

        return getFields(container, plateId);
    }

    public @NotNull List<PlateCustomField> getFields(Container container, Long plateId) throws ValidationException
    {
        Plate plate = requirePlate(container, plateId, "Failed to get plate custom fields.");
        return plate.getCustomFields();
    }

    private @NotNull List<PlateCustomField> getCustomFields(Container container, @NotNull Domain metadataDomain, long plateId)
    {
        AssayDbSchema schema = AssayDbSchema.getInstance();
        SQLFragment sql = new SQLFragment("SELECT FieldKey, PropertyURI FROM ").append(schema.getTableInfoPlateSetProperty(), "PP")
                .append(" INNER JOIN ").append(schema.getTableInfoPlate(), "PL").append(" ON PL.PlateSet = PP.PlateSetId")
                .append(" WHERE PL.RowId = ?").add(plateId);

        List<PlateCustomField> fields = new ArrayList<>();
        TableInfo wellTable = getWellTable(container, User.getAdminServiceUser());
        new SqlSelector(schema.getSchema(), sql).forEach(result -> {
            String fieldKey = result.getString("FieldKey");
            if (fieldKey != null)
                fields.add(new PlateCustomField(wellTable.getColumn(FieldKey.fromParts(fieldKey))));
            else
            {
                String propertyURI = result.getString("PropertyURI");
                DomainProperty dp = metadataDomain.getPropertyByURI(propertyURI);
                if (dp != null)
                    fields.add(new PlateCustomField(dp));
                else
                    fields.add(new PlateCustomField(propertyURI));
            }
        });

        // For now, we have a static sorting of the columns
        return sortCustomFields(fields);
    }

    private List<PlateCustomField> sortCustomFields(List<PlateCustomField> fields)
    {
        Map<FieldKey, Integer> order = new HashMap<>();
        order.put(WellTable.Column.Type.fieldKey(), 0);
        order.put(WellTable.Column.WellGroup.fieldKey(), 1);
        order.put(WellTable.Column.ReplicateGroup.fieldKey(), 2);
        order.put(WellTable.Column.SampleID.fieldKey(), 3);
        Comparator<PlateCustomField> nameComparator = Comparator.comparing((k) -> k.getName().toLowerCase(), Comparator.nullsLast(String::compareTo));

        fields.sort((f1, f2) -> {
            if (f1.isBuiltIn() && f2.isBuiltIn())
                return order.get(f1.getFieldKey()).compareTo(order.get(f2.getFieldKey()));
            else if (f1.isBuiltIn())
                return -1;
            else if (f2.isBuiltIn())
                return 1;

            return nameComparator.compare(f1, f2);
        });

        return fields;
    }

    public @NotNull List<WellCustomField> getWellCustomFields(User user, Plate plate, Integer wellId)
    {
        Well well = plate.getWell(wellId);
        if (well == null)
            throw new IllegalArgumentException("Failed to get well custom fields. Well id \"" + wellId + "\" not found.");

        Domain domain = getPlateMetadataDomain(plate.getContainer(), user);
        if (domain == null)
            return Collections.emptyList();

        List<WellCustomField> fields = plate.getCustomFields().stream().filter(field -> !field.isBuiltIn()).map(WellCustomField::new).toList();

        if (fields.isEmpty())
            return Collections.emptyList();

        // merge in any well metadata values
        Map<FieldKey, WellCustomField> customFieldMap = new HashMap<>();
        for (WellCustomField customField : fields)
            customFieldMap.put(FieldKey.fromParts(customField.getName()), customField);
        SimpleFilter filter = new SimpleFilter(WellTable.Column.RowId.fieldKey(), wellId);

        TableInfo wellTable = getWellTable(plate.getContainer(), user);
        Map<FieldKey, ColumnInfo> columnMap = QueryService.get().getColumns(wellTable, customFieldMap.keySet());
        try (Results r = QueryService.get().select(wellTable, columnMap.values(), filter, null))
        {
            while (r.next())
            {
                for (Map.Entry<FieldKey, Object> rowEntry : r.getFieldKeyRowMap().entrySet())
                {
                    if (customFieldMap.containsKey(rowEntry.getKey()))
                        customFieldMap.get(rowEntry.getKey()).setValue(rowEntry.getValue());
                }
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        return fields.stream().sorted(Comparator.comparing(PlateCustomField::getName)).toList();
    }

    public List<PlateCustomField> removeFields(Container container, User user, Long plateId, List<PlateCustomField> fields) throws ValidationException
    {
        Plate plate = requirePlate(container, plateId, "Failed to remove plate custom fields.");
        PlateSet plateSet = requirePlateSet(plate, "Failed to remove plate custom fields.");

        Domain domain = getPlateMetadataDomain(container, user);
        if (domain == null)
            throw new IllegalArgumentException("Failed to remove plate custom fields. Custom fields domain does not exist. Try creating fields first.");

        if (fields.isEmpty())
            return getFields(container, plateId);

        List<DomainProperty> metadataFieldsToRemove = new ArrayList<>();
        List<PlateCustomField> builtInFieldsToRemove = new ArrayList<>();
        Set<String> existingProps = plate.getCustomFields().stream().map(PlateCustomField::getName).collect(Collectors.toSet());

        for (PlateCustomField field : fields)
        {
            if (field.isBuiltIn())
            {
                builtInFieldsToRemove.add(field);
            }
            else
            {
                DomainProperty dp = domain.getPropertyByURI(field.getPropertyURI());
                if (dp == null)
                    throw new IllegalArgumentException("Failed to remove plate custom field. \"" + field.getPropertyURI() + "\" does not exist on domain.");

                if (!existingProps.contains(dp.getName()))
                    throw new IllegalArgumentException(String.format("Failed to remove plate custom fields. Custom field \"%s\" is not currently associated with this plate.", dp.getName()));

                metadataFieldsToRemove.add(dp);
            }
        }

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            if (!metadataFieldsToRemove.isEmpty())
            {
                List<String> propertyURIs = metadataFieldsToRemove.stream().map(DomainProperty::getPropertyURI).toList();
                SQLFragment sql = new SQLFragment("DELETE FROM ").append(AssayDbSchema.getInstance().getTableInfoPlateSetProperty(), "")
                        .append(" WHERE PlateSetId = ? ").add(plateSet.getRowId())
                        .append(" AND PropertyURI ").appendInClause(propertyURIs, AssayDbSchema.getInstance().getSchema().getSqlDialect());

                new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql);
            }

            if (!builtInFieldsToRemove.isEmpty())
            {
                List<String> fieldKeys = builtInFieldsToRemove.stream().map(field -> field.getFieldKey().getName()).toList();
                SQLFragment sql = new SQLFragment("DELETE FROM ").append(AssayDbSchema.getInstance().getTableInfoPlateSetProperty(), "")
                        .append(" WHERE PlateSetId = ? ").add(plateSet.getRowId())
                        .append(" AND FieldKey ").appendInClause(fieldKeys, AssayDbSchema.getInstance().getSchema().getSqlDialect());

                new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql);
            }

            transaction.addCommitTask(() -> PlateCache.uncache(container, plateSet), DbScope.CommitTaskOption.POSTCOMMIT);
            transaction.commit();
        }

        return getFields(container, plateId);
    }

    public List<PlateCustomField> setFields(Container container, User user, Long plateRowId, List<PlateCustomField> fields) throws SQLException, ValidationException
    {
        requirePlate(container, plateRowId, "Failed to set plate custom fields.");
        List<PlateCustomField> allFields = getPlateMetadataFields(container, user, true);
        Set<PlateCustomField> currentFields = new HashSet<>(getFields(container, plateRowId));

        Set<PlateCustomField> desiredFields = new HashSet<>();
        List<PlateCustomField> fieldsToAdd = new ArrayList<>();
        List<PlateCustomField> fieldsToRemove = new ArrayList<>();

        for (PlateCustomField partialField : fields)
        {
            Optional<PlateCustomField> opt = allFields.stream().filter(f -> {
                if (f.getName().equals(partialField.getName()))
                    return true;
                return f.getPropertyURI() != null && f.getPropertyURI().equals(partialField.getPropertyURI());
            }).findFirst();

            if (opt.isEmpty())
                throw new IllegalArgumentException("Failed to set plate custom fields. Unable to resolve field with name \"%s\".".formatted(partialField.getName()));

            PlateCustomField field = opt.get();

            desiredFields.add(field);

            if (currentFields.contains(field))
                currentFields.remove(field);
            else
                fieldsToAdd.add(field);
        }

        for (PlateCustomField currentField : currentFields)
        {
            if (!desiredFields.contains(currentField))
                fieldsToRemove.add(currentField);
        }

        if (!fieldsToAdd.isEmpty() || !fieldsToRemove.isEmpty())
        {
            try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
            {
                if (!fieldsToRemove.isEmpty())
                    removeFields(container, user, plateRowId, fieldsToRemove);
                if (!fieldsToAdd.isEmpty())
                    addFields(container, user, plateRowId, fieldsToAdd);
                tx.commit();
            }
        }

        return getFields(container, plateRowId);
    }

    @Override
    public @NotNull String getPlateSetNameExpression()
    {
        return PLATE_SET_NAME_EXPRESSION;
    }

    @Override
    public @NotNull String getPlateNameExpression()
    {
        return PLATE_NAME_EXPRESSION;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlateData(String name, Long plateType, Long templateId, String barcode, List<Map<String, Object>> data) {}

    private List<Plate> addPlatesToPlateSet(
        Container container,
        User user,
        long plateSetId,
        boolean plateSetIsTemplate,
        @NotNull List<PlateData> plates,
        @Nullable String additionalAuditComment
    ) throws Exception
    {
        if (plates.isEmpty())
            return emptyList();

        try (DbScope.Transaction tx = ensureTransaction())
        {
            ensureTransactionAuditId(tx, container, user, QueryService.AuditAction.INSERT);

            pausePlateIndexing();
            tx.addCommitTask(this::resumePlateIndexing, DbScope.CommitTaskOption.POSTCOMMIT, DbScope.CommitTaskOption.POSTROLLBACK);

            List<Plate> platesAdded = new ArrayList<>();

            for (var plate : plates)
            {
                var plateType = requirePlateType(plate.plateType, "Failed to add plates to plate set.");
                var plateImpl = new PlateImpl(container, plate.name, plate.barcode, plateType);
                plateImpl.setTemplate(plateSetIsTemplate);

                // TODO: Write a cheaper plate create/save for multiple plates
                var newPlate = (PlateImpl) createAndSavePlate(container, user, plateImpl, plateSetId, plate.data, true);
                if (plate.templateId != null)
                    newPlate.setSourcePlateRowId(plate.templateId);
                platesAdded.add(newPlate);
            }

            addPlateCreatedAuditEvents(container, user, tx, platesAdded, additionalAuditComment);

            tx.commit();

            return platesAdded;
        }
    }

    public PlateSetImpl createPlateSet(
        Container container,
        User user,
        @NotNull PlateSetImpl plateSet,
        @Nullable List<PlateData> plates,
        @Nullable Long parentPlateSetId,
        @Nullable String additionalAuditComment
    ) throws Exception
    {
        if (!container.hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("Insufficient permissions.");

        if (!plateSet.isNew())
            throw new ValidationException(String.format("Cannot create plate set with rowId (%d).", plateSet.getRowId()));

        if (plates != null && plates.size() > MAX_PLATES)
            throw new ValidationException(String.format("Plate sets can have a maximum of %d plates.", MAX_PLATES));

        PlateSetImpl parentPlateSet = null;
        if (parentPlateSetId != null)
        {
            if (plateSet.isTemplate())
                throw new ValidationException("Template plate sets do not support specifying a parent plate set.");
            parentPlateSet = (PlateSetImpl) getPlateSet(getPlateLookupContainerFilter(container, user), parentPlateSetId);
            if (parentPlateSet == null)
                throw new ValidationException(String.format("Parent plate set with rowId (%d) is not available.", parentPlateSetId));
            if (parentPlateSet.isTemplate())
                throw new ValidationException(String.format("Parent plate set with \"%s\" is a template plate set. Template plate sets are not supported as a parent plate set.", parentPlateSet.getName()));
            if (parentPlateSet.getRootPlateSetId() == null)
                throw new ValidationException(String.format("Parent plate set with rowId (%d) does not have a root plate set specified.", parentPlateSetId));
        }

        if (plateSet.getType() == null)
            plateSet.setType(PlateSetType.assay);

        try (DbScope.Transaction tx = ensureTransaction())
        {
            ensureTransactionAuditId(tx, container, user, QueryService.AuditAction.INSERT);
            BatchValidationException errors = new BatchValidationException();
            QueryUpdateService qus = getPlateSetUpdateService(container, user);

            Map<String, Object> plateSetRow = ObjectFactory.Registry.getFactory(PlateSetImpl.class).toMap(plateSet, new ArrayListMap<>());
            List<Map<String, Object>> rows = qus.insertRows(user, container, Collections.singletonList(plateSetRow), errors, null, null);
            if (errors.hasErrors())
                throw errors;

            Integer plateSetId = asInteger(rows.get(0).get(PlateSetTable.Column.RowId.name()));

            savePlateSetHeritage(plateSetId, plateSet.getType(), parentPlateSet);

            PlateSetImpl newPlateSet = (PlateSetImpl) requirePlateSet(container, plateSetId, "Failed to create plate set.");

            if (plates != null)
                addPlatesToPlateSet(container, user, plateSetId, newPlateSet.isTemplate(), plates, String.format("Added during creation of plate set \"%s\".", newPlateSet.getName()));

            // Set transient parent plate set property for auditing
            if (parentPlateSet != null)
                newPlateSet.setParentPlateSetId(parentPlateSet.getRowId());

            // Audit plate set creation
            {
                // Example comment: "Plate set was created. Created via reformat. Initially contains 5 plates."
                int plateCount = plates == null ? 0 : plates.size();
                String comment = StringUtilsLabKey.joinNonBlank(" ", StringUtils.trimToEmpty(additionalAuditComment), String.format("Initially contains %s.", StringUtilsLabKey.pluralize(plateCount, "plate")));
                PlateSetAuditEvent auditEvent = PlateSetAuditProvider.EventFactory.plateSetCreated(container, tx.getAuditEvent(), newPlateSet, comment);
                AuditLogService.get().addEvent(user, auditEvent);
            }

            tx.addCommitTask(() -> indexPlateSet(newPlateSet), DbScope.CommitTaskOption.POSTCOMMIT);
            tx.commit();

            return newPlateSet;
        }
    }

    public PlateSet createOrAddToPlateSet(Container container, User user, CreatePlateSetOptions options) throws Exception
    {
        if (!container.hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("Insufficient permissions.");

        PlateSetImpl targetPlateSet = getTargetPlateSet(container, options);
        List<PlateManager.PlateData> plates = options.getPlates();
        String selectionKey = options.getSelectionKey();

        if (targetPlateSet.isNew() && options.getParentPlateSetId() != null && selectionKey == null && plates.isEmpty())
        {
            // Re-plate into a new plate set. In this specific configuration we support copying the
            // parent plate set plates into a new plate set.
            return replatePlateSet(container, user, targetPlateSet, options.getParentPlateSetId());
        }

        if (selectionKey != null)
        {
            String selectionKey_ = StringUtils.trimToNull(selectionKey);
            if (selectionKey_ == null)
                throw new ValidationException("Invalid selection key.");

            // Re-array samples onto plates
            plates = reArrayFromSelection(container, user, plates, selectionKey_, options.getOperation());
        }
        else
        {
            // Fully hydrate plate data that may be sourced from a plate template
            plates = preparePlateData(container, user, plates);
        }

        // Create a new plate set
        if (targetPlateSet.isNew())
            return createPlateSet(container, user, targetPlateSet, plates, options.getParentPlateSetId(), null);

        // Update an existing plate set
        addPlatesToPlateSet(container, user, targetPlateSet.getRowId(), targetPlateSet.isTemplate(), plates, null);

        return getPlateSet(container, targetPlateSet.getRowId());
    }

    private PlateSet replatePlateSet(
        Container container,
        User user,
        @NotNull PlateSetImpl targetPlateSet,
        Long sourcePlateSetRowId
    ) throws Exception
    {
        PlateSetImpl parentPlateSet = (PlateSetImpl) requirePlateSet(container, sourcePlateSetRowId, null);
        Long parentId = parentPlateSet.isStandalone() ? null : parentPlateSet.getRowId();

        try (DbScope.Transaction tx = ensureTransaction())
        {
            ensureTransactionAuditId(tx, container, user, QueryService.AuditAction.INSERT);
            PlateSet newPlateSet = createPlateSet(container, user, targetPlateSet, null, parentId, String.format("Re-plated from plate set \"%s\".", parentPlateSet.getName()));

            for (Plate plate : parentPlateSet.getPlates())
                copyPlate(container, user, plate.getRowId(), false, newPlateSet.getRowId(), null, null, true);

            tx.commit();

            return getPlateSet(container, newPlateSet.getRowId());
        }
    }

    private void savePlateSetHeritage(Integer plateSetId, PlateSetType plateSetType, @Nullable PlateSetImpl parentPlateSet)
    {
        assert requireActiveTransaction();

        // Configure rootPlateSetId
        Long rootPlateSetId = null;
        if (PlateSetType.primary.equals(plateSetType))
            rootPlateSetId = parentPlateSet == null ? plateSetId : parentPlateSet.getRootPlateSetId();
        else if (PlateSetType.assay.equals(plateSetType))
            rootPlateSetId = parentPlateSet == null ? null : parentPlateSet.getRootPlateSetId();

        // Configure primaryPlateSetId
        Long primaryPlateSetId = null;
        if (parentPlateSet != null)
        {
            if (parentPlateSet.isPrimary())
                primaryPlateSetId = parentPlateSet.getRowId();
            else if (parentPlateSet.isAssay())
                primaryPlateSetId = parentPlateSet.getPrimaryPlateSetId(); // could be null
        }

        // Add lineage edge relating parent to this plate set
        if (parentPlateSet != null)
            addPlateSetEdges(List.of(new PlateSetEdge(parentPlateSet.getRowId(), plateSetId, parentPlateSet.getRootPlateSetId())));

        if (rootPlateSetId != null || primaryPlateSetId != null)
        {
            SQLFragment sql = new SQLFragment("UPDATE ").append(AssayDbSchema.getInstance().getTableInfoPlateSet()).append(" SET ");
            if (rootPlateSetId != null)
                sql = sql.append("RootPlateSetId = ?").add(rootPlateSetId);
            if (primaryPlateSetId != null)
                sql = sql.append(rootPlateSetId != null ? ", " : "").append("PrimaryPlateSetId = ?").add(primaryPlateSetId);
            sql = sql.append(" WHERE RowId = ?").add(plateSetId);

            new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql);
        }
    }

    private String getArchiveAction(boolean archive)
    {
        return archive ? "archive" : "restore";
    }

    public void archive(Container container, User user, @Nullable List<Long> plateSetIds, @Nullable List<Long> plateIds, boolean archive) throws Exception
    {
        boolean archivingPlates = plateIds != null && !plateIds.isEmpty();
        boolean archivingPlateSets = plateSetIds != null && !plateSetIds.isEmpty();

        if (!archivingPlates && !archivingPlateSets)
            throw new ValidationException(String.format("Failed to %s. Neither plates nor plate sets were specified.", getArchiveAction(archive)));

        try (DbScope.Transaction tx = ensureTransaction())
        {
            ensureTransactionAuditId(tx, container, user, QueryService.AuditAction.UPDATE);

            if (archivingPlates)
            {
                archive(container, user, AssayDbSchema.getInstance().getTableInfoPlate(), "plates", plateIds, archive);
                tx.addCommitTask(() -> clearCache(plateIds), DbScope.CommitTaskOption.POSTCOMMIT);
            }

            if (archivingPlateSets)
            {
                archive(container, user, AssayDbSchema.getInstance().getTableInfoPlateSet(), "plate sets", plateSetIds, archive);
                tx.addCommitTask(() -> clearPlateSetCache(container, plateSetIds), DbScope.CommitTaskOption.POSTCOMMIT);

                List<PlateSetAuditEvent> auditEvents = PlateSetAuditProvider.EventFactory.plateSetsArchived(container, tx.getAuditEvent(), plateSetIds, archive);
                AuditLogService.get().addEvents(user, auditEvents, true);
            }

            tx.commit();
        }
    }

    private void archive(Container container, User user, @NotNull TableInfo table, String type, @NotNull List<Long> rowIds, boolean archive) throws Exception
    {
        Class<? extends Permission> perm = UpdatePermission.class;

        if (!container.hasPermission(user, perm))
            throw new UnauthorizedException(String.format("Failed to %s %s. Insufficient permissions.", getArchiveAction(archive), type));

        if (rowIds.isEmpty())
            throw new ValidationException(String.format("Failed to %s %s. No %s specified.", getArchiveAction(archive), type, type));

        try (DbScope.Transaction tx = ensureTransaction())
        {
            // Ensure user has permission in all containers
            {
                SQLFragment sql = new SQLFragment("SELECT DISTINCT container FROM ")
                        .append(table, "")
                        .append(" WHERE rowId ")
                        .appendInClause(rowIds, table.getSqlDialect());

                for (String containerId : new SqlSelector(table.getSchema(), sql).getCollection(String.class))
                {
                    Container c = ContainerManager.getForId(containerId);
                    if (c != null && !c.hasPermission(user, perm))
                        throw new UnauthorizedException(String.format("Failed to %s %s. Insufficient permissions in %s.", getArchiveAction(archive), type, c.getPath()));
                }
            }

            SQLFragment sql = new SQLFragment("UPDATE ").append(table)
                    .append(" SET archived = ?").add(archive)
                    .append(" WHERE rowId ").appendInClause(rowIds, table.getSqlDialect());

            new SqlExecutor(table.getSchema()).execute(sql);

            tx.commit();
        }
    }

    private void addPlateSetEdges(Collection<PlateSetEdge> edges)
    {
        if (edges == null || edges.isEmpty())
            return;

        List<List<?>> params = new ArrayList<>();

        for (var edge : edges)
        {
            // ignore cycles from and to itself
            if (Objects.equals(edge.getFromPlateSetId(), edge.getToPlateSetId()))
                continue;

            params.add(Arrays.asList(
                edge.getFromPlateSetId(),
                edge.getToPlateSetId(),
                edge.getRootPlateSetId()
            ));
        }

        if (params.isEmpty())
            return;

        try (DbScope.Transaction tx = ensureTransaction())
        {
            String sql = "INSERT INTO " + AssayDbSchema.getInstance().getTableInfoPlateSetEdge() +
                    " (fromPlateSetId, toPlateSetId, rootPlateSetId) " +
                    " VALUES (?, ?, ?) ";

            Table.batchExecute(AssayDbSchema.getInstance().getSchema(), sql, params);
            tx.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    private ContainerFilter ensureContainerFilterForLineage(Container container, User user, @Nullable ContainerFilter cf)
    {
        if (cf != null)
            return cf;

        return QueryService.get().getProductContainerFilterForLookups(container, user, ContainerFilter.Type.Current.create(container, user));
    }

    public PlateSetLineage getPlateSetLineage(Container container, User user, long seedPlateSetId, @Nullable ContainerFilter cf)
    {
        cf = ensureContainerFilterForLineage(container, user, cf);
        PlateSetImpl seedPlateSet = (PlateSetImpl) getPlateSet(cf, seedPlateSetId);
        if (seedPlateSet == null)
            throw new NotFoundException();

        PlateSetLineage lineage = new PlateSetLineage(seedPlateSetId);
        Long rootPlateSetId = seedPlateSet.getRootPlateSetId();

        // stand-alone plate set
        if (rootPlateSetId == null)
        {
            lineage.setPlateSets(Map.of(seedPlateSetId, seedPlateSet));
            return lineage;
        }
        lineage.setRoot(rootPlateSetId);

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RootPlateSetId"), rootPlateSetId);
        List<PlateSetEdge> edges = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateSetEdge(), filter, null).getArrayList(PlateSetEdge.class);
        lineage.setEdges(edges);

        Set<Long> nodeIds = new HashSet<>();
        nodeIds.add(seedPlateSetId);
        nodeIds.add(rootPlateSetId);
        for (var edge : edges)
        {
            nodeIds.add(edge.getFromPlateSetId());
            nodeIds.add(edge.getToPlateSetId());
        }

        UserSchema schema = getPlateUserSchema(container, user);
        TableInfo plateSetTable = schema.getTableOrThrow(PlateSetTable.NAME, cf);
        SimpleFilter filterPS = new SimpleFilter();
        filterPS.addInClause(FieldKey.fromParts("RowId"), nodeIds);
        List<PlateSetImpl> nodes = new TableSelector(plateSetTable, filterPS, null).getArrayList(PlateSetImpl.class);

        Map<Long, PlateSet> plateSets = new LongHashMap<>();
        for (var node : nodes)
            plateSets.put(node.getRowId(), node);
        lineage.setPlateSets(plateSets);

        return lineage;
    }

    private Collection<Long> getResultRowsIds(@Nullable List<Long> resultRowIds, @Nullable String resultSelectionKey)
    {
        if (resultRowIds != null && !resultRowIds.isEmpty())
            return new LongArrayList(new HashSet<>(resultRowIds));
        if (StringUtils.trimToNull(resultSelectionKey) != null)
            return getSelection(resultSelectionKey);

        return emptyList();
    }

    private Collection<Long> getSelection(@NotNull String selectionKey)
    {
        ViewContext viewContext = HttpView.currentContext();
        if (viewContext == null)
            throw new IllegalStateException("Unable to resolve ViewContext to acquire selection key.");

        return DataRegionSelection.getSelectedIntegers(viewContext, selectionKey, false);
    }

    public void markHits(
        Container container,
        User user,
        long protocolId,
        boolean markAsHit,
        @Nullable List<Long> resultRowIds,
        @Nullable String resultSelectionKey
    ) throws SQLException, ValidationException
    {
        boolean hasRowIdResults = resultRowIds != null && !resultRowIds.isEmpty();
        boolean hasSelectionKey = StringUtils.trimToNull(resultSelectionKey) != null;

        if (!hasRowIdResults && !hasSelectionKey)
            throw new ValidationException("Failed to mark hits. You must specify either \"resultRowIds\" or \"resultSelectionKey\".");
        if (hasRowIdResults && hasSelectionKey)
            throw new ValidationException("Failed to mark hits. You can specify either \"resultRowIds\" or \"resultSelectionKey\" but not both.");

        ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolId);
        if (protocol == null)
            throw new ValidationException(String.format("Failed to mark hits. Protocol not found for protocol ID (%d).", protocolId));
        if (!protocol.getContainer().hasPermission(user, ReadPermission.class))
            throw new UnauthorizedException("Failed to mark hits. You do not have permissions to read this assay protocol.");

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
            throw new ValidationException(String.format("Failed to mark hits. Assay provider not found for protocol \"%s\" (%d).", protocol.getName(), protocolId));
        if (!provider.isPlateMetadataEnabled(protocol))
            throw new ValidationException(String.format("Failed to mark hits. Assay \"%s\" does not support plate metadata.", protocol.getName()));

        Collection<Long> rowIds = getResultRowsIds(resultRowIds, resultSelectionKey);
        if (rowIds.isEmpty())
            return;

        try (var tx = ensureTransaction())
        {
            TableInfo hitTable = AssayDbSchema.getInstance().getTableInfoHit();

            if (markAsHit)
            {
                // Validate that none of the selected rows have exclusions
                if (!isOperationPermittedOnResults(container, user, protocol, rowIds, PlateDataStateManager.DataOperation.hitSelection))
                    throw new ValidationException("Failed to mark hits, some of the rows have QC states which prevent the operation.");

                // Exclude preexisting hits
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ResultId"), rowIds, CompareType.IN);
                    filter.addCondition(FieldKey.fromParts("ProtocolId"), protocol.getRowId());

                    Set<Long> preexistingHits = new LongHashSet(new TableSelector(hitTable, Collections.singleton("ResultId"), filter, null).getArrayList(Long.class));
                    rowIds.removeAll(preexistingHits);
                }

                if (!rowIds.isEmpty())
                {
                    ContainerFilter cf = new ContainerFilter.AllInProjectPlusShared(container, user);
                    AssayProtocolSchema schema = provider.createProtocolSchema(user, protocol.getContainer(), protocol, null);
                    TableInfo resultsTable = schema.createDataTable(cf, false);
                    if (resultsTable == null)
                        throw new ValidationException(String.format("Failed to mark hits. Unable to resolve results table for assay \"%s\".", protocol.getName()));

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowIds, CompareType.IN);
                    TableSelector selector = new TableSelector(resultsTable, PageFlowUtil.set("Plate", "RowId", "Run", "WellLsid"), filter, null);

                    List<List<?>> newHits = new LinkedList<>();
                    Map<Integer, Pair<GUID, String>> cache = new IntHashMap<>();
                    for (var row : selector.getMapCollection())
                    {
                        Integer resultId = asInteger(row.get("RowId"));
                        String wellLsid = (String) row.get("WellLsid");
                        if (wellLsid == null)
                            throw new ValidationException(String.format("Failed to mark hits. \"%s\" result (Row Id %d) is not related to a plate well. Only plate well related results can be marked as hits.", protocol.getName(), resultId));

                        Integer plateId = asInteger(row.get("Plate"));
                        if (plateId == null)
                            throw new ValidationException(String.format("Failed to mark hits. \"%s\" result (Row Id %d) is not related to a plate. Only plate related results can be marked as hits.", protocol.getName(), resultId));

                        // locally cache plate/container validations
                        if (!cache.containsKey(plateId))
                        {
                            Plate plate = getPlate(cf, plateId);
                            if (plate == null)
                                throw new ValidationException(String.format("Failed to mark hits. Unable to resolve plate for \"%s\" result (Row Id %d)", protocol.getName(), resultId));
                            if (!plate.getContainer().hasPermission(user, UpdatePermission.class))
                                throw new UnauthorizedException(String.format("Failed to mark hits. You do not have permissions to update hits in %s.", container.getPath()));

                            PlateSetImpl plateSet = (PlateSetImpl) plate.getPlateSet();
                            if (plateSet == null)
                                throw new ValidationException(String.format("Failed to mark hits. Unable to resolve plate set for \"%s\" result (Row Id %d)", protocol.getName(), resultId));

                            PlateSetLineage lineage = getPlateSetLineage(container, user, plateSet.getRowId(), ContainerFilter.getUnsafeEverythingFilter());
                            String plateSetPath = lineage.getSeedPath();

                            cache.put(plateId, Pair.of(plate.getContainer().getEntityId(), plateSetPath));
                        }

                        Pair<GUID, String> parts = cache.get(plateId);
                        newHits.add(List.of(parts.first, protocolId, resultId, row.get("Run"), row.get("WellLsid"), parts.second));
                    }

                    SQLFragment insertSql = new SQLFragment("INSERT INTO ").append(hitTable)
                            .append(" (Container, ProtocolId, ResultId, RunId, WellLsid, PlateSetPath) VALUES (?, ?, ?, ?, ?, ?) ");

                    Table.batchExecute(hitTable.getSchema(), insertSql.getSQL(), newHits);
                }
            }
            else
            {
                deleteHits(protocolId, rowIds);
            }

            tx.commit();
        }
    }

    /**
     * Checks whether the specified data operation is permitted on the existing assay result rows.
     */
    private boolean isOperationPermittedOnResults(Container container, User user, @NotNull ExpProtocol protocol, Collection<Long> rowIds, PlateDataStateManager.DataOperation operation)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        Domain resultDomain = provider.getResultsDomain(protocol);
        DomainProperty stateProp = AssayPlateMetadataServiceImpl.getAssayStateProp(resultDomain);
        if (stateProp != null)
        {
            AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);
            TableInfo resultsTable = schema.createDataTable(null, false);

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), rowIds, CompareType.IN);
            Set<Long> dataStates = new HashSet<>(new TableSelector(resultsTable, Collections.singleton(stateProp.getName()), filter, null).getArrayList(Long.class));
            for (Long state : dataStates)
            {
                DataState dataState = PlateDataStateManager.get().getStateForRowId(container, state);
                if (!PlateDataStateManager.get().isOperationPermitted(dataState, operation))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private void deleteHits(SimpleFilter filter)
    {
        Table.delete(AssayDbSchema.getInstance().getTableInfoHit(), filter);
    }

    public void deleteHits(FieldKey fieldKey, Collection<? extends ExpObject> objects)
    {
        if (objects == null || objects.isEmpty())
            return;

        deleteHits(new SimpleFilter(fieldKey, objects.stream().map(ExpObject::getRowId).toList(), CompareType.IN));
    }

    public void deleteHits(long protocolId, Collection<Long> resultIds)
    {
        if (resultIds == null || resultIds.isEmpty())
            return;

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProtocolId"), protocolId);
        filter.addCondition(FieldKey.fromParts("ResultId"), resultIds, CompareType.IN);
        deleteHits(filter);
    }

    public void deleteHitsForRuns(Collection<Long> runIds)
    {
        if (runIds == null || runIds.isEmpty())
            return;

        deleteHits(new SimpleFilter(FieldKey.fromParts("RunId"), runIds, CompareType.IN));
    }

    private void deleteReplicateStats(ExpProtocol protocol, User user, SimpleFilter filter)
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider != null)
        {
            Domain replicateDomain = AssayPlateMetadataService.get().getPlateReplicateStatsDomain(protocol);
            if (replicateDomain != null)
            {
                DbScope scope = AssayDbSchema.getInstance().getScope();
                SQLFragment sql = new SQLFragment("DELETE FROM ")
                        .append(replicateDomain.getDomainKind().getStorageSchemaName()).append(".")
                        .append(replicateDomain.getStorageTableName()).append(" ")
                        .append(filter.getSQLFragment(scope.getSqlDialect()));

                new SqlExecutor(scope).execute(sql);
            }
       }
    }

    @Override
    public void beforeProtocolsDeleted(Container c, User user, List<? extends ExpProtocol> protocols)
    {
        deleteHits(FieldKey.fromParts("ProtocolId"), protocols);
    }

    @Override
    public void beforeRunDelete(ExpProtocol protocol, ExpRun run, User user)
    {
        deleteHits(FieldKey.fromParts("RunId"), List.of(run));
        deleteReplicateStats(protocol, user, new SimpleFilter(FieldKey.fromParts(PlateReplicateStatsDomainKind.Column.Run.name()), run.getRowId()));
    }

    @Override
    public void beforeResultDelete(Container container, User user, ExpRun run, Map<String, Object> resultRow)
    {
        AssayProvider provider = AssayManager.get().getProvider(run);
        if (provider == null || !provider.isPlateMetadataEnabled(run.getProtocol()))
            return;

        deleteHits(run.getProtocol().getRowId(), List.of(MapUtils.getLong(resultRow,"RowId")));
    }

    /**
     * Returns a PlateSetAssays model for all plate enabled GPAT assays for a given container and containerFilter that
     * have data associated with a given plateSetId or its descendents.
     */
    public PlateSetAssays getPlateSetAssays(Container container, User user, long plateSetId, @Nullable ContainerFilter cf)
    {
        PlateSetAssays plateSetAssays = new PlateSetAssays();
        // Get the list of GPAT protocols in the container
        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);

        if (provider == null)
            return plateSetAssays;

        cf = ensureContainerFilterForLineage(container, user, cf);
        PlateSetLineage lineage = getPlateSetLineage(container, user, plateSetId, cf);
        Map<Long, List<Long>> protocolPlateSets = new LongHashMap<>();
        Map<Long, PlateSet> plateSets = lineage.getPlateSetAndDescendents(plateSetId);
        plateSetAssays.setPlateSets(plateSets);
        TableInfo plateTable = getPlateTable(container, user, cf);
        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(container, provider)
                .stream().filter(provider::isPlateMetadataEnabled).toList();

        for (ExpProtocol protocol : protocols)
        {
            AssayProtocolSchema assayProtocolSchema = provider.createProtocolSchema(user, protocol.getContainer(), protocol, null);
            TableInfo assayDataTable = assayProtocolSchema.createDataTable(ContainerFilter.getUnsafeEverythingFilter(), false);

            if (assayDataTable != null)
            {
                // Query for the distinct set of plate sets that have data in their results domain for the given assay
                SQLFragment sql = new SQLFragment("SELECT DISTINCT pt.plateset FROM ")
                        .append(assayDataTable, "ad")
                        .append(" JOIN ")
                        .append(plateTable, "pt")
                        .append(" ON ad.plate = pt.rowId WHERE pt.plateset ")
                        .appendInClause(plateSets.keySet(), assayDataTable.getSqlDialect());
                ArrayList<Long> plateSetRowIds = new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(Long.class);

                if (!plateSetRowIds.isEmpty())
                    protocolPlateSets.put(protocol.getRowId(), plateSetRowIds);
            }
        }

        plateSetAssays.setProtocolPlateSets(protocolPlateSets);

        return plateSetAssays;
    }

    public void validatePrimaryPlateSetUniqueSamples(Set<Long> wellRowIds, BatchValidationException errors)
    {
        if (wellRowIds.isEmpty())
            return;

        AssayDbSchema dbSchema = AssayDbSchema.getInstance();
        SqlDialect dialect = dbSchema.getSchema().getSqlDialect();
        TableInfo plateTable = dbSchema.getTableInfoPlate();
        TableInfo plateSetTable = dbSchema.getTableInfoPlateSet();
        TableInfo wellTable = dbSchema.getTableInfoWell();

        // Determines the set of primary plate sets that are being touched from the collection of well rowIds
        // From the set of primary plate sets determine if any sample exists in more than one well within the entire plate set
        SQLFragment nonUniqueSamplesPerPrimaryPlateSetSQL = new SQLFragment("WITH PlateSetFilter AS (")
                .append("SELECT DISTINCT PS.RowId FROM ").append(wellTable, "W")
                .append(" INNER JOIN ").append(plateTable, "P").append(" ON P.RowId = W.PlateId")
                .append(" INNER JOIN ").append(plateSetTable, "PS").append(" ON PS.RowId = P.PlateSet")
                .append(" WHERE PS.Type = ?").add("primary").append(" AND W.RowId ").appendInClause(wellRowIds, dialect)
                .append(" )")
                .append(" SELECT PS.Name AS PlateSetName, W.SampleId FROM ").append(wellTable, "W")
                .append(" INNER JOIN ").append(plateTable, "P").append(" ON P.RowId = W.PlateId")
                .append(" INNER JOIN ").append(plateSetTable, "PS").append(" ON PS.RowId = P.PlateSet")
                .append(" INNER JOIN PlateSetFilter PSF ON PSF.RowId = PS.RowId")
                .append(" WHERE W.SampleId IS NOT NULL")
                .append(" GROUP BY PS.RowId, W.SampleId, PS.Name HAVING COUNT(W.SampleId) > 1");

        var duplicates = new SqlSelector(dbSchema.getSchema(), nonUniqueSamplesPerPrimaryPlateSetSQL).getMapCollection();

        if (!duplicates.isEmpty())
        {
            Map<String, Set<Long>> duplicateMap = new HashMap<>();

            for (var duplicate : duplicates)
            {
                var plateSetName = (String) duplicate.get("PlateSetName");
                duplicateMap.computeIfAbsent(plateSetName, (n) -> new LongHashSet()).add(asLong(duplicate.get("SampleId")));
            }

            for (var entry : duplicateMap.entrySet())
            {
                var plateSetName = entry.getKey();
                var sampleIds = entry.getValue();

                ValidationException ve;
                if (sampleIds.size() == 1)
                {
                    var sampleRowId = sampleIds.stream().findFirst().get();
                    var expMaterial = ExperimentService.get().getExpMaterial(sampleRowId);
                    var sampleName = expMaterial == null ? "unknown" : expMaterial.getName();

                    ve = new ValidationException(String.format("Sample \"%s\" is recorded in more than one well in Primary Plate Set \"%s\".", sampleName, plateSetName));
                }
                else
                    ve = new ValidationException(String.format("There are %d samples recorded in more than one well in Primary Plate Set \"%s\".", sampleIds.size(), plateSetName));

                errors.addRowError(ve);
            }
        }
    }

    private boolean requireActiveTransaction()
    {
        return AssayDbSchema.getInstance().getSchema().getScope().isTransactionActive();
    }

    Pair<Integer, List<Map<String, Object>>> getWellSampleData(
        Container c,
        @NotNull List<Long> sampleIds,
        Integer rowCount,
        Integer columnCount,
        int sampleIdsCounter,
        @Nullable ReformatOptions.ReformatOperation operation
    ) throws ValidationException
    {
        if (sampleIds.isEmpty())
            throw new ValidationException("No samples are in the current selection.");

        if (operation == null)
            operation = ReformatOptions.ReformatOperation.arrayByRow;

        Set<ReformatOptions.ReformatOperation> supportedOperations = Set.of(
            ReformatOptions.ReformatOperation.arrayByColumn,
            ReformatOptions.ReformatOperation.arrayByRow
        );
        if (!supportedOperations.contains(operation))
            throw new ValidationException(String.format("The operation \"%s\" is not supported.", operation.name()));

        List<Map<String, Object>> wellSampleDataForPlate = new ArrayList<>();
        boolean iterateByColumn = ReformatOptions.ReformatOperation.arrayByColumn.equals(operation);

        for (int outerIdx = 0; outerIdx < (iterateByColumn ? columnCount : rowCount); outerIdx++)
        {
            for (int innerIdx = 0; innerIdx < (iterateByColumn ? rowCount : columnCount); innerIdx++)
            {
                if (sampleIdsCounter >= sampleIds.size())
                    return Pair.of(sampleIdsCounter, wellSampleDataForPlate);

                int rowIdx = iterateByColumn ? innerIdx : outerIdx;
                int colIdx = iterateByColumn ? outerIdx : innerIdx;

                wellSampleDataForPlate.add(CaseInsensitiveHashMap.of(
                    WellTable.Column.SampleID.name(), sampleIds.get(sampleIdsCounter),
                    WellTable.Column.Type.name(), SAMPLE.name(),
                    WELL_LOCATION, createPosition(c, rowIdx, colIdx).getDescription()
                ));
                sampleIdsCounter++;
            }
        }

        return Pair.of(sampleIdsCounter, wellSampleDataForPlate);
    }

    /** Prepares the plate data for plates that specify a "templateId". */
    private List<PlateData> preparePlateData(Container container, User user, Collection<PlateData> plates)
    {
        if (plates == null || plates.isEmpty())
            return emptyList();

        List<PlateData> plateData = new ArrayList<>();

        for (PlateData plate : plates)
        {
            if (plate.templateId == null)
                plateData.add(plate);
            else
            {
                List<Map<String, Object>> data = getWellData(container, user, plate.templateId, false, true)
                        .stream()
                        .map(WellData::getData)
                        .toList();

                plateData.add(new PlateData(plate.name, plate.plateType, plate.templateId, null, data));
            }
        }

        return plateData;
    }

    /** Prepares the plate data for a plate template created from a plate type. */
    public List<Map<String, Object>> prepareEmptyPlateTemplateData(Container container, @NotNull PlateType plateType)
    {
        List<Map<String, Object>> data = new ArrayList<>();

        for (int rowIdx = 0; rowIdx < plateType.getRows(); rowIdx++)
        {
            for (int colIdx = 0; colIdx < plateType.getColumns(); colIdx++)
            {
                data.add(CaseInsensitiveHashMap.of(
                    WellTable.Column.Type.name(), SAMPLE.name(),
                    WELL_LOCATION, createPosition(container, rowIdx, colIdx).getDescription()
                ));
            }
        }

        return data;
    }

    /**
     * This is a re-array operation, so take the plate sources and apply the selected samples
     * according to each plate's layout.
     */
    private List<PlateData> reArrayFromSelection(
        Container container,
        User user,
        List<PlateData> plates,
        @NotNull String selectionKey,
        @Nullable ReformatOptions.ReformatOperation operation
    ) throws ValidationException
    {
        if (plates.isEmpty())
            throw new ValidationException("Failed to generate plate data. No plates specified.");

        List<Long> selectedSampleIds = getSelection(selectionKey).stream().sorted().toList();
        if (selectedSampleIds.isEmpty())
            throw new ValidationException("Failed to generate plate data. No samples selected.");

        int sampleIdsCounter = 0;
        List<PlateData> platesData = new ArrayList<>();
        Map<Long, PlateType> plateTypes = new LongHashMap<>();
        Map<Pair<WellGroup.Type, String>, Long> groupSampleMap = new HashMap<>();

        for (PlateData plate : plates)
        {
            long plateTypeId = plate.plateType;
            if (!plateTypes.containsKey(plateTypeId))
                plateTypes.put(plateTypeId, requirePlateType(plateTypeId, "Failed to generate plate data."));
            PlateType plateType = plateTypes.get(plateTypeId);

            if (plate.templateId != null)
            {
                // Generate well data from a source plate
                List<WellData> wellData = getWellData(container, user, plate.templateId, false, true);

                // Plate the samples into the well data
                sampleIdsCounter = plateSamples(wellData, selectedSampleIds, groupSampleMap, sampleIdsCounter);

                // Hydrate a CreatePlateSetPlate and add it to plate data
                List<Map<String, Object>> data = wellData.stream().map(WellData::getData).toList();
                platesData.add(new PlateData(plate.name, plateType.getRowId(), plate.templateId, null, data));
            }
            else
            {
                // Iterate through sorted samples array and place them in ascending order in each plate's wells
                Pair<Integer, List<Map<String, Object>>> pair;
                pair = getWellSampleData(container, selectedSampleIds, plateType.getRows(), plateType.getColumns(), sampleIdsCounter, operation);
                platesData.add(new PlateData(plate.name, plateType.getRowId(), null, null, pair.second));
                sampleIdsCounter = pair.first;
            }
        }

        if (selectedSampleIds.size() != sampleIdsCounter)
            throw new ValidationException("Failed to generate plate data. Plate dimensions are incompatible with selected sample count.");

        return platesData;
    }

    private int plateSamples(
        List<WellData> wellDataList,
        List<Long> sampleIds,
        Map<Pair<WellGroup.Type, String>, Long> groupSampleMap,
        int counter
    )
    {
        for (WellData wellData : wellDataList)
        {
            boolean isSampleOrReplicate = wellData.isSampleOrReplicate();
            Pair<WellGroup.Type, String> groupKey = wellData.getGroupKey();

            if (counter >= sampleIds.size())
            {
                // Fill remaining group wells
                if (isSampleOrReplicate && groupKey != null && groupSampleMap.containsKey(groupKey))
                {
                    wellData.setSampleId(groupSampleMap.get(groupKey));
                }
            }
            else if (isSampleOrReplicate)
            {
                Long sampleId = sampleIds.get(counter);

                if (groupKey != null)
                {
                    if (groupSampleMap.containsKey(groupKey))
                    {
                        // Do not increment counter as this reuses the same sample within a group
                        sampleId = groupSampleMap.get(groupKey);
                    }
                    else
                    {
                        groupSampleMap.put(groupKey, sampleId);
                        counter++;
                    }
                }
                else
                {
                    counter++;
                }

                wellData.setSampleId(sampleId);
            }
        }

        return counter;
    }

    public void getPlateSetExportFile(String fileName, List<ColumnDescriptor> cols, List<Object[]> rows, PlateController.FileType fileType, HttpServletResponse response) throws IOException
    {
        boolean isCSV = PlateController.FileType.CSV.equals(fileType);
        boolean isTSV = PlateController.FileType.TSV.equals(fileType);
        if (isCSV || isTSV)
        {
            try (TSVArrayWriter writer = new TSVArrayWriter(fileName, cols, rows))
            {
                writer.setDelimiterCharacter(isCSV ? TSVWriter.DELIM.COMMA : TSVWriter.DELIM.TAB);
                writer.write(response);
            }
        }
        else
        {
            ArrayExcelWriter xlWriter = new ArrayExcelWriter(rows, cols);
            xlWriter.setFullFileName(fileName + ".xlsx");
            xlWriter.renderWorkbook(response);
        }
    }

    public List<Object[]> getWorklist(
        long sourcePlateSetId,
        long destinationPlateSetId,
        List<FieldKey> sourceIncludedMetadataCols,
        List<FieldKey> destinationIncludedMetadataCols,
        Container c,
        User u
    ) throws RuntimeSQLException
    {
        TableInfo wellTable = getWellTable(c, u);
        return new PlateSetExport().getWorklist(wellTable, sourcePlateSetId, destinationPlateSetId, sourceIncludedMetadataCols, destinationIncludedMetadataCols);
    }

    public List<Object[]> getInstrumentInstructions(long plateSetId, List<FieldKey> includedMetadataCols, Container c, User u)
    {
        TableInfo wellTable = getWellTable(c, u);
        return new PlateSetExport().getInstrumentInstructions(wellTable, plateSetId, includedMetadataCols);
    }

    private List<FieldKey> getPlateExportFieldKeys(Plate plate, boolean isMapView)
    {
        List<FieldKey> fieldKeys = new ArrayList<>();
        fieldKeys.add(FieldKey.fromParts(WellTable.Column.SampleID.name(), "Name"));

        if (isMapView)
        {
            fieldKeys.add(WellTable.Column.Row.fieldKey());
            fieldKeys.add(WellTable.Column.Col.fieldKey());
        }
        else
        {
            // For non-map export view we always want "position" first
            fieldKeys.add(0, WellTable.Column.Position.fieldKey());
        }

        List<PlateCustomField> customFields = plate.getCustomFields();

        if (isMapView)
        {
            Set<FieldKey> excludedColumns = Set.of(
                WellTable.Column.SampleID.fieldKey(),
                WellTable.Column.Type.fieldKey(),
                WellTable.Column.WellGroup.fieldKey(),
                WellTable.Column.ReplicateGroup.fieldKey()
            );

            customFields = customFields.stream().filter(field -> field.getFieldKey() == null || !excludedColumns.contains(field.getFieldKey())).toList();
        }

        for (PlateCustomField customField : customFields)
            fieldKeys.add(FieldKey.fromParts(customField.getName()));

        return fieldKeys;
    }

    private QueryView getPlateQueryView(Container container, User user, ContainerFilter cf, Plate plate, boolean isMapView)
    {
        UserSchema userSchema = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME);
        List<FieldKey> fieldKeys = getPlateExportFieldKeys(plate, isMapView);
        ViewContext viewContext = new ViewContext();
        viewContext.setUser(user);
        QuerySettings settings = new QuerySettings(viewContext, plate.getName());
        settings.setFieldKeys(fieldKeys);
        settings.setContainerFilterName(cf.getType().name());
        settings.setSchemaName(userSchema.getSchemaName());
        settings.setQueryName(WellTable.NAME);
        settings.getBaseFilter().addCondition(WellTable.Column.PlateId.fieldKey(), plate.getRowId());
        return new QueryView(userSchema, settings, null);
    }

    private List<DisplayColumn> getPlateDisplayColumns(QueryView queryView)
    {
        // We have to use the display columns from the DataRegion returned from createDataView in order to get the
        // correct columns that we set via QuerySettings in getPlateQueryView, if we don't then we'll only get the
        // columns from the default view of the Well table, which could be anything.
        DataRegion dataRegion = queryView.createDataView().getDataRegion();

        // Filter on isQueryColumn, so we don't get the details or update columns
        return dataRegion.getDisplayColumns().stream()
                .filter(DisplayColumn::isQueryColumn)
                .filter(col -> !col.getName().equalsIgnoreCase(WellTable.Column.SampleID.name()))
                .toList();
    }

    public record PlateFileBytes(String plateName, ByteArrayOutputStream bytes) {}

    public List<PlateFileBytes> exportPlateData(Container c, User user, ContainerFilter cf, List<Integer> plateIds, TSVWriter.DELIM delim) throws Exception
    {
        if (plateIds.isEmpty()) return emptyList();

        List<PlateFileBytes> fileBytes = new ArrayList<>();

        for (Integer plateId : plateIds)
        {
            Plate plate = getPlate(cf, plateId);
            if (plate != null)
            {
                QueryView plateQueryView = getPlateQueryView(c, user, cf, plate, false);
                List<DisplayColumn> displayColumns = getPlateDisplayColumns(plateQueryView);
                PlateFileBytes plateFileBytes = new PlateFileBytes(plate.getName(), new ByteArrayOutputStream());
                FieldKey sampleIdNameFieldKey = FieldKey.fromParts(WellTable.Column.SampleID.name(), "Name");

                try (TSVGridWriter writer = new TSVGridWriter(plateQueryView::getResults, displayColumns, Collections.singletonMap(sampleIdNameFieldKey.toString(), "Sample ID")))
                {
                    writer.setDelimiterCharacter(delim);
                    writer.setColumnHeaderType(ColumnHeaderType.ImportField); // Issue 53431
                    writer.write(plateFileBytes.bytes);
                }

                fileBytes.add(plateFileBytes);
            }
        }

        return fileBytes;
    }

    public List<PlateFileBytes> exportPlateMaps(Container c, User user, ContainerFilter cf, List<Integer> plateIds) throws Exception
    {
        if (plateIds.isEmpty()) return emptyList();

        List<PlateFileBytes> fileBytes = new ArrayList<>();

        for (Integer plateId : plateIds)
        {
            Plate plate = getPlate(cf, plateId);
            if (plate != null)
            {
                QueryView plateQueryView = getPlateQueryView(c, user, cf, plate, true);
                List<DisplayColumn> displayColumns = getPlateDisplayColumns(plateQueryView);
                PlateFileBytes plateFileBytes = new PlateFileBytes(plate.getName(), new ByteArrayOutputStream());
                PlateMapExcelWriter writer = new PlateMapExcelWriter(plate, displayColumns, plateQueryView);
                writer.renderWorkbook(plateFileBytes.bytes);
                fileBytes.add(plateFileBytes);
            }
        }

        return fileBytes;
    }

    public List<WellData> getWellData(Container container, User user, long plateRowId, boolean includeSamples, boolean includeMetadata)
    {
        Set<String> columns = new HashSet<>();
        columns.add(WellTable.Column.Col.name());
        columns.add(WellTable.Column.Lsid.name());
        columns.add(WellTable.Column.Position.name());
        columns.add(WellTable.Column.Row.name());
        columns.add(WellTable.Column.RowId.name());
        columns.add(WellTable.Column.Type.name());
        columns.add(WellTable.Column.WellGroup.name());
        columns.add(WellTable.Column.ReplicateGroup.name());
        if (includeSamples)
            columns.add(WellTable.Column.SampleID.name());

        var wellTable = getWellTable(container, user, getPlateLookupContainerFilter(container, user));
        var filter = new SimpleFilter(WellTable.Column.PlateId.fieldKey(), plateRowId);
        var wellDatas = new TableSelector(wellTable, columns, filter, new Sort(WellTable.Column.RowId.name())).getArrayList(WellData.class);

        if (includeMetadata)
            return getWellMetadata(container, user, wellDatas);
        return wellDatas;
    }

    private List<WellData> getWellMetadata(Container container, User user, List<WellData> wellDataList)
    {
        List<String> wellLsids = wellDataList.stream().map(WellData::getLsid).toList();
        if (wellLsids.isEmpty())
            return wellDataList;

        var metadataTable = getPlateMetadataTable(container, user);
        if (metadataTable == null)
            return wellDataList;

        var filter = new SimpleFilter(WellTable.Column.Lsid.fieldKey(), wellLsids, CompareType.IN);
        var metadataMap = new HashMap<String, Map<String, Object>>();
        var ignoredKeys = CaseInsensitiveHashSet.of("_row", WellTable.Column.Lsid.name());

        try (Results results = new TableSelector(metadataTable, filter, null).getResults())
        {
            Map<FieldKey, ColumnInfo> fieldMap = results.getFieldMap();

            while (results.next())
            {
                var row = results.getFieldKeyRowMap();
                var metadata = new CaseInsensitiveHashMap<>();

                row.forEach((key, value) -> {
                    if (value != null)
                    {
                        // Issue 53017: usages of getWellData are expecting the WellData metadata map to be keyed
                        // by column names (see savePlateImpl wellQus.insertRows() which requires rows to be keyed
                        // by column names)
                        String colName = fieldMap.get(key).getName();
                        if (!ignoredKeys.contains(colName))
                            metadata.put(colName, value);
                    }
                });

                if (!metadata.isEmpty())
                {
                    var lsid = (String) row.get(WellTable.Column.Lsid.fieldKey());
                    metadataMap.put(lsid, metadata);
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        if (!metadataMap.isEmpty())
        {
            for (var wellData : wellDataList)
            {
                var metadata = metadataMap.get(wellData.getLsid());
                if (metadata != null)
                    wellData.setMetadata(metadata);
            }
        }

        return wellDataList;
    }

    public record WellGroupChange(Long plateRowId, Long wellRowId, String type, String group, String replicateGroup) {}

    /**
     * Computes the well groups based on changes (updates) made to the well "Type", "WellGroup", and "ReplicateGroup".
     * This is invoked whenever rows are inserted or updated in the assay.Well table.
     */
    public void computeWellGroups(
        Container container,
        User user,
        Map<Long, Map<Long, WellGroupChange>> wellGroupChanges
    ) throws ValidationException
    {
        assert requireActiveTransaction();

        if (wellGroupChanges.isEmpty())
            return;

        for (var entry : wellGroupChanges.entrySet())
        {
            var plate = (PlateImpl) requirePlate(container, entry.getKey(), "Failed to compute well group.");
            if (!TsvPlateLayoutHandler.TYPE.equalsIgnoreCase(plate.getAssayType()))
                continue;

            var wellChanges = entry.getValue();
            Map<Pair<WellGroup.Type, String>, List<Position>> wellGroupings = new HashMap<>();

            for (var wellData : getWellData(container, user, plate.getRowId(), false, false))
            {
                WellGroup.Type type = wellData.getType();
                String wellGroup = wellData.getWellGroup();
                String replicateGroup = wellData.getReplicateGroup();

                Long wellRowId = wellData.getRowId();
                var wellChange = wellChanges.get(wellRowId);
                if (wellChange != null)
                {
                    if (wellChange.type != null)
                    {
                        String typeStr = StringUtils.trimToNull(wellChange.type);
                        if (typeStr != null)
                            type = WellGroup.Type.valueOf(typeStr);
                        else
                            type = null;
                    }
                    if (wellChange.group != null)
                        wellGroup = StringUtils.trimToNull(wellChange.group);
                    if (wellChange.replicateGroup != null)
                        replicateGroup = StringUtils.trimToNull(wellChange.replicateGroup);
                }

                // Type/Group/ReplicateGroup are not set and are not being updated
                if (type == null && wellGroup == null && replicateGroup == null)
                    continue;

                var position = plate.getPosition(wellData.getRow(), wellData.getCol());

                // Specifying a group or replicate group requires that a type is also specified
                if (type == null)
                {
                    throw new ValidationException(String.format(
                        "Well %s must specify a \"%s\" when a \"%s\" is specified.",
                        position.getDescription(),
                        WellTable.Column.Type.name(),
                        wellGroup != null ? WellTable.Column.WellGroup.name() : WellTable.Column.ReplicateGroup.name()
                    ));
                }

                if (WellGroup.Type.REPLICATE.equals(type))
                {
                    throw new ValidationException(String.format(
                        "Type \"%s\" is not supported for well %s. Specify a \"ReplicateGroup\" instead.",
                        WellGroup.Type.REPLICATE.getLabel(),
                        position.getDescription()
                    ));
                }

                var wellGroupKey = Pair.of(type, wellGroup);
                wellGroupings.computeIfAbsent(wellGroupKey, k -> new ArrayList<>()).add(position);

                if (replicateGroup != null)
                {
                    var replicateGroupKey = Pair.of(WellGroup.Type.REPLICATE, replicateGroup);
                    wellGroupings.computeIfAbsent(replicateGroupKey, k -> new ArrayList<>()).add(position);
                }
            }

            // Mark pre-existing well groups on this plate for deletion
            for (WellGroup existingWellGroup : plate.getWellGroups())
                plate.markWellGroupForDeletion(existingWellGroup);

            // Create new well groups for this plate
            for (var wellGrouping : wellGroupings.entrySet())
            {
                var typeGroup = wellGrouping.getKey();
                plate.addWellGroup(typeGroup.second, typeGroup.first, wellGrouping.getValue());
            }

            try
            {
                savePlateImpl(container, user, plate);
            }
            catch (Exception e)
            {
                throw UnexpectedException.wrap(e);
            }
        }
    }

    public void validateWellGroups(Container container, User user, Collection<Long> plateRowIds) throws ValidationException
    {
        clearCache(plateRowIds);
        Set<Long> plateSetsWithSampleGroups = new HashSet<>();
        Set<Long> plateSetsWithReplicateGroups = new HashSet<>();
        Set<Pair<Long, Long>> plateSetsWithControls = new HashSet<>();

        for (var plateRowId : plateRowIds)
        {
            var plate = requirePlate(container, plateRowId, "Failed to validate well groups.");
            if (!TsvPlateLayoutHandler.TYPE.equalsIgnoreCase(plate.getAssayType()))
                continue;

            var plateSet = plate.getPlateSet();
            if (plateSet == null)
                throw new ValidationException("Failed to resolve plate set for plate \"%s\".", plate.getName());

            for (var wellGroup : plate.getWellGroups())
            {
                switch (wellGroup.getType())
                {
                    case REPLICATE ->
                    {
                        if (wellGroup.isZone())
                            throw new ValidationException(String.format("Replicates must specify a \"%s\".", WellTable.Column.ReplicateGroup.name()));

                        plateSetsWithReplicateGroups.add(plateSet.getRowId());
                    }
                    case CONTROL, NEGATIVE_CONTROL, POSITIVE_CONTROL, SAMPLE ->
                    {
                        validateWellGroup(plate, wellGroup);

                        if (plateSet.isTemplate())
                            continue;

                        if (!wellGroup.isZone())
                            plateSetsWithSampleGroups.add(plateSet.getRowId());

                        if (!SAMPLE.equals(wellGroup.getType()) && !plateSet.isStandalone() && !plateSet.getRowId().equals(plateSet.getRootPlateSetId()))
                        {
                            if (plateSet.getRootPlateSetId() != null)
                                plateSetsWithControls.add(Pair.of(plateSet.getRowId(), plateSet.getRootPlateSetId()));
                        }
                    }
                    default -> throw new ValidationException(
                        String.format(
                            "Well Group Type \"%s\" is not supported for assay type \"%s\" plates.",
                            wellGroup.getType(),
                            TsvPlateLayoutHandler.TYPE
                        )
                    );
                }
            }

            validateWells(plate);
        }

        if (!plateSetsWithReplicateGroups.isEmpty())
        {
            for (var plateSetId : plateSetsWithReplicateGroups.stream().sorted().toList())
                validatePlateSetReplicateGroups(container, user, plateSetId);
        }

        if (!plateSetsWithSampleGroups.isEmpty())
        {
            for (var plateSetId : plateSetsWithSampleGroups.stream().sorted().toList())
                validatePlateSetSampleGroups(container, user, plateSetId);
        }

        if (!plateSetsWithControls.isEmpty())
        {
            for (var plateSetIds : plateSetsWithControls.stream().sorted().toList())
                validatePlateSetControls(container, user, plateSetIds);
        }
    }

    private void validateWells(Plate plate) throws ValidationException
    {
        for (var well : plate.getWells())
        {
            var position = plate.getPosition(well.getRow(), well.getColumn());
            var wellGroups = plate.getWellGroups(position);

            if (wellGroups.isEmpty() && well.getSampleId() != null)
            {
                throw new ValidationException(String.format(
                    "Well %s must specify a \"%s\" when a \"%s\" is specified on plate \"%s\".",
                    position.getDescription(),
                    WellTable.Column.Type.name(),
                    WellTable.Column.SampleID.name(),
                    plate.getName()
                ));
            }

            // TODO: Can perform more precise checks here
            if (wellGroups.size() > 2)
            {
                throw new ValidationException(String.format(
                    "Well %s is included in more than two well groups. This is not supported for assay type \"%s\" plate \"%s\".",
                    position.getDescription(),
                    TsvPlateLayoutHandler.TYPE,
                    plate.getName()
                ));
            }
        }
    }

    private String getControlGroupLabKeySql(Pair<Long, Long> plateSetRowIds)
    {
        String controlTypes = StringUtils.join(
                Stream.of(WellGroup.Type.CONTROL, WellGroup.Type.NEGATIVE_CONTROL, WellGroup.Type.POSITIVE_CONTROL)
                        .map(type -> LabKeySql.quoteString(type.name())).toList(), ", "
        );

        return String.format("""
            SELECT
                SIPS.Name
            FROM
                plate.SamplesInPlateSets AS SIPS
            WHERE
                SIPS.PlateSetRowId = %s AND
                SIPS.RowId IN (
                    SELECT DISTINCT SampleId FROM plate.Well
                    WHERE PlateId.PlateSet = %s AND Type IN (%s)
                )
            LIMIT 1
        """, plateSetRowIds.second, plateSetRowIds.first, controlTypes);
    }

    private void validatePlateSetControls(Container container, User user, Pair<Long, Long> plateSetRowIds) throws ValidationException
    {
        String invalidSampleName = null;
        UserSchema schema = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME);
        String sql = getControlGroupLabKeySql(plateSetRowIds);

        try (Results rs = QueryService.get().getSelectBuilder(schema, sql).select())
        {
            if (rs.next())
                invalidSampleName = rs.getString(FieldKey.fromParts("name"));
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        if (invalidSampleName != null)
            throw new ValidationException(String.format("The sample \"%s\" is not a valid control.", invalidSampleName));
    }

    private long getReplicateGroupCount(@NotNull UserSchema plateSchema, @NotNull Long plateSetRowId)
    {
        String labkeySql = String.format("""
            SELECT DISTINCT WellGroup, ReplicateGroup
            FROM plate.Well WHERE PlateId.PlateSet.RowId = %s AND ReplicateGroup IS NOT NULL
        """, plateSetRowId);

        return QueryService.get().getSelectBuilder(plateSchema, labkeySql).buildSqlSelector(null).getRowCount();
    }

    private String getReplicateGroupLabKeySql(@NotNull UserSchema plateSchema, @NotNull Long plateSetRowId)
    {
        var wellTable = plateSchema.getTableOrThrow(WellTable.NAME);
        var columnNames = new CaseInsensitiveHashSet(wellTable.getColumnNameSet());
        var excludedColumns = CaseInsensitiveHashSet.of(
            WellTable.Column.Col.name(),
            WellTable.Column.Container.name(),
            WellTable.Column.Lsid.name(),
            WellTable.Column.PlateId.name(),
            WellTable.Column.Position.name(),
            WellTable.Column.Row.name(),
            WellTable.Column.RowId.name()
        );

        columnNames.removeAll(excludedColumns);

        StringBuilder columnsSql = new StringBuilder();
        {
            var separator = "";
            for (String columnName : columnNames)
            {
                columnsSql.append(separator).append(LabKeySql.quoteIdentifier(columnName)).append("\n");
                separator = ", ";
            }
        }

        return String.format("""
            SELECT
            %s
            FROM plate.Well
            WHERE PlateId.PlateSet.RowId = %s AND ReplicateGroup IS NOT NULL
            GROUP BY
            %s
        """, columnsSql, plateSetRowId, columnsSql);
    }

    private void validatePlateSetReplicateGroups(Container container, User user, @NotNull Long plateSetRowId) throws ValidationException
    {
        var plateSchema = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME);
        var replicateWellGroupCount = getReplicateGroupCount(plateSchema, plateSetRowId);

        if (replicateWellGroupCount == 0)
            return;

        var sql = getReplicateGroupLabKeySql(plateSchema, plateSetRowId);
        try (var results = QueryService.get().getSelectBuilder(plateSchema, sql).select())
        {
            if (replicateWellGroupCount == results.getSize())
                return;

            // Now we know that there are mismatched replicate rows within a group. Find the first mismatched group.
            Set<String> groups = new HashSet<>();
            while (results.next())
            {
                String groupName = StringUtils.trimToNull(results.getString(WellTable.Column.ReplicateGroup.name()));
                if (groupName == null)
                    continue;

                if (groups.contains(groupName))
                    throw new ValidationException(String.format("Replicate group \"%s\" contains mismatched well data. Ensure the same data is recorded for each well in this replicate group across all plates in the plate set.", groupName));

                groups.add(groupName);
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        // Fallback to a more generic message if we did not resolve a specific mismatch
        throw new ValidationException(String.format("Plate set (%d) contains mismatched replicate well data.", plateSetRowId));
    }

    private String getSampleGroupLabKeySql(@NotNull Long plateSetRowId, boolean includeSampleId)
    {
        List<String> columnNames = new ArrayList<>();
        columnNames.add(WellTable.Column.Type.name());
        columnNames.add(WellTable.Column.WellGroup.name());
        if (includeSampleId)
            columnNames.add(WellTable.Column.SampleID.name());
        String columns = columnNames.stream().map(LabKeySql::quoteIdentifier).collect(Collectors.joining(", "));

        String wellTypes = StringUtils.join(
                Stream.of(WellGroup.Type.CONTROL, WellGroup.Type.NEGATIVE_CONTROL, WellGroup.Type.POSITIVE_CONTROL, WellGroup.Type.SAMPLE)
                        .map(type -> LabKeySql.quoteString(type.name())).toList(), ", "
        );

        return String.format("""
            SELECT DISTINCT %s
            FROM plate.Well
            WHERE PlateId.PlateSet.RowId = %s AND WellGroup IS NOT NULL AND Type IN (%s) AND SampleID IS NOT NULL
            GROUP BY %s
        """, columns, plateSetRowId, wellTypes, columns);
    }

    private long getSampleGroupCount(@NotNull UserSchema plateSchema, @NotNull Long plateSetRowId)
    {
        String labkeySql = getSampleGroupLabKeySql(plateSetRowId, false);
        return QueryService.get().getSelectBuilder(plateSchema, labkeySql).buildSqlSelector(null).getRowCount();
    }

    private void validatePlateSetSampleGroups(Container container, User user, @NotNull Long plateSetRowId) throws ValidationException
    {
        var plateSchema = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME);
        var sampleGroupCount = getSampleGroupCount(plateSchema, plateSetRowId);

        if (sampleGroupCount == 0)
            return;

        var sampleGroupLabKeySql = getSampleGroupLabKeySql(plateSetRowId, true);
        try (var results = QueryService.get().getSelectBuilder(plateSchema, sampleGroupLabKeySql).select())
        {
            if (sampleGroupCount == results.getSize())
                return;

            // Now we know that there are mismatched samples within a sample group. Find the first mismatched group.
            Set<Pair<String, String>> groups = new HashSet<>();
            while (results.next())
            {
                String groupName = StringUtils.trimToNull(results.getString(WellTable.Column.WellGroup.name()));
                if (groupName == null)
                    continue;

                String type = StringUtils.trimToNull(results.getString(WellTable.Column.Type.name()));
                if (type == null)
                    continue;

                var key = Pair.of(groupName, type);
                if (groups.contains(key))
                    throw new ValidationException(String.format("Sample group \"%s\" contains mismatched samples across plates. Ensure the same sample is recorded for each well in this sample group across all plates in the plate set.", groupName));

                groups.add(key);
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    private void validateWellGroup(Plate plate, WellGroup wellGroup) throws ValidationException
    {
        if (wellGroup.isZone())
            return;

        // TODO: Handle the warning "Attempt to update table 'Well' with no valid fields." when only editing type.

        Long sampleId = null;
        for (var position : wellGroup.getPositions())
        {
            var well = plate.getWell(position.getRow(), position.getColumn());
            if (well.getSampleId() != null)
            {
                if (sampleId == null)
                    sampleId = well.getSampleId();
                else if (!well.getSampleId().equals(sampleId))
                {
                    throw new ValidationException(
                        String.format(
                            "Group \"%s\" refers to multiple samples. Choose the same sample for all wells in this group.",
                            wellGroup.getName()
                        )
                    );
                }
            }
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PreviewPlateData(
        String name,
        Long plateType,
        Long templateId,
        String barcode,
        List<Map<String, Object>> data,
        Long plateRowId,
        Integer wellCount,
        Integer wellsEmpty,
        Integer wellsFilled,
        Integer sampleCount,
        Integer samplesAdded
    ) {
        static PreviewPlateData create(
            PlateData plateData,
            Long plateRowId,
            Integer wellCount,
            Integer wellsEmpty,
            Integer wellsFilled,
            Integer sampleCount,
            Integer samplesAdded
        )
        {
            return new PreviewPlateData(plateData.name, plateData.plateType, plateData.templateId, plateData.barcode, plateData.data, plateRowId, wellCount, wellsEmpty, wellsFilled, sampleCount, samplesAdded);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReformatResult(
        List<PreviewPlateData> previewData,
        Integer plateCountCreated,
        Integer plateCountUpdated,
        Long plateSetRowId,
        String plateSetName,
        List<Long> plateRowIds,
        Integer platedSampleCount,
        Integer selectedSampleCount
    ) {}

    /**
     * Reformat a set of source plates to new plates via a reformat operation (e.g. quadrant, stamp, etc.).
     * @return A ReformatResult which will contain different data when previewing versus saving (not previewing).
     * - preview:
     *      The previewData contains the preview data. Null if the "previewData" flag is false.
     *      The plateCountCreated is the number of plates that will be created.
     *      The plateCountUpdated is the number of plates that will be updated.
     *      The platedSampleCount is the number of samples that will be plated for re-array operations.
     *      The plateSetRowId, plateSetName and plateRowIds will be null.
     * - saving (not preview):
     *      The previewData is null.
     *      The plateCountCreated is the number of plates that have been created.
     *      The plateCountUpdated is the number of plates that have been updated.
     *      The platedSampleCount is the number of samples that have been plated for re-array operations.
     *      The plateSetRowId is the rowId of the target plate set.
     *      The plateSetName is the name of the target plate set.
     *      The plateRowIds are the rowIds of all newly generated plates.
     */
    public @NotNull ReformatResult reformat(Container container, User user, ReformatOptions options) throws Exception
    {
        if (!container.hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("Insufficient permissions.");

        if (options == null)
            throw new ValidationException("Reformat options are required.");
        if (options.getOperation() == null)
            throw new ValidationException("An \"operation\" must be specified.");

        // Initialize / validate engine configuration
        List<? extends PlateType> allPlateTypes = getPlateTypes();
        LayoutEngine engine = new LayoutEngine(options, allPlateTypes);

        PlateSetImpl targetPlateSet = getReformatTargetPlateSet(container, options);
        Pair<PlateSet, List<Plate>> source = getReformatSourcePlates(container, options, engine.getOperation());
        PlateSetImpl sourcePlateSet = (PlateSetImpl) source.first;
        List<Plate> sourcePlates = source.second;
        engine.setSourcePlates(sourcePlates);

        Pair<PlateType, Plate> targetPlateSource = getReformatTargetPlateSource(container, user, options);
        engine.setTargetPlateType(targetPlateSource.first);
        engine.setTargetTemplate(targetPlateSource.second);

        // Resolve selected sample configuration (if any)
        Pair<Collection<Long>, Integer> sampleSelection = resolveSelectedSamples(options.getSampleSelectionKey(), targetPlateSet);
        engine.setSampleIds(sampleSelection.first);
        Integer selectedSampleCount = sampleSelection.second;

        engine.setTargetPlates(getReformatTargetPlates(targetPlateSet));
        engine.setTargetPlateData(getReformatTargetPlateData(options, targetPlateSet));

        // Execute plate layout
        WellData.Cache wellDataCache = new WellData.Cache(container, user);
        List<WellLayout> wellLayouts = engine.run(container, user, wellDataCache);

        int availablePlateCount = targetPlateSet.availablePlateCount();
        long newPlateCount = wellLayouts.stream().filter(layout -> layout.getTargetPlateId() == null).count();
        if (availablePlateCount < newPlateCount)
        {
            throw new ValidationException(String.format(
                "This plate set has space for %d more plates. This operation will generate %d plates.",
                availablePlateCount,
                newPlateCount
            ));
        }

        // Populate plate data from well layouts
        HydrateContext hydrateContext = new HydrateContext(container, user, wellLayouts, options, engine.getOperation(), new HashSet<>(), wellDataCache);
        HydratedResult hydratedResults = hydratePlateDataFromWellLayout(hydrateContext);
        List<PlateData> plateData = hydratedResults.plateData();
        List<Pair<Plate, PlateData>> existingPlates = hydratedResults.existingPlates();

        if (options.isPreview())
        {
            List<PreviewPlateData> previewData = getPreviewData(options, existingPlates, plateData, allPlateTypes);
            return new ReformatResult(previewData, plateData.size(), existingPlates.size(), null, null, null, hydratedResults.platedSampleCount(), selectedSampleCount);
        }

        if (plateData.isEmpty() && existingPlates.isEmpty())
            throw new ValidationException("This operation as configured does not create or update any plates.");

        Long plateSetRowId;
        String plateSetName;
        List<Plate> newPlates;

        if (targetPlateSet.isNew())
        {
            PlateSet parentPlateSet = resolveParentPlateSet(container, user, options, sourcePlateSet);
            Long parentPlateSetId = parentPlateSet != null ? parentPlateSet.getRowId() : null;

            PlateSet newPlateSet = createPlateSet(container, user, targetPlateSet, plateData, parentPlateSetId, "Created via reformat.");
            plateSetRowId = newPlateSet.getRowId();
            plateSetName = newPlateSet.getName();
            newPlates = newPlateSet.getPlates();
        }
        else
        {
            try (DbScope.Transaction tx = ensureTransaction())
            {
                ensureTransactionAuditId(tx, container, user, QueryService.AuditAction.INSERT);

                if (!existingPlates.isEmpty())
                {
                    QueryUpdateService qus = requiredUpdateService(getWellTable(container, user));

                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (Pair<Plate, PlateData> entry : existingPlates)
                        rows.addAll(entry.getValue().data());

                    BatchValidationException errors = new BatchValidationException();
                    qus.updateRows(user, container, rows, null, errors, null, null);
                    if (errors.hasErrors())
                        throw errors;
                }

                plateSetRowId = targetPlateSet.getRowId();
                plateSetName = targetPlateSet.getName();
                newPlates = addPlatesToPlateSet(container, user, plateSetRowId, targetPlateSet.isTemplate(), plateData, String.format("Added via reformat to plate set \"%s\".", plateSetName));

                tx.commit();
            }
        }

        List<Long> plateRowIds = newPlates.stream().map(Plate::getRowId).toList();
        return new ReformatResult(null, plateRowIds.size(), existingPlates.size(), plateSetRowId, plateSetName, plateRowIds, hydratedResults.platedSampleCount(), selectedSampleCount);
    }

    private @Nullable List<PreviewPlateData> getPreviewData(
        ReformatOptions options,
        List<Pair<Plate, PlateData>> existingPlates,
        List<PlateData> newPlateData,
        List<? extends PlateType> allPlateTypes
    )
    {
        if (!options.isPreviewData())
            return null;

        List<PreviewPlateData> previewData = new ArrayList<>();
        Map<Long, PlateType> plateTypes = new LongHashMap<>();

        for (PlateType type : allPlateTypes)
            plateTypes.put(type.getRowId(), type);

        for (Pair<Plate, PlateData> entry : existingPlates)
        {
            Plate plate = entry.getKey();
            PlateData plateData = entry.getValue();

            Integer wellCount = plate.getPlateType().getWellCount();
            Integer wellsEmpty = 0;
            Integer wellsFilled = 0;
            Integer samplesAdded = 0;
            Set<String> updatedPositions = new HashSet<>();
            Set<Long> sampleIds = new HashSet<>();

            for (Map<String, Object> row : plateData.data)
            {
                Long sampleId = MapUtils.getLong(row, WellTable.Column.SampleID.name());
                if (sampleId != null)
                {
                    wellsFilled++;
                    if (!sampleIds.contains(sampleId))
                        samplesAdded++;
                    sampleIds.add(sampleId);

                    String position = (String) row.get(WellTable.WELL_LOCATION);
                    if (position == null)
                        throw new IllegalStateException("Failed to resolve position from well data");
                    updatedPositions.add(position);
                }
            }

            for (Well well : plate.getWells())
            {
                if (updatedPositions.contains(well.getDescription()))
                    continue;

                if (well.getSampleId() != null)
                {
                    wellsFilled++;
                    sampleIds.add(well.getSampleId());
                }
                else
                    wellsEmpty++;
            }

            previewData.add(PreviewPlateData.create(plateData, plate.getRowId(), wellCount, wellsEmpty, wellsFilled, sampleIds.size(), samplesAdded));
        }

        for (PlateData plate : newPlateData)
        {
            Integer wellCount = null;
            Integer wellsEmpty = null;
            Integer wellsFilled = null;
            Integer sampleCount = null;

            if (plate.plateType != null && plateTypes.containsKey(plate.plateType) && plate.data != null)
            {
                PlateType type = plateTypes.get(plate.plateType);
                wellCount = type.getWellCount();
                wellsFilled = 0;
                Set<Long> sampleIds = new HashSet<>();

                for (Map<String, Object> row : plate.data)
                {
                    Long sampleId = MapUtils.getLong(row,WellTable.Column.SampleID.name());
                    if (sampleId != null)
                    {
                        wellsFilled++;
                        sampleIds.add(sampleId);
                    }
                }

                sampleCount = sampleIds.size();
                wellsEmpty = wellCount - wellsFilled;
            }

            previewData.add(PreviewPlateData.create(plate, null, wellCount, wellsEmpty, wellsFilled, sampleCount, sampleCount));
        }

        return previewData;
    }

    private @Nullable PlateSet resolveParentPlateSet(
        Container container,
        User user,
        ReformatOptions options,
        @Nullable PlateSet sourcePlateSet
    ) throws ValidationException
    {
        if (options.getTargetPlateSet() != null && options.getTargetPlateSet().getParentPlateSetId() != null)
        {
            // If a parent rowId is specified, then require that it resolves in this container scope
            PlateSet parentPlateSet = requirePlateSet(container, getPlateLookupContainerFilter(container, user), options.getTargetPlateSet().getParentPlateSetId(), null);
            if (parentPlateSet.isPrimary() || !parentPlateSet.isStandalone())
                return parentPlateSet;
        }
        else if (sourcePlateSet != null && (sourcePlateSet.isPrimary() || !sourcePlateSet.isStandalone()))
            return sourcePlateSet;

        return null;
    }

    private @NotNull List<Long> getSourcePlateRowIds(ReformatOptions options, LayoutOperation layoutOperation) throws ValidationException
    {
        boolean hasPlateRowIds = options.getPlateRowIds() != null && !options.getPlateRowIds().isEmpty();

        String selectionKey = StringUtils.trimToNull(options.getPlateSelectionKey());
        boolean hasPlateSelectionKey = selectionKey != null;

        if (hasPlateRowIds && hasPlateSelectionKey)
            throw new ValidationException("Either \"plateRowIds\" or \"plateSelectionKey\" can be specified but not both.");
        else if (!hasPlateRowIds && !hasPlateSelectionKey && layoutOperation.requiresSourcePlates())
            throw new ValidationException("Either \"plateRowIds\" or \"plateSelectionKey\" must be specified for this operation.");

        List<Long> plateRowIds = emptyList();
        if (hasPlateRowIds)
            plateRowIds = options.getPlateRowIds();
        else if (selectionKey != null)
            plateRowIds = getSelection(selectionKey).stream().toList();

        if (plateRowIds.isEmpty() && layoutOperation.requiresSourcePlates())
            throw new ValidationException("No source plates are specified.");

        for (Long plateRowId : plateRowIds)
        {
            if (plateRowId == null)
                throw new ValidationException("An invalid null plate row id was specified.");
            else if (plateRowId < 1)
                throw new ValidationException(String.format("An invalid plate row id (%d) was specified.", plateRowId));
        }

        return plateRowIds;
    }

    private @NotNull PlateSetImpl getReformatTargetPlateSet(Container container, ReformatOptions options) throws ValidationException
    {
        ReformatOptions.TargetPlateSet targetPlateSetOptions = options.getTargetPlateSet();
        if (targetPlateSetOptions == null)
            throw new ValidationException("A \"targetPlateSet\" must be specified.");

        boolean hasRowId = targetPlateSetOptions.getRowId() != null && targetPlateSetOptions.getRowId() > 0;
        boolean hasType = targetPlateSetOptions.getType() != null;

        if (hasRowId && hasType)
            throw new ValidationException("Either a \"rowId\" or a \"type\" can be specified for \"targetPlateSet\" but not both.");
        else if (!hasRowId && !hasType)
            throw new ValidationException("Either a \"rowId\" or a \"type\" must be specified for \"targetPlateSet\".");

        return getTargetPlateSet(container, targetPlateSetOptions);
    }

    private @NotNull PlateSetImpl getTargetPlateSet(Container container, ReformatOptions.TargetPlateSet targetPlateSetOptions) throws ValidationException
    {
        PlateSetImpl plateSet;
        if (targetPlateSetOptions.getRowId() != null && targetPlateSetOptions.getRowId() > 0)
        {
            plateSet = (PlateSetImpl) requirePlateSet(container, targetPlateSetOptions.getRowId(), null);
            if (plateSet.isArchived())
                throw new ValidationException(String.format("Plate Set \"%s\" is archived and cannot be modified.", plateSet.getName()));
            if (plateSet.isFull())
                throw new ValidationException(String.format("Plate Set \"%s\" is full and cannot include additional plates.", plateSet.getName()));
        }
        else
        {
            plateSet = new PlateSetImpl();
            plateSet.setType(targetPlateSetOptions.getType());

            String plateSetName = StringUtils.trimToNull(targetPlateSetOptions.getName());
            if (plateSetName != null)
                plateSet.setName(plateSetName);

            String description = StringUtils.trimToNull(targetPlateSetOptions.getDescription());
            if (description != null)
                plateSet.setDescription(description);

            if (Boolean.TRUE.equals(targetPlateSetOptions.isTemplate()))
                plateSet.setTemplate(true);
        }

        return plateSet;
    }

    private @NotNull Pair<PlateType, Plate> getReformatTargetPlateSource(
        Container container,
        User user,
        ReformatOptions options
    ) throws ValidationException
    {
        PlateType targetPlateType = null;
        Plate targetTemplate = null;

        ReformatOptions.TargetPlateSource plateSource = options.getTargetPlateSource();
        if (plateSource != null)
        {
            if (plateSource.getSourceType() == null)
                throw new ValidationException("A \"type\" must be specified for \"targetPlateSource\".");
            if (plateSource.getRowId() == null || plateSource.getRowId() < 1)
                throw new ValidationException("A \"rowId\" must be specified for \"targetPlateSource\".");

            if (ReformatOptions.TargetPlateSource.SourceType.type.equals(plateSource.getSourceType()))
                targetPlateType = requirePlateType(plateSource.getRowId(), null);
            else if (ReformatOptions.TargetPlateSource.SourceType.template.equals(plateSource.getSourceType()))
            {
                targetTemplate = getPlate(getPlateLookupContainerFilter(container, user), plateSource.getRowId());
                if (targetTemplate == null)
                    throw new ValidationException(String.format("Unable to plate template with rowId (%d).", plateSource.getRowId()));
                if (!targetTemplate.isTemplate())
                    throw new ValidationException(String.format("Plate \"%s\" is not a valid template.", targetTemplate.getName()));
                if (targetTemplate.isArchived())
                    throw new ValidationException(String.format("Template \"%s\" is archived and cannot be used for reformatting.", targetTemplate.getName()));
            }
            else
                throw new ValidationException("A valid \"type\" must be specified for \"targetPlateSource\".");
        }

        return Pair.of(targetPlateType, targetTemplate);
    }

    private Pair<PlateSet, List<Plate>> getReformatSourcePlates(
        Container container,
        ReformatOptions options,
        LayoutOperation layoutOperation
    ) throws ValidationException
    {
        List<Plate> sourcePlates = new ArrayList<>();
        PlateSet sourcePlateSet = null;
        for (Long plateRowId : getSourcePlateRowIds(options, layoutOperation))
        {
            Plate sourcePlate = requirePlate(container, plateRowId, null);
            PlateSet plateSet = sourcePlate.getPlateSet();
            if (plateSet == null || plateSet.getRowId() == null)
                throw new ValidationException(String.format("Unable to resolve plate set for source plate \"%s\".", sourcePlate.getName()));

            if (sourcePlateSet == null)
                sourcePlateSet = plateSet;
            else if (!Objects.equals(sourcePlateSet.getRowId(), plateSet.getRowId()))
                throw new ValidationException("All source plates must be from the same plate set.");

            sourcePlates.add(sourcePlate);
        }

        if (sourcePlateSet != null && !container.equals(sourcePlateSet.getContainer()))
            throw new ValidationException(String.format("Plate set \"%s\" is not in the %s folder.", sourcePlateSet.getName(), container.getPath()));

        return Pair.of(sourcePlateSet, sourcePlates);
    }

    private @NotNull List<Plate> getReformatTargetPlates(@NotNull PlateSetImpl targetPlateSet)
    {
        if (targetPlateSet.isNew())
            return emptyList();

        return getPlatesForPlateSet(targetPlateSet);
    }

    private @NotNull List<PlateData> getReformatTargetPlateData(ReformatOptions options, @NotNull PlateSetImpl targetPlateSet) throws ValidationException
    {
        List<PlateData> plateData = options.getPlates();
        if (plateData == null || plateData.isEmpty())
            return emptyList();

        if (targetPlateSet.isPrimary() && plateData.stream().anyMatch(data -> data.templateId != null))
            throw new ValidationException(String.format("Plate templates are not supported for %s plate sets.", PlateSetType.primary.name()));

        return plateData;
    }

    public @NotNull Pair<Collection<Long>, Integer> resolveSelectedSamples(String sampleSelectionKey, @NotNull PlateSetImpl targetPlateSet) throws ValidationException
    {
        String selectionKey = StringUtils.trimToNull(sampleSelectionKey);
        if (selectionKey == null)
            return Pair.of(emptyList(), null);

        Collection<Long> sampleIds = getSelection(selectionKey).stream().toList();
        if (sampleIds.isEmpty())
            throw new ValidationException("Empty sample selection.");

        int selectedSampleCount = sampleIds.size();

        if (targetPlateSet.isPrimary() && !targetPlateSet.isNew())
        {
            AssayDbSchema schema = AssayDbSchema.getInstance();

            SQLFragment sql = new SQLFragment("SELECT DISTINCT W.SampleId FROM ").append(schema.getTableInfoWell(), "W")
                    .append(" INNER JOIN ").append(schema.getTableInfoPlate(), "P").append(" ON P.RowId = W.PlateId")
                    .append(" INNER JOIN ").append(schema.getTableInfoPlateSet(), "PS").append(" ON PS.RowID = P.PlateSet")
                    .append(" WHERE PS.RowId = ?").add(targetPlateSet.getRowId())
                    .append(" AND W.SampleID ").appendInClause(sampleIds, schema.getScope().getSqlDialect());

            List<Long> overlap = new SqlSelector(schema.getSchema(), sql).getArrayList(Long.class);
            if (!overlap.isEmpty())
            {
                sampleIds = new LongArrayList(sampleIds);
                sampleIds.removeAll(overlap);

                if (sampleIds.isEmpty())
                    throw new ValidationException(String.format("All %d selected samples are already plated in plate set \"%s\".", selectedSampleCount, targetPlateSet.getName()));
            }
        }

        return Pair.of(sampleIds, selectedSampleCount);
    }

    private PlateData hydrateFromExistingPlate(HydrateContext context, WellLayout wellLayout, @NotNull Plate existingPlate)
    {
        List<Map<String, Object>> targetWellData = new ArrayList<>();
        List<WellData> existingPlateData = context.wellDataCache().getData(existingPlate.getRowId(), true, true);
        boolean isPreview = context.options().isPreview();

        for (WellLayout.Well well : wellLayout.getWells())
        {
            if (well == null)
                continue;

            for (WellData wellData : existingPlateData)
            {
                if (wellData.getRow() == well.destinationRowIdx() && wellData.getCol() == well.destinationColIdx())
                {
                    Position p = new PositionImpl(context.container(), well.destinationRowIdx(), well.destinationColIdx());

                    WellData d = new WellData();
                    d.setSampleId(well.sourceSampleId());

                    if (isPreview)
                    {
                        d.setPosition(p.getDescription());
                        d.setWellGroup(wellData.getWellGroup());
                        d.setReplicateGroup(wellData.getReplicateGroup());
                        d.setType(wellData.getType());
                    }
                    else
                        d.setRowId(wellData.getRowId());

                    if (d.getSampleId() != null)
                        context.platedSampleIds().add(d.getSampleId());

                    targetWellData.add(d.getData(true));
                }
            }
        }

        return new PlateData(existingPlate.getName(), existingPlate.getPlateType().getRowId(), null, existingPlate.getBarcode(), targetWellData);
    }

    private void hydrateFromPlate(HydrateContext context, WellLayout wellLayout, List<Map<String, Object>> targetWellData)
    {
        for (WellLayout.Well well : wellLayout.getWells())
        {
            if (well == null)
                continue;

            long sourcePlateId = well.sourcePlateId();

            if (sourcePlateId > 0)
            {
                List<WellData> sourceWellData = context.wellDataCache().getData(sourcePlateId, true, true);

                for (WellData wellData : sourceWellData)
                {
                    if (!wellData.hasData())
                        continue;

                    if (wellData.getRow() == well.sourceRowIdx() && wellData.getCol() == well.sourceColIdx())
                    {
                        Position p = new PositionImpl(context.container(), well.destinationRowIdx(), well.destinationColIdx());

                        WellData d = new WellData();
                        d.setPosition(p.getDescription());
                        d.setSampleId(wellData.getSampleId());

                        if (wellLayout.isSampleOnly())
                        {
                            d.setType(SAMPLE);
                            if (d.getSampleId() != null)
                                context.platedSampleIds().add(d.getSampleId());
                        }
                        else
                        {
                            d.setMetadata(wellData.getMetadata());
                            d.setWellGroup(wellData.getWellGroup());
                            d.setReplicateGroup(wellData.getReplicateGroup());
                            d.setType(wellData.getType());
                        }

                        targetWellData.add(d.getData());
                        break;
                    }
                }
            }
            else if (well.sourceSampleId() != null)
            {
                Position p = new PositionImpl(context.container(), well.destinationRowIdx(), well.destinationColIdx());

                WellData d = new WellData();
                d.setPosition(p.getDescription());
                d.setType(SAMPLE);
                d.setSampleId(well.sourceSampleId());
                context.platedSampleIds().add(well.sourceSampleId());

                targetWellData.add(d.getData());
            }
        }
    }

    private void hydrateFromPlateTemplate(HydrateContext context, WellLayout wellLayout, List<Map<String, Object>> targetWellData)
    {
        List<WellData> templateWellData = context.wellDataCache().getData(wellLayout.getTargetTemplateId(), false, true);

        for (WellData wellData : templateWellData)
        {
            WellData d = new WellData();

            int rowIdx = wellData.getRow();
            int colIdx = wellData.getCol();
            Position p = new PositionImpl(context.container(), rowIdx, colIdx);
            d.setPosition(p.getDescription());

            WellLayout.Well well = wellLayout.getWell(rowIdx, colIdx);
            if (well != null)
            {
                Long sampleId = well.sourceSampleId();
                d.setSampleId(sampleId);
                if (sampleId != null)
                    context.platedSampleIds().add(sampleId);
            }

            d.setMetadata(wellData.getMetadata());
            d.setWellGroup(wellData.getWellGroup());
            d.setReplicateGroup(wellData.getReplicateGroup());
            d.setType(wellData.getType());

            targetWellData.add(d.getData());
        }
    }

    private record HydrateContext(
        Container container,
        User user,
        List<WellLayout> wellLayouts,
        ReformatOptions options,
        LayoutOperation operation,
        Set<Long> platedSampleIds,
        WellData.Cache wellDataCache
    ) {}

    private record HydratedResult(List<PlateData> plateData, @Nullable Integer platedSampleCount, List<Pair<Plate, PlateData>> existingPlates) {}

    private @NotNull HydratedResult hydratePlateDataFromWellLayout(HydrateContext context) throws ValidationException
    {
        if (context.wellLayouts().isEmpty())
            return new HydratedResult(emptyList(), null, emptyList());

        List<PlateData> plates = new ArrayList<>();
        List<PlateData> plateData = context.options().getPlates();
        List<Pair<Plate, PlateData>> existingPlates = new ArrayList<>();
        int plateDataIndex = 0;

        for (WellLayout wellLayout : context.wellLayouts())
        {
            if (wellLayout.getTargetPlateId() != null)
            {
                Plate plate = requirePlate(context.container(), wellLayout.getTargetPlateId(), null);
                PlateData targetPlateData = hydrateFromExistingPlate(context, wellLayout, plate);
                existingPlates.add(Pair.of(plate, targetPlateData));
            }
            else
            {
                List<Map<String, Object>> targetWellData = new ArrayList<>();

                Long templateId = null;
                if (wellLayout.getTargetTemplateId() != null)
                {
                    templateId = wellLayout.getTargetTemplateId();
                    hydrateFromPlateTemplate(context, wellLayout, targetWellData);
                }
                else
                {
                    List<WellLayout.Well> sourcedWells = Arrays.stream(wellLayout.getWells()).filter(well -> well != null && well.sourcePlateId() > 0).toList();
                    if (!sourcedWells.isEmpty())
                    {
                        Long sourcePlateId = sourcedWells.get(0).sourcePlateId();
                        if (sourcedWells.stream().allMatch(w -> sourcePlateId.equals(w.sourcePlateId())))
                            templateId = sourcePlateId;
                    }

                    hydrateFromPlate(context, wellLayout, targetWellData);
                }

                if (context.operation().produceEmptyPlates() || !targetWellData.isEmpty())
                {
                    String name = null;
                    String barcode = null;
                    if (plateData != null && plateData.size() > plateDataIndex)
                    {
                        PlateData data = plateData.get(plateDataIndex);
                        if (data != null)
                        {
                            name = data.name();
                            barcode = data.barcode();
                        }
                    }

                    plates.add(new PlateData(name, wellLayout.getPlateType().getRowId(), templateId, barcode, targetWellData));
                }
            }

            plateDataIndex++;
        }

        return new HydratedResult(plates, context.platedSampleIds().isEmpty() ? null : context.platedSampleIds().size(), existingPlates);
    }

    @Override
    public void beforeMaterialDelete(List<? extends ExpMaterial> materials, Container container, User user)
    {
        if (materials == null || materials.isEmpty())
            return;

        // Issue 53578: Clear foreign key references in the well table when materials are deleted
        var wellTable = PlateSchema.getWellTable(container, user, ContainerFilter.getUnsafeEverythingFilter());
        var updateSql = new SQLFragment("UPDATE assay.Well SET SampleId = NULL WHERE SampleId ")
                .appendInClause(materials.stream().map(ExpObject::getRowId).toList(), wellTable.getSqlDialect());

        new SqlExecutor(wellTable.getSchema()).execute(updateSql);
    }

    private class BulkPlateIndexer extends Thread
    {
        Map<Container, Set<Long>> _plates;

        public BulkPlateIndexer(Map<Container, Set<Long>> plates)
        {
            _plates = plates;
        }

        @Override
        public void run()
        {
            for (Map.Entry<Container, Set<Long>> entry : _plates.entrySet())
            {
                for (Long plateId : entry.getValue())
                {
                    LOG.debug("Indexing plate ID " + plateId);
                    indexPlate(entry.getKey(), plateId, true);
                }
            }
        }
    }

    private void addPlateAuditEvents(User user, Collection<Plate> plates, Function<PlateImpl, PlateAuditEvent> eventFactory)
    {
        if (plates.isEmpty())
            return;

        List<PlateAuditEvent> auditEvents = new ArrayList<>(plates.size());
        for (Plate plate : plates)
            auditEvents.add(eventFactory.apply((PlateImpl) plate));

        AuditLogService.get().addEvents(user, auditEvents, true);
    }

    private void addPlateCreatedAuditEvents(Container container, User user, DbScope.Transaction tx, Collection<Plate> plates, @Nullable String additionalComment)
    {
        addPlateAuditEvents(user, plates, plate -> PlateAuditProvider.EventFactory.plateCreated(container, tx.getAuditEvent(), plate, additionalComment));
    }

    public void addPlateDeletedAuditEvents(Container container, User user, DbScope.Transaction tx, Collection<Plate> plates)
    {
        addPlateAuditEvents(user, plates, plate -> PlateAuditProvider.EventFactory.plateDeleted(container, tx.getAuditEvent(), plate));
    }

    public void addPlateImportAuditEvents(Container container, User user, DbScope.Transaction tx, Collection<Plate> plates, ExpRun run, boolean isReimport)
    {
        addPlateAuditEvents(user, plates, plate -> PlateAuditProvider.EventFactory.plateImported(container, tx.getAuditEvent(), plate, run, isReimport));
    }

    public void ensureTransactionAuditId(DbScope.Transaction tx, Container container, User user, QueryService.AuditAction auditAction)
    {
        if (tx.getAuditId() != null)
            return;

        AbstractQueryUpdateService.addTransactionAuditEvent(tx, user, AbstractQueryUpdateService.createTransactionAuditEvent(container, auditAction));
    }
}
