package org.labkey.api.specimen.importer;

import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.specimen.DefaultSpecimenTablesTemplate;
import org.labkey.api.study.SpecimenTablesTemplate;

import java.util.Collections;
import java.util.Set;

/**
 * A specimen tables template implementation that only provides the required (built in) fields but none of the optional
 * fields. This makes it easier to handle both additions and subtractions to the specimen domains on export/import.
 */
public class ImportTemplate extends DefaultSpecimenTablesTemplate implements SpecimenTablesTemplate
{
    @Override
    public Set<PropertyStorageSpec> getExtraSpecimenEventProperties()
    {
        return Collections.emptySet();
    }

    @Override
    public Set<PropertyStorageSpec> getExtraVialProperties()
    {
        return Collections.emptySet();
    }

    @Override
    public Set<PropertyStorageSpec> getExtraSpecimenProperties()
    {
        return Collections.emptySet();
    }
}
