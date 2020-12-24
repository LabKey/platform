package org.labkey.assay;

import com.google.common.base.Charsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.assay.AssayDataCollector;
import org.labkey.api.assay.AssayDomainService;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunCreator;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.files.FileContentService;
import org.labkey.api.files.FilesAdminOptions;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.security.User;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class AssayIntegrationTestCase
{
    public static final Logger LOG = LogManager.getLogger(AssayIntegrationTestCase.class);

    Container c;

    @Before
    public void setUp()
    {
        JunitUtil.deleteTestContainer();
        c = JunitUtil.getTestContainer();
    }

    // Issue 41675: ERROR: insert or update on table "edge" violates foreign key constraint "fk_edge_to_object"
    // - imports a file into an assay
    // - sets some file properties
    // - deletes the assay run
    // - verifies the exp.data is detatched from the run, but the properties haven't been deleted
    // - re-imports the same file again
    @Test
    public void testIssue41675() throws Exception
    {
        final User user = TestContext.get().getUser();
        final String ASSAY_NAME = "MyAssay";
        final var info = new ViewBackgroundInfo(c, user, null);
        final var context = new ViewContext(info);
        final var pipeRoot = PipelineService.get().findPipelineRoot(info.getContainer());


        // create a custom file property
        FileContentService fileSvc = FileContentService.get();
        PropertyDescriptor someStuffProp = null;
        if (fileSvc != null)
        {
            String domainUri = fileSvc.getDomainURI(c, FilesAdminOptions.fileConfig.useCustom);
            Domain domain = PropertyService.get().createDomain(c, domainUri, "FileProps");
            domain.addProperty(new PropertyStorageSpec("SomeStuff", JdbcType.VARCHAR));
            domain.save(user);

            DomainProperty dp = domain.getPropertyByName("SomeStuff");
            someStuffProp = dp.getPropertyDescriptor();
        }

        // create assay design
        AssayDomainService assayDomainService = new AssayDomainServiceImpl(context);
        GWTProtocol assayTemplate = assayDomainService.getAssayTemplate("General");
        assayTemplate.setName(ASSAY_NAME);
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
        resultDomain.getFields().add(sampleLookup);

        // create the assay
        GWTProtocol savedAssayDesign = assayDomainService.saveChanges(assayTemplate, true);
        ExpProtocol assayProtocol = ExperimentService.get().getExpProtocol(savedAssayDesign.getProtocolId());
        AssayProvider provider = AssayService.get().getProvider(assayProtocol);

        // create a sample that will be used as an input to the run
        final String materialName = "TestMaterial";
        final String materialLsid = ExperimentService.get().generateLSID(c, ExpMaterial.class, "TestMaterial");
        ExpMaterial material = ExperimentService.get().createExpMaterial(c, materialLsid, materialName);
        material.save(user);

        // create a file in the pipeline root to import
        var file = File.createTempFile(getClass().getSimpleName(), ".tsv", pipeRoot.getRootPath());
        Files.writeString(file.toPath(), "SampleLookup\n" + materialName + "\n", Charsets.UTF_8);

        // create an upload context that imports the file
        AssayRunUploadContext uploadContext = provider.createRunUploadFactory(assayProtocol, user, c)
                .setName("New Run")
                .setUploadedData(Map.of(AssayDataCollector.PRIMARY_FILE, file))
                .create();

        // create a new run
        AssayRunCreator runCreator = provider.getRunCreator();
        Pair<ExpExperiment, ExpRun> pair = runCreator.saveExperimentRun(uploadContext, null);
        ExpRun run = pair.second;

        // verify the exp.data is attached to the run
        assertEquals(1, run.getDataOutputs().size());
        final ExpData originalOutputData = run.getDataOutputs().get(0);
        assertEquals(file.getName(), originalOutputData.getName());

        final int dataRowId = originalOutputData.getRowId();
        final String dataLsid = originalOutputData.getLSID();
        final Integer dataOID = originalOutputData.getObjectId();
        final OntologyObject oo1 = OntologyManager.getOntologyObject(originalOutputData.getObjectId());
        assertNotNull(oo1);

        // set some properties that will be verified later
        originalOutputData.setComment(user, "hello world");
        if (someStuffProp != null)
            originalOutputData.setProperty(user, someStuffProp, "SomeData");

        // verify lineage
        var parents = ExperimentService.get().getParents(c, user, originalOutputData);
        assertThat(parents.second, CoreMatchers.hasItem(material));

        // delete the run
        run.delete(user);

        // verify the exp.data, exp.object, and the properties were not deleted
        ExpData data2 = ExperimentService.get().getExpData(dataRowId);
        assertNotNull(data2);
        assertEquals(dataLsid, data2.getLSID());
        assertEquals(dataOID, data2.getObjectId());
        assertEquals("hello world", data2.getComment());
        if (someStuffProp != null)
            assertEquals("SomeData", data2.getProperty(someStuffProp));

        assertNull(data2.getRunId());
        parents = ExperimentService.get().getParents(c, user, data2);
        assertThat(parents.second, CoreMatchers.not(CoreMatchers.hasItem(material)));

        OntologyObject oo2 = OntologyManager.getOntologyObject(data2.getObjectId());
        assertNotNull(oo2);
        assertEquals(oo1.getObjectId(), oo2.getObjectId());
        assertEquals(oo1.getObjectURI(), oo2.getObjectURI());
        assertEquals(oo1.getContainer(), oo2.getContainer());
        assertEquals(oo1.getOwnerObjectId(), oo2.getOwnerObjectId());

        // import the same file again
        uploadContext = provider.createRunUploadFactory(assayProtocol, user, c)
                .setName("New Run2")
                .setUploadedData(Map.of(AssayDataCollector.PRIMARY_FILE, file))
                .create();

        // create a new run
        runCreator = provider.getRunCreator();
        pair = runCreator.saveExperimentRun(uploadContext, null);
        ExpRun run2 = pair.second;

        // verify the exp.data and exp.object again
        ExpData data3 = ExperimentService.get().getExpData(dataRowId);
        assertNotNull(data3);
        assertEquals(dataLsid, data3.getLSID());
        assertEquals(dataOID, data3.getObjectId());
        assertEquals("hello world", data3.getComment());
        if (someStuffProp != null)
            assertEquals("SomeData", data3.getProperty(someStuffProp));

        assertEquals(run2.getRowId(), data3.getRunId().intValue());
        parents = ExperimentService.get().getParents(c, user, data3);
        assertThat(parents.second, CoreMatchers.hasItem(material));

    }
}
