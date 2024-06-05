package org.labkey.assay.plate;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.SimpleAssayDataImportHelper;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.ExcelPlateReader;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.PlateUtils;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.assay.plate.WellCustomField;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveLinkedHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
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
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.assay.TSVProtocolSchema;
import org.labkey.assay.plate.model.WellBean;
import org.labkey.assay.query.AssayDbSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.labkey.api.assay.AssayResultDomainKind.WELL_LSID_COLUMN_NAME;

public class AssayPlateMetadataServiceImpl implements AssayPlateMetadataService
{
    private boolean _domainDirty;
    private final Map<String, Set<Object>> _propValues = new HashMap<>();

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
            Domain resultDomain = provider.getResultsDomain(protocol);
            // The run level plate template value is to support the legacy JSON plate metadata feature
            DomainProperty templateProperty = runDomain.getPropertyByName(AssayPlateMetadataService.PLATE_TEMPLATE_COLUMN_NAME);
            DomainProperty wellLocationProperty = resultDomain.getPropertyByName(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME);
            Lsid plateLsid = null;

            if (templateProperty != null)
                plateLsid = Lsid.parse(String.valueOf(run.getProperty(templateProperty)));

            Map<Position, Map<String, Object>> plateData = prepareMergedPlateData(container, user, plateLsid, plateMetadata, protocol, true);
            Domain domain = getPlateDataDomain(protocol);

            Map<String, PropertyDescriptor> descriptorMap = new CaseInsensitiveHashMap<>();
            domain.getProperties().forEach(dp -> descriptorMap.put(dp.getName(), dp.getPropertyDescriptor()));
            List<Map<String, Object>> jsonData = new ArrayList<>();

