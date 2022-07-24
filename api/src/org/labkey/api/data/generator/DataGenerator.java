package org.labkey.api.data.generator;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.util.Pair;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class DataGenerator<T extends DataGenerator.Config>
{
    protected Container _container;
    protected final User _user;
    protected final Logger _log;
    protected T  _config;

    // Keep the set of timers so we can produce a report of all times at the end
    protected final List<CPUTimer> _timers = new ArrayList<>();

    protected List<ExpSampleType> _sampleTypes = new ArrayList<>();
    protected final Map<String, Pair<String, Long>> _nameData = new HashMap<>();

    protected List<ExpDataClass> _customDataClasses = new ArrayList<>();

    // map from rowId to # of generations (including the root) TODO remove
    private final Map<Integer, Integer> _sampleGenerations = new HashMap<>();
    // Map from rowId to # of aliquots. TODO remove
    private final Map<Integer, Integer> _numAliquotsPerParent = new HashMap<>();

    protected final UserSchema _samplesSchema;
    protected final UserSchema _dataClassSchema;

    protected final int _sampleCount = 0;

    record FieldPrefix(String uri, String namePrefix) { }

    private static final List<FieldPrefix> fieldPrefixes = new ArrayList<>();
    static {
        fieldPrefixes.add(new FieldPrefix("string", "TextField"));
        fieldPrefixes.add(new FieldPrefix("int", "IntField"));
        fieldPrefixes.add(new FieldPrefix("float", "FloatField"));
        fieldPrefixes.add(new FieldPrefix("date", "DateField"));
    }

    public DataGenerator(Container container, User user, T config, Logger log)
    {
        _container = container;
        _user = user;
        _log = log;
        _config = config;
        _samplesSchema = QueryService.get().getUserSchema(_user, _container, SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME));
        _dataClassSchema = QueryService.get().getUserSchema(_user, _container, ExpSchema.SCHEMA_EXP_DATA);
    }

    public Container getContainer()
    {
        return _container;
    }

    public void setContainer(Container container)
    {
        _container = container;
    }

    public T getConfig()
    {
        return _config;
    }

    public void setConfig(T config)
    {
        _config = config;
    }

    public List<ExpSampleType> getSampleTypes()
    {
        return _sampleTypes;
    }

    public void setSampleTypes(List<ExpSampleType> sampleTypes)
    {
        _sampleTypes = sampleTypes;
    }

    public List<ExpDataClass> getCustomDataClasses()
    {
        return _customDataClasses;
    }

    public void setCustomDataClasses(List<ExpDataClass> customDataClasses)
    {
        _customDataClasses = customDataClasses;
    }

    public void generateSampleTypes(String namePrefix, String namingPatternPrefix) throws ExperimentException, SQLException
    {
        int numSampleTypes = _config.getNumSampleTypes();
        int minFields = _config.getMinFields();
        int maxFields = _config.getMaxFields();

        int fieldIncrement = numSampleTypes <= 1 ? 0 : (maxFields - minFields)/(numSampleTypes-1);

        CPUTimer timer = new CPUTimer("Sample Type Generation");
        timer.start();
        int numFields = minFields;
        int typeIndex = 0;
        for (int i = 0; i < numSampleTypes; i++)
        {
            String sampleTypeName;
            do {
                typeIndex++;
                sampleTypeName = namePrefix + typeIndex;
            } while (SampleTypeService.get().getSampleType(_container, _user, sampleTypeName) != null);
            String prefixWithIndex = namingPatternPrefix + typeIndex + "_";
            String namingPattern = prefixWithIndex + "${genId}";
            ExpSampleType sampleType = generateSampleType(sampleTypeName, namingPattern, numFields);
            Pair<String, Long> nameData = new Pair<>(prefixWithIndex, sampleType.getCurrentGenId());
            _nameData.put(sampleTypeName, nameData);
            _sampleTypes.add(sampleType);
            numFields = Math.min(numFields + fieldIncrement, maxFields);
        }
        timer.stop();

        _log.info(String.format("Generating %d sample types took %s", numSampleTypes, timer.getDuration() + "."));
    }

    public ExpSampleType generateSampleType(String sampleTypeName, @Nullable String namingPattern, int numFields) throws ExperimentException, SQLException
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("Name", "string"));
        addDomainProperties(props, numFields);

        SampleTypeService service = SampleTypeService.get();
        _log.info(String.format("Creating Sample Type '%s' with %d fields", sampleTypeName, numFields));
        return service.createSampleType(_container, _user, sampleTypeName,
                "Generated sample type", props, List.of(), namingPattern);
    }

    public void generateSamplesForAllTypes() throws SQLException, BatchValidationException, QueryUpdateServiceException, DuplicateKeyException, InvalidKeyException
    {
        DataGenerator.Config config = getConfig();

        int sampleIncrement = config.getNumSampleTypes() <= 1 ? 0 : (config.getMaxSamples() - config.getMinSamples())/(config.getNumSampleTypes()-1);
        int numSamples = config.getMinSamples();
        for (int i = 0; i < config.getNumSampleTypes(); i++)
        {
            ExpSampleType sampleType = _sampleTypes.get(i);
            // TODO take into account number of samples that exist
            _log.info(String.format("Generating %d samples for sample type %s.", numSamples, sampleType.getName()));
            CPUTimer timer = new CPUTimer("Generate " + sampleType.getName() + " samples");
            _timers.add(timer);
            timer.start();
            generateSamples(_sampleTypes.get(i), numSamples);
            timer.stop();
            _log.info(String.format("Generating %d samples for sample type %s took %s.", numSamples, sampleType.getName(), timer.getDuration()));

            numSamples = Math.min(numSamples+sampleIncrement, config.getMaxSamples());
        }
    }

    public void generateSamples(ExpSampleType sampleType, int numSamplesAndAliquots) throws SQLException, BatchValidationException, QueryUpdateServiceException, DuplicateKeyException
    {
        TableInfo tableInfo = _samplesSchema.getTable(sampleType.getName());
        QueryUpdateService svc = tableInfo.getUpdateService();
        int numAliquots = Math.round(numSamplesAndAliquots * _config.getPctAliquots());
        int numPooled = Math.round(numSamplesAndAliquots * _config.getPctPooled());
        int numSamples = numSamplesAndAliquots - numAliquots - numPooled;

        generateDomainData(numSamples, svc, sampleType.getDomain());
        // TODO create 75% of the pooled samples
        int aliquotCount = generateAliquots(sampleType, svc, numAliquots);
        // TODO create the other pooled samples from aliquots
//      poolSamples(samples, svc, sampleType.getName(), Math.round(numSamplesAndAliquots * _config.getPctPooled()));
    }

    public int generateAliquots(ExpSampleType sampleType, QueryUpdateService svc, int quantity) throws SQLException, BatchValidationException, QueryUpdateServiceException, DuplicateKeyException
    {
        _log.info(String.format("Generating %d aliquots for sample type %s.", quantity, sampleType.getName()));
        CPUTimer timer = new CPUTimer(sampleType.getName() + " aliquots");
        timer.start();
        int totalAliquots = 0;
        int iterations = 0;
        int numGenerated;
        do
        {
            List<Map<String, Object>> parents = getRandomSamples(sampleType, Math.min(100, quantity/10));
            numGenerated = generateAliquotsForParents(parents, svc, quantity);
            totalAliquots += numGenerated;
            iterations++;
        } while (totalAliquots < quantity && numGenerated > 0);
        timer.stop();
        if (totalAliquots < quantity)
            _log.warn(String.format("Generated only %d aliquots after %d iterations", totalAliquots, iterations));
        _log.info(String.format("Generating %d aliquots for sample type %s in %d iterations took %s.", totalAliquots, sampleType.getName(), iterations, timer.getDuration()));
        return totalAliquots;
    }

    private int generateAliquotsForParents(List<Map<String, Object>> parents, QueryUpdateService svc, int quantity) throws SQLException, BatchValidationException, QueryUpdateServiceException, DuplicateKeyException
    {
        int generatedCount = 0;
        List<Map<String, Object>> allAliquots = new ArrayList<>();
        for (int p = 0; p < parents.size() && generatedCount < quantity; p++)
        {
            List<Map<String, Object>> rows = new ArrayList<>();

            Map<String, Object> parent = parents.get(p);
            Integer parentId = (Integer) parent.get("rowId");
            // skip any parents that already are at the max depth
            if (_sampleGenerations.getOrDefault(parentId, 1) > _config.getMaxGenerations())
                continue;

            // choose a number of aliquots to create
            int currentAliquots = _numAliquotsPerParent.getOrDefault(parentId, 0);
            int numAliquots = Math.min(randomInt(0, _config.getMaxAliquotsPerParent()), _config.getMaxGenerations() - currentAliquots);
            numAliquots = Math.min(numAliquots, quantity - generatedCount);
            // generate that number of aliquots
            for (int i = 0; i < numAliquots; i++)
            {
                Map<String, Object> row = new CaseInsensitiveHashMap<>();
                row.put("AliquotedFrom", parent.get("Name"));
                rows.add(row);
            }
            if (!rows.isEmpty())
            {
                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> aliquots = svc.insertRows(_user, _container, rows, errors, null, null);
                if (errors.hasErrors())
                    throw errors;

                // record generation number for the aliquots
                int parentGen = _sampleGenerations.getOrDefault(parentId, 1);
                aliquots.forEach(aliquot -> _sampleGenerations.put((Integer) aliquot.get("RowId"), parentGen + 1));
                _numAliquotsPerParent.put(parentId, currentAliquots + numAliquots);
                generatedCount += numAliquots;
                allAliquots.addAll(aliquots);
            }
        }
        // for each of the aliquots, possibly generate further aliquot generations
        if (generatedCount < quantity)
        {
            generatedCount += generateAliquotsForParents(allAliquots, svc, quantity-generatedCount);
        }
        return generatedCount;
    }

    private List<Map<String, Object>> getRandomSamples(ExpSampleType sampleType, int quantity)
    {
        TableInfo tableInfo = _samplesSchema.getTable(sampleType.getName());
        var nameGenData = _nameData.get(sampleType.getName());
        return getRowsByRandomNames(tableInfo, nameGenData.first, nameGenData.second, sampleType.getCurrentGenId(), quantity, Set.of("Name", "RowId", "AliquotedFrom", "AliquotedFrom/Name", "IsAliquot"));
    }

    protected List<Map<String, Object>> getRowsByRandomNames(TableInfo tableInfo, String namePrefix, long startIndex, long endIndex, int quantity, Set<String> columns)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(_container);
        filter.addCondition(FieldKey.fromParts("Name"),
                getRandomNames(namePrefix, startIndex, endIndex, quantity), CompareType.IN);
        TableSelector selector = new TableSelector(tableInfo, columns, filter, null);
        return Arrays.asList(selector.getMapArray());
    }


