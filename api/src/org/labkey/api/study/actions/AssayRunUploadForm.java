/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.*;
import org.labkey.api.study.assay.*;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.ACL;
import org.labkey.api.util.GUID;

import java.util.*;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * User: brittp
* Date: Jul 11, 2007
* Time: 2:52:54 PM
*/
public class AssayRunUploadForm extends ProtocolIdForm implements AssayRunUploadContext
{
    private Map<PropertyDescriptor, String> _uploadSetProperties = null;
    private Map<PropertyDescriptor, String> _runProperties = null;
    private Map<String, String> _runSamplesByCaption = null;
    private Map<String, String> _runSamplesByFormElementName = null;
    private String _comments;
    private String _name;
    private String _dataCollectorName;
    private boolean _multiRunUpload;
    private String _uploadStep;
    private String _targetStudy;
    private boolean _ignoreWarnings;
    private boolean _resetDefaultValues;
    private Map<String, File> _uploadedData;
    private boolean _successfulUploadComplete;
    private String _uploadAttemptID = GUID.makeGUID();
    private Map<PropertyDescriptor, File> _additionalFiles;

    // Unfortunate query hackery that orders display columns based on default view
    protected PropertyDescriptor[] reorderDomainColumns(PropertyDescriptor[] unorderedColumns, ViewContext context, ExpProtocol protocol)
    {
        Map<String, PropertyDescriptor> nameToCol = new HashMap<String, PropertyDescriptor>();
        // unfortunately, we have to match on label/caption when mapping propertydescriptors to columninfo objects;
        // there are no other pieces of data that are the same.
        for (PropertyDescriptor pd : unorderedColumns)
            nameToCol.put(pd.getNonBlankLabel(), pd);

        List<PropertyDescriptor> orderedColumns = new ArrayList<PropertyDescriptor>();
        // add all columns that are found in the default view in the correct order:
        QueryView dataView = getProvider().createRunDataView(context, protocol);
        List<DisplayColumn> allColumns = dataView.getDisplayColumns();
        for (DisplayColumn dc : allColumns)
        {
            if (!dc.isEditable())
                continue;

            if (dc instanceof UrlColumn)
                continue;

            PropertyDescriptor col = nameToCol.get(dc.getCaption());
            if (col != null)
            {
                orderedColumns.add(col);
                nameToCol.remove(dc.getCaption());
            }
        }
        // add the remaining columns:
        for (PropertyDescriptor col : nameToCol.values())
            orderedColumns.add(col);
        return orderedColumns.toArray(new PropertyDescriptor[orderedColumns.size()]);
    }

    public PropertyDescriptor[] getRunDataProperties()
    {
        AssayProvider provider = AssayService.get().getProvider(getProtocol());
        PropertyDescriptor[] properties = provider.getRunDataColumns(getProtocol());
        return reorderDomainColumns(properties, getViewContext(), getProtocol());
    }

    public Map<PropertyDescriptor, String> getRunProperties()
    {
        if (_runProperties == null)
        {
            AssayProvider provider = AssayService.get().getProvider(getProtocol());
            PropertyDescriptor[] properties = provider.getRunInputPropertyColumns(getProtocol());
            properties = reorderDomainColumns(properties, getViewContext(), getProtocol());
            _runProperties = getPropertyMapFromRequest(Arrays.asList(properties));
        }
        return _runProperties;
    }

    public String getFormElementName(PropertyDescriptor pd)
    {
        return ColumnInfo.propNameFromName(pd.getName());
    }

    public Map<String, String> getRunSamplesByCaption()
    {
        if (_runSamplesByCaption == null)
        {
            _runSamplesByCaption = new HashMap<String, String>();
            // We've been creating bad assay definitions into the database - they all have MaxInputMaterialPerInstance
            // set to 0 when they should be set to 1, but it was masked by a bug where getMaxInputMaterialPerInstance()
            // used to return the value for getMaxInputDataPerInstance(). For now, assume that there should be at
            // least one material input.
            Integer protocolInputCount = getProtocol().getMaxInputMaterialPerInstance();
            int inputCount = 1;
            if (protocolInputCount != null && protocolInputCount.intValue() > 1)
            {
                inputCount = protocolInputCount.intValue();
            }
            for (int i = 0; i < inputCount; i++)
            {
                String value = getRequest().getParameter("_sampleId" + i);
                if (value == null)
                    value = "Unknown";
                _runSamplesByCaption.put("Sample Id " + i, value);
            }
        }
        return _runSamplesByCaption;
    }

    public Map<String, String> getRunSamplesByFormElementName()
    {
        if (_runSamplesByFormElementName == null)
        {
            _runSamplesByFormElementName = new HashMap<String, String>();
            for (int i = 0; i < getProtocol().getMaxInputMaterialPerInstance(); i++)
            {
                String elementName = "_sampleId" + i;
                String value = getRequest().getParameter(elementName);
                if (value == null)
                    value = "Unknown";
                _runSamplesByFormElementName.put(elementName, value);
            }
        }
        return _runSamplesByFormElementName;
    }

