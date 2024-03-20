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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.assay.plate.AbstractPlateLayoutHandler;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateCustomField;
import org.labkey.api.assay.plate.PlateLayoutHandler;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetEdge;
import org.labkey.api.assay.plate.PlateSetType;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.PlateUtils;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.assay.plate.WellCustomField;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
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
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
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
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.assay.TsvAssayProvider;
import org.labkey.assay.plate.model.PlateBean;
import org.labkey.assay.plate.model.PlateSetLineage;
import org.labkey.assay.plate.model.PlateTypeBean;
import org.labkey.assay.plate.model.WellBean;
import org.labkey.assay.plate.model.WellGroupBean;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.PlateSetTable;
import org.labkey.assay.plate.query.PlateTable;
import org.labkey.assay.plate.query.WellGroupTable;
import org.labkey.assay.plate.query.WellTable;
import org.labkey.assay.query.AssayDbSchema;

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
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.labkey.api.assay.plate.PlateSet.MAX_PLATES;

public class PlateManager implements PlateService
{
    private static final Logger LOG = LogManager.getLogger(PlateManager.class);
    private static final String LSID_CLASS_OBJECT_ID = "objectType";

    private final List<PlateService.PlateDetailsResolver> _detailsLinkResolvers = new ArrayList<>();
    private boolean _lsidHandlersRegistered = false;
    private final Map<String, PlateLayoutHandler> _plateLayoutHandlers = new HashMap<>();

    // name expressions, currently not configurable
    private static final String PLATE_SET_NAME_EXPRESSION = "PLS-${now:date('yyyyMMdd')}-${RowId}";
    private static final String PLATE_NAME_EXPRESSION = "${${PlateSet/PlateSetId}-:withCounter}";

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
            public Plate createTemplate(@Nullable String templateTypeName, Container container, @NotNull PlateType plateType)
            {
                validatePlateType(plateType);
                return PlateService.get().createPlateTemplate(container, getAssayType(), plateType);
            }

            @Override
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
                return List.of(new Pair<>(8, 12));
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

    public @NotNull Plate createAndSavePlate(
        @NotNull Container container,
        @NotNull User user,
        @NotNull PlateType plateType,
        @Nullable String plateName,
        @Nullable Integer plateSetId,
        @Nullable String assayType,
        @Nullable List<Map<String, Object>> data
    ) throws Exception
    {
        Plate plate = null;
        try (DbScope.Transaction tx = ensureTransaction())
        {
            Plate plateTemplate = PlateService.get().createPlateTemplate(container, assayType, plateType);

            plate = createPlate(plateTemplate, null, null);
            if (plateSetId != null)
            {
                PlateSet plateSet = getPlateSet(container, plateSetId);
                if (plateSet == null)
                    throw new IllegalArgumentException("Failed to create plate. Plate set with rowId (" + plateSetId + ") is not available in " + container.getPath());
                ((PlateImpl) plate).setPlateSet(plateSet);
            }

            if (StringUtils.trimToNull(plateName) != null)
                plate.setName(plateName.trim());

            int plateRowId = save(container, user, plate);
            plate = getPlate(container, plateRowId);
            if (plate == null)
                throw new IllegalStateException("Unexpected failure. Failed to retrieve plate after save.");

            // if well data was specified, save that to the well table
            if (data != null && !data.isEmpty())
            {
                QueryUpdateService qus = getWellUpdateService(container, user);
                TableInfo wellTable = getWellTable(container, user);
                BatchValidationException errors = new BatchValidationException();
                Set<PlateCustomField> customFields = new HashSet<>();

                TableInfo metadataTable = getPlateMetadataTable(container, user);
                Set<FieldKey> metadataFields = Collections.emptySet();
                if (metadataTable != null)
                    metadataFields = metadataTable.getColumns().stream().map(ColumnInfo::getFieldKey).collect(Collectors.toSet());

                // resolve columns and set any custom fields associated with the plate
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map<String, Object> dataRow : data)
                {
                    if (dataRow.containsKey("wellLocation"))
                    {
                        PlateUtils.Location loc = PlateUtils.parseLocation(String.valueOf(dataRow.get("wellLocation")));
                        Well well = plate.getWell(loc.getRow(), loc.getCol());
                        if (well != null)
                        {
                            Map<String, Object> row = new CaseInsensitiveHashMap<>(dataRow);
                            row.put("rowId", well.getRowId());
                            rows.add(row);

                            for (String colName : dataRow.keySet())
                            {
                                ColumnInfo col = wellTable.getColumn(FieldKey.fromParts(colName));
                                if (col != null && metadataFields.contains(col.getFieldKey()))
                                {
                                    PlateCustomField customField = new PlateCustomField(col.getPropertyURI());
                                    customFields.add(customField);
                                }
                            }
                        }
                        else
                            LOG.error("There is no corresponding well at : " + dataRow.get("wellLocation") + " for the plate : " + plate.getName());
                    }
                    else
                    {
                        // should we fail or just log?
                        LOG.error("Unable to add well data for plate: " + plate.getName() + " each data row must contain a wellLocation field.");
                    }
                }

                // update the well table
                qus.updateRows(user, container, rows, null, errors, null, null);
                if (errors.hasErrors())
                    throw errors;

                // add custom fields to the plate
                if (!customFields.isEmpty())
                    addFields(container, user, plate.getRowId(), customFields.stream().toList());
            }
            tx.commit();
            return getPlate(container, plate.getRowId());
        }
        catch (Exception e)
        {
            // perhaps a better way to handle this
            if (plate != null && plate.getRowId() != null)
                PlateCache.uncache(container, plate);
            throw e;
        }
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

    @Override
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

        List<Integer> runIds = getRunIdsUsingPlateInResults(c, user, plate);
        if (runIds != null)
            count += runIds.size();

