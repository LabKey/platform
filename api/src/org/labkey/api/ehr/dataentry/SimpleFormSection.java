package org.labkey.api.ehr.dataentry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ehr.EHRService;
import org.labkey.api.ehr.dataentry.AbstractFormSection;
import org.labkey.api.ehr.dataentry.FormElement;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: bimber
 * Date: 4/27/13
 * Time: 10:54 AM
 */
public class SimpleFormSection extends AbstractFormSection
{
    private String _schemaName;
    private String _queryName;

    public SimpleFormSection(String schemaName, String queryName, String label, String xtype)
    {
        this(schemaName, queryName, label, xtype, EHRService.FORM_SECTION_LOCATION.Body);
    }

    public SimpleFormSection(String schemaName, String queryName, String label, String xtype, EHRService.FORM_SECTION_LOCATION location)
    {
        super(queryName, label, xtype, location);
        _schemaName = schemaName;
        _queryName = queryName;
    }

    @Override
    public Set<Pair<String, String>> getTableNames()
    {
        Set<Pair<String, String>> tables = new HashSet<>();
        tables.add(Pair.of(_schemaName, _queryName));
        return tables;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);

        JSONArray queries = new JSONArray();

        JSONObject q = new JSONObject();
        q.put("schemaName", _schemaName);
        q.put("queryName", _queryName);
        queries.put(q);

        json.put("queries", queries);
        return json;
    }

    @Override
    protected List<FormElement> getFormElements(Container c, User u)
    {
        List<FormElement> list = new ArrayList<>();
        for (TableInfo ti : getTables(c, u))
        {
            List<FieldKey> keys = EHRService.get().getDefaultFieldKeys(ti);
            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(ti, keys);
            for (FieldKey key : keys)
            {
                if (!cols.containsKey(key))
                {
                    _log.error("Unable to find field: " +  key.toString() + " on table " + ti.getPublicSchemaName() + "." + ti.getPublicName());
                    continue;
                }

                list.add(FormElement.createForColumn(cols.get(key)));
            }
        }

        return list;
    }
}
