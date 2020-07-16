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

package org.labkey.experiment;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlOptions;
import org.fhcrc.cpas.exp.xml.*;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.AbstractParameter;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.ProtocolParameter;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpDataProtocolInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpMaterialProtocolInput;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpProtocolInput;
import org.labkey.api.exp.api.ExpProtocolInputCriteria;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Pair;
import org.labkey.experiment.api.Data;
import org.labkey.experiment.api.DataInput;
import org.labkey.experiment.api.ExpDataImpl;
import org.labkey.experiment.api.ExpExperimentImpl;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.Experiment;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.Material;
import org.labkey.experiment.api.MaterialInput;
import org.labkey.experiment.api.ProtocolActionPredecessor;
import org.labkey.experiment.xar.AutoFileLSIDReplacer;
import org.labkey.experiment.xar.XarExportSelection;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * User: jeckels
 * Date: Nov 21, 2005
 */
public class XarExporter
{
    private final URLRewriter _urlRewriter;
    private final User _user;
    private final ExperimentArchiveDocument _document;
    private final ExperimentArchiveType _archive;

    private String _xarXmlFileName = "experiment.xar.xml";

    /**
     * As we export objects to XML, we may transform the LSID so we need to remember the
     * original LSIDs
     */
    private Map<String, Set<String>> _experimentLSIDToRunLSIDs = new HashMap<>();
    private Set<String> _experimentRunLSIDs = new HashSet<>();
    private Set<String> _protocolLSIDs = new HashSet<>();
    private Set<String> _inputDataLSIDs = new HashSet<>();
    private Set<String> _inputMaterialLSIDs = new HashSet<>();

    private Set<String> _sampleSetLSIDs = new HashSet<>();
    /** Use a TreeMap so that we order domains by their RowIds, see issue 22459 */
    private Map<Integer, Domain> _domainsToAdd = new TreeMap<>();
    private Set<String> _domainLSIDs = new HashSet<>();
    private Set<Integer> _expDataIDs = new HashSet<>();

    private final LSIDRelativizer.RelativizedLSIDs _relativizedLSIDs;
    private Logger _log;

    private boolean _includeXML = true;

    // it is possible to export runs from different assays, for caching purposes this is the assayProvider of the current/previous run
    AssayProvider assayProvider = null;
    // container of current/previous run
    Container runContainer = null;
    // assayCallbacks corresponding to the this.assayProvider/this.runContainer
    AssayProvider.XarCallbacks assayCallbacks = null;


    public XarExporter(LSIDRelativizer lsidRelativizer, URLRewriter urlRewriter, User user)
    {
        _relativizedLSIDs = new LSIDRelativizer.RelativizedLSIDs(lsidRelativizer);
        _urlRewriter = urlRewriter;
        _user = user;

        _document = ExperimentArchiveDocument.Factory.newInstance();
        _archive = _document.addNewExperimentArchive();
    }

    public XarExporter(LSIDRelativizer lsidRelativizer, XarExportSelection selection, User user, String xarXmlFileName, Logger log) throws ExperimentException
    {
        this(lsidRelativizer, selection.createURLRewriter(), user);
        _log = log;

        selection.addContent(this);

        if (xarXmlFileName != null)
        {
            setXarXmlFileName(xarXmlFileName);
        }
        setFileIncludes(selection.isIncludeXarXml());
    }

    private void logProgress(String message)
    {
        if (_log != null)
        {
            _log.info(message);
        }
    }

    public void setFileIncludes(boolean xarXML)
    {
        _includeXML = xarXML;
    }

    public void setXarXmlFileName(String fileName)
    {
        _xarXmlFileName = fileName;
    }

    public void addExpData(ExpData data) throws ExperimentException
    {
        if(_expDataIDs.contains(data.getRowId()))
        {
            return;
        }
        logProgress("Adding experiment data " + data.getRowId());
        _expDataIDs.add(data.getRowId());

        ArchiveURLRewriter u = (ArchiveURLRewriter)_urlRewriter;

        Path rootPath = FileUtil.getAbsoluteCaseSensitivePath(data.getContainer(), data.getFilePath().getParent().toUri());
        u.addFile(data, data.getFilePath(), "", rootPath, data.findDataHandler(), _user);
    }

