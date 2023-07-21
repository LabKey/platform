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
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateTypeHandler;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ObjectFactory;
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
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:13:08 AM
 */
public class PlateManager implements PlateService
{
    private static final Logger LOG = LogManager.getLogger(PlateManager.class);
    private static final String LSID_CLASS_OBJECT_ID = "objectType";

    private final List<PlateService.PlateDetailsResolver> _detailsLinkResolvers = new ArrayList<>();
    private boolean _lsidHandlersRegistered = false;
    private final Map<String, PlateTypeHandler> _plateTypeHandlers = new HashMap<>();

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

    /**
     * Note that this does not use the cache.
     */
    public @Nullable Plate getPlate(String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("lsid"), lsid);
        PlateImpl plate = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getObject(PlateImpl.class);
        populatePlate(plate);

        return plate;
    }

    /**
     * Checks to see if there is a plate with the same name in the folder
     */
    public boolean plateExists(Container c, String name)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("Name"), name);

        return new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getRowCount() > 0;
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

        Position[][] positionArray;
        if (plate.isTemplate())
            positionArray = new Position[plate.getRows()][plate.getColumns()];
        else
            positionArray = new WellImpl[plate.getRows()][plate.getColumns()];

        // get position objects
        PositionImpl[] positions = getPositions(plate);

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
        for (PositionImpl position : positions)
        {
            positionArray[position.getRow()][position.getColumn()] = position;

            Set<Integer> wellGroupIds = wellToWellGroups.get(position.getRowId());
            if (wellGroupIds != null)
            {
                for (Integer wellGroupId : wellGroupIds)
                {
                    List<PositionImpl> groupPositions = groupIdToPositions.computeIfAbsent(wellGroupId, k -> new ArrayList<>());
                    groupPositions.add(position);
                }
            }
        }

        // not sure if this would ever be true
        if (positionArray instanceof WellImpl[][] wells)
            plate.setWells(wells);

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
    }

    private PositionImpl[] getPositions(Plate plate)
    {
        SimpleFilter plateFilter = new SimpleFilter(FieldKey.fromParts("PlateId"), plate.getRowId());
        Sort sort = new Sort("Col,Row");
        Class<? extends PositionImpl> clazz = plate.isTemplate() ? PositionImpl.class : WellImpl.class;
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), plateFilter, sort).getArray(clazz);

    }

    private WellGroupImpl[] getWellGroups(Plate plate)
    {
        SimpleFilter plateFilter = new SimpleFilter(FieldKey.fromParts("PlateId"), plate.getRowId());
        Class<WellGroupImpl> clazz = plate.isTemplate() ? WellGroupImpl.class : WellGroupImpl.class;
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoWellGroup(), plateFilter, null).getArray(clazz);
    }

    private String getLsid(Plate plate, Class type, boolean instance)
    {
        return getLsid(type, plate.getContainer(), plate.isTemplate(), instance).toString();
    }

    public Lsid getLsid(Class type, Container container, boolean isTemplate, boolean isInstance)
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
                for (PositionImpl existingPosition : getPositions(plate))
                {
                    existingPositionMap.put(Pair.of(existingPosition.getRow(), existingPosition.getCol()), existingPosition);
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

            transaction.addCommitTask(() -> PlateManager.get().clearCache(container), DbScope.CommitTaskOption.POSTCOMMIT);
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
                wellGroupPositions.add(List.<Integer>of(wellId, wellGroupId));
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

    // Called by the Plate Query Update Service prior to deleting a plate
    public void beforePlateDelete(Container container, Integer plateId)
    {
        final AssayDbSchema schema = AssayDbSchema.getInstance();

        SimpleFilter plateFilter = SimpleFilter.createContainerFilter(container);
        plateFilter.addCondition(FieldKey.fromParts("RowId"), plateId);
        PlateImpl plate = new TableSelector(schema.getTableInfoPlate(),
                plateFilter, null).getObject(PlateImpl.class);
        WellGroupImpl[] wellgroups = getWellGroups(plate);
        PositionImpl[] positions = getPositions(plate);

        List<String> lsids = new ArrayList<>();
        lsids.add(plate.getLSID());
        for (WellGroupImpl wellgroup : wellgroups)
            lsids.add(wellgroup.getLSID());
        for (PositionImpl position : positions)
            lsids.add(position.getLsid());

        SimpleFilter plateIdFilter = SimpleFilter.createContainerFilter(container);
        plateIdFilter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());

        OntologyManager.deleteOntologyObjects(container, lsids.toArray(new String[lsids.size()]));
        deleteWellGroupPositions(plate);
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

    private @NotNull QueryUpdateService getPlateUpdateService(Container container, User user)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME);
        TableInfo tableInfo = schema.getTable(PlateTable.NAME);
        QueryUpdateService qus = tableInfo.getUpdateService();
        if (qus == null)
            throw new IllegalStateException("Unable to resolve QueryUpdateService for Plates.");

        return qus;
    }

    private @NotNull QueryUpdateService getWellGroupUpdateService(Container container, User user)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME);
        TableInfo tableInfo = schema.getTable(WellGroupTable.NAME);
        QueryUpdateService qus = tableInfo.getUpdateService();
        if (qus == null)
            throw new IllegalStateException("Unable to resolve QueryUpdateService for Well Groups.");

        return qus;
    }

    private @NotNull QueryUpdateService getWellUpdateService(Container container, User user)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, PlateSchema.SCHEMA_NAME);
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

            return PlateManager.get().getPlate(lsid.toString());
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
        Plate destination = PlateService.get().getPlate(destContainer, source.getName());
        if (destination != null)
            throw new PlateService.NameConflictException(source.getName());
        destination = PlateService.get().createPlateTemplate(destContainer, source.getType(), source.getRows(), source.getColumns());
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
        return this.getPlate(destContainer, destination.getName());
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

    public @NotNull Map<String, Collection<Map<String, Object>>> getPlateOperationConfirmationData(
        @NotNull Container container,
        @NotNull Set<Integer> plateRowIds
    )
    {
        List<Map<String, Object>> allowedRows = new ArrayList<>();
        List<Map<String, Object>> notAllowedRows = new ArrayList<>();

        // TODO: This is really expensive. Find a way to consolidate this check into a single query.
        plateRowIds.forEach(plateRowId -> {
            Map<String, Object> rowMap = Map.of("RowId", plateRowId);
            Plate plate = getPlate(container, plateRowId);
            if (plate == null)
                notAllowedRows.add(rowMap);
            else if (getRunCountUsingPlate(container, plate) > 0)
                notAllowedRows.add(rowMap);
            else
                allowedRows.add(rowMap);
        });

        return Map.of("allowed", allowedRows, "notAllowed", notAllowedRows);
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
            int plateId = PlateService.get().save(c, user, plate);

            // Assert
            assertTrue("Expected saved plateId to be returned", plateId != 0);
            assertTrue("Expected saved plate to have the template field set to true", PlateService.get().getPlate(c, plateId).isTemplate());

            // Verify only plate templates are returned
            plate = PlateService.get().createPlate(c, TsvPlateTypeHandler.TYPE, 8, 12);
            PlateService.get().save(c, user, plate);

            List<Plate> plates = PlateService.get().getPlateTemplates(c);
            assertEquals("Expected only a single plate to be returned", 1, plates.size());
            for (Plate template : plates)
            {
                assertTrue("Expected saved plate to have the template field set to true", template.isTemplate());
            }
        }
    }
}
