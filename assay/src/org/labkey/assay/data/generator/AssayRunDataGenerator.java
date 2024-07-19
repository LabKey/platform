package org.labkey.assay.data.generator;

import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.DefaultAssayRunCreator;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.generator.DataGenerator;
import org.labkey.api.dataiterator.AbstractMapDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.assay.AssayException;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.labkey.api.gwt.client.ui.PropertyType.SAMPLE_CONCEPT_URI;

public class AssayRunDataGenerator extends DataGenerator<AssayRunDataGenerator.Config>
{

    public AssayRunDataGenerator(PipelineJob job, Config config)
    {
        super(job, config);
    }

    public void generateAssayRunData() throws ValidationException, ExperimentException
    {
        AssayProvider provider = AssayService.get().getProvider("General");
        if (provider == null)
            throw new ValidationException("No provider for 'General' assays.");
        List<String> designNames = _config.getAssayDesignNames();
        List<ExpProtocol>  protocols = AssayService.get().getAssayProtocols(getContainer(), provider);
        if (!designNames.isEmpty())
        {
            protocols = protocols.stream().filter(protocol -> designNames.contains(protocol.getName())).toList();
        }
        if (protocols.isEmpty())
        {
            _log.info("No assay run data generated because no protocols found matching parameters.");
            return;
        }
        if (_config.getMaxRunsPerDesign() <= 0)
        {
            _log.info(String.format("No assay run data generated because %s=%d.", Config.MAX_RUNS_PER_DESIGN, _config.getMaxRunsPerDesign()));
            return;
        }
        if (_config.getMaxRunsPerDesign() < _config.getMinRunsPerDesign())
        {
            _log.info(String.format("No assay run data generated because %s (%d) is less than %s (%d).", Config.MAX_RUNS_PER_DESIGN, _config.getMaxRunsPerDesign(), Config.MIN_RUNS_PER_DESIGN, _config.getMinRunsPerDesign()));
            return;
        }
        if (_config.getMaxRowsPerRun() <= 0)
        {
            _log.info(String.format("No assay run data generated because %s=%s.", Config.MAX_ROWS_PER_RUN, _config.getMaxRowsPerRun()));
            return;
        }
        if (_config.getMaxRowsPerRun() < _config.getMinRowsPerRun())
        {
            _log.info(String.format("No assay run data generated because %s (%d) is less than %s (%d).", Config.MAX_ROWS_PER_RUN, _config.getMaxRowsPerRun(), Config.MIN_ROWS_PER_RUN, _config.getMinRowsPerRun()));
            return;
        }
        ViewContext context = new ViewContext();
        context.setUser(getUser());
        context.setContainer(getContainer());

        for (ExpProtocol protocol : protocols)
        {
            int numRuns = randomInt(_config.getMinRunsPerDesign(), _config.getMaxRunsPerDesign());
            CPUTimer timer = addTimer(String.format("%d '%s' assay runs", numRuns, protocol.getName()));
            timer.start();
            Domain resultsDomain = provider.getResultsDomain(protocol);
            for (int i = 0; i < numRuns; i++)
            {
                checkAlive(_job);
                int numRows = randomInt(_config.getMinRowsPerRun(), _config.getMaxRowsPerRun());
                _log.info(String.format("Generating %d rows of run data for run %d of design %s", numRows, i, protocol.getName()));
                List<Map<String, Object>> rawData = createRows(numRows, resultsDomain);
                updateSampleProps(protocol.getName(), rawData, resultsDomain);
                var factory = provider.createRunUploadFactory(protocol, context)
                        .setName(protocol.getName() + " - Run " + (i+1) )
                        .setJobDescription("Run with " + rawData.size() + " results.")
                        .setRawData(AbstractMapDataIterator.builderOf(rawData))
                        .setUploadedData(Collections.emptyMap());
                Map<Object, String> outputData = new HashMap<>();
                // Create an ExpData for the results
                DefaultAssayRunCreator.generateResultData(getUser(), getContainer(), provider, rawData, outputData, null);
                factory.setOutputDatas(outputData);
                provider.getRunCreator().saveExperimentRun(factory.create(), null);
            }
            timer.stop();
        }
    }

