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
import org.junit.Test;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.assay.plate.AbstractPlateTypeHandler;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateCustomField;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateTypeHandler;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.assay.plate.WellCustomField;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
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
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.assay.TsvAssayProvider;
import org.labkey.assay.plate.model.PlateType;
import org.labkey.assay.plate.model.WellGroupBean;
import org.labkey.assay.plate.query.PlateSchema;
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
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PlateManager implements PlateService
{
    private static final Logger LOG = LogManager.getLogger(PlateManager.class);
    private static final String LSID_CLASS_OBJECT_ID = "objectType";
    public static final String PLATE_WELL_DOMAIN = "PlateMetadataDomain";

    private final List<PlateService.PlateDetailsResolver> _detailsLinkResolvers = new ArrayList<>();
    private boolean _lsidHandlersRegistered = false;
    private final Map<String, PlateTypeHandler> _plateTypeHandlers = new HashMap<>();

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
        registerPlateTypeHandler(new AbstractPlateTypeHandler()
        {
            @Override
            public Plate createTemplate(@Nullable String templateTypeName, Container container, int rowCount, int colCount)
            {
                return PlateService.get().createPlateTemplate(container, getAssayType(), rowCount, colCount);
            }

            @Override
            public String getAssayType()
            {
                return "blank";
            }

            @Override
            public List<String> getTemplateTypes(Pair<Integer, Integer> size)
            {
                return new ArrayList<>();
            }

            @Override
            public List<Pair<Integer, Integer>> getSupportedPlateSizes()
            {
                return Collections.singletonList(new Pair<>(8, 12));
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
    public @Nullable Plate createPlate(Plate template, double[][] wellValues, boolean[][] excluded)
    {
        return createPlate(template, wellValues, excluded, PlateService.NO_RUNID, 1);
    }

    @Override
    public @Nullable Plate createPlate(Plate template, double[][] wellValues, boolean[][] excluded, int runId, int plateNumber)
    {
        if (template == null)
            return null;

        if (template instanceof PlateImpl plateTemplate)
            return new PlateImpl(plateTemplate, wellValues, excluded, runId, plateNumber);

        throw new IllegalArgumentException("Only plate templates retrieved from the plate service can be used to create plate instances.");
    }

    public @NotNull Plate createAndSavePlate(
        @NotNull Container container,
        @NotNull User user,
        @NotNull PlateType plateType,
        @Nullable String plateName
    ) throws Exception
    {
        try (DbScope.Transaction tx = ensureTransaction())
        {
            PlateTypeHandler plateTypeHandler = getPlateTypeHandler(plateType.getAssayType());
            Plate plateTemplate = plateTypeHandler.createTemplate(plateType.getType(), container, plateType.getRows(), plateType.getCols());

            Plate plate = createPlate(plateTemplate, null, null);
            if (StringUtils.trimToNull(plateName) != null)
                plate.setName(plateName.trim());

            int plateRowId = save(container, user, plate);
            plate = getPlate(container, plateRowId);
            if (plate == null)
                throw new IllegalStateException("Unexpected failure. Failed to retrieve plate after save.");

            tx.commit();

            return plate;
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

    @Override
    public @Nullable Plate getPlate(Container container, String plateName)
    {
        return PlateCache.getPlate(container, plateName);
    }

    @Override
    @NotNull
    public List<Plate> getPlateTemplates(Container container)
    {
        return PlateCache.getPlateTemplates(container);
    }

    @Override
    public List<? extends ExpRun> getRunsUsingPlate(@NotNull Container c, @NotNull Plate plateTemplate)
    {
        SqlSelector se = selectRunUsingPlate(c, plateTemplate);
        if (se == null)
            return emptyList();

        Collection<Integer> runIds = se.getCollection(Integer.class);
        return ExperimentService.get().getExpRuns(runIds);
    }

    @Override
    public int getRunCountUsingPlate(@NotNull Container c, @NotNull Plate plateTemplate)
    {
        SqlSelector se = selectRunUsingPlate(c, plateTemplate);
        if (se == null)
            return 0;

        return (int)se.getRowCount();
    }

    private @Nullable SqlSelector selectRunUsingPlate(@NotNull Container c, @NotNull Plate plate)
    {
        if (plate == null)
            return null;

        // first, get the list of GPAT protocols in the container
        AssayProvider gpat = AssayService.get().getProvider(TsvAssayProvider.NAME);
        if (gpat == null)
            return null;

        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(c, gpat);

        // next, for the plate metadata enabled assays,
        // get the set of "PlateTemplate" PropertyDescriptors from the RunDomains of those assays
        List<PropertyDescriptor> plateTemplateProps = protocols.stream()
                .filter(gpat::isPlateMetadataEnabled)
                .map(gpat::getRunDomain)
                .filter(Objects::nonNull)
                .map(r -> r.getPropertyByName(TsvAssayProvider.PLATE_TEMPLATE_PROPERTY_NAME))
                .filter(Objects::nonNull)
                .map(DomainProperty::getPropertyDescriptor)
                .toList();

        if (plateTemplateProps.isEmpty())
            return null;

        List<Integer> plateTemplatePropIds = plateTemplateProps.stream().map(PropertyDescriptor::getPropertyId).toList();

        // query for runs with that property that point to the plate by LSID
        SQLFragment sql = new SQLFragment()
                .append("SELECT r.rowId\n")
                .append("FROM ").append(ExperimentService.get().getTinfoExperimentRun(), "r").append("\n")
                .append("INNER JOIN ").append(OntologyManager.getTinfoObject(), "o").append(" ON o.objectUri = r.lsid\n")
                .append("INNER JOIN ").append(OntologyManager.getTinfoObjectProperty(), "op").append(" ON op.objectId = o.objectId\n")
                .append("WHERE r.container = ?\n").add(c.getId())
                .append("AND op.propertyId ").appendInClause(plateTemplatePropIds, AssayDbSchema.getInstance().getSchema().getSqlDialect()).append("\n")
                .append("AND op.stringvalue = ?").add(plate.getLSID());

        return new SqlSelector(ExperimentService.get().getSchema(), sql);
    }

    @Override
    @NotNull
    public Plate createPlate(Container container, String templateType, int rowCount, int colCount)
    {
        return new PlateImpl(container, null, templateType, rowCount, colCount);
    }

    @Override
    @NotNull
    public Plate createPlateTemplate(Container container, String templateType, int rowCount, int colCount)
    {
        Plate plate = createPlate(container, templateType, rowCount, colCount);
        ((PlateImpl)plate).setTemplate(true);

        return plate;
    }

    @Override
    public @Nullable Plate getPlate(Container container, int rowId)
    {
        return PlateCache.getPlate(container, rowId);
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

    /**
     * Checks to see if there is a plate with the same name in the folder
     */
    public boolean plateExists(Container c, String name)
    {
        return PlateCache.getPlate(c, name) != null;
    }

    private Collection<Plate> getPlates(Container c)
    {
        return PlateCache.getPlates(c);
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

    protected void populatePlate(PlateImpl plate)
    {
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

    private String getLsid(Plate plate, Class<?> type, boolean instance)
    {
        return getLsid(type, plate.getContainer(), plate.isTemplate(), instance).toString();
    }

    public Lsid getLsid(Class<?> type, Container container, boolean isTemplate, boolean isInstance)
    {
        String nameSpace;
        if (type == Plate.class)
            nameSpace = isTemplate ? "PlateTemplate" : "PlateInstance";
        else if (type == WellGroup.class)
            nameSpace = isTemplate ? "WellGroupTemplate" : "WellGroupInstance";
        else if (type == Well.class)
            nameSpace = isTemplate ? "WellTemplate" : "WellInstance";
        else
            throw new IllegalArgumentException("Unknown type " + type);

        String id;
        if (isInstance)
            id = GUID.makeGUID();
        else
            id = LSID_CLASS_OBJECT_ID;
        return new Lsid(nameSpace, "Folder-" + container.getRowId(), id);
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
            String plateInstanceLsid = plate.getLSID();
            String plateObjectLsid;
            Map<String, Object> plateRow = ObjectFactory.Registry.getFactory(PlateImpl.class).toMap(plate, new ArrayListMap<>());
            QueryUpdateService qus = getPlateUpdateService(container, user);
            BatchValidationException errors = new BatchValidationException();

            if (updateExisting)
            {
                // replace the GUID objectId with the fixed "objectType" value
                Lsid lsid = Lsid.parse(plateInstanceLsid);
                plateObjectLsid = lsid.edit().setObjectId(LSID_CLASS_OBJECT_ID).toString();

                // special case if the plate name changes, we want to remove the cache key with the old name
                Plate oldPlate = getPlate(container, plateId);
                if (!oldPlate.getName().equals(plate.getName()))
                    clearCache(container, oldPlate);

                qus.updateRows(user, container, Collections.singletonList(plateRow), null, errors, null, null);
                if (errors.hasErrors())
                    throw errors;
            }
            else
            {
                plateObjectLsid = getLsid(plate, Plate.class, false);
                List<Map<String, Object>> insertedRows = qus.insertRows(user, container, Collections.singletonList(plateRow), errors, null, null);
                if (errors.hasErrors())
                    throw errors;
                plateId = (Integer)insertedRows.get(0).get("RowId");
                plateInstanceLsid = (String)insertedRows.get(0).get("Lsid");
                plate.setRowId(plateId);
                plate.setLsid(plateInstanceLsid);
            }
            savePropertyBag(container, plateInstanceLsid, plateObjectLsid, plate.getProperties(), updateExisting);

            // delete well groups first
            List<WellGroupImpl> deletedWellGroups = plate.getDeletedWellGroups();
            for (WellGroupImpl deletedWellGroup : deletedWellGroups)
            {
                assert deletedWellGroup.getRowId() != null && deletedWellGroup.getRowId() > 0;
                LOG.debug("Deleting well group: name=" + deletedWellGroup.getName() + ", rowId=" + deletedWellGroup.getRowId());
                deleteWellGroup(container, user, deletedWellGroup.getRowId());
            }

            QueryUpdateService wellGroupQus = getWellGroupUpdateService(container, user);
            for (WellGroup group : plate.getWellGroupTemplates(null))
            {
                WellGroupImpl wellgroup = (WellGroupImpl) group;
                assert !wellgroup._deleted;
                String wellGroupInstanceLsid = wellgroup.getLSID();
                String wellGroupObjectLsid;
                Map<String, Object> wellGroupRow;
                BatchValidationException wellGroupErrors = new BatchValidationException();

                if (wellgroup.getRowId() != null && wellgroup.getRowId() > 0)
                {
                    // replace the GUID objectId with the fixed "objectType" value
                    Lsid lsid = Lsid.parse(wellGroupInstanceLsid);
                    wellGroupObjectLsid = lsid.edit().setObjectId(LSID_CLASS_OBJECT_ID).toString();

                    wellGroupRow = ObjectFactory.Registry.getFactory(WellGroupBean.class).toMap(WellGroupBean.from(wellgroup), new ArrayListMap<>());
                    wellGroupQus.updateRows(user, container, Collections.singletonList(wellGroupRow), null, wellGroupErrors, null, null);
                    if (wellGroupErrors.hasErrors())
                        throw wellGroupErrors;

                    savePropertyBag(container, wellGroupInstanceLsid, wellGroupObjectLsid, wellgroup.getProperties(), true);
                }
                else
                {
                    wellGroupObjectLsid = getLsid(plate, WellGroup.class, false);
                    wellgroup.setPlateId(plateId);
                    wellGroupRow = ObjectFactory.Registry.getFactory(WellGroupBean.class).toMap(WellGroupBean.from(wellgroup), new ArrayListMap<>());

                    List<Map<String, Object>> insertedRows = wellGroupQus.insertRows(user, container, Collections.singletonList(wellGroupRow), wellGroupErrors, null, null);
                    if (wellGroupErrors.hasErrors())
                        throw wellGroupErrors;

                    wellGroupInstanceLsid = (String)insertedRows.get(0).get("Lsid");
                    wellgroup = ObjectFactory.Registry.getFactory(WellGroupImpl.class).fromMap(wellgroup, insertedRows.get(0));
                    savePropertyBag(container, wellGroupInstanceLsid, wellGroupObjectLsid, wellgroup.getProperties(), false);
                }
            }

            String wellInstanceLsidPrefix = null;

            // Get existing wells for the plate
            Map<Pair<Integer, Integer>, PositionImpl> existingPositionMap = new HashMap<>();
            if (updateExisting)
            {
                for (Well existingPosition : plate.getWells())
                {
                    existingPositionMap.put(Pair.of(existingPosition.getRow(), existingPosition.getColumn()), (PositionImpl) existingPosition);
                }
            }
            else
            {
                wellInstanceLsidPrefix = getLsid(plate, Well.class, true);
            }

            List<List<Integer>> wellGroupPositions = new LinkedList<>();
            QueryUpdateService wellQus = getWellUpdateService(container, user);
            for (int row = 0; row < plate.getRows(); row++)
            {
                for (int col = 0; col < plate.getColumns(); col++)
                {
                    PositionImpl position;
                    if (updateExisting)
                    {
                        position = existingPositionMap.get(Pair.of(row, col));
                        assert position != null;
                        assert position.getRowId() != null && position.getRowId() > 0;
                        assert position.getLsid() != null;

                        Lsid lsid = Lsid.parse(position.getLsid());
                        String objectId = lsid.getObjectId();
                        assert objectId.endsWith("-well-" + row + "-" + col);
                    }
                    else
                    {
                        position = plate.getPosition(row, col);
                        assert position.getRowId() == null || position.getRowId() == 0;
                        assert wellInstanceLsidPrefix != null;

                        position.setPlateId(plateId);
                        Map<String, Object> wellRow = ObjectFactory.Registry.getFactory(PositionImpl.class).toMap(position, new ArrayListMap<>());
                        BatchValidationException wellErrors = new BatchValidationException();

                        List<Map<String, Object>> insertedRows = wellQus.insertRows(user, container, Collections.singletonList(wellRow), wellErrors, null, null);
                        if (wellErrors.hasErrors())
                            throw wellErrors;

                        position = ObjectFactory.Registry.getFactory(PositionImpl.class).fromMap(position, insertedRows.get(0));
                    }

                    // collect well group positions to save
                    wellGroupPositions.addAll(getWellGroupPositions(plate, position));
                }
            }

            // delete all existing well group positions for the plate
            if (updateExisting)
            {
                deleteWellGroupPositions(plate);
            }

            // save well group positions
            String insertSql = "INSERT INTO " + AssayDbSchema.getInstance().getTableInfoWellGroupPositions() +
                    " (wellId, wellGroupId) VALUES (?, ?)";
            Table.batchExecute(AssayDbSchema.getInstance().getSchema(), insertSql, wellGroupPositions);

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
    private List<List<Integer>> getWellGroupPositions(Plate plate, PositionImpl position)
    {
        List<WellGroup> groups = plate.getWellGroups(position);
        List<List<Integer>> wellGroupPositions = new ArrayList<>(groups.size());

        for (WellGroup group : groups)
        {
            if (group.contains(position))
            {
                assert position.getRowId() > 0;
                assert group.getRowId() != null && group.getRowId() > 0;
                Integer wellId = position.getRowId();
                Integer wellGroupId = group.getRowId();
                wellGroupPositions.add(List.of(wellId, wellGroupId));
            }
        }

        return wellGroupPositions;
    }

    private void savePropertyBag(Container container, String ownerLsid,
                                 String classLsid, Map<String, Object> props,
                                 boolean updateExisting) throws SQLException
    {
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
                OntologyManager.insertProperties(container, ownerLsid, objectProperties);
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
        final AssayDbSchema schema = AssayDbSchema.getInstance();

        Plate plate = PlateCache.getPlate(container, plateId);
        List<String> lsids = new ArrayList<>();
        lsids.add(plate.getLSID());
        for (WellGroup wellgroup : plate.getWellGroups())
            lsids.add(wellgroup.getLSID());
        for (Well well : plate.getWells())
            lsids.add(well.getLsid());

        SimpleFilter plateIdFilter = SimpleFilter.createContainerFilter(container);
        plateIdFilter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());

        OntologyManager.deleteOntologyObjects(container, lsids.toArray(new String[lsids.size()]));
        deleteWellGroupPositions(plate);

        // delete any plate metadata values
        SQLFragment sql = new SQLFragment("SELECT Lsid FROM ")
                .append(AssayDbSchema.getInstance().getTableInfoWell(), "")
                .append(" WHERE PlateId = ?")
                .add(plateId);
        OntologyManager.deleteOntologyObjects(AssayDbSchema.getInstance().getSchema(), sql, container, false);

        // delete PlateProperty mappings
        SQLFragment sql2 = new SQLFragment("DELETE FROM ")
                .append(AssayDbSchema.getInstance().getTableInfoPlateProperty(), "")
                .append(" WHERE PlateId = ?")
                .add(plateId);
        new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql2);

        Table.delete(schema.getTableInfoWell(), plateIdFilter);
        Table.delete(schema.getTableInfoWellGroup(), plateIdFilter);
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
        final AssayDbSchema schema = AssayDbSchema.getInstance();
        new SqlExecutor(schema.getScope()).execute("" +
                "DELETE FROM " + schema.getTableInfoWellGroupPositions() + " WHERE wellId IN " +
                "(SELECT rowId FROM " + schema.getTableInfoWell() + " WHERE container=?)", container.getId());

        // delete any plate metadata values
        SQLFragment sql = new SQLFragment("SELECT Lsid FROM ")
                .append(AssayDbSchema.getInstance().getTableInfoWell(), "AW")
                .append(" WHERE Container = ?")
                .add(container);
        OntologyManager.deleteOntologyObjects(AssayDbSchema.getInstance().getSchema(), sql, container, false);

        // delete PlateProperty mappings
        SQLFragment sql2 = new SQLFragment("DELETE FROM ")
                .append(AssayDbSchema.getInstance().getTableInfoPlateProperty(), "")
                .append(" WHERE PlateId IN (SELECT RowId FROM ").append(AssayDbSchema.getInstance().getTableInfoPlate(), "AP")
                .append(" WHERE Container = ? )")
                .add(container);
        new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql2);

        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        Table.delete(schema.getTableInfoWell(), filter);
        Table.delete(schema.getTableInfoWellGroup(), filter);
        Table.delete(schema.getTableInfoPlate(), filter);
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

    public List<PlateTypeHandler> getPlateTypeHandlers()
    {
        List<PlateTypeHandler> result = new ArrayList<>(_plateTypeHandlers.values());
        result.sort(Comparator.comparing(PlateTypeHandler::getAssayType, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public PlateTypeHandler getPlateTypeHandler(String plateTypeName)
    {
        return _plateTypeHandlers.get(plateTypeName);
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
        TableInfo tableInfo = schema.getTable(PlateTable.NAME);
        QueryUpdateService qus = tableInfo.getUpdateService();
        if (qus == null)
            throw new IllegalStateException("Unable to resolve QueryUpdateService for Plates.");

        return qus;
    }

    private @NotNull QueryUpdateService getWellGroupUpdateService(Container container, User user)
    {
        UserSchema schema = getPlateUserSchema(container, user);
        TableInfo tableInfo = schema.getTable(WellGroupTable.NAME);
        QueryUpdateService qus = tableInfo.getUpdateService();
        if (qus == null)
            throw new IllegalStateException("Unable to resolve QueryUpdateService for Well Groups.");

        return qus;
    }

    private @NotNull QueryUpdateService getWellUpdateService(Container container, User user)
    {
        UserSchema schema = getPlateUserSchema(container, user);
        TableInfo tableInfo = schema.getTable(WellTable.NAME);
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
        PlateLsidHandler plateHandler = new PlateLsidHandler();
        WellGroupLsidHandler wellgroupHandler = new WellGroupLsidHandler();
        LsidManager.get().registerHandler("PlateTemplate", plateHandler);
        LsidManager.get().registerHandler("PlateInstance", plateHandler);
        LsidManager.get().registerHandler("WellGroupTemplate", wellgroupHandler);
        LsidManager.get().registerHandler("WellGroupInstance", wellgroupHandler);
        _lsidHandlersRegistered = true;
    }

    @Override
    public Plate copyPlate(Plate source, User user, Container destContainer)
            throws Exception
    {
        Plate destination = getPlate(destContainer, source.getName());
        if (destination != null)
            throw new PlateService.NameConflictException(source.getName());
        destination = createPlateTemplate(destContainer, source.getType(), source.getRows(), source.getColumns());
        destination.setName(source.getName());
        for (String property : source.getPropertyNames())
            destination.setProperty(property, source.getProperty(property));
        for (WellGroup originalGroup : source.getWellGroups())
        {
            List<Position> positions = new ArrayList<>();
            for (Position position : originalGroup.getPositions())
                positions.add(destination.getPosition(position.getRow(), position.getColumn()));
            WellGroup copyGroup = destination.addWellGroup(originalGroup.getName(), originalGroup.getType(), positions);
            for (String property : originalGroup.getPropertyNames())
                copyGroup.setProperty(property, originalGroup.getProperty(property));
        }
        save(destContainer, user, destination);
        return getPlate(destContainer, destination.getName());
    }

    @Override
    public void registerPlateTypeHandler(PlateTypeHandler handler)
    {
        if (_plateTypeHandlers.containsKey(handler.getAssayType()))
        {
            throw new IllegalArgumentException(handler.getAssayType());
        }
        _plateTypeHandlers.put(handler.getAssayType(), handler);
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

    public List<PlateType> getPlateTypes()
    {
        List<PlateType> plateTypes = new ArrayList<>();

        for (PlateTypeHandler handler : getPlateTypeHandlers())
        {
            for (Pair<Integer, Integer> size : handler.getSupportedPlateSizes())
            {
                int rows = size.first;
                int cols = size.second;

                int wellCount = rows * cols;
                String sizeDescription = wellCount + " well (" + rows + "x" + cols + ") ";

                List<String> types = handler.getTemplateTypes(size);
                if (types == null || types.isEmpty())
                {
                    String description = sizeDescription + handler.getAssayType();
                    plateTypes.add(new PlateType(handler.getAssayType(), null, description, rows, cols));
                }
                else
                {
                    for (String type : types)
                    {
                        String description = sizeDescription + handler.getAssayType() + " " + type;
                        plateTypes.add(new PlateType(handler.getAssayType(), type, description, rows, cols));
                    }
                }
            }
        }

        return plateTypes;
    }

    public PlateType getPlateType(@NotNull Plate plate)
    {
        for (PlateType plateType : getPlateTypes())
        {
            if (
                plateType.getRows() == plate.getRows() &&
                Objects.equals(plateType.getCols(), plateType.getCols()) &&
                Objects.equals(plateType.getType(), plate.getType())
            )
            {
                return plateType;
            }
        }

        return null;
    }

    public @NotNull Map<String, List<Map<String, Integer>>> getPlateOperationConfirmationData(
        @NotNull Container container,
        @NotNull Set<Integer> plateRowIds
    )
    {
        Set<Integer> permittedIds = new HashSet<>(plateRowIds);
        Set<Integer> notPermittedIds = new HashSet<>();

        ExperimentService.get().getObjectReferencers().forEach(referencer ->
                notPermittedIds.addAll(referencer.getItemsWithReferences(permittedIds, "plate")));
        permittedIds.removeAll(notPermittedIds);

        // TODO: This is really expensive. Find a way to consolidate this check into a single query.
        permittedIds.forEach(plateRowId -> {
            Plate plate = getPlate(container, plateRowId);
            if (plate == null || getRunCountUsingPlate(container, plate) > 0)
                notPermittedIds.add(plateRowId);
        });
        permittedIds.removeAll(notPermittedIds);

        return Map.of(
        "allowed", permittedIds.stream().map(rowId -> Map.of("RowId", rowId)).toList(),
            "notAllowed", notPermittedIds.stream().map(rowId -> Map.of("RowId", rowId)).toList()
        );
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
        DomainKind vocabDomainKind = PropertyService.get().getDomainKindByName("Vocabulary");

        if (vocabDomainKind == null)
            return null;

        // the domain is scoped at the project level (project and subfolder scoping)
        String domainURI = vocabDomainKind.generateDomainURI(null, PLATE_WELL_DOMAIN, getPlateMetadataDomainContainer(container), user);
        return PropertyService.get().getDomain(container, domainURI);
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
        Domain vocabDomain = getPlateMetadataDomain(container, user);

        if (vocabDomain == null)
        {
            DomainKind domainKind = PropertyService.get().getDomainKindByName("Vocabulary");
            Container domainContainer = getPlateMetadataDomainContainer(container);

            if (!domainKind.canCreateDefinition(user, domainContainer))
                throw new IllegalArgumentException("Unable to create the plate well domain in folder: " + domainContainer.getPath() + "\". Insufficient permissions.");

            vocabDomain = DomainUtil.createDomain("Vocabulary", new GWTDomain(), null, domainContainer, user, PLATE_WELL_DOMAIN, null);
        }
        return vocabDomain;
    }

    /**
     * Adds custom fields to the well domain
     */
    public @NotNull List<PlateCustomField> createPlateMetadataFields(Container container, User user, List<GWTPropertyDescriptor> fields) throws Exception
    {
        Domain vocabDomain = ensurePlateMetadataDomain(container, user);
        DomainKind domainKind = vocabDomain.getDomainKind();

        if (vocabDomain == null)
            throw new IllegalArgumentException("Unable to create fields on the domain, the domain was not found.");

        if (!domainKind.canEditDefinition(user, vocabDomain))
            throw new IllegalArgumentException("Unable to create field on domain \"" + vocabDomain.getTypeURI() + "\". Insufficient permissions.");

        if (!fields.isEmpty())
        {
            try (DbScope.Transaction tx = ExperimentService.get().ensureTransaction())
            {
                Set<String> existingProperties = vocabDomain.getProperties().stream().map(ImportAliasable::getName).collect(Collectors.toSet());
                for (GWTPropertyDescriptor pd : fields)
                {
                    if (existingProperties.contains(pd.getName()))
                        throw new IllegalStateException(String.format("Unable to create field: %s on domain: %s. The field already exists.", pd.getName(), vocabDomain.getTypeURI()));

                    DomainUtil.addProperty(vocabDomain, pd, new HashMap<>(), new HashSet<>(), null);
                }
                vocabDomain.save(user);
                tx.commit();
            }
        }
        return getPlateMetadataFields(container, user);
    }

    public @NotNull List<PlateCustomField> deletePlateMetadataFields(Container container, User user, List<PlateCustomField> fields) throws Exception
    {
        Domain vocabDomain = getPlateMetadataDomain(container, user);

        if (vocabDomain == null)
            throw new IllegalArgumentException("Unable to remove fields from the domain, the domain was not found.");

        if (!vocabDomain.getDomainKind().canEditDefinition(user, vocabDomain))
            throw new IllegalArgumentException("Unable to remove fields on domain \"" + vocabDomain.getTypeURI() + "\". Insufficient permissions.");

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
                Set<String> existingProperties = vocabDomain.getProperties().stream().map(ImportAliasable::getPropertyURI).collect(Collectors.toSet());
                for (PlateCustomField field : fields)
                {
                    if (!existingProperties.contains(field.getPropertyURI()))
                        throw new IllegalStateException(String.format("Unable to remove field: %s on domain: %s. The field does not exist.", field.getName(), vocabDomain.getTypeURI()));

                    DomainProperty dp = vocabDomain.getPropertyByURI(field.getPropertyURI());
                    dp.delete();
                }
                vocabDomain.save(user);
                tx.commit();
            }
        }
        return getPlateMetadataFields(container, user);
    }

    public @NotNull List<PlateCustomField> getPlateMetadataFields(Container container, User user)
    {
        Domain vocabDomain = getPlateMetadataDomain(container, user);
        if (vocabDomain != null)
        {
            List<PlateCustomField> fields = vocabDomain.getProperties().stream().map(PlateCustomField::new).toList();
            return fields.stream()
                    .sorted(Comparator.comparing(PlateCustomField::getName))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public @NotNull List<PlateCustomField> addFields(Container container, User user, Integer plateId, List<PlateCustomField> fields) throws SQLException
    {
        if (plateId == null)
            throw new IllegalArgumentException("Failed to add plate custom fields. Invalid plateId provided.");

        if (fields == null || fields.size() == 0)
            throw new IllegalArgumentException("Failed to add plate custom fields. No fields specified.");

        Plate plate = getPlate(container, plateId);
        if (plate == null)
            throw new IllegalArgumentException("Failed to add plate custom fields. Plate id \"" + plateId + "\" not found.");

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

    public List<PlateCustomField> getFields(Container container, Integer plateId)
    {
        Plate plate = getPlate(container, plateId);
        if (plate == null)
            throw new IllegalArgumentException("Failed to get plate custom fields. Plate id \"" + plateId + "\" not found.");

        return plate.getCustomFields();
    }

    /**
     * Returns the list of custom properties associated with a plate
     */
    private List<DomainProperty> _getFields(Container container, User user, Integer plateId)
    {
        Plate plate = getPlate(container, plateId);
        if (plate == null)
            throw new IllegalArgumentException("Failed to get plate custom fields. Plate id \"" + plateId + "\" not found.");

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

    public List<WellCustomField> getWellCustomFields(Container container, User user, Integer plateId, Integer wellId)
    {
        List<WellCustomField> fields = _getFields(container, user, plateId).stream().map(WellCustomField::new).toList();

        // need to get the well values associated with each custom field
        Plate plate = getPlate(container, plateId);
        Well well = plate.getWell(wellId);
        if (well == null)
            throw new IllegalArgumentException("Failed to get well custom fields. Well id \"" + wellId   + "\" not found.");

        Map<String, Object> properties = OntologyManager.getProperties(container, well.getLsid());
        for (WellCustomField field : fields)
            field.setValue(properties.get(field.getPropertyURI()));

        return fields.stream()
                .sorted(Comparator.comparing(PlateCustomField::getName))
                .collect(Collectors.toList());
    }

    public List<PlateCustomField> removeFields(Container container, User user, Integer plateId, List<PlateCustomField> fields)
    {
        Plate plate = getPlate(container, plateId);
        if (plate == null)
            throw new IllegalArgumentException("Failed to remove plate custom fields. Plate id \"" + plateId + "\" not found.");

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
                List<String> propertyURIs = fieldsToRemove.stream().map(DomainProperty::getPropertyURI).collect(Collectors.toList());
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

    public static final class TestCase
    {
        @Test
        public void createPlateTemplate() throws Exception
        {
            final Container c = JunitUtil.getTestContainer();

            PlateManager.get().deleteAllPlateData(c);

            //
            // INSERT
            //

            PlateTypeHandler handler = PlateManager.get().getPlateTypeHandler(TsvPlateTypeHandler.TYPE);
            Plate template = handler.createTemplate("UNUSED", c, 8, 12);
            template.setName("bob");
            template.setProperty("friendly", "yes");
            assertNull(template.getRowId());
            assertNull(template.getLSID());

            WellGroup wg1 = template.addWellGroup("wg1", WellGroup.Type.SAMPLE,
                    PlateService.get().createPosition(c, 0, 0),
                    PlateService.get().createPosition(c, 0, 11));
            wg1.setProperty("score", "100");
            assertNull(wg1.getRowId());
            assertNull(wg1.getLSID());

            int plateId = PlateService.get().save(c, TestContext.get().getUser(), template);

            //
            // VERIFY INSERT
            //

            assertEquals(1, PlateManager.get().getPlateTemplates(c).size());

            Plate savedTemplate = PlateService.get().getPlate(c, "bob");
            assertEquals(plateId, savedTemplate.getRowId().intValue());
            assertEquals("bob", savedTemplate.getName());
            assertEquals("yes", savedTemplate.getProperty("friendly")); assertNotNull(savedTemplate.getLSID());

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
                    PlateService.get().createPosition(c, 1, 0),
                    PlateService.get().createPosition(c, 1, 11));

            // rename existing well group
            ((WellGroupImpl)savedWg1).setName("wg1_renamed");

            // add positions
            controlWellGroups.get(0).setPositions(List.of(
                    PlateService.get().createPosition(c, 0, 0),
                    PlateService.get().createPosition(c, 0, 1)));

            // delete well group
            ((PlateImpl)savedTemplate).markWellGroupForDeletion((WellGroupImpl)controlWellGroups.get(1));

            int newPlateId = PlateService.get().save(c, TestContext.get().getUser(), savedTemplate);
            assertEquals(savedTemplate.getRowId().intValue(), newPlateId);

            //
            // VERIFY UPDATE
            //

            // verify plate
            Plate updatedTemplate = PlateService.get().getPlate(c, plateId);
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

            //
            // DELETE
            //

            PlateService.get().deletePlate(c, TestContext.get().getUser(), updatedTemplate.getRowId());

            assertNull(PlateService.get().getPlate(c, updatedTemplate.getRowId()));
            assertEquals(0, PlateManager.get().getPlateTemplates(c).size());
        }

        @Test
        public void testCreateAndSavePlate() throws Exception
        {
            // Arrange
            Container container = JunitUtil.getTestContainer();
            User user = TestContext.get().getUser();
            PlateType plateType = new PlateType(TsvPlateTypeHandler.TYPE, TsvPlateTypeHandler.BLANK_PLATE, "Test plate type", 8, 12);

            // Act
            Plate plate = PlateManager.get().createAndSavePlate(container, user, plateType, "testCreateAndSavePlate plate");

            // Assert
            assertTrue("Expected plate to have been persisted and provided with a rowId", plate.getRowId() > 0);
        }

        @Test
        public void testCreatePlateTemplates() throws Exception
        {
            final Container c = JunitUtil.getTestContainer();
            final User user = TestContext.get().getUser();
            PlateService.get().deleteAllPlateData(c);

            // Verify plate service assumptions about plate templates
            Plate plate = PlateService.get().createPlateTemplate(c, TsvPlateTypeHandler.TYPE, 16, 24);
            plate.setName("my plate template");
            int plateId = PlateService.get().save(c, user, plate);

            // Assert
            assertTrue("Expected saved plateId to be returned", plateId != 0);
            assertTrue("Expected saved plate to have the template field set to true", PlateService.get().getPlate(c, plateId).isTemplate());

            // Verify only plate templates are returned
            plate = PlateService.get().createPlate(c, TsvPlateTypeHandler.TYPE, 8, 12);
            plate.setName("non plate template");
            PlateService.get().save(c, user, plate);

            List<Plate> plates = PlateService.get().getPlateTemplates(c);
            assertEquals("Expected only a single plate to be returned", 1, plates.size());
            for (Plate template : plates)
            {
                assertTrue("Expected saved plate to have the template field set to true", template.isTemplate());
            }
        }

        @Test
        public void testCreatePlateMetadata() throws Exception
        {
            final Container c = JunitUtil.getTestContainer();
            final User user = TestContext.get().getUser();

            PlateService.get().deleteAllPlateData(c);
            Domain domain = PlateManager.get().getPlateMetadataDomain(c ,user);
            if (domain != null)
                domain.delete(user);

            Plate plate = PlateService.get().createPlateTemplate(c, TsvPlateTypeHandler.TYPE, 16, 24);
            plate.setName("new plate with metadata");
            int plateId = PlateService.get().save(c, user, plate);

            // Assert
            assertTrue("Expected saved plateId to be returned", plateId != 0);

            // create custom properties
            List<GWTPropertyDescriptor> customFields = List.of(
                    new GWTPropertyDescriptor("barcode", "http://www.w3.org/2001/XMLSchema#string"),
                    new GWTPropertyDescriptor("concentration", "http://www.w3.org/2001/XMLSchema#double"),
                    new GWTPropertyDescriptor("negativeControl", "http://www.w3.org/2001/XMLSchema#double"));

            List<PlateCustomField> fields = PlateManager.get().createPlateMetadataFields(c, user, customFields);

            // Verify returned sorted by name
            assertTrue("Expected plate custom fields", fields.size() == 3);
            assertTrue("Expected barcode custom field", fields.get(0).getName().equals("barcode"));
            assertTrue("Expected concentration custom field", fields.get(1).getName().equals("concentration"));
            assertTrue("Expected negativeControl custom field", fields.get(2).getName().equals("negativeControl"));

            // assign custom fields to the plate
            assertTrue("Expected custom fields to be added to the plate", PlateManager.get().addFields(c, user, plateId, fields).size() == 3);

            // verification when adding custom fields to the plate
            try
            {
                PlateManager.get().addFields(c, user, plateId, fields);
                fail("Expected a validation error when adding existing fields");
            }
            catch (IllegalArgumentException e)
            {
                assertTrue("Expected validation exception", e.getMessage().equals("Failed to add plate custom fields. Custom field \"barcode\" already is associated with this plate."));
            }

            // remove a plate custom field
            fields = PlateManager.get().removeFields(c, user, plateId, List.of(fields.get(0)));
            assertTrue("Expected 2 plate custom fields", fields.size() == 2);
            assertTrue("Expected concentration custom field", fields.get(0).getName().equals("concentration"));
            assertTrue("Expected negativeControl custom field", fields.get(1).getName().equals("negativeControl"));

            // select wells
            SimpleFilter filter = SimpleFilter.createContainerFilter(c);
            filter.addCondition(FieldKey.fromParts("PlateId"), plateId);
            filter.addCondition(FieldKey.fromParts("Row"), 0);
            List< org.labkey.assay.plate.model.Well> wells = new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, new Sort("Col")).getArrayList(org.labkey.assay.plate.model.Well.class);

            assertTrue("Expected 24 wells to be returned", wells.size() == 24);

            // update
            TableInfo wellTable = QueryService.get().getUserSchema(user, c, PlateSchema.SCHEMA_NAME).getTable(WellTable.NAME);
            QueryUpdateService qus = wellTable.getUpdateService();
            BatchValidationException errors = new BatchValidationException();

            // verify metadata update works for Property URI as well as field key
            org.labkey.assay.plate.model.Well well = wells.get(0);
            List<Map<String, Object>> rows = List.of(CaseInsensitiveHashMap.of(
                    "rowid", well.getRowId(),
                    fields.get(0).getPropertyURI(), 1.25,       // concentration
                    fields.get(1).getPropertyURI(), 5.25            // negativeControl
            ));

            qus.updateRows(user, c, rows, null, errors, null, null);
            if (errors.hasErrors())
                fail(errors.getMessage());

            well = wells.get(1);
            rows = List.of(CaseInsensitiveHashMap.of(
                    "rowid", well.getRowId(),
                    "properties/concentration", 2.25,
                    "properties/negativeControl", 6.25
            ));

            qus.updateRows(user, c, rows, null, errors, null, null);
            if (errors.hasErrors())
                fail(errors.getMessage());

            ColumnInfo colConcentration = wellTable.getColumn("properties/concentration");
            ColumnInfo colNegControl = wellTable.getColumn("properties/negativeControl");

            // verify vocab property updates
            try (Results r = QueryService.get().select(wellTable, List.of(colConcentration, colNegControl), filter, new Sort("Col")))
            {
                int row = 0;
                while (r.next())
                {
                    if (row == 0)
                    {
                        assertEquals(1.25, r.getDouble(colConcentration.getFieldKey()), 0);
                        assertEquals(5.25, r.getDouble(colNegControl.getFieldKey()), 0);
                    }
                    else if (row == 1)
                    {
                        assertEquals(2.25, r.getDouble(colConcentration.getFieldKey()), 0);
                        assertEquals(6.25, r.getDouble(colNegControl.getFieldKey()), 0);
                    }
                    else
                    {
                        // the remainder should be null
                        assertEquals(0, r.getDouble(colConcentration.getFieldKey()), 0);
                        assertEquals(0, r.getDouble(colNegControl.getFieldKey()), 0);
                    }
                    row++;
                }
            }
        }
    }
}
