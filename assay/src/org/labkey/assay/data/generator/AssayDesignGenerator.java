package org.labkey.assay.data.generator;

import org.labkey.api.assay.AssayDomainService;
import org.labkey.api.data.generator.DataGenerator;
import org.labkey.api.exp.query.ExpSchema;
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
        }
        timer.stop();
        _log.info(String.format("Generating %d assay designs took %s", numAssayDesigns, timer.getDuration() + "."));
    }

    private void createStandardAssayDesign(String name) throws ValidationException, AssayException
    {
        // create assay design
        AssayDomainService assayDomainService = new AssayDomainServiceImpl(new ViewContext(new ViewBackgroundInfo(getContainer(), getUser(), null)));

        GWTProtocol assayTemplate = assayDomainService.getAssayTemplate("General");
        assayTemplate.setName(name);
        List<GWTDomain<GWTPropertyDescriptor>> domains = assayTemplate.getDomains();

        // clear the batch domain fields
        GWTDomain<GWTPropertyDescriptor> batchDomain = domains.stream().filter(d -> "Batch Fields".equals(d.getName())).findFirst().orElseThrow();
        batchDomain.getFields().clear();

        // clear the run domain fields
        GWTDomain<GWTPropertyDescriptor> runDomain = domains.stream().filter(d -> "Run Fields".equals(d.getName())).findFirst().orElseThrow();
        runDomain.getFields().clear();

        // clear the result domain fields and add a sample lookup
        GWTDomain<GWTPropertyDescriptor> resultDomain = domains.stream().filter(d -> "Data Fields".equals(d.getName())).findFirst().orElseThrow();
        resultDomain.getFields().clear();
        GWTPropertyDescriptor sampleLookup = new GWTPropertyDescriptor("SampleLookup", "int");
        sampleLookup.setLookupSchema(ExpSchema.SCHEMA_NAME);
        sampleLookup.setLookupQuery(ExpSchema.TableType.Materials.name());
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(sampleLookup);
        addDomainProperties(props, randomInt(_config.getMinFields(), _config.getMaxFields()));
        resultDomain.getFields().addAll(props);

        // create the assay
        assayDomainService.saveChanges(assayTemplate, true);
    }

    public static class Config extends DataGenerator.Config
    {
        public static final String NUM_ASSAY_DESIGNS = "numAssayDesigns";
        int _numAssayDesigns = 0;

        public Config(Properties properties)
        {
            super(properties);
            _numAssayDesigns = Integer.parseInt(properties.getProperty(NUM_ASSAY_DESIGNS, "0"));
        }


        public int getNumAssayDesigns()
        {
            return _numAssayDesigns;
        }

        public void setNumAssayDesigns(int numAssayDesigns)
        {
            _numAssayDesigns = numAssayDesigns;
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
