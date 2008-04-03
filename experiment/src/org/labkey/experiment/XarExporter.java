package org.labkey.experiment;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlOptions;
import org.fhcrc.cpas.exp.xml.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.util.DateUtil;
import org.labkey.experiment.api.*;
import org.labkey.experiment.xar.XarExportSelection;
import org.w3c.dom.Document;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * User: jeckels
 * Date: Nov 21, 2005
 */
public class XarExporter
{
    private final LSIDRelativizer _lsidRelativizer;
    private final URLRewriter _urlRewriter;
    private final ExperimentArchiveDocument _document;
    private final ExperimentArchiveType _archive;

    private String _xarXmlFileName = "experiment.xar.xml";

    /**
     * As we export objects to XML, we may transform the LSID so we need to remember the
     * original LSIDs
     */
    private Set<String> _experimentLSIDs = new HashSet<String>();
    private Set<String> _experimentRunLSIDs = new HashSet<String>();
    private Set<String> _protocolLSIDs = new HashSet<String>();
    private Set<String> _inputDataLSIDs = new HashSet<String>();
    private Set<String> _inputMaterialLSIDs = new HashSet<String>();

    private Set<String> _sampleSetLSIDs = new HashSet<String>();
    private Set<String> _domainLSIDs = new HashSet<String>();

    private LSIDRelativizer.RelativizedLSIDs _relativizedLSIDs = new LSIDRelativizer.RelativizedLSIDs();
    private Logger _log;

    public XarExporter(LSIDRelativizer lsidRelativizer, DataURLRelativizer urlRelativizer)
    {
        _lsidRelativizer = lsidRelativizer;
        _urlRewriter = urlRelativizer.createURLRewriter();

        _document = ExperimentArchiveDocument.Factory.newInstance();
        _archive = _document.addNewExperimentArchive();
    }

    public XarExporter(LSIDRelativizer lsidRelativizer, DataURLRelativizer urlRelativizer, XarExportSelection selection, String xarXmlFileName, Logger log) throws SQLException, ExperimentException
    {
        this(lsidRelativizer, urlRelativizer);
        _log = log;

        selection.addContent(this);

        if (xarXmlFileName != null)
        {
            setXarXmlFileName(xarXmlFileName);
        }
    }

    private void logProgress(String message)
    {
        if (_log != null)
        {
            _log.info(message);
        }
    }

    public void setXarXmlFileName(String fileName) {
        this._xarXmlFileName = fileName;
    }

