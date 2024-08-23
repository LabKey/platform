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
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
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
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
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
import org.labkey.assay.plate.data.PlateMapExcelWriter;
import org.labkey.assay.plate.data.WellData;
import org.labkey.assay.plate.layout.LayoutEngine;
import org.labkey.assay.plate.layout.LayoutOperation;
import org.labkey.assay.plate.layout.WellLayout;
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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.labkey.api.assay.plate.PlateSet.MAX_PLATES;
import static org.labkey.assay.plate.query.WellTable.WELL_LOCATION;

public class PlateManager implements PlateService, AssayListener, ExperimentListener
{
    private static final Logger LOG = LogManager.getLogger(PlateManager.class);
    private static final String LSID_CLASS_OBJECT_ID = "objectType";

    private final List<PlateService.PlateDetailsResolver> _detailsLinkResolvers = new ArrayList<>();
    private boolean _lsidHandlersRegistered = false;
    private final Map<String, PlateLayoutHandler> _plateLayoutHandlers = new HashMap<>();

    // name expressions, currently not configurable
    private static final String PLATE_SET_NAME_EXPRESSION = "PLS-${now:date('yyyyMMdd')}-${RowId}";
    private static final String PLATE_NAME_EXPRESSION = "${${PlateSet/PlateSetId}-:withCounter}";

    private final Map<Container, Set<Integer>> _plateIndexMap = new ConcurrentHashMap<>();
    private final AtomicBoolean _pausePlateIndex = new AtomicBoolean(false);
    private static final Object PLATE_INDEX_LOCK = new Object();

    // This flag is applied to the extraScriptContext of query mutating calls (e.g. insertRows, updateRows, etc.)
    // when those calls are being made for a plate copy operation.
    public static final String PLATE_COPY_FLAG = ".plateCopy";

    // This flag is applied to the extraScriptContext of query mutating calls (e.g. insertRows, updateRows, etc.)
    // when those calls are being made for a plate save operation.
    public static final String PLATE_SAVE_FLAG = ".plateSave";

    public SearchService.SearchCategory PLATE_CATEGORY = new SearchService.SearchCategory("plate", "Plate") {
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
    public @Nullable Plate createPlate(Plate plate, double[][] wellValues, boolean[][] excluded, int runId, int plateNumber)
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
        @Nullable Integer plateSetId,
        @Nullable List<Map<String, Object>> data
    ) throws Exception
    {
        if (!plate.isNew())
            throw new ValidationException(String.format("Failed to create plate. The provided plate already exists with rowId (%d).", plate.getRowId()));

        if (plate.isTemplate() && isDuplicatePlateTemplateName(container, plate.getName()))
            throw new ValidationException(String.format("Failed to create plate template. A plate template already exists with the name \"%s\".", plate.getName()));

        try (DbScope.Transaction tx = ensureTransaction())
        {
            if (plateSetId != null)
            {
                PlateSet plateSet = getPlateSet(container, plateSetId);
                if (plateSet == null)
                    throw new ValidationException(String.format("Failed to create plate. Plate set with rowId (%d) is not available in %s.", plateSetId, container.getPath()));
                if (plate.isTemplate() && !plateSet.isTemplate())
                    throw new ValidationException(String.format("Failed to create plate. Plate set \"%s\" is not a template plate set.", plateSet.getName()));
                if (!plate.isTemplate() && plateSet.isTemplate())
                    throw new ValidationException(String.format("Failed to create plate. Plate set \"%s\" is a template plate set.", plateSet.getName()));
                ((PlateImpl) plate).setPlateSet(plateSet);
            }

            int plateRowId = save(container, user, plate, data);
            plate = getPlate(container, plateRowId);
            if (plate == null)
                throw new IllegalStateException("Unexpected failure. Failed to retrieve plate after save (pre-commit).");

            deriveCustomFieldsFromWellData(container, user, plate, data);

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

    private void deriveCustomFieldsFromWellData(
        @NotNull Container container,
        @NotNull User user,
        @NotNull Plate plate,
        List<Map<String, Object>> data
    ) throws Exception
    {
        requireActiveTransaction();

        if (data == null || data.isEmpty())
            return;

        TableInfo wellTable = getWellTable(container, user);
        Set<PlateCustomField> customFields = new HashSet<>();

        TableInfo metadataTable = getPlateMetadataTable(container, user);
        Set<FieldKey> metadataFields = Collections.emptySet();
        if (metadataTable != null)
            metadataFields = metadataTable.getColumns().stream().map(ColumnInfo::getFieldKey).collect(Collectors.toSet());

        // resolve columns and set any custom fields associated with the plate
        for (Map<String, Object> dataMap : data)
        {
            var dataRow = new CaseInsensitiveHashMap<>(dataMap);

            if (dataRow.containsKey(WELL_LOCATION))
            {
                for (String colName : dataRow.keySet())
                {
                    ColumnInfo col = wellTable.getColumn(FieldKey.fromParts(colName));
                    if (col != null && metadataFields.contains(col.getFieldKey()))
                    {
                        customFields.add(new PlateCustomField(col.getPropertyURI()));
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
        filter.addCondition(FieldKey.fromParts("name"), plateName);

        List<PlateBean> plates = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getArrayList(PlateBean.class);
        // this should be 1 or 0, but don't blow up if there are more than one
        if (!plates.isEmpty())
            return populatePlate(plates.get(0));

        return null;
    }

    public List<FieldKey> getMetadataColumns(@NotNull PlateSet plateSet, Container c, User user, ContainerFilter cf)
    {
        Set<FieldKey> includedMetadataCols = new HashSet<>();
        for (Plate plate : plateSet.getPlates())
        {
            QueryView plateQueryView = getPlateQueryView(c, user, cf, plate, false);
            Map<String, FieldKey> displayColumns = getPlateDisplayColumns(plateQueryView)
                    .stream()
                    .filter(col -> col.getFilterKey() != null)
                    .collect(Collectors.toMap(col -> col.getColumnInfo().getPropertyURI(), DisplayColumn::getFilterKey));

            for (PlateCustomField field : plate.getCustomFields())
            {
                FieldKey lookupFk = displayColumns.get(field.getPropertyURI());
                if (lookupFk != null)
                    includedMetadataCols.add(lookupFk);
                else
                    includedMetadataCols.add(FieldKey.fromParts(field.getName()));
            }
        }

        return includedMetadataCols.stream().sorted(Comparator.comparing(k -> k.getName().toLowerCase())).toList();
    }

    @NotNull
    public List<Plate> getPlateTemplates(Container container)
    {
        return PlateCache.getPlateTemplates(container);
    }

    @Override
    public @Nullable PlateSet getPlateSet(Container container, int plateSetId)
    {
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateSet()).getObject(container, plateSetId, PlateSetImpl.class);
    }

    @Override
    public @Nullable PlateSet getPlateSet(ContainerFilter cf, int plateSetId)
    {
        SimpleFilter filterPlateSet = new SimpleFilter(FieldKey.fromParts("RowId"), plateSetId);
        Container c = getContainerWithPlateSetIdentifier(cf, filterPlateSet);
        return getPlateSet(c, plateSetId);
    }

    @Override
    public List<? extends ExpRun> getRunsUsingPlate(@NotNull Container c, @NotNull User user, @NotNull Plate plate)
    {
        SqlSelector se = selectRunUsingPlateTemplate(c, user, plate);
        if (se == null)
            return emptyList();

        Collection<Integer> runIds = se.getCollection(Integer.class);
        return ExperimentService.get().getExpRuns(runIds);
    }

    @Override
    public int getRunCountUsingPlate(@NotNull Container c, @NotNull User user, @NotNull Plate plate)
    {
        int count = 0;
        SqlSelector se = selectRunUsingPlateTemplate(c, user, plate);
        if (se != null)
            count += (int) se.getRowCount();

        count += getRunIdsUsingPlateInResults(c, user, plate).size();

        return count;
    }

    private @NotNull List<Integer> getRunIdsUsingPlateInResults(@NotNull Container c, @NotNull User user, @NotNull Plate plate)
    {
        // first, get the list of GPAT protocols in the container
        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);
        if (provider == null)
            return Collections.emptyList();

        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c, provider)
                .stream().filter(provider::isPlateMetadataEnabled).toList();

        // get the runIds for each protocol, query against its assay results table
        List<Integer> runIds = new ArrayList<>();
        for (ExpProtocol protocol : protocols)
        {
            AssayProtocolSchema assayProtocolSchema = provider.createProtocolSchema(user, protocol.getContainer(), protocol, null);
            TableInfo assayDataTable = assayProtocolSchema.createDataTable(ContainerFilter.EVERYTHING, false);
            if (assayDataTable != null)
            {
                ColumnInfo dataIdCol = assayDataTable.getColumn("DataId");
                if (dataIdCol != null)
                {
                    SQLFragment subSelectSql = new SQLFragment("SELECT DISTINCT dataid FROM ")
                            .append(assayDataTable)
                            .append(" WHERE plate = ?")
                            .add(plate.getRowId());

                    SQLFragment sql = new SQLFragment("SELECT DISTINCT runid FROM ")
                            .append(ExperimentService.get().getTinfoData())
                            .append(" WHERE rowid IN (").append(subSelectSql).append(")");

                    Collection<Integer> assayRunIds = new SqlSelector(ExperimentService.get().getSchema(), sql).getCollection(Integer.class);
                    if (!assayRunIds.isEmpty())
                        runIds.addAll(assayRunIds);
                }
            }
        }

        return runIds;
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

    @NotNull Plate createPlateTemplate(Container container, String assayType, @NotNull PlateType plateType)
    {
        Plate plate = createPlate(container, assayType, plateType);
        ((PlateImpl)plate).setTemplate(true);

        return plate;
    }

    @Override
    public @Nullable Plate getPlate(Container container, int rowId)
    {
        return PlateCache.getPlate(container, rowId);
    }

    @Override
    public @Nullable Plate getPlate(ContainerFilter cf, int rowId)
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
    public @Nullable Plate getPlate(ContainerFilter cf, Integer plateSetId, Object plateIdentifier)
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
        ContainerFilter lookupCf = QueryService.get().getContainerFilterForLookups(container, user);
        ContainerFilter currentCf = ContainerFilter.Type.Current.create(protocol != null ? protocol.getContainer() : container, user);
        return lookupCf != null ? lookupCf : currentCf;
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

    private @NotNull Plate requirePlate(Container container, int plateRowId, @Nullable String errorPrefix) throws ValidationException
    {
        return (Plate) require(getPlate(container, plateRowId), "Plate id \"" + plateRowId + "\" not found.", errorPrefix);
    }

    private @NotNull PlateSet requirePlateSet(Container container, int plateSetRowId, @Nullable String errorPrefix) throws ValidationException
    {
        return (PlateSet) require(
            getPlateSet(container, plateSetRowId),
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

    private @NotNull PlateType requirePlateType(int plateTypeRowId, @Nullable String errorPrefix) throws ValidationException
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

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Name"), name);
        filter.addCondition(FieldKey.fromParts("Template"), true);

        ContainerFilter cf = getPlateLookupContainerFilter(container, User.getAdminServiceUser());
        filter.addCondition(cf.createFilterClause(AssayDbSchema.getInstance().getSchema(), FieldKey.fromParts("Container")));

        return new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), Set.of("RowId"), filter, null).exists();
    }

    private @NotNull ContainerFilter getPlateLookupContainerFilter(Container container, User user)
    {
        ContainerFilter cf = QueryService.get().getContainerFilterForLookups(container, user);
        if (cf != null)
            return cf;
        return ContainerFilter.current(container);
    }

    @Override
    public @NotNull List<Plate> getPlates(Container c)
    {
        return PlateCache.getPlates(c);
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
    public int save(Container container, User user, Plate plate) throws Exception
    {
        return save(container, user, plate, null);
    }

    private int save(Container container, User user, Plate plate, @Nullable List<Map<String, Object>> wellData) throws Exception
    {
        if (plate instanceof PlateImpl plateTemplate)
            return savePlateImpl(container, user, plateTemplate, false, wellData);
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
        Map<Integer, Set<Integer>> wellToWellGroups = new HashMap<>();
        for (Map<String, Object> groupPosition : allGroupPositions)
        {
            Integer wellId = (Integer) groupPosition.get("wellId");
            Integer wellGroupId = (Integer) groupPosition.get("wellGroupId");
            Set<Integer> wellGroupIds = wellToWellGroups.computeIfAbsent(wellId, k -> new HashSet<>());
            wellGroupIds.add(wellGroupId);
        }

        // construct groupIdToPositions: map of wellGroupId -> List of PositionImpl
        Map<Integer, List<PositionImpl>> groupIdToPositions = new HashMap<>();
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
            List<DomainProperty> fields = getPlateMetadataDomainProperties(domain, plate.getRowId());

            if (!fields.isEmpty())
            {
                plate.setCustomFields(fields.stream()
                        .sorted(Comparator.comparing(DomainProperty::getName))
                        .map(PlateCustomField::new).toList());
            }
        }
        return plate;
    }

    private WellImpl[] getWells(Plate plate)
    {
        SimpleFilter plateFilter = new SimpleFilter(FieldKey.fromParts("PlateId"), plate.getRowId());
        Sort sort = new Sort("Col,Row");
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), plateFilter, sort).getArray(WellImpl.class);
    }

    public Lsid getLsid(Class<?> type, Container container)
    {
        String nameSpace;
        if (type == Plate.class)
            nameSpace = "Plate";
        else if (type == WellGroup.class)
            nameSpace = "WellGroup";
        else if (type == Well.class)
            nameSpace = "Well";
        else
            throw new IllegalArgumentException("Unknown type " + type);

        return new Lsid(nameSpace, "Folder-" + container.getRowId(), GUID.makeGUID());
    }

    /* package private */ DbScope.Transaction ensureTransaction(Lock... locks)
    {
        return AssayDbSchema.getInstance().getSchema().getScope().ensureTransaction(locks);
    }

    private int savePlateImpl(Container container, User user, @NotNull PlateImpl plate) throws Exception
    {
        return savePlateImpl(container, user, plate, false);
    }

    private int savePlateImpl(Container container, User user, @NotNull PlateImpl plate, boolean isCopy) throws Exception
    {
        return savePlateImpl(container, user, plate, isCopy, null);
    }

    private int savePlateImpl(
        Container container,
        User user,
        @NotNull PlateImpl plate,
        boolean isCopy,
        @Nullable List<Map<String, Object>> wellData
    ) throws Exception
    {
        boolean updateExisting = plate.getRowId() != null;

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            Integer plateId = plate.getRowId();

            if (!updateExisting && plate.getPlateSet() == null)
            {
                // ensure a plate set for each new plate
                PlateSetImpl plateSet = new PlateSetImpl();
                plateSet.setTemplate(plate.isTemplate());

                plate.setPlateSet(createPlateSet(container, user, plateSet, null, null));
            }

            Map<String, Object> plateRow = ObjectFactory.Registry.getFactory(PlateBean.class).toMap(PlateBean.from(plate), new ArrayListMap<>());
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
                plateId = (Integer) row.get("RowId");
                plate.setRowId(plateId);
                plate.setLsid((String) row.get("Lsid"));
                plate.setName((String) row.get("Name"));
                plate.setPlateId((String) row.get("PlateId"));
                plate.setBarcode((String) row.get("Barcode"));
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

                    wellGroupInstanceLsid = (String)insertedRows.get(0).get("Lsid");
                    wellgroup = ObjectFactory.Registry.getFactory(WellGroupImpl.class).fromMap(wellgroup, insertedRows.get(0));
                    savePropertyBag(container, user, wellGroupInstanceLsid, wellgroup.getProperties(), false);
                }
            }
            List<List<Integer>> wellGroupPositions = new LinkedList<>();
            List<Map<String, Object>> insertedRows = Collections.emptyList();

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

