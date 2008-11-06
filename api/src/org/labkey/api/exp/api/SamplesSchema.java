/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
    public static final String SCHEMA_NAME = "Samples";

    static private Map<String, ExpSampleSet> getSampleSetMap(Container container, User user)
    {
        Map<String, ExpSampleSet> map = new TreeMap<String, ExpSampleSet>();
        for (ExpSampleSet ss : ExperimentService.get().getSampleSets(container, user, false))
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
                Map<String, ExpSampleSet> map = getSampleSetMap(schema.getContainer(), schema.getUser());
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
    private ContainerFilter _containerFilter = ContainerFilter.CURRENT;

    private SamplesSchema(User user, Container container, Map<String, ExpSampleSet> sampleSetMap)
    {
        super("Samples", user, container, ExperimentService.get().getSchema());
        _sampleSetMap = sampleSetMap;
    }

    public void setContainerFilter(ContainerFilter containerFilter)
    {
        _containerFilter = containerFilter;
    }

    protected Map<String, ExpSampleSet> getSampleSets()
    {
        if (_sampleSetMap == null)
        {
            _sampleSetMap = getSampleSetMap(getContainer(), getUser());
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
        ret.setContainerFilter(_containerFilter);
        ret.populate(ss, true);
        return ret;
    }

    public ForeignKey materialIdForeignKey(final ExpSampleSet ss)
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpMaterialTable ret = ExperimentService.get().createMaterialTable("lookup", SamplesSchema.this);
                ret.populate(ss, false);
                return ret;
            }
        };
    }
}