    public void addExperimentRun(ExperimentRun run, ExpExperiment exp) throws SQLException, ExperimentException
    {
        if (_experimentRunLSIDs.contains(run.getLSID()))
        {
            return;
        }
        logProgress("Adding experiment run " + run.getLSID());
        _experimentRunLSIDs.add(run.getLSID());
        ExperimentArchiveType.ExperimentRuns runs = _archive.getExperimentRuns();
        if (runs == null)
        {
            runs = _archive.addNewExperimentRuns();
        }
        ExperimentRunType xRun = runs.addNewExperimentRun();
        xRun.setAbout(_lsidRelativizer.relativize(run.getLSID(), _relativizedLSIDs));
        
        if (null!=exp)
            xRun.setExperimentLSID(_lsidRelativizer.relativize(exp.getLSID(), _relativizedLSIDs));

        if (run.getComments() != null)
        {
            xRun.setComments(run.getComments());
        }
        xRun.setName(run.getName());
        PropertyCollectionType properties = getProperties(run.getLSID(), ContainerManager.getForId(run.getContainer()));
        if (properties != null)
        {
            xRun.setProperties(properties);
        }
        xRun.setProtocolLSID(_lsidRelativizer.relativize(run.getProtocolLSID(), _relativizedLSIDs));

        Protocol protocol = ExperimentServiceImpl.get().getProtocol(run.getProtocolLSID());
        if (protocol != null)
        {
            addProtocol(protocol, true);
        }

        if (exp != null)
        {
            Experiment experiment= ExperimentServiceImpl.get().getExperiment(exp.getLSID());
            if (experiment == null)
            {
                throw new ExperimentException("Could not find an experiment with RowId " + exp.getLSID());
            }

            addExperiment(experiment);
        }

        List<Data> inputData = ExperimentServiceImpl.get().getRunInputData(run.getLSID());
        ExperimentArchiveType.StartingInputDefinitions inputDefs = _archive.getStartingInputDefinitions();
        if (inputData.size() > 0 && inputDefs == null)
        {
            inputDefs = _archive.addNewStartingInputDefinitions();
        }
        for (Data data : inputData)
        {
            if (!_inputDataLSIDs.contains(data.getLSID()))
            {
                _inputDataLSIDs.add(data.getLSID());

                DataBaseType xData = inputDefs.addNewData();
                populateData(xData, data, run);
            }
        }

        List<Material> inputMaterials = ExperimentServiceImpl.get().getRunInputMaterial(run.getLSID());
        if (inputMaterials.size() > 0 && inputDefs == null)
        {
            inputDefs = _archive.addNewStartingInputDefinitions();
        }
        for (Material material: inputMaterials)
        {
            if (!_inputMaterialLSIDs.contains(material.getLSID()))
            {
                _inputMaterialLSIDs.add(material.getLSID());

                MaterialBaseType xMaterial = inputDefs.addNewMaterial();
                populateMaterial(xMaterial, material);
            }
        }

        ExpProtocolApplication[] applications = ExperimentService.get().getExpProtocolApplicationsForRun(run.getRowId());
        ExperimentRunType.ProtocolApplications xApplications = xRun.addNewProtocolApplications();

        Map<String, PropertyDescriptor> dataInputRoles = ExperimentService.get().getDataInputRoles(ContainerManager.getForId(run.getContainer()));
        Map<String, PropertyDescriptor> materialInputRoles = ExperimentService.get().getMaterialInputRoles(ContainerManager.getForId(run.getContainer()));

        for (ExpProtocolApplication application : applications)
        {
            ProtocolApplicationBaseType xApplication = xApplications.addNewProtocolApplication();
            xApplication.setAbout(_lsidRelativizer.relativize(application.getLSID(), _relativizedLSIDs));
            xApplication.setActionSequence(application.getActionSequence());
            Date activityDate = application.getActivityDate();
            if (activityDate != null)
            {
                Calendar cal = new GregorianCalendar();
                cal.setTime(application.getActivityDate());
                xApplication.setActivityDate(cal);
            }
            if (application.getComments() != null)
            {
                xApplication.setComments(application.getComments());
            }
            xApplication.setCpasType(application.getCpasType());

            InputOutputRefsType inputRefs = null;
            Data[] inputDataRefs = ExperimentServiceImpl.get().getDataInputReferencesForApplication(application.getRowId());
            DataInput[] dataInputs = ExperimentServiceImpl.get().getDataInputsForApplication(application.getRowId());
            for (Data data : inputDataRefs)
            {
                if (inputRefs == null)
                {
                    inputRefs = xApplication.addNewInputRefs();
                }
                InputOutputRefsType.DataLSID dataLSID = inputRefs.addNewDataLSID();
                dataLSID.setStringValue(_lsidRelativizer.relativize(data.getLSID(), _relativizedLSIDs));
                String roleName = null;
                for (DataInput dataInput : dataInputs)
                {
                    if (dataInput.getDataId() == data.getRowId() && dataInput.getPropertyId() != null)
                    {
                        for (PropertyDescriptor descriptor : dataInputRoles.values())
                        {
                            if (descriptor.getPropertyId() == dataInput.getPropertyId().intValue())
                            {
                                roleName = descriptor.getName();
                            }
                        }
                    }
                }
                if (roleName != null)
                {
                    dataLSID.setRoleName(roleName);
                }
            }

            Material[] inputMaterial = ExperimentServiceImpl.get().getMaterialInputReferencesForApplication(application.getRowId());
            MaterialInput[] materialInputs = ExperimentServiceImpl.get().getMaterialInputsForApplication(application.getRowId());
            for (Material material : inputMaterial)
            {
                if (inputRefs == null)
                {
                    inputRefs = xApplication.addNewInputRefs();
                }
                InputOutputRefsType.MaterialLSID materialLSID = inputRefs.addNewMaterialLSID();
                materialLSID.setStringValue(_lsidRelativizer.relativize(material.getLSID(), _relativizedLSIDs));

                String roleName = null;
                for (MaterialInput materialInput : materialInputs)
                {
                    if (materialInput.getMaterialId() == material.getRowId() && materialInput.getPropertyId() != null)
                    {
                        for (PropertyDescriptor descriptor : materialInputRoles.values())
                        {
                            if (descriptor.getPropertyId() == materialInput.getPropertyId().intValue())
                            {
                                roleName = descriptor.getName();
                            }
                        }
                    }
                }
                if (roleName != null)
                {
                    materialLSID.setRoleName(roleName);
                }
            }

            xApplication.setName(application.getName());

            ProtocolApplicationBaseType.OutputDataObjects outputDataObjects = xApplication.addNewOutputDataObjects();
            Data[] outputData = ExperimentServiceImpl.get().getOutputDataForApplication(application.getRowId());
            if (outputData.length > 0)
            {
                for (Data data : outputData)
                {
                    DataBaseType xData = outputDataObjects.addNewData();
                    populateData(xData, data, run);
                }
            }

            ProtocolApplicationBaseType.OutputMaterials outputMaterialObjects = xApplication.addNewOutputMaterials();
            Material[] outputMaterials = ExperimentServiceImpl.get().getOutputMaterialForApplication(application.getRowId());
            if (outputMaterials.length > 0)
            {
                for (Material material : outputMaterials)
                {
                    MaterialBaseType xMaterial = outputMaterialObjects.addNewMaterial();
                    populateMaterial(xMaterial, material);
                }
            }

            PropertyCollectionType appProperties = getProperties(application.getLSID(), ContainerManager.getForId(run.getContainer()));
            if (appProperties != null)
            {
                xApplication.setProperties(appProperties);
            }

            ProtocolApplicationParameter[] parameters = ExperimentService.get().getProtocolApplicationParameters(application.getRowId());
            if (parameters != null)
            {
                SimpleValueCollectionType xParameters = xApplication.addNewProtocolApplicationParameters();
                for (ProtocolApplicationParameter parameter : parameters)
                {
                    SimpleValueType xValue = xParameters.addNewSimpleVal();
                    populateXmlBeanValue(xValue, parameter);
                }
            }

            xApplication.setProtocolLSID(_lsidRelativizer.relativize(application.getProtocol().getLSID(), _relativizedLSIDs));
        }
    }

