/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.assay.AbstractTsvAssayProvider;
import org.labkey.api.assay.AssayDataCollector;
import org.labkey.api.assay.AssayDataType;
import org.labkey.api.assay.AssayPipelineProvider;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProviderSchema;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssaySaveHandler;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.AssayTableMetadata;
import org.labkey.api.assay.FileUploadDataCollector;
import org.labkey.api.assay.PipelineDataCollector;
import org.labkey.api.assay.PreviouslyUploadedDataCollector;
import org.labkey.api.assay.TsvDataHandler;
import org.labkey.api.assay.actions.AssayRunUploadForm;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
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
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.qc.TsvDataExchangeHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.study.assay.StudyParticipantVisitResolverType;
import org.labkey.api.study.assay.ThawListResolverType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.assay.actions.TsvImportAction;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.PlateSetTable;
import org.labkey.assay.plate.query.PlateTable;
import org.labkey.assay.plate.view.AssayPlateMetadataTemplateAction;
import org.labkey.assay.view.PlateMetadataDataCollector;
import org.springframework.web.servlet.mvc.Controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.data.CompareType.STARTS_WITH;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 9:59:39 AM
 */
public class TsvAssayProvider extends AbstractTsvAssayProvider
{
    public static final String NAME = "General";
    public static final String PLATE_TEMPLATE_PROPERTY_NAME = "PlateTemplate";
    public static final String PLATE_TEMPLATE_PROPERTY_CAPTION = "Plate Template";

    private static final Set<String> participantImportAliases;
    private static final Set<String> specimenImportAliases;
    private static final Set<String> visitImportAliases;
    private static final Set<String> dateImportAliases;

    public static final Class<Module> assayModuleClass;

    public static final String ASSAY_DBSEQ = "GpatAssayDBSeq";
    public static final String ASSAY_DBSEQ_SUBSTITUTION = "${" + ASSAY_DBSEQ + "}";

    static
    {
        // this is the static lists of import aliases used in the default template
        participantImportAliases = PageFlowUtil.set("ptid", "participantId");
        specimenImportAliases = PageFlowUtil.set("specId", "vialId", "vialId1", "vial1_id", "guspec");
        visitImportAliases = PageFlowUtil.set("visitNo", "visit_no");
        dateImportAliases = PageFlowUtil.set("drawDt", "draw_date", "drawDate");

        // TODO: Assay migration - replace with AssayModule.class once these classes are moved out of assay-api into assay-src
        try
        {
            assayModuleClass = (Class<Module>)Class.forName("org.labkey.assay.AssayModule");
        }
        catch (ClassNotFoundException e)
        {
            throw new RuntimeException(e);
        }
    }

    public TsvAssayProvider()
    {
        this("GeneralAssayProtocol", "GeneralAssayRun", "General" + RESULT_LSID_PREFIX_PART, ModuleLoader.getInstance().getModule(assayModuleClass));
    }

    protected TsvAssayProvider(String protocolLSIDPrefix, String runLSIDPrefix, String resultRowLSIDPrefix, Module declaringModule)
    {
        super(protocolLSIDPrefix, runLSIDPrefix, resultRowLSIDPrefix, (AssayDataType) ExperimentService.get().getDataType(TsvDataHandler.NAMESPACE), declaringModule);
        setMaxFileInputs(100);  // no specific requirement for this, can be changed easily
    }

    @Override
    public List<AssayDataCollector> getDataCollectors(@Nullable Map<String, File> uploadedFiles, AssayRunUploadForm context)
    {
        List<AssayDataCollector> result = super.getDataCollectors(uploadedFiles, context);
        if (PipelineDataCollector.getFileQueue(context).isEmpty())
        {
            result.add(0, new TextAreaDataCollector());
        }
        return result;
    }

    @Override
    public @Nullable AssayDataCollector getPlateMetadataDataCollector(AssayRunUploadForm context)
    {
        if (context.getProvider().isPlateMetadataEnabled(context.getProtocol()))
        {
            return new PlateMetadataDataCollector(1, context);
        }
        return null;
    }

    @Override
    public @Nullable ActionURL getPlateMetadataTemplateURL(Container container, ExpProtocol protocol)
    {
        return new ActionURL(AssayPlateMetadataTemplateAction.class, container).addParameter("protocol", protocol.getRowId());
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    protected String getPresubstitutionRunLsid()
    {
        return getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_RUN, getLsidIdSub());
    }

