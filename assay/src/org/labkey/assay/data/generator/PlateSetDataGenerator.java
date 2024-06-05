package org.labkey.assay.data.generator;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.DefaultAssayRunCreator;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateCustomField;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetType;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.generator.DataGenerator;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;
import org.labkey.assay.TsvAssayProvider;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.PlateSetImpl;
import org.labkey.assay.plate.query.WellTable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.assay.plate.PlateManager.CreatePlateSetPlate;

public class PlateSetDataGenerator extends DataGenerator<PlateSetDataGenerator.Config>
{
    private static final int MAX_LINEAGE_DEPTH = 10;
    private static final int MAX_PLATE_SETS_PER_LEVEL = 10;
    private PlateType _plateType;
    private int _plateSetsCreated = 0;
    private final List<String> _wellProperties = new ArrayList<>();
    private static final String INT_PROP_PREFIX = "IntegerProp";
    private static final String DOUBLE_PROP_PREFIX = "DoubleProp";
    private final List<PlateSet> _assayPlateSets = new ArrayList<>();

    public PlateSetDataGenerator(PipelineJob job, PlateSetDataGenerator.Config config)
    {
        super(job, config);
    }

    public void generatePlateSets()
    {
        Config config = getConfig();
        if (validateConfiguration(config))
        {
            _plateType = getPlateType();
            if (_plateType == null)
            {
                _log.error(String.format("Unable to resolve plate type (%s). Plate types must be expressed in the format : <rows>x<columns> eg: 8x12", config.getPlateType()));
                return;
            }

            // generate plates and plate sets
            if (config.getPlateLineageDepth() > 0)
                _log.info(String.format("Generating %d root Plate set(s) with %d level(s)",
                        config.getNumPlatesets(),
                        config.getPlateLineageDepth()));
            else
                _log.info(String.format("Generating %d root Plate set(s)", config.getNumPlatesets()));

            try
            {
                CPUTimer timer = addTimer("Plate Sets");
                timer.start();
                createWellProperties(config);
                for (int i = 0; i < config.getNumPlatesets(); i++)
                {
                    // root primary plate set
                    PlateSet parentPlateSet = createPlateSet(PlateSetType.primary, null);

                    if (config.getPlateLineageDepth() > 0)
                        createLevel(parentPlateSet, 0);
                }
                _log.info(String.format("Created a total of %d plate sets.", _plateSetsCreated));
                timer.stop();

                if (config._importPlatesets)
                {
                    CPUTimer importTimer = addTimer("Plate Sets Assay Import");
                    importTimer.start();
                    importPlatesets();
                    importTimer.stop();
                }
            }
            catch (Exception e)
            {
                _log.error(e.getMessage());
                throw UnexpectedException.wrap(e);
            }
        }
    }

    private boolean validateConfiguration(Config config)
    {
        if (config.getNumPlatesets() <= 0)
        {
            _log.info(String.format("No plate sets generated because %s=%d", Config.NUM_PLATESETS, config.getNumPlatesets()));
            return false;
        }

        if (config.getPlatesPerPlateset() > PlateSet.MAX_PLATES)
        {
            _log.error(String.format("The number of plates per plates ets cannot exceed %d", PlateSet.MAX_PLATES));
            return false;
        }

        if (config.getPlateLineageDepth() > MAX_LINEAGE_DEPTH)
        {
            _log.error(String.format("The max plate set lineage depth cannot exceed %d", MAX_LINEAGE_DEPTH));
            return false;
        }

        if ((config.getPrimaryPlateSetsPerLevel() + config.getAssayPlateSetsPerLevel()) > MAX_PLATE_SETS_PER_LEVEL)
        {
            _log.error(String.format("The max number of plate sets per level cannot exceed %d", MAX_PLATE_SETS_PER_LEVEL));
            return false;
        }

        if (config.getMinCustomProperties() > config.getMaxCustomProperties())
        {
            _log.error(String.format("The max number of plate sets per level cannot exceed %d", MAX_PLATE_SETS_PER_LEVEL));
            return false;
        }

        if (config.isImportPlatesets() && (config.getPlateLineageDepth() == 0 || config.getAssayPlateSetsPerLevel() == 0))
        {
            _log.error("Unable to import plate set data into an assay, there must be at least one assay plate set. This can be created by " +
                    "specifying a lineage depth greater than zero and at least one assay plate set per level.");
            return false;
        }
        return true;
    }