    private void updateSampleProps(String protocolName, List<Map<String, Object>> rawData, Domain resultsDomain)
    {
        ContainerFilter currentCf = ContainerFilter.current(getContainer());
        List<DomainProperty> sampleProps = resultsDomain.getProperties().stream().filter(prop -> SAMPLE_CONCEPT_URI.equals(prop.getConceptURI())).collect(Collectors.toList());
        // Find the sample type for each sample field.
        for (DomainProperty sampleProp : sampleProps)
        {
            if (sampleProp.getLookup().getSchemaKey().equals(SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME)))
            {
                String sampleTypeName = sampleProp.getLookup().getQueryName();
                ExpSampleType sampleType = SampleTypeService.get().getSampleType(getContainer(), sampleTypeName);
                if (sampleType == null)
                {
                    _log.warn(String.format("Sample type '%s' referenced in assay design %s not found.", sampleTypeName, protocolName));
                    // remove the values set by the default data generator for the row since they won't be valid sample ids.
                    rawData.forEach(row -> row.put(sampleProp.getName(), null));
                }
                else
                {
                    List<Integer> sampleIds = new ArrayList<>();
                    while (sampleIds.size() < rawData.size())
                    {
                        _sampleTypeCounts.computeIfAbsent(sampleType.getRowId(), t -> sampleType.getSamplesCount(getContainer(), null));
                        sampleIds.addAll(selectExistingSamplesIds(sampleType, Math.min(5, rawData.size() - sampleIds.size()), _sampleTypeCounts.get(sampleType.getRowId()), currentCf));
                    }
                    for (int i = 0; i < rawData.size(); i++)
                        rawData.get(i).put(sampleProp.getName(), sampleIds.get(i));
                }
            }
            else
            {
                List<Integer> sampleIds = new ArrayList<>();
                long totalSampleCount = SampleTypeService.get().getProjectSampleCount(getContainer());
                while (sampleIds.size() < rawData.size())
                    sampleIds.addAll(selectExistingSampleIds(Math.min(5, rawData.size() - sampleIds.size()), totalSampleCount, currentCf));
                for (int i = 0; i < rawData.size(); i++)
                    rawData.get(i).put(sampleProp.getName(), sampleIds.get(i));
            }
        }
    }

    public static class Config extends DataGenerator.Config
    {
        public static final String MIN_RUNS_PER_DESIGN = "minRunsPerDesign";
        public static final String MAX_RUNS_PER_DESIGN = "maxRunsPerDesign";
        public static final String MIN_ROWS_PER_RUN = "minRowsPerRun";
        public static final String MAX_ROWS_PER_RUN = "maxRowsPerRun";
        public static final String ASSAY_DESIGNS = "assayDesignNames";
        List<String> _assayDesignNames;
        int _minRunsPerDesign;
        int _maxRunsPerDesign;
        int _minRowsPerRun;
        int _maxRowsPerRun;


        public Config(Properties properties)
        {
            super(properties);
            _minRunsPerDesign = Integer.parseInt(properties.getProperty(MIN_RUNS_PER_DESIGN, "0"));
            _maxRunsPerDesign = Integer.parseInt(properties.getProperty(MAX_RUNS_PER_DESIGN, "0"));
            _assayDesignNames = parseNameList(properties, ASSAY_DESIGNS);
            _minRowsPerRun = Integer.parseInt(properties.getProperty(MIN_ROWS_PER_RUN, "0"));
            _maxRowsPerRun = Integer.parseInt(properties.getProperty(MAX_ROWS_PER_RUN, "0"));
        }

        public List<String> getAssayDesignNames()
        {
            return _assayDesignNames;
        }

        public void setAssayDesignNames(List<String> assayDesignNames)
        {
            _assayDesignNames = assayDesignNames;
        }

        public int getMinRunsPerDesign()
        {
            return _minRunsPerDesign;
        }

        public void setMinRunsPerDesign(int minRunsPerDesign)
        {
            _minRunsPerDesign = minRunsPerDesign;
        }

        public int getMaxRunsPerDesign()
        {
            return _maxRunsPerDesign;
        }

        public void setMaxRunsPerDesign(int maxRunsPerDesign)
        {
            _maxRunsPerDesign = maxRunsPerDesign;
        }

        public int getMinRowsPerRun()
        {
            return _minRowsPerRun;
        }

        public void setMinRowsPerRun(int minRowsPerRun)
        {
            _minRowsPerRun = minRowsPerRun;
        }

        public int getMaxRowsPerRun()
        {
            return _maxRowsPerRun;
        }

        public void setMaxRowsPerRun(int maxRowsPerRun)
        {
            _maxRowsPerRun = maxRowsPerRun;
        }
    }

    public static class Driver implements DataGenerationDriver
    {
        @Override
        public List<CPUTimer> generateData(PipelineJob job, Properties properties) throws ValidationException, AssayException, ExperimentException
        {
            AssayRunDataGenerator generator = new AssayRunDataGenerator(job, new AssayRunDataGenerator.Config(properties));
            generator.generateAssayRunData();
            return generator.getTimers();
        }
    }
}
