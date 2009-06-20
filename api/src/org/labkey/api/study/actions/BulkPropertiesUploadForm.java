/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.PipelineDataCollector;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.util.*;

/**
 * User: jeckels
 * Date: May 31, 2009
 */
public abstract class BulkPropertiesUploadForm<ProviderType extends AssayProvider> extends AssayRunUploadForm<ProviderType>
{
    private List<Map<String, Object>> _bulkProperties;
    private ActionURL _templateURL;

    protected abstract String getIdentifierColumnName();

    public boolean isBulkUploadAttempted()
    {
        return "on".equals(getRequest().getParameter(BulkPropertiesDisplayColumn.ENABLED_FIELD_NAME));
    }

    @Override
    /** To support bulk properties you must be using the pipeline to choose your source files */
    public PipelineDataCollector<BulkPropertiesUploadForm<ProviderType>> getSelectedDataCollector()
    {
        return (PipelineDataCollector)super.getSelectedDataCollector();
    }

    public abstract Map<String, Object> getBulkProperties() throws ExperimentException;

    @Override
    public Map<DomainProperty, String> getRunProperties()
    {
        if (_runProperties == null)
        {
            _runProperties = new HashMap<DomainProperty, String>(super.getRunProperties());
            if (isBulkUploadAttempted())
            {
                try
                {
                    Map<String, Object> values = getBulkProperties();
                    for (DomainProperty prop : _runProperties.keySet())
                    {
                        Object value = values.get(prop.getName());
                        if (value == null)
                        {
                            value = values.get(prop.getLabel());
                        }
                        _runProperties.put(prop, value == null ? null : value.toString());
                    }
                }
                catch (ExperimentException e)
                {
                    throw new UnexpectedException(e);
                }
            }
        }
        return _runProperties;
    }

    protected List<Map<String, Object>> getParsedBulkProperties() throws ExperimentException
    {
        if (_bulkProperties == null)
        {
            String tsv = getRawBulkProperties();
            try
            {
                TabLoader loader = new TabLoader(tsv, true);
                List<Map<String, Object>> maps = loader.load();
                _bulkProperties = new ArrayList<Map<String, Object>>(maps.size());
                for (Map<String, Object> map : maps)
                {
                    _bulkProperties.add(new CaseInsensitiveHashMap<Object>(map));
                }
            }
            catch (IOException e)
            {
                // Shouldn't get this from an in-memory TSV parse
                throw new ExperimentException(e);
            }

            Set<String> identifiers = new CaseInsensitiveHashSet();
            for (Map<String, Object> row : _bulkProperties)
            {
                String identifier = row.get(getIdentifierColumnName()) == null ? null : row.get(getIdentifierColumnName()).toString();
                if (identifier == null || identifier.equals(""))
                {
                    throw new ExperimentException("All rows must have a " + getIdentifierColumnName() + " value.");
                }
                if (!identifiers.add(identifier))
                {
                    throw new ExperimentException("Duplicate " + getIdentifierColumnName() + " value '" + identifier +
                            "' was found. All " +  getIdentifierColumnName() + " entries must be unique.");
                }
            }
        }
        return _bulkProperties;
    }

    public String getRawBulkProperties()
    {
        return getRequest().getParameter(BulkPropertiesDisplayColumn.PROPERTIES_FIELD_NAME);
    }

    public void setTemplateURL(ActionURL templateURL)
    {
        _templateURL = templateURL;
    }

    public ActionURL getTemplateURL()
    {
        return _templateURL;
    }

    /**
     * @param desiredIdentifiers values to look for in the identifier column. Will be searched for in order specified,
     * and if none are found the first will be used as the error message.
     */
    protected Map<String, Object> getProperties(String... desiredIdentifiers) throws ExperimentException
    {
        for (String desiredIdentifier : desiredIdentifiers)
        {
            for (Map<String, Object> props : getParsedBulkProperties())
            {
                Object possibleMatch = props.get(getIdentifierColumnName());
                String possibleMatchString = possibleMatch == null ? null : possibleMatch.toString();
                if (desiredIdentifier.equalsIgnoreCase(possibleMatchString))
                {
                    return props;
                }
            }
        }
        throw new ExperimentException("Could not find a row for " + getIdentifierColumnName() + " '" + desiredIdentifiers[0] + "'.");
    }
}