    private void createWellProperties(Config config) throws Exception
    {
        if (config.getMaxCustomProperties() > 0)
        {
            Set<String> existingProps = PlateManager.get().getPlateMetadataFields(getContainer(), getUser()).stream().map(PlateCustomField::getName).collect(Collectors.toSet());
            List<GWTPropertyDescriptor> customFields = new ArrayList<>();
            for (int i=0; i < config.getMaxCustomProperties(); i++)
            {
                String prefix = ((i % 2) == 0) ? INT_PROP_PREFIX : DOUBLE_PROP_PREFIX;
                String rangeUri = ((i % 2) == 0) ? "http://www.w3.org/2001/XMLSchema#int" : "http://www.w3.org/2001/XMLSchema#double";

                String name = getUniqueCustomPropName(prefix, existingProps);
                _wellProperties.add(name);
                customFields.add(new GWTPropertyDescriptor(name, rangeUri));
            }
            PlateManager.get().createPlateMetadataFields(getContainer(), getUser(), customFields);
        }
    }

    private String getUniqueCustomPropName(String prefix, Set<String> existing)
    {
        int i=1;
        String name = prefix + "_" + i;
        while (existing.contains(name))
        {
            name = prefix + "_" + (++i);
        }
        existing.add(name);
        return name;
    }

    private void createLevel(PlateSet parent, int depth) throws Exception
    {
        if (depth > getConfig().getPlateLineageDepth())
            return;

        for (int i=0; i < getConfig().getPrimaryPlateSetsPerLevel(); i++)
        {
            int currentDepth = depth + 1;
            PlateSet plateSet = createPlateSet(PlateSetType.primary, parent);
            createLevel(plateSet, currentDepth);
        }

        for (int i=0; i < getConfig().getAssayPlateSetsPerLevel(); i++)
        {
            _assayPlateSets.add(createPlateSet(PlateSetType.assay, parent));
        }
    }

    private @Nullable PlateType getPlateType()
    {
        String plateType = getConfig().getPlateType();
        if (!StringUtils.isEmpty(plateType))
        {
            String[] parts = plateType.split("x");
            if (parts.length == 2)
            {
                int rows = Integer.parseInt(parts[0]);
                int cols = Integer.parseInt(parts[1]);

                return PlateManager.get().getPlateType(rows, cols);
            }
        }
        return null;
    }

