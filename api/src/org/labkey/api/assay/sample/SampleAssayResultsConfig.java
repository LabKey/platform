package org.labkey.api.assay.sample;

import org.jetbrains.annotations.Nullable;
import org.json.old.JSONObject;
import org.labkey.api.data.ContainerFilter;

/**
 * This class is for a module to define a configuration that will be used in the LKB/LKSM apps to display assay
 * results for a given sample in the Assays tabbed grid view of the app. This allows for non-standard module based
 * assays that are not part of the assay designer infrastructure to participate in the app results display.
 */
public class SampleAssayResultsConfig
{
    private String _moduleName;
    private String _title;
    private String _schemaName;
    private String _queryName;
    private String _viewName; //  optional
    private String _sampleRowKey; // optional sample row property to use for key in baseFilter, defaults to 'RowId' on client
    private String _filterKey; // field key of the query/view to use for the sample filter IN clause
    private ContainerFilter.Type _containerFilter; //  optional, defaults to 'Current' on client

    public SampleAssayResultsConfig(
            String title, String schemaName, String queryName, @Nullable String viewName,
            @Nullable String sampleRowKey, String filterKey, @Nullable ContainerFilter.Type containerFilter
        )
    {
        _title = title;
        _schemaName = schemaName;
        _queryName = queryName;
        _viewName = viewName;
        _sampleRowKey = sampleRowKey;
        _filterKey = filterKey;
        _containerFilter = containerFilter;
    }

    public String getTitle()
    {
        return _title;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public String getViewName()
    {
        return _viewName;
    }

    public String getSampleRowKey()
    {
        return _sampleRowKey;
    }

    public String getFilterKey()
    {
        return _filterKey;
    }

    public ContainerFilter.Type getContainerFilter()
    {
        return _containerFilter;
    }

    public String getModuleName()
    {
        return _moduleName;
    }

    public void setModuleName(String moduleName)
    {
        _moduleName = moduleName;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("moduleName", getModuleName());
        json.put("title", getTitle());
        json.put("schemaName", getSchemaName());
        json.put("queryName", getQueryName());
        if (getViewName() != null)
            json.put("viewName", getViewName());
        if (getSampleRowKey() != null)
            json.put("sampleRowKey", getSampleRowKey());
        json.put("filterKey", getFilterKey());
        if (getContainerFilter() != null)
            json.put("containerFilter", getContainerFilter().name());
        return json;
    }
}
