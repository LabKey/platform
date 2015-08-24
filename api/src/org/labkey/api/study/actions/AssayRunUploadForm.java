/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayDataCollector;
import org.labkey.api.study.assay.AssayFileWriter;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.ParticipantVisitResolverType;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
* Date: Jul 11, 2007
* Time: 2:52:54 PM
*/
public class AssayRunUploadForm<ProviderType extends AssayProvider> extends ProtocolIdForm implements AssayRunUploadContext<ProviderType>
{
    protected Map<DomainProperty, String> _uploadSetProperties = null;
    protected Map<DomainProperty, String> _runProperties = null;
    private String _comments;
    private String _name;
    private String _dataCollectorName;
    private boolean _multiRunUpload;
    private String _uploadStep;
    private String _targetStudy;
    private boolean _resetDefaultValues;
    private Map<String, File> _uploadedData;
    private boolean _successfulUploadComplete;
    private String _uploadAttemptID = GUID.makeGUID();
    private Map<DomainProperty, File> _additionalFiles;
    private Integer _batchId;
    private Integer _reRunId;
    protected BindException _errors;
    private List<AssayDataCollector> _collectors;
    private TransformResult _transformResult = DefaultTransformResult.createEmptyResult();
    private ExpRun _reRun;

    public List<? extends DomainProperty> getRunDataProperties()
    {
        Domain domain = getProvider().getResultsDomain(getProtocol());
        return domain.getProperties();
    }

    public Map<DomainProperty, String> getRunProperties() throws ExperimentException
    {
        if (_runProperties == null)
        {
            Domain domain = getProvider().getRunDomain(getProtocol());
            List<? extends DomainProperty> properties = domain.getProperties();
            _runProperties = getPropertyMapFromRequest(properties);
        }
        return Collections.unmodifiableMap(_runProperties);
    }

    /** @return property descriptor to value */
    public Map<DomainProperty, String> getBatchProperties() throws ExperimentException
    {
        if (_uploadSetProperties == null)
        {
            Domain domain = getProvider().getBatchDomain(getProtocol());
            _uploadSetProperties = getPropertyMapFromRequest(domain.getProperties());
        }
        return Collections.unmodifiableMap(_uploadSetProperties);
    }

    protected Map<DomainProperty, String> getPropertyMapFromRequest(List<? extends DomainProperty> columns) throws ExperimentException
    {
        Map<DomainProperty, String> properties = new LinkedHashMap<>();
        Map<DomainProperty, File> additionalFiles = getAdditionalPostedFiles(columns);
        for (DomainProperty pd : columns)
        {
            String propName = UploadWizardAction.getInputName(pd);
            String value = getRequest().getParameter(propName);
            if (pd.getPropertyDescriptor().getPropertyType() == PropertyType.BOOLEAN &&
                    (value == null || value.length() == 0))
                value = Boolean.FALSE.toString();
            value = StringUtils.trimToNull(value);

            if (pd.getName().equals(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME) && value != null)
            {
                value = ParticipantVisitResolverType.Serializer.encode(value, getRequest());
            }

            if (additionalFiles.containsKey(pd))
                properties.put(pd, additionalFiles.get(pd).getPath());
            else
                properties.put(pd, value);
        }
        return properties;
    }

    public String getComments()
    {
        return _comments;
    }

