package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 4/14/13
 * Time: 9:33 AM
 */
public class QueryTabbedReportItem extends TabbedReportItem
{
    private String _schemaName;
    private String _queryName;

    public QueryTabbedReportItem(DataProvider provider, String schemaName, String queryName, String label, String category)
    {
        super(provider, queryName, label, category);
        _schemaName = schemaName;
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

    public String getQueryName()
    {
        return _queryName;
    }

    public void setQueryName(String queryName)
    {
        _queryName = queryName;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, getSchemaName());
        if (us == null)
            return null;

        QueryDefinition qd = us.getQueryDefForTable(getQueryName());
        if (qd == null)
            return null;

        List<QueryException> errors = new ArrayList<QueryException>();
        TableInfo ti = qd.getTable(errors, true);
        if (errors.size() > 0)
        {
            _log.error("Unable to create tabbed report item for query: " + getSchemaName() + "." + getQueryName());
            for (QueryException e : errors)
            {
                _log.error(e.getMessage(), e);
            }
            return null;
        }

        if (ti == null)
        {
            return null;
        }

        inferColumnsFromTable(ti);
        JSONObject json = super.toJSON(c, u);

        json.put("schemaName", getSchemaName());
        json.put("queryName", getQueryName());

        return json;
    }
}