    private void populateXmlBeanValue(SimpleValueType xValue, AbstractParameter param)
    {
        xValue.setName(param.getName());
        xValue.setOntologyEntryURI(param.getOntologyEntryURI());
        xValue.setValueType(param.getXmlBeanValueType());
        String value = relativizeLSIDPropertyValue(param.getXmlBeanValue(), param.getXmlBeanValueType());
        if (value != null)
        {
            xValue.setStringValue(value);
        }
    }

    private String relativizeLSIDPropertyValue(String value, SimpleTypeNames.Enum type)
    {
        if (type == SimpleTypeNames.STRING &&
            value != null &&
            value.indexOf("urn:lsid:") == 0)
        {
            return _lsidRelativizer.relativize(value, _relativizedLSIDs);
        }
        else
        {
            return value;
        }
    }

    private void populateMaterial(MaterialBaseType xMaterial, Material material)
            throws SQLException, ExperimentException
    {
        logProgress("Adding material " + material.getLSID());
        addSampleSet(material.getCpasType());
        xMaterial.setAbout(_lsidRelativizer.relativize(material.getLSID(), _relativizedLSIDs));
        xMaterial.setCpasType(material.getCpasType() == null ? "Material" : _lsidRelativizer.relativize(material.getCpasType(), _relativizedLSIDs));
        xMaterial.setName(material.getName());
        PropertyCollectionType materialProperties = getProperties(material.getLSID(), ContainerManager.getForId(material.getContainer()));
        if (materialProperties != null)
        {
            xMaterial.setProperties(materialProperties);
        }
        String sourceProtocolLSID = _lsidRelativizer.relativize(material.getSourceProtocolLSID(), _relativizedLSIDs);
        if (sourceProtocolLSID != null)
        {
            xMaterial.setSourceProtocolLSID(sourceProtocolLSID);
        }
    }

