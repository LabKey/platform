package org.labkey.api.ehr.demographics;

import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 7/9/13
 * Time: 9:42 PM
 */
abstract public class AbstractDemographicsProvider implements DemographicsProvider
{
    protected static final Logger _log = Logger.getLogger(AbstractDemographicsProvider.class);

    protected String _schemaName = "study";
    protected String _queryName;

    public AbstractDemographicsProvider(String queryName)
    {
        _queryName = queryName;
    }

    public boolean isAvailable(Container c)
    {
        return c.getActiveModules().contains(ModuleLoader.getInstance().getModule("ehr"));
    }

    public Map<String, Map<String, Object>> getProperties(Container c, User u, Collection<String> ids)
    {
        final Map<String, Map<String, Object>> ret = new HashMap<String, Map<String, Object>>();
        final TableInfo ti = getTableInfo(c, u);

        SimpleFilter filter = getFilter(ids);
        final Map<FieldKey, ColumnInfo> cols = getColumns(ti);
        TableSelector ts = new TableSelector(ti, cols.values(), filter, null);
        ts.setForDisplay(true);

        ts.forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet object) throws SQLException
            {
                Results rs = new ResultsImpl(object, cols);

                String id = rs.getString(FieldKey.fromString(ti.getColumn("Id").getSelectName()));

                Map<String, Object> map = ret.get(id);
                if (map == null)
                    map = new HashMap<String, Object>();

                processRow(rs, cols, map);

                ret.put(id, map);
            }
        });

        return ret;
    }

    protected TableInfo getTableInfo(Container c, User u)
    {
        UserSchema us = QueryService.get().getUserSchema(u, c, _schemaName);
        if (us == null)
        {
            throw new IllegalArgumentException("Schema " + _schemaName + " not found in the container: " + c.getPath());
        }

        final TableInfo ti = us.getTable(_queryName);
        if (ti == null)
        {
            throw new IllegalArgumentException("Table: " + _queryName + " not found in the container: " + c.getPath());
        }

        return ti;
    }

    protected SimpleFilter getFilter(Collection<String> ids)
    {
        return new SimpleFilter(FieldKey.fromString("Id"), ids, CompareType.IN);
    }

    protected void processRow(Results rs, Map<FieldKey, ColumnInfo> cols, Map<String, Object> map) throws SQLException
    {
        for  (FieldKey key : cols.keySet())
        {
            map.put(key.toString(), rs.getObject(key));
        }
    }

    protected Map<FieldKey, ColumnInfo> getColumns(TableInfo ti)
    {
        Set<FieldKey> keys = new HashSet<FieldKey>();
        keys.add(FieldKey.fromString("Id"));
        keys.addAll(getFieldKeys());

        return QueryService.get().getColumns(ti, keys);
    }

    abstract protected Collection<FieldKey> getFieldKeys();
}