    private PlateSet createPlateSet(PlateSetType plateSetType, @Nullable PlateSet parentPlateSet) throws Exception
    {
        int plateWells = _plateType.getColumns() * _plateType.getRows();
        int samplesNeeded = plateWells;
        checkAlive(_job);

        if (plateSetType.equals(PlateSetType.primary))
            // make sure we have enough distinct samples to populate a primary plate set
            samplesNeeded = plateWells * getConfig().getPlatesPerPlateset();
        else
            // for now assay plate sets will have distinct samples per plate (even though this is not enforced)
            samplesNeeded = plateWells * getConfig().getPlatesPerPlateset();

        List<Integer> ids = selectExistingSampleIds(samplesNeeded, samplesNeeded, ContainerFilter.current(getContainer()));
        if (ids.size() < samplesNeeded)
            throw new IllegalStateException(String.format("There are not enough samples to properly plate all of the data, you need at least (%d) samples.", samplesNeeded));

        PlateSetImpl plateSet = new PlateSetImpl();
        plateSet.setType(plateSetType);

        List<CreatePlateSetPlate> plates = new ArrayList<>();
        int sampleIdx = 0;

        // custom well properties to add to this plate set
        List<String> wellProperties = new ArrayList<>();
        if (getConfig().getMaxCustomProperties() > 0)
        {
            int count = randomInt(getConfig().getMinCustomProperties(), getConfig().getMaxCustomProperties());
            for (int i=0; i < count; i++)
                wellProperties.add(_wellProperties.get(i));
        }

        for (int i=0; i < getConfig().getPlatesPerPlateset(); i++)
        {
            List<Map<String, Object>> rows = new ArrayList<>();

            // row, column ordering
            for (int row=0; row < _plateType.getRows(); row++)
            {
                for (int col=0; col < _plateType.getColumns(); col++)
                {
                    Position position = PlateManager.get().createPosition(getContainer(), row, col);
                    Map<String, Object> rowMap = new HashMap<>();

                    rowMap.put(WellTable.WELL_LOCATION, position.getDescription());
                    rowMap.put(WellTable.Column.Type.name(), WellGroup.Type.SAMPLE.name());
                    rowMap.put(WellTable.Column.SampleId.name(), ids.get(sampleIdx++));

                    for (String name : wellProperties)
                    {
                        if (name.startsWith(INT_PROP_PREFIX))
                            rowMap.put(WellTable.Column.Properties.name() + "/" + name, randomInt(1, 5000));
                        else
                            rowMap.put(WellTable.Column.Properties.name() + "/" + name, randomDouble(1, 100000));
                    }
                    rows.add(rowMap);
                }
            }
            plates.add(new CreatePlateSetPlate(null, _plateType.getRowId(), null, rows));
        }
        _plateSetsCreated++;
        return PlateManager.get().createPlateSet(getContainer(), getUser(), plateSet, plates, parentPlateSet != null ? parentPlateSet.getRowId() : null);
    }

    private void importPlatesets() throws Exception
    {
        if (_assayPlateSets.isEmpty())
            throw new ValidationException("There are no assay plate sets to import");
        _log.info(String.format("Importing data for %d plate sets.", _assayPlateSets.size()));

        AssayProvider provider = AssayService.get().getProvider(TsvAssayProvider.NAME);
        if (provider == null)
            throw new ValidationException(String.format("No provider for \"%s\" assays.", TsvAssayProvider.NAME));

        ExpProtocol assayToImport = null;
        for (ExpProtocol protocol : AssayService.get().getAssayProtocols(getContainer(), provider))
        {
            if (provider.isPlateMetadataEnabled(protocol))
            {
                assayToImport = protocol;
                break;
            }
        }

        if (assayToImport == null)
            throw new ValidationException("No protocols with plate support enabled");

        ViewContext context = new ViewContext();
        context.setUser(getUser());
        context.setContainer(getContainer());

        DomainProperty measure = provider.getResultsDomain(assayToImport)
                .getProperties()
                .stream()
                .filter(dp -> dp.getName().startsWith("FloatField")).findFirst().orElse(null);

        for (PlateSet plateSet : _assayPlateSets)
        {
            checkAlive(_job);
            List<Map<String, Object>> rawData = createRunData(plateSet, measure);
            var factory = provider.createRunUploadFactory(assayToImport, context)
                    .setRunProperties(Map.of(AssayPlateMetadataService.PLATE_SET_COLUMN_NAME, plateSet.getRowId()))
                    .setRawData(rawData)
                    .setUploadedData(Collections.emptyMap());
            Map<Object, String> outputData = new HashMap<>();

            DefaultAssayRunCreator.generateResultData(getUser(), getContainer(), provider, rawData, outputData, null);
            factory.setOutputDatas(outputData);
            provider.getRunCreator().saveExperimentRun(factory.create(), null);
        }
    }