                        if (wellDataMap.containsKey(position.getDescription()))
                        {
                            wellDataMap.get(position.getDescription()).forEach(
                                (key, value) -> wellRow.merge(key, value, (v1, v2) -> v1)
                            );
                        }

                        wellRows.add(wellRow);
                    }
                }

                BatchValidationException wellErrors = new BatchValidationException();
                insertedRows = wellQus.insertRows(user, container, wellRows, wellErrors, null, extraScriptContext);
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

            final Integer plateRowId = plateId;
            transaction.addCommitTask(() -> {
                clearCache(container, plate);
                indexPlate(container, plateRowId, false);
            }, DbScope.CommitTaskOption.POSTCOMMIT);
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
            return Collections.emptyMap();

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
    public void deletePlate(Container container, User user, int rowId) throws Exception
    {
        Map<String, Object> key = Collections.singletonMap("RowId", rowId);
        QueryUpdateService qus = getPlateUpdateService(container, user);
        qus.deleteRows(user, container, Collections.singletonList(key), null, null);
    }

    // Called by the Plate Query Update Service after deleting a plate
    public void afterPlateDelete(Container container, Plate plate)
    {
        clearCache(container, plate);
        deindexPlates(List.of(Lsid.parse(plate.getLSID())));
    }

    // Called by the Plate Query Update Service prior to deleting a plate
    public void beforePlateDelete(Container container, Integer plateId)
    {
        requireActiveTransaction();

        Plate plate = PlateCache.getPlate(container, plateId);
        List<String> lsids = new ArrayList<>();
        lsids.add(plate.getLSID());
        for (WellGroup wellgroup : plate.getWellGroups())
            lsids.add(wellgroup.getLSID());

        SimpleFilter plateIdFilter = SimpleFilter.createContainerFilter(container);
        plateIdFilter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());

        OntologyManager.deleteOntologyObjects(container, lsids.toArray(new String[lsids.size()]));
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

    public void beforePlateSetDelete(Container container, User user, Integer rowId)
    {
        beforePlateSetsDelete(List.of(rowId));
    }

    private void beforePlateSetsDelete(Collection<Integer> plateSetIds)
    {
        requireActiveTransaction();

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

                ArrayList<Integer> emptyPlateSetIds = new SqlSelector(schema.getSchema(), emptyPlateSetsSql).getArrayList(Integer.class);

                if (!emptyPlateSetIds.isEmpty())
                {
                    beforePlateSetsDelete(emptyPlateSetIds);

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
    public void registerDetailsLinkResolver(PlateService.PlateDetailsResolver resolver)
    {
        _detailsLinkResolvers.add(resolver);
    }

    public ActionURL getDetailsURL(Plate plate)
    {
        for (PlateService.PlateDetailsResolver resolver : _detailsLinkResolvers)
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

    public @NotNull TableInfo getPlateTable(Container container, User user, @Nullable ContainerFilter cf)
    {
        return getPlateUserSchema(container, user).getTableOrThrow(PlateTable.NAME, cf);
    }

    private @NotNull TableInfo getWellTable(Container container, User user)
    {
        return getPlateUserSchema(container, user).getTableOrThrow(WellTable.NAME);
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
        requireActiveTransaction();

        var container = source.getContainer();
        var wellTable = getWellTable(container, user);

        var sourceWellData = new TableSelector(wellTable, Set.of("RowId", "LSID", "SampleId"), new SimpleFilter(FieldKey.fromParts("PlateId"), source.getRowId()), new Sort("RowId")).getMapArray();
        var copyWellData = new TableSelector(wellTable, Set.of("RowId", "LSID"), new SimpleFilter(FieldKey.fromParts("PlateId"), copy.getRowId()), new Sort("RowId")).getMapArray();

        if (sourceWellData.length != copyWellData.length)
        {
            String msg = "Failed to copy well data. Source plate \"%s\" contains %d rows of well data which does not match %d in copied plate.";
            throw new ValidationException(String.format(msg, source.getName(), sourceWellData.length, copyWellData.length));
        }

        var sourceWellLSIDS = Arrays.stream(sourceWellData).map(data -> data.get("LSID")).toList();
        var sourceFilter = new SimpleFilter(FieldKey.fromParts("LSID"), sourceWellLSIDS, CompareType.IN);

        final Set<FieldKey> wellMetadataFields;
        final Map<String, Map<String, Object>> sourceMetaData;

        var metadataTable = getPlateMetadataTable(container, user);
        if (metadataTable != null)
        {
            wellMetadataFields = metadataTable.getColumns().stream().map(ColumnInfo::getFieldKey).collect(Collectors.toSet());
            wellMetadataFields.remove(FieldKey.fromParts("LSID"));

            var metaDataRows = new TableSelector(metadataTable, sourceFilter, null).getMapCollection();
            sourceMetaData = new CaseInsensitiveHashMap<>();
            for (var row : metaDataRows)
                sourceMetaData.put((String) row.get("LSID"), row);
        }
        else
        {
            wellMetadataFields = Collections.emptySet();
            sourceMetaData = Collections.emptyMap();
        }

        List<Map<String, Object>> newWellData = new ArrayList<>();

        for (int i = 0; i < sourceWellData.length; i++)
        {
            var sourceRow = sourceWellData[i];
            String sourceWellLSID = (String) sourceRow.get("LSID");
            var copyRow = copyWellData[i];

            var updateCopyRow = new CaseInsensitiveHashMap<>();
            if (copySample && sourceRow.get("SampleId") != null)
                updateCopyRow.put("SampleId", sourceRow.get("SampleId"));

            if (sourceMetaData.containsKey(sourceWellLSID))
            {
                var sourceMetaDataRow = (Map<String, Object>) sourceMetaData.get(sourceWellLSID);

                for (var field : wellMetadataFields)
                {
                    var value = sourceMetaDataRow.get(field.toString());
                    if (value != null)
                        updateCopyRow.put(FieldKey.fromParts(field.toString()).toString(), value);
                }
            }

            if (!updateCopyRow.isEmpty())
            {
                updateCopyRow.put("RowId", copyRow.get("RowId"));
                newWellData.add(updateCopyRow);
            }
        }

        if (newWellData.isEmpty())
            return;

        var errors = new BatchValidationException();
        Map<String, Object> extraScriptContext = CaseInsensitiveHashMap.of(PLATE_COPY_FLAG, true);
        getWellUpdateService(container, user).updateRows(user, container, newWellData, null, errors, null, extraScriptContext);
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
        Integer sourcePlateRowId,
        boolean copyAsTemplate,
        @Nullable Integer destinationPlateSetRowId,
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

        try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
        {
            // Copy the plate
            PlateImpl newPlate = new PlateImpl(container, name, null, sourcePlate.getAssayType(), sourcePlate.getPlateType());
            newPlate.setCustomFields(sourcePlate.getCustomFields());
            newPlate.setDescription(description);

            if (copyAsTemplate)
                newPlate.setTemplate(true);
            else
                newPlate.setPlateSet(destinationPlateSet);

            copyProperties(sourcePlate, newPlate);
            copyWellGroups(sourcePlate, newPlate);

            // Save the plate
            int plateId = savePlateImpl(container, user, newPlate, true);
            newPlate = (PlateImpl) getPlate(container, plateId);
            if (newPlate == null)
                throw new IllegalStateException("Unexpected failure. Failed to retrieve plate after save (pre-commit).");

            // Copy plate metadata
            if (copySamples == null)
                copySamples = true;
            copyWellData(user, sourcePlate, newPlate, !newPlate.isTemplate() && copySamples);

            tx.commit();

            return newPlate;
        }
    }

    /**
     * @deprecated Use {@link #copyPlate(Container, User, Integer, boolean, Integer, String, String, Boolean)}
     */
    @Deprecated
    public Plate copyPlateDeprecated(Plate source, User user, Container destContainer)
            throws Exception
    {
        if (isDuplicatePlateName(destContainer, user, source.getName(), null))
            throw new PlateService.NameConflictException(source.getName());
        Plate newPlate = createPlate(destContainer, source.getAssayType(), source.getPlateType());
        newPlate.setName(source.getName());

        copyProperties(source, newPlate);
        copyWellGroups(source, newPlate);

        int plateId = save(destContainer, user, newPlate);
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
    private void clearCache(Collection<Integer> plateRowIds)
    {
        var table = AssayDbSchema.getInstance().getTableInfoPlate();
        SQLFragment sql = new SQLFragment("SELECT rowId, container FROM ").append(table, "")
                .append("WHERE rowId ").appendInClause(plateRowIds, table.getSqlDialect());
        Collection<Map<String, Object>> plateData = new SqlSelector(table.getSchema(), sql).getMapCollection();

        for (Map<String, Object> data : plateData)
        {
            Integer rowId = (Integer) data.get("rowId");
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

            Plate plate = PlateCache.getPlate(c, rowId);
            if (plate != null)
                PlateCache.uncache(c, plate);
        }
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

    public PlateType getPlateType(Integer plateTypeId)
    {
        if (plateTypeId == null) return null;
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateType()).getObject(plateTypeId, PlateTypeBean.class);
    }

    public @NotNull Map<String, List<Map<String, Object>>> getPlateOperationConfirmationData(
        @NotNull Container container,
        @NotNull User user,
        @NotNull Set<Integer> plateRowIds
    )
    {
        Set<Integer> permittedIds = new HashSet<>(plateRowIds);
        Set<Integer> notPermittedIds = new HashSet<>();

        ExperimentService.get().getObjectReferencers().forEach(referencer ->
                notPermittedIds.addAll(referencer.getItemsWithReferences(permittedIds, "plate")));
        permittedIds.removeAll(notPermittedIds);

        Map<Integer, Plate> plates = new HashMap<>();
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
            allowedRows.add(CaseInsensitiveHashMap.of("RowId", rowId, "Name", plate.getName(), "ContainerPath", plate.getContainer().getPath()));
        });

        List<Map<String, Object>> notAllowedRows = new ArrayList<>();
        notPermittedIds.forEach(rowId -> {
            Plate plate = plates.get(rowId);
            Map<String, Object> rowMap = new CaseInsensitiveHashMap<>();
            rowMap.put("RowId", rowId);

            if (plate != null)
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
        SearchService ss = SearchService.get();
        if (ss == null)
            return;

        Set<String> documentIds = new HashSet<>();
        for (Lsid lsid : plateLsids)
            documentIds.add(PlateDocumentProvider.getDocumentId(lsid));
        ss.deleteResources(documentIds);
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

    private void indexPlate(Container c, Integer plateRowId, boolean ignorePauseFlag)
    {
        if (_pausePlateIndex.get() && !ignorePauseFlag)
        {
            _plateIndexMap.computeIfAbsent(c, k -> new HashSet<>()).add(plateRowId);
        }
        else
        {
            Plate plate = getPlate(c, plateRowId);
            SearchService ss = SearchService.get();

            if (ss == null || plate == null)
                return;

            indexPlate(ss.defaultTask(), plate);
        }
    }

    private void indexPlate(SearchService.IndexTask task, @NotNull Plate plate)
    {
        WebdavResource resource = PlateDocumentProvider.createDocument(plate);
        task.addResource(resource, SearchService.PRIORITY.item);
    }

    public void indexPlates(SearchService.IndexTask task, Container c, @Nullable Date modifiedSince)
    {
        for (Plate plate : getPlates(c))
        {
            if (modifiedSince == null || modifiedSince.before(((PlateImpl) plate).getModified()))
                indexPlate(task, plate);
        }
    }

    /**
     * Returns the domain attached to the Well table,
     */
    public @Nullable Domain getPlateMetadataDomain(Container container, User user)
    {
        // the domain is scoped at the project level (project and subfolder scoping)
        String domainURI = PlateMetadataDomainKind.generateDomainURI(getPlateMetadataDomainContainer(container));
        return PropertyService.get().getDomain(container, domainURI);
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
    public @NotNull Domain ensurePlateMetadataDomain(Container container, User user) throws ValidationException
    {
        Domain metadataDomain = getPlateMetadataDomain(container, user);

        if (metadataDomain == null)
        {
            DomainKind<?> domainKind = PropertyService.get().getDomainKindByName(PlateMetadataDomainKind.KIND_NAME);
            Container domainContainer = getPlateMetadataDomainContainer(container);

            if (!domainKind.canCreateDefinition(user, domainContainer))
                throw new IllegalArgumentException("Unable to create the plate well domain in folder: " + domainContainer.getPath() + "\". Insufficient permissions.");

            metadataDomain = DomainUtil.createDomain(PlateMetadataDomainKind.KIND_NAME, new GWTDomain(), null, domainContainer, user, PlateMetadataDomainKind.DOMAiN_NAME, null);
        }
        return metadataDomain;
    }

    /**
     * Adds custom fields to the well domain
     */
    public @NotNull List<PlateCustomField> createPlateMetadataFields(Container container, User user, List<GWTPropertyDescriptor> fields) throws Exception
    {
        Domain metadataDomain = ensurePlateMetadataDomain(container, user);
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

                    DomainUtil.addProperty(metadataDomain, pd, new HashMap<>(), new HashSet<>(), null);
                }
                metadataDomain.save(user);
                tx.commit();
            }
        }
        return getPlateMetadataFields(container, user);
    }

    public @NotNull List<PlateCustomField> deletePlateMetadataFields(Container container, User user, List<PlateCustomField> fields) throws Exception
    {
        Domain metadataDomain = getPlateMetadataDomain(container, user);

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
        Domain metadataDomain = getPlateMetadataDomain(container, user);
        if (metadataDomain == null)
            return Collections.emptyList();

        return metadataDomain.getProperties()
                .stream()
                .map(PlateCustomField::new)
                .sorted(Comparator.comparing(k -> k.getName().toLowerCase()))
                .toList();
    }

    public @NotNull List<PlateCustomField> addFields(
        Container container,
        User user,
        Integer plateId,
        List<PlateCustomField> fields
    ) throws SQLException, ValidationException
    {
        if (plateId == null)
            throw new IllegalArgumentException("Failed to add plate custom fields. Invalid plateId provided.");

        if (fields == null || fields.isEmpty())
            throw new IllegalArgumentException("Failed to add plate custom fields. No fields specified.");

        Plate plate = requirePlate(container, plateId, "Failed to add plate custom fields.");
        PlateSet plateSet = requirePlateSet(plate, "Failed to add plate custom fields.");

        Domain domain = getPlateMetadataDomain(container, user);
        if (domain == null)
            throw new IllegalArgumentException("Failed to add plate custom fields. Custom fields domain does not exist. Try creating fields first.");

        List<DomainProperty> fieldsToAdd = new ArrayList<>();
        Set<String> existingProps = plate.getCustomFields().stream().map(PlateCustomField::getPropertyURI).collect(Collectors.toSet());

        // validate fields
        for (PlateCustomField field : fields)
        {
            DomainProperty dp = domain.getPropertyByURI(field.getPropertyURI());
            if (dp == null)
                throw new IllegalArgumentException("Failed to add plate custom field. \"" + field.getPropertyURI() + "\" does not exist on domain.");
            if (!existingProps.contains(dp.getPropertyURI()))
                fieldsToAdd.add(dp);
        }

        if (!fieldsToAdd.isEmpty())
        {
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                List<List<?>> insertedValues = new LinkedList<>();
                for (DomainProperty dp : fieldsToAdd)
                    insertedValues.add(List.of(plateSet.getRowId(), dp.getPropertyId(), dp.getPropertyURI()));

                String insertSql = "INSERT INTO " + AssayDbSchema.getInstance().getTableInfoPlateSetProperty() +
                        " (plateSetId, propertyId, propertyURI)" +
                        " VALUES (?, ?, ?)";
                Table.batchExecute(AssayDbSchema.getInstance().getSchema(), insertSql, insertedValues);

                transaction.addCommitTask(() -> PlateCache.uncache(container, plateSet), DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
        }
        return getFields(container, plateId);
    }

    public @NotNull List<PlateCustomField> getFields(Container container, Integer plateId) throws ValidationException
    {
        Plate plate = requirePlate(container, plateId, "Failed to get plate custom fields.");
        return plate.getCustomFields();
    }

    /**
     * Returns the list of custom properties associated with a plate
     */
    private List<DomainProperty> getPlateMetadataDomainProperties(@NotNull Domain plateMetadataDomain, Integer plateId)
    {
        AssayDbSchema schema = AssayDbSchema.getInstance();
        SQLFragment sql = new SQLFragment("SELECT PropertyURI FROM ").append(schema.getTableInfoPlateSetProperty(), "PP")
                .append(" INNER JOIN ").append(schema.getTableInfoPlate(), "PL").append(" ON PL.PlateSet = PP.PlateSetId")
                .append(" WHERE PL.RowId = ?").add(plateId);

        List<DomainProperty> fields = new ArrayList<>();
        for (String uri : new SqlSelector(schema.getSchema(), sql).getArrayList(String.class))
        {
            DomainProperty dp = plateMetadataDomain.getPropertyByURI(uri);
            if (dp == null)
                throw new IllegalArgumentException("Failed to get plate custom field. \"" + uri + "\" does not exist on domain.");

            fields.add(dp);
        }
        return fields;
    }

    public @NotNull List<WellCustomField> getWellCustomFields(User user, Plate plate, Integer wellId)
    {
        Well well = plate.getWell(wellId);
        if (well == null)
            throw new IllegalArgumentException("Failed to get well custom fields. Well id \"" + wellId   + "\" not found.");

        Domain domain = getPlateMetadataDomain(plate.getContainer(), user);
        if (domain == null)
            return Collections.emptyList();

        List<WellCustomField> fields = getPlateMetadataDomainProperties(domain, plate.getRowId()).stream().map(WellCustomField::new).toList();
        if (fields.isEmpty())
            return Collections.emptyList();

        // merge in any well metadata values
        Map<FieldKey, WellCustomField> customFieldMap = new HashMap<>();
        for (WellCustomField customField : fields)
            customFieldMap.put(FieldKey.fromParts(customField.getName()), customField);
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("rowId"), wellId);

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

    public List<PlateCustomField> removeFields(Container container, User user, Integer plateId, List<PlateCustomField> fields) throws ValidationException
    {
        Plate plate = requirePlate(container, plateId, "Failed to remove plate custom fields.");
        PlateSet plateSet = requirePlateSet(plate, "Failed to remove plate custom fields.");

        Domain domain = getPlateMetadataDomain(container, user);
        if (domain == null)
            throw new IllegalArgumentException("Failed to remove plate custom fields. Custom fields domain does not exist. Try creating fields first.");

        List<DomainProperty> fieldsToRemove = new ArrayList<>();
        // validate fields
        for (PlateCustomField field : fields)
        {
            DomainProperty dp = domain.getPropertyByURI(field.getPropertyURI());
            if (dp == null)
                throw new IllegalArgumentException("Failed to remove plate custom field. \"" + field.getPropertyURI() + "\" does not exist on domain.");

            fieldsToRemove.add(dp);
        }

        if (!fieldsToRemove.isEmpty())
        {
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                List<String> propertyURIs = fieldsToRemove.stream().map(DomainProperty::getPropertyURI).toList();
                Set<String> existingProps = plate.getCustomFields().stream().map(PlateCustomField::getPropertyURI).collect(Collectors.toSet());
                for (DomainProperty dp : fieldsToRemove)
                {
                    if (!existingProps.contains(dp.getPropertyURI()))
                        throw new IllegalArgumentException(String.format("Failed to remove plate custom fields. Custom field \"%s\" is not currently associated with this plate.", dp.getName()));
                }

                SQLFragment sql = new SQLFragment("DELETE FROM ").append(AssayDbSchema.getInstance().getTableInfoPlateSetProperty(), "")
                        .append(" WHERE PlateSetId = ? ").add(plateSet.getRowId())
                        .append(" AND PropertyURI ").appendInClause(propertyURIs, AssayDbSchema.getInstance().getSchema().getSqlDialect());

                new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql);

                transaction.addCommitTask(() -> PlateCache.uncache(container, plateSet), DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
        }
        return getFields(container, plateId);
    }

    public List<PlateCustomField> setFields(Container container, User user, Integer plateRowId, List<PlateCustomField> fields) throws SQLException, ValidationException
    {
        requirePlate(container, plateRowId, "Failed to set plate custom fields.");

        List<PlateCustomField> allFields = getPlateMetadataFields(container, user);
        Set<PlateCustomField> currentFields = new HashSet<>(getFields(container, plateRowId));

        Set<PlateCustomField> desiredFields = new HashSet<>();
        List<PlateCustomField> fieldsToAdd = new ArrayList<>();
        List<PlateCustomField> fieldsToRemove = new ArrayList<>();

        for (PlateCustomField partialField : fields)
        {
            Optional<PlateCustomField> opt = allFields.stream().filter(f -> f.getName().equals(partialField.getName()) || f.getPropertyURI().equals(partialField.getPropertyURI())).findFirst();
            if (opt.isEmpty())
                throw new IllegalArgumentException("Failed to set plate custom fields. Unable to resolve field with (name, propertyURI) (%s, %s)".formatted(partialField.getName(), partialField.getPropertyURI()));

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
    public record PlateData(String name, Integer plateType, Integer templateId, String barcode, List<Map<String, Object>> data) {}

    private List<Plate> addPlatesToPlateSet(
        Container container,
        User user,
        int plateSetId,
        boolean plateSetIsTemplate,
        @NotNull List<PlateData> plates
    ) throws Exception
    {
        if (plates.isEmpty())
            return Collections.emptyList();

        try (DbScope.Transaction tx = ensureTransaction())
        {
            pausePlateIndexing();
            tx.addCommitTask(this::resumePlateIndexing, DbScope.CommitTaskOption.POSTCOMMIT, DbScope.CommitTaskOption.POSTROLLBACK);

            List<Plate> platesAdded = new ArrayList<>();

            for (var plate : plates)
            {
                var plateType = requirePlateType(plate.plateType, "Failed to add plates to plate set.");
                var plateImpl = new PlateImpl(container, plate.name, plate.barcode, plateType);
                plateImpl.setTemplate(plateSetIsTemplate);

                // TODO: Write a cheaper plate create/save for multiple plates
                platesAdded.add(createAndSavePlate(container, user, plateImpl, plateSetId, plate.data));
            }

            tx.commit();

            return platesAdded;
        }
    }

    public PlateSetImpl createPlateSet(
        Container container,
        User user,
        @NotNull PlateSetImpl plateSet,
        @Nullable List<PlateData> plates,
        @Nullable Integer parentPlateSetId
    ) throws Exception
    {
        if (!container.hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("Failed to create plate set. Insufficient permissions.");

        if (!plateSet.isNew())
            throw new ValidationException(String.format("Failed to create plate set. Cannot create plate set with rowId (%d).", plateSet.getRowId()));

        if (plates != null && plates.size() > MAX_PLATES)
            throw new ValidationException(String.format("Failed to create plate set. Plate sets can have a maximum of %d plates.", MAX_PLATES));

        PlateSetImpl parentPlateSet = null;
        if (parentPlateSetId != null)
        {
            if (plateSet.isTemplate())
                throw new ValidationException("Failed to create plate set. Template plate sets do not support specifying a parent plate set.");
            parentPlateSet = (PlateSetImpl) getPlateSet(getPlateLookupContainerFilter(container, user), parentPlateSetId);
            if (parentPlateSet == null)
                throw new ValidationException(String.format("Failed to create plate set. Parent plate set with rowId (%d) is not available.", parentPlateSetId));
            if (parentPlateSet.isTemplate())
                throw new ValidationException(String.format("Failed to create plate set. Parent plate set with \"%s\" is a template plate set. Template plate sets are not supported as a parent plate set.", parentPlateSet.getName()));
            if (parentPlateSet.getRootPlateSetId() == null)
                throw new ValidationException(String.format("Failed to create plate set. Parent plate set with rowId (%d) does not have a root plate set specified.", parentPlateSetId));
        }

        if (plateSet.getType() == null)
            plateSet.setType(PlateSetType.assay);

        try (DbScope.Transaction tx = ensureTransaction())
        {
            BatchValidationException errors = new BatchValidationException();
            QueryUpdateService qus = getPlateSetUpdateService(container, user);

            Map<String, Object> plateSetRow = ObjectFactory.Registry.getFactory(PlateSetImpl.class).toMap(plateSet, new ArrayListMap<>());
            List<Map<String, Object>> rows = qus.insertRows(user, container, Collections.singletonList(plateSetRow), errors, null, null);
            if (errors.hasErrors())
                throw errors;

            Integer plateSetId = (Integer) rows.get(0).get("RowId");

            savePlateSetHeritage(plateSetId, plateSet.getType(), parentPlateSet);

            if (plates != null)
                addPlatesToPlateSet(container, user, plateSetId, plateSet.isTemplate(), plates);

            plateSet = (PlateSetImpl) getPlateSet(container, plateSetId);
            tx.commit();
        }

        return plateSet;
    }

    public PlateSet replatePlateSet(
        Container container,
        User user,
        @NotNull PlateSetImpl plateSet,
        Integer sourcePlateSetRowId
    ) throws Exception
    {
        PlateSetImpl parentPlateSet = (PlateSetImpl) requirePlateSet(container, sourcePlateSetRowId, "Failed to create plate set.");

        Integer parentId = parentPlateSet.isStandalone() ? null : parentPlateSet.getRowId();

        try (DbScope.Transaction tx = ensureTransaction())
        {
            PlateSet newPlateSet = createPlateSet(container, user, plateSet, null, parentId);

            for (Plate plate : parentPlateSet.getPlates())
                copyPlate(container, user, plate.getRowId(), false, newPlateSet.getRowId(), null, null, true);

            tx.commit();

            return getPlateSet(container, newPlateSet.getRowId());
        }
    }

    private void savePlateSetHeritage(Integer plateSetId, PlateSetType plateSetType, @Nullable PlateSetImpl parentPlateSet)
    {
        requireActiveTransaction();

        // Configure rootPlateSetId
        Integer rootPlateSetId = null;
        if (PlateSetType.primary.equals(plateSetType))
            rootPlateSetId = parentPlateSet == null ? plateSetId : parentPlateSet.getRootPlateSetId();
        else if (PlateSetType.assay.equals(plateSetType))
            rootPlateSetId = parentPlateSet == null ? null : parentPlateSet.getRootPlateSetId();

        // Configure primaryPlateSetId
        Integer primaryPlateSetId = null;
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

    public void archive(Container container, User user, @Nullable List<Integer> plateSetIds, @Nullable List<Integer> plateIds, boolean archive) throws Exception
    {
        boolean archivingPlates = plateIds != null && !plateIds.isEmpty();
        boolean archivingPlateSets = plateSetIds != null && !plateSetIds.isEmpty();

        if (!archivingPlates && !archivingPlateSets)
            throw new ValidationException(String.format("Failed to %s. Neither plates nor plate sets were specified.", getArchiveAction(archive)));

        try (DbScope.Transaction tx = ensureTransaction())
        {
            if (archivingPlates)
            {
                archive(container, user, AssayDbSchema.getInstance().getTableInfoPlate(), "plates", plateIds, archive);
                tx.addCommitTask(() -> clearCache(plateIds), DbScope.CommitTaskOption.POSTCOMMIT);
            }

            if (archivingPlateSets)
                archive(container, user, AssayDbSchema.getInstance().getTableInfoPlateSet(), "plate sets", plateSetIds, archive);

            tx.commit();
        }
    }

    private void archive(Container container, User user, @NotNull TableInfo table, String type, @NotNull List<Integer> rowIds, boolean archive) throws Exception
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

    public PlateSetLineage getPlateSetLineage(Container container, User user, int seedPlateSetId, @Nullable ContainerFilter cf)
    {
        cf = ensureContainerFilterForLineage(container, user, cf);
        PlateSetImpl seedPlateSet = (PlateSetImpl) getPlateSet(cf, seedPlateSetId);
        if (seedPlateSet == null)
            throw new NotFoundException();

        PlateSetLineage lineage = new PlateSetLineage(seedPlateSetId);
        Integer rootPlateSetId = seedPlateSet.getRootPlateSetId();

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

        Set<Integer> nodeIds = new HashSet<>();
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

        Map<Integer, PlateSet> plateSets = new HashMap<>();
        for (var node : nodes)
            plateSets.put(node.getRowId(), node);
        lineage.setPlateSets(plateSets);

        return lineage;
    }

    private Collection<Integer> getResultRowsIds(@Nullable List<Integer> resultRowIds, @Nullable String resultSelectionKey)
    {
        if (resultRowIds != null && !resultRowIds.isEmpty())
            return new ArrayList<>(new HashSet<>(resultRowIds));
        if (StringUtils.trimToNull(resultSelectionKey) != null)
            return getSelection(resultSelectionKey);

        return Collections.emptyList();
    }

    private Collection<Integer> getSelection(@NotNull String selectionKey)
    {
        ViewContext viewContext = HttpView.currentContext();
        if (viewContext == null)
            throw new IllegalStateException("Unable to resolve ViewContext to acquire selection key.");

        return DataRegionSelection.getSelectedIntegers(viewContext, selectionKey, false);
    }

    public void markHits(
        Container container,
        User user,
        int protocolId,
        boolean markAsHit,
        @Nullable List<Integer> resultRowIds,
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

        Collection<Integer> rowIds = getResultRowsIds(resultRowIds, resultSelectionKey);
        if (rowIds.isEmpty())
            return;

        try (var tx = ensureTransaction())
        {
            TableInfo hitTable = AssayDbSchema.getInstance().getTableInfoHit();

            if (markAsHit)
            {
                // Exclude preexisting hits
                {
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ResultId"), rowIds, CompareType.IN);
                    filter.addCondition(FieldKey.fromParts("ProtocolId"), protocol.getRowId());

                    Set<Integer> preexistingHits = new HashSet<>(new TableSelector(hitTable, Collections.singleton("ResultId"), filter, null).getArrayList(Integer.class));
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
                    Map<Integer, Pair<GUID, String>> cache = new HashMap<>();
                    for (var row : selector.getMapCollection())
                    {
                        Integer resultId = (Integer) row.get("RowId");
                        String wellLsid = (String) row.get("WellLsid");
                        if (wellLsid == null)
                            throw new ValidationException(String.format("Failed to mark hits. \"%s\" result (Row Id %d) is not related to a plate well. Only plate well related results can be marked as hits.", protocol.getName(), resultId));

                        Integer plateId = (Integer) row.get("Plate");
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

                            PlateSetLineage lineage = getPlateSetLineage(container, user, plateSet.getRowId(), ContainerFilter.EVERYTHING);
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

    private void deleteHits(SimpleFilter filter)
    {
        Table.delete(AssayDbSchema.getInstance().getTableInfoHit(), filter);
    }

    private void deleteHits(FieldKey fieldKey, Collection<? extends ExpObject> objects)
    {
        if (objects == null || objects.isEmpty())
            return;

        deleteHits(new SimpleFilter(fieldKey, objects.stream().map(ExpObject::getRowId).toList(), CompareType.IN));
    }

    private void deleteHits(int protocolId, Collection<Integer> resultIds)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ProtocolId"), protocolId);
        filter.addCondition(FieldKey.fromParts("ResultId"), resultIds, CompareType.IN);
        deleteHits(filter);
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
    }

    @Override
    public void beforeResultDelete(Container container, User user, ExpRun run, Map<String, Object> resultRow)
    {
        AssayProvider provider = AssayManager.get().getProvider(run);
        if (provider == null || !provider.isPlateMetadataEnabled(run.getProtocol()))
            return;

        deleteHits(run.getProtocol().getRowId(), List.of((Integer) resultRow.get("RowId")));
    }

    /**
     * Returns a PlateSetAssays model for all plate enabled GPAT assays for a given container and containerFilter that
     * have data associated with a given plateSetId or its descendents.
     */
    public PlateSetAssays getPlateSetAssays(Container container, User user, int plateSetId, @Nullable ContainerFilter cf)
    {
        PlateSetAssays plateSetAssays = new PlateSetAssays();
        // Get the list of GPAT protocols in the container
        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);

        if (provider == null)
            return plateSetAssays;

        cf = ensureContainerFilterForLineage(container, user, cf);
        PlateSetLineage lineage = getPlateSetLineage(container, user, plateSetId, cf);
        Map<Integer, List<Integer>> protocolPlateSets = new HashMap<>();
        Map<Integer, PlateSet> plateSets = lineage.getPlateSetAndDescendents(plateSetId);
        plateSetAssays.setPlateSets(plateSets);
        TableInfo plateTable = getPlateTable(container, user, cf);
        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(container, provider)
                .stream().filter(provider::isPlateMetadataEnabled).toList();

        for (ExpProtocol protocol : protocols)
        {
            AssayProtocolSchema assayProtocolSchema = provider.createProtocolSchema(user, protocol.getContainer(), protocol, null);
            TableInfo assayDataTable = assayProtocolSchema.createDataTable(ContainerFilter.EVERYTHING, false);

            if (assayDataTable != null)
            {
                // Query for the distinct set of plate sets that have data in their results domain for the given assay
                SQLFragment sql = new SQLFragment("SELECT DISTINCT pt.plateset FROM ")
                        .append(assayDataTable, "ad")
                        .append(" JOIN ")
                        .append(plateTable, "pt")
                        .append(" ON ad.plate = pt.rowId WHERE pt.plateset ")
                        .appendInClause(plateSets.keySet(), assayDataTable.getSqlDialect());
                ArrayList<Integer> plateSetRowIds = new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(Integer.class);

                if (!plateSetRowIds.isEmpty())
                    protocolPlateSets.put(protocol.getRowId(), plateSetRowIds);
            }
        }

        plateSetAssays.setProtocolPlateSets(protocolPlateSets);

        return plateSetAssays;
    }

    public void validatePrimaryPlateSetUniqueSamples(Set<Integer> wellRowIds, BatchValidationException errors)
    {
        if (wellRowIds.isEmpty())
            return;

        AssayDbSchema dbSchema = AssayDbSchema.getInstance();
        SqlDialect dialect = dbSchema.getSchema().getSqlDialect();
        TableInfo plateTable = dbSchema.getTableInfoPlate();
        TableInfo plateSetTable = dbSchema.getTableInfoPlateSet();
        TableInfo wellTable = dbSchema.getTableInfoWell();

        // Determines the set of primary plate sets that are being touched from the collection of well rowIds
        SQLFragment primaryPlateSetsFromWellRowIdsSQL = new SQLFragment("SELECT PS.RowId FROM ").append(wellTable, "W")
                .append(" INNER JOIN ").append(plateTable, "P").append(" ON P.RowId = W.PlateId")
                .append(" INNER JOIN ").append(plateSetTable, "PS").append(" ON PS.RowId = P.PlateSet")
                .append(" WHERE PS.Type = ?").add("primary").append(" AND W.RowId ").appendInClause(wellRowIds, dialect);

        // From the set of primary plate sets determine if any sample exists in more than one well within the entire plate set
        SQLFragment nonUniqueSamplesPerPrimaryPlateSetSQL = new SQLFragment("SELECT PS.Name AS PlateSetName, M.Name AS SampleName FROM ")
                .append(wellTable, "W")
                .append(" INNER JOIN ").append(plateTable, "P").append(" ON P.RowId = W.PlateId")
                .append(" INNER JOIN ").append(plateSetTable, "PS").append(" ON PS.RowId = P.PlateSet")
                .append(" LEFT JOIN ").append(ExperimentService.get().getTinfoMaterial(), "M").append(" ON M.RowId = W.SampleId")
                .append(" WHERE W.SampleId IS NOT NULL AND PS.RowId IN (").append(primaryPlateSetsFromWellRowIdsSQL).append(")")
                .append(" GROUP BY PS.RowId, M.Name, W.SampleId, PS.Name HAVING COUNT(W.SampleId) > 1");

        var duplicates = new SqlSelector(dbSchema.getSchema(), nonUniqueSamplesPerPrimaryPlateSetSQL).getMapCollection();

        for (var duplicate : duplicates)
        {
            errors.addRowError(new ValidationException(String.format("Sample \"%s\" is recorded in more than one well in Primary Plate Set \"%s\".", duplicate.get("SampleName"), duplicate.get("PlateSetName"))));
        }
    }

    private void requireActiveTransaction()
    {
        if (!AssayDbSchema.getInstance().getSchema().getScope().isTransactionActive())
            throw new IllegalStateException("This method must be called from within a transaction");
    }

    Pair<Integer, List<Map<String, Object>>> getWellSampleData(
        Container c,
        @NotNull List<Integer> sampleIds,
        Integer rowCount,
        Integer columnCount,
        int sampleIdsCounter
    )
    {
        if (sampleIds.isEmpty())
            throw new IllegalArgumentException("No samples are in the current selection.");

        List<Map<String, Object>> wellSampleDataForPlate = new ArrayList<>();
        for (int rowIdx = 0; rowIdx < rowCount; rowIdx++)
        {
            for (int colIdx = 0; colIdx < columnCount; colIdx++)
            {
                if (sampleIdsCounter >= sampleIds.size())
                    return Pair.of(sampleIdsCounter, wellSampleDataForPlate);

                wellSampleDataForPlate.add(CaseInsensitiveHashMap.of(
                    WellTable.Column.SampleId.name(), sampleIds.get(sampleIdsCounter),
                    WellTable.Column.Type.name(), WellGroup.Type.SAMPLE.name(),
                    WELL_LOCATION, createPosition(c, rowIdx, colIdx).getDescription()
                ));
                sampleIdsCounter++;
            }
        }

        return Pair.of(sampleIdsCounter, wellSampleDataForPlate);
    }

    /** Prepares the plate data for plates that specify a "templateId". */
    public List<PlateData> preparePlateData(Container container, User user, Collection<PlateData> plates)
    {
        if (plates == null || plates.isEmpty())
            return Collections.emptyList();

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
                    WellTable.Column.Type.name(), WellGroup.Type.SAMPLE.name(),
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
    public List<PlateData> reArrayFromSelection(
        Container container,
        User user,
        List<PlateData> plates,
        @NotNull String selectionKey
    ) throws ValidationException
    {
        List<Integer> selectedSampleIds = getSelection(selectionKey).stream().sorted().toList();
        if (selectedSampleIds.isEmpty())
            throw new ValidationException("Failed to generate plate data. No samples selected.");

        int sampleIdsCounter = 0;
        List<PlateData> platesData = new ArrayList<>();
        Map<Integer, PlateType> plateTypes = new HashMap<>();
        Map<Pair<WellGroup.Type, String>, Integer> groupSampleMap = new HashMap<>();

        for (PlateData plate : plates)
        {
            int plateTypeId = plate.plateType;
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
                pair = getWellSampleData(container, selectedSampleIds, plateType.getRows(), plateType.getColumns(), sampleIdsCounter);
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
        List<Integer> sampleIds,
        Map<Pair<WellGroup.Type, String>, Integer> groupSampleMap,
        int counter
    )
    {
        if (counter >= sampleIds.size())
            return counter;

        for (WellData wellData : wellDataList)
        {
            boolean isSampleWell = WellGroup.Type.SAMPLE.equals(wellData.getType());
            boolean isReplicateWell = WellGroup.Type.REPLICATE.equals(wellData.getType());
            boolean isSampleOrReplicate = isSampleWell || isReplicateWell;

            Pair<WellGroup.Type, String> groupKey = null;
            if (isSampleOrReplicate && wellData.getWellGroup() != null)
            {
                WellGroup.Type type = isSampleWell ? WellGroup.Type.SAMPLE : WellGroup.Type.REPLICATE;
                groupKey = Pair.of(type, wellData.getWellGroup());
            }

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
                Integer sampleId = sampleIds.get(counter);

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

    public void getPlateSetExportFile(String fileName, ColumnDescriptor[] cols, List<Object[]> rows, PlateController.FileType fileType, HttpServletResponse response) throws IOException
    {
        boolean isCSV = fileType.equals(PlateController.FileType.CSV);
        boolean isTSV = fileType.equals(PlateController.FileType.TSV);
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
            xlWriter.setFullFileName(fileName);
            xlWriter.renderWorkbook(response);
        }
    }

    public List<Object[]> getWorklist(
        int sourcePlateSetId,
        int destinationPlateSetId,
        List<FieldKey> sourceIncludedMetadataCols,
        List<FieldKey> destinationIncludedMetadataCols,
        Container c,
        User u
    ) throws RuntimeSQLException
    {
        TableInfo wellTable = getWellTable(c, u);
        return new PlateSetExport().getWorklist(wellTable, sourcePlateSetId, destinationPlateSetId, sourceIncludedMetadataCols, destinationIncludedMetadataCols);
    }

    public List<Object[]> getInstrumentInstructions(int plateSetId, List<FieldKey> includedMetadataCols, Container c, User u)
    {
        TableInfo wellTable = getWellTable(c, u);
        return new PlateSetExport().getInstrumentInstructions(wellTable, plateSetId, includedMetadataCols);
    }

    private List<FieldKey> getPlateExportFieldKeys(Plate plate, boolean isMapView)
    {
        List<FieldKey> fieldKeys = new ArrayList<>(List.of(FieldKey.fromParts("SampleId", "Name")));

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
        for (PlateCustomField customField : plate.getCustomFields())
        {
            fieldKeys.add(FieldKey.fromParts(customField.getName()));
        }

        return fieldKeys;
    }

    public QueryView getPlateQueryView(Container container, User user, ContainerFilter cf, Plate plate, boolean isMapView)
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

        // Filter on isQueryColumn so we don't get the details or update columns
        return dataRegion.getDisplayColumns().stream().filter(DisplayColumn::isQueryColumn).toList();
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

                try (TSVGridWriter writer = new TSVGridWriter(plateQueryView::getResults, displayColumns, Collections.singletonMap("SampleId/Name", "Sample Id")))
                {
                    writer.setDelimiterCharacter(delim);
                    writer.setColumnHeaderType(ColumnHeaderType.FieldKey);
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

    private List<WellData> getWellData(Container container, User user, int plateRowId, boolean includeSamples, boolean includeMetadata)
    {
        Set<String> columns = new HashSet<>();
        columns.add(WellTable.Column.Col.name());
        columns.add(WellTable.Column.Lsid.name());
        columns.add(WellTable.Column.Position.name());
        columns.add(WellTable.Column.Row.name());
        columns.add(WellTable.Column.RowId.name());
        columns.add(WellTable.Column.Type.name());
        columns.add(WellTable.Column.WellGroup.name());
        if (includeSamples)
            columns.add(WellTable.Column.SampleId.name());

        var wellTable = getWellTable(container, user);
        var filter = new SimpleFilter(FieldKey.fromParts(WellTable.Column.PlateId.name()), plateRowId);
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

        var filter = new SimpleFilter(FieldKey.fromParts(WellTable.Column.Lsid.name()), wellLsids, CompareType.IN);
        var metadataMap = new HashMap<String, Map<String, Object>>();
        var ignoredKeys = CaseInsensitiveHashSet.of("_row", WellTable.Column.Lsid.name());

        new TableSelector(metadataTable, filter, null).getMapCollection().forEach(row -> {
            var lsid = (String) row.get(WellTable.Column.Lsid.name());
            var metadata = new CaseInsensitiveHashMap<>();

            for (var key : row.keySet())
            {
                if (row.get(key) != null && !ignoredKeys.contains(key))
                    metadata.put(key, row.get(key));
            }

            if (!metadata.isEmpty())
                metadataMap.put(lsid, metadata);
        });

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

    public record WellGroupChange(Integer plateRowId, Integer wellRowId, String type, String group) {}

    /**
     * Computes the well groups based on changes (updates) made to the well "Type" and "Group".
     * This is invoked whenever rows are inserted or updated in the assay.Well table.
     */
    public void computeWellGroups(
        Container container,
        User user,
        Map<Integer, Map<Integer, WellGroupChange>> wellGroupChanges
    ) throws ValidationException
    {
        requireActiveTransaction();

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

                Integer wellRowId = wellData.getRowId();
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
                }

                // Type/Group are not set and are not being updated
                if (type == null && wellGroup == null)
                    continue;

                var position = plate.getPosition(wellData.getRow(), wellData.getCol());

                // Specifying a group requires that a type is also specified
                if (type == null)
                {
                    throw new ValidationException(String.format(
                        "Well %s must specify a \"%s\" when a \"%s\" is specified.",
                        position.getDescription(),
                        WellTable.Column.Type.name(),
                        WellTable.Column.WellGroup.name()
                    ));
                }

                var wellGroupKey = Pair.of(type, wellGroup);
                wellGroupings.computeIfAbsent(wellGroupKey, k -> new ArrayList<>()).add(position);
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

    public void validateWellGroups(Container container, User user, Collection<Integer> plateRowIds) throws ValidationException
    {
        clearCache(plateRowIds);
        Set<Integer> plateSetsWithReplicates = new HashSet<>();

        for (var plateRowId : plateRowIds)
        {
            var plate = requirePlate(container, plateRowId, "Failed to validate well groups.");
            if (!TsvPlateLayoutHandler.TYPE.equalsIgnoreCase(plate.getAssayType()))
                continue;

            for (var wellGroup : plate.getWellGroups())
            {
                switch (wellGroup.getType())
                {
                    case REPLICATE -> {
                        if (wellGroup.isZone())
                            throw new ValidationException(String.format("Replicates must specify a \"%s\".", WellTable.Column.WellGroup.name()));

                        var plateSet = plate.getPlateSet();
                        if (plateSet != null)
                            plateSetsWithReplicates.add(plateSet.getRowId());
                    }
                    case CONTROL, NEGATIVE_CONTROL, POSITIVE_CONTROL, SAMPLE -> validateWellGroup(plate, wellGroup);
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

        if (!plateSetsWithReplicates.isEmpty())
        {
            for (var plateSetId : plateSetsWithReplicates.stream().sorted().toList())
                validatePlateSetReplicates(container, user, plateSetId);
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
                    WellTable.Column.SampleId.name(),
                    plate.getName()
                ));
            }

            if (wellGroups.size() > 1)
            {
                throw new ValidationException(String.format(
                    "Well %s is included in more than one well group. This is not supported for assay type \"%s\" plate \"%s\".",
                    position.getDescription(),
                    TsvPlateLayoutHandler.TYPE,
                    plate.getName()
                ));
            }
        }
    }

    private long getReplicateGroupCount(@NotNull UserSchema plateSchema, @NotNull Integer plateSetRowId)
    {
        String labkeySql = "SELECT DISTINCT Type, WellGroup FROM plate.Well WHERE" +
                " PlateId.PlateSet.RowId = " + plateSetRowId +
                " AND Type = " + LabKeySql.quoteString(WellGroup.Type.REPLICATE.name());

        return QueryService.get().getSelectBuilder(plateSchema, labkeySql).buildSqlSelector(null).getRowCount();
    }

    private String getReplicateGroupLabKeySql(@NotNull UserSchema plateSchema, @NotNull Integer plateSetRowId)
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

        return "SELECT\n " + columnsSql + "FROM plate.Well\n WHERE"
                + " PlateId.PlateSet.RowId = " + plateSetRowId
                + " AND Type = " + LabKeySql.quoteString(WellGroup.Type.REPLICATE.name()) + "\n"
                + " GROUP BY\n" + columnsSql;
    }

    private void validatePlateSetReplicates(Container container, User user, @NotNull Integer plateSetRowId) throws ValidationException
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
                String groupName = StringUtils.trimToNull(results.getString(WellTable.Column.WellGroup.name()));
                if (groupName == null)
                    continue;

                if (groups.contains(groupName))
                    throw new ValidationException(String.format("Replicate group \"%s\" contains mismatched well data. Ensure all data aligns for the replicates declared in these wells.", groupName));

                groups.add(groupName);
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        // Fallback to more generic message if we did not resolve a specific mismatch
        throw new ValidationException(String.format("Plate set (%d) contains mismatched replicate well data.", plateSetRowId));
    }

    private void validateWellGroup(Plate plate, WellGroup wellGroup) throws ValidationException
    {
        if (wellGroup.isZone())
            return;

        // TODO: Handle the warning "Attempt to update table 'Well' with no valid fields." when only editing type.

        Integer sampleId = null;
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
    public record ReformatResult(List<PlateData> previewData, Integer plateCount, Integer plateSetRowId, String plateSetName, List<Integer> plateRowIds) {}

    /**
     * Reformat a set of source plates to new plates via a reformat operation (e.g. quadrant, stamp, etc.).
     * @return The return ReformatResult will contain different data when previewing versus saving (not previewing).
     * - preview:
     *      The previewData contains the preview data. Null if the "previewData" flag is false.
     *      The plateCount is the count of the number of plates that will be created.
     *      Both plateSetRowId, plateSetName and plateRowIds will be null.
     * - saving (not preview):
     *      The previewData is null.
     *      The plateCount is the count of the number of plates that have been created.
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

        PlateSetImpl destinationPlateSet = getReformatDestinationPlateSet(container, options);
        Pair<PlateSet, List<Plate>> source = getReformatSourcePlates(container, options);
        PlateSetImpl sourcePlateSet = (PlateSetImpl) source.first;
        List<Plate> sourcePlates = source.second;

        PlateType targetPlateType = null;
        if (options.getTargetPlateTypeId() != null)
            targetPlateType = requirePlateType(options.getTargetPlateTypeId(), null);

        LayoutEngine engine = new LayoutEngine(options, sourcePlates, targetPlateType, getPlateTypes());

        List<WellLayout> wellLayouts = engine.run();
        int availablePlateCount = destinationPlateSet.availablePlateCount();

        if (availablePlateCount < wellLayouts.size())
        {
            throw new ValidationException(String.format(
                "This plate set has space for %d more plates. This operation will generate %d plates.",
                availablePlateCount,
                wellLayouts.size()
            ));
        }

        List<PlateData> plateData = hydratePlateDataFromWellLayout(container, user, wellLayouts, engine.getOperation());

        if (options.isPreview())
            return new ReformatResult(options.isPreviewData() ? plateData : null, plateData.size(), null, null, null);

        if (plateData.isEmpty())
            throw new ValidationException("This operation as configured does not create any plates.");

        Integer plateSetRowId;
        String plateSetName;
        List<Plate> newPlates;

        if (destinationPlateSet.isNew())
        {
            PlateSet newPlateSet = createPlateSet(container, user, destinationPlateSet, plateData, getReformatParentPlateSetId(sourcePlateSet));
            plateSetRowId = newPlateSet.getRowId();
            plateSetName = newPlateSet.getName();
            newPlates = newPlateSet.getPlates();
        }
        else
        {
            plateSetRowId = destinationPlateSet.getRowId();
            plateSetName = destinationPlateSet.getName();
            newPlates = addPlatesToPlateSet(container, user, plateSetRowId, destinationPlateSet.isTemplate(), plateData);
        }

        List<Integer> plateRowIds = newPlates.stream().map(Plate::getRowId).toList();
        return new ReformatResult(null, plateRowIds.size(), plateSetRowId, plateSetName, plateRowIds);
    }

    private @Nullable Integer getReformatParentPlateSetId(@NotNull PlateSet sourcePlateSet)
    {
        if (sourcePlateSet.isPrimary() || !sourcePlateSet.isStandalone())
            return sourcePlateSet.getRowId();
        return null;
    }

    private @NotNull List<Integer> getReformatPlateRowIds(ReformatOptions options) throws ValidationException
    {
        boolean hasPlateRowIds = options.getPlateRowIds() != null && !options.getPlateRowIds().isEmpty();

        String selectionKey = StringUtils.trimToNull(options.getPlateSelectionKey());
        boolean hasPlateSelectionKey = selectionKey != null;

        if (hasPlateRowIds && hasPlateSelectionKey)
            throw new ValidationException("Either \"plateRowIds\" or \"plateSelectionKey\" can be specified but not both.");
        else if (!hasPlateRowIds && !hasPlateSelectionKey)
            throw new ValidationException("Either \"plateRowIds\" or \"plateSelectionKey\" must be specified.");

        List<Integer> plateRowIds;
        if (hasPlateRowIds)
            plateRowIds = options.getPlateRowIds();
        else
            plateRowIds = getSelection(selectionKey).stream().toList();

        if (plateRowIds.isEmpty())
            throw new ValidationException("No source plates are specified.");

        for (Integer plateRowId : plateRowIds)
        {
            if (plateRowId == null)
                throw new ValidationException("An invalid null plate row id was specified.");
            else if (plateRowId < 1)
                throw new ValidationException(String.format("An invalid plate row id (%d) was specified.", plateRowId));
        }

        return plateRowIds;
    }

    private @NotNull PlateSetImpl getReformatDestinationPlateSet(Container container, ReformatOptions options) throws ValidationException
    {
        ReformatOptions.ReformatPlateSet targetPlateSetOptions = options.getTargetPlateSet();
        if (targetPlateSetOptions == null)
            throw new ValidationException("A \"targetPlateSet\" must be specified.");

        boolean hasRowId = targetPlateSetOptions.getRowId() != null && targetPlateSetOptions.getRowId() > 0;
        boolean hasType = targetPlateSetOptions.getType() != null;

        if (hasRowId && hasType)
            throw new ValidationException("Either a \"rowId\" or a \"type\" can be specified for \"targetPlateSet\" but not both.");
        else if (!hasRowId && !hasType)
            throw new ValidationException("Either a \"rowId\" or a \"type\" must be specified for \"targetPlateSet\".");

        PlateSetImpl plateSet;
        if (hasRowId)
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
        }

        return plateSet;
    }

    private Pair<PlateSet, List<Plate>> getReformatSourcePlates(Container container, ReformatOptions options) throws ValidationException
    {
        List<Plate> sourcePlates = new ArrayList<>();
        PlateSet sourcePlateSet = null;
        for (Integer plateRowId : getReformatPlateRowIds(options))
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

    private @NotNull List<PlateData> hydratePlateDataFromWellLayout(
        Container container,
        User user,
        List<WellLayout> wellLayouts,
        LayoutOperation operation
    )
    {
        if (wellLayouts.isEmpty())
            return Collections.emptyList();

        List<PlateData> plates = new ArrayList<>();
        Map<Integer, List<WellData>> sourceWellDataMap = new HashMap<>();

        for (WellLayout wellLayout : wellLayouts)
        {
            PlateType plateType = wellLayout.getPlateType();
            List<Map<String, Object>> targetWellData = new ArrayList<>();

            for (WellLayout.Well well : wellLayout.getWells())
            {
                if (well == null)
                    continue;

                List<WellData> sourceWellDatas = sourceWellDataMap.computeIfAbsent(
                    well.sourcePlateId(),
                    (plateRowId) -> getWellData(container, user, plateRowId, true, true)
                );

                for (WellData wellData : sourceWellDatas)
                {
                    if (!wellData.hasData())
                        continue;

                    if (wellData.getRow() == well.sourceRowIdx() && wellData.getCol() == well.sourceColIdx())
                    {
                        Position p = new PositionImpl(container, well.destinationRowIdx(), well.destinationColIdx());

                        WellData d = new WellData();
                        d.setPosition(p.getDescription());
                        d.setSampleId(wellData.getSampleId());
                        d.setMetadata(wellData.getMetadata());
                        d.setWellGroup(wellData.getWellGroup());
                        d.setType(wellData.getType());

                        targetWellData.add(d.getData());
                    }
                }
            }

            if (operation.produceEmptyPlates() || !targetWellData.isEmpty())
                plates.add(new PlateData(null, plateType.getRowId(), null, null, targetWellData));
        }

        return plates;
    }

    private class BulkPlateIndexer extends Thread
    {
        Map<Container, Set<Integer>> _plates;

        public BulkPlateIndexer(Map<Container, Set<Integer>> plates)
        {
            _plates = plates;
        }

        @Override
        public void run()
        {
            for (Map.Entry<Container, Set<Integer>> entry : _plates.entrySet())
            {
                for (Integer plateId : entry.getValue())
                {
                    LOG.debug("Indexing plate ID " + plateId);
                    indexPlate(entry.getKey(), plateId, true);
                }
            }
        }
    }
}
