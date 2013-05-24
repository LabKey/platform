/*
 * Copyright (c) 2013 LabKey Corporation
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
    LocalOrRefFiltersType customizeFilters(String name, TableInfo table, LocalOrRefFiltersType xmlFilters);

    Collection<QueryService.ParameterDecl> customizeParameters(String name, TableInfo table, LocalOrRefFiltersType xmlFilters);

    Map<String, Object> customizeParamValues(TableInfo table);
}