    private void addSampleSet(String cpasType)
    {
        if (_sampleSetLSIDs.contains(cpasType))
        {
            return;
        }
        _sampleSetLSIDs.add(cpasType);
        ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(cpasType);
        if (sampleSet == null)
        {
            return;
        }
        if (_archive.getSampleSets() == null)
        {
            _archive.addNewSampleSets();
        }
        SampleSetType xSampleSet = _archive.getSampleSets().addNewSampleSet();
        xSampleSet.setAbout(_lsidRelativizer.relativize(sampleSet.getLSID(), _relativizedLSIDs));
        xSampleSet.setMaterialLSIDPrefix(_lsidRelativizer.relativize(sampleSet.getMaterialLSIDPrefix(), _relativizedLSIDs));
        xSampleSet.setName(sampleSet.getName());
        if (sampleSet.getDescription() != null)
        {
            xSampleSet.setDescription(sampleSet.getDescription());
        }
        
        Domain domain = sampleSet.getType();
        addDomain(domain);
    }

    private void addDomain(Domain domain)
    {
        if (_domainLSIDs.contains(domain.getTypeURI()))
        {
            return;
        }
        _domainLSIDs.add(domain.getTypeURI());
        
        ExperimentArchiveType.DomainDefinitions domainDefs = _archive.getDomainDefinitions();
        if (domainDefs == null)
        {
            domainDefs = _archive.addNewDomainDefinitions();
        }
        DomainDescriptorType xDomain = domainDefs.addNewDomain();
        xDomain.setName(domain.getName());
        if (domain.getDescription() != null)
        {
            xDomain.setDescription(domain.getDescription());
        }
        xDomain.setDomainURI((_lsidRelativizer.relativize(domain.getTypeURI(), _relativizedLSIDs)));
        for (DomainProperty domainProp : domain.getProperties())
        {
            PropertyDescriptor prop = domainProp.getPropertyDescriptor();

            PropertyDescriptorType xProp = xDomain.addNewPropertyDescriptor();
            if (domainProp.getDescription() != null)
            {
                xProp.setDescription(domainProp.getDescription());
            }
            xProp.setName(domainProp.getName());
            xProp.setPropertyURI(_lsidRelativizer.relativize(domainProp.getPropertyURI(), _relativizedLSIDs));
            if (prop.getConceptURI() != null)
            {
                xProp.setConceptURI(prop.getConceptURI());
            }
            if (xProp.getDescription() != null)
            {
                xProp.setDescription(domainProp.getDescription());
            }
            if (domainProp.getFormatString() != null)
            {
                xProp.setFormat(domainProp.getFormatString());
            }
            if (domainProp.getLabel() != null)
            {
                xProp.setLabel(domainProp.getLabel());
            }
            if (prop.getOntologyURI() != null)
            {
                xProp.setOntologyURI(prop.getOntologyURI());
            }
            if (prop.getRangeURI() != null)
            {
                xProp.setRangeURI(prop.getRangeURI());
            }
            if (prop.getSearchTerms() != null)
            {
                xProp.setSearchTerms(prop.getSearchTerms());
            }
            if( prop.getSemanticType() != null)
            {
                xProp.setSemanticType(prop.getSemanticType());
            }
        }
    }

    private PropertyCollectionType addOriginalURLProperty(PropertyCollectionType properties, String originalURL)
    {
        if (properties == null)
        {
            properties = PropertyCollectionType.Factory.newInstance();
        }

        for (SimpleValueType prop : properties.getSimpleValArray())
        {
            if (XarReader.ORIGINAL_URL_PROPERTY.equals(prop.getOntologyEntryURI()) &&
                XarReader.ORIGINAL_URL_PROPERTY_NAME.equals(prop.getName()))
            {
                return properties;
            }
        }

        SimpleValueType newProperty = properties.addNewSimpleVal();
        newProperty.setValueType(SimpleTypeNames.STRING);
        newProperty.setOntologyEntryURI(XarReader.ORIGINAL_URL_PROPERTY);
        newProperty.setName(XarReader.ORIGINAL_URL_PROPERTY_NAME);
        newProperty.setStringValue(originalURL);

        return properties;
    }

