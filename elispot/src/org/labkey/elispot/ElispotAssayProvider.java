package org.labkey.elispot;

import org.labkey.api.data.*;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.*;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.assay.*;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.query.RunDataQueryView;
import org.labkey.api.view.*;
import org.labkey.elispot.plate.ExcelPlateReader;
import org.labkey.elispot.plate.TextPlateReader;
import org.labkey.elispot.plate.ElispotPlateReaderService;

import javax.servlet.ServletException;
import java.util.*;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 7, 2008
 */
public class ElispotAssayProvider extends PlateBasedAssayProvider
{
    public static final String NAME = "ELISpot";
    public static final String ASSAY_DOMAIN_ANTIGEN_WELLGROUP = ExpProtocol.ASSAY_DOMAIN_PREFIX + "AntigenWellGroup";

    // run properties
    public static final String READER_PROPERTY_NAME = "PlateReader";
    public static final String READER_PROPERTY_CAPTION = "Plate Reader";

    // sample well groups
    public static final String SAMPLE_DESCRIPTION_PROPERTY_NAME = "SampleDescription";
    public static final String SAMPLE_DESCRIPTION_PROPERTY_CAPTION = "Sample Description";
    public static final String EFFECTOR_PROPERTY_NAME = "Effector";
    public static final String EFFECTOR_PROPERTY_CAPTION = "Effector Cell";
    public static final String STCL_PROPERTY_NAME = "STCL";
    public static final String STCL_PROPERTY_CAPTION = "Stimulation Antigen";

    // antigen well groups
    public static final String CELLWELL_PROPERTY_NAME = "CellWell";
    public static final String CELLWELL_PROPERTY_CAPTION = "Cells per Well";
    public static final String ANTIGENID_PROPERTY_NAME = "AntigenID";
    public static final String ANTIGENID_PROPERTY_CAPTION = "Antigen ID";
    public static final String ANTIGENNAME_PROPERTY_NAME = "AntigenName";
    public static final String ANTIGENNAME_PROPERTY_CAPTION = "Antigen Name";
    public static final String PEPTIDE_CONCENTRATION_NAME = "PeptideConcentration";
    public static final String PEPTIDE_CONCENTRATION_CAPTION = "Peptide Concentration (ug/ml)";


    public ElispotAssayProvider()
    {
        super("ElispotAssayProtocol", "ElispotAssayRun", ElispotDataHandler.ELISPOT_DATA_TYPE);
    }

    public ExpData getDataForDataRow(Object dataRowId)
    {
        if (!(dataRowId instanceof Integer))
            return null;
        OntologyObject dataRow = OntologyManager.getOntologyObject((Integer) dataRowId);
        if (dataRow == null)
            return null;
        OntologyObject dataRowParent = OntologyManager.getOntologyObject(dataRow.getOwnerObjectId());
        if (dataRowParent == null)
            return null;
        return ExperimentService.get().getExpData(dataRowParent.getObjectURI());
    }

    public String getName()
    {
        return NAME;
    }

