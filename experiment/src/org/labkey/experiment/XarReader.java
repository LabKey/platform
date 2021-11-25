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

import org.apache.commons.beanutils.ConversionException;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.fhcrc.cpas.exp.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.ProtocolApplicationParameter;
import org.labkey.api.exp.ProtocolParameter;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.XarSource;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpDataProtocolInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpMaterialProtocolInput;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpProtocolInput;
import org.labkey.api.exp.api.ExpProtocolInputCriteria;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.exp.xar.XarReaderRegistry;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.study.SpecimenService;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;
import org.labkey.experiment.api.*;
import org.labkey.experiment.api.property.DomainImpl;
import org.labkey.experiment.pipeline.MoveRunsPipelineJob;
import org.labkey.experiment.xar.AbstractXarImporter;
import org.labkey.experiment.xar.AutoFileLSIDReplacer;
import org.labkey.experiment.xar.XarExpander;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class XarReader extends AbstractXarImporter
{
    private final Set<String> _experimentLSIDs = new HashSet<>();
    private final Map<String, Integer> _propertyIdMap = new HashMap<>();
    private final Map<Integer, String> _runWorkflowTaskMap = new HashMap<>();
    /** Retain replacement info so we can wire them up after all runs have been imported */
    private final Map<Integer, String> _runReplacedByMap = new HashMap<>();

    private final List<DeferredDataLoad> _deferredDataLoads = new ArrayList<>();

    private boolean _reloadExistingRuns = false;
    private boolean _useOriginalFileUrl = false;
    private boolean _strictValidateExistingSampleType = true;

    private final List<ExpRun> _loadedRuns = new ArrayList<>();
    private final List<ProtocolApplication> _loadedProtocolApplications = new ArrayList<>();
    private final List<ExpSampleType> _loadedSampleTypes = new ArrayList<>();
    private final List<ExpDataClass> _loadedDataClasses = new ArrayList<>();

    public static final String CONTACT_PROPERTY = "terms.fhcrc.org#Contact";
    public static final String CONTACT_ID_PROPERTY = "terms.fhcrc.org#ContactId";
    public static final String CONTACT_EMAIL_PROPERTY = "terms.fhcrc.org#Email";
    public static final String CONTACT_FIRST_NAME_PROPERTY = "terms.fhcrc.org#FirstName";
    public static final String CONTACT_LAST_NAME_PROPERTY = "terms.fhcrc.org#LastName";

    public static final String ORIGINAL_URL_PROPERTY = "terms.fhcrc.org#Data.OriginalURL";
    public static final String ORIGINAL_URL_PROPERTY_NAME = "OriginalURL";
    private final List<String> _processedRunsLSIDs = new ArrayList<>();
    private AuditBehaviorType _auditBehaviorType = null;

    public XarReader(XarSource source, PipelineJob job)
    {
        super(source, job);
    }

    public void setReloadExistingRuns(boolean reloadExistingRuns)
    {
        _reloadExistingRuns = reloadExistingRuns;
    }

    public void setUseOriginalFileUrl(boolean useOriginalFileUrl)
    {
        _useOriginalFileUrl = useOriginalFileUrl;
    }

    public void setStrictValidateExistingSampleType(boolean strictValidateExistingSampleType)
    {
        _strictValidateExistingSampleType = strictValidateExistingSampleType;
    }

    public void parseAndLoad(boolean reloadExistingRuns, @Nullable AuditBehaviorType auditBehaviorType) throws ExperimentException
    {
        _reloadExistingRuns = reloadExistingRuns;
        _auditBehaviorType = auditBehaviorType;
        parseAndLoad();
    }

    public void parseAndLoad() throws ExperimentException
    {
        try
        {
            ExperimentArchiveDocument document = _xarSource.getDocument();

            // Create an XmlOptions instance and set the error listener.
            XmlOptions validateOptions = new XmlOptions();
            ArrayList<XmlError> errorList = new ArrayList<>();
            validateOptions.setErrorListener(errorList);

            // Validate the XML.
            if (!document.validate(validateOptions))
                checkValidationErrors(document, errorList);

            _experimentArchive = document.getExperimentArchive();
            loadDoc();

            Path expDir = _xarSource.getRootPath().resolve("export");
            if (Files.exists(expDir) && Files.isDirectory(expDir) &&
                _experimentArchive.getExperimentRuns() != null && _experimentArchive.getExperimentRuns().getExperimentRunArray().length > 0)
            {
                ExperimentRunType a = _experimentArchive.getExperimentRuns().getExperimentRunArray(0);
                a.setCreateNewIfDuplicate(false);
                a.setGenerateDataFromStepRecord(false);
                for (int i = a.getExperimentLog().getExperimentLogEntryArray().length - 1; i >= 0; i--)
                    a.getExperimentLog().removeExperimentLogEntry(i);

                try (OutputStream fos = Files.newOutputStream(expDir.resolve("experiment.xar.xml")))
                {
                    XmlOptions xOpt = new XmlOptions().setSavePrettyPrint();
                    document.save(fos, xOpt);
                }
            }
        }
        catch (IOException | XmlException e)
        {
            throw new XarFormatException(e);
        }
    }

    private void checkValidationErrors(ExperimentArchiveDocument xd, ArrayList<XmlError> errorList) throws XarFormatException
    {
        StringBuilder errorSB = new StringBuilder();

        boolean bHasDerivedTypeErrorsOnly = true;
        for (XmlError error : errorList)
        {
            // for one particular error type, try a fallback strategy
            if (bHasDerivedTypeErrorsOnly)
            {
                try
                {
                    XmlObject xObj = error.getObjectLocation();
                    XmlObject xObjWild;
                    String typeName;
                    if (null != xObj)
                    {
                        QName qName = xObj.schemaType().getName();
                        if (qName != null)
                        {
                            typeName = qName.getLocalPart();
                            if (typeName.endsWith("BaseType"))
                            {
                                String wType = "org.fhcrc.cpas.exp.xml." + typeName.substring(0, typeName.indexOf("BaseType")) + "Type";
                                SchemaType swType = xd.schemaType().getTypeSystem().typeForClassname(wType);
                                if (null != swType)
                                {
                                    getLog().warn("Schema validation error: " + error.getMessage());
                                    xObjWild = xObj.changeType(swType);
                                    bHasDerivedTypeErrorsOnly = xObjWild.validate();
                                    if (bHasDerivedTypeErrorsOnly)
                                        getLog().warn("Fixed by change to wildcard type");
                                    continue;
                                }
                            }
                        }
                    }
                    // if fallback strategy throws, just report original error
                } catch (Exception ignored) {   }
            }
            bHasDerivedTypeErrorsOnly = false;
            errorSB.append("Schema validation error: ");
            errorSB.append(error.getMessage());
            errorSB.append("\n");
            errorSB.append("Location of invalid XML: Line ");
            errorSB.append(error.getLine());
            errorSB.append("\n");
            errorSB.append("Source of invalid XML: ");
            errorSB.append(error.getCursorLocation().xmlText());
            errorSB.append("\n");
        }

        if (!bHasDerivedTypeErrorsOnly)
        {
            throw new XarFormatException("Document failed schema validation\n"
                    + "The current schema for this _document can be found at " + ExperimentService.SCHEMA_LOCATION + " \n"
                    + "Validation errors found: \n"
                    + errorSB);
        }
    }

    private void loadDoc() throws ExperimentException
    {
        try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction())
        {
            ExperimentArchiveType.ExperimentRuns experimentRuns = _experimentArchive.getExperimentRuns();
            // Start by clearing out existing things that we're going to be importing
            if (experimentRuns != null)
            {
                deleteExistingExperimentRuns(experimentRuns);
            }

            ExperimentArchiveType.ProtocolActionDefinitions actionDefs = _experimentArchive.getProtocolActionDefinitions();
            if (actionDefs != null)
            {
                deleteUniqueActions(actionDefs.getProtocolActionSetArray());
            }

            ExperimentArchiveType.ProtocolDefinitions protocolDefs = _experimentArchive.getProtocolDefinitions();
            if (protocolDefs != null)
            {
                deleteUniqueProtocols(protocolDefs.getProtocolArray());
            }

            ExperimentArchiveType.DomainDefinitions domainDefs = _experimentArchive.getDomainDefinitions();
            if (domainDefs != null)
            {
                for (DomainDescriptorType domain : _experimentArchive.getDomainDefinitions().getDomainArray())
                {
                    loadDomain(domain);
                }
            }

            ExperimentArchiveType.SampleSets sampleSets = _experimentArchive.getSampleSets();
            if (sampleSets != null)
            {
                for (SampleSetType sampleSet : sampleSets.getSampleSetArray())
                {
                    _loadedSampleTypes.add(loadSampleType(sampleSet));
                }
            }

            ExperimentArchiveType.DataClasses dataClasses = _experimentArchive.getDataClasses();
            if (dataClasses != null)
            {
                for (DataClassType dataClass : dataClasses.getDataClassArray())
                {
                    _loadedDataClasses.add(loadDataClass(dataClass));
                }
            }

            // Then start loading
            if (protocolDefs != null)
            {
                // Load protocols first as they might be referenced from experiments (assay batches)
                try (DbScope.Transaction protocolTransaction = ExperimentService.get().getSchema().getScope().ensureTransaction(ExperimentService.get().getProtocolImportLock()))
                {
                    for (ProtocolBaseType p : protocolDefs.getProtocolArray())
                    {
                        loadProtocol(p);
                    }
                    protocolTransaction.commit();
                }
                getLog().debug("Protocol import complete");
            }

            for (ExperimentType exp : _experimentArchive.getExperimentArray())
            {
                loadExperiment(exp);
                getLog().debug("Experiment/Run group import complete");
            }

            if (actionDefs != null)
            {
                for (ProtocolActionSetType actionSet : actionDefs.getProtocolActionSetArray())
                {
                    loadActionSet(actionSet);
                }

                getLog().debug("Protocol action set import complete");
            }

            List<ExpMaterial> startingMaterials = new ArrayList<>();
            List<Data> startingData = new ArrayList<>();

            if (_experimentArchive.getStartingInputDefinitions() != null)
            {
                for (MaterialBaseType material : _experimentArchive.getStartingInputDefinitions().getMaterialArray())
                {
                    // ignore dups of starting inputs
                    startingMaterials.add(loadMaterial(material, null, null, getRootContext()));
                }
                for (DataBaseType data : _experimentArchive.getStartingInputDefinitions().getDataArray())
                {
                    startingData.add(loadData(data, null, null, getRootContext()));
                }

                getLog().debug("Starting input import complete");
            }

            if (experimentRuns != null)
            {
                for (ExperimentRunType experimentRun : experimentRuns.getExperimentRunArray())
                {
                    loadExperimentRun(experimentRun, startingMaterials, startingData);
                }
            }

            if (_runWorkflowTaskMap.size() > 0)
            {
                saveRunWorkflowTaskIds();
            }

            resolveReplacedByRunLSIDs();

            transaction.commit();
        }
        catch (SQLException | BatchValidationException e)
        {
            throw new XarFormatException(e);
        }

        ExperimentRunGraph.clearCache(getContainer());

        try
        {
            for (DeferredDataLoad deferredDataLoad : _deferredDataLoads)
            {
                Path path = deferredDataLoad.getData().getFilePath();
                if (path == null)
                    continue;
                else if (Files.exists(path))
                    deferredDataLoad.getData().importDataFile(_job, _xarSource);
                else
                    getLog().warn("Data file " + FileUtil.getFileName(path) + " does not exist and could not be loaded.");
            }
        }
        catch (SQLException e)
        {
            throw new XarFormatException(e);
        }

        for (ExpRun loadedRun : _loadedRuns)
        {
            try
            {
                ExperimentService.get().onRunDataCreated(loadedRun.getProtocol(), loadedRun, getContainer(), getUser());
                ExperimentService.get().queueSyncRunEdges(loadedRun);
            }
            catch (BatchValidationException e)
            {
                throw new ExperimentException(e);
            }
        }
    }

    private void resolveReplacedByRunLSIDs() throws XarFormatException, BatchValidationException
    {
        for (Map.Entry<Integer, String> entry : _runReplacedByMap.entrySet())
        {
            String runLSID = LsidUtils.resolveLsidFromTemplate(entry.getValue(), _xarSource.getXarContext(), "ExperimentRun", "ExperimentRun");
            ExpRunImpl replacedByRun = ExperimentServiceImpl.get().getExpRun(runLSID);
            if (replacedByRun != null && replacedByRun.getContainer().equals(getContainer()))
            {
                ExpRunImpl run = ExperimentServiceImpl.get().getExpRun(entry.getKey());
                if (run == null)
                {
                    throw new XarFormatException("Could not find previously imported run with RowId " + entry.getKey());
                }
                run.setReplacedByRun(replacedByRun);
                run.save(getUser());
            }
            else
            {
                getLog().warn("Could not resolve replacement run " + entry.getValue() + " so it will not be referenced.");
            }
        }
    }

    private void logErrorAndThrow(String msg) throws XarFormatException
    {
        logErrorAndThrow(msg, null);
    }

    private void logErrorAndThrow(String msg, @Nullable Throwable t) throws XarFormatException
    {
        getLog().error(msg, t);
        throw new XarFormatException(msg, t);
    }

    private String prefixNameWithDomainLSID(String lsid, String name)
    {
        return name.startsWith(lsid + "#") ? name : lsid + "#" + name;
    }

    // For backwards compatibility, try to find properties by name if it can't be found by uri
    @Nullable
    private DomainProperty findPropertyByUriOrName(Domain domain, String name)
    {
        String propertyURI = prefixNameWithDomainLSID(domain.getTypeURI(), name);
        DomainProperty dp = domain.getPropertyByURI(propertyURI);
        if (dp == null)
            dp = domain.getPropertyByName(name);
        return dp;
    }

    private ExpSampleTypeImpl loadSampleType(SampleSetType sampleSet) throws XarFormatException
    {
        String lsid = LsidUtils.resolveLsidFromTemplate(sampleSet.getAbout(), getRootContext(), "SampleSet");
        ExpSampleTypeImpl existingMaterialSource = SampleTypeServiceImpl.get().getSampleType(lsid);

        getLog().debug("Importing SampleType with LSID '" + lsid + "'");
        ExpSampleTypeImpl materialSource = SampleTypeServiceImpl.get().createSampleType();
        materialSource.setDescription(sampleSet.getDescription());
        materialSource.setName(sampleSet.getName());
        materialSource.setLSID(lsid);
        materialSource.setContainer(getContainer());
        materialSource.setMaterialLSIDPrefix(LsidUtils.resolveLsidFromTemplate(sampleSet.getMaterialLSIDPrefix(), getRootContext(), ExpMaterial.DEFAULT_CPAS_TYPE));

        Domain domain = materialSource.getDomain();
        String[] keyFieldArray = sampleSet.getKeyFieldArray();
        List<String> keyFields = keyFieldArray != null && keyFieldArray.length > 0 ?
                Arrays.asList(keyFieldArray) :
                Collections.emptyList();

        if (sampleSet.isSetNameExpression())
        {
            materialSource.setNameExpression(sampleSet.getNameExpression());
        }

        if (sampleSet.isSetAliquotNameExpression())
        {
            materialSource.setAliquotNameExpression(sampleSet.getAliquotNameExpression());
        }

        if (keyFields.size() == 1 && keyFields.get(0).equals(ExpMaterialTable.Column.Name.name()))
        {
            // We can use Name as the idCol1 without requiring it to be a domain property
            materialSource.setIdCol1(ExpMaterialTable.Column.Name.name());
        }
        else if (keyFields.size() > 0)
        {
            List<String> propertyURIs = new ArrayList<>(keyFields.size());
            for (String keyField : keyFields)
            {
                DomainProperty dp = findPropertyByUriOrName(domain, keyField);
                if (dp == null)
                    logErrorAndThrow("Failed to find keyField '" + keyField + " when importing SampleType with LSID '" + lsid + "' ");
                else
                    propertyURIs.add(dp.getPropertyURI());
            }
            materialSource.setIdCols(propertyURIs);
        }

        if (sampleSet.getParentField() != null)
        {
            DomainProperty dp = findPropertyByUriOrName(domain, sampleSet.getParentField());
            if (dp == null)
                logErrorAndThrow("Failed to find parentField '" + sampleSet.getParentField() + " when importing SampleSet with LSID '" + lsid + "' ");
            else
                materialSource.setParentCol(dp.getPropertyURI());
        }

        if (sampleSet.isSetLabelColor())
        {
            materialSource.setLabelColor(sampleSet.getLabelColor());
        }

        if (sampleSet.isSetMetricUnit())
        {
            materialSource.setMetricUnit(sampleSet.getMetricUnit());
        }

        if (sampleSet.isSetAutoLinkTargetContainerId())
        {
            materialSource.setAutoLinkTargetContainer(ContainerManager.getForId(sampleSet.getAutoLinkTargetContainerId()));
        }

        if (sampleSet.isSetAutoLinkCategory())
        {
            materialSource.setAutoLinkCategory(sampleSet.getAutoLinkCategory());
        }

        SampleSetType.ParentImportAlias parentImportAlias = sampleSet.getParentImportAlias();
        if (parentImportAlias != null)
        {
            Map<String, String> aliasMap = new LinkedHashMap<>();
            for (ImportAlias importAlias : parentImportAlias.getAliasArray())
            {
                aliasMap.put(importAlias.getName(), importAlias.getValue());
            }
            materialSource.setImportAliasMap(aliasMap);
        }

        if (sampleSet.isSetCategory())
        {
            materialSource.setCategory(sampleSet.getCategory());
        }

        if (existingMaterialSource != null)
        {
            if (_strictValidateExistingSampleType)
            {
                List<IdentifiableEntity.Difference> diffs = new ArrayList<>();
                IdentifiableEntity.diff(materialSource.getName(), existingMaterialSource.getName(), "Name", diffs);

                // Issue 37936 - Skip validation of description for the magic specimen/sample link, which is auto-generated based on the container name
                if (!existingMaterialSource.getName().equalsIgnoreCase(SpecimenService.SAMPLE_TYPE_NAME))
                {
                    // Issue 42708 - don't compare the material LSID prefix. Just keep the current one, which should be
                    // enough to assign unique LSIDs for new entries
                    IdentifiableEntity.diff(materialSource.getDescription(), existingMaterialSource.getDescription(), "Description", diffs);
                }
                if (!diffs.isEmpty())
                {
                    getLog().error("The SampleSet specified with LSID '" + lsid + "' has " + diffs.size() + " differences from the one that has already been loaded");
                    for (IdentifiableEntity.Difference diff : diffs)
                    {
                        getLog().error(diff.toString());
                    }
                    throw new XarFormatException("SampleSet with LSID '" + lsid + "' does not match existing SampleSet");
                }
            }

            return existingMaterialSource;
        }

        materialSource.save(getUser());

        return materialSource;
    }

    private ExpDataClass loadDataClass(DataClassType dataClassType) throws ExperimentException
    {
        String lsid = LsidUtils.resolveLsidFromTemplate(dataClassType.getAbout(), getRootContext(), "DataClass");
        ExpDataClass existingDataClass = ExperimentService.get().getDataClass(lsid);

        getLog().debug("Importing DataClass with LSID '" + lsid + "'");
        DataClass bean = new DataClass();
        bean.setContainer(getContainer());
        bean.setName(dataClassType.getName());
        bean.setLSID(lsid);
        ExpDataClass dataClass = new ExpDataClassImpl(bean);

        if (dataClassType.getDescription() != null)
            dataClass.setDescription(dataClassType.getDescription());

        if (dataClassType.getNameExpression() != null)
            dataClass.setNameExpression(dataClassType.getNameExpression());

        if (dataClassType.getCategory() != null)
            dataClass.setCategory(dataClassType.getCategory());

        if (dataClassType.getSampleType() != null)
        {
            ExpSampleType sampleType = SampleTypeService.get().getSampleType(getContainer(), getUser(), dataClassType.getSampleType());
            if (sampleType != null)
                dataClass.setSampleType(sampleType.getRowId());
            else
                getLog().warn("DataClass Sample Type : '" + dataClassType.getSampleType() + "' was not found.");
        }

        if (existingDataClass != null)
        {
            if (_strictValidateExistingSampleType)
            {
                List<IdentifiableEntity.Difference> diffs = new ArrayList<>();
                IdentifiableEntity.diff(dataClassType.getName(), existingDataClass.getName(), "Name", diffs);
                IdentifiableEntity.diff(dataClassType.getDescription(), existingDataClass.getDescription(), "Description", diffs);
                IdentifiableEntity.diff(dataClassType.getNameExpression(), existingDataClass.getNameExpression(), "Name Expression", diffs);
                IdentifiableEntity.diff(dataClassType.getCategory(), existingDataClass.getCategory(), "Category", diffs);

                if (!diffs.isEmpty())
                {
                    getLog().error("The DataClass specified with LSID '" + lsid + "' has " + diffs.size() + " differences from the one that has already been loaded");
                    for (IdentifiableEntity.Difference diff : diffs)
                    {
                        getLog().error(diff.toString());
                    }
                    throw new XarFormatException("DataClass with LSID '" + lsid + "' does not match existing DataClass");
                }
            }
            return existingDataClass;
        }
        dataClass.save(getUser());
        return dataClass;
    }

    private Domain loadDomain(DomainDescriptorType xDomain) throws ExperimentException
    {
        Pair<Domain, Map<DomainProperty, Object>> loaded = PropertyService.get().createDomain(getContainer(), getRootContext(), xDomain);
        Domain domain = loaded.getKey();
        Map<DomainProperty, Object> newDefaultValues = loaded.getValue();
        String lsid = domain.getTypeURI();

        DomainDescriptor existingDomainDescriptor = OntologyManager.getDomainDescriptor(lsid, getContainer());
        DomainImpl existingDomain = existingDomainDescriptor == null ? null : new DomainImpl(existingDomainDescriptor);
        if (existingDomain != null)
        {
            Map<String, DomainProperty> newProps = new HashMap<>();
            for (DomainProperty prop : domain.getProperties())
            {
                newProps.put(prop.getPropertyURI(), prop);
            }

            List<IdentifiableEntity.Difference> diffs = new ArrayList<>();
            IdentifiableEntity.diff(existingDomain.getName(), domain.getName(), "Name", diffs);
            Map<String, DomainProperty> oldProps = new HashMap<>();
            for (DomainProperty oldProp : existingDomain.getProperties())
            {
                oldProps.put(oldProp.getPropertyURI(), oldProp);
            }

            Map<DomainProperty, Object> existingDefaultValues = DefaultValueService.get().getDefaultValues(existingDomain.getContainer(), existingDomain);
            if (!IdentifiableEntity.diff(oldProps.keySet(), newProps.keySet(), "Domain Properties", diffs))
            {
                for (String key : oldProps.keySet())
                {
                    DomainProperty oldProp = oldProps.get(key);
                    DomainProperty newProp = newProps.get(key);

                    IdentifiableEntity.diff(oldProp.getDescription(), newProp.getDescription(), key + " description", diffs);
                    IdentifiableEntity.diff(oldProp.getFormat(), newProp.getFormat(), key + " format string", diffs);
                    IdentifiableEntity.diff(oldProp.getLabel(), newProp.getLabel(), key + " label", diffs);
                    IdentifiableEntity.diff(oldProp.getName(), newProp.getName(), key + " name", diffs);
                    IdentifiableEntity.diff(oldProp.isHidden(), newProp.isHidden(), key + " hidden", diffs);
                    IdentifiableEntity.diff(oldProp.isShownInDetailsView(), newProp.isShownInDetailsView(), key + " shown in details view", diffs);
                    IdentifiableEntity.diff(oldProp.isShownInInsertView(), newProp.isShownInInsertView(), key + " shown in insert view", diffs);
                    IdentifiableEntity.diff(oldProp.isShownInUpdateView(), newProp.isShownInUpdateView(), key + " shown in update view", diffs);
                    IdentifiableEntity.diff(oldProp.getPropertyURI(), newProp.getPropertyURI(), key + " property URI", diffs);
                    IdentifiableEntity.diff(oldProp.getPropertyDescriptor().getRangeURI(), newProp.getPropertyDescriptor().getRangeURI(), key + " range URI", diffs);
                    IdentifiableEntity.diff(oldProp.getPropertyDescriptor().getConceptURI(), newProp.getPropertyDescriptor().getConceptURI(), key + " concept URI", diffs);
                    IdentifiableEntity.diff(oldProp.isMvEnabled(), newProp.isMvEnabled(), key + " missing value enabled", diffs);
                    IdentifiableEntity.diff(oldProp.getURL(), newProp.getURL(), key + " url", diffs);
                    IdentifiableEntity.diff(oldProp.getImportAliasSet(), newProp.getImportAliasSet(), key + " import aliases", diffs);
                    IdentifiableEntity.diff(oldProp.getDefaultValueTypeEnum(), newProp.getDefaultValueTypeEnum(), key + " default value type", diffs);
                    IdentifiableEntity.diff(oldProp.getPrincipalConceptCode(), newProp.getPrincipalConceptCode(), key + " principal concept code", diffs);
                    IdentifiableEntity.diff(oldProp.getSourceOntology(), newProp.getSourceOntology(), key + " source ontology", diffs);
                    IdentifiableEntity.diff(oldProp.getConceptImportColumn(), newProp.getConceptImportColumn(), key + " concept import column", diffs);
                    IdentifiableEntity.diff(oldProp.getConceptLabelColumn(), newProp.getConceptLabelColumn(), key + " concept label column", diffs);
                    IdentifiableEntity.diff(oldProp.getConceptSubtree(), newProp.getConceptSubtree(), key + " concept lookup subtree", diffs);
                    IdentifiableEntity.diff(existingDefaultValues.get(oldProp), newDefaultValues.get(newProp), key + " default value", diffs);
                }
            }

            if (!diffs.isEmpty())
            {
                getLog().error("The domain specified with LSID '" + lsid + "' has " + diffs.size() + " differences from the domain that has already been loaded");
                for (IdentifiableEntity.Difference diff : diffs)
                {
                    getLog().error(diff.toString());
                }
                throw new XarFormatException("Domain with LSID '" + lsid + "' does not match existing domain");
            }

            return new DomainImpl(existingDomainDescriptor);
        }

        try
        {
            domain.save(getUser());
            DefaultValueService.get().setDefaultValues(domain.getContainer(), newDefaultValues);
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw new XarFormatException(e);
        }

        return domain;
    }

    private void deleteUniqueActions(ProtocolActionSetType[] actionDefs) throws ExperimentException
    {
        for (ProtocolActionSetType actionDef : actionDefs)
        {
            String protocolLSID = LsidUtils.resolveLsidFromTemplate(actionDef.getParentProtocolLSID(), getRootContext(), "Protocol");
            ExpProtocol existingProtocol = ExperimentService.get().getExpProtocol(protocolLSID);

            if (existingProtocol != null)
            {
                // First make sure it isn't in use by some run that's not part of this file
                if (ExperimentService.get().getExpProtocolApplicationsForProtocolLSID(protocolLSID).isEmpty() &&
                    ExperimentService.get().getExpRunsForProtocolIds(false, existingProtocol.getRowId()).isEmpty())
                {
                    getLog().debug("Deleting existing action set with parent protocol LSID '" + protocolLSID + "' so that the protocol specified in the file can be uploaded");
                    existingProtocol.delete(getUser());
                }
                else
                {
                    getLog().debug("Existing action set with parent protocol LSID '" + protocolLSID + "' is referenced by other experiment runs, so it cannot be updated");
                }
            }
        }
    }

    private void deleteUniqueProtocols(ProtocolBaseType[] protocolDefs) throws ExperimentException
    {
        for (ProtocolBaseType protocol : protocolDefs)
        {
            String protocolLSID = LsidUtils.resolveLsidFromTemplate(protocol.getAbout(), getRootContext(), "Protocol");
            ExpProtocol existingProtocol = ExperimentService.get().getExpProtocol(protocolLSID);

            if (existingProtocol != null)
            {
                // Delete any protocols from the XAR that are in the database but aren't referenced as part of a ProtocolActionSet
                if (existingProtocol.getParentProtocols().isEmpty())
                {
                    getLog().debug("Deleting existing protocol with LSID '" + protocolLSID + "' so that the protocol specified in the file can be uploaded");
                    existingProtocol.delete(getUser());
                }
                else
                {
                    getLog().debug("Existing protocol with LSID '" + protocolLSID + "' is referenced by other experiment runs, so it cannot be updated");
                }
            }
        }
    }

    private void deleteExistingExperimentRuns(ExperimentArchiveType.ExperimentRuns experimentRuns) throws ExperimentException
    {
        for (ExperimentRunType experimentRun : experimentRuns.getExperimentRunArray())
        {
            String runLSID = LsidUtils.resolveLsidFromTemplate(experimentRun.getAbout(), getRootContext(), "ExperimentRun", "ExperimentRun");

            // Clear out any existing runs with the same LSID
            ExpRun existingRun = ExperimentService.get().getExpRun(runLSID);
            if (existingRun != null && (_reloadExistingRuns || !Objects.equals(existingRun.getFilePathRoot() == null ? null : FileUtil.getAbsoluteCaseSensitiveFile(existingRun.getFilePathRoot()), _xarSource.getRoot() == null ? null : FileUtil.getAbsoluteCaseSensitiveFile(_xarSource.getRoot()))))
            {
                getLog().debug("Deleting existing experiment run with LSID'" + runLSID + "' so that the run specified in the file can be uploaded");
                existingRun.delete(getUser());
            }
        }
    }


    private void loadExperiment(ExperimentType exp) throws SQLException, XarFormatException
    {
        if (exp == null)
            throw new XarFormatException("No experiment found");

        PropertyCollectionType xbProps = exp.getProperties();
        if (null != xbProps)
        {
            Map<String, Object> mProps = getSimplePropertiesMap(xbProps);
            Object lsidAuthorityTemplate = mProps.get("terms.fhcrc.org#XarTemplate.LSIDAuthority");
            if (lsidAuthorityTemplate instanceof String)
            {
                getRootContext().addSubstitution("LSIDAuthority", (String)lsidAuthorityTemplate);
            }
            Object lsidNamespaceSuffixTemplate = mProps.get("terms.fhcrc.org#XarTemplate.LSIDNamespaceSuffix");
            if (lsidNamespaceSuffixTemplate instanceof String)
            {
                getRootContext().addSubstitution("LSIDNamespace.Suffix", (String)lsidNamespaceSuffixTemplate);
            }
        }

        String experimentLSID = LsidUtils.resolveLsidFromTemplate(exp.getAbout(), getRootContext(), "Experiment");
        _experimentLSIDs.add(experimentLSID);

        TableInfo tiExperiment = ExperimentServiceImpl.get().getTinfoExperiment();

        ExpExperiment experiment = ExperimentService.get().getExpExperiment(experimentLSID);
        if (null == experiment)
        {
            Experiment experimentDataObject = new Experiment();

            experimentDataObject.setLSID(experimentLSID);
            experimentDataObject.setHypothesis(trimString(exp.getHypothesis()));
            experimentDataObject.setName(trimString(exp.getName()));
            if (null != exp.getContact())
                experimentDataObject.setContactId(exp.getContact().getContactId());
            experimentDataObject.setExperimentDescriptionURL(trimString(exp.getExperimentDescriptionURL()));
            experimentDataObject.setComments(trimString(exp.getComments()));
            experimentDataObject.setContainer(getContainer());

            if (exp.isSetBatchProtocolLSID())
            {
                String batchProtocolLSID = LsidUtils.resolveLsidFromTemplate(exp.getBatchProtocolLSID(), getRootContext(), "Protocol");
                ExpProtocol batchProtocol = ExperimentService.get().getExpProtocol(batchProtocolLSID);
                if (batchProtocol == null)
                {
                    throw new XarFormatException("Could not resolve protocol with LSID '" + batchProtocolLSID + "'");
                }
                experimentDataObject.setBatchProtocolId(batchProtocol.getRowId());
            }

            experimentDataObject = Table.insert(getUser(), tiExperiment, experimentDataObject);
            experiment = new ExpExperimentImpl(experimentDataObject);

            ObjectProperty contactProperty = null;
            if (null != exp.getContact())
                contactProperty = readContact(exp.getContact(), experimentLSID);

            savePropertyCollection(xbProps, experimentLSID, experimentLSID, contactProperty);
        }
        else
        {
            if (!experiment.getContainer().equals(getContainer()))
            {
                throw new XarFormatException("This experiment already exists in another folder, " + experiment.getContainer().getPath());
            }
        }

        if (null != exp.getContact())
            getRootContext().addSubstitution("ContactId", exp.getContact().getContactId());

        getLog().debug("Finished loading Experiment with LSID '" + experimentLSID + "'");
    }

    public List<ExpRun> getExperimentRuns()
    {
        return _loadedRuns;
    }

    public List<ExpSampleType> getSampleTypes()
    {
        return _loadedSampleTypes;
    }

    public List<ExpDataClass> getDataClasses()
    {
        return _loadedDataClasses;
    }

    private void loadExperimentRun(ExperimentRunType a, List<ExpMaterial> startingMaterials, List<Data> startingData) throws SQLException, ExperimentException
    {
        XarContext runContext = new XarContext(getRootContext());

        String experimentLSID = null;
        if (a.isSetExperimentLSID())
        {
            experimentLSID = LsidUtils.resolveLsidFromTemplate(a.getExperimentLSID(), runContext, "Experiment");
        }
        else
        {
            if (_experimentLSIDs.size() == 1)
            {
                experimentLSID = _experimentLSIDs.iterator().next();
            }
        }

        runContext.addSubstitution("ExperimentLSID", experimentLSID);

        String runLSID = LsidUtils.resolveLsidFromTemplate(a.getAbout(), runContext, "ExperimentRun", "ExperimentRun");

        // First check if the run has already been deleted
        ExpRun existingRun = ExperimentService.get().getExpRun(runLSID);
        if (existingRun != null)
        {
            getLog().debug("Experiment run already exists, it will NOT be reimported, LSID '" + runLSID + "'");
            for (ExpData d : existingRun.getAllDataUsedByRun())
            {
                _deferredDataLoads.add(new DeferredDataLoad(d, existingRun));
            }
            getLog().info("Experiment run '" + existingRun.getName() + "' complete");
            getLog().debug("Experiment run import complete, LSID '" + runLSID + "'");
            return;
        }

        Lsid pRunLSID = new Lsid(runLSID);
        String runProtocolLSID = LsidUtils.resolveLsidFromTemplate(a.getProtocolLSID(), getRootContext(), "Protocol");
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(runProtocolLSID);
        if (protocol == null)
        {
            throw new XarFormatException("Unknown protocol " + runProtocolLSID + " referenced by ExperimentRun " + pRunLSID);
        }

        if (null != AssayService.get())
        {
            AssayProvider ap = AssayService.get().getProvider(protocol);
            if (null != ap)
            {
                ap.getXarCallbacks(getUser(),getContainer()).beforeXarImportRun(a);
            }
        }

        ExperimentRun run = ExperimentServiceImpl.get().getExperimentRun(pRunLSID.toString());

        if (null != run)
        {
            if (a.getCreateNewIfDuplicate())
            {
                while (null != run)
                {
                    //make the lsid unique and retry
                    String suffix = Long.toString(Math.round(Math.random() * 100));
                    pRunLSID = pRunLSID.edit().setObjectId(pRunLSID.getObjectId() + "." + suffix).build();
                    run = ExperimentServiceImpl.get().getExperimentRun(pRunLSID.toString());
                }
            }
            else
            {
                throw new XarFormatException("An ExperimentRun with LSID " + pRunLSID + " already exists");
            }
        }

        if (run == null)
        {
            ExperimentRun vals = new ExperimentRun();
            // todo not sure about having roots stored in database
            // todo support substitutions here?

            vals.setLSID(pRunLSID.toString());

            vals.setName(trimString(a.getName()));
            vals.setProtocolLSID(runProtocolLSID);
            vals.setComments(trimString(a.getComments()));

            vals.setFilePathRoot(FileUtil.getAbsolutePath(_xarSource.getRootPath()));     //  FileUtil.getAbsolutePath(runContext.getContainer(), _job.getPipeRoot().getRootNioPath()));
            vals.setContainer(getContainer());
            String workflowTaskLSID = a.getWorkflowTaskLSID();

            if (workflowTaskLSID != null)
            {
                if (!workflowTaskLSID.startsWith("${WorkflowTaskReference}:"))
                    throw new XarFormatException("Invalid WorkflowTaskLSID encountered: " + workflowTaskLSID);

                workflowTaskLSID = workflowTaskLSID.split(":")[1];
            }
            // remember which job created the run so we can show this run on the job details page
            vals.setJobId(PipelineService.get().getJobId(_job.getUser(), _job.getContainer(), _job.getJobGUID()));

            ExpRunImpl impl = new ExpRunImpl(vals);
            try
            {
                impl.save(getUser());
                run = impl.getDataObject();

                if (workflowTaskLSID != null)
                    _runWorkflowTaskMap.put(run.getRowId(), workflowTaskLSID);

                String replacedByLSID = a.getReplacedByRunLSID();
                if (replacedByLSID != null)
                    // Save for later so that we can resolve after everything's been imported
                {
                    _runReplacedByMap.put(impl.getRowId(), replacedByLSID);
                }
            }
            catch (BatchValidationException x)
            {
                throw new ExperimentException(x);
            }
        }

        if (experimentLSID != null)
        {
            ExpExperiment e = ExperimentService.get().getExpExperiment(experimentLSID);
            ExpProtocol batchProtocol = e.getBatchProtocol();
            // Not an ideal fix, but avoid wiring up a run to an experiment that's a batch for a different protocol on
            // this server. See issue 24588
            if (batchProtocol == null || run.getProtocolLSID().equals(batchProtocol.getLSID()))
            {
                e.addRuns(getUser(), new ExpRunImpl(run));
            }
        }

        runContext.setCurrentRun(new ExpRunImpl(run));

        PropertyCollectionType xbProps = a.getProperties();

        savePropertyCollection(xbProps, run.getLSID(), run.getLSID(), null);


        // if ExperimentLog is present and ProtocolApps section is not, generate from log
        // if both are present, look for generatedata attribute

        if ((a.getProtocolApplications().getProtocolApplicationArray().length == 0) || a.getGenerateDataFromStepRecord())
        {
            ExperimentLogEntryType [] steps = a.getExperimentLog().getExperimentLogEntryArray();

            if ((null != steps) && (steps.length > 0))
            {
                ProtocolActionStepDetail stepProtocol = ExperimentServiceImpl.get().getProtocolActionStepDetail(runProtocolLSID, steps[0].getActionSequenceRef());
                if (stepProtocol == null)
                {
                    throw new XarFormatException("Protocol Not Found for Action Sequence =" + steps[0].getActionSequenceRef() + " in parent protocol " + runProtocolLSID);
                }
                String stepProtocolLSID = trimString(stepProtocol.getLSID());
                if (!stepProtocolLSID.equals(runProtocolLSID))
                {
                    throw new XarFormatException("Invalid ExperimentRun start action: " + stepProtocolLSID);
                }

                XarExpander expander = new XarExpander(_xarSource, _job, run, startingData, startingMaterials, _experimentArchive);

                expander.expandSteps(steps, runContext, a);
                loadProtocolApplications(a, run, runContext);
            }
        }
        else
        {
            loadProtocolApplications(a, run, runContext);
        }
        _processedRunsLSIDs.add(runLSID);
        ExpRun loadedRun = ExperimentService.get().getExpRun(runLSID);
        assert loadedRun != null;
        XarReaderRegistry.get().postProcessImportedRun(getContainer(), getUser(), loadedRun, getLog());
        _loadedRuns.add(loadedRun);
        getLog().debug("Finished loading ExperimentRun with LSID '" + runLSID + "'");
    }

    /**
     * This method runs last, and is used to wire up the Workflow Task FK relationship between Exp Runs and
     * Exp ProtocolApplications. This needs to run last because we have no good way to guarantee import order and ensure
     * all the appropriate ProtocolApplications are imported before the Exp Runs.
     */
    private void saveRunWorkflowTaskIds() throws ExperimentException
    {
        for (ExpRun run : _loadedRuns)
        {
            String objectId = _runWorkflowTaskMap.get(run.getRowId());

            if (objectId != null)
            {
                List<? extends ExpProtocolApplication> protocolApplications = ExperimentService.get().getExpProtocolApplicationsByObjectId(getContainer(), objectId);

                if (protocolApplications.size() > 1)
                {
                    throw new ExperimentException("Multiple ProtocolApplications found with object id: " + objectId);
                }
                else if (protocolApplications.size() == 0)
                {
                    getLog().warn("Could not find ProtocolApplication with LSID containing object id: " + objectId);
                }
                else
                {
                    run.setWorkflowTaskId(protocolApplications.get(0).getRowId());

                    try {
                        run.save(getUser());
                    }
                    catch (BatchValidationException e)
                    {
                        throw new ExperimentException(e);
                    }
                }
            }
        }
    }

    public List<String> getProcessedRunsLSIDs()
    {
        return _processedRunsLSIDs;
    }

    private void loadProtocolApplications(ExperimentRunType a, ExperimentRun run, XarContext context)
            throws SQLException, ExperimentException
    {
        ProtocolApplicationBaseType[] protApps = a.getProtocolApplications().getProtocolApplicationArray();
        boolean firstApp = true;
        for (ProtocolApplicationBaseType protApp : protApps)
        {
            loadProtocolApplication(protApp, run, context, firstApp);
            firstApp = false;
        }
    }

    private java.sql.Timestamp getSqlTimestamp(Calendar date)
    {
        if (date != null)
            return new java.sql.Timestamp(date.getTimeInMillis());

        return null;
    }

    private void loadProtocolApplication(ProtocolApplicationBaseType xmlProtocolApp,
                                         ExperimentRun experimentRun,
                                         XarContext context, boolean firstApp) throws SQLException, ExperimentException
    {
        InputOutputRefsType.MaterialLSID[] inputMaterialLSIDs;
        InputOutputRefsType.DataLSID[] inputDataLSIDs;
        if (xmlProtocolApp.getInputRefs() == null)
        {
            inputMaterialLSIDs = new InputOutputRefsType.MaterialLSID[0];
            inputDataLSIDs = new InputOutputRefsType.DataLSID[0];
        }
        else
        {
            inputMaterialLSIDs = xmlProtocolApp.getInputRefs().getMaterialLSIDArray();
            inputDataLSIDs = xmlProtocolApp.getInputRefs().getDataLSIDArray();
        }

        TableInfo tiProtApp = ExperimentServiceImpl.get().getTinfoProtocolApplication();
        TableInfo tiMaterialInput = ExperimentServiceImpl.get().getTinfoMaterialInput();
        TableInfo tiDataInput = ExperimentServiceImpl.get().getTinfoDataInput();

        java.sql.Timestamp sqlDateTime = null;
        java.sql.Timestamp sqlStartTime = null;
        java.sql.Timestamp sqlEndTime = null;
        Integer recordCount = null;

        if (!xmlProtocolApp.isNilActivityDate())
        {
            sqlDateTime = getSqlTimestamp(xmlProtocolApp.getActivityDate());
        }
        if (sqlDateTime == null)
        {
            sqlDateTime = new java.sql.Timestamp(System.currentTimeMillis());
        }
        if (!xmlProtocolApp.isNilStartTime())
        {
            sqlStartTime = getSqlTimestamp(xmlProtocolApp.getStartTime());
        }
        if (!xmlProtocolApp.isNilEndTime())
        {
            sqlEndTime = getSqlTimestamp(xmlProtocolApp.getEndTime());
        }
        if (!xmlProtocolApp.isNilRecordCount())
        {
            recordCount = xmlProtocolApp.getRecordCount();
        }

        String protAppLSID = LsidUtils.resolveLsidFromTemplate(xmlProtocolApp.getAbout(), context, "ProtocolApplication");

        String protocolLSID = LsidUtils.resolveLsidFromTemplate(xmlProtocolApp.getProtocolLSID(), context, "Protocol");
        if (ExperimentService.get().getExpProtocol(protocolLSID) == null)
        {
            throw new XarFormatException("Unknown protocol " + xmlProtocolApp.getProtocolLSID() + " referenced by protocol application " + protAppLSID);
        }

        int runId = experimentRun.getRowId();

        ProtocolApplication protocolApp = ExperimentServiceImpl.get().getProtocolApplication(protAppLSID);
        if (protocolApp == null)
        {
            protocolApp = new ProtocolApplication();

            protocolApp.setLSID(protAppLSID);
            protocolApp.setName(trimString(xmlProtocolApp.getName()));
            String cpasType = trimString(xmlProtocolApp.getCpasType());
            checkProtocolApplicationCpasType(cpasType, getLog());
            protocolApp.setCpasType(cpasType);
            protocolApp.setProtocolLSID(protocolLSID);
            protocolApp.setActivityDate(sqlDateTime);
            protocolApp.setActionSequence(xmlProtocolApp.getActionSequence());
            protocolApp.setRunId(runId);
            protocolApp.setComments(trimString(xmlProtocolApp.getComments()));
            protocolApp.setStartTime(sqlStartTime);
            protocolApp.setEndTime(sqlEndTime);
            protocolApp.setRecordCount(recordCount);

            protocolApp = Table.insert(getUser(), tiProtApp, protocolApp);
        }

        if (null == protocolApp)
            throw new XarFormatException("No row found");

        _loadedProtocolApplications.add(protocolApp);
        int protAppId = protocolApp.getRowId();

        PropertyCollectionType xbProps = xmlProtocolApp.getProperties();

        savePropertyCollection(xbProps, protAppLSID, experimentRun.getLSID(), null);

        SimpleValueCollectionType xbParams = xmlProtocolApp.getProtocolApplicationParameters();
        if (xbParams != null)
            loadProtocolApplicationParameters(xbParams, protAppId);

        //todo  extended protocolApp types??

        for (InputOutputRefsType.MaterialLSID inputMaterialLSID : inputMaterialLSIDs)
        {
            String declaredType = (inputMaterialLSID.isSetCpasType() ? inputMaterialLSID.getCpasType() : ExpMaterial.DEFAULT_CPAS_TYPE);
            checkMaterialCpasType(declaredType);
            String lsid = LsidUtils.resolveLsidFromTemplate(inputMaterialLSID.getStringValue(), context, declaredType, ExpMaterial.DEFAULT_CPAS_TYPE);

            ExpMaterial inputRow = _xarSource.getMaterial(firstApp ? null : new ExpRunImpl(experimentRun), new ExpProtocolApplicationImpl(protocolApp), lsid);
            if (firstApp)
            {
                _xarSource.addMaterial(experimentRun.getLSID(), inputRow, null);
            }
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("MaterialId"), inputRow.getRowId());
            filter = filter.addCondition(FieldKey.fromParts("TargetApplicationId"), protAppId);
            if (new TableSelector(tiMaterialInput, filter, null).getObject(MaterialInput.class) == null)
            {
                MaterialInput mi = new MaterialInput();
                mi.setMaterialId(inputRow.getRowId());
                mi.setTargetApplicationId(protAppId);
                String roleName = inputMaterialLSID.getRoleName();
                mi.setRole(roleName);
                Table.insert(getUser(), tiMaterialInput, mi);
            }
        }

        for (InputOutputRefsType.DataLSID inputDataLSID : inputDataLSIDs)
        {
            String declaredType = (inputDataLSID.isSetCpasType() ? inputDataLSID.getCpasType() : "Data");
            checkDataCpasType(declaredType);
            String lsid = LsidUtils.resolveLsidFromTemplate(inputDataLSID.getStringValue(), context, declaredType, new AutoFileLSIDReplacer(inputDataLSID.getDataFileUrl(), getContainer(), _xarSource));

            ExpData data = _xarSource.getData(firstApp ? null : new ExpRunImpl(experimentRun), new ExpProtocolApplicationImpl(protocolApp), lsid);
            if (firstApp)
            {
                _xarSource.addData(experimentRun.getLSID(), data, null);
            }

            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("DataId"), data.getRowId());
            filter = filter.addCondition(FieldKey.fromParts("TargetApplicationId"), protAppId);
            if (new TableSelector(tiDataInput, filter, null).getObject(DataInput.class) == null)
            {
                DataInput input = new DataInput();
                input.setDataId(data.getRowId());
                input.setTargetApplicationId(protAppId);
                String roleName = inputDataLSID.getRoleName();
                input.setRole(roleName);

                Table.insert(getUser(), tiDataInput, input);
            }
        }

        MaterialBaseType [] outputMaterials = xmlProtocolApp.getOutputMaterials().getMaterialArray();
        for (MaterialBaseType outputMaterial : outputMaterials)
        {
            loadMaterial(outputMaterial, experimentRun, protAppId, context);
        }

        DataBaseType [] outputData = xmlProtocolApp.getOutputDataObjects().getDataArray();
        for (DataBaseType d : outputData)
        {
            loadData(d, experimentRun, protAppId, context);
        }
        getLog().debug("Finished loading ProtocolApplication with LSID '" + protocolLSID + "'");
    }

    private ExpMaterial loadMaterial(MaterialBaseType xbMaterial,
                                  @Nullable ExperimentRun run,
                                  Integer sourceApplicationId,
                                  XarContext context) throws XarFormatException
    {
        TableInfo tiMaterial = ExperimentServiceImpl.get().getTinfoMaterial();

        String declaredType = xbMaterial.getCpasType();
        if (null == declaredType)
            declaredType = ExpMaterial.DEFAULT_CPAS_TYPE;
        if (declaredType.contains("${"))
        {
            declaredType = LsidUtils.resolveLsidFromTemplate(declaredType, context, "SampleSet");
        }
        ExpSampleTypeImpl sampleSet = checkMaterialCpasType(declaredType);

        String materialLSID = LsidUtils.resolveLsidFromTemplate(xbMaterial.getAbout(), context, declaredType, ExpMaterial.DEFAULT_CPAS_TYPE);
        String rootMaterialLSID = LsidUtils.resolveLsidFromTemplate(xbMaterial.getRootMaterialLSID(), context, declaredType, ExpMaterial.DEFAULT_CPAS_TYPE);
        String aliquotedFromLSID = LsidUtils.resolveLsidFromTemplate(xbMaterial.getAliquotedFromLSID(), context, declaredType, ExpMaterial.DEFAULT_CPAS_TYPE);

        ExpMaterialImpl material = ExperimentServiceImpl.get().getExpMaterial(materialLSID);
        if (material == null && sampleSet != null)
        {
            // Try resolving it by name within the sample type in case we have it under a different LSID
            material = sampleSet.getSample(context.getContainer(), xbMaterial.getName());
            if (material != null)
            {
                // Remember this as an alternate LSID during import
                _xarSource.addMaterial(null, material, materialLSID);
                if (run != null)
                {
                    _xarSource.addMaterial(run.getLSID(), material, materialLSID);
                }
            }
        }
        if (material == null)
        {
            Material m = new Material();
            m.setRootMaterialLSID(rootMaterialLSID);
            m.setAliquotedFromLSID(aliquotedFromLSID);
            m.setLSID(materialLSID);
            m.setName(trimString(xbMaterial.getName()));
            m.setCpasType(declaredType);

            if (null != run)
                m.setRunId(run.getRowId());

            m.setContainer(getContainer());

            if (null != sourceApplicationId)
                m.setSourceApplicationId(sourceApplicationId);

            PropertyCollectionType xbProps = xbMaterial.getProperties();
            if (null == xbProps)
                xbProps = xbMaterial.addNewProperties();

            Map<String, ObjectProperty> props = loadObjectProperties(xbProps, materialLSID, null);

            try
            {
                ExpMaterialImpl mi = new ExpMaterialImpl(m);
                mi.save(getUser());
                mi.setProperties(getUser(), props, false);

                if (_auditBehaviorType == AuditBehaviorType.DETAILED)
                {
                    SampleTypeServiceImpl service = SampleTypeServiceImpl.get();
                    service.addAuditEvent(getUser(), getContainer(), service.getCommentDetailed(QueryService.AuditAction.INSERT, false), mi, null);
                }
                if (xbMaterial.isSetAlias())
                {
                    AliasInsertHelper.handleInsertUpdate(getContainer(), getUser(), mi.getLSID(),
                            ExperimentService.get().getTinfoMaterialAliasMap(), xbMaterial.getAlias());
                }
            }
            catch (ValidationException vex)
            {
                throw new XarFormatException(vex.getMessage(), vex);
            }

            loadExtendedMaterialType(xbMaterial, materialLSID, run);
            material = new ExpMaterialImpl(m);
        }
        else
        {
            updateSourceInfo(material.getDataObject(), sourceApplicationId, run, rootMaterialLSID, aliquotedFromLSID, context, tiMaterial);
        }

        _xarSource.addMaterial(run == null ? null : run.getLSID(), material, null);

        getLog().debug("Finished loading material with LSID '" + materialLSID + "'");
        return material;
    }

    private void updateSourceInfo(RunItem output, Integer sourceApplicationId,
                                  ExperimentRun run, String rootMaterialLSID, String aliquotedFromLSID, XarContext context, TableInfo tableInfo)
            throws XarFormatException
    {
        String description = output.getClass().getSimpleName();
        String lsid = output.getLSID();
        boolean changed = false;

        getLog().debug("Found an existing entry for " + description + " LSID " + lsid + ", not reloading its values from scratch");

        if (sourceApplicationId != null)
        {
            if (output.getSourceApplicationId() == null)
            {
                getLog().debug("Updating " + description + " with LSID '" + lsid + "', setting SourceApplicationId");
                output.setSourceApplicationId(sourceApplicationId);
                changed = true;
            }
            else
            {
                throw new XarFormatException(description + " with LSID '" + lsid + "' already has a source application of " + output.getSourceApplicationId() + ", cannot set it to " + sourceApplicationId);
            }
        }
        if (run != null && sourceApplicationId != null)
        {
            if (output.getRunId() == null)
            {
                getLog().debug("Updating " + description + " with LSID '" + lsid + "', setting its RunId");
                output.setRunId(run.getRowId());
                changed = true;
            }
            else
            {
                throw new XarFormatException(description + " with LSID '" + lsid + "' already has an experiment run id of " + output.getRunId() + ", cannot set it to " + run.getRowId());
            }
        }

        if (output instanceof Material)
        {
            if (rootMaterialLSID != null)
            {
                ExpMaterial rootMaterial = null;
                if (run != null)
                    rootMaterial = _xarSource.getMaterial(run.getExpObject(), null, rootMaterialLSID);
                getLog().debug("Updating " + description + " with aliquot root LSID");

                String newRootLsid = rootMaterial != null ? rootMaterial.getLSID() : rootMaterialLSID;
                if (((Material) output).getRootMaterialLSID() != null && !((Material) output).getRootMaterialLSID().equals(rootMaterialLSID))
                {
                    throw new XarFormatException(description + " with LSID '" + lsid + "' already has aliquot root material LSID of " + ((Material) output).getRootMaterialLSID() + "; cannot set it to " + newRootLsid);
                }
                else
                {
                    ((Material) output).setRootMaterialLSID(newRootLsid);
                    changed = true;
                }

            }
            if (aliquotedFromLSID != null)
            {
                ExpMaterial aliquotParent = null;
                if (run != null)
                    aliquotParent = _xarSource.getMaterial(run.getExpObject(), null, aliquotedFromLSID);
                getLog().debug("Updating " + description + " with aliquot parent LSID");

                String newParentLsid = aliquotParent != null ? aliquotParent.getLSID() : aliquotedFromLSID;
                if (((Material) output).getAliquotedFromLSID() != null && !((Material) output).getAliquotedFromLSID().equalsIgnoreCase(aliquotedFromLSID))
                {
                    throw new XarFormatException(description + " with LSID '" + lsid + "' already has aliquot parent LSID of " + ((Material) output).getAliquotedFromLSID() + "; cannot set it to " + aliquotedFromLSID);
                }
                else
                {
                    ((Material) output).setAliquotedFromLSID(newParentLsid);
                    changed = true;
                }
            }

        }
        if (changed)
        {
            Table.update(getUser(), tableInfo, output, output.getRowId());
        }
    }


    String findOriginalUrlProperty(PropertyCollectionType xbProps)
    {
        if (xbProps != null)
        {
            SimpleValueType[] simpleValArray = xbProps.getSimpleValArray();
            if (simpleValArray != null)
            {
                for (SimpleValueType simpleValue : simpleValArray)
                {
                    if (ORIGINAL_URL_PROPERTY.equals(simpleValue.getOntologyEntryURI()) && ORIGINAL_URL_PROPERTY_NAME.equals(simpleValue.getName()))
                    {
                        return simpleValue.getStringValue();
                    }
                }
            }
        }
        return null;
    }


    private Data loadData(DataBaseType xbData,
                          ExperimentRun experimentRun,
                          Integer sourceApplicationId,
                          XarContext context) throws SQLException, ExperimentException
    {
        TableInfo tiData = ExperimentServiceImpl.get().getTinfoData();

        String declaredType = xbData.getCpasType();
        if (null == declaredType)
            declaredType = "Data";
        if (declaredType.contains("${"))
        {
            declaredType = LsidUtils.resolveLsidFromTemplate(declaredType, context, "Data");
        }
        checkDataCpasType(declaredType);

        String dataLSID = LsidUtils.resolveLsidFromTemplate(xbData.getAbout(), context, declaredType, new AutoFileLSIDReplacer(xbData.getDataFileUrl(), getContainer(), _xarSource));
        ExpDataImpl expData = ExperimentServiceImpl.get().getExpData(dataLSID);
        ExpDataClassImpl expDataClass = ExperimentServiceImpl.get().getDataClass(declaredType);
        if (expData == null && expDataClass != null)
        {
            // Try resolving it by name within the data class in case we have it under a different LSID
            ExpDataImpl data = expDataClass.getData(getContainer(), xbData.getName());
            if (data != null)
            {
                // Remember this as an alternate LSID during import
                _xarSource.addData(null, data, dataLSID);
                if (experimentRun != null)
                {
                    _xarSource.addData(experimentRun.getLSID(), data, dataLSID);
                }
                expData = data;
            }
        }

        if (expData != null)
        {
            Data data = expData.getDataObject();
            Path existingFile = expData.getFilePath();
            String uri = _xarSource.getCanonicalDataFileURL(trimString(xbData.getDataFileUrl()));
            if (uri != null && existingFile != null && !Files.isDirectory(existingFile))
            {
                Path newFile = FileUtil.stringToPath(context.getContainer(), uri);
                if (null == newFile)
                    throw new ExperimentException("Unable to create path from URI: " + uri);

                boolean newFileExists = !Files.isDirectory(newFile) && Files.exists(newFile);
                if (!newFileExists)
                {
                    getLog().warn("The data file with LSID " + dataLSID + " (referenced as "
                            + xbData.getAbout() + " in the xar.xml, does not exist.");
                }

                // Issue 37561: if the existing file does not exist, don't try to keep using it or compare its contents
                if (!Files.exists(existingFile))
                {
                    getLog().debug("Updating " + data.getClass().getSimpleName() + " with LSID '" + dataLSID + "', setting dataFileUrl");
                    data.setDataFileUrl(uri);
                }
                else if (newFileExists && !newFile.equals(existingFile))
                {
                    byte[] existingHash = hashFile(existingFile);
                    byte[] newHash = hashFile(newFile);
                    if (!Arrays.equals(existingHash, newHash))
                    {
                        throw new ExperimentException("The data file with LSID " + dataLSID + " (referenced as "
                                + xbData.getAbout() + " in the xar.xml, does not have the same contents as the " +
                                "existing data file that has already been loaded.");
                    }
                }
            }

            if (!expData.getContainer().equals(getContainer()))
            {
                Container otherContainer = expData.getContainer();
                String containerDesc = otherContainer == null ? expData.getContainer().getPath() : otherContainer.getPath();
                throw new XarFormatException("Cannot reference a data file (" + expData.getDataFileUrl() + ") that has already been loaded into another container, " + containerDesc);
            }

            updateSourceInfo(data, sourceApplicationId, experimentRun, null, null, context, tiData);
        }
        else
        {
            Data data = new Data();
            data.setLSID(dataLSID);
            data.setName(trimString(xbData.getName()));
            data.setCpasType(declaredType);
            ExpDataClass currentDataClass = null;
            for (ExpDataClass dataClass : _loadedDataClasses)
            {
                if (dataClass.getLSID().equals(declaredType))
                {
                    currentDataClass = dataClass;
                    data.setClassId(dataClass.getRowId());
                }
            }

            // This existing hack is that newData has the source URL, but the dest container
            // We change to set it with the source container here -- Handler must change the container along with the dataFileURL (Dave 2/15/18)
            data.setContainer(_job instanceof MoveRunsPipelineJob ?
                    ((MoveRunsPipelineJob)_job).getSourceContainer() :
                    getContainer());

            if (null != sourceApplicationId)
            {
                data.setSourceApplicationId(sourceApplicationId);
                data.setRunId(experimentRun.getRowId());
            }

            if (null != trimString(xbData.getDataFileUrl()))
            {
                data.setDataFileUrl(_xarSource.getCanonicalDataFileURL(trimString(xbData.getDataFileUrl())));
            }
            else if (_useOriginalFileUrl)
            {
                String original = findOriginalUrlProperty(xbData.getProperties());
                if (null != original)
                {
                    URI uri = FileUtil.createUri(original);
                    if ("file".equals(uri.getScheme()))
                    {
                        if (new File(uri).exists())
                            data.setDataFileUrl(original);
                    }
                }
            }

            expData = new ExpDataImpl(data);
            expData.save(getUser());
            if (_auditBehaviorType == AuditBehaviorType.DETAILED && currentDataClass != null)
            {
                UserSchema userSchema = QueryService.get().getUserSchema(getUser(), getContainer(), ExpSchema.SCHEMA_EXP_DATA.toString());
                TableInfo dataTable = userSchema.getTable(currentDataClass.getName());
                if (dataTable != null)
                {
                    Map<String, Object> row = BeanObjectFactory.Registry.getFactory(Data.class).toMap(data, null);
                    dataTable.getAuditHandler(_auditBehaviorType).addAuditEvent(getUser(), getContainer(), dataTable, _auditBehaviorType, null, QueryService.AuditAction.INSERT, Collections.singletonList(row), null);
                }
            }

            PropertyCollectionType xbProps = xbData.getProperties();
            if (null == xbProps)
                xbProps = xbData.addNewProperties();

            savePropertyCollection(xbProps, dataLSID, null, null);

            Path path = expData.getFilePath();
            if (null != path)
            {
                String originalUrl = findOriginalUrlProperty(xbProps);
                if (null != originalUrl)
                    getRootContext().addData(new ExpDataImpl(data), originalUrl);
            }

            loadExtendedDataType(xbData, dataLSID, experimentRun);
        }

        if (expData.isFileOnDisk())
        {
            _deferredDataLoads.add(new DeferredDataLoad(expData, new ExpRunImpl(experimentRun)));
        }
        else
        {
            getLog().info("No data file found for " + expData.getName() + ". (LSID: " + expData.getLSID() + ", path: " + expData.getDataFileUrl() + ")");
        }


        _xarSource.addData(experimentRun == null ? null : experimentRun.getLSID(), expData, null);
        getLog().debug("Finished loading Data with LSID '" + dataLSID + "'");
        return expData.getDataObject();
    }

    private byte[] hashFile(Path existingFile) throws ExperimentException
    {
        try (InputStream fIn =  Files.newInputStream(existingFile))
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            DigestInputStream dIn = new DigestInputStream(fIn, digest);
            byte[] b = new byte[4096];
            while (dIn.read(b) != -1)
            {
            }
            return digest.digest();
        }
        catch (IOException | NoSuchAlgorithmException e)
        {
            throw new ExperimentException(e);
        }
    }

    private void loadProtocolApplicationParameters(SimpleValueCollectionType xbParams,
                                                   int protAppId)
    {
        TableInfo tiValueTable = ExperimentServiceImpl.get().getTinfoProtocolApplicationParameter();
        for (SimpleValueType simple : xbParams.getSimpleValArray())
        {
            ProtocolApplicationParameter param = new ProtocolApplicationParameter();
            param.setProtocolApplicationId(protAppId);
            param.setRunId(protAppId);
            param.setXMLBeanValue(simple, getLog());
            ExperimentServiceImpl.get().loadParameter(getUser(), param, tiValueTable, FieldKey.fromParts("ProtocolApplicationId"), protAppId);
        }
    }


    private List<ProtocolParameter> readProtocolParameters(SimpleValueCollectionType xbParams)
    {
        List<ProtocolParameter> result = new ArrayList<>();
        for (SimpleValueType simple : xbParams.getSimpleValArray())
        {
            ProtocolParameter param = new ProtocolParameter();
            param.setXMLBeanValue(simple, getLog());
            result.add(param);
        }
        return result;
    }

    private ObjectProperty readContact(ContactType contact,
                                       String parentLSID)
    {
        String propertyURI = GUID.makeURN();

        Map<String, ObjectProperty> childProps = new HashMap<>();

        if (null != contact.getContactId() && !contact.getContactId().equals(""))
        {
            childProps.put(CONTACT_ID_PROPERTY, new ObjectProperty(propertyURI, getContainer(), CONTACT_ID_PROPERTY, trimString(contact.getContactId()), "Contact Id"));
        }
        if (null != contact.getEmail() && !contact.getEmail().equals(""))
        {
            childProps.put(CONTACT_EMAIL_PROPERTY, new ObjectProperty(propertyURI, getContainer(), CONTACT_EMAIL_PROPERTY, trimString(contact.getEmail()), "Contact Email"));
        }
        if (null != contact.getFirstName() && !contact.getFirstName().equals(""))
        {
            childProps.put(CONTACT_FIRST_NAME_PROPERTY, new ObjectProperty(propertyURI, getContainer(), CONTACT_FIRST_NAME_PROPERTY, trimString(contact.getFirstName()), "Contact First Name"));
        }
        if (null != contact.getLastName() && !contact.getLastName().equals(""))
        {
            childProps.put(CONTACT_LAST_NAME_PROPERTY, new ObjectProperty(propertyURI, getContainer(), CONTACT_LAST_NAME_PROPERTY, trimString(contact.getLastName()), "Contact Last Name"));
        }

        if (childProps.isEmpty())
        {
            return null;
        }

        ObjectProperty contactProperty = new ObjectProperty(parentLSID, getContainer(), CONTACT_PROPERTY, new IdentifiableBase(propertyURI), "Contact");
        contactProperty.setChildProperties(childProps);

        return contactProperty;
    }

    private void setPropertyId(ObjectProperty objectProperty)
    {
        Integer id = _propertyIdMap.get(objectProperty.getPropertyURI());
        if (id != null)
        {
            objectProperty.setPropertyId(id.intValue());
            return;
        }
        PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(objectProperty.getPropertyURI(), getContainer());
        if (pd != null)
        {
            objectProperty.setPropertyId(pd.getPropertyId());
            _propertyIdMap.put(objectProperty.getPropertyURI(), pd.getPropertyId());
        }
    }

    private Map<String, ObjectProperty> readPropertyCollection(PropertyCollectionType xbValues,
                                                               String parentLSID, boolean checkForDuplicates) throws XarFormatException
    {
        Map<String, ObjectProperty> existingProps = OntologyManager.getPropertyObjects(getContainer(), parentLSID);
        Map<String, ObjectProperty> result = new HashMap<>();

        for (SimpleValueType simpleProp : xbValues.getSimpleValArray())
        {
            PropertyType propType = PropertyType.getFromXarName(simpleProp.getValueType().toString());
            String value = trimString(simpleProp.getStringValue());
            if (value != null && value.startsWith("urn:lsid:"))
            {
                value = LsidUtils.resolveLsidFromTemplate(value, getRootContext());
            }

            if (StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI.equals(simpleProp.getOntologyEntryURI()) && value != null)
            {
                Container autoLinkContainer = ContainerManager.getForPath(value);
                if (autoLinkContainer != null)
                    value = autoLinkContainer.getId();
            }

            String ontologyEntryURI = trimString(simpleProp.getOntologyEntryURI());
            if (ontologyEntryURI != null && ontologyEntryURI.contains("${"))
            {
                ontologyEntryURI = LsidUtils.resolveLsidFromTemplate(simpleProp.getOntologyEntryURI(), getRootContext());
            }
            ObjectProperty objectProp = new ObjectProperty(parentLSID, getContainer(), ontologyEntryURI, value, propType, simpleProp.getName());
            setPropertyId(objectProp);

            if (ExternalDocsURLCustomPropertyRenderer.URI.equals(trimString(objectProp.getPropertyURI())))
            {
                String relativePath = trimString(objectProp.getStringValue());
                if (relativePath != null)
                {
                    try
                    {
                        String fullPath = _xarSource.getCanonicalDataFileURL(relativePath);
                        Path file = FileUtil.stringToPath(getContainer(), fullPath);
                        if (Files.exists(file))
                        {
                            objectProp.setStringValue(fullPath);
                        }
                    }
                    catch (XarFormatException ignored)
                    {
                        // That's OK, don't treat the value as a relative path to the file
                    }
                }
            }

            if (checkForDuplicates && existingProps.containsKey(ontologyEntryURI))
            {
                ObjectProperty existingProp = existingProps.get(ontologyEntryURI);
                Object existingValue = existingProp.value();
                Object newValue = objectProp.value();
                if ((existingValue != null && !existingValue.equals(newValue)) || (existingValue == null && newValue != null) )
                {
                    throw new XarFormatException("Property: " + ontologyEntryURI + " of object " + parentLSID + " already exists with value " + existingProp);
                }
            }
            // Set the child properties to an empty map to prevent ExperimentService.savePropertyCollection from having to query to retrieve child props.
            objectProp.setChildProperties(Collections.emptyMap());
            result.put(ontologyEntryURI, objectProp);
        }

        for (PropertyObjectType xbPropObject : xbValues.getPropertyObjectArray())
        {
            PropertyObjectDeclarationType propObjDecl = xbPropObject.getPropertyObjectDeclaration();
            String ontologyEntryURI = trimString(propObjDecl.getOntologyEntryURI());
            if (ontologyEntryURI != null && ontologyEntryURI.contains("${"))
            {
                ontologyEntryURI = LsidUtils.resolveLsidFromTemplate(ontologyEntryURI, getRootContext());
            }
            if (checkForDuplicates && existingProps.containsKey(ontologyEntryURI))
            {
                throw new XarFormatException("Duplicate nested property for ParentURI " + parentLSID + ", OntologyEntryURI = " + propObjDecl.getOntologyEntryURI());
            }

            String uri = GUID.makeURN();
            ObjectProperty childProperty = new ObjectProperty(parentLSID, getContainer(), ontologyEntryURI, new IdentifiableBase(uri), propObjDecl.getName());
            setPropertyId(childProperty);
            childProperty.setChildProperties(readPropertyCollection(xbPropObject.getChildProperties(), uri, checkForDuplicates));
            result.put(ontologyEntryURI, childProperty);
        }

        return result;
    }


    @NotNull
    private Map<String, ObjectProperty> loadObjectProperties(PropertyCollectionType xbProps, String parentLSID, ObjectProperty additionalProperty) throws XarFormatException
    {
        Map<String, ObjectProperty> propsToInsert = new HashMap<>();
        if (xbProps != null)
        {
            propsToInsert.putAll(readPropertyCollection(xbProps, parentLSID, true));
        }
        if (additionalProperty != null)
        {
            propsToInsert.put(additionalProperty.getPropertyURI(), additionalProperty);
        }
        return propsToInsert;
    }


    private void savePropertyCollection(
        PropertyCollectionType xbProps,
        String parentLSID,
        String ownerLSID,
        ObjectProperty additionalProperty) throws SQLException, XarFormatException
    {
        Map<String, ObjectProperty> propsToInsert = loadObjectProperties(xbProps, parentLSID, additionalProperty);

        if (!propsToInsert.isEmpty())
        {
            ExperimentServiceImpl.get().savePropertyCollection(propsToInsert, ownerLSID, getContainer(), false);
        }
    }

    private void loadExtendedMaterialType(MaterialBaseType xbMaterial,
                                          String materialLSID,
                                          ExperimentRun run)
    {
        String cpasTypeName = xbMaterial.getCpasType();

        if (null != cpasTypeName)
        {
            MaterialType extMaterial = (MaterialType) xbMaterial.changeType(MaterialType.type);
            loadMaterialWildcardProperties(extMaterial, materialLSID, run);
        }
    }

    private void loadExtendedDataType(DataBaseType xbData,
                                      String dataLSID,
                                      ExperimentRun run)
    {
        String cpasTypeName = trimString(xbData.getCpasType());

        if (null != cpasTypeName)
        {
            // todo here's where we dynamically load a Data derived type
            DataType extData = (DataType) xbData.changeType(DataType.type);
            loadDataWildcardProperties(extData, dataLSID, run);
        }
    }

    private void loadProtocol(ProtocolBaseType p) throws ExperimentException, SQLException
    {
        String protocolLSID = LsidUtils.resolveLsidFromTemplate(p.getAbout(), getRootContext(), "Protocol");
        ExpProtocolImpl existingProtocol = ExperimentServiceImpl.get().getExpProtocol(protocolLSID);

        Protocol xarProtocol = readProtocol(p);

        Protocol protocol;

        if (existingProtocol != null)
        {
            List<IdentifiableEntity.Difference> diffs = existingProtocol.getDataObject().diff(xarProtocol);
            
            if (diffs.size() == 1 && !xarProtocol.getContainer().equals(existingProtocol.getContainer()) &&
                existingProtocol.getContainer().equals(ContainerManager.getSharedContainer()))
            {
                getLog().debug("Reusing the same protocol found in the /Shared container");
            }
            else if (!diffs.isEmpty())
            {
                getLog().error("The protocol specified in the file with LSID '" + protocolLSID + "' has " + diffs.size() + " differences from the protocol that has already been loaded");
                for (IdentifiableEntity.Difference diff : diffs)
                {
                    getLog().error(diff.toString());
                }
                throw new XarFormatException("Protocol with LSID '" + protocolLSID + "' does not match existing protocol");
            }
            protocol = existingProtocol.getDataObject();
            getLog().debug("Protocol with LSID '" + protocolLSID + "' matches a protocol with the same LSID that has already been loaded.");
        }
        else
        {
            if (xarProtocol.getLSID().equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID) || xarProtocol.getLSID().equals(ExperimentService.SAMPLE_ALIQUOT_PROTOCOL_LSID))
            {
                // create derivation and aliquot protocol using shared folder
                if (xarProtocol.getLSID().equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID))
                    ExperimentServiceImpl.get().ensureSampleDerivationProtocol(getUser());
                else
                    ExperimentServiceImpl.get().ensureSampleAliquotProtocol(getUser());

                ExpProtocolImpl ensuredExpProtocol = ExperimentServiceImpl.get().getExpProtocol(protocolLSID);

                if (ensuredExpProtocol == null)
                    throw new XarFormatException("Protocol with LSID '" + protocolLSID + "' does not exist.");

                protocol = ensuredExpProtocol.getDataObject();
            }
            else
                protocol = ExperimentServiceImpl.get().saveProtocol(getUser(), xarProtocol);

            getLog().debug("Finished loading Protocol with LSID '" + protocolLSID + "'");
        }

        ExpProtocolImpl protocolImpl = new ExpProtocolImpl(protocol);

        _xarSource.addProtocol(protocolImpl);
        XarReaderRegistry.get().postProcessImportedProtocol(getContainer(), getUser(), protocolImpl, _job.getLogger());
    }

    private void loadActionSet(ProtocolActionSetType actionSet) throws XarFormatException
    {
        TableInfo tiAction = ExperimentServiceImpl.get().getTinfoProtocolAction();

        //Check that the parent is defined already
        String parentLSID = LsidUtils.resolveLsidFromTemplate(actionSet.getParentProtocolLSID(), getRootContext(), "Protocol");
        ExpProtocol parentProtocol = _xarSource.getProtocol(parentLSID, "Parent");

        ProtocolActionType[] xActions = actionSet.getProtocolActionArray();

        List<ProtocolAction> existingActions = ExperimentServiceImpl.get().getProtocolActions(parentProtocol.getRowId());
        boolean alreadyLoaded = existingActions.size() != 0;
        if (alreadyLoaded && existingActions.size() != xActions.length)
        {
            throw new XarFormatException("Protocol actions for protocol " + parentLSID + " do not match those that have " +
                    "already been loaded. The existing protocol has " + existingActions.size() +
                    " actions but the file contains " + xActions.length + " actions.");
        }

        int priorSeq = -1;

        for (ProtocolActionType xAction : xActions)
        {
            String childLSID = LsidUtils.resolveLsidFromTemplate(xAction.getChildProtocolLSID(), getRootContext(), "Protocol");

            int currentSeq = xAction.getActionSequence();
            if (currentSeq <= priorSeq)
            {
                throw new XarFormatException("Sequence number under parent protocol '" + parentLSID + "' not unique and ascending: " + currentSeq);
            }
            priorSeq = currentSeq;

            int parentProtocolRowId = parentProtocol.getRowId();
            int childProtocolRowId = _xarSource.getProtocol(childLSID, "ActionSet child").getRowId();

            ProtocolAction action = null;
            // Look for an existing action that matches
            for (ProtocolAction existingAction : existingActions)
            {
                if (existingAction.getChildProtocolId() == childProtocolRowId &&
                    existingAction.getParentProtocolId() == parentProtocolRowId &&
                    existingAction.getSequence() == currentSeq)
                {
                    action = existingAction;
                }
            }

            if (action == null)
            {
                if (alreadyLoaded)
                {
                    throw new XarFormatException("Protocol actions for protocol " + parentLSID + " do not match the ones " +
                            "that have already been loaded - no match found for action defined in file with child protocol " +
                            childLSID + " and action sequence " + currentSeq);
                }
                action = new ProtocolAction();
                action.setParentProtocolId(parentProtocolRowId);
                action.setChildProtocolId(childProtocolRowId);
                action.setSequence(currentSeq);
                action = Table.insert(getUser(), tiAction, action);
            }

            int actionRowId = action.getRowId();

            ProtocolActionType.PredecessorAction[] predecessors = xAction.getPredecessorActionArray();
            if (predecessors == null)
            {
                predecessors = new ProtocolActionType.PredecessorAction[0];
            }
            List<ProtocolActionPredecessor> existingPredecessors = ExperimentServiceImpl.get().getProtocolActionPredecessors(parentLSID, childLSID);

            if (alreadyLoaded && predecessors.length != existingPredecessors.size())
            {
                throw new XarFormatException("Predecessors for child protocol " + childLSID + " do not match those " +
                        "that have already been loaded. The existing protocol has " + existingPredecessors.size() +
                        " predecessors but the file contains " + predecessors.length + " predecessors.");
            }

            for (ProtocolActionType.PredecessorAction xPredecessor : predecessors)
            {
                int predecessorActionSequence = xPredecessor.getActionSequenceRef();
                ProtocolActionStepDetail predecessorRow = ExperimentServiceImpl.get().getProtocolActionStepDetail(parentLSID, predecessorActionSequence);
                if (predecessorRow == null)
                {
                    throw new XarFormatException("Protocol Not Found for Action Sequence =" + predecessorActionSequence + " in parent protocol " + parentLSID);
                }
                int predecessorRowId = predecessorRow.getActionId();

                ProtocolActionPredecessor predecessor = null;
                for (ProtocolActionPredecessor existingPredecessor : existingPredecessors)
                {
                    if (predecessorActionSequence == existingPredecessor.getPredecessorSequence())
                    {
                        predecessor = existingPredecessor;
                    }
                }

                if (predecessor == null)
                {
                    if (alreadyLoaded)
                    {
                        throw new XarFormatException("Predecessors for child protocol " + childLSID + " do not match the ones " +
                                "that have already been loaded - no match found for predecessor defined in file with predecessor " +
                                "sequence " + predecessorActionSequence);
                    }

                    ExperimentServiceImpl.get().insertProtocolPredecessor(getUser(), actionRowId, predecessorRowId);
                }
            }
        }
    }

    private String trimString(String s)
    {
        return s == null ? null : s.trim();
    }

    private Protocol readProtocol(ProtocolBaseType p) throws XarFormatException, SQLException
    {
        Protocol protocol = new Protocol();
        protocol.setLSID(LsidUtils.resolveLsidFromTemplate(p.getAbout(), getRootContext(), "Protocol"));
        protocol.setName(trimString(p.getName()));
        protocol.setProtocolDescription(trimString(p.getProtocolDescription()));
        String applicationType = trimString(p.getApplicationType());
        if (applicationType == null)
        {
            applicationType = "ProtocolApplication";
        }
        protocol.setApplicationType(applicationType);

        if ((!p.isSetMaxInputMaterialPerInstance()) || p.isNilMaxInputMaterialPerInstance())
            protocol.setMaxInputMaterialPerInstance(null);
        else
            protocol.setMaxInputMaterialPerInstance(p.getMaxInputMaterialPerInstance());

        if ((!p.isSetMaxInputDataPerInstance()) || p.isNilMaxInputDataPerInstance())
            protocol.setMaxInputDataPerInstance(null);
        else
            protocol.setMaxInputDataPerInstance(p.getMaxInputDataPerInstance());

        if ((!p.isSetOutputMaterialPerInstance()) || p.isNilOutputMaterialPerInstance())
            protocol.setOutputMaterialPerInstance(null);
        else
            protocol.setOutputMaterialPerInstance(p.getOutputMaterialPerInstance());

        if ((!p.isSetOutputDataPerInstance()) || p.isNilOutputDataPerInstance())
            protocol.setOutputDataPerInstance(null);
        else
            protocol.setOutputDataPerInstance(p.getOutputDataPerInstance());

        String materialType = trimString(p.getOutputMaterialType());
        protocol.setOutputMaterialType(materialType == null ? ExpMaterial.DEFAULT_CPAS_TYPE : materialType);
        String dataType = trimString(p.getOutputDataType());
        protocol.setOutputDataType(dataType == null ? "Data" : dataType);

        List<ExpProtocolInput> protocolInputs = new ArrayList<>(10);
        if (p.isSetInputs())
        {
            ProtocolBaseType.Inputs inputs = p.getInputs();
            protocolInputs.addAll(loadMaterialProtocolInputs(inputs.getMaterialInputArray(), true));
            protocolInputs.addAll(loadDataProtocolInputs(inputs.getDataInputArray(), true));
        }
        if (p.isSetOutputs())
        {
            ProtocolBaseType.Outputs outputs = p.getOutputs();
            protocolInputs.addAll(loadMaterialProtocolInputs(outputs.getMaterialOutputArray(), false));
            protocolInputs.addAll(loadDataProtocolInputs(outputs.getDataOutputArray(), false));
        }
        protocol.storeProtocolInputs(protocolInputs);

        protocol.setInstrument(trimString(p.getInstrument()));
        protocol.setSoftware(trimString(p.getSoftware()));
        if (null != p.getContact())
            protocol.setContactId(p.getContact().getContactId());

        if (null != p.getStatus())
            protocol.setStatus(ExpProtocol.Status.valueOf(p.getStatus()));
        else if (applicationType.equals("ExperimentRun"))
            protocol.setStatus(ExpProtocol.Status.Active);

        protocol.setContainer(getContainer());

        // Protocol parameters
        List<ProtocolParameter> params = Collections.emptyList();
        SimpleValueCollectionType xbParams = p.getParameterDeclarations();
        if (null != xbParams)
        {
            params = readProtocolParameters(xbParams);
        }
        protocol.storeProtocolParameters(params);

        Map<String, ObjectProperty> properties = new HashMap<>();

        // Protocol properties
        PropertyCollectionType xbProps = p.getProperties();
        if (null != xbProps)
        {
            // now save the properties
            properties = readPropertyCollection(xbProps, protocol.getLSID(), false);
        }

        if (p.getContact() != null)
        {
            ObjectProperty contactProperty = readContact(p.getContact(), protocol.getLSID());
            if (contactProperty != null)
            {
                properties.put(contactProperty.getPropertyURI(), contactProperty);
            }
        }
        protocol.storeObjectProperties(properties);

        return protocol;
    }

    private List<ExpProtocolInput> loadMaterialProtocolInputs(MaterialProtocolInputType[] inputs, boolean input) throws SQLException, XarFormatException
    {
        if (inputs == null || inputs.length == 0)
            return Collections.emptyList();

        List<ExpProtocolInput> protocolInputs = new ArrayList<>(inputs.length);
        for (MaterialProtocolInputType pi : inputs)
        {
            ExpMaterialProtocolInput protocolInput = loadMaterialProtocolInput(pi, input);
            protocolInputs.add(protocolInput);
        }

        return protocolInputs;
    }

    private List<ExpProtocolInput> loadDataProtocolInputs(DataProtocolInputType[] inputs, boolean input) throws XarFormatException, SQLException
    {
        if (inputs == null || inputs.length == 0)
            return Collections.emptyList();

        List<ExpProtocolInput> protocolInputs = new ArrayList<>(inputs.length);
        for (DataProtocolInputType pi : inputs)
        {
            ExpDataProtocolInput protocolInput = loadDataProtocolInput(pi, input);
            protocolInputs.add(protocolInput);
        }

        return protocolInputs;
    }

    private ExpDataProtocolInput loadDataProtocolInput(DataProtocolInputType pi, boolean input) throws SQLException, XarFormatException
    {
        String name = pi.getName();
        String dataClassName = pi.getDataClass();
        ExpDataClass dc = null;
        if (dataClassName != null)
        {
            dc = ExperimentService.get().getDataClass(getContainer(), getUser(), dataClassName);
            if (dc == null)
                logErrorAndThrow("DataClass '" + dataClassName + "' not found for protocol input '" + name + "'");
        }

        ExpProtocolInputCriteria criteria = null;
        if (pi.isSetCriteria())
        {
            String criteriaType = pi.getCriteria().getType();
            String criteriaConfig = pi.getCriteria().getStringValue();
            criteria = ExperimentServiceImpl.get().createProtocolInputCriteria(criteriaType, criteriaConfig);
        }

        int minOccurs = 1;
        if (pi.isSetMinOccurs())
            minOccurs = pi.getMinOccurs();

        Integer maxOccurs = null;
        if (pi.isSetMinOccurs())
            maxOccurs = pi.getMaxOccurs();

        ExpDataProtocolInput protocolInput = ExperimentServiceImpl.get().createDataProtocolInput(getContainer(), name, 0, input, dc, criteria, minOccurs, maxOccurs);

        savePropertyCollection(pi.getProperties(), protocolInput.getLSID(), protocolInput.getLSID(), null);

        return protocolInput;
    }

    private ExpMaterialProtocolInput loadMaterialProtocolInput(MaterialProtocolInputType pi, boolean input) throws SQLException, XarFormatException
    {
        String name = pi.getName();
        String sampleTypeName = pi.getSampleSet();
        ExpSampleType sampleType = null;
        if (sampleTypeName != null)
        {
            sampleType = SampleTypeService.get().getSampleType(getContainer(), getUser(), sampleTypeName);
            if (sampleType == null)
                logErrorAndThrow("SampleSet '" + sampleTypeName + "' not found for protocol input '" + name + "'");
        }

        ExpProtocolInputCriteria criteria = null;
        if (pi.isSetCriteria())
        {
            String criteriaType = pi.getCriteria().getType();
            String criteriaConfig = pi.getCriteria().getStringValue();
            criteria = ExperimentServiceImpl.get().createProtocolInputCriteria(criteriaType, criteriaConfig);
        }

        int minOccurs = 1;
        if (pi.isSetMinOccurs())
            minOccurs = pi.getMinOccurs();

        Integer maxOccurs = null;
        if (pi.isSetMinOccurs())
            maxOccurs = pi.getMaxOccurs();

        ExpMaterialProtocolInput protocolInput = ExperimentServiceImpl.get().createMaterialProtocolInput(getContainer(), name, 0, input, sampleType, criteria, minOccurs, maxOccurs);

        savePropertyCollection(pi.getProperties(), protocolInput.getLSID(), protocolInput.getLSID(), null);

        return protocolInput;
    }

    private Map<String, Object> getSimplePropertiesMap(PropertyCollectionType xbProps) throws XarFormatException
    {
        Map<String, Object> mSimpleProperties = new HashMap<>();
        SimpleValueType[] aSVals = xbProps.getSimpleValArray();
        for (SimpleValueType sVal : aSVals)
        {
            String key = sVal.getOntologyEntryURI();
            Object val;
            SimpleTypeNames.Enum valType = sVal.getValueType();
            try
            {
                switch (valType.intValue())
                {
                    case (SimpleTypeNames.INT_INTEGER):
                        val = Integer.valueOf(sVal.getStringValue());
                        break;
                    case (SimpleTypeNames.INT_DOUBLE):
                        val = Double.valueOf(sVal.getStringValue());
                        break;
                    case (SimpleTypeNames.INT_DATE_TIME):
                        val = new Date(DateUtil.parseDateTime(getContainer(), sVal.getStringValue()));
                        break;
                    default:
                        val = sVal.getStringValue();
                }
            }
            catch (ConversionException e)
            {
                val = sVal.getStringValue();
                logErrorAndThrow("Failed to parse value " + val
                        + ":   Declared as type " + valType + " ; returned as string instead", e);
            }
            mSimpleProperties.put(key, val);
        }

        return mSimpleProperties;
    }

    private void loadMaterialWildcardProperties(MaterialType xbOriginal,
                                                String parentLSID,
                                                ExperimentRun run)
    {
        PropertyCollectionType xbProps = xbOriginal.getProperties();
        if (null == xbProps)
            xbProps = xbOriginal.addNewProperties();
        loadWildcardProperties(xbOriginal, parentLSID, run);
        xbOriginal.setProperties(xbProps);
    }

    private void loadDataWildcardProperties(DataType xbOriginal,
                                            String parentLSID,
                                            ExperimentRun run)
    {
        PropertyCollectionType xbProps = xbOriginal.getProperties();
        if (null == xbProps)
            xbProps = xbOriginal.addNewProperties();
        loadWildcardProperties(xbOriginal, parentLSID, run);
        xbOriginal.setProperties(xbProps);
    }

    private void loadWildcardProperties(XmlObject xObj,
                                        String parentLSID,
                                        ExperimentRun run)
    {
        XmlObject xElem;
        String key = null;

        XmlCursor c = xObj.newCursor();
        XmlCursor cTest;
        Map m = new HashMap();
        c.getAllNamespaces(m);
        String nsObject = c.getName().getNamespaceURI();

        c.selectPath("./*");

        while (c.hasNextSelection())
        {
            try
            {
                c.toNextSelection();
                String nsChild = c.getName().getNamespaceURI();

                // skip elements in the object's own namespace
                if (nsChild.equals(nsObject))
                    continue;

                key = c.getName().getLocalPart();
                xElem = c.getObject();
                cTest = c.newCursor();
                String stringValue = null;
                String propertyURI = nsChild + "." + key;

                PropertyType propertyType = PropertyType.XML_TEXT;
                if (!xElem.isNil())
                {
                    stringValue = c.xmlText();

                    // clone the cursor and check for complex elements
                    // if none, also load value as string for usability.
                    if (!cTest.toFirstChild() && !cTest.toFirstAttribute())
                    {
                        propertyType = PropertyType.STRING;
                        stringValue = trimString(c.getTextValue());
                    }
                }

                OntologyManager.insertProperties(getContainer(), getUser(), run.getLSID(), new ObjectProperty(parentLSID, getContainer(), propertyURI, stringValue, propertyType));
            }
            catch (Exception e)
            {
                getLog().debug("Skipped element " + key + " exception " + e.getMessage(), e);
            }

        }
        c.dispose();
    }

    private static class DeferredDataLoad
    {
        private final ExpData _data;
        private final ExpRun _run;

        public DeferredDataLoad(ExpData data, ExpRun run)
        {
            _data = data;
            _run = run;
        }

        public ExpData getData()
        {
            return _data;
        }

        public ExpRun getRun()
        {
            return _run;
        }
    }
}