//    public List<Map<String, Object>> poolSamples(ExpSampleType sampleType, QueryUpdateService service, int numPooled) throws SQLException, BatchValidationException, QueryUpdateServiceException, InvalidKeyException
//    {
//        // TODO This can pool samples from different generations, which seems a little odd, but we'll go with it for now.
//        List<Map<String, Object>> rows = new ArrayList<>();
//        List<Map<String, Object>> oldKeys = new ArrayList<>();
//        // choose a random set of child samples to.
//        List<Integer> possibleParents = new ArrayList<>(samples.stream().map(sample -> (Integer) sample.get("RowId")).toList());
//        List<Integer> possibleChildren = new ArrayList<>(getRandomSamples(sampleType, numPooled).stream().map(sample -> (Integer) sample.get("RowId")).toList());
//        for (int i = 0;  i < possibleChildren.size() ; i++)
//        {
//            Map<String, Object> row = new CaseInsensitiveHashMap<>();
//            Map<String, Object> keys = new CaseInsensitiveHashMap<>();
//            // choose a random sample
//            int childIndex = randomInt(0, possibleChildren.size());
//            Integer childId = possibleChildren.get(childIndex);
//            keys.put("RowId", childId);
//            possibleChildren.remove(childIndex);
//            // choose a random pool size
//            int poolSize = randomInt(0, _config.getMaxPoolSize());
//            List<Integer> parentIds = new ArrayList<>();
//            int childAsParentIndex = possibleParents.indexOf(childId);
//            while (parentIds.size() < poolSize && possibleParents.size() > 0)
//            {
//                int parentIndex = randomInt(0, possibleParents.size());
//                if (parentIndex != childAsParentIndex)
//                {
//                    parentIds.add(possibleParents.get(parentIndex));
//                    possibleParents.remove(parentIndex);
//                }
//            }
//            if (parentIds.size() > 2)
//            {
//                row.put("MaterialInputs/" + sampleType.getName(), parentIds);
//                rows.add(row);
//                oldKeys.add(keys);
//            }
//            else
//            {
//                _log.info(String.format("Generated %d pooled sample and then ran out of parents.", i+1));
//            }
//        }
//        return service.updateRows(_user, _container, rows, oldKeys, null, null);
//    }


    private List<String> getRandomNames(String namePrefix, long startIndex, long endIndex, int quantity)
    {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < quantity; i++)
            names.add(namePrefix + randomLong(startIndex, endIndex));
        return names;
    }


   public ExpDataClass generateDataClass(String dataClassName, @Nullable String namingPattern, int numFields, Logger log, @Nullable String category) throws ExperimentException
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        addDomainProperties(props, numFields);

        ExperimentService service = ExperimentService.get();

        log.info(String.format("Creating Data Class '%s' with %d fields", dataClassName, numFields));
        return service.createDataClass(_container, _user, dataClassName, "Custom data class with " + numFields + " fields",
                    props, List.of(), null,
                    namingPattern, null, category);
    }


    public void generateDataClassObjects(ExpDataClass dataClass, int numObjects) throws SQLException, BatchValidationException, QueryUpdateServiceException, DuplicateKeyException
    {
        QueryUpdateService svc = _dataClassSchema.getTable(dataClass.getName()).getUpdateService();
        generateDomainData(numObjects, svc, dataClass.getDomain());
    }


    public void generateDerivedSamples(SchemaKey parentSchemaKey, String parentQueryName)
    {
        // TODO
        // Choose random set of
    }

    public void logTimes()
    {
        _log.info("===== Timing Summary ======");
        _timers.forEach((timer) -> {
            _log.info(String.format("%s\t%s", timer.getName(), timer.getDuration()));
        });
    }

    private void generateDomainData(int totalRows, QueryUpdateService service, Domain domain) throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        int numImported = 0;
        int batchSize = Math.min(10000, totalRows);
        while (numImported < totalRows)
        {
            List<Map<String, Object>> rows = createRows(Math.min(batchSize, totalRows - numImported), domain);
            BatchValidationException errors = new BatchValidationException();
            ListofMapsDataIterator rowsDI = new ListofMapsDataIterator(rows.get(0).keySet(), rows);
            numImported += service.importRows(_user, _container, rowsDI, errors, null, null);
            if (errors.hasErrors())
                throw errors;
        }
    }

    private void addDomainProperties(List<GWTPropertyDescriptor> props, int numFields)
    {
        for (int i = 0; i < numFields; i++)
        {
            int suffix = i / fieldPrefixes.size() + 1;
            var fieldPrefix = fieldPrefixes.get(i % fieldPrefixes.size());
            props.add(new GWTPropertyDescriptor(fieldPrefix.namePrefix() + "_" + suffix, fieldPrefix.uri()));
        }
    }

    private List<Map<String, Object>> createRows(int numRows, Domain domain)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= numRows; i++)
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            List<? extends DomainProperty> properties = domain.getProperties();
            for (int p = 0; p < properties.size(); p++)
            {
                DomainProperty property = properties.get(p);
                int dataNum = p + (i % 15);
                Object value = switch (property.getRangeURI())
                        {
                            case "string" -> "Text " + dataNum;
                            case "int" -> dataNum;
                            case "float" -> dataNum * 1.5;
                            case "date" -> randomDate();
                            default -> null;
                        };
                row.put(property.getName(), value);
            }
            rows.add(row);
        }
        return rows;
    }

    public static String randomDate()
    {
        var startDate = new Date(112 /* 2012 */, Calendar.JANUARY, 1);
        var endDate = new Date();

        var random = new Date(ThreadLocalRandom.current().nextLong(startDate.getTime(), endDate.getTime()));
        return new SimpleDateFormat("dd-MMM-yy").format(random);
    }

    public static String randomDouble(int min, int max)
    {
        double random = Math.random() < 0.5 ? ((1-Math.random()) * (max-min) + min) : (Math.random() * (max-min) + min);
        return String.format("%.2f", random);
    }

    public static <T> T randomIndex(T[] array)
    {
        return array[randomInt(0, array.length)];
    }

    public static int randomInt(int min, int max)
    {
        // The maximum is exclusive and the minimum is inclusive
        return (int) Math.round(Math.floor(Math.random() * (max - min) + min));
    }

    public static long randomLong(long startIndex, long endIndex)
    {
        return Math.round(Math.floor(Math.random() * (endIndex - startIndex) + startIndex));
    }

    public static class Config
    {
        public static final String NUM_SAMPLE_TYPES = "numSampleTypes";
        public static final String PCT_ALIQUOTS = "percentAliquots";
        public static final String PCT_DERIVED = "percentDerived";
        public static final String PCT_POOLED = "percentPooled";
        public static final String MAX_POOL_SIZE = "maxPoolSize";
        public static final String MIN_SAMPLES = "minSamples";
        public static final String MAX_SAMPLES = "maxSamples";
        private static final String MAX_ALIQUOTS_PER_SAMPLE = "maxAliquotsPerSample";
        private static final String MAX_GENERATIONS = "maxGenerations";
        public static final String NUM_CUSTOM_DATA_CLASSES = "numCustomDataClasses";
        public static final String MIN_NUM_FIELDS = "minFields";
        public static final String MAX_NUM_FIELDS = "maxFields";

        int _numSampleTypes = 0;
        int _minSamples = 0;
        int _maxSamples = 0;
        float _pctAliquots = 0;
        float _pctPooled = 0;
        float _pctDerived = 0;
        int _maxPoolSize = 2;
        int _maxGenerations = 1;
        int _maxAliquotsPerParent = 0;


        int _minFields = 1;
        int _maxFields = 1;


        public Config(Map<String, String> parameters)
        {
            _numSampleTypes = Integer.parseInt(parameters.getOrDefault(NUM_SAMPLE_TYPES, "0"));
            _minSamples = Integer.parseInt(parameters.getOrDefault(MIN_SAMPLES, "0"));
            _maxSamples = Math.max(Integer.parseInt(parameters.getOrDefault(DataGenerator.Config.MAX_SAMPLES, "0")), _minSamples);
            _pctAliquots = Float.parseFloat(parameters.getOrDefault(PCT_ALIQUOTS, "0.0"));
            _pctDerived = Float.parseFloat(parameters.getOrDefault(PCT_DERIVED, "0.0"));
            _pctPooled = Float.parseFloat(parameters.getOrDefault(PCT_POOLED, "0.0"));
            _maxPoolSize = Integer.parseInt(parameters.getOrDefault(MAX_POOL_SIZE, "2"));
            _maxGenerations = Integer.parseInt(parameters.getOrDefault(MAX_GENERATIONS, "1"));
            _maxAliquotsPerParent = Integer.parseInt(parameters.getOrDefault(MAX_ALIQUOTS_PER_SAMPLE, "0"));

            _minFields = Integer.parseInt(parameters.getOrDefault(MIN_NUM_FIELDS, "1"));
            _maxFields = Math.max(Integer.parseInt(parameters.getOrDefault(MAX_NUM_FIELDS, "1")), _minFields);
        }

        public Config(Properties properties)
        {
            _numSampleTypes = Integer.parseInt(properties.getProperty(NUM_SAMPLE_TYPES, "0"));
            _minSamples = Integer.parseInt(properties.getProperty(MIN_SAMPLES, "0"));
            _maxSamples = Math.max(Integer.parseInt(properties.getProperty(DataGenerator.Config.MAX_SAMPLES, "0")), _minSamples);
            _pctAliquots = Float.parseFloat(properties.getProperty(PCT_ALIQUOTS, "0.0"));
            _pctDerived = Float.parseFloat(properties.getProperty(PCT_DERIVED, "0.0"));
            _pctPooled = Float.parseFloat(properties.getProperty(PCT_POOLED, "0.0"));
            _maxPoolSize = Integer.parseInt(properties.getProperty(MAX_POOL_SIZE, "2"));
            _maxGenerations = Integer.parseInt(properties.getProperty(MAX_GENERATIONS, "1"));
            _maxAliquotsPerParent = Integer.parseInt(properties.getProperty(MAX_ALIQUOTS_PER_SAMPLE, "0"));

            _minFields = Integer.parseInt(properties.getProperty(MIN_NUM_FIELDS, "1"));
            _maxFields = Math.max(Integer.parseInt(properties.getProperty(MAX_NUM_FIELDS, "1")), _minFields);

        }

        public int getNumSampleTypes()
        {
            return _numSampleTypes;
        }

        public void setNumSampleTypes(int numSampleTypes)
        {
            _numSampleTypes = numSampleTypes;
        }

        public int getMinSamples()
        {
            return _minSamples;
        }

        public void setMinSamples(int minSamples)
        {
            _minSamples = minSamples;
        }

        public int getMaxSamples()
        {
            return _maxSamples;
        }

        public void setMaxSamples(int maxSamples)
        {
            _maxSamples = maxSamples;
        }

        public float getPctAliquots()
        {
            return _pctAliquots;
        }

        public void setPctAliquots(float pctAliquots)
        {
            _pctAliquots = pctAliquots;
        }

        public float getPctPooled()
        {
            return _pctPooled;
        }

        public void setPctPooled(float pctPooled)
        {
            _pctPooled = pctPooled;
        }

        public float getPctDerived()
        {
            return _pctDerived;
        }

        public void setPctDerived(float pctDerived)
        {
            _pctDerived = pctDerived;
        }

        public int getMaxPoolSize()
        {
            return _maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize)
        {
            _maxPoolSize = maxPoolSize;
        }

        public int getMaxGenerations()
        {
            return _maxGenerations;
        }

        public void setMaxGenerations(int maxGenerations)
        {
            _maxGenerations = maxGenerations;
        }

        public int getMaxAliquotsPerParent()
        {
            return _maxAliquotsPerParent;
        }

        public void setMaxAliquotsPerParent(int maxAliquotsPerParent)
        {
            _maxAliquotsPerParent = maxAliquotsPerParent;
        }

        public int getMinFields()
        {
            return _minFields;
        }

        public void setMinFields(int minFields)
        {
            _minFields = minFields;
        }

        public int getMaxFields()
        {
            return _maxFields;
        }

        public void setMaxFields(int maxFields)
        {
            _maxFields = maxFields;
        }
    }

}
