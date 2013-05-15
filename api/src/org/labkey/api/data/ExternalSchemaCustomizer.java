package org.labkey.api.data;

import org.labkey.data.xml.queryCustomView.NamedFiltersType;

import java.util.Map;

/**
 * User: kevink
 * Date: 5/14/13
 */
public interface ExternalSchemaCustomizer extends UserSchemaCustomizer
{
    void customizeNamedFilters(Map<String, NamedFiltersType> filters);
}
