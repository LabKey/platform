package org.labkey.api.data;

import org.labkey.api.query.QueryService;
import org.labkey.data.xml.queryCustomView.LocalOrRefFiltersType;

import java.util.Collection;
import java.util.Map;

/**
 * User: kevink
 * Date: 5/13/13
 */
public interface LinkedSchemaCustomizer extends ExternalSchemaCustomizer
{
    Collection<QueryService.ParameterDecl> customizeParameters(String name, TableInfo table, LocalOrRefFiltersType xmlFilters);

    Map<String, Object> customizeParamValues(TableInfo table);
}
