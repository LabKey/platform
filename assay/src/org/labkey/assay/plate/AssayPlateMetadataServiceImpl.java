package org.labkey.assay.plate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateTemplate;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.WellGroupTemplate;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AssayPlateMetadataServiceImpl implements AssayPlateMetadataService
{
    private boolean _domainDirty;

    @Override
    public void addAssayPlateMetadata(ExpData plateMetadata, Container container, User user, ExpRun run, AssayProvider provider, ExpProtocol protocol,
                                      List<Map<String, Object>> inserted, Map<Integer, String> rowIdToLsidMap) throws ExperimentException
    {
        PlateTemplate template = getPlateTemplate(run, provider, protocol);
        if (template != null)
        {
            Map<String, PlateLayer> layers = parseDataFile(plateMetadata.getFile());
            List<Map<String, Object>> rows = new ArrayList<>();
            Domain domain = ensureDomain(protocol);

            // map the metadata to the plate template
            for (int row=0; row < template.getRows(); row++)
            {
                for (int col=0; col < template.getColumns(); col++)
                {
                    Position pos = template.getPosition(row, col);
                    Map<String, Object> rowMap = new HashMap<>();
                    rows.add(rowMap);

                    rowMap.put("WellLocation", pos.getDescription());
                    for (WellGroupTemplate group : template.getWellGroups(pos))
                    {
                        PlateLayer plateLayer = layers.get(group.getType().name());
                        if (plateLayer != null)
                        {
                            PlateLayer.WellGroup wellGroup = plateLayer.getWellGroups().get(group.getName());
                            if (wellGroup != null)
                            {
                                for (Map.Entry<String, Object> entry : wellGroup.getProperties().entrySet())
                                {
                                    DomainProperty domainProperty = ensureDomainProperty(domain, entry);
                                    rowMap.put(domainProperty.getName(), entry.getValue());
                                }
                            }
                        }
                    }
                }
            }

            if (_domainDirty)
                domain.save(user);
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
        DomainProperty plateTemplate = runDomain.getPropertyByName("PlateTemplate");
        if (plateTemplate != null)
        {
            Object templateId = run.getProperty(plateTemplate);
            if (templateId instanceof Integer)
            {
                return PlateService.get().getPlateTemplate(protocol.getContainer(), (int)templateId);
            }
        }
        return null;
    }

    private Map<String, PlateLayer> parseDataFile(File dataFile) throws ExperimentException
    {
        try
        {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, PlateLayer> layers = new CaseInsensitiveHashMap<>();

            JsonNode rootNode = mapper.readTree(dataFile);

            JsonNode metadata = rootNode.get("metadata");
            metadata.fields().forEachRemaining(layerEntry -> {

                String layerName = layerEntry.getKey();
                if (!layers.containsKey(layerName))
                    layers.put(layerName, new PlateLayer(layerName));

                PlateLayer currentLayer = layers.get(layerName);

                layerEntry.getValue().fields().forEachRemaining(wellEntry -> {

                    if (!currentLayer.getWellGroups().containsKey(wellEntry.getKey()))
                        currentLayer.addWellGroup(new PlateLayer.WellGroup(wellEntry.getKey()));

                    PlateLayer.WellGroup currentWellGroup = currentLayer.getWellGroups().get(wellEntry.getKey());
                    wellEntry.getValue().fields().forEachRemaining(propEntry -> {
                        if (propEntry.getValue().isTextual())
                            currentWellGroup.addProperty(propEntry.getKey(), propEntry.getValue().textValue());
                        else if (propEntry.getValue().isNumber())
                            currentWellGroup.addProperty(propEntry.getKey(), propEntry.getValue().numberValue());
                        else if (propEntry.getValue().isBoolean())
                            currentWellGroup.addProperty(propEntry.getKey(), propEntry.getValue().booleanValue());
                        else
                        {
                            // log a warning
                        }
                    });
                });
            });

            return layers;
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
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

    private DomainProperty ensureDomainProperty(Domain domain, Map.Entry<String, Object> prop)
    {
        DomainProperty domainProperty = domain.getPropertyByName(prop.getKey());
        if (domainProperty == null && prop.getValue() != null)
        {
            PropertyStorageSpec spec = new PropertyStorageSpec(prop.getKey(), JdbcType.valueOf(prop.getValue().getClass()));
            return domain.addProperty(spec);
        }
        return domainProperty;
    }

    private static class PlateLayer
    {
        private String _name;
        private Map<String, WellGroup> _wellGroupMap = new CaseInsensitiveHashMap<>();

        public PlateLayer(String name)
        {
            _name = name;
        }

        public String getName()
        {
            return _name;
        }

        public Map<String, WellGroup> getWellGroups()
        {
            return _wellGroupMap;
        }

        public void addWellGroup(WellGroup wellGroup)
        {
            _wellGroupMap.put(wellGroup.getName(), wellGroup);
        }

        public static class WellGroup
        {
            private String _name;
            private Map<String, Object> _properties = new CaseInsensitiveHashMap<>();

            public WellGroup(String name)
            {
                _name = name;
            }

            public String getName()
            {
                return _name;
            }

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
}