            // merge the plate data with the uploaded result data
            for (Map<String, Object> row : inserted)
            {
                // ensure the result data includes a wellLocation field with values like : A1, F12, etc
                Object wellLocation = PropertyService.get().getDomainPropertyValueFromRow(wellLocationProperty, row);
                if (wellLocation != null)
                {
                    Object rowId = row.get("RowId");
                    if (rowId != null)
                    {
                        PositionImpl well = new PositionImpl(container, String.valueOf(wellLocation));
                        // need to adjust the column value to be 0 based to match the template locations
                        well.setColumn(well.getColumn()-1);

                        if (plateData.containsKey(well))
                        {
                            Map<String, Object> jsonRow = new HashMap<>();
                            plateData.get(well).forEach((k, v) -> {
                                if (descriptorMap.containsKey(k))
                                {
                                    if (v != null)
                                        jsonRow.put(descriptorMap.get(k).getURI(), v);
                                }
                            });
                            jsonRow.put("Lsid", rowIdToLsidMap.get(rowId));
                            jsonData.add(jsonRow);
                        }
                    }
                }
                else
                    throw new ExperimentException("Imported data must contain a WellLocation column to support plate metadata integration.");
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
        Integer plateSetId,
        List<Map<String, Object>> rows,
        @Nullable Map<String, MetadataLayer> plateMetadata,
        AssayProvider provider,
        ExpProtocol protocol
    ) throws ExperimentException
    {
        Domain resultDomain = provider.getResultsDomain(protocol);
        DomainProperty plateProperty = resultDomain.getPropertyByName(AssayResultDomainKind.PLATE_COLUMN_NAME);
        DomainProperty wellLocationProperty = resultDomain.getPropertyByName(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME);

        List<Map<String, Object>> mergedRows = new ArrayList<>();
        for (Map<String, Object> row : rows)
        {
            Map<String, Object> newRow = new CaseInsensitiveLinkedHashMap<>(row);

            // ensure the result data includes a wellLocation field with values like : A1, F12, etc
            Object wellLocation = PropertyService.get().getDomainPropertyValueFromRow(wellLocationProperty, newRow);
            if (wellLocation == null)
                throw new ExperimentException("Imported data must contain a WellLocation column to support plate metadata integration.");

            mergedRows.add(newRow);
        }

        if (plateMetadata != null)
        {
            Map<Position, Map<String, Object>> plateData = prepareMergedPlateData(container, user, plateLsid, plateMetadata, protocol, false);
            for (Map<String, Object> row : mergedRows)
            {
                Object wellLocation = PropertyService.get().getDomainPropertyValueFromRow(wellLocationProperty, row);
                PositionImpl well = new PositionImpl(null, String.valueOf(wellLocation));
                // need to adjust the column value to be 0 based to match the template locations
                well.setColumn(well.getColumn()-1);

                if (plateData.containsKey(well))
                    row.putAll(plateData.get(well));
            }
        }

        if (AssayPlateMetadataService.isExperimentalAppPlateEnabled())
        {
            Map<Object, Pair<Plate, Map<Position, WellBean>>> plateIdentifierMap = new HashMap<>();
            ContainerFilter cf = PlateManager.get().getPlateContainerFilter(protocol, container, user);
            int rowCounter = 0;
            Map<Integer, ExpMaterial> sampleMap = new HashMap<>();

            // include metadata that may have been applied directly to the plate
            for (Map<String, Object> row : mergedRows)
            {
                rowCounter++;

                Object plateIdentifier = PropertyService.get().getDomainPropertyValueFromRow(plateProperty, row);
                if (plateIdentifier == null)
                    throw new ExperimentException("Unable to resolve plate identifier for results row (" + rowCounter + ").");

                Plate plate = PlateService.get().getPlate(cf, plateSetId, plateIdentifier);
                if (plate == null)
                    throw new ExperimentException("Unable to resolve the plate \"" + plateIdentifier + "\" for the results row (" + rowCounter + ").");

                plateIdentifierMap.putIfAbsent(plateIdentifier, new Pair<>(plate, new HashMap<>()));

                // if the plate identifier is the plate name, we need to make sure it resolves during importRows
                // so replace it with the plateId (which will be unique)
                if (!StringUtils.isNumeric(plateIdentifier.toString()))
                    PropertyService.get().replaceDomainPropertyValue(plateProperty, row, plate.getPlateId());

                // create the map of well locations to the well for the given plate
                Map<Position, WellBean> positionToWell = plateIdentifierMap.get(plateIdentifier).second;
                if (positionToWell.isEmpty())
                {
                    SimpleFilter filter = SimpleFilter.createContainerFilter(plate.getContainer());
                    filter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());
                    Set<Integer> wellSamples = new HashSet<>();
                    for (WellBean well : new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, null).getArrayList(WellBean.class))
                    {
                        positionToWell.put(new PositionImpl(plate.getContainer(), well.getRow(), well.getCol()), well);
                        if (well.getSampleId() != null && !sampleMap.containsKey(well.getSampleId()))
                            wellSamples.add(well.getSampleId());
                    }

                    if (!wellSamples.isEmpty())
                        // stash away any samples associated with the plate
                        ExperimentService.get().getExpMaterials(wellSamples).forEach(s -> sampleMap.put(s.getRowId(), s));
                }

                Object wellLocation = PropertyService.get().getDomainPropertyValueFromRow(wellLocationProperty, row);
                if (wellLocation != null)
                {
                    PositionImpl well = new PositionImpl(null, String.valueOf(wellLocation));
                    // need to adjust the column value to be 0 based to match the template locations
                    well.setColumn(well.getColumn() - 1);

                    if (positionToWell.containsKey(well))
                    {
                        WellBean wellBean = positionToWell.get(well);
                        for (WellCustomField customField : PlateManager.get().getWellCustomFields(user, plate, wellBean.getRowId()))
                            row.put(customField.getName(), customField.getValue());

                        // include the sample information from the well (Issue 50276)
                        if (!sampleMap.isEmpty())
                        {
                            ExpMaterial sample = sampleMap.get(wellBean.getSampleId());
                            row.put("SampleID", sample != null ? sample.getRowId() : null);
                            row.put("SampleName", sample != null ? sample.getName() : null);
                        }
                    }
                    else
                        throw new ExperimentException("Unable to resolve well \"" + wellLocation + "\" for plate \"" + plate.getName() + "\".");
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
            if (json == null)
                throw new ExperimentException("No plate metadata was uploaded");

            return _parsePlateMetadata(JsonUtil.DEFAULT_MAPPER.readTree(json.toString()));
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
            if (jsonData == null)
                throw new ExperimentException("No plate metadata was uploaded");

            return _parsePlateMetadata(JsonUtil.DEFAULT_MAPPER.readTree(jsonData));
        }
        catch (Exception e)
        {
            throw new ExperimentException(e);
        }
    }

