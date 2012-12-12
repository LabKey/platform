package org.labkey.survey.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.Entity;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: 12/11/12
 */
public class SurveyDesign extends Entity
{
    private int _rowId;
    private String _label;
    private String _queryName;
    private String _schemaName;
    private String _metadata;

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    public String getSchemaName()
    {
        return _schemaName;
    }

    public void setSchemaName(String schemaName)
    {
        _schemaName = schemaName;
    }

    public String getMetadata()
    {
        return _metadata;
    }

    public void setMetadata(String metadata)
    {
        _metadata = metadata;
    }
}