        return count;
    }

    private List<Integer> getRunIdsUsingPlateInResults(@NotNull Container c, @NotNull User user, @NotNull Plate plate)
    {
        // first, get the list of GPAT protocols in the container
        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);
        if (provider == null)
            return null;

        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c, provider)
                .stream().filter(provider::isPlateMetadataEnabled).toList();

        // get the runIds for each protocol, query against its assayresults table
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

    @Override
    @NotNull
    public Plate createPlate(Container container, String templateType, @NotNull PlateType plateType)
    {
        return new PlateImpl(container, null, templateType, plateType);
    }

    @Override
    @NotNull
    public Plate createPlateTemplate(Container container, String templateType, @NotNull PlateType plateType)
    {
        Plate plate = createPlate(container, templateType, plateType);
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

    private @NotNull Plate requirePlate(Container container, int plateRowId, @Nullable String errorPrefix)
    {
        Plate plate = getPlate(container, plateRowId);
        if (plate == null)
        {
            String error = "Plate id \"" + plateRowId + "\" not found.";
            String errorPrefix_ = StringUtils.trimToEmpty(errorPrefix);
            if (!errorPrefix_.isEmpty())
                error = errorPrefix_ + " " + error;
            throw new IllegalArgumentException(error);
        }

        return plate;
    }

    /**
     * Issue 49665 : Checks to see if there is a plate with the same name in the folder, or for
     * Biologics folders if there is a duplicate plate name in the plate set.
     */
    public boolean isDuplicatePlate(Container c, User user, String name, @Nullable PlateSet plateSet)
    {
        // Identifying the "Biologics" folder type as the logic we pivot this behavior on is not intended to be
        // a long-term solution. We will be looking to introduce plating as a ProductFeature which we can then
        // leverage instead.
        boolean isBiologicsProject = c.getProject() != null && "Biologics".equals(ContainerManager.getFolderTypeName(c.getProject()));
        if (isBiologicsProject && plateSet != null)
        {
            for (Plate plate : plateSet.getPlates(user))
            {
                if (plate.getName().equalsIgnoreCase(name))
                    return true;
            }
            return false;
        }
        else
        {
            Plate plate = getPlateByName(c, name);
            return plate != null && plate.getName().equals(name);
        }
    }

    private Collection<Plate> getPlates(Container c)
    {
        return PlateCache.getPlates(c);
    }

    public List<Plate> getPlatesForPlateSet(PlateSet plateSet)
    {
        return PlateCache.getPlatesForPlateSet(plateSet.getContainer(), plateSet.getRowId());
    }

    @Override
    public WellGroup getWellGroup(Container container, int rowid)
    {
        WellGroupImpl unboundWellgroup = new TableSelector(AssayDbSchema.getInstance().getTableInfoWellGroup()).getObject(rowid, WellGroupImpl.class);
        if (unboundWellgroup == null || !unboundWellgroup.getContainer().equals(container))
            return null;
        Plate plate = getPlate(container, unboundWellgroup.getPlateId());
        for (WellGroup wellgroup : plate.getWellGroups())
        {
            if (wellgroup.getRowId().intValue() == rowid)
                return wellgroup;
        }
        assert false : "Unbound well group was found: bound group should always be present.";
        return null;
    }

    public WellGroup getWellGroup(String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("lsid"), lsid);
        WellGroupImpl unboundWellgroup = new TableSelector(AssayDbSchema.getInstance().getTableInfoWellGroup(), filter, null).getObject(WellGroupImpl.class);
        if (unboundWellgroup == null)
            return null;
        Plate plate = getPlate(unboundWellgroup.getContainer(), unboundWellgroup.getPlateId());
        for (WellGroup wellgroup : plate.getWellGroups())
        {
            if (wellgroup.getRowId().intValue() == unboundWellgroup.getRowId().intValue())
                return wellgroup;
        }
        assert false : "Unbound well group was not found: bound group should always be present.";
        return null;
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
        if (plate instanceof PlateImpl plateTemplate)
            return savePlateImpl(container, user, plateTemplate);
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
        WellGroupImpl[] wellgroups = getWellGroups(plate);
        List<WellGroupImpl> sortedGroups = new ArrayList<>();
        for (WellGroupImpl wellgroup : wellgroups)
        {
            setProperties(plate.getContainer(), wellgroup);
            List<PositionImpl> groupPositions = groupIdToPositions.get(wellgroup.getRowId());

            wellgroup.setPositions(groupPositions != null ? groupPositions : emptyList());
            sortedGroups.add(wellgroup);
        }

        sortedGroups.sort(new WellGroupComparator());

        for (WellGroupImpl group : sortedGroups)
            plate.addWellGroup(group);

        // custom plate properties
        Domain domain = getPlateMetadataDomain(plate.getContainer(), null);
        if (domain != null)
        {
            plate.setMetadataDomainId(domain.getTypeId());
            SQLFragment sqlPlateProps = new SQLFragment("SELECT PropertyURI FROM ").append(AssayDbSchema.getInstance().getTableInfoPlateProperty(), "PP")
                    .append(" WHERE PlateId = ?").add(plate.getRowId());

            List<DomainProperty> fields = new ArrayList<>();
            for (String uri : new SqlSelector(AssayDbSchema.getInstance().getSchema(), sqlPlateProps).getArrayList(String.class))
            {
                DomainProperty dp = domain.getPropertyByURI(uri);
                if (dp == null)
                    throw new IllegalArgumentException("Failed to get plate custom field. \"" + uri + "\" does not exist on domain.");

                fields.add(dp);
            }

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

    private WellGroupImpl[] getWellGroups(Plate plate)
    {
        SimpleFilter plateFilter = new SimpleFilter(FieldKey.fromParts("PlateId"), plate.getRowId());
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoWellGroup(), plateFilter, null).getArray(WellGroupImpl.class);
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

    private DbScope.Transaction ensureTransaction(Lock... locks)
    {
        return AssayDbSchema.getInstance().getSchema().getScope().ensureTransaction(locks);
    }

    private int savePlateImpl(Container container, User user, PlateImpl plate) throws Exception
    {
        boolean updateExisting = plate.getRowId() != null;

        try (DbScope.Transaction transaction = ensureTransaction())
        {
            Integer plateId = plate.getRowId();

            if (!updateExisting && plate.getPlateSet() == null)
            {
                // ensure a plate set for each new plate
                plate.setPlateSet(createPlateSet(container, user, new PlateSetImpl(), null, null));
            }
            Map<String, Object> plateRow = ObjectFactory.Registry.getFactory(PlateBean.class).toMap(PlateBean.from(plate), new ArrayListMap<>());
            QueryUpdateService qus = getPlateUpdateService(container, user);
            BatchValidationException errors = new BatchValidationException();

            if (updateExisting)
            {
                qus.updateRows(user, container, Collections.singletonList(plateRow), null, errors, null, null);
                if (errors.hasErrors())
                    throw errors;
            }
            else
            {
                List<Map<String, Object>> insertedRows = qus.insertRows(user, container, Collections.singletonList(plateRow), errors, null, null);
                if (errors.hasErrors())
                    throw errors;
                Map<String, Object> row = insertedRows.get(0);
                plateId = (Integer) row.get("RowId");
                plate.setRowId(plateId);
                plate.setLsid((String) row.get("Lsid"));
                plate.setPlateId((String) row.get("PlateId"));
            }
            savePropertyBag(container, user, plate.getLSID(), plate.getProperties(), updateExisting);

            // delete well groups first
            List<WellGroupImpl> deletedWellGroups = plate.getDeletedWellGroups();
            for (WellGroupImpl deletedWellGroup : deletedWellGroups)
            {
                assert deletedWellGroup.getRowId() != null && deletedWellGroup.getRowId() > 0;
                LOG.debug("Deleting well group: name=" + deletedWellGroup.getName() + ", rowId=" + deletedWellGroup.getRowId());
                deleteWellGroup(container, user, deletedWellGroup.getRowId());
            }

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
                    wellGroupQus.updateRows(user, container, Collections.singletonList(wellGroupRow), null, wellGroupErrors, null, null);
                    if (wellGroupErrors.hasErrors())
                        throw wellGroupErrors;

                    savePropertyBag(container, user, wellGroupInstanceLsid, wellgroup.getProperties(), true);
                }
                else
                {
                    wellgroup.setPlateId(plateId);
                    wellGroupRow = ObjectFactory.Registry.getFactory(WellGroupBean.class).toMap(WellGroupBean.from(wellgroup), new ArrayListMap<>());

                    List<Map<String, Object>> insertedRows = wellGroupQus.insertRows(user, container, Collections.singletonList(wellGroupRow), wellGroupErrors, null, null);
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
                for (int row = 0; row < plate.getRows(); row++)
                {
                    for (int col = 0; col < plate.getColumns(); col++)
                    {
                        PositionImpl position;
                        position = plate.getPosition(row, col);
                        if (position.getRowId() != null)
                            throw new IllegalStateException("Attempting to create a new plate but there are existing wells associated with it.");

                        position.setPlateId(plateId);
                        wellRows.add(factory.toMap(position, new ArrayListMap<>()));
                    }
                }
                BatchValidationException wellErrors = new BatchValidationException();
                insertedRows = wellQus.insertRows(user, container, wellRows, wellErrors, null, null);
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

            final Integer plateRowId = plateId;
            transaction.addCommitTask(() -> {
                clearCache(container, plate);
                indexPlate(container, plateRowId);
            }, DbScope.CommitTaskOption.POSTCOMMIT);
            transaction.commit();

            return plateId;
        }
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
    public void deletePlate(Container container, User user, int rowid) throws Exception
    {
        Map<String, Object> key = Collections.singletonMap("RowId", rowid);
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

        // delete PlateProperty mappings
        AssayDbSchema schema = AssayDbSchema.getInstance();
        SQLFragment sql = new SQLFragment("DELETE FROM ")
                .append(schema.getTableInfoPlateProperty(), "")
                .append(" WHERE PlateId = ?")
                .add(plateId);
        new SqlExecutor(schema.getSchema()).execute(sql);

        // delete any plate metadata values from the provisioned table
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

        SQLFragment edgeSql = new SQLFragment("DELETE FROM ").append(schema.getTableInfoPlateSetEdge())
                .append(" WHERE FromPlateSetId ").appendInClause(plateSetIds, sqlDialect)
                .append(" OR ToPlateSetId ").appendInClause(plateSetIds, sqlDialect)
                .append(" OR RootPlateSetId ").appendInClause(plateSetIds, sqlDialect);
        new SqlExecutor(schema.getSchema()).execute(edgeSql);

        SQLFragment primaryPlateSetSql = new SQLFragment("UPDATE ").append(schema.getTableInfoPlateSet())
                .append(" SET PrimaryPlateSetId = NULL WHERE PrimaryPlateSetId ").appendInClause(plateSetIds, sqlDialect);
        new SqlExecutor(schema.getSchema()).execute(primaryPlateSetSql);

        SQLFragment rootPlateSetSql = new SQLFragment("UPDATE ").append(schema.getTableInfoPlateSet())
                .append(" SET RootPlateSetId = NULL WHERE RootPlateSetId ").appendInClause(plateSetIds, sqlDialect);
        new SqlExecutor(schema.getSchema()).execute(rootPlateSetSql);
    }

    private void deleteWellGroup(Container container, User user, int wellGroupId) throws Exception
    {
        final AssayDbSchema schema = AssayDbSchema.getInstance();
        DbScope scope = schema.getSchema().getScope();
        assert scope.isTransactionActive();

        Map<String, Object> key = Collections.singletonMap("RowId", wellGroupId);
        QueryUpdateService qus = getWellGroupUpdateService(container, user);
        qus.deleteRows(user, container, Collections.singletonList(key), null, null);
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

    @Override
    public void deleteAllPlateData(Container container)
    {
        try (DbScope.Transaction tx = ensureTransaction())
        {
            final AssayDbSchema schema = AssayDbSchema.getInstance();
            // delete well group positions
            {
                SQLFragment sql = new SQLFragment("DELETE FROM ").append(schema.getTableInfoWellGroupPositions())
                        .append(" WHERE wellId IN (SELECT rowId FROM ").append(schema.getTableInfoWell())
                        .append(" WHERE container = ?)").add(container);
                new SqlExecutor(schema.getSchema()).execute(sql);
            }

            // delete PlateProperty mappings
            {
                SQLFragment sql = new SQLFragment("DELETE FROM ")
                        .append(schema.getTableInfoPlateProperty(), "")
                        .append(" WHERE PlateId IN (SELECT RowId FROM ").append(schema.getTableInfoPlate())
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
    
    private UserSchema getPlateUserSchema(Container container, User user)
    {
        return QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME);
    }

    @Override
    public TableInfo getPlateTableInfo()
    {
        return AssayDbSchema.getInstance().getTableInfoPlate();
    }

    private @NotNull QueryUpdateService getPlateUpdateService(Container container, User user)
    {
        UserSchema schema = getPlateUserSchema(container, user);
        TableInfo tableInfo = schema.getTableOrThrow(PlateTable.NAME);
        QueryUpdateService qus = tableInfo.getUpdateService();
        if (qus == null)
            throw new IllegalStateException("Unable to resolve QueryUpdateService for Plates.");

        return qus;
    }

    private @NotNull QueryUpdateService getPlateSetUpdateService(Container container, User user)
    {
        UserSchema schema = getPlateUserSchema(container, user);
        TableInfo tableInfo = schema.getTableOrThrow(PlateSetTable.NAME);
        QueryUpdateService qus = tableInfo.getUpdateService();
        if (qus == null)
            throw new IllegalStateException("Unable to resolve QueryUpdateService for PlateSets.");

        return qus;
    }

    private @NotNull QueryUpdateService getWellGroupUpdateService(Container container, User user)
    {
        UserSchema schema = getPlateUserSchema(container, user);
        TableInfo tableInfo = schema.getTableOrThrow(WellGroupTable.NAME);
        QueryUpdateService qus = tableInfo.getUpdateService();
        if (qus == null)
            throw new IllegalStateException("Unable to resolve QueryUpdateService for Well Groups.");

        return qus;
    }

    private @NotNull TableInfo getWellTable(Container container, User user)
    {
        UserSchema schema = getPlateUserSchema(container, user);
        return schema.getTableOrThrow(WellTable.NAME);
    }

    private @NotNull QueryUpdateService getWellUpdateService(Container container, User user)
    {
        TableInfo tableInfo = getWellTable(container, user);
        QueryUpdateService qus = tableInfo.getUpdateService();
        if (qus == null)
            throw new IllegalStateException("Unable to resolve QueryUpdateService for Wells.");

        return qus;
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

    @Override
    public Plate copyPlate(Plate source, User user, Container destContainer)
            throws Exception
    {
        if (isDuplicatePlate(destContainer, user, source.getName(), null))
            throw new PlateService.NameConflictException(source.getName());
        Plate newPlate = createPlateTemplate(destContainer, source.getAssayType(), source.getPlateType());
        newPlate.setName(source.getName());
        for (String property : source.getPropertyNames())
            newPlate.setProperty(property, source.getProperty(property));
        for (WellGroup originalGroup : source.getWellGroups())
        {
            List<Position> positions = new ArrayList<>();
            for (Position position : originalGroup.getPositions())
                positions.add(newPlate.getPosition(position.getRow(), position.getColumn()));
            WellGroup copyGroup = newPlate.addWellGroup(originalGroup.getName(), originalGroup.getType(), positions);
            for (String property : originalGroup.getPropertyNames())
                copyGroup.setProperty(property, originalGroup.getProperty(property));
        }
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

    public void clearCache(Container c)
    {
        PlateCache.uncache(c);
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

    private void indexPlate(Container c, Integer plateRowId)
    {
        Plate plate = getPlate(c, plateRowId);
        SearchService ss = SearchService.get();

        if (ss == null || plate == null)
            return;

        indexPlate(ss.defaultTask(), plate);
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

    /**
     * Well metadata has transitioned to a provisioned architecture.
     */
    @Deprecated
    public @Nullable Domain getPlateMetadataVocabDomain(Container container, User user)
    {
        DomainKind<?> vocabDomainKind = PropertyService.get().getDomainKindByName("Vocabulary");

        if (vocabDomainKind == null)
            return null;

        // the domain is scoped at the project level (project and subfolder scoping)
        String domainURI = vocabDomainKind.generateDomainURI(null, "PlateMetadataDomain", getPlateMetadataDomainContainer(container), user);
        return PropertyService.get().getDomain(container, domainURI);
    }

    public @Nullable TableInfo getPlateMetadataTable(Container container, User user)
    {
        Domain domain = getPlateMetadataDomain(container, user);
        if (domain != null)
        {
            return StorageProvisioner.createTableInfo(domain);
        }
        return null;
    }

    private Container getPlateMetadataDomainContainer(Container container)
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
            SQLFragment sql = new SQLFragment("SELECT COUNT(DISTINCT(PlateId)) FROM ").append(AssayDbSchema.getInstance().getTableInfoPlateProperty(), "PP")
                    .append(" WHERE PropertyURI ").appendInClause(propertyURIs, AssayDbSchema.getInstance().getSchema().getSqlDialect());
            int inUsePlates = new SqlSelector(AssayDbSchema.getInstance().getSchema(), sql).getObject(Integer.class);
            if (inUsePlates > 0)
                throw new IllegalArgumentException(String.format("Unable to remove fields from domain, there are %d plates that are referencing these fields. Fields need to be removed from the plates first.", inUsePlates));

            try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
            {
                Set<String> existingProperties = metadataDomain.getProperties().stream().map(ImportAliasable::getPropertyURI).collect(Collectors.toSet());
                for (PlateCustomField field : fields)
                {
                    if (!existingProperties.contains(field.getPropertyURI()))
                        throw new IllegalStateException(String.format("Unable to remove field: %s on domain: %s. The field does not exist.", field.getName(), metadataDomain.getTypeURI()));

                    DomainProperty dp = metadataDomain.getPropertyByURI(field.getPropertyURI());
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
                .sorted(Comparator.comparing(PlateCustomField::getName))
                .toList();
    }

    public @NotNull List<PlateCustomField> addFields(Container container, User user, Integer plateId, List<PlateCustomField> fields) throws SQLException
    {
        if (plateId == null)
            throw new IllegalArgumentException("Failed to add plate custom fields. Invalid plateId provided.");

        if (fields == null || fields.isEmpty())
            throw new IllegalArgumentException("Failed to add plate custom fields. No fields specified.");

        Plate plate = requirePlate(container, plateId, "Failed to add plate custom fields.");

        Domain domain = getPlateMetadataDomain(container, user);
        if (domain == null)
            throw new IllegalArgumentException("Failed to add plate custom fields. Custom fields domain does not exist. Try creating fields first.");

        List<DomainProperty> fieldsToAdd = new ArrayList<>();
        // validate fields
        for (PlateCustomField field : fields)
        {
            DomainProperty dp = domain.getPropertyByURI(field.getPropertyURI());
            if (dp == null)
                throw new IllegalArgumentException("Failed to add plate custom field. \"" + field.getPropertyURI() + "\" does not exist on domain.");
            fieldsToAdd.add(dp);
        }

        if (!fieldsToAdd.isEmpty())
        {
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                Set<String> existingProps = plate.getCustomFields().stream().map(PlateCustomField::getPropertyURI).collect(Collectors.toSet());
                for (DomainProperty dp : fieldsToAdd)
                {
                    if (existingProps.contains(dp.getPropertyURI()))
                        throw new IllegalArgumentException(String.format("Failed to add plate custom fields. Custom field \"%s\" already is associated with this plate.", dp.getName()));
                }

                List<List<?>> insertedValues = new LinkedList<>();
                for (DomainProperty dp : fieldsToAdd)
                {
                    insertedValues.add(List.of(plateId,
                            dp.getPropertyId(),
                            dp.getPropertyURI()));
                }
                String insertSql = "INSERT INTO " + AssayDbSchema.getInstance().getTableInfoPlateProperty() +
                        " (plateId, propertyId, propertyURI)" +
                        " VALUES (?, ?, ?)";
                Table.batchExecute(AssayDbSchema.getInstance().getSchema(), insertSql, insertedValues);

                transaction.addCommitTask(() -> clearCache(container, plate), DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
        }
        return getFields(container, plateId);
    }

    public @NotNull List<PlateCustomField> getFields(Container container, Integer plateId)
    {
        Plate plate = requirePlate(container, plateId, "Failed to get plate custom fields.");
        return plate.getCustomFields();
    }

    /**
     * Returns the list of custom properties associated with a plate
     */
    private List<DomainProperty> _getFields(Container container, User user, Integer plateId)
    {
        Domain domain = getPlateMetadataDomain(container, user);
        if (domain == null)
            throw new IllegalArgumentException("Failed to get plate custom fields. Custom fields domain does not exist. Try creating fields first.");

        SQLFragment sql = new SQLFragment("SELECT PropertyURI FROM ").append(AssayDbSchema.getInstance().getTableInfoPlateProperty(), "PP")
                .append(" WHERE PlateId = ?").add(plateId);

        List<DomainProperty> fields = new ArrayList<>();
        for (String uri : new SqlSelector(AssayDbSchema.getInstance().getSchema(), sql).getArrayList(String.class))
        {
            DomainProperty dp = domain.getPropertyByURI(uri);
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

        List<WellCustomField> fields = _getFields(plate.getContainer(), user, plate.getRowId()).stream().map(WellCustomField::new).toList();

        // merge in any well metadata values
        if (!fields.isEmpty())
        {
            Map<FieldKey, WellCustomField> customFieldMap = new HashMap<>();
            for (WellCustomField customField : fields)
                customFieldMap.put(FieldKey.fromParts("properties", customField.getName()), customField);
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
        }

        return fields.stream().sorted(Comparator.comparing(PlateCustomField::getName)).toList();
    }

    public List<PlateCustomField> removeFields(Container container, User user, Integer plateId, List<PlateCustomField> fields)
    {
        Plate plate = requirePlate(container, plateId, "Failed to remove plate custom fields.");

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

                SQLFragment sql = new SQLFragment("DELETE FROM ").append(AssayDbSchema.getInstance().getTableInfoPlateProperty(), "")
                        .append(" WHERE PlateId = ? ").add(plateId)
                        .append(" AND PropertyURI ").appendInClause(propertyURIs, AssayDbSchema.getInstance().getSchema().getSqlDialect());

                new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql);

                transaction.addCommitTask(() -> clearCache(container, plate), DbScope.CommitTaskOption.POSTCOMMIT);
                transaction.commit();
            }
        }
        return getFields(container, plateId);
    }

    public List<PlateCustomField> setFields(Container container, User user, Integer plateRowId, List<PlateCustomField> fields) throws SQLException
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

    public record CreatePlateSetPlate(String name, Integer plateType) {}

    public PlateSetImpl createPlateSet(
        Container container,
        User user,
        @NotNull PlateSetImpl plateSet,
        @Nullable List<CreatePlateSetPlate> plates,
        @Nullable Integer parentPlateSetId
    ) throws Exception
    {
        if (!container.hasPermission(user, InsertPermission.class))
            throw new UnauthorizedException("Failed to create plate set. Insufficient permissions.");

        if (plateSet.getRowId() != null)
            throw new ValidationException("Failed to create plate set. Cannot create plate set with rowId (" + plateSet.getRowId() + ").");

        if (plates != null && plates.size() > MAX_PLATES)
            throw new ValidationException(String.format("Failed to create plate set. Plate sets can have a maximum of %d plates.", MAX_PLATES));

        PlateSetImpl parentPlateSet = null;
        if (parentPlateSetId != null)
        {
            parentPlateSet = (PlateSetImpl) getPlateSet(container, parentPlateSetId);
            if (parentPlateSet == null)
                throw new ValidationException(String.format("Failed to create plate set. Parent plate set with rowId (%d) is not available.", parentPlateSetId));
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
            {
                for (var plate : plates)
                {
                    var plateType = getPlateType(plate.plateType);
                    if (plateType == null)
                        throw new ValidationException("Failed to create plate set. Plate Type (" + plate.plateType + ") is invalid.");

                    // TODO: Write a cheaper plate create/save for multiple plates
                    createAndSavePlate(container, user, plateType, plate.name, plateSetId, TsvPlateLayoutHandler.TYPE, null);
                }
            }

            plateSet = (PlateSetImpl) getPlateSet(container, plateSetId);
            tx.commit();
        }

        return plateSet;
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
            if (PlateSetType.primary.equals(parentPlateSet.getType()))
                primaryPlateSetId = parentPlateSet.getRowId();
            else if (PlateSetType.assay.equals(parentPlateSet.getType()))
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

    public void archivePlateSets(Container container, User user, List<Integer> plateSetIds, boolean archive) throws Exception
    {
        String action = archive ? "archive" : "restore";
        Class<? extends Permission> perm = UpdatePermission.class;

        if (!container.hasPermission(user, perm))
            throw new UnauthorizedException("Failed to " + action + " plate sets. Insufficient permissions.");

        if (plateSetIds.isEmpty())
            throw new ValidationException("Failed to " + action + " plate sets. No plate sets specified.");

        try (DbScope.Transaction tx = ensureTransaction())
        {
            TableInfo plateSetTable = AssayDbSchema.getInstance().getTableInfoPlateSet();

            // Ensure user has permission in all containers
            {
                SQLFragment sql = new SQLFragment("SELECT DISTINCT container FROM ")
                        .append(plateSetTable, "PS")
                        .append(" WHERE rowId ")
                        .appendInClause(plateSetIds, plateSetTable.getSqlDialect());

                for (String containerId : new SqlSelector(plateSetTable.getSchema(), sql).getCollection(String.class))
                {
                    Container c = ContainerManager.getForId(containerId);
                    if (c != null && !c.hasPermission(user, perm))
                        throw new UnauthorizedException("Failed to " + action + " plate sets. Insufficient permissions in " + c.getPath());
                }
            }

            SQLFragment sql = new SQLFragment("UPDATE ").append(plateSetTable)
                    .append(" SET archived = ?").add(archive)
                    .append(" WHERE rowId ").appendInClause(plateSetIds, plateSetTable.getSqlDialect());

            new SqlExecutor(plateSetTable.getSchema()).execute(sql);

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

    public PlateSetLineage getPlateSetLineage(Container container, User user, int seedPlateSetId, @Nullable ContainerFilter cf)
    {
        ContainerFilter cf_ = cf;
        if (cf_ == null)
            cf_ = QueryService.get().getProductContainerFilterForLookups(container, user, ContainerFilter.Type.Current.create(container, user));

        PlateSetImpl seedPlateSet = (PlateSetImpl) getPlateSet(cf_, seedPlateSetId);
        if (seedPlateSet == null)
            throw new NotFoundException();

        PlateSetLineage lineage = new PlateSetLineage();
        lineage.setSeed(seedPlateSetId);

        // stand-alone plate set
        if (seedPlateSet.getRootPlateSetId() == null)
        {
            lineage.setPlateSets(Map.of(seedPlateSetId, seedPlateSet));
            return lineage;
        }

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RootPlateSetId"), seedPlateSet.getRootPlateSetId());
        List<PlateSetEdge> edges = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateSetEdge(), filter, null).getArrayList(PlateSetEdge.class);
        lineage.setEdges(edges);

        Set<Integer> nodeIds = new HashSet<>();
        nodeIds.add(seedPlateSetId);
        nodeIds.add(seedPlateSet.getRootPlateSetId());
        for (var edge : edges)
        {
            nodeIds.add(edge.getFromPlateSetId());
            nodeIds.add(edge.getToPlateSetId());
        }

        UserSchema schema = getPlateUserSchema(container, user);
        TableInfo plateSetTable = schema.getTableOrThrow(PlateSetTable.NAME, cf_);
        SimpleFilter filterPS = new SimpleFilter();
        filterPS.addInClause(FieldKey.fromParts("RowId"), nodeIds);
        List<PlateSetImpl> nodes = new TableSelector(plateSetTable, filterPS, null).getArrayList(PlateSetImpl.class);

        Map<Integer, PlateSet> plateSets = new HashMap<>();
        for (var node : nodes)
            plateSets.put(node.getRowId(), node);
        lineage.setPlateSets(plateSets);

        return lineage;
    }

    private void requireActiveTransaction()
    {
        if (!AssayDbSchema.getInstance().getSchema().getScope().isTransactionActive())
            throw new IllegalStateException("This method must be called from within a transaction");
    }

    public static final class TestCase
    {
        private static Container container;
        private static User user;

        @BeforeClass
        public static void setupTest() throws Exception
        {
            container = JunitUtil.getTestContainer();
            user = TestContext.get().getUser();

            PlateService.get().deleteAllPlateData(container);
            Domain domain = PlateManager.get().getPlateMetadataDomain(container ,user);
            if (domain != null)
                domain.delete(user);

            // create custom properties
            List<GWTPropertyDescriptor> customFields = List.of(
                    new GWTPropertyDescriptor("barcode", "http://www.w3.org/2001/XMLSchema#string"),
                    new GWTPropertyDescriptor("concentration", "http://www.w3.org/2001/XMLSchema#double"),
                    new GWTPropertyDescriptor("negativeControl", "http://www.w3.org/2001/XMLSchema#double"));

            PlateManager.get().createPlateMetadataFields(container, user, customFields);
        }

        @After
        public void cleanupTest() throws Exception
        {
            PlateManager.get().deleteAllPlateData(container);
        }

        @Test
        public void createPlateTemplate() throws Exception
        {
            //
            // INSERT
            //

            PlateLayoutHandler handler = PlateManager.get().getPlateLayoutHandler(TsvPlateLayoutHandler.TYPE);
            PlateType plateType = PlateManager.get().getPlateType(8, 12);
            assertNotNull("96 well plate type was not found", plateType);

            Plate template = handler.createTemplate("UNUSED", container, plateType);
            template.setName("bob");
            template.setProperty("friendly", "yes");
            assertNull(template.getRowId());
            assertNull(template.getLSID());

            WellGroup wg1 = template.addWellGroup("wg1", WellGroup.Type.SAMPLE,
                    PlateService.get().createPosition(container, 0, 0),
                    PlateService.get().createPosition(container, 0, 11));
            wg1.setProperty("score", "100");
            assertNull(wg1.getRowId());
            assertNull(wg1.getLSID());

            int plateId = PlateService.get().save(container, user, template);

            //
            // VERIFY INSERT
            //

            assertEquals(1, PlateManager.get().getPlateTemplates(container).size());

            Plate savedTemplate = PlateManager.get().getPlateByName(container, "bob");
            assertEquals(plateId, savedTemplate.getRowId().intValue());
            assertEquals("bob", savedTemplate.getName());
            assertEquals("yes", savedTemplate.getProperty("friendly")); assertNotNull(savedTemplate.getLSID());
            assertEquals(plateType.getRowId(), savedTemplate.getPlateType().getRowId());

            List<WellGroup> wellGroups = savedTemplate.getWellGroups();
            assertEquals(3, wellGroups.size());

            // TsvPlateTypeHandler creates two CONTROL well groups "Positive" and "Negative"
            List<WellGroup> controlWellGroups = savedTemplate.getWellGroups(WellGroup.Type.CONTROL);
            assertEquals(2, controlWellGroups.size());

            List<WellGroup> sampleWellGroups = savedTemplate.getWellGroups(WellGroup.Type.SAMPLE);
            assertEquals(1, sampleWellGroups.size());
            WellGroup savedWg1 = sampleWellGroups.get(0);
            assertEquals("wg1", savedWg1.getName());
            assertEquals("100", savedWg1.getProperty("score"));

            List<Position> savedWg1Positions = savedWg1.getPositions();
            assertEquals(12, savedWg1Positions.size());

            //
            // UPDATE
            //

            // rename plate
            savedTemplate.setName("sally");

            // add well group
            WellGroup wg2 = savedTemplate.addWellGroup("wg2", WellGroup.Type.SAMPLE,
                    PlateService.get().createPosition(container, 1, 0),
                    PlateService.get().createPosition(container, 1, 11));

            // rename existing well group
            ((WellGroupImpl)savedWg1).setName("wg1_renamed");

            // add positions
            controlWellGroups.get(0).setPositions(List.of(
                    PlateService.get().createPosition(container, 0, 0),
                    PlateService.get().createPosition(container, 0, 1)));

            // delete well group
            ((PlateImpl)savedTemplate).markWellGroupForDeletion(controlWellGroups.get(1));

            int newPlateId = PlateService.get().save(container, user, savedTemplate);
            assertEquals(savedTemplate.getRowId().intValue(), newPlateId);

            //
            // VERIFY UPDATE
            //

            // verify plate
            Plate updatedTemplate = PlateService.get().getPlate(container, plateId);
            assertEquals("sally", updatedTemplate.getName());
            assertEquals(savedTemplate.getLSID(), updatedTemplate.getLSID());

            // verify well group rename
            WellGroup updatedWg1 = updatedTemplate.getWellGroup(savedWg1.getRowId());
            assertNotNull(updatedWg1);
            assertEquals(savedWg1.getLSID(), updatedWg1.getLSID());
            assertEquals("wg1_renamed", updatedWg1.getName());

            // verify added well group
            WellGroup updatedWg2 = updatedTemplate.getWellGroup(wg2.getRowId());
            assertNotNull(updatedWg2);

            // verify deleted well group
            List<WellGroup> updatedControlWellGroups = updatedTemplate.getWellGroups(WellGroup.Type.CONTROL);
            assertEquals(1, updatedControlWellGroups.size());

            // verify added positions
            assertEquals(2, updatedControlWellGroups.get(0).getPositions().size());

            // verify plate type information
            assertEquals(plateType.getRows().intValue(), updatedTemplate.getRows());
            assertEquals(plateType.getColumns().intValue(), updatedTemplate.getColumns());

            //
            // DELETE
            //

            PlateService.get().deletePlate(container, user, updatedTemplate.getRowId());

            assertNull(PlateService.get().getPlate(container, updatedTemplate.getRowId()));
            assertEquals(0, PlateManager.get().getPlateTemplates(container).size());
        }

        @Test
        public void testCreateAndSavePlate() throws Exception
        {
            // Arrange
            PlateType plateType = PlateManager.get().getPlateType(8, 12);
            assertNotNull("96 well plate type was not found", plateType);

            // Act
            Plate plate = PlateManager.get().createAndSavePlate(container, user, plateType, "testCreateAndSavePlate plate", null, null, null);

            // Assert
            assertTrue("Expected plate to have been persisted and provided with a rowId", plate.getRowId() > 0);
            assertTrue("Expected plate to have been persisted and provided with a plateId", plate.getPlateId() != null);

            // verify access via plate ID
            Plate savedPlate = PlateService.get().getPlate(container, plate.getPlateId());
            assertTrue("Expected plate to be accessible via it's plate ID", savedPlate != null);
            assertTrue("Plate retrieved by plate ID doesn't match the original plate.", savedPlate.getRowId().equals(plate.getRowId()));

            // verify container filter access
            savedPlate = PlateService.get().getPlate(ContainerManager.getSharedContainer(), plate.getRowId());
            assertTrue("Saved plate should not exist in the shared container", savedPlate == null);

            savedPlate = PlateService.get().getPlate(ContainerFilter.Type.CurrentAndSubfolders.create(ContainerManager.getSharedContainer(), user), plate.getRowId());
            assertTrue("Expected plate to be accessible via a container filter", plate.getRowId().equals(savedPlate.getRowId()));
        }

        @Test
        public void testAccessPlateByIdentifiers() throws Exception
        {
            // Arrange
            PlateType plateType = PlateManager.get().getPlateType(8, 12);
            assertNotNull("96 well plate type was not found", plateType);
            PlateSetImpl plateSetImpl = new PlateSetImpl();
            plateSetImpl.setName("testAccessPlateByIdentifiersPlateSet");
            ContainerFilter cf = ContainerFilter.Type.CurrentAndSubfolders.create(ContainerManager.getSharedContainer(), user);

            // Act
            PlateSet plateSet = PlateManager.get().createPlateSet(container, user, plateSetImpl, List.of(
                new CreatePlateSetPlate("testAccessPlateByIdentifiersFirst", plateType.getRowId()),
                new CreatePlateSetPlate("testAccessPlateByIdentifiersSecond", plateType.getRowId()),
                new CreatePlateSetPlate("testAccessPlateByIdentifiersThird", plateType.getRowId())
            ), null);

            // Assert
            assertTrue("Expected plateSet to have been persisted and provided with a rowId", plateSet.getRowId() > 0);
            List<Plate> plates = plateSet.getPlates(user);
            assertEquals("Expected plateSet to have 3 plates", 3, plates.size());

            // verify access via plate rowId
            assertNotNull("Expected plate to be accessible via it's rowId", PlateService.get().getPlate(cf, plateSet.getRowId(), plates.get(0).getRowId()));
            assertNotNull("Expected plate to be accessible via it's rowId", PlateService.get().getPlate(cf, plateSet.getRowId(), plates.get(1).getRowId()));
            assertNotNull("Expected plate to be accessible via it's rowId", PlateService.get().getPlate(cf, plateSet.getRowId(), plates.get(2).getRowId()));

            // verify access via plate ID
            assertNotNull("Expected plate to be accessible via it's plate ID", PlateService.get().getPlate(cf, plateSet.getRowId(), plates.get(0).getPlateId()));
            assertNotNull("Expected plate to be accessible via it's plate ID", PlateService.get().getPlate(cf, plateSet.getRowId(), plates.get(1).getPlateId()));
            assertNotNull("Expected plate to be accessible via it's plate ID", PlateService.get().getPlate(cf, plateSet.getRowId(), plates.get(2).getPlateId()));

            // verify access via plate name
            assertNotNull("Expected plate to be accessible via it's name", PlateService.get().getPlate(cf, plateSet.getRowId(), "testAccessPlateByIdentifiersFirst"));
            // verify error when trying to access non-existing plate name
            try
            {
                PlateService.get().getPlate(cf, plateSet.getRowId(), "testAccessPlateByIdentifiersBogus");
                fail("Expected a validation error when accessing plates by non-existing name");
            }
            catch (IllegalArgumentException e)
            {
                assertEquals("Expected validation exception", "The plate identifier \"testAccessPlateByIdentifiersBogus\" does not match any plate in the plate set \"testAccessPlateByIdentifiersPlateSet\".", e.getMessage());
            }
        }

        @Test
        public void testCreatePlateTemplates() throws Exception
        {
            // Verify plate service assumptions about plate templates
            PlateType plateType = PlateManager.get().getPlateType(16, 24);
            assertNotNull("384 well plate type was not found", plateType);
            Plate plate = PlateService.get().createPlateTemplate(container, TsvPlateLayoutHandler.TYPE, plateType);
            plate.setName("my plate template");
            int plateId = PlateService.get().save(container, user, plate);

            // Assert
            assertTrue("Expected saved plateId to be returned", plateId != 0);
            assertTrue("Expected saved plate to have the template field set to true", PlateService.get().getPlate(container, plateId).isTemplate());

            // Verify only plate templates are returned
            plateType = PlateManager.get().getPlateType(8, 12);
            assertNotNull("96 well plate type was not found", plateType);

            plate = PlateService.get().createPlate(container, TsvPlateLayoutHandler.TYPE, plateType);
            plate.setName("non plate template");
            PlateService.get().save(container, user, plate);

            List<Plate> plates = PlateService.get().getPlateTemplates(container);
            assertEquals("Expected only a single plate to be returned", 1, plates.size());
            for (Plate template : plates)
            {
                assertTrue("Expected saved plate to have the template field set to true", template.isTemplate());
            }
        }

        @Test
        public void testCreatePlateMetadata() throws Exception
        {
            PlateType plateType = PlateManager.get().getPlateType(16, 24);
            assertNotNull("384 well plate type was not found", plateType);

            Plate plate = PlateService.get().createPlateTemplate(container, TsvPlateLayoutHandler.TYPE, plateType);
            plate.setName("new plate with metadata");
            int plateId = PlateService.get().save(container, user, plate);

            // Assert
            assertTrue("Expected saved plateId to be returned", plateId != 0);

            List<PlateCustomField> fields = PlateManager.get().getPlateMetadataFields(container, user);

            // Verify returned sorted by name
            assertEquals("Expected plate custom fields", 3, fields.size());
            assertEquals("Expected barcode custom field", "barcode", fields.get(0).getName());
            assertEquals("Expected concentration custom field", "concentration", fields.get(1).getName());
            assertEquals("Expected negativeControl custom field", "negativeControl", fields.get(2).getName());

            // assign custom fields to the plate
            assertEquals("Expected custom fields to be added to the plate", 3, PlateManager.get().addFields(container, user, plateId, fields).size());

            // verification when adding custom fields to the plate
            try
            {
                PlateManager.get().addFields(container, user, plateId, fields);
                fail("Expected a validation error when adding existing fields");
            }
            catch (IllegalArgumentException e)
            {
                assertEquals("Expected validation exception", "Failed to add plate custom fields. Custom field \"barcode\" already is associated with this plate.", e.getMessage());
            }

            // remove a plate custom field
            fields = PlateManager.get().removeFields(container, user, plateId, List.of(fields.get(0)));
            assertEquals("Expected 2 plate custom fields", 2, fields.size());
            assertEquals("Expected concentration custom field", "concentration", fields.get(0).getName());
            assertEquals("Expected negativeControl custom field", "negativeControl", fields.get(1).getName());

            // select wells
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("PlateId"), plateId);
            filter.addCondition(FieldKey.fromParts("Row"), 0);
            List<WellBean> wells = new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, new Sort("Col")).getArrayList(WellBean.class);

            assertEquals("Expected 24 wells to be returned", 24, wells.size());

            // update
            TableInfo wellTable = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME).getTable(WellTable.NAME);
            QueryUpdateService qus = wellTable.getUpdateService();
            assertNotNull(qus);
            BatchValidationException errors = new BatchValidationException();

            // add metadata to 2 rows
            WellBean well = wells.get(0);
            List<Map<String, Object>> rows = List.of(CaseInsensitiveHashMap.of(
                    "rowid", well.getRowId(),
                    "properties/concentration", 1.25,
                    "properties/negativeControl", 5.25
            ));

            qus.updateRows(user, container, rows, null, errors, null, null);
            if (errors.hasErrors())
                fail(errors.getMessage());

            well = wells.get(1);
            rows = List.of(CaseInsensitiveHashMap.of(
                    "rowid", well.getRowId(),
                    "properties/concentration", 2.25,
                    "properties/negativeControl", 6.25
            ));

            qus.updateRows(user, container, rows, null, errors, null, null);
            if (errors.hasErrors())
                fail(errors.getMessage());

            FieldKey fkConcentration = FieldKey.fromParts("properties", "concentration");
            FieldKey fkNegativeControl = FieldKey.fromParts("properties", "negativeControl");
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(wellTable, List.of(fkConcentration, fkNegativeControl));

            // verify plate metadata property updates
            try (Results r = QueryService.get().select(wellTable, columns.values(), filter, new Sort("Col")))
            {
                int row = 0;
                while (r.next())
                {
                    if (row == 0)
                    {
                        assertEquals(1.25, r.getDouble(fkConcentration), 0);
                        assertEquals(5.25, r.getDouble(fkNegativeControl), 0);
                    }
                    else if (row == 1)
                    {
                        assertEquals(2.25, r.getDouble(fkConcentration), 0);
                        assertEquals(6.25, r.getDouble(fkNegativeControl), 0);
                    }
                    else
                    {
                        // the remainder should be null
                        assertEquals(0, r.getDouble(fkConcentration), 0);
                        assertEquals(0, r.getDouble(fkNegativeControl), 0);
                    }
                    row++;
                }
            }
        }

        @Test
        public void testCreateAndSavePlateWithData() throws Exception
        {
            // Arrange
            PlateType plateType = PlateManager.get().getPlateType(8, 12);
            assertNotNull("96 well plate type was not found", plateType);

            // Act
            List<Map<String, Object>> rows = List.of(
                    CaseInsensitiveHashMap.of(
                            "wellLocation", "A1",
                            "properties/concentration", 2.25,
                            "properties/barcode", "B1234")
                    ,
                    CaseInsensitiveHashMap.of(
                            "wellLocation", "A2",
                            "properties/concentration", 1.25,
                            "properties/barcode", "B5678"
                    )
            );
            Plate plate = PlateManager.get().createAndSavePlate(container, user, plateType, "hit selection plate", null, null, rows);
            assertEquals("Expected 2 plate custom fields", 2, plate.getCustomFields().size());

            TableInfo wellTable = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME).getTable(WellTable.NAME);
            FieldKey fkConcentration = FieldKey.fromParts("properties", "concentration");
            FieldKey fkBarcode = FieldKey.fromParts("properties", "barcode");
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(wellTable, List.of(fkConcentration, fkBarcode));

            // verify that well data was added
            SimpleFilter filter = SimpleFilter.createContainerFilter(container);
            filter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());
            filter.addCondition(FieldKey.fromParts("Row"), 0);
            try (Results r = QueryService.get().select(wellTable, columns.values(), filter, new Sort("Col")))
            {
                int row = 0;
                while (r.next())
                {
                    if (row == 0)
                    {
                        assertEquals(2.25, r.getDouble(fkConcentration), 0);
                        assertEquals("B1234", r.getString(fkBarcode));
                    }
                    else if (row == 1)
                    {
                        assertEquals(1.25, r.getDouble(fkConcentration), 0);
                        assertEquals("B5678", r.getString(fkBarcode));
                    }
                    else
                    {
                        // the remainder should be null
                        assertEquals(0, r.getDouble(fkConcentration), 0);
                        assertNull(r.getString(fkBarcode));
                    }
                    row++;
                }
            }
        }
    }
}