    private void populateData(DataBaseType xData, Data data, ExperimentRun run) throws SQLException, ExperimentException
    {
        logProgress("Adding data " + data.getLSID());
        xData.setAbout(_lsidRelativizer.relativize(data.getLSID(), _relativizedLSIDs));
        xData.setCpasType(data.getCpasType() == null ? "Data" : _lsidRelativizer.relativize(data.getCpasType(), _relativizedLSIDs));

        File f = data.getFile();
        String url = null;
        if (f != null)
        {
            url = _urlRewriter.rewriteURL(f, new ExpDataImpl(data), run);
        }
        xData.setName(data.getName());
        PropertyCollectionType dataProperties = getProperties(data.getLSID(), ContainerManager.getForId(data.getContainer()));
        if (url != null)
        {
            xData.setDataFileUrl(url);
            if (!url.equals(data.getDataFileUrl()))
            {
                // Add the original URL as a property on the data object
                // so that it's easier to figure out links between files later, since
                // the URLs have all been rewritten
                dataProperties = addOriginalURLProperty(dataProperties, data.getDataFileUrl());
            }
        }
        if (dataProperties != null)
        {
            xData.setProperties(dataProperties);
        }
        String sourceProtocolLSID = _lsidRelativizer.relativize(data.getSourceProtocolLSID(), _relativizedLSIDs);
        if (sourceProtocolLSID != null)
        {
            xData.setSourceProtocolLSID(sourceProtocolLSID);
        }
    }

    public void addProtocol(Protocol protocol, boolean includeChildren) throws SQLException, ExperimentException
    {
        if (_protocolLSIDs.contains(protocol.getLSID()))
        {
            return;
        }
        logProgress("Adding protocol " + protocol.getLSID());
        _protocolLSIDs.add(protocol.getLSID());

        ExperimentArchiveType.ProtocolDefinitions protocolDefs = _archive.getProtocolDefinitions();
        if (protocolDefs == null)
        {
            protocolDefs = _archive.addNewProtocolDefinitions();
        }
        ProtocolBaseType xProtocol = protocolDefs.addNewProtocol();

        xProtocol.setAbout(_lsidRelativizer.relativize(protocol.getLSID(), _relativizedLSIDs));
        xProtocol.setApplicationType(protocol.getApplicationType());
        ContactType contactType = getContactType(protocol.getLSID(), ContainerManager.getForId(protocol.getContainer()));
        if (contactType != null)
        {
            xProtocol.setContact(contactType);
        }
        if (protocol.getInstrument() != null)
        {
            xProtocol.setInstrument(protocol.getInstrument());
        }
        xProtocol.setName(protocol.getName());
        PropertyCollectionType properties = getProperties(protocol.getLSID(), ContainerManager.getForId(protocol.getContainer()), XarReader.CONTACT_PROPERTY);
        if (properties != null)
        {
            xProtocol.setProperties(properties);
        }

        if (protocol.getMaxInputDataPerInstance() == null)
        {
            xProtocol.setNilMaxInputDataPerInstance();
        }
        else
        {
            xProtocol.setMaxInputDataPerInstance(protocol.getMaxInputDataPerInstance().intValue());
        }

        if (protocol.getMaxInputMaterialPerInstance() == null)
        {
            xProtocol.setNilMaxInputMaterialPerInstance();
        }
        else
        {
            xProtocol.setMaxInputMaterialPerInstance(protocol.getMaxInputMaterialPerInstance().intValue());
        }

        if (protocol.getOutputDataPerInstance() == null)
        {
            xProtocol.setNilOutputDataPerInstance();
        }
        else
        {
            xProtocol.setOutputDataPerInstance(protocol.getOutputDataPerInstance().intValue());
        }

        if (protocol.getOutputMaterialPerInstance() == null)
        {
            xProtocol.setNilOutputMaterialPerInstance();
        }
        else
        {
            xProtocol.setOutputMaterialPerInstance(protocol.getOutputMaterialPerInstance().intValue());
        }

        xProtocol.setOutputDataType(protocol.getOutputDataType());
        xProtocol.setOutputMaterialType(protocol.getOutputMaterialType());
        if (protocol.getProtocolDescription() != null)
        {
            xProtocol.setProtocolDescription(protocol.getProtocolDescription());
        }
        if (protocol.getSoftware() != null)
        {
            xProtocol.setSoftware(protocol.getSoftware());
        }

        Map<String, ProtocolParameter> params = ExperimentService.get().getProtocolParameters(protocol.getRowId());
        SimpleValueCollectionType valueCollection = null;
        for (ProtocolParameter param : params.values())
        {
            if (valueCollection == null)
            {
                valueCollection = xProtocol.addNewParameterDeclarations();
            }
            SimpleValueType xValue = valueCollection.addNewSimpleVal();
            populateXmlBeanValue(xValue, param);
        }

        if (includeChildren)
        {
            ProtocolAction[] protocolActions = ExperimentServiceImpl.get().getProtocolActions(protocol.getRowId());

            if (protocolActions.length > 0)
            {
                ExperimentArchiveType.ProtocolActionDefinitions actionDefs = _archive.getProtocolActionDefinitions();
                if (actionDefs == null)
                {
                    actionDefs = _archive.addNewProtocolActionDefinitions();
                }

                ProtocolActionSetType actionSet = actionDefs.addNewProtocolActionSet();
                actionSet.setParentProtocolLSID(_lsidRelativizer.relativize(protocol.getLSID(), _relativizedLSIDs));
                for (ProtocolAction action : protocolActions)
                {
                    addProtocolAction(action, actionSet);
                }
            }
        }
    }

