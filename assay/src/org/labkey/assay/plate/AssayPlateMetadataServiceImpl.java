package org.labkey.assay.plate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssayRunDomainKind;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateTemplate;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.WellGroupTemplate;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.assay.TSVProtocolSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AssayPlateMetadataServiceImpl implements AssayPlateMetadataService
{
    private boolean _domainDirty;

    @Override
    public void addAssayPlateMetadata(ExpData resultData, Map<String, MetadataLayer> plateMetadata, Container container, User user, ExpRun run, AssayProvider provider, ExpProtocol protocol,
                                      List<Map<String, Object>> inserted, Map<Integer, String> rowIdToLsidMap) throws ExperimentException
    {
        PlateTemplate template = getPlateTemplate(run, provider, protocol);
        if (template != null)
        {
            try
            {
                if (plateMetadata.isEmpty())
                {
                    throw new ExperimentException("No plate information was parsed from the JSON metadata, please check the format of the metadata.");
                }
                Map<Position, Map<String, Object>> plateData = new HashMap<>();
                Domain domain = ensureDomain(protocol);

                // map the metadata to the plate template
                for (int row=0; row < template.getRows(); row++)
                {
                    for (int col=0; col < template.getColumns(); col++)
                    {
                        Position pos = template.getPosition(row, col);
                        Map<String, Object> wellProps = new CaseInsensitiveHashMap<>();
                        plateData.put(pos, wellProps);

                        for (WellGroupTemplate group : template.getWellGroups(pos))
                        {
                            MetadataLayer plateLayer = plateMetadata.get(group.getType().name());
                            if (plateLayer != null)
                            {
                                // ensure the column for the plate layer that we will insert the well group name into
                                String layerName = plateLayer.getName() + TSVProtocolSchema.PLATE_DATA_LAYER_SUFFIX;
                                ensureDomainProperty(user, domain, layerName, JdbcType.VARCHAR);

                                MetadataWellGroup wellGroup = plateLayer.getWellGroups().get(group.getName());
                                if (wellGroup != null)
                                {
                                    // insert the well group name into the layer
                                    wellProps.put(layerName, wellGroup.getName());

                                    // combine both properties from the metadata as well as those explicitly set on the plate template
                                    Map<String, Object> props = new HashMap<>(wellGroup.getProperties());
                                    props.putAll(((WellGroupTemplateImpl)group).getProperties());

                                    for (Map.Entry<String, Object> entry : props.entrySet())
                                    {
                                        DomainProperty domainProperty = ensureDomainProperty(user, domain, entry.getKey(), JdbcType.valueOf(entry.getValue().getClass()));
                                        if (!wellProps.containsKey(domainProperty.getName()))
                                            wellProps.put(domainProperty.getName(), entry.getValue());
                                        else
                                            throw new ExperimentException("The metadata property name : " + domainProperty.getName() + " already exists from a different well group for " +
                                                    "the well location : " + pos.getDescription() + ". If well groups overlap from different layers, their metadata property names " +
                                                    "need to be unique.");
                                    }
                                }
                            }
                        }
                    }
                }

                if (_domainDirty)
                {
                    domain.save(user);
                    domain = getPlateDataDomain(protocol);
                }

                Map<String, PropertyDescriptor> descriptorMap = domain.getProperties().stream().collect(Collectors.toMap(DomainProperty :: getName, DomainProperty :: getPropertyDescriptor));
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
                                        jsonRow.put(descriptorMap.get(k).getURI(), v);
                                        propsToInsert.add(descriptorMap.get(k));
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
                    try
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
                    catch (Exception e)
                    {
                        throw new ExperimentException(e);
                    }
                }
            }
            catch (Exception e)
            {
                throw new ExperimentException(e);
            }
        }
        else
        {
            throw new ExperimentException("Unable to resolve the plate template for the run");
        }
    }

    @Nullable
    private PlateTemplate getPlateTemplate(ExpRun run, AssayProvider provider, ExpProtocol protocol)
    {
        Domain runDomain = provider.getRunDomain(protocol);
        DomainProperty plateTemplate = runDomain.getPropertyByName(AssayRunDomainKind.PLATE_TEMPLATE_COLUMN_NAME);
        if (plateTemplate != null)
        {
            Object templateLsid = run.getProperty(plateTemplate);
            if (templateLsid instanceof String)
            {
                return PlateService.get().getPlateTemplateFromLsid(protocol.getContainer(), (String)templateLsid);
            }
        }
        return null;
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

    private DomainProperty ensureDomainProperty(User user, Domain domain, String name, JdbcType type) throws ExperimentException
    {
        DomainProperty domainProperty = domain.getPropertyByName(name);
        if (domainProperty == null)
        {
            // we are dynamically adding a new property to the plate data domain, ensure the user has at least
            // the DesignAssayPermission
            if (!domain.getContainer().hasPermission(user, DesignAssayPermission.class))
                throw new ExperimentException("This import will create a new plate metadata field : " + name + ". Only users with the AssayDesigner role are allowed to do this.");

            _domainDirty = true;
            PropertyStorageSpec spec = new PropertyStorageSpec(name, type);
            return domain.addProperty(spec);
        }
        return domainProperty;
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
