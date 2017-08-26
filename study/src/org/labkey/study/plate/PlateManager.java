/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.study.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.dilution.DilutionCurve;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.statistics.FitFailedException;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.AbstractPlateTypeHandler;
import org.labkey.api.study.Plate;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.PlateTypeHandler;
import org.labkey.api.study.Position;
import org.labkey.api.study.PositionImpl;
import org.labkey.api.study.Well;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.WellGroupTemplate;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:13:08 AM
 */
public class PlateManager implements PlateService
{
    private List<PlateService.PlateDetailsResolver> _detailsLinkResolvers = new ArrayList<>();
    private boolean _lsidHandlersRegistered = false;
    private Map<String, PlateTypeHandler> _plateTypeHandlers = new HashMap<>();

    public PlateManager()
    {
        registerPlateTypeHandler(new AbstractPlateTypeHandler()
        {
            public PlateTemplate createPlate(String templateTypeName, Container container, int rowCount, int colCount) throws SQLException
            {
                return PlateService.get().createPlateTemplate(container, getAssayType(), rowCount, colCount);
            }

            public String getAssayType()
            {
                return "blank";
            }

            public List<String> getTemplateTypes(Pair<Integer, Integer> size)
            {
                return new ArrayList<>();
            }

            @Override
            public List<Pair<Integer, Integer>> getSupportedPlateSizes()
            {
                return Collections.singletonList(new Pair<>(8, 12));
            }

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

    public Position createPosition(Container container, int row, int column)
    {
        return new PositionImpl(container, row, column);
    }

    public PlateTemplateImpl getPlateTemplate(Container container, String name)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Template"), Boolean.TRUE);
        filter.addCondition(FieldKey.fromParts("Name"), name);
        filter.addCondition(FieldKey.fromParts("Container"), container);
        PlateTemplateImpl template = new TableSelector(StudySchema.getInstance().getTableInfoPlate(), filter, null).getObject(PlateTemplateImpl.class);
        if (template != null)
        {
            populatePlate(template);
            cache(template);
        }
        return template;
    }

    public PlateTemplateImpl getPlateTemplate(Container container, int plateId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Template"), Boolean.TRUE);
        filter.addCondition(FieldKey.fromParts("RowId"), plateId);
        filter.addCondition(FieldKey.fromParts("Container"), container);
        PlateTemplateImpl template = new TableSelector(StudySchema.getInstance().getTableInfoPlate(), filter, null).getObject(PlateTemplateImpl.class);
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
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Template"), Boolean.TRUE);
        filter.addCondition(FieldKey.fromParts("Container"), container);
        List<PlateTemplateImpl> templates = new TableSelector(StudySchema.getInstance().getTableInfoPlate(),
                filter, new Sort("Name")).getArrayList(PlateTemplateImpl.class);
        for (int i = 0; i < templates.size(); i++)
        {
            PlateTemplateImpl template = templates.get(i);
            PlateTemplateImpl cached = getCachedPlateTemplate(container, template.getRowId().intValue());
            if (cached != null)
                templates.set(i, cached);
            else
                populatePlate(template);
        }
        return templates;
    }

    public PlateTemplate createPlateTemplate(Container container, String templateType, int rowCount, int colCount)
    {
        return new PlateTemplateImpl(container, null, templateType, rowCount, colCount);
    }

    public PlateImpl getPlate(Container container, int rowid)
    {
        PlateImpl plate = (PlateImpl) getCachedPlateTemplate(container, rowid);
        if (plate != null)
            return plate;
        plate = new TableSelector(StudySchema.getInstance().getTableInfoPlate()).getObject(rowid, PlateImpl.class);
        if (plate == null)
            return null;
        populatePlate(plate);
        cache(plate);
        return plate;
    }

    public PlateImpl getPlate(Container container, String entityId)
    {
        PlateImpl plate = (PlateImpl) getCachedPlateTemplate(container, entityId);
        if (plate != null)
            return plate;
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Container"), container).addCondition(FieldKey.fromParts("DataFileId"), entityId);
        plate = new TableSelector(StudySchema.getInstance().getTableInfoPlate(), filter, null).getObject(PlateImpl.class);
        if (plate == null)
            return null;
        populatePlate(plate);
        cache(plate);
        return plate;
    }

    public PlateImpl getPlate(String lsid)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("lsid"), lsid);
        PlateImpl plate = new TableSelector(StudySchema.getInstance().getTableInfoPlate(), filter, null).getObject(PlateImpl.class);
        if (plate == null)
            return null;
        populatePlate(plate);
        return plate;
    }

