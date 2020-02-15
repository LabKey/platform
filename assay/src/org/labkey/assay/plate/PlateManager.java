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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.AfterClass;
import org.labkey.assay.query.AssayDbSchema;
import org.junit.Test;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.assay.plate.AbstractPlateTypeHandler;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateTemplate;
import org.labkey.api.assay.plate.PlateTypeHandler;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.assay.plate.WellGroupTemplate;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ActionURL;
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
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.labkey.api.data.CompareType.IN;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:13:08 AM
 */
public class PlateManager implements PlateService
{
    private static final Logger LOG = Logger.getLogger(PlateManager.class);
    private static final String LSID_CLASS_OBJECT_ID = "objectType";

    private List<PlateService.PlateDetailsResolver> _detailsLinkResolvers = new ArrayList<>();
    private boolean _lsidHandlersRegistered = false;
    private Map<String, PlateTypeHandler> _plateTypeHandlers = new HashMap<>();

    public static PlateManager get()
    {
        return (PlateManager) PlateService.get();
    }

    public PlateManager()
    {
        registerPlateTypeHandler(new AbstractPlateTypeHandler()
        {
            @Override
            public PlateTemplate createPlate(String templateTypeName, Container container, int rowCount, int colCount)
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
    public Plate createPlate(PlateTemplate template, double[][] wellValues, @Nullable boolean[][] excluded)
    {
        return createPlate(template, wellValues, excluded, PlateService.NO_RUNID, 1);
    }

    @Override
    public Plate createPlate(PlateTemplate template, double[][] wellValues, @Nullable boolean[][] excluded, int runId, int plateNumber)
    {
        if (template == null)
            return null;
        if (!(template instanceof PlateTemplateImpl))
            throw new IllegalArgumentException("Only plate templates retrieved from the plate service can be used to create plate instances.");
        return new PlateImpl((PlateTemplateImpl) template, wellValues, excluded, runId, plateNumber);
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
    public PlateTemplateImpl getPlateTemplate(Container container, String name)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Template"), Boolean.TRUE);
        filter.addCondition(FieldKey.fromParts("Name"), name);
        filter.addCondition(FieldKey.fromParts("Container"), container);
        PlateTemplateImpl template = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getObject(PlateTemplateImpl.class);
        if (template != null)
        {
            populatePlate(template);
            cache(template);
        }
        return template;
    }

    @Override
    public PlateTemplate getPlateTemplateFromLsid(Container container, String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Template"), Boolean.TRUE);
        filter.addCondition(FieldKey.fromParts("Lsid"), lsid);
        filter.addCondition(FieldKey.fromParts("Container"), container);
        PlateTemplateImpl template = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getObject(PlateTemplateImpl.class);
        if (template != null)
        {
            populatePlate(template);
            cache(template);
        }
        return template;
    }

    @Override
    public PlateTemplateImpl getPlateTemplate(Container container, int plateId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Template"), Boolean.TRUE);
        filter.addCondition(FieldKey.fromParts("RowId"), plateId);
        filter.addCondition(FieldKey.fromParts("Container"), container);
        PlateTemplateImpl template = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getObject(PlateTemplateImpl.class);
        if (template != null)
        {
            populatePlate(template);
            cache(template);
        }
        return template;
    }

    @NotNull
    public List<PlateTemplateImpl> getPlateTemplates(Container container)
    {
        return getPlateTemplates(container, null);
    }

    @NotNull
    public List<PlateTemplateImpl> getPlateTemplates(Container container, @Nullable Collection<Integer> templateIds)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Template"), Boolean.TRUE);
        filter.addCondition(FieldKey.fromParts("Container"), container);
        if (templateIds != null)
            filter.addCondition(FieldKey.fromParts("rowId"), templateIds, IN);

        List<PlateTemplateImpl> templates = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(),
                filter, new Sort("Name")).getArrayList(PlateTemplateImpl.class);
        for (int i = 0; i < templates.size(); i++)
        {
            PlateTemplateImpl template = templates.get(i);
            PlateTemplateImpl cached = getCachedPlateTemplate(container, template.getRowId().intValue());
            if (cached != null)
                templates.set(i, cached);
            else
            {
                populatePlate(template);
                cache(template);
            }
        }
        return templates;
    }

    @Override
    public @NotNull List<PlateTemplateImpl> getPlateTemplatesUsedByAssay(@NotNull Container c, @NotNull ExpProtocol protocol)
    {
        // TODO: caching
        // TODO: need to get the property descriptor used on the run for the PlateTemplate -- faked as 91454 below
        SQLFragment sql = new SQLFragment()
                .append("SELECT * FROM assay.plate p\n")
                .append("WHERE p.template = true\n")
                .append("AND p.container = ?\n").add(c.getId())
                .append("AND p.rowid IN (")
                .append("  SELECT op.floatvalue AS PlateTemplateId\n")
                .append("  FROM exp.experimentrun r\n")
                .append("  INNER JOIN exp.object o ON o.objecturi = r.lsid\n")
                .append("  INNER JOIN exp.objectproperty op On o.objectid = op.objectid\n")
                .append("  WHERE r.protocollsid IN (\n")
                .append("    SELECT lsid\n")
                .append("    FROM exp.protocol\n")
                .append("    WHERE protocol.rowid = ?\n").add(protocol.getRowId())
                .append("  )\n")
                .append("AND op.propertyid = 91454")
                .append(")");

        SqlSelector se = new SqlSelector(ExperimentService.get().getSchema(), sql);
        List<Integer> templateIds = se.getArrayList(Integer.class);
        return getPlateTemplates(c, templateIds);
    }

    @Override
    public PlateTemplateImpl createPlateTemplate(Container container, String templateType, int rowCount, int colCount)
    {
        return new PlateTemplateImpl(container, null, templateType, rowCount, colCount);
    }

    @Override
    public PlateImpl getPlate(Container container, int rowid)
    {
        PlateImpl plate = (PlateImpl) getCachedPlateTemplate(container, rowid);
        if (plate != null)
            return plate;
        plate = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate()).getObject(rowid, PlateImpl.class);
        if (plate == null)
            return null;
        populatePlate(plate);
        cache(plate);
        return plate;
    }

