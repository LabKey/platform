package org.labkey.assay.data.generator;

import org.labkey.api.assay.AssayDomainService;
import org.labkey.api.data.generator.DataGenerator;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.assay.AssayException;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.CPUTimer;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.assay.AssayDomainServiceImpl;
import org.labkey.assay.AssayManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.labkey.api.gwt.client.ui.PropertyType.SAMPLE_CONCEPT_URI;

public class AssayDesignGenerator extends DataGenerator<AssayDesignGenerator.Config>
{

    public AssayDesignGenerator(PipelineJob job, Config config)
    {
        super(job, config);
    }

    public void generateAssayDesigns(String namePrefix) throws ValidationException, AssayException
    {
        int numAssayDesigns = _config.getNumAssayDesigns();
        if (numAssayDesigns <= 0)
        {
            _log.info(String.format("No assay designs generated because %s=%d", Config.NUM_ASSAY_DESIGNS, numAssayDesigns));
            return;
        }
        checkAlive(_job);
        CPUTimer timer = addTimer(String.format("%d assay designs", numAssayDesigns));
        timer.start();
        int numGenerated = 0;
        int index = 1;
        while (numGenerated < numAssayDesigns)
        {
            String name = String.format("%s%d", namePrefix, index);
            if (AssayManager.get().getAssayProtocolByName(getContainer(),name) == null)
            {
                createStandardAssayDesign(name);
                numGenerated++;
            }
            index++;
            checkAlive(_job);
        }
        timer.stop();
        _log.info(String.format("Generating %d assay designs took %s", numAssayDesigns, timer.getDuration() + "."));
    }

    private void createStandardAssayDesign(String name) throws ValidationException
    {
        // create assay design
        AssayDomainService assayDomainService = new AssayDomainServiceImpl(new ViewContext(new ViewBackgroundInfo(getContainer(), getUser(), null)));

        GWTProtocol assayTemplate = assayDomainService.getAssayTemplate("General");
        assayTemplate.setName(name);

        if (getConfig().isAssayDesignPlateSupport())
            assayTemplate.setPlateMetadata(true);

        List<GWTDomain<GWTPropertyDescriptor>> domains = assayTemplate.getDomains();

        // clear the batch domain fields
        GWTDomain<GWTPropertyDescriptor> batchDomain = domains.stream().filter(d -> "Batch Fields".equals(d.getName())).findFirst().orElseThrow();
        batchDomain.getAllFields().clear();

        // clear the run domain fields
        GWTDomain<GWTPropertyDescriptor> runDomain = domains.stream().filter(d -> "Run Fields".equals(d.getName())).findFirst().orElseThrow();
        runDomain.getAllFields().clear();

        // clear the result domain fields and add a sample lookup
        GWTDomain<GWTPropertyDescriptor> resultDomain = domains.stream().filter(d -> "Data Fields".equals(d.getName())).findFirst().orElseThrow();
        resultDomain.getAllFields().clear();
        GWTPropertyDescriptor sampleLookup = new GWTPropertyDescriptor("SampleID", "int");
        sampleLookup.setConceptURI(SAMPLE_CONCEPT_URI);
        sampleLookup.setLabel("Sample ID");
        if (_config.getAssayDesignSampleTypes().isEmpty())
        {
            sampleLookup.setLookupSchema(ExpSchema.SCHEMA_NAME);
            sampleLookup.setLookupQuery(ExpSchema.TableType.Materials.name());
            assayTemplate.setDescription("Assay design for All Samples");
        }
        else
        {
            sampleLookup.setLookupSchema(SamplesSchema.SCHEMA_NAME);
            sampleLookup.setLookupQuery(randomIndex(_config.getAssayDesignSampleTypes()));
            assayTemplate.setDescription("Assay design for " + sampleLookup.getLookupQuery() + " samples");
        }
        assayTemplate.setName(name);

        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(sampleLookup);
        addDomainProperties(props, randomInt(_config.getMinFields(), _config.getMaxFields()));
        resultDomain.getAllFields().addAll(props);

        // create the assay
        assayDomainService.saveChanges(assayTemplate, true);
    }

    public static class Config extends DataGenerator.Config
    {
        public static final String NUM_ASSAY_DESIGNS = "numAssayDesigns";
        public static final String ASSAY_DESIGN_SAMPLE_TYPES = "assayDesignSampleTypes";
        public static final String ASSAY_DESIGN_PLATE_SUPPORT = "assayDesignPlateSupport";

        int _numAssayDesigns = 0;
        List<String> _assayDesignSampleTypes;
        boolean _assayDesignPlateSupport;

        public Config(Properties properties)
        {
            super(properties);
            _numAssayDesigns = Integer.parseInt(properties.getProperty(NUM_ASSAY_DESIGNS, "0"));
            _assayDesignSampleTypes = parseNameList(properties, ASSAY_DESIGN_SAMPLE_TYPES);
            _assayDesignPlateSupport = Boolean.parseBoolean(properties.getProperty(ASSAY_DESIGN_PLATE_SUPPORT, "false"));
        }


        public int getNumAssayDesigns()
        {
            return _numAssayDesigns;
        }

        public void setNumAssayDesigns(int numAssayDesigns)
        {
            _numAssayDesigns = numAssayDesigns;
        }

        public List<String> getAssayDesignSampleTypes()
        {
            return _assayDesignSampleTypes;
        }

        public void setAssayDesignSampleTypes(List<String> assayDesignSampleTypes)
        {
            _assayDesignSampleTypes = assayDesignSampleTypes;
        }

        public boolean isAssayDesignPlateSupport()
        {
            return _assayDesignPlateSupport;
        }
    }

    public static class Driver implements DataGenerationDriver
    {
        @Override
        public List<CPUTimer> generateData(PipelineJob job, Properties properties) throws ValidationException, AssayException
        {
            AssayDesignGenerator generator = new AssayDesignGenerator(job, new AssayDesignGenerator.Config(properties));
            generator.generateAssayDesigns("Assay Design ");
            return generator.getTimers();
        }
    }
}