    private Map<String, MetadataLayer> _parsePlateMetadata(JsonNode rootNode)
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
    public List<Map<String, Object>> parsePlateData(
        Container container,
        User user,
        AssayProvider provider,
        ExpProtocol protocol,
        Integer plateSetId,
        File dataFile,
        List<Map<String, Object>> data,
        Pair<String, Boolean> plateIdAdded
    ) throws ExperimentException
    {
        // get the ordered list of plates for the plate set
        ContainerFilter cf = PlateManager.get().getPlateContainerFilter(protocol, container, user);
        PlateSet plateSet = PlateManager.get().getPlateSet(cf, plateSetId);
        if (plateSet == null)
            throw new ExperimentException("Plate set " + plateSetId + " not found.");
        if (plateSet.isTemplate())
            throw new ExperimentException(String.format("Plate set \"%s\" is a template plate set. Template plate sets do not support associating assay data.", plateSet.getName()));

        List<Plate> plates = PlateManager.get().getPlatesForPlateSet(plateSet);
        if (plates.isEmpty())
            throw new ExperimentException("No plates were found for the plate set (" + plateSetId + ").");

        if (isGridFormat(data))
        {
            plateIdAdded.setValue(true);
            List<Map<String, Object>> gridRows = parsePlateGrids(container, user, provider, protocol, plateSet, plates, dataFile);

            // best attempt at returning something we can import
            return (gridRows.isEmpty() && !data.isEmpty()) ? data : gridRows;
        }

        return parsePlateRows(provider, protocol, plates, data, plateIdAdded);
    }

    private boolean isGridFormat(List<Map<String, Object>> data)
    {
        // best guess whether the incoming data is in a graphical grid format
        if (data.isEmpty())
            return true;

        // only the tabular formats will have the well location field
        return !data.get(0).containsKey(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME);
    }

    private List<Map<String, Object>> parsePlateRows(
        AssayProvider provider,
        ExpProtocol protocol,
        List<Plate> plates,
        List<Map<String, Object>> data,
        Pair<String, Boolean> plateIdAdded
    ) throws ExperimentException
    {
        DomainProperty plateProp = provider.getResultsDomain(protocol).getPropertyByName(AssayResultDomainKind.PLATE_COLUMN_NAME);
        Set<String> importAliases = new CaseInsensitiveHashSet(plateProp.getImportAliasSet());
        importAliases.add(AssayResultDomainKind.PLATE_COLUMN_NAME);

        // check whether the data rows have plate identifiers
        String plateIdField = data.get(0).keySet().stream().filter(importAliases::contains).findFirst().orElse(null);
        boolean hasPlateIdentifiers = plateIdField != null && (data.stream().filter(row -> row.get(plateIdField) != null).findFirst().orElse(null) != null);

        if (hasPlateIdentifiers)
            return data;

        final String ERROR_MESSAGE = "Unable to automatically assign plate identifiers to the data rows because %s. Please include plate identifiers for the data rows.";
        plateIdAdded.second = true;

        // verify all plates in the set have the same shape
        Set<PlateType> types = plates.stream().map(Plate::getPlateType).collect(Collectors.toSet());
        if (types.size() > 1)
            throw new ExperimentException(String.format(ERROR_MESSAGE, "the plate set contains different plate types"));

        PlateType type = types.stream().toList().get(0);
        int plateSize = type.getRows() * type.getColumns();
        if ((data.size() % plateSize) != 0)
            throw new ExperimentException(String.format(ERROR_MESSAGE, "the number of rows in the data (" + data.size() + ") does not fit evenly and would result in a plate with partial wells filled"));

        if (data.size() > (plates.size() * plateSize))
            throw new ExperimentException(String.format(ERROR_MESSAGE, "the number of rows in the data (" + data.size() + ") exceeds the total number of wells available in the plate set (" + (plates.size() * plateSize) + ")"));

        // attempt to add the plate identifier into the data rows in the order that they appear in the plate set
        List<Map<String, Object>> newData = new ArrayList<>();
        int rowCount = 0;
        int curPlate = 0;
        Set<Position> positions = new HashSet<>();
        String plateFieldName = plateIdField != null ? plateIdField : AssayResultDomainKind.PLATE_COLUMN_NAME;
        for (Map<String, Object> row : data)
        {
            // well location field is required, return if not provided or it will fail downstream
            String well = String.valueOf(row.get(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME));
            if (well == null)
                return data;

            Position position = new PositionImpl(null, well);
            if (positions.contains(position))
                throw new ExperimentException(String.format(ERROR_MESSAGE, "there is more than one well referencing the same position in the plate " + position));

            positions.add(position);
            Map<String, Object> newRow = new HashMap<>(row);
            newRow.put(plateFieldName, plates.get(curPlate).getPlateId());
            newData.add(newRow);

            if (++rowCount >= plateSize)
            {
                // move to the next plate in the set
                rowCount = 0;
                curPlate++;
                positions.clear();
            }
        }

        return newData;
    }