    public Collection<String> getSampleIds()
    {
        return getRunSamplesByCaption().values();
    }

    /** @return property descriptor to value */
    public Map<PropertyDescriptor, String> getUploadSetProperties()
    {
        if (_uploadSetProperties == null)
        {
            AssayProvider provider = AssayService.get().getProvider(getProtocol());
            PropertyDescriptor[] properties = provider.getUploadSetColumns(getProtocol());
            properties = reorderDomainColumns(properties, getViewContext(), getProtocol());
            _uploadSetProperties = getPropertyMapFromRequest(Arrays.asList(properties));
        }
        return _uploadSetProperties;
    }

    protected Map<PropertyDescriptor, String> getPropertyMapFromRequest(List<PropertyDescriptor> columns)
    {
        Map<PropertyDescriptor, String> properties = new LinkedHashMap<PropertyDescriptor, String>();
        Map<PropertyDescriptor, File> additionalFiles = getAdditionalPostedFiles(columns);
        for (PropertyDescriptor pd : columns)
        {
            String propName = getFormElementName(pd);
            String value = getRequest().getParameter(propName);
            if (pd.isRequired() && pd.getPropertyType() == PropertyType.BOOLEAN && 
                    (value == null || value.length() == 0))
                value = Boolean.FALSE.toString();

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
        _uploadedData = Collections.emptyMap();
    }

    public AssayDataCollector getSelectedDataCollector()
    {
        List<AssayDataCollector> collectors = getProvider().getDataCollectors(Collections.<String, File>emptyMap());
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

    public Map<String, File> getUploadedData() throws ExperimentException
    {
        if (_uploadedData == null)
        {
            for (AssayDataCollector collector : getProvider().getDataCollectors(Collections.<String, File>emptyMap()))
            {
                if (collector.getShortName().equals(_dataCollectorName))
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
            }
            return Collections.emptyMap();
        }
        return _uploadedData;
    }

    public Map<PropertyDescriptor, File> getAdditionalPostedFiles(List<PropertyDescriptor> pds)
    {
        if (_additionalFiles == null)
        {
            Map<String, PropertyDescriptor> fileParameters = new HashMap<String, PropertyDescriptor>();
            for (PropertyDescriptor pd : pds)
            {
                if (pd.getPropertyType() == PropertyType.FILE_LINK)
                    fileParameters.put(getFormElementName(pd), pd);
            }

            if (!fileParameters.isEmpty())
            {
                AssayFileWriter writer = new AssayFileWriter();
                try
                {
                    Map<String, File> postedFiles = writer.savePostedFiles(this, fileParameters.keySet());
                    _additionalFiles = new HashMap<PropertyDescriptor, File>();
                    for (Map.Entry<String, File> entry : postedFiles.entrySet())
                        _additionalFiles.put(fileParameters.get(entry.getKey()), entry.getValue());
                }
                catch (ExperimentException e)
                {
                    throw new RuntimeException(e);
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

    public AssayProvider getProvider()
    {
        return AssayService.get().getProvider(getProviderName());
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
            _uploadStep = UploadWizardAction.UploadSetStepHandler.NAME;
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

    public boolean isIgnoreWarnings()
    {
        return _ignoreWarnings;
    }

    public void setIgnoreWarnings(boolean ignoreWarnings)
    {
        _ignoreWarnings = ignoreWarnings;
    }

    public String getUploadSetPropertyValue(PropertyDescriptor key, String value)
    {
        if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(key.getName()))
        {
            if (value == null || "".equals(value))
            {
                return "[None]";
            }
            Map<Container, String> targets = AssayPublishService.get().getValidPublishTargets(getUser(), ACL.PERM_READ);
            Container container = ContainerManager.getForId(value);
            String studyName = targets.get(container);
            if (studyName != null)
            {
                return container.getPath() + " (" + studyName + ")";
            }
        }
        else if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equals(key.getName()))
        {
            return AbstractAssayProvider.findType(value, getProvider().getParticipantVisitResolverTypes()).getDescription();
        }

        if (key.getLookupQuery() != null)
        {
            PdLookupForeignKey lookupKey = new PdLookupForeignKey(getUser(), key);
            TableInfo lookupTable = lookupKey.getLookupTableInfo();
            if (lookupTable != null)
            {
                List<ColumnInfo> pks = lookupTable.getPkColumns();
                if (pks.size() == 1)
                {
                    SimpleFilter filter = new SimpleFilter(pks.get(0).getName(), value);
                    try
                    {
                        Map[] maps =  Table.selectForDisplay(lookupTable, Collections.singleton(lookupTable.getTitleColumn()), filter, null, Map.class);
                        if (maps.length > 0)
                        {
                            Object title = maps[0].get(lookupTable.getTitleColumn());
                            if (title != null)
                                value = title.toString();
                        }
                    }
                    catch (SQLException e)
                    {
                        throw new RuntimeSQLException(e);
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
}