    private void addProtocolAction(ProtocolAction protocolAction, ProtocolActionSetType actionSet) throws SQLException, ExperimentException
    {
        Protocol protocol = ExperimentServiceImpl.get().getProtocol(protocolAction.getChildProtocolId());
        Protocol parentProtocol = ExperimentServiceImpl.get().getProtocol(protocolAction.getParentProtocolId());
        String lsid = protocol.getLSID();

        ProtocolActionType xProtocolAction = actionSet.addNewProtocolAction();
        xProtocolAction.setActionSequence(protocolAction.getSequence());
        xProtocolAction.setChildProtocolLSID(_lsidRelativizer.relativize(lsid, _relativizedLSIDs));

        ProtocolActionPredecessor[] predecessors = ExperimentServiceImpl.get().getProtocolActionPredecessors(parentProtocol.getLSID(), lsid);
        for (ProtocolActionPredecessor predecessor : predecessors)
        {
            ProtocolActionType.PredecessorAction xPredecessor = xProtocolAction.addNewPredecessorAction();
            xPredecessor.setActionSequenceRef(predecessor.getPredecessorSequence());
        }

        addProtocol(protocol, true);
    }

    public void addExperiment(Experiment experiment) throws ExperimentException, SQLException
    {
        if (_experimentLSIDs.contains(experiment.getLSID()))
        {
            return;
        }
        logProgress("Adding experiment " + experiment.getLSID());
        _experimentLSIDs.add(experiment.getLSID());

        ExperimentType xExperiment = _archive.addNewExperiment();
        xExperiment.setAbout(_lsidRelativizer.relativize(experiment.getLSID(), _relativizedLSIDs));
        if (experiment.getComments() != null)
        {
            xExperiment.setComments(experiment.getComments());
        }
        ContactType contactType = getContactType(experiment.getLSID(), ContainerManager.getForId(experiment.getContainer()));
        if (contactType != null)
        {
            xExperiment.setContact(contactType);
        }
        if (experiment.getExperimentDescriptionURL() != null)
        {
            xExperiment.setExperimentDescriptionURL(experiment.getExperimentDescriptionURL());
        }
        if (experiment.getHypothesis() != null)
        {
            xExperiment.setHypothesis(experiment.getHypothesis());
        }
        xExperiment.setName(experiment.getName());

        PropertyCollectionType xProperties = getProperties(experiment.getLSID(), ContainerManager.getForId(experiment.getContainer()), XarReader.CONTACT_PROPERTY);
        if (xProperties != null)
        {
            xExperiment.setProperties(xProperties);
        }
    }

