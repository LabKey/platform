package org.labkey.api.query;

import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewForm;

import javax.servlet.ServletException;

public class QueryForm extends ViewForm
{
    public static final String PARAMVAL_NOFILTER = "NONE";
    private UserSchema _schema;
    private QueryDefinition _queryDef;
    private CustomView _columnList;
    private QuerySettings _querySettings;
    private boolean _exportAsWebPage = false;
    private String _queryViewActionURL;

    protected UserSchema createSchema()
    {
        UserSchema ret = null;
        String schemaName = getSchemaName();
        if (schemaName != null)
        {
            UserSchema baseSchema = (UserSchema) DefaultSchema.get(getUser(), getContainer()).getSchema(schemaName);
            if (baseSchema == null)
            {
                return null;
            }
            QuerySettings settings = createQuerySettings(baseSchema);
            try
            {
                return baseSchema.createView(getViewContext(), settings).getSchema();
            }
            catch (ServletException e)
            {
                throw UnexpectedException.wrap(e);
            }

        }
        return ret;
    }

    final public QuerySettings getQuerySettings()
    {
        if (_querySettings == null)
        {
            UserSchema schema = getSchema();
            if (schema != null)
                _querySettings = createQuerySettings(schema);
        }
        return _querySettings;
    }

    protected QuerySettings createQuerySettings(UserSchema schema)
    {
        return schema.getSettings(getViewContext().getActionURL(), getDataRegionName());
    }

    protected String getDataRegionName()
    {
        return QueryView.DATAREGIONNAME_DEFAULT;
    }

    public String getSchemaName()
    {
        return getViewContext().getRequest().getParameter(QueryParam.schemaName.toString());
    }

    public UserSchema getSchema()
    {
        if (_schema == null)
        {
            _schema = createSchema();
        }
        return _schema;
    }

    public QueryDefinition getQueryDef()
    {
        if (getQueryName() == null)
            return null;
        if (_queryDef == null)
        {
            _queryDef = QueryService.get().getQueryDef(getContainer(), getSchemaName(), getQueryName());
        }
        if (_queryDef == null)
        {
            _queryDef = getSchema().getQueryDefForTable(getQueryName());
        }
        return _queryDef;
    }

    public ActionURL urlFor(QueryAction action)
    {
        ActionURL ret = getSchema().urlFor(action, getQueryDef());
        if (_columnList != null && _columnList.getName() != null)
        {
            ret.replaceParameter(QueryParam.viewName.toString(), _columnList.getName());
        }
        return ret;
    }

    public boolean isExportAsWebPage()
    {
        return _exportAsWebPage;
    }

    public void setExportAsWebPage(boolean exportAsWebPage)
    {
        _exportAsWebPage = exportAsWebPage;
    }

    public CustomView getCustomView()
    {
        if (_columnList != null)
            return _columnList;
        if (getQuerySettings() == null)
            return null;
        String columnListName = getQuerySettings().getViewName();
        _columnList = getQueryDef().getCustomView(getUser(), getRequest(), columnListName);
        return _columnList;
    }

    final public String getQueryName()
    {
        return getQuerySettings() != null ? getQuerySettings().getQueryName() : null;
    }

    public boolean canEdit()
    {
        return null != getQueryDef() && getQueryDef().canEdit(getUser());
    }

    public String getQueryViewActionURL()
    {
        return _queryViewActionURL;
    }

    public void setQueryViewActionURL(String queryViewActionURL)
    {
        _queryViewActionURL = queryViewActionURL;
    }
}