    public List<Domain> createDefaultDomains(Container c, User user)
    {
        List<Domain> result = super.createDefaultDomains(c, user);
        result.add(createAntigenWellGroupDomain(c, user));
        return result;
    }
    
    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new HtmlView("The Elispot data file is the output file from the plate reader that has been selected.");
    }

    public TableInfo createDataTable(QuerySchema schema, String alias, ExpProtocol protocol)
    {
        return ElispotSchema.getDataRowTable(schema, protocol, alias);
    }

    protected Domain createSampleWellGroupDomain(Container c, User user)
    {
        Domain sampleWellGroupDomain = super.createSampleWellGroupDomain(c, user);

        addProperty(sampleWellGroupDomain, SPECIMENID_PROPERTY_NAME, SPECIMENID_PROPERTY_CAPTION, PropertyType.STRING);
        addProperty(sampleWellGroupDomain, PARTICIPANTID_PROPERTY_NAME, PARTICIPANTID_PROPERTY_CAPTION, PropertyType.STRING);
        addProperty(sampleWellGroupDomain, VISITID_PROPERTY_NAME, VISITID_PROPERTY_CAPTION, PropertyType.DOUBLE);
        addProperty(sampleWellGroupDomain, DATE_PROPERTY_NAME, DATE_PROPERTY_CAPTION, PropertyType.DATE_TIME);
        addProperty(sampleWellGroupDomain, SAMPLE_DESCRIPTION_PROPERTY_NAME, SAMPLE_DESCRIPTION_PROPERTY_CAPTION, PropertyType.STRING);
        //addProperty(sampleWellGroupDomain, EFFECTOR_PROPERTY_NAME, EFFECTOR_PROPERTY_CAPTION, PropertyType.STRING);
        //addProperty(sampleWellGroupDomain, STCL_PROPERTY_NAME, STCL_PROPERTY_CAPTION, PropertyType.STRING);

        return sampleWellGroupDomain;
    }

    protected Domain createAntigenWellGroupDomain(Container c, User user)
    {
        String domainLsid = getPresubstitutionLsid(ASSAY_DOMAIN_ANTIGEN_WELLGROUP);
        Domain antigenWellGroupDomain = PropertyService.get().createDomain(c, domainLsid, "Antigen Fields");

        antigenWellGroupDomain.setDescription("The user will be prompted to enter these properties for each of the antigen well groups in their chosen plate template.");
        addProperty(antigenWellGroupDomain, ANTIGENID_PROPERTY_NAME, ANTIGENID_PROPERTY_CAPTION, PropertyType.INTEGER);
        addProperty(antigenWellGroupDomain, ANTIGENNAME_PROPERTY_NAME, ANTIGENNAME_PROPERTY_CAPTION, PropertyType.STRING);
        addProperty(antigenWellGroupDomain, CELLWELL_PROPERTY_NAME, CELLWELL_PROPERTY_CAPTION, PropertyType.INTEGER);
        //addProperty(antigenWellGroupDomain, PEPTIDE_CONCENTRATION_NAME, PEPTIDE_CONCENTRATION_CAPTION, PropertyType.DOUBLE);

        return antigenWellGroupDomain;
    }

    protected Domain createRunDomain(Container c, User user)
    {
        Domain runDomain =  super.createRunDomain(c, user);

        addProperty(runDomain, "Protocol", "Protocol", PropertyType.STRING);
        addProperty(runDomain, "LabID", "Lab ID", PropertyType.STRING);
        addProperty(runDomain, "PlateID", "Plate ID", PropertyType.STRING);
        addProperty(runDomain, "TemplateID", "Template ID", PropertyType.STRING);
        addProperty(runDomain, "ExperimentDate", "Experiment Date", PropertyType.DATE_TIME);

        ListDefinition plateReaderList = createPlateReaderList(c, user);
        DomainProperty reader = addProperty(runDomain, READER_PROPERTY_NAME, READER_PROPERTY_CAPTION, PropertyType.STRING);
        reader.setLookup(new Lookup(c.getProject(), "lists", plateReaderList.getName()));
        reader.setRequired(true);

        return runDomain;
    }

    private ListDefinition createPlateReaderList(Container c, User user)
    {
        ListDefinition readerList = ElispotPlateReaderService.getPlateReaderList(c);
        if (readerList == null)
        {
            readerList = ElispotPlateReaderService.createPlateReaderList(c, user);

            DomainProperty nameProperty = readerList.getDomain().getPropertyByName(ElispotPlateReaderService.PLATE_READER_PROPERTY);
            DomainProperty typeProperty = readerList.getDomain().getPropertyByName(ElispotPlateReaderService.READER_TYPE_PROPERTY);

            try {
                addItem(readerList, user, "Cellular Technology Ltd. (CTL)", nameProperty, ExcelPlateReader.TYPE, typeProperty);
                addItem(readerList, user, "AID", nameProperty, TextPlateReader.TYPE, typeProperty);
                addItem(readerList, user, "Zeiss", nameProperty, TextPlateReader.TYPE, typeProperty);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
        return readerList;
    }

    private void addItem(ListDefinition list, User user, String name, DomainProperty nameProperty,
                         String fileType, DomainProperty fileTypeProperty) throws Exception
    {
        ListItem reader = list.createListItem();
        reader.setKey(name);
        reader.setProperty(nameProperty, name);
        reader.setProperty(fileTypeProperty, fileType);
        reader.save(user);
    }

    public FieldKey getParticipantIDFieldKey()
    {
        return FieldKey.fromParts("Properties",
                ElispotDataHandler.ELISPOT_INPUT_MATERIAL_DATA_PROPERTY, "Property", PARTICIPANTID_PROPERTY_NAME);
    }

    public FieldKey getVisitIDFieldKey(Container targetStudy)
    {
        if (AssayPublishService.get().getTimepointType(targetStudy) == TimepointType.VISIT)
            return FieldKey.fromParts("Properties",
                    ElispotDataHandler.ELISPOT_INPUT_MATERIAL_DATA_PROPERTY, "Property", VISITID_PROPERTY_NAME);
        else
            return FieldKey.fromParts("Properties",
                    ElispotDataHandler.ELISPOT_INPUT_MATERIAL_DATA_PROPERTY, "Property", DATE_PROPERTY_NAME);
    }

    public FieldKey getRunIdFieldKeyFromDataRow()
    {
        return FieldKey.fromParts("Run", "RowId");
    }

    public FieldKey getDataRowIdFieldKey()
    {
        return FieldKey.fromParts("ObjectId");
    }

    public FieldKey getSpecimenIDFieldKey()
    {
        return FieldKey.fromParts("Properties",
                ElispotDataHandler.ELISPOT_INPUT_MATERIAL_DATA_PROPERTY, "Property", SPECIMENID_PROPERTY_NAME);
    }

    public ActionURL publish(User user, ExpProtocol protocol, Container study, Set<AssayPublishKey> dataKeys, List<String> errors)
    {
        try {
            int rowIndex = 0;

            TimepointType studyType = AssayPublishService.get().getTimepointType(study);

            Map<Integer, ExpRun> runCache = new HashMap<Integer, ExpRun>();
            Map<Integer, Map<String, Object>> runPropertyCache = new HashMap<Integer, Map<String, Object>>();

            List<PropertyDescriptor> runPDs = new ArrayList<PropertyDescriptor>();
            runPDs.addAll(Arrays.asList(getRunPropertyColumns(protocol)));
            runPDs.addAll(Arrays.asList(getUploadSetColumns(protocol)));

            PropertyDescriptor[] samplePDs = getSampleWellGroupColumns(protocol);
            PropertyDescriptor[] dataPDs = ElispotSchema.getExistingDataProperties(protocol);
            Map<Object, AssayPublishKey> dataIdToPublishKey = new HashMap<Object, AssayPublishKey>();

            List<Object> ids = new ArrayList<Object>();
            for (AssayPublishKey dataKey : dataKeys)
            {
                ids.add(dataKey.getDataId());
                dataIdToPublishKey.put(dataKey.getDataId(), dataKey);
            }

            SimpleFilter filter = new SimpleFilter();
            filter.addInClause(getDataRowIdFieldKey().toString(), ids);

            // get the selected rows from the copy to study wizard
            OntologyObject[] dataRows = Table.select(OntologyManager.getTinfoObject(), Table.ALL_COLUMNS, filter,
                    new Sort(getDataRowIdFieldKey().toString()), OntologyObject.class);

            Map<String, Object>[] dataMaps = new HashMap[dataRows.length];
            Set<PropertyDescriptor> typeSet = new LinkedHashSet<PropertyDescriptor>();
            typeSet.add(createPublishPropertyDescriptor(study, getDataRowIdFieldKey().toString(), PropertyType.INTEGER));
            typeSet.add(createPublishPropertyDescriptor(study, "SourceLSID", PropertyType.INTEGER));

            Container sourceContainer = null;

            for (OntologyObject row : dataRows)
            {
                Map<String, Object> dataMap = new HashMap<String, Object>();
                Map<String, Object> rowProperties = OntologyManager.getProperties(row.getContainer(), row.getObjectURI());

                // add the data (or antigen group) properties
                String materialLsid = null;
                for (PropertyDescriptor pd : dataPDs)
                {
                    Object value = rowProperties.get(pd.getPropertyURI());
                    if (!ElispotDataHandler.ELISPOT_INPUT_MATERIAL_DATA_PROPERTY.equals(pd.getName()))
                        addProperty(pd, value, dataMap, typeSet);
                    else
                        materialLsid = (String) value;
                }

                // add the specimen group properties
                ExpMaterial material = ExperimentService.get().getExpMaterial(materialLsid);
                if (material != null)
                {
                    for (PropertyDescriptor pd : samplePDs)
                    {
                        if (!PARTICIPANTID_PROPERTY_NAME.equals(pd.getName()) &&
                                !VISITID_PROPERTY_NAME.equals(pd.getName()) &&
                                !DATE_PROPERTY_NAME.equals(pd.getName()))
                        {
                            addProperty(pd, material.getProperty(pd), dataMap, typeSet);
                        }
                    }
                }

                ExpRun run = runCache.get(row.getOwnerObjectId());
                if (run == null)
                {
                    OntologyObject dataRowParent = OntologyManager.getOntologyObject(row.getOwnerObjectId());
                    ExpData data = ExperimentService.get().getExpData(dataRowParent.getObjectURI());
                    run = data.getRun();
                    sourceContainer = run.getContainer();
                    runCache.put(row.getOwnerObjectId(), run);
                }

                // add the run level properties
                Map<String, Object> runProperties = runPropertyCache.get(run.getRowId());
                if (runProperties == null)
                {
                    runProperties = OntologyManager.getProperties(run.getContainer().getId(), run.getLSID());
                    runPropertyCache.put(run.getRowId(), runProperties);
                }

                for (PropertyDescriptor pd : runPDs)
                {
                    if (!TARGET_STUDY_PROPERTY_NAME.equals(pd.getName()) &&
                            !PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equals(pd.getName()))
                    {
                        PropertyDescriptor publishPd = pd.clone();
                        publishPd.setName("Run " + pd.getName());
                        addProperty(publishPd, runProperties.get(pd.getPropertyURI()), dataMap, typeSet);
                    }
                }

                AssayPublishKey publishKey = dataIdToPublishKey.get(row.getObjectId());
                dataMap.put("ParticipantID", publishKey.getParticipantId());
                dataMap.put("SequenceNum", publishKey.getVisitId());
                if (TimepointType.DATE == studyType)
                {
                    dataMap.put("Date", publishKey.getDate());
                }
                dataMap.put("SourceLSID", run.getLSID());
                dataMap.put(getDataRowIdFieldKey().toString(), publishKey.getDataId());
                addProperty(study, "Run Name", run.getName(), dataMap, typeSet);
                addProperty(study, "Run Comments", run.getComments(), dataMap, typeSet);
                addProperty(study, "Run CreatedOn", run.getCreated(), dataMap, typeSet);
                User createdBy = run.getCreatedBy();
                addProperty(study, "Run CreatedBy", createdBy == null ? null : createdBy.getDisplayName(HttpView.currentContext()), dataMap, typeSet);

                dataMaps[rowIndex++] = dataMap;
            }
            return AssayPublishService.get().publishAssayData(user, sourceContainer, study, protocol.getName(), protocol,
                    dataMaps, new ArrayList<PropertyDescriptor>(typeSet), getDataRowIdFieldKey().toString(), errors);
        }
        catch (SQLException se)
        {
            errors.add(se.getMessage());
            return null;
        }
        catch (IOException e)
        {
            errors.add(e.getMessage());
            return null;
        }
        catch (ServletException e)
        {
            errors.add(e.getMessage());
            return null;
        }
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Arrays.asList(new ParticipantVisitLookupResolverType(), new SpecimenIDLookupResolverType(), new ParticipantDateLookupResolverType(), new ThawListResolverType());
    }

    public ActionURL getUploadWizardURL(Container container, ExpProtocol protocol)
    {
        ActionURL url = new ActionURL(ElispotUploadWizardAction.class, container);
        url.addParameter("rowId", protocol.getRowId());
        return url;
    }

    public PropertyDescriptor[] getAntigenWellGroupColumns(ExpProtocol protocol)
    {
        return getPropertiesForDomainPrefix(protocol, ASSAY_DOMAIN_ANTIGEN_WELLGROUP);
    }

    private static final class ElispotRunDataQueryView extends RunDataQueryView
    {
        public ElispotRunDataQueryView(ExpProtocol protocol, ViewContext context, QuerySettings settings)
        {
            super(protocol, context, settings);
        }

        protected DataView createDataView()
        {
            DataView view = super.createDataView();
            view.getDataRegion().setRecordSelectorValueColumns("ObjectId");
            return view;
        }
    }

    public QueryView createRunDataView(ViewContext context, ExpProtocol protocol)
    {
        String name = getRunDataTableName(protocol);
        QuerySettings settings = new QuerySettings(context.getActionURL(), name);
        settings.setSchemaName(AssayService.ASSAY_SCHEMA_NAME);
        settings.setQueryName(name);
        return new ElispotRunDataQueryView(protocol, context, settings);
    }

    public QueryView createRunView(ViewContext context, ExpProtocol protocol)
    {
        return new RunListDetailsQueryView(protocol, context,
                ElispotController.RunDetailsAction.class, "rowId", ExpRunTable.Column.RowId.toString());
    }
}
