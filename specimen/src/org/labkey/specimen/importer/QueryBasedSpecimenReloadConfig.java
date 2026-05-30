/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.specimen.importer;

import org.labkey.api.study.SpecimenTransform;

public class QueryBasedSpecimenReloadConfig implements SpecimenTransform.ExternalImportConfig
{
    private String schemaName;
    private String queryName;
    private String viewName;

    public String getSchemaName()
    {
        return schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        this.schemaName = schemaName;
    }

    public String getQueryName()
    {
        return queryName;
    }

    public void setQueryName(String queryName)
    {
        this.queryName = queryName;
    }

    public String getViewName()
    {
        return viewName;
    }

    public void setViewName(String viewName)
    {
        this.viewName = viewName;
    }

    @Override
    public String getBaseServerUrl()
    {
        return null;
    }

    @Override
    public String getUsername()
    {
        return null;
    }

    @Override
    public String getPassword()
    {
        return null;
    }
}