    public void setComments(String comments)
    {
        _comments = comments;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getDataCollectorName()
    {
        return _dataCollectorName;
    }

    public void setDataCollectorName(String dataCollectorName)
    {
        _dataCollectorName = dataCollectorName;
    }

    /** Used after the data has been successfully loaded - we don't need to offer to use it again */ 
    public void clearUploadedData()
    {
        _uploadedData = null;
        _runProperties = null;
    }

    private List<AssayDataCollector> getDataCollectors()
    {
        if (_collectors == null)
            _collectors = getProvider().getDataCollectors(Collections.<String, File>emptyMap(), this);
        return _collectors;
    }

    public AssayDataCollector getSelectedDataCollector()
    {
        List<AssayDataCollector> collectors = getDataCollectors();
        for (AssayDataCollector collector : collectors)
        {
            if (collector.getShortName().equals(_dataCollectorName))
            {
                return collector;
            }
        }
        if (collectors.size() == 1)
        {
            return collectors.get(0);
        }
        return null;
    }

    @Override @NotNull
    public Map<String, File> getUploadedData() throws ExperimentException
    {
        if (_uploadedData == null)
        {
            AssayDataCollector collector = getSelectedDataCollector();
            if (collector != null)
            {
                try
                {
                    _uploadedData = collector.createData(this);
                }
                catch (IOException e)
                {
                    throw new ExperimentException(e);
                }
                return _uploadedData;
            }
            return Collections.emptyMap();
        }
        return _uploadedData;
    }

    public Map<DomainProperty, File> getAdditionalPostedFiles(List<? extends DomainProperty> pds) throws ExperimentException
    {
        if (_additionalFiles == null)
        {
            Map<String, DomainProperty> fileParameters = new HashMap<>();
            for (DomainProperty pd : pds)
            {
                if (pd.getPropertyDescriptor().getPropertyType() == PropertyType.FILE_LINK)
                    fileParameters.put(UploadWizardAction.getInputName(pd), pd);
            }

            if (!fileParameters.isEmpty())
            {
                AssayFileWriter writer = new AssayFileWriter();
                try
                {
                    Map<String, File> postedFiles = writer.savePostedFiles(this, fileParameters.keySet());
                    _additionalFiles = new HashMap<>();
                    for (Map.Entry<String, File> entry : postedFiles.entrySet())
                        _additionalFiles.put(fileParameters.get(entry.getKey()), entry.getValue());
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
            else
                _additionalFiles = Collections.emptyMap();
        }
        return _additionalFiles;
    }

    @NotNull
    @Override
    public Map<Object, String> getInputDatas()
    {
        return Collections.emptyMap();
    }

    @NotNull
    public ProviderType getProvider()
    {
        return (ProviderType)super.getProvider();
    }

    public ActionURL getActionURL()
    {
        return getViewContext().getActionURL();
    }

    public boolean isMultiRunUpload()
    {
        return _multiRunUpload;
    }

    public void setMultiRunUpload(boolean multiRunUpload)
    {
        _multiRunUpload = multiRunUpload;
    }

    public String getUploadStep()
    {
        return _uploadStep;
    }

    public void setUploadStep(String step)
    {
        if (step == null)
        {
            _uploadStep = UploadWizardAction.BatchStepHandler.NAME;
        }
        else
        {
            _uploadStep = step;
        }
    }

    public String getTargetStudy()
    {
        return _targetStudy;
    }

    public void setTargetStudy(String targetStudy)
    {
        _targetStudy = targetStudy;
    }

    public String getBatchPropertyValue(PropertyDescriptor key, String value)
    {
        if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(key.getName()))
        {
            if (value == null || "".equals(value))
            {
                return "[None]";
            }
            Set<Study> targets = AssayPublishService.get().getValidPublishTargets(getUser(), ReadPermission.class);
            Container container = ContainerManager.getForId(value);
            Study study = StudyService.get().getStudy(container);
            if (study != null && targets.contains(study))
            {
                return container.getPath() + " (" + study.getLabel() + ")";
            }
        }
        else if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equals(key.getName()))
        {
            return AbstractAssayProvider.findType(value, getProvider().getParticipantVisitResolverTypes()).getDescription();
        }

        if (key.getLookupQuery() != null)
        {
            PdLookupForeignKey lookupKey = new PdLookupForeignKey(getUser(), key, getContainer());
            TableInfo lookupTable = lookupKey.getLookupTableInfo();
            if (lookupTable != null)
            {
                List<ColumnInfo> pks = lookupTable.getPkColumns();
                if (pks.size() == 1)
                {
                    ColumnInfo pk = pks.get(0);
                    try
                    {
                        Object filterValue = ConvertUtils.convert(value, pk.getJavaClass());
                        SimpleFilter filter = new SimpleFilter(pk.getFieldKey(), filterValue);
                        Set<String> cols = new HashSet<>();
                        cols.add(lookupTable.getTitleColumn());
                        cols.add(pks.get(0).getName());
                        Map<String, Object>[] maps = new TableSelector(lookupTable, cols, filter, null).setForDisplay(true).getMapArray();
                        if (maps.length > 0)
                        {
                            Object title = maps[0].get(lookupTable.getTitleColumn());
                            if (title != null)
                                value = title.toString();
                        }
                    }
                    catch (ConversionException e)
                    {
                        // Use the raw value as the display value
                    }
                }
            }
        }
        return value;
    }

    public boolean isResetDefaultValues()
    {
        return _resetDefaultValues;
    }

    public void setResetDefaultValues(boolean resetDefaultValues)
    {
        _resetDefaultValues = resetDefaultValues;
    }

    public boolean isSuccessfulUploadComplete()
    {
        return _successfulUploadComplete;
    }

    public void setSuccessfulUploadComplete(boolean successfulUploadComplete)
    {
        _successfulUploadComplete = successfulUploadComplete;
    }
    
    public String getUploadAttemptID()
    {
        return _uploadAttemptID;
    }

    public void setUploadAttemptID(String uploadAttemptID)
    {
        _uploadAttemptID = uploadAttemptID;
    }

    public void resetUploadAttemptID()
    {
        _uploadAttemptID = GUID.makeGUID();
    }

    public Integer getBatchId()
    {
        return _batchId;
    }

    public void setBatchId(Integer batchId)
    {
        _batchId = batchId;
    }


    public void clearDefaultValues(Domain domain) throws ExperimentException
    {
        DefaultValueService.get().clearDefaultValues(getContainer(), domain, getUser());
    }

    // Issue 22698 - don't retain uploaded files as default values
    private void stripFileProperties(Map<DomainProperty, Object> values)
    {
        for (DomainProperty prop: values.keySet())
        {
            if (prop.getPropertyDescriptor().getPropertyType() == PropertyType.FILE_LINK)
            {
                values.put(prop, null);
            }
        }
    }

    public void saveDefaultBatchValues() throws ExperimentException
    {
        Map<DomainProperty, Object> objectMap = new HashMap<DomainProperty, Object>(getBatchProperties());
        stripFileProperties(objectMap);
        DefaultValueService.get().setDefaultValues(getContainer(), objectMap, getUser());
    }

    public void saveDefaultRunValues() throws ExperimentException
    {
        Map<DomainProperty, Object> objectMap = new HashMap<DomainProperty, Object>(getRunProperties());
        stripFileProperties(objectMap);
        DefaultValueService.get().setDefaultValues(getContainer(), objectMap, getUser());
    }

    public void saveDefaultValues(Map<DomainProperty, String> values, String scope) throws ExperimentException
    {
        if (values.isEmpty())
            return;

        Map<DomainProperty, Object> objectMap = new HashMap<DomainProperty, Object>(values);
        DefaultValueService.get().setDefaultValues(getContainer(), objectMap, getUser(), scope);
    }

    public Map<DomainProperty, Object> getDefaultValues(Domain domain, String scope) throws ExperimentException
    {
        if (isResetDefaultValues())
            clearDefaultValues(domain);
        return DefaultValueService.get().getDefaultValues(getContainer(), domain, getUser(), scope);
    }

    public Map<DomainProperty, Object> getDefaultValues(Domain domain) throws ExperimentException
    {
        ExpRun reRun = getReRun();
        if (reRun != null)
        {
            Map<DomainProperty, Object> ret = new HashMap<>();
            String batchDomainURI = AbstractAssayProvider.getDomainURIForPrefix(getProtocol(), ExpProtocol.ASSAY_DOMAIN_BATCH);
            String runDomainURI = AbstractAssayProvider.getDomainURIForPrefix(getProtocol(), ExpProtocol.ASSAY_DOMAIN_RUN);
            if (batchDomainURI.equals(domain.getTypeURI()))
            {
                // we're getting batch values
                ExpExperiment batch = AssayService.get().findBatch(reRun);
                if (batch != null)
                {
                    Map<String, ObjectProperty> batchProperties = batch.getObjectProperties();
                    for (DomainProperty property : domain.getProperties())
                    {
                        ObjectProperty value = batchProperties.get(property.getPropertyURI());
                        ret.put(property, value != null ? value.value() : null);
                    }
                }
            }
            else if (runDomainURI.equals(domain.getTypeURI()))
            {
                // we're getting run values
                Map<String, Object> values = OntologyManager.getProperties(getContainer(), reRun.getLSID());
                for (DomainProperty property : domain.getProperties())
                    ret.put(property, values.get(property.getPropertyURI()));

                // bad hack here to temporarily create domain properties for name and comments.  These need to be
                // repopulated just like the rest of the domain properties, but they aren't actually part of the
                // domain- they're hard columns on the ExperimentRun table.  Since the DomainProperty objects are
                // just used to calculate the input form element names, this hack works to pre-populate the values.
                DomainProperty nameProperty = domain.addProperty();
                nameProperty.setName("Name");
                ret.put(nameProperty, reRun.getName());
                nameProperty.delete();

                DomainProperty commentsProperty = domain.addProperty();
                commentsProperty.setName("Comments");
                ret.put(commentsProperty, reRun.getComments());
                commentsProperty.delete();
            }
            return ret;
        }

        return getDefaultValues(domain, null);
    }

    public void setErrors(BindException errors)
    {
        _errors = errors;
    }

    public TransformResult getTransformResult()
    {
        return _transformResult;
    }

    public void setTransformResult(TransformResult transformResult)
    {
        _transformResult = transformResult;
    }

    public Integer getReRunId()
    {
        return _reRunId;
    }

    public void setReRunId(Integer reRunId)
    {
        _reRunId = reRunId;
    }

    public ExpRun getReRun()
    {
        if (_reRunId != null && _reRun == null)
        {
            _reRun = ExperimentService.get().getExpRun(_reRunId);
            if (_reRun == null)
            {
                throw new NotFoundException("Existing run " + _reRunId + " could not be found.");
            }
            if (!_reRun.getContainer().hasPermission(getUser(), ReadPermission.class))
            {
                throw new UnauthorizedException("You do not have read access for run id " + _reRunId);
            }
        }
        return _reRun;
    }

    public void uploadComplete(ExpRun run) throws ExperimentException
    {
        AssayDataCollector collector = getSelectedDataCollector();
        if (collector != null)
        {
            _uploadedData = collector.uploadComplete(this, run);
        }
    }

    @Override
    public Logger getLogger()
    {
        return null;
    }
}