    public void addExperimentRun(ExpRun run) throws ExperimentException
    {
        if (_experimentRunLSIDs.contains(run.getLSID()))
        {
            return;
        }
        logProgress("Adding experiment run " + run.getLSID());
        _experimentRunLSIDs.add(run.getLSID());

        ExpExperiment batch = run.getBatch();
        if (batch != null)
        {
            addExperiment((ExpExperimentImpl)batch);
        }

        ExperimentArchiveType.ExperimentRuns runs = _archive.getExperimentRuns();
        if (runs == null)
        {
            runs = _archive.addNewExperimentRuns();
        }
        ExperimentRunType xRun = runs.addNewExperimentRun();
        xRun.setAbout(_relativizedLSIDs.relativize(run.getLSID()));

        // The XAR schema only supports one experiment (run group) association per run, so choose the first one that it belongs to
        // At the moment, the UI only lets you export one experiment (run group) at a time anyway
        for (Map.Entry<String,Set<String>> entry : _experimentLSIDToRunLSIDs.entrySet())
        {
            if (entry.getValue().contains(run.getLSID()))
            {
                xRun.setExperimentLSID(_relativizedLSIDs.relativize(entry.getKey()));
                break;
            }
        }

        if (run.getComments() != null)
        {
            xRun.setComments(run.getComments());
        }
        xRun.setName(run.getName());
        PropertyCollectionType properties = getProperties(run.getLSID(), run.getContainer());
        if (null != properties)
        {
            xRun.setProperties(properties);
        }
        ExpProtocol protocol = run.getProtocol();
        xRun.setProtocolLSID(_relativizedLSIDs.relativize(protocol.getLSID()));

        addProtocol(protocol, true);

        Set<Map.Entry<ExpData, String>> inputData = run.getDataInputs().entrySet();
        ExperimentArchiveType.StartingInputDefinitions inputDefs = _archive.getStartingInputDefinitions();
        if (inputData.size() > 0 && inputDefs == null)
        {
            inputDefs = _archive.addNewStartingInputDefinitions();
        }
        for (Map.Entry<ExpData, String> entry : inputData)
        {
            ExpData data = entry.getKey();
            if (!_inputDataLSIDs.contains(data.getLSID()))
            {
                _inputDataLSIDs.add(data.getLSID());

                DataBaseType xData = inputDefs.addNewData();
                populateData(xData, data, entry.getValue(), run);
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
                populateMaterial(xMaterial, new ExpMaterialImpl(material));
            }
        }

        ExperimentRunType.ProtocolApplications xApplications = xRun.addNewProtocolApplications();
        for (ExpProtocolApplication application : ExperimentService.get().getExpProtocolApplicationsForRun(run.getRowId()))
        {
            addProtocolApplication(application, run, xApplications);
        }

        // get AssayService.get().getProvider(run).getXarCallbacks().beforeXarExportRun() with simple attempt at caching for common case
        if (null != AssayService.get())
        {
            AssayProvider ap = AssayService.get().getProvider(run);
            if (null != ap)
            {
                if (null == assayCallbacks || assayProvider != ap || runContainer != run.getContainer())
                {
                    this.assayProvider = ap;
                    this.runContainer = run.getContainer();
                    this.assayCallbacks = assayProvider.getXarCallbacks(_user, runContainer);
                }
                if (null != assayCallbacks)
                    assayCallbacks.beforeXarExportRun(run, xRun);
            }
        }
    }

    private Calendar getGregorianCalender(Date date)
    {
        Calendar cal = new GregorianCalendar();
        cal.setTime(date);
        return cal;
    }

