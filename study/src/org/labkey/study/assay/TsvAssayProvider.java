/*
 * Copyright (c) 2007-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.qc.TsvDataExchangeHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.AbstractTsvAssayProvider;
import org.labkey.api.study.assay.AssayDataCollector;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.study.assay.AssayPipelineProvider;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssaySaveHandler;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.FileUploadDataCollector;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.PipelineDataCollector;
import org.labkey.api.study.assay.PreviouslyUploadedDataCollector;
import org.labkey.api.study.assay.StudyParticipantVisitResolverType;
import org.labkey.api.study.assay.TextAreaDataCollector;
import org.labkey.api.study.assay.ThawListResolverType;
import org.labkey.api.study.assay.TsvDataHandler;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.study.StudyModule;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 9:59:39 AM
 */
public class TsvAssayProvider extends AbstractTsvAssayProvider
{
    private static final Set<String> participantImportAliases;
    private static final Set<String> specimenImportAliases;
    private static final Set<String> visitImportAliases;
    private static final Set<String> dateImportAliases;

    static
    {
        // this is the static lists of import aliases used in the default template
        participantImportAliases = PageFlowUtil.set("ptid", "participantId");
        specimenImportAliases = PageFlowUtil.set("specId", "vialId", "vialId1", "vial1_id", "guspec");
        visitImportAliases = PageFlowUtil.set("visitNo", "visit_no");
        dateImportAliases = PageFlowUtil.set("drawDt", "draw_date", "drawDate");
    }

    public TsvAssayProvider()
    {
        this("GeneralAssayProtocol", "GeneralAssayRun", ModuleLoader.getInstance().getModule(StudyModule.class));
    }

