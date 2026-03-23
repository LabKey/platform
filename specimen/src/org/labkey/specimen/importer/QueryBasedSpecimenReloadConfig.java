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