    public WellGroup getWellGroup(Container container, int rowid)
    {
        WellGroupImpl unboundWellgroup = new TableSelector(StudySchema.getInstance().getTableInfoWellGroup()).getObject(rowid, WellGroupImpl.class);
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
        WellGroupImpl unboundWellgroup = new TableSelector(StudySchema.getInstance().getTableInfoWellGroup(), filter, null).getObject(WellGroupImpl.class);
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

    public int save(Container container, User user, PlateTemplate plateObj) throws SQLException
    {
        if (!(plateObj instanceof PlateTemplateImpl))
            throw new IllegalArgumentException("Only plate instances created by the plate service can be saved.");
        PlateTemplateImpl plate = (PlateTemplateImpl) plateObj;
        return savePlateImpl(container, user, plate);
    }

    public static class WellGroupTemplateComparator implements Comparator<WellGroupTemplateImpl>
    {
        public int compare(WellGroupTemplateImpl first, WellGroupTemplateImpl second)
        {
            Position firstPos = first.getTopLeft();
            Position secondPos = second.getTopLeft();
            if (firstPos == null && secondPos == null)
                return 0;
            if (firstPos == null)
                return -1;
            if (secondPos == null)
                return 1;
            int comp = firstPos.getColumn() - secondPos.getColumn();
            if (comp == 0)
                comp = firstPos.getRow() - secondPos.getRow();
            return comp;
        }
    }

    private void populatePlate(PlateTemplateImpl plate)
    {
        // set plate properties:
        setProperties(plate.getContainer(), plate);

        // populate wells:
        Map<String, List<PositionImpl>> groupLsidToPositions = new HashMap<>();
        Position[][] positionArray;
        if (plate.isTemplate())
            positionArray = new Position[plate.getRows()][plate.getColumns()];
        else
            positionArray = new WellImpl[plate.getRows()][plate.getColumns()];
        PositionImpl[] positions = getPositions(plate);
        for (PositionImpl position : positions)
        {
            positionArray[position.getRow()][position.getColumn()] = position;
            Map<String, Object> props = OntologyManager.getProperties(plate.getContainer(), position.getLsid());
            // this is a bit counter-intuitive: the groups to which a position belongs are indicated by the values of the properties
            // associated with the position:
            for (Map.Entry<String, Object> entry : props.entrySet())
            {
                String wellgroupLsid = (String) entry.getValue();
                List<PositionImpl> groupPositions = groupLsidToPositions.computeIfAbsent(wellgroupLsid, k -> new ArrayList<>());
                groupPositions.add(position);
            }
        }

        if (plate instanceof PlateImpl)
            ((PlateImpl) plate).setWells((WellImpl[][]) positionArray);

        // populate well groups:
        WellGroupTemplateImpl[] wellgroups = getWellGroups(plate);
        List<WellGroupTemplateImpl> sortedGroups = new ArrayList<>();
        for (WellGroupTemplateImpl wellgroup : wellgroups)
        {
            setProperties(plate.getContainer(), wellgroup);
            List<PositionImpl> groupPositions = groupLsidToPositions.get(wellgroup.getLSID());
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
        return new TableSelector(StudySchema.getInstance().getTableInfoWell(), plateFilter, sort).getArray(clazz);

    }

    private WellGroupTemplateImpl[] getWellGroups(PlateTemplateImpl plate)
    {
        SimpleFilter plateFilter = new SimpleFilter(FieldKey.fromParts("PlateId"), plate.getRowId());
        Class<? extends WellGroupTemplateImpl> clazz = plate.isTemplate() ? WellGroupTemplateImpl.class : WellGroupImpl.class;
        return new TableSelector(StudySchema.getInstance().getTableInfoWellGroup(), plateFilter, null).getArray(clazz);
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
            id = "objectType";
        return new Lsid(nameSpace, "Folder-" + plate.getContainer().getRowId(), id).toString();
    }

    private int savePlateImpl(Container container, User user, PlateTemplateImpl plate) throws SQLException
    {
        if (plate.getRowId() != null)
            throw new UnsupportedOperationException("Resaving of plate templates is not supported.");

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            String plateInstanceLsid = getLsid(plate, Plate.class, true);
            String plateObjectLsid = getLsid(plate, Plate.class, false);
            plate.setLsid(plateInstanceLsid);
            plate.setContainer(container);
            plate.setCreatedBy(user.getUserId());
            plate.setCreated(new Date());
            PlateTemplateImpl newPlate = Table.insert(user, StudySchema.getInstance().getTableInfoPlate(), plate);
            savePropertyBag(container, plateInstanceLsid, plateObjectLsid, newPlate.getProperties());

            for (WellGroupTemplateImpl wellgroup : plate.getWellGroupTemplates())
            {
                String wellGroupInstanceLsid = getLsid(plate, WellGroup.class, true);
                String wellGroupObjectLsid = getLsid(plate, WellGroup.class, false);
                wellgroup.setLsid(wellGroupInstanceLsid);
                wellgroup.setPlateId(newPlate.getRowId());
                Table.insert(user, StudySchema.getInstance().getTableInfoWellGroup(), wellgroup);
                savePropertyBag(container, wellGroupInstanceLsid, wellGroupObjectLsid, wellgroup.getProperties());
            }

            String wellInstanceLsidPrefix = getLsid(plate, Well.class, true);
            String wellObjectLsid = getLsid(plate, Well.class, false);
            for (int row = 0; row < plate.getRows(); row++)
            {
                for (int col = 0; col < plate.getColumns(); col++)
                {
                    PositionImpl position = plate.getPosition(row, col);
                    String wellLsid = wellInstanceLsidPrefix + "-well-" + position.getRow() + "-" + position.getCol();
                    position.setLsid(wellLsid);
                    position.setPlateId(newPlate.getRowId());
                    Table.insert(user, StudySchema.getInstance().getTableInfoWell(), position);
                    savePropertyBag(container, wellLsid, wellObjectLsid, getPositionProperties(plate, position));
                }
            }
            transaction.commit();
            clearCache();
            return newPlate.getRowId();
        }
    }

    private Map<String, Object> getPositionProperties(PlateTemplateImpl plate, PositionImpl position)
    {
        List<? extends WellGroupTemplateImpl> groups = plate.getWellGroups(position);
        Map<String, Object> properties = new HashMap<>();
        int index = 0;
        for (WellGroupTemplateImpl group : groups)
        {
            if (group.contains(position))
                properties.put("Group " + index++, group.getLSID());
        }
        return properties;
    }

    private void savePropertyBag(Container container, String ownerLsid,
                                 String classLsid, Map<String, Object> props) throws SQLException
    {
        Map<String, ObjectProperty> resourceProperties = OntologyManager.getPropertyObjects(container, ownerLsid);
        if (resourceProperties != null && !resourceProperties.isEmpty())
            throw new IllegalStateException("Did not expect to find property set for new plate.");
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
        try {
            if (objectProperties.length > 0)
                OntologyManager.insertProperties(container, ownerLsid, objectProperties);
        }
        catch (ValidationException ve)
        {
            throw new SQLException(ve.getMessage());
        }
    }

    public void deletePlate(Container container, int rowid)
    {
        SimpleFilter plateFilter = SimpleFilter.createContainerFilter(container);
        plateFilter.addCondition(FieldKey.fromParts("RowId"), rowid);
        PlateTemplateImpl plate = new TableSelector(StudySchema.getInstance().getTableInfoPlate(),
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

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            OntologyManager.deleteOntologyObjects(container, lsids.toArray(new String[lsids.size()]));
            Table.delete(StudySchema.getInstance().getTableInfoWell(), plateIdFilter);
            Table.delete(StudySchema.getInstance().getTableInfoWellGroup(), plateIdFilter);
            Table.delete(StudySchema.getInstance().getTableInfoPlate(), plateFilter);
            transaction.commit();
            clearCache();
        }
    }

    public void deleteAllPlateData(Container container)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        Table.delete(StudySchema.getInstance().getTableInfoWell(), filter);
        Table.delete(StudySchema.getInstance().getTableInfoWellGroup(), filter);
        Table.delete(StudySchema.getInstance().getTableInfoPlate(), filter);
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

    public static PlateManager get()
    {
        return (PlateManager) PlateService.get();
    }

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
}