    private void addProtocolApplication(ExpProtocolApplication application, ExpRun run, ExperimentRunType.ProtocolApplications xApplications)
        throws ExperimentException
    {
        ProtocolApplicationBaseType xApplication = xApplications.addNewProtocolApplication();
        xApplication.setAbout(_relativizedLSIDs.relativize(application.getLSID()));
        xApplication.setActionSequence(application.getActionSequence());
        Date date = application.getActivityDate();
        if (date != null)
        {
            xApplication.setActivityDate(getGregorianCalender(date));
        }
        if (application.getComments() != null)
        {
            xApplication.setComments(application.getComments());
        }
        xApplication.setCpasType(application.getApplicationType().toString());
        date = application.getStartTime();
        if (date != null)
        {
            xApplication.setStartTime(getGregorianCalender(date));
        }
        date = application.getEndTime();
        if (date != null)
        {
            xApplication.setEndTime(getGregorianCalender(date));
        }

        Integer recordCount = application.getRecordCount();
        if (recordCount != null)
        {
            xApplication.setRecordCount(recordCount);
        }

        InputOutputRefsType inputRefs = null;
        List<Data> inputDataRefs = ExperimentServiceImpl.get().getDataInputReferencesForApplication(application.getRowId());
        List<DataInput> dataInputs = ExperimentServiceImpl.get().getDataInputsForApplication(application.getRowId());
        for (Data data : inputDataRefs)
        {
            if (inputRefs == null)
            {
                inputRefs = xApplication.addNewInputRefs();
            }
            InputOutputRefsType.DataLSID dataLSID = inputRefs.addNewDataLSID();
            dataLSID.setStringValue(_relativizedLSIDs.relativize(data.getLSID()));
            String roleName = null;
            for (DataInput dataInput : dataInputs)
            {
                if (dataInput.getDataId() == data.getRowId())
                {
                    roleName = dataInput.getRole();

                    if (dataInput.getProtocolInputId() != null)
                    {
                        ExpProtocolInput protocolInput = ExperimentServiceImpl.get().getProtocolInput(dataInput.getProtocolInputId());
                        if (protocolInput != null)
                        {
                            Lsid lsid = Lsid.parse(protocolInput.getLSID());
                            dataLSID.setProtocolInput(lsid.getObjectId());
                        }
                    }
                    break;
                }
            }
            if (roleName != null)
            {
                dataLSID.setRoleName(roleName);
            }

            ExpDataImpl expData = new ExpDataImpl(data);
            String url = _urlRewriter.rewriteURL(expData.getFilePath(), expData, roleName, run, _user);
            if (AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION.equals(dataLSID.getStringValue()))
            {
                if (url != null && !"".equals(url))
                {
                    dataLSID.setDataFileUrl(url);
                }
            }
        }

        List<Material> inputMaterial = ExperimentServiceImpl.get().getMaterialInputReferencesForApplication(application.getRowId());
        List<MaterialInput> materialInputs = ExperimentServiceImpl.get().getMaterialInputsForApplication(application.getRowId());
        for (Material material : inputMaterial)
        {
            if (inputRefs == null)
            {
                inputRefs = xApplication.addNewInputRefs();
            }
            InputOutputRefsType.MaterialLSID materialLSID = inputRefs.addNewMaterialLSID();
            materialLSID.setStringValue(_relativizedLSIDs.relativize(material.getLSID()));

            for (MaterialInput materialInput : materialInputs)
            {
                if (materialInput.getMaterialId() == material.getRowId())
                {
                    if (materialInput.getRole() != null)
                    {
                        materialLSID.setRoleName(materialInput.getRole());
                    }

                    if (materialInput.getProtocolInputId() != null)
                    {
                        ExpProtocolInput protocolInput = ExperimentServiceImpl.get().getProtocolInput(materialInput.getProtocolInputId());
                        if (protocolInput != null)
                        {
                            Lsid lsid = Lsid.parse(protocolInput.getLSID());
                            materialLSID.setProtocolInput(lsid.getObjectId());
                        }
                    }
                    break;
                }
            }
        }

        xApplication.setName(application.getName());

        ProtocolApplicationBaseType.OutputDataObjects outputDataObjects = xApplication.addNewOutputDataObjects();
        List<? extends ExpData> outputData = application.getOutputDatas();
        if (!outputData.isEmpty())
        {
            for (ExpData data : outputData)
            {
                // Issue 31727: When data is imported via the API, no file is created for the data.  We want to
                // include a file path in the export, though.  We use "Data" + rowId of the data object as the file name
                // to ensure that the path won't collide with other output data from exported runs in the xar archive.
                if (data.getDataFileUrl() == null && data.isFinalRunOutput())
                    data.setDataFileURI(run.getFilePathRootPath().resolve("Data" + data.getRowId()).toUri());
                DataBaseType xData = outputDataObjects.addNewData();
                populateData(xData, data, null, run);
            }
        }

        ProtocolApplicationBaseType.OutputMaterials outputMaterialObjects = xApplication.addNewOutputMaterials();
        for (ExpMaterial material : application.getOutputMaterials())
        {
            MaterialBaseType xMaterial = outputMaterialObjects.addNewMaterial();
            populateMaterial(xMaterial, material);
        }

        ProvenanceService pvs = ProvenanceService.get();
        if (pvs != null)
        {
            var provURIs = pvs.getProvenanceObjectUris(application.getRowId());
            if (!provURIs.isEmpty())
            {
                ProtocolApplicationBaseType.ProvenanceMap xProvMap = xApplication.addNewProvenanceMap();
                for (Pair<String, String> pair : provURIs)
                {
                    if (StringUtils.isEmpty(pair.first) && StringUtils.isEmpty(pair.second))
                        continue;

                    var objectRefs = xProvMap.addNewObjectRefs();
                    if (!StringUtils.isEmpty(pair.first))
                    {
                        objectRefs.setFrom(_relativizedLSIDs.relativize(pair.first));
                    }

                    if (!StringUtils.isEmpty(pair.second))
                    {
                        objectRefs.setTo(_relativizedLSIDs.relativize(pair.second));
                    }
                }
            }
        }

        PropertyCollectionType appProperties = getProperties(application.getLSID(), run.getContainer());
        if (appProperties != null)
        {
            xApplication.setProperties(appProperties);
        }

        List<ProtocolApplicationParameter> parameters = ExperimentService.get().getProtocolApplicationParameters(application.getRowId());
        if (!parameters.isEmpty())
        {
            SimpleValueCollectionType xParameters = xApplication.addNewProtocolApplicationParameters();
            for (ProtocolApplicationParameter parameter : parameters)
            {
                SimpleValueType xValue = xParameters.addNewSimpleVal();
                populateXmlBeanValue(xValue, parameter);
            }
        }

        xApplication.setProtocolLSID(_relativizedLSIDs.relativize(application.getProtocol().getLSID()));
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
            return _relativizedLSIDs.relativize(value);
        }
        else
        {
            return value;
        }
    }

    private void populateMaterial(MaterialBaseType xMaterial, ExpMaterial material) throws ExperimentException
    {
        logProgress("Adding material " + material.getLSID());
        addSampleType(material.getCpasType());
        xMaterial.setAbout(_relativizedLSIDs.relativize(material.getLSID()));
        xMaterial.setCpasType(material.getCpasType() == null ? ExpMaterial.DEFAULT_CPAS_TYPE : _relativizedLSIDs.relativize(material.getCpasType()));
        xMaterial.setName(material.getName());

        Map<String, ObjectProperty> objectProperties = material.getObjectProperties();
        PropertyCollectionType materialProperties = getProperties(objectProperties, material.getContainer());
        if (materialProperties != null)
        {
            xMaterial.setProperties(materialProperties);
        }
    }

    private void addSampleType(String cpasType)
    {
        if (_sampleSetLSIDs.contains(cpasType))
        {
            return;
        }
        _sampleSetLSIDs.add(cpasType);
        ExpSampleType sampleType = SampleTypeService.get().getSampleType(cpasType);
        addSampleType(sampleType);
    }

    public void addSampleType(ExpSampleType sampleType)
    {
        if (sampleType == null)
        {
            return;
        }
        if (_archive.getSampleSets() == null)
        {
            _archive.addNewSampleSets();
        }
        SampleSetType xSampleSet = _archive.getSampleSets().addNewSampleSet();
        xSampleSet.setAbout(_relativizedLSIDs.relativize(sampleType.getLSID()));
        xSampleSet.setMaterialLSIDPrefix(_relativizedLSIDs.relativize(sampleType.getMaterialLSIDPrefix()));
        xSampleSet.setName(sampleType.getName());
        if (sampleType.getDescription() != null)
        {
            xSampleSet.setDescription(sampleType.getDescription());
        }
        if (!sampleType.hasNameExpression())
        {
            for (DomainProperty keyCol : sampleType.getIdCols())
            {
                xSampleSet.addKeyField(getPropertyName(keyCol));
            }
        }
        if (sampleType.getParentCol() != null)
        {
            xSampleSet.setParentField(getPropertyName(sampleType.getParentCol()));
        }
        if (sampleType.hasNameExpression())
        {
            xSampleSet.setNameExpression(sampleType.getNameExpression());
        }
        if (sampleType.getLabelColor() != null)
        {
            xSampleSet.setLabelColor(sampleType.getLabelColor());
        }

        Domain domain = sampleType.getDomain();
        queueDomain(domain);
    }

    // Return the "name" portion of the propertyURI after the hash
    private String getPropertyName(DomainProperty dp)
    {
        String name = dp.getName();
        String propertyURI = dp.getPropertyURI();
        int i = propertyURI.indexOf('#');
        if (i != -1)
            name = propertyURI.substring(i+1);

        return name;
    }

    private void queueDomain(Domain domain)
    {
        // Save them up so that we are sure they are written out in the proper order
        _domainsToAdd.put(domain.getTypeId(), domain);
    }

    private void addPropertyDescriptor(DomainDescriptorType xDomain, DomainProperty domainProp, Object defaultValue) throws ExperimentException
    {
        PropertyDescriptorType xProp = xDomain.addNewPropertyDescriptor();
        if (domainProp.getDescription() != null)
        {
            xProp.setDescription(domainProp.getDescription());
        }
        xProp.setName(domainProp.getName());
        // Deal with legacy property URIs that don't have % in the name part properly encoded
        xProp.setPropertyURI(_relativizedLSIDs.relativize(Lsid.fixupPropertyURI(domainProp.getPropertyURI())));
        PropertyDescriptor prop = domainProp.getPropertyDescriptor();
        if (prop.getConceptURI() != null)
        {
            xProp.setConceptURI(prop.getConceptURI());
        }
        xProp.setRequired(domainProp.isRequired());
        if (domainProp.getDescription() != null)
        {
            xProp.setDescription(domainProp.getDescription());
        }
        if (domainProp.getFormat() != null)
        {
            xProp.setFormat(domainProp.getFormat());
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
        if (prop.getSemanticType() != null)
        {
            xProp.setSemanticType(prop.getSemanticType());
        }
        if (prop.getURL() != null)
        {
            xProp.setURL(prop.getURL().toString());
        }
        if (!prop.getImportAliasSet().isEmpty())
        {
            PropertyDescriptorType.ImportAliases xImportAliases = xProp.addNewImportAliases();
            for (String importAlias : prop.getImportAliasSet())
            {
                xImportAliases.addImportAlias(importAlias);
            }
        }
        Lookup lookup = domainProp.getLookup();
        if (lookup != null)
        {
            PropertyDescriptorType.FK xFK = xProp.addNewFK();
            xFK.setQuery(lookup.getQueryName());
            xFK.setSchema(lookup.getSchemaName());
            if (lookup.getContainer() != null && !lookup.getContainer().equals(prop.getContainer()))
            {
                // Export the lookup's target path if it's set and it's not the same as the property descriptor's container
                xFK.setFolderPath(lookup.getContainer().getPath());
            }
        }

        if (domainProp.getDefaultValueTypeEnum() != null)
        {
            switch (domainProp.getDefaultValueTypeEnum())
            {
                case FIXED_EDITABLE:
                    xProp.setDefaultType(DefaultType.EDITABLE_DEFAULT);
                    break;
                case FIXED_NON_EDITABLE:
                    xProp.setDefaultType(DefaultType.FIXED_VALUE);
                    break;
                case LAST_ENTERED:
                    xProp.setDefaultType(DefaultType.LAST_ENTERED);
                    break;
                default:
                    throw new ExperimentException("Unsupported default value type: " + domainProp.getDefaultValueTypeEnum());
            }
        }

        if (defaultValue != null)
        {
            if (defaultValue instanceof Date)
            {
                xProp.setDefaultValue(DateUtil.formatDateTime((Date)defaultValue, AbstractParameter.SIMPLE_FORMAT_PATTERN));
            }
            else
            {
                xProp.setDefaultValue(defaultValue.toString());
            }
        }

        xProp.setHidden(domainProp.isHidden());
        xProp.setShownInDetailsView(domainProp.isShownInDetailsView());
        xProp.setShownInInsertView(domainProp.isShownInInsertView());
        xProp.setShownInUpdateView(domainProp.isShownInUpdateView());
        xProp.setMvEnabled(domainProp.isMvEnabled());

        for (IPropertyValidator validator : domainProp.getValidators())
        {
            addPropertyValidator(xProp, validator);
        }

        //Only export scale if storage field is a string type
        if (domainProp.getRangeURI().equals("http://www.w3.org/2001/XMLSchema#string"))
            xProp.setScale(domainProp.getScale());

        ConditionalFormat.convertToXML(domainProp.getConditionalFormats(), xProp);

        xProp.setMeasure(domainProp.isMeasure());
        xProp.setDimension(domainProp.isDimension());
        xProp.setRecommendedVariable(domainProp.isRecommendedVariable());
    }

    private PropertyValidatorType addPropertyValidator(PropertyDescriptorType xProp, IPropertyValidator validator)
    {
        PropertyValidatorType xValidator = xProp.addNewPropertyValidator();
        xValidator.setName(validator.getName());
        xValidator.setTypeURI(validator.getTypeURI());
        if (validator.getDescription() != null)
        {
            xValidator.setDescription(validator.getDescription());
        }
        if (validator.getErrorMessage() != null)
        {
            xValidator.setErrorMessage(validator.getErrorMessage());
        }
        if (validator.getExpressionValue() != null)
        {
            xValidator.setExpression(validator.getExpressionValue());
        }
        for (Map.Entry<String, String> property : validator.getProperties().entrySet())
        {
            PropertyValidatorPropertyType xProperty = xValidator.addNewProperty();
            xProperty.setName(property.getKey());
            xProperty.setValue(property.getValue());
        }
        return xValidator;
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

    private void populateData(DataBaseType xData, ExpData data, @Nullable String role, ExpRun run) throws ExperimentException
    {
        logProgress("Adding data " + data.getLSID());
        xData.setName(data.getName());
        xData.setAbout(_relativizedLSIDs.relativize(data));
        xData.setCpasType(data.getCpasType() == null ? ExpData.DEFAULT_CPAS_TYPE : _relativizedLSIDs.relativize(data.getCpasType()));

        Path path = data.getFilePath();
        if (path != null)
        {
            String url = _urlRewriter.rewriteURL(path, data, role, run, _user);
            if (url != null)
                xData.setDataFileUrl(url);
        }

        // Add the original URL as a property on the data object
        // so that it's easier to figure out links between files later, since
        // the URLs have all been rewritten
        PropertyCollectionType dataProperties = getProperties(data.getLSID(), data.getContainer());
        if (!StringUtils.isBlank(data.getDataFileUrl()) && !StringUtils.equals(xData.getDataFileUrl(), data.getDataFileUrl()))
            dataProperties = addOriginalURLProperty(dataProperties, data.getDataFileUrl());
        if (dataProperties != null)
            xData.setProperties(dataProperties);
    }

    public void addProtocol(ExpProtocol protocol, boolean includeChildren) throws ExperimentException
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

        xProtocol.setAbout(_relativizedLSIDs.relativize(protocol.getLSID()));
        xProtocol.setApplicationType(protocol.getApplicationType().toString());
        ContactType contactType = getContactType(protocol.getLSID(), protocol.getContainer());
        if (contactType != null)
        {
            xProtocol.setContact(contactType);
        }
        if (protocol.getInstrument() != null)
        {
            xProtocol.setInstrument(protocol.getInstrument());
        }
        xProtocol.setName(protocol.getName());
        PropertyCollectionType properties = getProperties(protocol.getLSID(), protocol.getContainer(), XarReader.CONTACT_PROPERTY);
        if (properties != null)
        {
            xProtocol.setProperties(properties);
        }

        if (protocol.getMaxInputDataPerInstance() != null)
        {
            xProtocol.setMaxInputDataPerInstance(protocol.getMaxInputDataPerInstance().intValue());
        }

        if (protocol.getMaxInputMaterialPerInstance() != null)
        {
            xProtocol.setMaxInputMaterialPerInstance(protocol.getMaxInputMaterialPerInstance().intValue());
        }

        if (protocol.getOutputDataPerInstance() != null)
        {
            xProtocol.setOutputDataPerInstance(protocol.getOutputDataPerInstance().intValue());
        }

        if (protocol.getOutputMaterialPerInstance() != null)
        {
            xProtocol.setOutputMaterialPerInstance(protocol.getOutputMaterialPerInstance().intValue());
        }

        xProtocol.setOutputDataType(protocol.getOutputDataType());
        xProtocol.setOutputMaterialType(protocol.getOutputMaterialType());

        ProtocolType.Inputs xInputs = null;
        Collection<? extends ExpMaterialProtocolInput> materialProtocolInputs = protocol.getMaterialProtocolInputs();
        if (!materialProtocolInputs.isEmpty())
        {
            xInputs = xProtocol.addNewInputs();
            for (ExpMaterialProtocolInput pi : materialProtocolInputs)
            {
                MaterialProtocolInputType xMpi = xInputs.addNewMaterialInput();
                addMaterialProtocolInput(xMpi, pi);
            }
        }

        Collection<? extends ExpDataProtocolInput> dataProtocolInputs = protocol.getDataProtocolInputs();
        if (!dataProtocolInputs.isEmpty())
        {
            if (xInputs == null)
                xInputs = xProtocol.addNewInputs();
            for (ExpDataProtocolInput pi : dataProtocolInputs)
            {
                DataProtocolInputType xDpi = xInputs.addNewDataInput();
                addDataProtocolInput(xDpi, pi);
            }
        }

        ProtocolType.Outputs xOutputs = null;
        Collection<? extends ExpMaterialProtocolInput> materialProtocolOutputs = protocol.getMaterialProtocolOutputs();
        if (!materialProtocolOutputs.isEmpty())
        {
            xOutputs = xProtocol.addNewOutputs();
            for (ExpMaterialProtocolInput pi : materialProtocolOutputs)
            {
                MaterialProtocolInputType xMpo = xOutputs.addNewMaterialOutput();
                addMaterialProtocolInput(xMpo, pi);
            }
        }

        Collection<? extends ExpDataProtocolInput> dataProtocolOutputs = protocol.getDataProtocolOutputs();
        if (!dataProtocolOutputs.isEmpty())
        {
            if (xOutputs == null)
                xOutputs = xProtocol.addNewOutputs();
            for (ExpDataProtocolInput pi : dataProtocolOutputs)
            {
                DataProtocolInputType xDpo = xOutputs.addNewDataOutput();
                addDataProtocolInput(xDpo, pi);
            }
        }

        if (protocol.getProtocolDescription() != null)
        {
            xProtocol.setProtocolDescription(protocol.getProtocolDescription());
        }
        if (protocol.getSoftware() != null)
        {
            xProtocol.setSoftware(protocol.getSoftware());
        }

        Map<String, ProtocolParameter> params = protocol.getProtocolParameters();
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
            List<? extends ExpProtocolAction> protocolActions = protocol.getSteps();

            if (protocolActions.size() > 0)
            {
                ExperimentArchiveType.ProtocolActionDefinitions actionDefs = _archive.getProtocolActionDefinitions();
                if (actionDefs == null)
                {
                    actionDefs = _archive.addNewProtocolActionDefinitions();
                }

                ProtocolActionSetType actionSet = actionDefs.addNewProtocolActionSet();
                actionSet.setParentProtocolLSID(_relativizedLSIDs.relativize(protocol.getLSID()));
                for (ExpProtocolAction action : protocolActions)
                {
                    addProtocolAction(action, actionSet);
                }
            }
        }
    }

    private void addDataProtocolInput(DataProtocolInputType xDpi, ExpDataProtocolInput pi) throws ExperimentException
    {
        xDpi.setName(pi.getName());
        Lsid lsid = Lsid.parse(pi.getLSID());
        xDpi.setGuid(lsid.getObjectId());

        ExpDataClass dc = pi.getType();
        if (dc != null)
            xDpi.setDataClass(dc.getName());

        ExpProtocolInputCriteria criteria = pi.getCriteria();
        if (criteria != null)
        {
            ProtocolInputCriteria xCriteria = xDpi.addNewCriteria();
            xCriteria.setType(criteria.getTypeName());
            xCriteria.setStringValue(criteria.serializeConfig());
        }

        // min occurs defaults to 1
        if (pi.getMinOccurs() != 1)
            xDpi.setMinOccurs(pi.getMinOccurs());

        if (pi.getMaxOccurs() != null)
            xDpi.setMaxOccurs(pi.getMaxOccurs());

        PropertyCollectionType properties = getProperties(pi.getLSID(), pi.getContainer());
        if (properties != null)
        {
            xDpi.setProperties(properties);
        }
    }

    private void addMaterialProtocolInput(MaterialProtocolInputType xMpi, ExpMaterialProtocolInput pi) throws ExperimentException
    {
        xMpi.setName(pi.getName());
        Lsid lsid = Lsid.parse(pi.getLSID());
        xMpi.setGuid(lsid.getObjectId());

        ExpSampleType st = pi.getType();
        if (st != null)
            xMpi.setSampleSet(st.getName());

        ExpProtocolInputCriteria criteria = pi.getCriteria();
        if (criteria != null)
        {
            ProtocolInputCriteria xCriteria = xMpi.addNewCriteria();
            xCriteria.setType(criteria.getTypeName());
            xCriteria.setStringValue(criteria.serializeConfig());
        }

        // min occurs defaults to 1
        if (pi.getMinOccurs() != 1)
            xMpi.setMinOccurs(pi.getMinOccurs());

        if (pi.getMaxOccurs() != null)
            xMpi.setMaxOccurs(pi.getMaxOccurs());

        PropertyCollectionType properties = getProperties(pi.getLSID(), pi.getContainer());
        if (properties != null)
        {
            xMpi.setProperties(properties);
        }
    }

    private void addProtocolAction(ExpProtocolAction protocolAction, ProtocolActionSetType actionSet) throws ExperimentException
    {
        ExpProtocol protocol = protocolAction.getChildProtocol();
        ExpProtocol parentProtocol = protocolAction.getParentProtocol();
        String lsid = protocol.getLSID();

        ProtocolActionType xProtocolAction = actionSet.addNewProtocolAction();
        xProtocolAction.setActionSequence(protocolAction.getActionSequence());
        xProtocolAction.setChildProtocolLSID(_relativizedLSIDs.relativize(lsid));

        for (ProtocolActionPredecessor predecessor : ExperimentServiceImpl.get().getProtocolActionPredecessors(parentProtocol.getLSID(), lsid))
        {
            ProtocolActionType.PredecessorAction xPredecessor = xProtocolAction.addNewPredecessorAction();
            xPredecessor.setActionSequenceRef(predecessor.getPredecessorSequence());
        }

        addProtocol(protocol, true);
    }

    public void addExperiment(ExpExperimentImpl exp) throws ExperimentException
    {
        Experiment experiment = exp.getDataObject();
        if (_experimentLSIDToRunLSIDs.containsKey(experiment.getLSID()))
        {
            return;
        }
        logProgress("Adding experiment " + experiment.getLSID());
        Set<String> runLsids = new HashSet<>();
        for (ExpRun expRun : exp.getRuns())
        {
            runLsids.add(expRun.getLSID());
        }
        _experimentLSIDToRunLSIDs.put(experiment.getLSID(), runLsids);

        ExperimentType xExperiment = _archive.addNewExperiment();
        xExperiment.setAbout(_relativizedLSIDs.relativize(experiment.getLSID()));
        if (experiment.getBatchProtocolId() != null)
        {
            ExpProtocol protocol = ExperimentService.get().getExpProtocol(experiment.getBatchProtocolId());
            if (protocol != null)
            {
                xExperiment.setBatchProtocolLSID(_relativizedLSIDs.relativize(protocol.getLSID()));
            }
        }
        if (experiment.getComments() != null)
        {
            xExperiment.setComments(experiment.getComments());
        }
        ContactType contactType = getContactType(experiment.getLSID(), experiment.getContainer());
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

        PropertyCollectionType xProperties = getProperties(experiment.getLSID(), experiment.getContainer(), XarReader.CONTACT_PROPERTY);
        if (xProperties != null)
        {
            xExperiment.setProperties(xProperties);
        }
    }


    private PropertyCollectionType getProperties(String lsid, Container parentContainer, String... ignoreProperties) throws ExperimentException
    {
        Map<String, ObjectProperty> properties = getObjectProperties(parentContainer, lsid);
        return getProperties(properties, parentContainer, ignoreProperties);
    }


    private PropertyCollectionType getProperties(Map<String, ObjectProperty> properties, Container parentContainer, String... ignoreProperties) throws ExperimentException
    {
        Set<String> ignoreSet = new HashSet<>(Arrays.asList(ignoreProperties));

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
                propDec.setOntologyEntryURI(_relativizedLSIDs.relativize(value.getPropertyURI()));
                propDec.setValueType(SimpleTypeNames.PROPERTY_URI);

                PropertyCollectionType childProperties = getProperties(value.getStringValue(), parentContainer);
                if (childProperties != null)
                {
                    subProperty.setChildProperties(childProperties);
                }
            }
            else
            {
                // check for NULL numbers
                // NOTE this code does not handle Missing Value indicators!
                switch(value.getPropertyType())
                {
                    case DOUBLE: case INTEGER: case BOOLEAN:
                        if (null == value.getFloatValue())
                            continue;
                        break;
                    case DATE_TIME:
                        if (null == value.getDateTimeValue())
                            continue;
                        break;
                    default:
                }

                SimpleValueType simpleValue = result.addNewSimpleVal();
                simpleValue.setName(value.getName());
                simpleValue.setOntologyEntryURI(_relativizedLSIDs.relativize(value.getPropertyURI()));

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
                        simpleValue.setStringValue(value.getStringValue());
                        break;
                    case INTEGER:
                        simpleValue.setValueType(SimpleTypeNames.INTEGER);
                        simpleValue.setStringValue(Long.toString(value.getFloatValue().longValue()));
                        break;
                    case BOOLEAN:
                        simpleValue.setValueType(SimpleTypeNames.BOOLEAN);
                        simpleValue.setStringValue(value.getBooleanValue() == null ? null : value.getBooleanValue().toString());
                        break;
                    case STRING:
                    case MULTI_LINE:
                    case XML_TEXT:
                        simpleValue.setValueType(SimpleTypeNames.STRING);
                        if (ExternalDocsURLCustomPropertyRenderer.URI.equals(value.getPropertyURI()))
                        {
                            String link = value.getStringValue();
                            try
                            {
                                URI uri = new URI(link);
                                if (uri.getScheme().equals("file") || FileUtil.hasCloudScheme(uri))
                                {
                                    Path path = FileUtil.getPath(parentContainer, uri);
                                    if (Files.exists(path))
                                    {
                                        link = _urlRewriter.rewriteURL(path, null, null, null, _user);
                                    }
                                }
                            }
                            catch (URISyntaxException ignored) {}
                            simpleValue.setStringValue(link);
                        }
                        else
                        {
                            simpleValue.setStringValue(relativizeLSIDPropertyValue(value.getStringValue(), SimpleTypeNames.STRING));
                            Domain domain = PropertyService.get().getDomain(parentContainer, value.getStringValue());
                            if (domain != null)
                            {
                                queueDomain(domain);
                            }

                            if (AssayPublishService.AUTO_COPY_TARGET_PROPERTY_URI.equals(value.getPropertyURI()))
                            {
                                Container autoCopyContainer = ContainerManager.getForId(value.getStringValue());
                                if (autoCopyContainer != null)
                                    simpleValue.setStringValue(autoCopyContainer.getPath());
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

    private Map<String, ObjectProperty> getObjectProperties(Container container, String lsid)
    {
        return OntologyManager.getPropertyObjects(container, lsid);
    }

    public void dumpXML(OutputStream out) throws IOException, ExperimentException
    {
        ensureDomainsWritten();

        XmlOptions validateOptions = new XmlOptions();
        ArrayList<XmlError> errorList = new ArrayList<>();
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

    private void ensureDomainsWritten() throws ExperimentException
    {
        for (Map.Entry<Integer, Domain> entry : _domainsToAdd.entrySet())
        {
            Domain domain = entry.getValue();
            if (_domainLSIDs.add(domain.getTypeURI()))
            {
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
                xDomain.setDomainURI(_relativizedLSIDs.relativize(domain.getTypeURI()));
                Map<DomainProperty, Object> defaults = DefaultValueService.get().getDefaultValues(domain.getContainer(), domain);
                for (DomainProperty domainProp : domain.getProperties())
                {
                    addPropertyDescriptor(xDomain, domainProp, defaults.get(domainProp));
                }

            }
        }
    }

    @Deprecated
    public void write(OutputStream out) throws IOException
    {
        writeAsArchive(out);
    }

    /** TODO use VFS so we can void two impl */
    public void writeAsArchive(OutputStream out) throws IOException
    {
        try (ZipOutputStream zOut = new ZipOutputStream(out))
        {
            try
            {
                if (_includeXML)
                {
                    ZipEntry xmlEntry = new ZipEntry(_xarXmlFileName);
                    zOut.putNextEntry(xmlEntry);
                    logProgress("Adding XAR XML to archive");
                    dumpXML(zOut);
                    zOut.closeEntry();
                }

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
        }
    }

    public void writeAsDirectory(File dir) throws IOException
    {
        try
        {
            if (_includeXML)
            {
                File xmlEntry = new File(dir, _xarXmlFileName);
                logProgress("Writing XAR XML file");
                try (FileOutputStream os = new FileOutputStream(xmlEntry))
                {
                    dumpXML(os);
                }
            }

            for (URLRewriter.FileInfo fileInfo : _urlRewriter.getFileInfos())
            {
                if (fileInfo.hasContentToExport())
                {
                    logProgress("Adding data file to archive: " + fileInfo.getName());
                    File fileEntry = new File(dir, fileInfo.getName());
                    fileEntry.getParentFile().mkdirs();
                    try (FileOutputStream os = new FileOutputStream(fileEntry))
                    {
                        fileInfo.writeFile(os);
                    }
                }
            }
        }
        catch (Exception e)
        {
            // insert the stack trace into the zip file
            File errorEntry = new File(dir,"error.log");

            try (FileOutputStream os = new FileOutputStream(errorEntry); PrintStream ps = new PrintStream(os))
            {
                ps.println("Failed to complete export of the XAR file: ");
                e.printStackTrace(ps);
            }
        }
    }

    private ContactType getContactType(String parentLSID, Container parentContainer) throws ExperimentException
    {
        Map<String, Object> parentProperties = getProperties(parentContainer, parentLSID);
        Object contactLSIDObject = parentProperties.get(XarReader.CONTACT_PROPERTY);
        if (!(contactLSIDObject instanceof String))
        {
            return null;
        }
        String contactLSID = (String)contactLSIDObject;
        Map<String, Object> contactProperties = getProperties(parentContainer, contactLSID);

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

    private Map<String, Object> getProperties(Container container, String contactLSID)
    {
        return OntologyManager.getProperties(container, contactLSID);
    }
}