    private PropertyCollectionType getProperties(String lsid, Container parentContainer, String... ignoreProperties) throws SQLException, ExperimentException
    {
        Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(lsid);

        Set<String> ignoreSet = new HashSet<String>();
        for (String ignore : ignoreProperties)
        {
            ignoreSet.add(ignore);
        }

        PropertyCollectionType result = PropertyCollectionType.Factory.newInstance();
        boolean addedProperty = false;
        for (ObjectProperty value : properties.values())
        {
            if (ignoreSet.contains(value.getPropertyURI()))
            {
                continue;
            }
            addedProperty = true;

            if (value.getPropertyType() == PropertyType.RESOURCE)
            {
                PropertyObjectType subProperty = result.addNewPropertyObject();
                PropertyObjectDeclarationType propDec = subProperty.addNewPropertyObjectDeclaration();
                propDec.setName(value.getName());
                propDec.setOntologyEntryURI(_lsidRelativizer.relativize(value.getPropertyURI(), _relativizedLSIDs));
                propDec.setValueType(SimpleTypeNames.PROPERTY_URI);

                PropertyCollectionType childProperties = getProperties(value.getStringValue(), parentContainer);
                if (childProperties != null)
                {
                    subProperty.setChildProperties(childProperties);
                }
            }
            else
            {
                SimpleValueType simpleValue = result.addNewSimpleVal();
                simpleValue.setName(value.getName());
                simpleValue.setOntologyEntryURI(_lsidRelativizer.relativize(value.getPropertyURI(), _relativizedLSIDs));

                switch(value.getPropertyType())
                {
                    case DATE_TIME:
                        simpleValue.setValueType(SimpleTypeNames.DATE_TIME);
                        simpleValue.setStringValue(DateUtil.formatDateTime(value.getDateTimeValue(), AbstractParameter.SIMPLE_FORMAT_PATTERN));
                        break;
                    case DOUBLE:
                        simpleValue.setValueType(SimpleTypeNames.DOUBLE);
                        simpleValue.setStringValue(value.getFloatValue().toString());
                        break;
                    case FILE_LINK:
                        simpleValue.setValueType(SimpleTypeNames.FILE_LINK);
                        simpleValue.setStringValue(value.getEitherStringValue());
                        break;
                    case INTEGER:
                        simpleValue.setValueType(SimpleTypeNames.INTEGER);
                        simpleValue.setStringValue(Long.toString(value.getFloatValue().longValue()));
                        break;
                    case STRING:
                    case MULTI_LINE:
                    case XML_TEXT:
                        simpleValue.setValueType(SimpleTypeNames.STRING);
                        if (ExternalDocsURLCustomPropertyRenderer.URI.equals(value.getPropertyURI()))
                        {
                            String link = value.getEitherStringValue();
                            try
                            {
                                URI uri = new URI(link);
                                if (uri.getScheme().equals("file"))
                                {
                                    File f = new File(uri);
                                    if (f.exists())
                                    {
                                        link = _urlRewriter.rewriteURL(f, null, null);
                                    }
                                }
                            }
                            catch (URISyntaxException e) {}
                            simpleValue.setStringValue(link);
                        }
                        else
                        {
                            simpleValue.setStringValue(relativizeLSIDPropertyValue(value.getEitherStringValue(), SimpleTypeNames.STRING));
                            Domain domain = PropertyService.get().getDomain(parentContainer, value.getEitherStringValue());
                            if (domain != null)
                            {
                                addDomain(domain);
                            }
                        }
                        break;
                    default:
                        logProgress("Warning: skipping export of " + value.getName() + " -- unknown type " + value.getPropertyType());
                }
            }
        }

        if (!addedProperty)
        {
            return null;
        }
        return result;
    }