    /**
     * Helper class to organize plate grid info and annotations
     */
    private static class PlateGridInfo extends PlateUtils.GridInfo
    {
        public static final String PLATE_PREFIX = "plate";
        public static final String MEASURE_PREFIX = "measure";
        private Plate _plate;
        private String _measureName;

        public PlateGridInfo(PlateUtils.GridInfo info, PlateSet plateSet) throws ExperimentException
        {
            super(info.getData(), info.getAnnotations());

            // locate the plate in the plate set this grid is associated with plus an optional
            // measure name
            List<Plate> plates = PlateManager.get().getPlatesForPlateSet(plateSet);
            List<String> annotations = getAnnotations();

            // single annotation can only be a plate identifier
            if (annotations.size() == 1)
                _plate = getPlateForId(annotations.get(0), plates);
            else
            {
                // multiple annotation must have an annotation prefix
                for (String annotation : annotations)
                {
                    String plateID = getPrefixedValue(annotation, PLATE_PREFIX);
                    if (plateID != null)
                        _plate = getPlateForId(plateID, plates);
                    else
                        _measureName = getPrefixedValue(annotation, MEASURE_PREFIX);
                }
            }
        }

        private @NotNull Plate getPlateForId(String annotation, List<Plate> platesetPlates) throws ExperimentException
        {
            Plate plate = platesetPlates.stream().filter(p -> p.isIdentifierMatch(annotation)).findFirst().orElse(null);
            if (plate == null)
                throw new ExperimentException("The plate identifier (" + annotation + ") is not valid for the configured plate set.");

            return plate;
        }

        private @Nullable String getPrefixedValue(String annotation, String prefix)
        {
            if (annotation != null && annotation.trim().toLowerCase().startsWith(prefix))
            {
                String[] parts = annotation.split(":");
                if (parts.length == 2)
                {
                    return parts[1].trim();
                }
            }
            return null;
        }

        public @Nullable Plate getPlate()
        {
            return _plate;
        }

        public @Nullable String getMeasureName()
        {
            return _measureName;
        }
    }

