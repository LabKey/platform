package org.labkey.assay.plate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.SimpleAssayDataImportHelper;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.WellCustomField;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveLinkedHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.TSVProtocolSchema;
import org.labkey.assay.plate.model.WellBean;
import org.labkey.assay.query.AssayDbSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.assay.AssayResultDomainKind.WELL_LSID_COLUMN_NAME;

public class AssayPlateMetadataServiceImpl implements AssayPlateMetadataService
{
    private boolean _domainDirty;
    private Map<String, Set<Object>> _propValues = new HashMap<>();

    @Override
    public void addAssayPlateMetadata(
        ExpData resultData,
        Map<String, MetadataLayer> plateMetadata,
        Container container,
        User user,
        ExpRun run,
        AssayProvider provider,
        ExpProtocol protocol,
        List<Map<String, Object>> inserted,
        Map<Integer, String> rowIdToLsidMap
    ) throws ExperimentException
    {
        try
        {
            Domain runDomain = provider.getRunDomain(protocol);
            DomainProperty property = runDomain.getPropertyByName(AssayPlateMetadataService.PLATE_TEMPLATE_COLUMN_NAME);
            Lsid plateLsid = null;

            if (property != null)
                plateLsid = Lsid.parse(String.valueOf(run.getProperty(property)));

            Map<Position, Map<String, Object>> plateData = prepareMergedPlateData(container, user, plateLsid, plateMetadata, protocol, true);
            Domain domain = getPlateDataDomain(protocol);

            Map<String, PropertyDescriptor> descriptorMap = new CaseInsensitiveHashMap<>();
            domain.getProperties().forEach(dp -> descriptorMap.put(dp.getName(), dp.getPropertyDescriptor()));
            List<Map<String, Object>> jsonData = new ArrayList<>();
            Set<PropertyDescriptor> propsToInsert = new HashSet<>();

            // merge the plate data with the uploaded result data
            for (Map<String, Object> row : inserted)
            {
                // ensure the result data includes a wellLocation field with values like : A1, F12, etc
                if (row.containsKey(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME))
                {
                    Object rowId = row.get("RowId");
                    if (rowId != null)
                    {
                        PositionImpl well = new PositionImpl(container, String.valueOf(row.get(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME)));
                        // need to adjust the column value to be 0 based to match the template locations
                        well.setColumn(well.getColumn()-1);

                        if (plateData.containsKey(well))
                        {
                            Map<String, Object> jsonRow = new HashMap<>();
                            plateData.get(well).forEach((k, v) -> {
                                if (descriptorMap.containsKey(k))
                                {
                                    if (v != null)
                                    {
                                        jsonRow.put(descriptorMap.get(k).getURI(), v);
                                        propsToInsert.add(descriptorMap.get(k));
                                    }
                                }
                            });
                            jsonRow.put("Lsid", rowIdToLsidMap.get(rowId));
                            jsonData.add(jsonRow);
                        }
                    }
                }
                else
                    throw new ExperimentException("Imported data must contain a WellLocation column to support plate metadata integration");
            }

            if (!jsonData.isEmpty())
            {
                AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);
                TableInfo tableInfo = schema.createTable(TSVProtocolSchema.PLATE_DATA_TABLE, null);
                if (tableInfo != null)
                {
                    QueryUpdateService qus = tableInfo.getUpdateService();
                    BatchValidationException errors = new BatchValidationException();

                    qus.insertRows(user, container, jsonData, errors, null, null);
                    if (errors.hasErrors())
                    {
                        throw new ExperimentException(errors.getLastRowError());
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new ExperimentException(e);
        }
    }

    /**
     * Helper to merge the plate metadata to the plate wells, producing a map of well location
     * to metadata properties.
     */
    private Map<Position, Map<String, Object>> prepareMergedPlateData(
            Container container,
            User user,
            Lsid plateLsid,
            Map<String, MetadataLayer> plateMetadata,
            ExpProtocol protocol,
            boolean ensurePlateDomain           // true to create the plate domain and properties if they don't exist
    ) throws ExperimentException
    {
        _domainDirty = false;
        _propValues.clear();

        Plate plate = PlateService.get().getPlate(PlateManager.get().getPlateContainerFilter(protocol, container, user), plateLsid);
        if (plate == null)
            throw new ExperimentException("Unable to resolve the plate template for the run");

        if (plateMetadata.isEmpty())
            throw new ExperimentException("No plate information was parsed from the JSON metadata, please check the format of the metadata.");

        Map<Position, Map<String, Object>> plateData = new HashMap<>();
        Set<String> metadataFieldNames = new HashSet<>();

        Domain domain = null;
        if (ensurePlateDomain)
            domain = ensureDomain(protocol);

        // map the metadata to the plate template
        for (int row=0; row < plate.getRows(); row++)
        {
            for (int col=0; col < plate.getColumns(); col++)
            {
                Position pos = plate.getPosition(row, col);
                Map<String, Object> wellProps = new CaseInsensitiveHashMap<>();
                plateData.put(pos, wellProps);

                for (WellGroup group : plate.getWellGroups(pos))
                {
                    MetadataLayer plateLayer = plateMetadata.get(group.getType().name());
                    if (plateLayer != null)
                    {
                        // ensure the column for the plate layer that we will insert the well group name into
                        String layerName = plateLayer.getName() + TSVProtocolSchema.PLATE_DATA_LAYER_SUFFIX;
                        if (ensurePlateDomain)
                            ensureDomainProperty(domain, layerName, group.getName());

                        MetadataWellGroup wellGroup = plateLayer.getWellGroups().get(group.getName());
                        if (wellGroup != null)
                        {
                            // insert the well group name into the layer
                            wellProps.put(layerName, wellGroup.getName());
                            metadataFieldNames.add(layerName);

                            // combine both properties from the metadata and those explicitly set on the plate template
                            Map<String, Object> props = new HashMap<>(wellGroup.getProperties());
                            props.putAll(((WellGroupImpl)group).getProperties());

                            for (Map.Entry<String, Object> entry : props.entrySet())
                            {
                                String propName = entry.getKey();

                                if (ensurePlateDomain)
                                    ensureDomainProperty(domain, propName, entry.getValue());
                                if (!wellProps.containsKey(propName))
                                {
                                    wellProps.put(propName, entry.getValue());
                                    metadataFieldNames.add(propName);
                                }
                                else
                                    throw new ExperimentException("The metadata property name : " + propName + " already exists from a different well group for " +
                                            "the well location : " + pos.getDescription() + ". If well groups overlap from different layers, their metadata property names " +
                                            "need to be unique.");
                            }
                        }
                    }
                }
            }
        }

        if (_domainDirty && ensurePlateDomain)
        {
            createNewDomainProperties(user, domain);
            domain.save(user);
        }

        // ensure each well position has the entire set of metadata field names (to help with merging with result data)
        plateData.values().forEach(wellProp -> {
            metadataFieldNames.forEach(prop -> {
                if (!wellProp.containsKey(prop))
                    wellProp.put(prop, null);
            });
        });

        return plateData;
    }

    @Override
    public List<Map<String, Object>> mergePlateMetadata(
            Container container,
            User user,
            Lsid plateLsid,
            List<Map<String, Object>> rows,
            @Nullable Map<String, MetadataLayer> plateMetadata,
            ExpProtocol protocol) throws ExperimentException
    {
        List<Map<String, Object>> mergedRows = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            Map<String, Object> newRow = new CaseInsensitiveLinkedHashMap<>(row);
            // ensure the result data includes a wellLocation field with values like : A1, F12, etc
            if (!newRow.containsKey(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME))
                throw new ExperimentException("Imported data must contain a WellLocation column to support plate metadata integration");
            mergedRows.add(newRow);
        }

        if (plateMetadata != null)
        {
            Map<Position, Map<String, Object>> plateData = prepareMergedPlateData(container, user, plateLsid, plateMetadata, protocol, false);
            for (Map<String, Object> row : mergedRows)
            {
                PositionImpl well = new PositionImpl(null, String.valueOf(row.get(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME)));
                // need to adjust the column value to be 0 based to match the template locations
                well.setColumn(well.getColumn()-1);

                if (plateData.containsKey(well))
                    row.putAll(plateData.get(well));
            }
        }

        if (AssayPlateMetadataService.isExperimentalAppPlateEnabled())
        {
            // include metadata that may have been applied directly to the plate
            Plate plate = PlateService.get().getPlate(PlateManager.get().getPlateContainerFilter(protocol, container, user), plateLsid);
            if (plate == null)
                throw new ExperimentException("Unable to resolve the plate for the run");

            // if there are metadata fields configured for this plate
            if (!plate.getCustomFields().isEmpty())
            {
                // create the map of well locations to the well
                Map<Position, WellBean> positionToWell = new HashMap<>();

                SimpleFilter filter = SimpleFilter.createContainerFilter(plate.getContainer());
                filter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());
                for (WellBean well : new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, null).getArrayList(WellBean.class))
                    positionToWell.put(new PositionImpl(plate.getContainer(), well.getRow(), well.getCol()), well);

                for (Map<String, Object> row : mergedRows)
                {
                    PositionImpl well = new PositionImpl(null, String.valueOf(row.get(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME)));
                    // need to adjust the column value to be 0 based to match the template locations
                    well.setColumn(well.getColumn()-1);

                    if (positionToWell.containsKey(well))
                    {
                        for (WellCustomField customField : PlateManager.get().getWellCustomFields(user, plate, positionToWell.get(well).getRowId()))
                        {
                            row.put(customField.getName(), customField.getValue());
                        }
                    }
                }
            }
        }
        return mergedRows;
    }

    @Override
    public @Nullable Domain getPlateDataDomain(ExpProtocol protocol)
    {
        String uri = getPlateDataDomainUri(protocol);
        return PropertyService.get().getDomain(protocol.getContainer(), uri);
    }

    private String getPlateDataDomainUri(ExpProtocol protocol)
    {
        DomainKind domainKind = PropertyService.get().getDomainKindByName(AssayPlateDataDomainKind.KIND_NAME);
        return domainKind.generateDomainURI(AssaySchema.NAME, protocol.getName(), protocol.getContainer(), null);
    }

    private Domain ensureDomain(ExpProtocol protocol)
    {
        Domain domain = getPlateDataDomain(protocol);
        if (domain == null)
        {
            _domainDirty = true;
            domain = PropertyService.get().createDomain(protocol.getContainer(), getPlateDataDomainUri(protocol), "PlateDataDomain");
        }
        return domain;
    }

    /**
     * Check if the field name exists in the domain. If not save the value being assigned to the field. We will
     * collect all values, so we can later determine the type of the field to create.
     */
    private void ensureDomainProperty(Domain domain, String name, Object value)
    {
        DomainProperty domainProperty = domain.getPropertyByName(name);
        if (domainProperty == null)
        {
            _domainDirty = true;
            _propValues.computeIfAbsent(name, f -> new HashSet<>()).add(value);
        }
    }

    // classes to test from most to least specific
    private final static List<Class> CONVERT_CLASSES = List.of(Date.class, Integer.class, Double.class, Boolean.class, String.class);

    private void createNewDomainProperties(User user, Domain domain) throws ExperimentException
    {
        for (Map.Entry<String, Set<Object>> entry : _propValues.entrySet())
        {
            // test all values for the domain property finding the most appropriate type for
            // the property
            int classIdx = 0;
            for (Object val : entry.getValue())
            {
                for (int i = classIdx; i < CONVERT_CLASSES.size(); i++)
                {
                    try
                    {
                        Object convertedVal = ConvertUtils.convert(String.valueOf(val), CONVERT_CLASSES.get(i));
                        // found a class that we can convert to
                        if (convertedVal != null && i > classIdx)
                            classIdx = i;
                        break;
                    }
                    catch (Exception e)
                    {
                        // no match, keep going
                    }
                }
            }
            createDomainProperty(user, domain, entry.getKey(), JdbcType.valueOf(CONVERT_CLASSES.get(classIdx)));
        }
    }

    private void createDomainProperty(User user, Domain domain, String name, JdbcType type) throws ExperimentException
    {
        DomainProperty domainProperty = domain.getPropertyByName(name);
        if (domainProperty == null)
        {
            // we are dynamically adding a new property to the plate data domain, ensure the user has at least
            // the DesignAssayPermission
            if (!domain.getContainer().hasPermission(user, DesignAssayPermission.class))
                throw new ExperimentException("This import will create a new plate metadata field : " + name + ". Only users with the AssayDesigner role are allowed to do this.");

            PropertyStorageSpec spec = new PropertyStorageSpec(name, type);
            domain.addProperty(spec);
        }
    }

    @Override
    public Map<String, MetadataLayer> parsePlateMetadata(JSONObject json) throws ExperimentException
    {
        try
        {
            ObjectMapper mapper = new ObjectMapper();
            if (json == null)
                throw new ExperimentException("No plate metadata was uploaded");

            return _parsePlateMetadata(mapper.readTree(json.toString()));
        }
        catch (Exception e)
        {
            throw new ExperimentException(e);
        }
    }

    @Override
    public Map<String, MetadataLayer> parsePlateMetadata(File jsonData) throws ExperimentException
    {
        try
        {
            ObjectMapper mapper = new ObjectMapper();
            if (jsonData == null)
                throw new ExperimentException("No plate metadata was uploaded");

            return _parsePlateMetadata(mapper.readTree(jsonData));
        }
        catch (Exception e)
        {
            throw new ExperimentException(e);
        }
    }

    private Map<String, MetadataLayer> _parsePlateMetadata(JsonNode rootNode) throws ExperimentException
    {
        Map<String, MetadataLayer> layers = new CaseInsensitiveHashMap<>();

        rootNode.fields().forEachRemaining(layerEntry -> {

            String layerName = layerEntry.getKey();
            if (!layers.containsKey(layerName))
                layers.put(layerName, new MetadataLayerImpl(layerName));

            MetadataLayerImpl currentLayer = (MetadataLayerImpl)layers.get(layerName);

            layerEntry.getValue().fields().forEachRemaining(wellEntry -> {

                if (!currentLayer.getWellGroups().containsKey(wellEntry.getKey()))
                    currentLayer.addWellGroup(new MetadataWellGroupImpl(wellEntry.getKey()));

                MetadataWellGroupImpl currentWellGroup = (MetadataWellGroupImpl)currentLayer.getWellGroups().get(wellEntry.getKey());
                wellEntry.getValue().fields().forEachRemaining(propEntry -> {
                    if (propEntry.getValue().isTextual())
                        currentWellGroup.addProperty(propEntry.getKey(), propEntry.getValue().textValue());
                    else if (propEntry.getValue().isNumber())
                        currentWellGroup.addProperty(propEntry.getKey(), propEntry.getValue().numberValue());
                    else if (propEntry.getValue().isBoolean())
                        currentWellGroup.addProperty(propEntry.getKey(), propEntry.getValue().booleanValue());
                    else
                    {
                        throw new RuntimeException("Only string, numeric and boolean properties can be added through plate metadata");
                    }
                });
            });
        });
        return layers;
    }

    @Override
    @NotNull
    public OntologyManager.UpdateableTableImportHelper getImportHelper(
            Container container,
            User user,
            ExpRun run,
            ExpData data,
            ExpProtocol protocol,
            AssayProvider provider) throws ExperimentException
    {
        // get the plate associated with this run
        Domain runDomain = provider.getRunDomain(protocol);
        DomainProperty property = runDomain.getPropertyByName(AssayPlateMetadataService.PLATE_TEMPLATE_COLUMN_NAME);
        Lsid plateLsid = null;

        if (property != null)
            plateLsid = Lsid.parse(String.valueOf(run.getProperty(property)));

        Plate plate = PlateService.get().getPlate(PlateManager.get().getPlateContainerFilter(protocol, container, user), plateLsid);
        if (plate == null)
            throw new ExperimentException(String.format("Unable to resolve the plate : %s for the run", plateLsid));

        // create the map of well locations to the well table lsid
        Map<Position, Lsid> positionToWellLsid = new HashMap<>();

        SimpleFilter filter = SimpleFilter.createContainerFilter(plate.getContainer());
        filter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());
        for (WellBean well : new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, null).getArrayList(WellBean.class))
            positionToWellLsid.put(new PositionImpl(plate.getContainer(), well.getRow(), well.getCol()), Lsid.parse(well.getLsid()));

        return new PlateMetadataImportHelper(plate.getContainer(), data, Collections.unmodifiableMap(positionToWellLsid));
    }

    private static class PlateMetadataImportHelper extends SimpleAssayDataImportHelper
    {
        private Map<Position, Lsid> _wellPositionMap;
        private Container _container;

        public PlateMetadataImportHelper(Container container, ExpData data, Map<Position, Lsid> wellPositionMap)
        {
            super(data);
            _wellPositionMap = wellPositionMap;
            _container = container;
        }

        @Override
        public void bindAdditionalParameters(Map<String, Object> map, ParameterMapStatement target) throws ValidationException
        {
            super.bindAdditionalParameters(map, target);

            // to join plate based metadata to assay results we need to line up the incoming assay results with the
            // corresponding well on the plate used in the import
            if (map.containsKey(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME))
            {
                PositionImpl pos = new PositionImpl(_container, String.valueOf(map.get(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME)));
                // need to adjust the column value to be 0 based to match the template locations
                pos.setCol(pos.getColumn() - 1);
                if (_wellPositionMap.containsKey(pos))
                    target.put(WELL_LSID_COLUMN_NAME, _wellPositionMap.get(pos));
            }
        }
    }

    private static class MetadataLayerImpl implements MetadataLayer
    {
        private String _name;
        private Map<String, MetadataWellGroup> _wellGroupMap = new CaseInsensitiveHashMap<>();

        public MetadataLayerImpl(String name)
        {
            _name = name;
        }

        @Override
        public String getName()
        {
            return _name;
        }

        @Override
        public Map<String, MetadataWellGroup> getWellGroups()
        {
            return _wellGroupMap;
        }

        public void addWellGroup(MetadataWellGroup wellGroup)
        {
            _wellGroupMap.put(wellGroup.getName(), wellGroup);
        }
    }

    private static class MetadataWellGroupImpl implements MetadataWellGroup
    {
        private String _name;
        private Map<String, Object> _properties = new CaseInsensitiveHashMap<>();

        public MetadataWellGroupImpl(String name)
        {
            _name = name;
        }

        @Override
        public String getName()
        {
            return _name;
        }

        @Override
        public Map<String, Object> getProperties()
        {
            return _properties;
        }

        public void addProperty(String name, Object value)
        {
            _properties.put(name, value);
        }
    }
}