    @Override
    protected String getPresubstitutionBatchLsid()
    {
        return getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_BATCH, getLsidIdSub());
    }

    @Override
    protected String getAssayProtocolLsid(Container container, String assayName, XarContext context)
    {
        Container projectContainer = container;
        if (!container.isProject() && container.getProject() != null)
            projectContainer = container.getProject();

        DbSequence sequence = DbSequenceManager.get(projectContainer, ASSAY_DBSEQ);
        sequence.ensureMinimum(getMaxExistingAssayDesignId(container));

        String assayDesignDBSeq = String.valueOf(sequence.next());
        context.addSubstitution(ASSAY_DBSEQ, assayDesignDBSeq);
        return new Lsid(_protocolLSIDPrefix, "Folder-" + container.getRowId(), assayDesignDBSeq).toString();
    }

    private Long getMaxExistingAssayDesignId(Container container)
    {
        long max = 0;
        TableInfo protocolTable = ExperimentService.get().getTinfoProtocol();
        String lsidPrefix = "urn:lsid:" + AppProps.getInstance().getDefaultLsidAuthority() + ":" + _protocolLSIDPrefix + ".Folder-" + container.getRowId() + ":";

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("LSID"), lsidPrefix, STARTS_WITH);

        TableSelector selector = new TableSelector(protocolTable, Collections.singleton("LSID"), filter, null);
        final List<String> nameSuffixes = new ArrayList<>();
        selector.forEach(String.class, fullname -> nameSuffixes.add(fullname.replace(lsidPrefix, "")));

        for (String nameSuffix : nameSuffixes)
        {
            if (nameSuffix.matches("\\d+"))
            {
                long id = Long.parseLong(nameSuffix);
                if (id > max)
                    max = id;
            }
        }

        return max;
    }

    @Override
    public String getLabel()
    {
        // Hack because there are many subclasses where we want to keep showing their name in the UI, but we want to
        // show "Standard" instead of "General" for direct uses of TsvAssayProvider
        return getClass().equals(TsvAssayProvider.class) ? "Standard" : super.getLabel();
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
                FieldKey.fromParts(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME),
                AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME,
                FieldKey.fromParts("LSID")
        );
    }

    @Override
    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain, Map<DomainProperty, Object>>> result = super.createDefaultDomains(c, user);

        Pair<Domain, Map<DomainProperty, Object>> resultDomain = createResultDomain(c, user);
        if (resultDomain != null)
            result.add(resultDomain);
        return result;
    }

    protected String getLsidIdSub()
    {
        return ASSAY_DBSEQ_SUBSTITUTION;
    }

    protected Pair<Domain,Map<DomainProperty,Object>> createResultDomain(Container c, User user)
    {
        Domain dataDomain = PropertyService.get().createDomain(c, getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_DATA, getLsidIdSub()), "Data Fields");
        dataDomain.setDescription("Define the results fields for this assay design. The user is prompted for these fields for individual rows within the imported run, typically done as a file upload.");
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

    @Override
    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new JspView<>("/org/labkey/assay/view/tsvDataDescription.jsp", form);
    }

    @Override
    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Arrays.asList(new StudyParticipantVisitResolverType(), new ThawListResolverType());
    }

    @Override
    public AssayProtocolSchema createProtocolSchema(User user, Container container, @NotNull ExpProtocol protocol, @Nullable Container targetStudy)
    {
        return new TSVProtocolSchema(user, container, this, protocol, targetStudy);
    }

    @Override
    public AssayProviderSchema createProviderSchema(User user, Container container, Container targetStudy)
    {
        return new TsvProviderSchema(user, container, this, targetStudy);
    }

    @Override
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

    @Override
    public String getDescription()
    {
        return "Imports data from simple Excel or TSV files.";
    }

    @Override
    public DataExchangeHandler createDataExchangeHandler()
    {
        return new TsvDataExchangeHandler();
    }

    @Override
    public PipelineProvider getPipelineProvider()
    {
        return new AssayPipelineProvider(assayModuleClass,
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
    public boolean supportsQC()
    {
        return true;
    }

    @Override
    public ReRunSupport getReRunSupport()
    {
        return ReRunSupport.ReRunAndReplace;
    }

    @Override
    public boolean isExclusionSupported()
    {
        return true;
    }

    @Override
    public boolean supportsFlagColumnType(ExpProtocol.AssayDomainTypes type)
    {
        return type== ExpProtocol.AssayDomainTypes.Result;
    }

    @Override
    public boolean supportsPlateMetadata()
    {
        return true;
    }

    private boolean hasDomainNameChanged(ExpProtocol protocol, GWTDomain<GWTPropertyDescriptor> domain)
    {
        return !(protocol.getName() + getDomainNameSuffix(domain)).equals(domain.getName());
    }

    @Override
    public void changeDomain(User user, ExpProtocol protocol, GWTDomain<GWTPropertyDescriptor> orig, GWTDomain<GWTPropertyDescriptor> update)
    {
        super.changeDomain(user, protocol, orig, update);

        if (hasDomainNameChanged(protocol, orig))
        {
            update.setName(protocol.getName() + getDomainNameSuffix(orig));
        }

        if (isPlateMetadataEnabled(protocol))
        {
            Set<String> existingFields = update.getFields().stream().map(GWTPropertyDescriptor::getName).collect(Collectors.toSet());
            boolean hasRuns = !protocol.getExpRuns().isEmpty();

            // for plate metadata support we need to ensure specific fields on both the run and result domains
            Domain runDomain = getRunDomain(protocol);
            if (runDomain != null && runDomain.getTypeURI().equals(update.getDomainURI()))
            {
                ArrayList<GWTPropertyDescriptor> newFields = new ArrayList<>();

                Optional<GWTPropertyDescriptor> plateTemplateColumn = update.getFields().stream().filter(field -> field.getName().equals(AssayPlateMetadataService.PLATE_TEMPLATE_COLUMN_NAME)).findFirst();
                if (plateTemplateColumn.isPresent())
                {
                    // Ensure the lookup container is null, so it defaults to "Current Folder" to more easily support
                    // cross-folder support.
                    GWTPropertyDescriptor plateTemplate = plateTemplateColumn.get();
                    plateTemplate.setLookupContainer(null);
                }
                else
                {
                    GWTPropertyDescriptor plateTemplate = new GWTPropertyDescriptor(AssayPlateMetadataService.PLATE_TEMPLATE_COLUMN_NAME, PropertyType.STRING.getTypeUri());
                    plateTemplate.setLookupSchema(AssaySchema.NAME + "." + getResourceName());
                    plateTemplate.setLookupQuery(TsvProviderSchema.PLATE_TEMPLATE_TABLE);
                    plateTemplate.setLookupContainer(null);
                    plateTemplate.setRequired(!AssayPlateMetadataService.isExperimentalAppPlateEnabled());
                    plateTemplate.setShownInUpdateView(false);

                    newFields.add(plateTemplate);
                }

                if (!existingFields.contains(AssayPlateMetadataService.PLATE_SET_COLUMN_NAME))
                {
                    GWTPropertyDescriptor plateSet = new GWTPropertyDescriptor(AssayPlateMetadataService.PLATE_SET_COLUMN_NAME, PropertyType.INTEGER.getTypeUri());
                    plateSet.setLookupSchema(PlateSchema.SCHEMA_NAME);
                    plateSet.setLookupQuery(PlateSetTable.NAME);
                    plateSet.setLookupContainer(null);
                    plateSet.setRequired(AssayPlateMetadataService.isExperimentalAppPlateEnabled() && !hasRuns);
                    plateSet.setShownInUpdateView(false);

                    newFields.add(plateSet);
                }

                if (!newFields.isEmpty())
                {
                    newFields.addAll(update.getFields());
                    update.setFields(newFields);
                }
            }

            Domain resultsDomain = getResultsDomain(protocol);
            if (resultsDomain != null && resultsDomain.getTypeURI().equals(update.getDomainURI()))
            {
                ArrayList<GWTPropertyDescriptor> newFields = new ArrayList<>();

                if (!existingFields.contains(AssayResultDomainKind.PLATE_COLUMN_NAME))
                {
                    GWTPropertyDescriptor plate = new GWTPropertyDescriptor(AssayResultDomainKind.PLATE_COLUMN_NAME, PropertyType.INTEGER.getTypeUri());
                    plate.setLookupSchema(PlateSchema.SCHEMA_NAME);
                    plate.setLookupQuery(PlateTable.NAME);
                    plate.setLookupContainer(null);
                    plate.setRequired(AssayPlateMetadataService.isExperimentalAppPlateEnabled() && !hasRuns);
                    plate.setImportAliases("PlateID,\"Plate ID\"");
                    plate.setShownInUpdateView(false);
                    plate.setHidden(true);

                    newFields.add(plate);
                }

                if (!existingFields.contains(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME))
                {
                    GWTPropertyDescriptor wellLocation = new GWTPropertyDescriptor(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME, PropertyType.STRING.getTypeUri());
                    wellLocation.setImportAliases("Well,\"Well Location\"");
                    wellLocation.setShownInUpdateView(false);

                    newFields.add(wellLocation);
                }

                if (!existingFields.contains(AssayResultDomainKind.WELL_LSID_COLUMN_NAME))
                {
                    GWTPropertyDescriptor wellLsid = new GWTPropertyDescriptor(AssayResultDomainKind.WELL_LSID_COLUMN_NAME, PropertyType.STRING.getTypeUri());
                    wellLsid.setShownInInsertView(false);
                    wellLsid.setShownInUpdateView(false);
                    wellLsid.setHidden(true);

                    newFields.add(wellLsid);
                }

                if (!newFields.isEmpty())
                {
                    newFields.addAll(update.getFields());
                    update.setFields(newFields);
                }
            }
        }
    }

    @Override
    public void ensurePropertyDomainName(ExpProtocol protocol, ObjectProperty prop)
    {
        if (prop.getName() == null)
            prop.setName(protocol.getName()); // set domain name to match assay design name
    }

    private String getDomainNameSuffix(GWTDomain<GWTPropertyDescriptor> domain)
    {
        String domainKindName = domain.getDomainKindName();
        String nameSuffix = switch (domainKindName)
                {
                    case "Assay Batches" -> "Batch";
                    case "Assay Runs" -> "Run";
                    case "Assay Results" -> "Data";
                    default -> "";
                };
        return " " + nameSuffix + " Fields";
    }

    @Override
    public boolean canRename()
    {
        return true;
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
            assertEquals(2, dataCollectors.size());
            assertEquals(TextAreaDataCollector.class, dataCollectors.get(0).getClass());
            assertEquals(FileUploadDataCollector.class, dataCollectors.get(1).getClass());
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