    private List<Map<String, Object>> parsePlateGrids(
        Container container,
        User user,
        AssayProvider provider,
        ExpProtocol protocol,
        PlateSet plateSet,
        List<Plate> plates,
        File dataFile
    ) throws ExperimentException
    {
        // parse the data file for each distinct plate type found in the set of plates for the plateSetId
        ExcelPlateReader plateReader = new ExcelPlateReader();
        MultiValuedMap<PlateType, PlateGridInfo> plateTypeGrids = new HashSetValuedHashMap<>();

        boolean hasPlateIdentifiers = false;
        boolean missingPlateIdentifiers = false;
        boolean multipleMeasures = false;

        for (Plate plate : plates)
        {
            if (!plateTypeGrids.containsKey(plate.getPlateType()))
            {
                Plate p = PlateService.get().createPlate(container, TsvPlateLayoutHandler.TYPE, plate.getPlateType());
                for (PlateUtils.GridInfo gridInfo : plateReader.loadMultiGridFile(p, dataFile))
                {
                    PlateGridInfo plateInfo = new PlateGridInfo(gridInfo, plateSet);
                    plateTypeGrids.put(plate.getPlateType(), plateInfo);

                    if (plateInfo.getPlate() != null && !hasPlateIdentifiers)
                        hasPlateIdentifiers = true;
                    if (plateInfo.getPlate() == null && !missingPlateIdentifiers)
                        missingPlateIdentifiers = true;
                    if (plateInfo.getMeasureName() != null && !multipleMeasures)
                        multipleMeasures = true;
                }
            }
        }

        List<DomainProperty> measureProperties = provider.getResultsDomain(protocol).getProperties().stream().filter(DomainProperty::isMeasure).collect(Collectors.toList());
        if (!multipleMeasures && measureProperties.size() != 1)
            throw new ExperimentException("The assay protocol must have exactly one measure property to support graphical plate layout file parsing.");
        String defaultMeasureName = measureProperties.get(0).getName();

        // if any of the plateGrids keys have plate identifiers, import using those identifiers
        List<Map<String, Object>> dataRows = new ArrayList<>();
        if (hasPlateIdentifiers)
        {
            if (missingPlateIdentifiers)
                throw new ExperimentException("Some plate grids parsed from the file are missing plate identifiers.");

            for (Map.Entry<PlateType, Collection<PlateGridInfo>> plateTypeMapEntry : plateTypeGrids.asMap().entrySet())
            {
                if (multipleMeasures)
                {
                    // group by plate within the plate type
                    MultiValuedMap<Plate, PlateGridInfo> plateMaps = new HashSetValuedHashMap<>();
                    plateTypeMapEntry.getValue().forEach(gi -> {plateMaps.put(gi.getPlate(), gi);});

                    for (Map.Entry<Plate, Collection<PlateGridInfo>> entry : plateMaps.asMap().entrySet())
                    {
                        Set<String> measures = new HashSet<>();
                        Map<Position, Map<String, Object>> measureDataRows = new LinkedHashMap<>();
                        for (PlateGridInfo gridInfo : entry.getValue())
                        {
                            String measureName = gridInfo.getMeasureName();
                            if (measureName == null)
                                throw new ExperimentException("The measure name for plate (" + gridInfo.getPlate().getPlateId() + ") has not been specified in the data file.");

                            if (measures.contains(measureName))
                                throw new ExperimentException("The measure name (" + measureName + ") has been previously associated with data for the same plate.");
                            measures.add(measureName);

                            Plate currentPlate = gridInfo.getPlate();
                            Plate dataForPlate = PlateService.get().createPlate(currentPlate, gridInfo.getData(), null);
                            // get wells guarantees a consistent row/column oriented order
                            for (Well well : dataForPlate.getWells())
                            {
                                measureDataRows.computeIfAbsent(well, f -> getDataRowFromWell(currentPlate.getPlateId(), well, measureName)).put(measureName, well.getValue());
                            }
                        }

                        // add combined measures to rows for the entire run
                        dataRows.addAll(measureDataRows.values());
                    }
                }
                else
                {
                    for (PlateGridInfo gridInfo : plateTypeMapEntry.getValue())
                    {
                        Plate matchingPlate = gridInfo.getPlate();
                        if (matchingPlate != null)
                        {
                            double[][] plateGrid = gridInfo.getData();
                            PlateType plateGridType = PlateManager.get().getPlateType(plateGrid.length, plateGrid[0].length);
                            if (matchingPlate.getPlateType().equals(plateGridType))
                            {
                                Plate dataForPlate = PlateService.get().createPlate(matchingPlate, plateGrid, null);
                                for (Well well : dataForPlate.getWells())
                                    dataRows.add(getDataRowFromWell(matchingPlate.getPlateId(), well, defaultMeasureName));
                            }
                        }
                    }
                }
            }
        }
        // else if only one plateType was parsed (i.e. all 96-well plate grids), use plateGrids ordering to match plate set order
        else if (plateTypeGrids.keySet().size() == 1)
        {
            for (Map.Entry<PlateType, Collection<PlateGridInfo>> entry : plateTypeGrids.asMap().entrySet())
            {
                if (entry.getValue().size() > plates.size())
                    throw new ExperimentException("The number of plate grids parsed from the file exceeds the number of plates in the plate set.");

                int plateIndex = 0;
                for (PlateGridInfo gridInfo : entry.getValue())
                {
                    Plate targetPlate = plates.get(plateIndex++);
                    Plate dataForPlate = PlateService.get().createPlate(targetPlate, gridInfo.getData(), null);
                    for (Well well : dataForPlate.getWells())
                        dataRows.add(getDataRowFromWell(targetPlate.getPlateId(), well, defaultMeasureName));
                }
            }
        }
        else if (plateTypeGrids.keySet().size() > 1)
            throw new ExperimentException("Unable to match the plate grids parsed from the file to the plates in the plate set. Please include plate identifiers for the plate grids.");

        return dataRows;
    }