    @Override
    public PlateImpl getPlate(Container container, String entityId)
    {
        PlateImpl plate = (PlateImpl) getCachedPlateTemplate(container, entityId);
        if (plate != null)
            return plate;
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), container).addCondition(FieldKey.fromParts("DataFileId"), entityId);
        plate = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getObject(PlateImpl.class);
        if (plate == null)
            return null;
        populatePlate(plate);
        cache(plate);
        return plate;
    }

    public PlateImpl getPlate(String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("lsid"), lsid);
        PlateImpl plate = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate(), filter, null).getObject(PlateImpl.class);
        if (plate == null)
            return null;
        populatePlate(plate);
        return plate;
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
    public int save(Container container, User user, PlateTemplate plateObj) throws SQLException
    {
        if (!(plateObj instanceof PlateTemplateImpl))
            throw new IllegalArgumentException("Only plate instances created by the plate service can be saved.");
        PlateTemplateImpl plate = (PlateTemplateImpl) plateObj;
        return savePlateImpl(container, user, plate);
    }

    private void populatePlate(PlateTemplateImpl plate)
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

        if (plate instanceof PlateImpl)
            ((PlateImpl) plate).setWells((WellImpl[][]) positionArray);

        // populate well groups: assign all positions to the well group object
        WellGroupTemplateImpl[] wellgroups = getWellGroups(plate);
        List<WellGroupTemplateImpl> sortedGroups = new ArrayList<>();
        for (WellGroupTemplateImpl wellgroup : wellgroups)
        {
            setProperties(plate.getContainer(), wellgroup);
            List<PositionImpl> groupPositions = groupIdToPositions.get(wellgroup.getRowId());

            wellgroup.setPositions(groupPositions != null ? groupPositions : Collections.emptyList());
            sortedGroups.add(wellgroup);
        }

        sortedGroups.sort(new WellGroupTemplateComparator());

        for (WellGroupTemplateImpl group : sortedGroups)
            plate.addWellGroup(group);

    }

    private PositionImpl[] getPositions(PlateTemplateImpl plate)
    {
        SimpleFilter plateFilter = new SimpleFilter(FieldKey.fromParts("PlateId"), plate.getRowId());
        Sort sort = new Sort("Col,Row");
        Class<? extends PositionImpl> clazz = plate.isTemplate() ? PositionImpl.class : WellImpl.class;
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), plateFilter, sort).getArray(clazz);

    }

    private WellGroupTemplateImpl[] getWellGroups(PlateTemplateImpl plate)
    {
        SimpleFilter plateFilter = new SimpleFilter(FieldKey.fromParts("PlateId"), plate.getRowId());
        Class<? extends WellGroupTemplateImpl> clazz = plate.isTemplate() ? WellGroupTemplateImpl.class : WellGroupImpl.class;
        return new TableSelector(AssayDbSchema.getInstance().getTableInfoWellGroup(), plateFilter, null).getArray(clazz);
    }

    private String getLsid(PlateTemplateImpl plate, Class type, boolean instance)
    {
        String nameSpace;
        if (type == Plate.class)
            nameSpace = plate.isTemplate() ? "PlateTemplate" : "PlateInstance";
        else if (type == WellGroup.class)
            nameSpace = plate.isTemplate() ? "WellGroupTemplate" : "WellGroupInstance";
        else if (type == Well.class)
            nameSpace = plate.isTemplate() ? "WellTemplate" : "WellInstance";
        else
            throw new IllegalArgumentException("Unknown type " + type);

        String id;
        if (instance)
            id = GUID.makeGUID();
        else
            id = LSID_CLASS_OBJECT_ID;
        return new Lsid(nameSpace, "Folder-" + plate.getContainer().getRowId(), id).toString();
    }

    private int savePlateImpl(Container container, User user, PlateTemplateImpl plate) throws SQLException
    {
        boolean updateExisting = plate.getRowId() != null;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            PlateTemplateImpl savedPlate;
            String plateInstanceLsid;
            String plateObjectLsid;
            if (updateExisting)
            {
                plateInstanceLsid = plate.getLSID();

                // replace the GUID objectId with the fixed "objectType" value
                Lsid lsid = Lsid.parse(plateInstanceLsid);
                plateObjectLsid = lsid.edit().setObjectId(LSID_CLASS_OBJECT_ID).toString();

                LOG.debug("Updating existing plate. name=" + plate.getName() + ", rowId=" + plate.getRowId() + ", lsid=" + plateInstanceLsid + ", objectLsid=" + plateObjectLsid);
                savedPlate = Table.update(user, AssayDbSchema.getInstance().getTableInfoPlate(), plate, plate.getRowId());
            }
            else
            {
                plateInstanceLsid = getLsid(plate, Plate.class, true);
                plateObjectLsid = getLsid(plate, Plate.class, false);
                plate.setLsid(plateInstanceLsid);
                plate.setContainer(container);

                LOG.debug("Creating new plate. name=" + plate.getName() + ", rowId=" + plate.getRowId() + ", lsid=" + plateInstanceLsid + ", objectLsid=" + plateObjectLsid);
                savedPlate = Table.insert(user, AssayDbSchema.getInstance().getTableInfoPlate(), plate);
            }

            savePropertyBag(container, plateInstanceLsid, plateObjectLsid, savedPlate.getProperties(), updateExisting);

            // delete well groups first
            List<? extends WellGroupTemplateImpl> deletedWellGroups = plate.getDeletedWellGroups();
            for (WellGroupTemplateImpl deletedWellGroup : deletedWellGroups)
            {
                assert deletedWellGroup.getRowId() != null && deletedWellGroup.getRowId() > 0;
                LOG.debug("Deleting well group: name=" + deletedWellGroup.getName() + ", rowId=" + deletedWellGroup.getRowId());
                deleteWellGroup(deletedWellGroup.getRowId());
            }

            for (WellGroupTemplateImpl wellgroup : plate.getWellGroupTemplates(null))
            {
                assert !wellgroup._deleted;
                String wellGroupInstanceLsid;
                String wellGroupObjectLsid;

                if (wellgroup.getRowId() != null && wellgroup.getRowId() > 0)
                {
                    wellGroupInstanceLsid = wellgroup.getLSID();

                    // replace the GUID objectId with the fixed "objectType" value
                    Lsid lsid = Lsid.parse(wellGroupInstanceLsid);
                    wellGroupObjectLsid = lsid.edit().setObjectId(LSID_CLASS_OBJECT_ID).toString();

                    LOG.debug("Updating well group: name=" + wellgroup.getName() + ", rowId=" + wellgroup.getRowId() + ", lsid=" + wellGroupInstanceLsid + ", objectLsid=" + wellGroupObjectLsid);
                    WellGroupTemplateImpl savedWellGroup = Table.update(user, AssayDbSchema.getInstance().getTableInfoWellGroup(), wellgroup, wellgroup.getRowId());

                    savePropertyBag(container, wellGroupInstanceLsid, wellGroupObjectLsid, wellgroup.getProperties(), true);
                }
                else
                {
                    wellGroupInstanceLsid = getLsid(plate, WellGroup.class, true);
                    wellGroupObjectLsid = getLsid(plate, WellGroup.class, false);
                    wellgroup.setLsid(wellGroupInstanceLsid);
                    wellgroup.setPlateId(savedPlate.getRowId());

                    LOG.debug("Creating new well group: name=" + wellgroup.getName() + ", lsid=" + wellGroupInstanceLsid + ", objectLsid=" + wellGroupObjectLsid);
                    assert wellgroup.getRowId() == null;
                    WellGroupTemplateImpl newWellGroup = Table.insert(user, AssayDbSchema.getInstance().getTableInfoWellGroup(), wellgroup);
                    assert newWellGroup.getRowId() != null && newWellGroup.getRowId() > 0;

                    savePropertyBag(container, wellGroupInstanceLsid, wellGroupObjectLsid, wellgroup.getProperties(), false);
                }
            }

            String wellInstanceLsidPrefix = null;

            // Get existing wells for the plate
            Map<Pair<Integer, Integer>, PositionImpl> existingPositionMap = new HashMap<>();
            if (updateExisting)
            {
                for (PositionImpl existingPosition : getPositions(savedPlate))
                {
                    existingPositionMap.put(Pair.of(existingPosition.getRow(), existingPosition.getCol()), existingPosition);
                }
            }
            else
            {
                wellInstanceLsidPrefix = getLsid(plate, Well.class, true);
            }

            List<List<Integer>> wellGroupPositions = new LinkedList<>();
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

                        String wellLsid = wellInstanceLsidPrefix + "-well-" + position.getRow() + "-" + position.getCol();
                        position.setLsid(wellLsid);
                        position.setPlateId(savedPlate.getRowId());
                        PositionImpl newPosition = Table.insert(user, AssayDbSchema.getInstance().getTableInfoWell(), position);
                        assert newPosition.getRowId() != 0;
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

            transaction.commit();
            clearCache();
            return savedPlate.getRowId();
        }
    }

    // return a list of wellId and wellGroupId pairs
    private List<List<Integer>> getWellGroupPositions(PlateTemplateImpl plate, PositionImpl position) throws SQLException
    {
        List<? extends WellGroupTemplateImpl> groups = plate.getWellGroups(position);
        List<List<Integer>> wellGroupPositions = new ArrayList<>(groups.size());

        for (WellGroupTemplateImpl group : groups)
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
    public void deletePlate(Container container, int rowid)
    {
        final AssayDbSchema schema = AssayDbSchema.getInstance();

        SimpleFilter plateFilter = SimpleFilter.createContainerFilter(container);
        plateFilter.addCondition(FieldKey.fromParts("RowId"), rowid);
        PlateTemplateImpl plate = new TableSelector(schema.getTableInfoPlate(),
                plateFilter, null).getObject(PlateTemplateImpl.class);
        WellGroupTemplateImpl[] wellgroups = getWellGroups(plate);
        PositionImpl[] positions = getPositions(plate);

        List<String> lsids = new ArrayList<>();
        lsids.add(plate.getLSID());
        for (WellGroupTemplateImpl wellgroup : wellgroups)
            lsids.add(wellgroup.getLSID());
        for (PositionImpl position : positions)
            lsids.add(position.getLsid());

        SimpleFilter plateIdFilter = SimpleFilter.createContainerFilter(container);
        plateIdFilter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());

        DbScope scope = schema.getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            OntologyManager.deleteOntologyObjects(container, lsids.toArray(new String[lsids.size()]));
            deleteWellGroupPositions(plate);
            Table.delete(schema.getTableInfoWell(), plateIdFilter);
            Table.delete(schema.getTableInfoWellGroup(), plateIdFilter);
            Table.delete(schema.getTableInfoPlate(), plateFilter);
            transaction.commit();
            clearCache();
        }
    }

    private void deleteWellGroup(int wellGroupId)
    {
        final AssayDbSchema schema = AssayDbSchema.getInstance();
        DbScope scope = schema.getSchema().getScope();
        assert scope.isTransactionActive();

        deleteWellGroupPositions(wellGroupId);
        Table.delete(schema.getTableInfoWellGroup(), wellGroupId);
    }

    private void deleteWellGroupPositions(int wellGroupId)
    {
        final AssayDbSchema schema = AssayDbSchema.getInstance();
        DbScope scope = schema.getSchema().getScope();
        assert scope.isTransactionActive();

        new SqlExecutor(scope).execute("" +
                "DELETE FROM " + schema.getTableInfoWellGroupPositions() +
                " WHERE wellGroupId = ?", wellGroupId);
    }

    private void deleteWellGroupPositions(PlateTemplate plate)
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
        clearCache();
    }

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

    private static class PlateLsidHandler implements LsidManager.LsidHandler
    {
        protected PlateImpl getPlate(Lsid lsid)
        {
            return PlateManager.get().getPlate(lsid.toString());
        }

        @Nullable
        public ActionURL getDisplayURL(Lsid lsid)
        {
            PlateImpl plate = getPlate(lsid);
            if (plate == null)
                return null;
            return PlateManager.get().getDetailsURL(plate);
        }

        public ExpObject getObject(Lsid lsid)
        {
            throw new UnsupportedOperationException("Not Yet Implemented.");
        }

        public Container getContainer(Lsid lsid)
        {
            PlateImpl plate = getPlate(lsid);
            if (plate == null)
                return null;
            return plate.getContainer();
        }

        public boolean hasPermission(Lsid lsid, @NotNull User user, @NotNull Class<? extends Permission> perm)
        {
            Container c = getContainer(lsid);
            if (c != null)
                return c.hasPermission(user, perm);
            return false;
        }
    }

    private static class WellGroupLsidHandler implements LsidManager.LsidHandler
    {
        protected WellGroup getWellGroup(Lsid lsid)
        {
            return PlateManager.get().getWellGroup(lsid.toString());
        }

        @Nullable
        public ActionURL getDisplayURL(Lsid lsid)
        {
            if (lsid == null)
                return null;
            WellGroup wellGroup = getWellGroup(lsid);
            if (wellGroup == null)
                return null;
            return PlateManager.get().getDetailsURL(wellGroup.getPlate());
        }

        public ExpObject getObject(Lsid lsid)
        {
            throw new UnsupportedOperationException("Not Yet Implemented.");
        }

        public Container getContainer(Lsid lsid)
        {
            WellGroup wellGroup = getWellGroup(lsid);
            if (wellGroup == null)
                return null;
            return wellGroup.getContainer();
        }

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
    public PlateTemplate copyPlateTemplate(PlateTemplate source, User user, Container destContainer)
            throws SQLException, PlateService.NameConflictException
    {
        PlateTemplate destination = PlateService.get().getPlateTemplate(destContainer, source.getName());
        if (destination != null)
            throw new PlateService.NameConflictException(source.getName());
        destination = PlateService.get().createPlateTemplate(destContainer, source.getType(), source.getRows(), source.getColumns());
        destination.setName(source.getName());
        for (String property : source.getPropertyNames())
            destination.setProperty(property, source.getProperty(property));
        for (WellGroupTemplate originalGroup : source.getWellGroups())
        {
            List<Position> positions = new ArrayList<>();
            for (Position position : originalGroup.getPositions())
                positions.add(destination.getPosition(position.getRow(), position.getColumn()));
            WellGroupTemplate copyGroup = destination.addWellGroup(originalGroup.getName(), originalGroup.getType(), positions);
            for (String property : originalGroup.getPropertyNames())
                copyGroup.setProperty(property, originalGroup.getProperty(property));
        }
        PlateService.get().save(destContainer, user, destination);
        return getPlateTemplate(destContainer, destination.getName());
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

    private String getPlateTemplateCacheKey(Container container, int rowId)
    {
        return PlateTemplateImpl.class.getName() + "/Folder-" + container.getRowId() + "-" + rowId;
    }

    private String getPlateTemplateCacheKey(Container container, String idString)
    {
        return PlateTemplateImpl.class.getName() + "/Folder-" + container.getRowId() + "-" + idString;
    }

    private static final StringKeyCache<PlateTemplateImpl> PLATE_TEMPLATE_CACHE = CacheManager.getSharedCache();

    private void cache(PlateTemplateImpl template)
    {
        if (template.getRowId() == null)
            return;
        PLATE_TEMPLATE_CACHE.put(getPlateTemplateCacheKey(template.getContainer(), template.getRowId().intValue()), template);
        PLATE_TEMPLATE_CACHE.put(getPlateTemplateCacheKey(template.getContainer(), template.getEntityId()), template);
    }

    private void clearCache()
    {
        PLATE_TEMPLATE_CACHE.removeUsingPrefix(PlateTemplateImpl.class.getName());
    }

    private PlateTemplateImpl getCachedPlateTemplate(Container container, int rowId)
    {
        return PLATE_TEMPLATE_CACHE.get(getPlateTemplateCacheKey(container, rowId));
    }

    private PlateTemplateImpl getCachedPlateTemplate(Container container, String idString)
    {
        return PLATE_TEMPLATE_CACHE.get(getPlateTemplateCacheKey(container, idString));
    }

    @Override
    public DilutionCurve getDilutionCurve(List<WellGroup> wellGroups, boolean assumeDecreasing, DilutionCurve.PercentCalculator percentCalculator, StatsService.CurveFitType type) throws FitFailedException
    {
        return CurveFitFactory.getCurveImpl(wellGroups, assumeDecreasing, percentCalculator, type);
    }

    public static final class TestCase
    {
        @Test
        public void createPlateTemplate() throws SQLException
        {
            final Container c = JunitUtil.getTestContainer();

            PlateManager.get().deleteAllPlateData(c);

            //
            // INSERT
            //

            PlateTypeHandler handler = PlateManager.get().getPlateTypeHandler(TsvPlateTypeHandler.TYPE);
            PlateTemplate template = handler.createPlate("UNUSED", c, 8, 12);
            template.setName("bob");
            template.setProperty("friendly", "yes");
            assertNull(template.getRowId());
            assertNull(template.getLSID());

            WellGroupTemplate wg1 = template.addWellGroup("wg1", WellGroup.Type.SAMPLE,
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

            PlateTemplate savedTemplate = PlateService.get().getPlateTemplate(c, "bob");
            assertEquals(plateId, savedTemplate.getRowId().intValue());
            assertEquals("bob", savedTemplate.getName());
            assertEquals("yes", savedTemplate.getProperty("friendly")); assertNotNull(savedTemplate.getLSID());

            List<? extends WellGroupTemplate> wellGroups = savedTemplate.getWellGroups();
            assertEquals(3, wellGroups.size());

            // TsvPlateTypeHandler creates two CONTROL well groups "Positive" and "Negative"
            List<? extends WellGroupTemplate> controlWellGroups = savedTemplate.getWellGroups(WellGroup.Type.CONTROL);
            assertEquals(2, controlWellGroups.size());

            List<? extends WellGroupTemplate> sampleWellGroups = savedTemplate.getWellGroups(WellGroup.Type.SAMPLE);
            assertEquals(1, sampleWellGroups.size());
            WellGroupTemplate savedWg1 = sampleWellGroups.get(0);
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
            WellGroupTemplate wg2 = savedTemplate.addWellGroup("wg2", WellGroup.Type.SAMPLE,
                    PlateService.get().createPosition(c, 1, 0),
                    PlateService.get().createPosition(c, 1, 11));

            // rename existing well group
            ((WellGroupTemplateImpl)savedWg1).setName("wg1_renamed");

            // add positions
            controlWellGroups.get(0).setPositions(List.of(
                    PlateService.get().createPosition(c, 0, 0),
                    PlateService.get().createPosition(c, 0, 1)));

            // delete well group
            ((PlateTemplateImpl)savedTemplate).markWellGroupForDeletion((WellGroupTemplateImpl)controlWellGroups.get(1));

            int newPlateId = PlateService.get().save(c, TestContext.get().getUser(), savedTemplate);
            assertEquals(savedTemplate.getRowId().intValue(), newPlateId);

            //
            // VERIFY UPDATE
            //

            // verify plate
            PlateTemplate updatedTemplate = PlateService.get().getPlateTemplate(c, plateId);
            assertEquals("sally", updatedTemplate.getName());
            assertEquals(savedTemplate.getLSID(), updatedTemplate.getLSID());

            // verify well group rename
            WellGroupTemplate updatedWg1 = updatedTemplate.getWellGroup(savedWg1.getRowId());
            assertNotNull(updatedWg1);
            assertEquals(savedWg1.getLSID(), updatedWg1.getLSID());
            assertEquals("wg1_renamed", updatedWg1.getName());

            // verify added well group
            WellGroupTemplate updatedWg2 = updatedTemplate.getWellGroup(wg2.getRowId());
            assertNotNull(updatedWg2);

            // verify deleted well group
            List<? extends WellGroupTemplate> updatedControlWellGroups = updatedTemplate.getWellGroups(WellGroup.Type.CONTROL);
            assertEquals(1, updatedControlWellGroups.size());

            // veriy added positions
            assertEquals(2, updatedControlWellGroups.get(0).getPositions().size());

            //
            // DELETE
            //

            PlateService.get().deletePlate(c, updatedTemplate.getRowId());

            assertNull(PlateService.get().getPlate(c, updatedTemplate.getRowId()));
            assertEquals(0, PlateManager.get().getPlateTemplates(c).size());
        }

    }
}