    private List<Map<String, Object>> createRunData(PlateSet plateSet, @Nullable DomainProperty measure)
    {
        // create a row for every plate in the plate set
        List<Map<String, Object>> data = new ArrayList<>();
        for (Plate plate : plateSet.getPlates(getUser()))
        {
            for (int row=0; row < _plateType.getRows(); row++)
            {
                for (int col=0; col < _plateType.getColumns(); col++)
                {
                    Position pos = PlateManager.get().createPosition(getContainer(), row, col);
                    if (measure != null)
                        data.add(Map.of("plate", plate.getPlateId(), "wellLocation", pos.getDescription(), measure.getName(), randomDouble(1, 100000)));
                    else
                        data.add(Map.of("plate", plate.getPlateId(), "wellLocation", pos.getDescription()));
                }
            }
        }
        return data;
    }

    public static class Config extends DataGenerator.Config
    {
        public static final String NUM_PLATESETS = "numPlateSets";
        public static final String PLATES_PER_PLATESET = "platesPerPlateSet";
        public static final String MIN_NUM_CUSTOM_PROPERTIES = "minCustomWellProperties";
        public static final String MAX_NUM_CUSTOM_PROPERTIES = "maxCustomWellProperties";
        public static final String PLATE_TYPE = "plateType";

        public static final String PLATE_LINEAGE_DEPTH = "plateLineageDepth";
        public static final String ASSAY_PLATE_SETS_PER_LEVEL = "assayPlateSetsPerLevel";
        public static final String PRIMARY_PLATE_SETS_PER_LEVEL = "primaryPlateSetsPerLevel";
        public static final String IMPORT_PLATE_SETS = "importPlateSets";

        int _numPlatesets;
        int _platesPerPlateset;
        int _minCustomProperties;
        int _maxCustomProperties;
        int _plateLineageDepth;
        int _assayPlateSetsPerLevel;
        int _primaryPlateSetsPerLevel;
        boolean _importPlatesets;

        String _plateType;

        public Config(Properties properties)
        {
            super(properties);
            _numPlatesets = Integer.parseInt(properties.getProperty(NUM_PLATESETS, "0"));
            _platesPerPlateset = Integer.parseInt(properties.getProperty(PLATES_PER_PLATESET, "0"));
            _minCustomProperties = Integer.parseInt(properties.getProperty(MIN_NUM_CUSTOM_PROPERTIES, "0"));
            _maxCustomProperties = Integer.parseInt(properties.getProperty(MAX_NUM_CUSTOM_PROPERTIES, "0"));

            _plateLineageDepth = Integer.parseInt(properties.getProperty(PLATE_LINEAGE_DEPTH, "0"));
            _assayPlateSetsPerLevel = Integer.parseInt(properties.getProperty(ASSAY_PLATE_SETS_PER_LEVEL, "0"));
            _primaryPlateSetsPerLevel = Integer.parseInt(properties.getProperty(PRIMARY_PLATE_SETS_PER_LEVEL, "0"));
            _importPlatesets = Boolean.parseBoolean(properties.getProperty(IMPORT_PLATE_SETS, "false"));

            _plateType = properties.getProperty(PLATE_TYPE, "8x12");
        }

        public int getNumPlatesets()
        {
            return _numPlatesets;
        }

        public int getPlatesPerPlateset()
        {
            return _platesPerPlateset;
        }

        public int getMinCustomProperties()
        {
            return _minCustomProperties;
        }

        public int getMaxCustomProperties()
        {
            return _maxCustomProperties;
        }

        public int getPlateLineageDepth()
        {
            return _plateLineageDepth;
        }

        public int getAssayPlateSetsPerLevel()
        {
            return _assayPlateSetsPerLevel;
        }

        public int getPrimaryPlateSetsPerLevel()
        {
            return _primaryPlateSetsPerLevel;
        }

        public String getPlateType()
        {
            return _plateType;
        }

        public boolean isImportPlatesets()
        {
            return _importPlatesets;
        }
    }

    public static class Driver implements DataGenerationDriver
    {
        @Override
        public List<CPUTimer> generateData(PipelineJob job, Properties properties)
        {
            PlateSetDataGenerator generator = new PlateSetDataGenerator(job, new PlateSetDataGenerator.Config(properties));
            generator.generatePlateSets();
            return generator.getTimers();
        }
    }
}
