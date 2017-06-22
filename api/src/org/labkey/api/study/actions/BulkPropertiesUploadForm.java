/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.PipelineDataCollector;
import org.labkey.api.view.ActionURL;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public Map<DomainProperty, String> getRunProperties() throws ExperimentException
    {
        if (_runProperties == null)
        {
            _runProperties = new HashMap<>(super.getRunProperties());
            if (isBulkUploadAttempted())
            {
                Map<String, Object> values = getBulkProperties();
                for (DomainProperty prop : _runProperties.keySet())
                {
                    _runProperties.put(prop, getPropertyValue(values, prop));
                }
            }
        }
        return _runProperties;
    }

    /** @return String-ified version of the property from the bulk set of values */
    protected String getPropertyValue(Map<String, Object> values, DomainProperty prop)
    {
        Object value = values.get(prop.getName());
        if (value == null)
        {
            value = values.get(prop.getLabel());
        }
        return value == null ? null : value.toString();
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
                _bulkProperties = new ArrayList<>(maps.size());

                for (Map<String, Object> map : maps)
                {
                    _bulkProperties.add(new CaseInsensitiveHashMap<>(map));
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

    public void saveDefaultRunValues() throws ExperimentException
    {
        if (!isBulkUploadAttempted())
        {
            // Only save values that the user entered into the form, not ones from a pasted TSV
            super.saveDefaultRunValues();
        }
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

    protected ExpMaterial resolveSample(String name)
            throws ExperimentException
    {
        List<? extends ExpMaterial> materials = ExperimentService.get().getExpMaterialsByName(name, getContainer(), getUser());
        if (materials.size() == 1)
        {
            return materials.get(0);
        }
        // Couldn't find exactly one match, check if it might be of the form <SAMPLE_SET_NAME>.<SAMPLE_NAME>
        int dotIndex = name.indexOf(".");
        if (dotIndex != -1)
        {
            String sampleSetName = name.substring(0, dotIndex);
            String sampleName = name.substring(dotIndex + 1);
            // Could easily do some caching here, but probably not a significant perf issue
            for (ExpSampleSet sampleSet : ExperimentService.get().getSampleSets(getContainer(), getUser(), true))
            {
                // Look for a sample set with the right name
                if (sampleSetName.equals(sampleSet.getName()))
                {
                    for (ExpMaterial sample : sampleSet.getSamples())
                    {
                        // Look for a sample with the right name
                        if (sample.getName().equals(sampleName))
                        {
                            return sample;
                        }
                    }
                }
            }
        }

        // If we can't find a <SAMPLE_SET_NAME>.<SAMPLE_NAME> match, then fall back on the original results
        if (materials.isEmpty())
        {
            throw new ExperimentException("No sample with name '" + name + "' was found.");
        }
        // Must be more than one match
        throw new ExperimentException("Found samples with name '" + name + "' in multiple sample sets. Please prefix the name with the desired sample set, in the format 'SAMPLE_SET.SAMPLE'.");
    }

    public abstract String getHelpPopupHTML();

}
