package org.labkey.assay.data.generator;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateSetType;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.generator.DataGenerator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.gwt.client.assay.AssayException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.UnexpectedException;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.PlateSetImpl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import static org.labkey.assay.plate.PlateManager.CreatePlateSetPlate;

public class PlateSetDataGenerator extends DataGenerator<PlateSetDataGenerator.Config>
{
    private PlateType _plateType;
    private int _plateSetsCreated = 0;

    public PlateSetDataGenerator(PipelineJob job, PlateSetDataGenerator.Config config)
    {
        super(job, config);
    }

    public void generatePlateSets()
    {
        Config config = getConfig();

        if (config.getNumPlatesets() <= 0)
        {
            _log.info(String.format("No platesets generated because %s=%d", Config.NUM_PLATESETS, config.getNumPlatesets()));
            return;
        }

        if (config.getPlatesPerPlateset() > PlateSet.MAX_PLATES)
        {
            _log.info(String.format("The number of plates per platesets cannot exceed %d", PlateSet.MAX_PLATES));
            return;
        }

        _plateType = getPlateType();
        if (_plateType == null)
        {
            _log.error(String.format("Unable to resolve plate type (%s). Plate types must be expressed in the format : <rows>x<columns> eg: 8x12", config.getPlateType()));
            return;
        }

        // generate plates and plate sets
        if (config.getPlateLineageDepth() > 0)
            _log.info(String.format("Generating %d root Plate sets with %d level(s)",
                    config.getNumPlatesets(),
                    config.getPlateLineageDepth()));
        else
            _log.info(String.format("Generating %d root Plate sets", config.getNumPlatesets()));

        CPUTimer timer = addTimer("Plate Sets");
        timer.start();
        try
        {
            for (int i = 0; i < config.getNumPlatesets(); i++)
            {
                // root primary plate set
                PlateSet parentPlateSet = createPlateSet(PlateSetType.primary, null);

                if (config.getPlateLineageDepth() > 0)
                    createLevel(parentPlateSet, 0);
            }
            _log.info(String.format("Created a total of %d plate sets.", _plateSetsCreated));
        }
        catch (Exception e)
        {
            _log.error(e.getMessage());
            throw UnexpectedException.wrap(e);
        }
        timer.stop();
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
            createPlateSet(PlateSetType.assay, parent);
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

        // make sure we have enough distinct samples to populate a primary plate set
        if (plateSetType.equals(PlateSetType.primary))
            samplesNeeded = plateWells * getConfig().getPlatesPerPlateset();
        else
            samplesNeeded = plateWells * getConfig().getPlatesPerPlateset();

        List<Integer> ids = selectExistingSampleIds(samplesNeeded, samplesNeeded, ContainerFilter.current(getContainer()));
        if (ids.size() < samplesNeeded)
            throw new IllegalStateException(String.format("There are not enough samples to properly plate all of the data, you need at least (%d) samples.", samplesNeeded));

        PlateSetImpl plateSet = new PlateSetImpl();
        plateSet.setType(plateSetType);

        List<CreatePlateSetPlate> plates = new ArrayList<>();
        int sampleIdx = 0;
        for (int i=0; i < getConfig().getPlatesPerPlateset(); i++)
        {
            List<Map<String, Object>> rows = new ArrayList<>();

            // row, column ordering
            for (int row=0; row < _plateType.getRows(); row++)
            {
                for (int col=0; col < _plateType.getColumns(); col++)
                {
                    Position position = PlateManager.get().createPosition(getContainer(), row, col);
                    rows.add(Map.of(
                            "wellLocation", position.getDescription(),
                            "sampleId", ids.get(sampleIdx++)));
                }
            }
            plates.add(new CreatePlateSetPlate(null, _plateType.getRowId(), rows));
        }
        _plateSetsCreated++;
        return PlateManager.get().createPlateSet(getContainer(), getUser(), plateSet, plates, parentPlateSet != null ? parentPlateSet.getRowId() : null);
    }

    public static class Config extends DataGenerator.Config
    {
        public static final String NUM_PLATESETS = "numPlatesets";
        public static final String PLATES_PER_PLATESET = "platesPerPlateset";
        public static final String MIN_NUM_CUSTOM_PROPERTIES = "minCustomProperties";
        public static final String MAX_NUM_CUSTOM_PROPERTIES = "maxCustomProperties";
        public static final String PLATE_TYPE = "plateType";

        public static final String PLATE_LINEAGE_DEPTH = "plateLineageDepth";
        public static final String ASSAY_PLATE_SETS_PER_LEVEL = "assayPlateSetsPerLevel";
        public static final String PRIMARY_PLATE_SETS_PER_LEVEL = "primaryPlateSetsPerLevel";

        int _numPlatesets;
        int _platesPerPlateset;
        int _minCustomProperties;
        int _maxCustomProperties;
        int _plateLineageDepth;
        int _assayPlateSetsPerLevel;
        int _primaryPlateSetsPerLevel;

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
    }

    public static class Driver implements DataGenerationDriver
    {
        @Override
        public List<CPUTimer> generateData(PipelineJob job, Properties properties) throws ValidationException, AssayException, ExperimentException
        {
            PlateSetDataGenerator generator = new PlateSetDataGenerator(job, new PlateSetDataGenerator.Config(properties));
            generator.generatePlateSets();
            return generator.getTimers();
        }
    }
}
