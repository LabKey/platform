package org.labkey.api.data.generator;

import java.util.HashMap;
import java.util.Map;

public class DataGeneratorRegistry
{
    public enum DataType {
        AssayDesigns,
        AssayRunData,
        StorageHierarchy,
        SamplesInStorage,
        WorkflowJobs,
        Notebooks,
        PlateSets,
    };

    private static final Map<DataType, DataGenerator.DataGenerationDriver> _dataGeneratorMap = new HashMap<>();

    public static void registerGenerator(DataType type, DataGenerator.DataGenerationDriver generator)
    {
        _dataGeneratorMap.put(type, generator);
    }

    public static DataGenerator.DataGenerationDriver getGenerator(DataType type)
    {
        return _dataGeneratorMap.get(type);
    }
}
