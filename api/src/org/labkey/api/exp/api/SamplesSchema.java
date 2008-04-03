package org.labkey.api.exp.api;

import org.labkey.api.security.User;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ForeignKey;

import java.util.Set;
import java.util.Map;
import java.util.TreeMap;

public class SamplesSchema extends UserSchema
{
    static private Map<String, ExpSampleSet> getSampleSetMap(Container container)
    {
        Map<String, ExpSampleSet> map = new TreeMap();
        for (ExpSampleSet ss : ExperimentService.get().getSampleSets(container, false))
        {
            map.put(ss.getName(), ss);
        }
        return map;
    }

    static public void register()
    {
        DefaultSchema.registerProvider("Samples", new DefaultSchema.SchemaProvider() {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                Map<String, ExpSampleSet> map = getSampleSetMap(schema.getContainer());
                if (map.isEmpty())
                    return null;
                return new SamplesSchema(schema.getUser(), schema.getContainer(), map); 
            }
        });
    }

    public SamplesSchema(User user, Container container)
    {
        this(user, container, null);        
    }

    private Map<String, ExpSampleSet> _sampleSetMap;
    private SamplesSchema(User user, Container container, Map<String, ExpSampleSet> sampleSetMap)
    {
        super("Samples", user, container, ExperimentService.get().getSchema());
        _sampleSetMap = sampleSetMap;
    }

    protected Map<String, ExpSampleSet> getSampleSets()
    {
        if (_sampleSetMap == null)
        {
            _sampleSetMap = getSampleSetMap(getContainer());
        }
        return _sampleSetMap;
    }

    public Set<String> getTableNames()
    {
        return getSampleSets().keySet();
    }

    public TableInfo getTable(String name, String alias)
    {
        ExpSampleSet ss = getSampleSets().get(name);
        if (ss == null)
            return super.getTable(name, alias);
        return getSampleTable(alias, ss);
    }

    public ExpMaterialTable getSampleTable(String alias, ExpSampleSet ss)
    {
        ExpMaterialTable ret = ExperimentService.get().createMaterialTable(alias, this);
        ret.populate(this, ss, true);
        return ret;
    }

    public ForeignKey materialIdForeignKey(final ExpSampleSet ss)
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpMaterialTable ret = ExperimentService.get().createMaterialTable("lookup", SamplesSchema.this);
                ret.populate(SamplesSchema.this, ss, false);
                return ret;
            }
        };
    }
}