    public void dumpXML(OutputStream out) throws IOException, ExperimentException
    {
        XmlOptions validateOptions = new XmlOptions();
        ArrayList<XmlError> errorList = new ArrayList<XmlError>();
        validateOptions.setErrorListener(errorList);
        if (!_document.validate(validateOptions))
        {
            StringBuilder sb = new StringBuilder();
            for (XmlError error : errorList)
            {
                sb.append("Schema validation error: ");
                sb.append(error.getMessage());
                sb.append("\n");
                sb.append("Location of invalid XML: ");
                sb.append(error.getCursorLocation().xmlText());
                sb.append("\n");
            }
            throw new ExperimentException("Failed to create a valid XML file\n" + sb.toString());
        }

        XmlOptions options = new XmlOptions();
        options.setSaveAggressiveNamespaces();
        options.setSavePrettyPrint();

        XmlCursor cursor = _document.newCursor();
        if (cursor.toFirstChild())
        {
          cursor.setAttributeText(new QName("http://www.w3.org/2001/XMLSchema-instance","schemaLocation"), ExperimentService.SCHEMA_LOCATION);
        }

        _document.save(out, options);
    }

    public void write(OutputStream out) throws IOException, ExperimentException
    {
        ZipOutputStream zOut = null;
        try
        {
            zOut = new ZipOutputStream(out);
            ZipEntry xmlEntry = new ZipEntry(_xarXmlFileName);
            zOut.putNextEntry(xmlEntry);
            logProgress("Adding XAR XML to archive");
            dumpXML(zOut);

            zOut.closeEntry();

            for (URLRewriter.FileInfo fileInfo : _urlRewriter.getFileInfos())
            {
                if (fileInfo.hasContentToExport())
                {
                    logProgress("Adding data file to archive: " + fileInfo.getName());
                    ZipEntry fileEntry = new ZipEntry(fileInfo.getName());
                    zOut.putNextEntry(fileEntry);

                    fileInfo.writeFile(zOut);
                    zOut.closeEntry();
                }
            }
        }
        catch (Exception e)
        {
            // insert the stack trace into the zip file
            ZipEntry errorEntry = new ZipEntry("error.log");
            zOut.putNextEntry(errorEntry);

            final PrintStream ps = new PrintStream(zOut, true);
            ps.println("Failed to complete export of the XAR file: ");
            e.printStackTrace(ps);
            zOut.closeEntry();
        }
        finally
        {
            if (zOut != null) { try { zOut.close(); } catch (IOException e) {} }
        }
    }

    private ContactType getContactType(String parentLSID, Container parentContainer) throws SQLException, ExperimentException
    {
        Map<String, Object> parentProperties = OntologyManager.getProperties(parentLSID);
        Object contactLSIDObject = parentProperties.get(XarReader.CONTACT_PROPERTY);
        if (!(contactLSIDObject instanceof String))
        {
            return null;
        }
        String contactLSID = (String)contactLSIDObject;
        Map<String, Object> contactProperties = OntologyManager.getProperties(contactLSID);

        Object contactIdObject = contactProperties.get(XarReader.CONTACT_ID_PROPERTY);
        Object emailObject = contactProperties.get(XarReader.CONTACT_EMAIL_PROPERTY);
        Object firstNameObject = contactProperties.get(XarReader.CONTACT_FIRST_NAME_PROPERTY);
        Object lastNameObject = contactProperties.get(XarReader.CONTACT_LAST_NAME_PROPERTY);

        ContactType contactType = ContactType.Factory.newInstance();
        if (contactIdObject instanceof String)
        {
            contactType.setContactId((String)contactIdObject);
        }
        if (emailObject instanceof String)
        {
            contactType.setEmail((String)emailObject);
        }
        if (firstNameObject instanceof String)
        {
            contactType.setFirstName((String)firstNameObject);
        }
        if (lastNameObject instanceof String)
        {
            contactType.setLastName((String)lastNameObject);
        }
        PropertyCollectionType properties = getProperties(contactLSID, parentContainer, XarReader.CONTACT_ID_PROPERTY, XarReader.CONTACT_EMAIL_PROPERTY, XarReader.CONTACT_FIRST_NAME_PROPERTY, XarReader.CONTACT_LAST_NAME_PROPERTY);
        if (properties != null)
        {
            contactType.setProperties(properties);
        }
        return contactType;
    }

    public Document getDOMDocument()
    {
        return (Document)_document.getDomNode();
    }

    public ExperimentArchiveDocument getXMLBean()
    {
        return _document;
    }
}