    private Map<String, Object> getDataRowFromWell(String plateId, Well well, String measure)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(AssayResultDomainKind.PLATE_COLUMN_NAME, plateId);
        row.put(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME, well.getDescription());
        row.put(measure, well.getValue());
        return row;
    }

    @Override
    @NotNull
    public OntologyManager.UpdateableTableImportHelper getImportHelper(
        Container container,
        User user,
        ExpRun run,
        ExpData data,
        ExpProtocol protocol,
        AssayProvider provider
    )
    {
        return new PlateMetadataImportHelper(data, container, user, run, protocol, provider);
    }

    private static class PlateMetadataImportHelper extends SimpleAssayDataImportHelper
    {
        private final Map<Integer, Map<Position, Lsid>> _wellPositionMap;
        private final Map<Object, Plate> _plateIdentifierMap;
        private final Container _container;
        private final User _user;
        private final ExpRun _run;
        private final ExpProtocol _protocol;
        private final AssayProvider _provider;

        public PlateMetadataImportHelper(ExpData data, Container container, User user, ExpRun run, ExpProtocol protocol, AssayProvider provider)
        {
            super(data);
            _wellPositionMap = new HashMap<>();
            _plateIdentifierMap = new HashMap<>();
            _container = container;
            _user = user;
            _run = run;
            _protocol = protocol;
            _provider = provider;
        }

        @Override
        public void bindAdditionalParameters(Map<String, Object> map, ParameterMapStatement target) throws ValidationException
        {
            super.bindAdditionalParameters(map, target);

            Domain runDomain = _provider.getRunDomain(_protocol);
            Domain resultDomain = _provider.getResultsDomain(_protocol);
            DomainProperty plateSetProperty = runDomain.getPropertyByName(AssayPlateMetadataService.PLATE_SET_COLUMN_NAME);
            DomainProperty templateProperty = runDomain.getPropertyByName(AssayPlateMetadataService.PLATE_TEMPLATE_COLUMN_NAME);
            DomainProperty plateProperty = resultDomain.getPropertyByName(AssayResultDomainKind.PLATE_COLUMN_NAME);
            DomainProperty wellLocationProperty = resultDomain.getPropertyByName(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME);

            // get the plate associated with this row (checking the results domain field first)
            Object plateIdentifier = PropertyService.get().getDomainPropertyValueFromRow(plateProperty, map);
            Plate plate = _plateIdentifierMap.get(plateIdentifier);
            if (plate == null)
            {
                if (plateSetProperty != null && plateIdentifier != null)
                {
                    Object plateSetVal = _run.getProperty(plateSetProperty);
                    Integer plateSetRowId = plateSetVal != null ? Integer.parseInt(String.valueOf(plateSetVal)) : null;
                    plate = PlateService.get().getPlate(PlateManager.get().getPlateContainerFilter(_protocol, _container, _user), plateSetRowId, plateIdentifier);
                }
                else if (templateProperty != null)
                {
                    Lsid plateLsid = Lsid.parse(String.valueOf(_run.getProperty(templateProperty)));
                    plate = PlateService.get().getPlate(PlateManager.get().getPlateContainerFilter(_protocol, _container, _user), plateLsid);
                }
                _plateIdentifierMap.put(plateIdentifier, plate);
            }

            if (plate == null)
                throw new ValidationException("Unable to resolve the plate for the data result row.");

            // create the map of well locations to the well table lsid for the plate
            if (!_wellPositionMap.containsKey(plate.getRowId()))
            {
                Map<Position, Lsid> positionToWellLsid = new HashMap<>();
                SimpleFilter filter = SimpleFilter.createContainerFilter(plate.getContainer());
                filter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());
                for (WellBean well : new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, null).getArrayList(WellBean.class))
                    positionToWellLsid.put(new PositionImpl(plate.getContainer(), well.getRow(), well.getCol()), Lsid.parse(well.getLsid()));
                _wellPositionMap.put(plate.getRowId(), positionToWellLsid);
            }
            Map<Position, Lsid> positionToWellLsid = _wellPositionMap.get(plate.getRowId());

            // to join plate based metadata to assay results we need to line up the incoming assay results with the
            // corresponding well on the plate used in the import
            String wellLocationStr = (String) PropertyService.get().getDomainPropertyValueFromRow(wellLocationProperty, map);
            if (wellLocationStr != null)
            {
                PositionImpl pos = new PositionImpl(_container, wellLocationStr);
                // need to adjust the column value to be 0 based to match the template locations
                pos.setCol(pos.getColumn() - 1);
                if (positionToWellLsid.containsKey(pos))
                    target.put(WELL_LSID_COLUMN_NAME, positionToWellLsid.get(pos));
            }
        }
    }

    private static class MetadataLayerImpl implements MetadataLayer
    {
        private final String _name;
        private final Map<String, MetadataWellGroup> _wellGroupMap = new CaseInsensitiveHashMap<>();

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
        private final String _name;
        private final Map<String, Object> _properties = new CaseInsensitiveHashMap<>();

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

    public static final class TestCase
    {
        private static Container container;
        private static User user;

        @BeforeClass
        public static void setup()
        {
            JunitUtil.deleteTestContainer();

            container = JunitUtil.getTestContainer();
            user = TestContext.get().getUser();
        }

        @Test
        public void testGridAnnotations() throws Exception
        {
            PlateType plateType = PlateManager.get().getPlateType(8, 12);
            assertNotNull("Expected 8x12 plate type to resolve", plateType);

            List<PlateManager.CreatePlateSetPlate> plates = List.of(
                new PlateManager.CreatePlateSetPlate(null, plateType.getRowId(), null, Collections.emptyList()),
                new PlateManager.CreatePlateSetPlate(null, plateType.getRowId(), null, Collections.emptyList())
            );

            PlateSet plateSet = PlateManager.get().createPlateSet(container, user, new PlateSetImpl(), plates, null);
            List<Plate> plateSetPlates = PlateManager.get().getPlatesForPlateSet(plateSet);
            assertEquals("Expected two plates to be created.", 2, plateSetPlates.size());
            Plate plate = plateSetPlates.get(0);

            PlateGridInfo gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of(plate.getPlateId())),
                    plateSet);
            assertEquals("Expected plate to resolve on annotation", plate.getRowId(), gridInfo.getPlate().getRowId());

            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of(plate.getName())),
                    plateSet);
            assertEquals("Expected plate to resolve on annotation", plate.getRowId(), gridInfo.getPlate().getRowId());

            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of(plate.getRowId().toString())),
                    plateSet);
            assertEquals("Expected plate to resolve on annotation", plate.getRowId(), gridInfo.getPlate().getRowId());

            // test for multiple annotations
            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of(plate.getPlateId(), "Density")),
                    plateSet);
            assertNull("Expected plate to not resolve on annotation without a prefix", gridInfo.getPlate());
            assertNull("Expected measure to not resolve on annotation without a prefix", gridInfo.getMeasureName());

            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of("PLATE:" + plate.getPlateId(), "Density")),
                    plateSet);
            assertEquals("Expected plate to resolve on annotation with a prefix", plate.getRowId(), gridInfo.getPlate().getRowId());
            assertNull("Expected measure to not resolve on annotation without a prefix", gridInfo.getMeasureName());

            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of("plate:" + plate.getPlateId(), "MEASURE : Density")),
                    plateSet);
            assertEquals("Expected plate to resolve on annotation with a prefix", plate.getRowId(), gridInfo.getPlate().getRowId());
            assertEquals("Expected measure to resolve on annotation with a prefix", "Density", gridInfo.getMeasureName());

            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of(plate.getPlateId(), "measure : Density")),
                    plateSet);
            assertNull("Expected plate to not resolve on annotation without a prefix", gridInfo.getPlate());
            assertEquals("Expected measure to resolve on annotation with a prefix", "Density", gridInfo.getMeasureName());
        }
    }
}