    protected TsvAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, Module declaringModule)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, (AssayDataType) ExperimentService.get().getDataType(TsvDataHandler.NAMESPACE), declaringModule);
    }

    public List<AssayDataCollector> getDataCollectors(@Nullable Map<String, File> uploadedFiles, AssayRunUploadForm context)
    {
        List<AssayDataCollector> result = super.getDataCollectors(uploadedFiles, context);
        if (PipelineDataCollector.getFileQueue(context).isEmpty())
        {
            result.add(0, new TextAreaDataCollector());
        }
        return result;
    }

    public String getName()
    {
        return "General";
    }

    @Override
    public AssaySaveHandler getSaveHandler()
    {
        AssaySaveHandler saveHandler = new TsvAssaySaveHandler();
        saveHandler.setProvider(this);
        return saveHandler;
    }

    @Override @NotNull
    public AssayTableMetadata getTableMetadata(@NotNull ExpProtocol protocol)
    {
        return new AssayTableMetadata(
                this,
                protocol,
                null,
                FieldKey.fromParts("Run"),
                FieldKey.fromParts(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME));
    }

    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = super.createDefaultDomains(c, user);

        Pair<Domain, Map<DomainProperty, Object>> resultDomain = createResultDomain(c, user);
        if (resultDomain != null)
            result.add(resultDomain);
        return result;
    }

    protected Pair<Domain,Map<DomainProperty,Object>> createResultDomain(Container c, User user)
    {
        Domain dataDomain = PropertyService.get().createDomain(c, "urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + ExpProtocol.ASSAY_DOMAIN_DATA + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":" + ASSAY_NAME_SUBSTITUTION, "Data Fields");
        dataDomain.setDescription("The user is prompted to enter data values for row of data associated with a run, typically done as uploading a file.  This is part of the second step of the upload process.");
        DomainProperty specimenID = addProperty(dataDomain, SPECIMENID_PROPERTY_NAME,  SPECIMENID_PROPERTY_CAPTION, PropertyType.STRING, "When a matching specimen exists in a study, can be used to identify subject and timepoint for assay. Alternately, supply " + PARTICIPANTID_PROPERTY_NAME + " and either " + VISITID_PROPERTY_NAME + " or " + DATE_PROPERTY_NAME + ".");
        specimenID.setImportAliasSet(specimenImportAliases);

        DomainProperty participantID = addProperty(dataDomain, PARTICIPANTID_PROPERTY_NAME, PARTICIPANTID_PROPERTY_CAPTION, PropertyType.STRING, "Used with either " + VISITID_PROPERTY_NAME + " or " + DATE_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        participantID.setConceptURI(org.labkey.api.gwt.client.ui.PropertyType.PARTICIPANT_CONCEPT_URI);
        participantID.setImportAliasSet(participantImportAliases);

        DomainProperty visitID = addProperty(dataDomain, VISITID_PROPERTY_NAME,  VISITID_PROPERTY_CAPTION, PropertyType.DOUBLE, "Used with " + PARTICIPANTID_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        visitID.setImportAliasSet(visitImportAliases);

        DomainProperty dateProperty = addProperty(dataDomain, DATE_PROPERTY_NAME,  DATE_PROPERTY_CAPTION, PropertyType.DATE_TIME, "Used with " + PARTICIPANTID_PROPERTY_NAME + " to identify subject and timepoint for assay.");
        dateProperty.setImportAliasSet(dateImportAliases);

        return new Pair<>(dataDomain, Collections.emptyMap());
    }

    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new JspView<>("/org/labkey/study/assay/view/tsvDataDescription.jsp", form);
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Arrays.asList(new StudyParticipantVisitResolverType(), new ThawListResolverType());
    }

    @Override
    public AssayProtocolSchema createProtocolSchema(User user, Container container, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        return new TSVProtocolSchema(user, container, this, protocol, targetStudy);
    }



    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        // intentionally do NOT require any columns exist for a TSV-based assay:
        return Collections.emptyMap();
    }

    @Override
    public boolean hasUsefulDetailsPage()
    {
        return false;
    }

    public String getDescription()
    {
        return "Imports data from simple Excel or TSV files.";
    }

    @Override
    public DataExchangeHandler createDataExchangeHandler()
    {
        return new TsvDataExchangeHandler();
    }

    public PipelineProvider getPipelineProvider()
    {
        return new AssayPipelineProvider(StudyModule.class,
                new PipelineProvider.FileTypesEntryFilter(getDataType().getFileType()), 
                this, "Import Text or Excel Assay");
    }

    @Override
    public Class<? extends Controller> getDataImportAction()
    {
        return TsvImportAction.class;
    }

    @Override
    public boolean supportsEditableResults()
    {
        return true;
    }

    @Override
    public boolean supportsBackgroundUpload()
    {
        return true;
    }

    @Override
    public ReRunSupport getReRunSupport()
    {
        return ReRunSupport.ReRunAndReplace;
    }

    @Override
    public boolean supportsFlagColumnType(ExpProtocol.AssayDomainTypes type)
    {
        return type== ExpProtocol.AssayDomainTypes.Result;
    }

    public static class TestCase extends Assert
    {
        private Mockery _context;

        private Container _container = ContainerManager.createMockContainer();
        private HttpServletRequest _request;
        private HttpSession _session;
        private ExpProtocol _protocol;
        private ExpRun _run;
        private ExpData _data;
        private AssayRunUploadForm _uploadContext;

        @Before
        public void setUp()
        {
            _context = new Mockery();
            _context.setImposteriser(ClassImposteriser.INSTANCE);
            _request = _context.mock(HttpServletRequest.class);
            _session = _context.mock(HttpSession.class);
            _protocol = _context.mock(ExpProtocol.class);
            _run = _context.mock(ExpRun.class);
            _data = _context.mock(ExpData.class);
            _uploadContext = _context.mock(AssayRunUploadForm.class);

            _context.checking(new Expectations(){{
                allowing(_uploadContext).getRequest();
                will(returnValue(_request));
                allowing(_uploadContext).getContainer();
                will(returnValue(_container));
                allowing(_uploadContext).getProtocol();
                will(returnValue(_protocol));
                allowing(_protocol);
                allowing(_request).getSession(true);
                will(returnValue(_session));
            }});
        }

        @Test
        public void testReRunDataCollectorList()
        {
            // Pretend that the user is rerunning an existing run, and should be offered the option of uploading new
            // data or reusing the existing file
            _context.checking(new Expectations(){{
                allowing(_session).getAttribute(PipelineDataCollector.class.getName());
                will(returnValue(new HashMap()));
                allowing(_uploadContext).getReRun();
                will(returnValue(_run));
                allowing(_run).getInputDatas(ExpDataRunInput.DEFAULT_ROLE, ExpProtocol.ApplicationType.ExperimentRunOutput);
                will(returnValue(Collections.singletonList(_data)));
                allowing(_data).getFile();
                will(returnValue(PipelineService.get().getPipelineRootSetting(_uploadContext.getContainer()).resolvePath("mockFile")));
            }});

            TsvAssayProvider provider = new TsvAssayProvider();
            List<AssayDataCollector> dataCollectors = provider.getDataCollectors(null, _uploadContext);
            assertEquals(3, dataCollectors.size());
            assertEquals(TextAreaDataCollector.class, dataCollectors.get(0).getClass());
            assertEquals(PreviouslyUploadedDataCollector.class, dataCollectors.get(1).getClass());
            assertEquals(FileUploadDataCollector.class, dataCollectors.get(2).getClass());
        }

        @Test
        public void testReRunDataNotUnderRootCollectorList()
        {
            // Pretend that the user is rerunning an existing run, and should be offered the option of uploading new
            // data or reusing the existing file
            _context.checking(new Expectations(){{
                allowing(_session).getAttribute(PipelineDataCollector.class.getName());
                will(returnValue(new HashMap()));
                allowing(_uploadContext).getReRun();
                will(returnValue(_run));
                allowing(_run).getInputDatas(ExpDataRunInput.DEFAULT_ROLE, ExpProtocol.ApplicationType.ExperimentRunOutput);
                will(returnValue(Collections.singletonList(_data)));
                allowing(_data).getFile();
                // Use a file that's not under the pipeline root for the folder
                will(returnValue(new File("mockFile")));
            }});

            TsvAssayProvider provider = new TsvAssayProvider();
            List<AssayDataCollector> dataCollectors = provider.getDataCollectors(null, _uploadContext);
            // Make sure that we're not offered the option of reusing the file
            assertEquals(2, dataCollectors.size());
            assertEquals(TextAreaDataCollector.class, dataCollectors.get(0).getClass());
            assertEquals(FileUploadDataCollector.class, dataCollectors.get(1).getClass());
        }

        @Test
        public void testDataCollectorList()
        {
            // Simulate a regular upload wizard
            _context.checking(new Expectations(){{
                allowing(_session).getAttribute(PipelineDataCollector.class.getName());
                will(returnValue(new HashMap()));
                allowing(_uploadContext).getReRun();
                will(returnValue(null));
            }});

            TsvAssayProvider provider = new TsvAssayProvider();
            List<AssayDataCollector> dataCollectors = provider.getDataCollectors(null, _uploadContext);
            assertEquals(2, dataCollectors.size());
            assertEquals(TextAreaDataCollector.class, dataCollectors.get(0).getClass());
            assertEquals(FileUploadDataCollector.class, dataCollectors.get(1).getClass());
        }

        @Test
        public void testPipelineDataCollectorList()
        {
            // Pretend the user selected a file from the pipeline directory and shouldn't be given other upload options
            _context.checking(new Expectations()
            {{
                allowing(_session).getAttribute(PipelineDataCollector.class.getName());
                Map<Pair<Container, Integer>, Collection> map = new HashMap<>();
                map.put(new Pair<>(_container, _protocol.getRowId()), Collections.singletonList(Collections.singletonMap(AssayDataCollector.PRIMARY_FILE, new File("mockFile"))));
                will(returnValue(map));
                allowing(_uploadContext).getReRun();
                will(returnValue(null));
            }});

            TsvAssayProvider provider = new TsvAssayProvider();
            List<AssayDataCollector> dataCollectors = provider.getDataCollectors(null, _uploadContext);
            assertEquals(1, dataCollectors.size());
            assertEquals(PipelineDataCollector.class, dataCollectors.get(0).getClass());
        }

        @Test
        public void testReshowDataCollectorList()
        {
            // Simulate an error reshow, where the user should be able to reuse the existing file or upload a replacment
            _context.checking(new Expectations(){{
                allowing(_session).getAttribute(PipelineDataCollector.class.getName());
                will(returnValue(new HashMap()));
                allowing(_uploadContext).getReRun();
                will(returnValue(null));
            }});

            TsvAssayProvider provider = new TsvAssayProvider();
            List<AssayDataCollector> dataCollectors = provider.getDataCollectors(Collections.singletonMap(AssayDataCollector.PRIMARY_FILE, new File("mockFile")), _uploadContext);
            assertEquals(3, dataCollectors.size());
            assertEquals(TextAreaDataCollector.class, dataCollectors.get(0).getClass());
            assertEquals(PreviouslyUploadedDataCollector.class, dataCollectors.get(1).getClass());
            assertEquals(FileUploadDataCollector.class, dataCollectors.get(2).getClass());
        }
    }
}
