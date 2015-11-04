/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.api.exp.query;

import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.module.Module;
import org.labkey.api.security.User;
import org.labkey.api.query.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.NotFoundException;

import java.util.ArrayList;
import java.util.Set;
import java.util.Map;

public class SamplesSchema extends AbstractExpSchema
{
    public static final String SCHEMA_NAME = "samples";
    public static final String SCHEMA_DESCR = "Contains data about the samples used in experiment runs.";

    static private Map<String, ExpSampleSet> getSampleSetMap(Container container, User user)
    {
        Map<String, ExpSampleSet> map = new CaseInsensitiveTreeMap<>();
        // User can be null if we're running in a background thread, such as doing a study export
        for (ExpSampleSet ss : ExperimentService.get().getSampleSets(container, user, user != null))
        {
            map.put(ss.getName(), ss);
        }
        return map;
    }

    static public void register(final Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module) {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                // createSchema must check if the Samples schema is available since it passes the sample set map as a constructor argument
                return true;
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
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

    private SamplesSchema(User user, Container container, Map<String, ExpSampleSet> sampleSetMap)
    {
        this(SchemaKey.fromParts(SCHEMA_NAME), user, container, sampleSetMap);
    }

    /*package*/ SamplesSchema(SchemaKey path, User user, Container container, Map<String, ExpSampleSet> sampleSetMap)
    {
        super(path, SCHEMA_DESCR, user, container, ExperimentService.get().getSchema());
        _sampleSetMap = sampleSetMap;
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

    public TableInfo createTable(String name)
    {
        ExpSampleSet ss = getSampleSets().get(name);
        if (ss == null)
            return null;
        return getSampleTable(ss);
    }

    /** Creates a table of materials, scoped to the given sample set and including its custom columns, if provided */
    public ExpMaterialTable getSampleTable(ExpSampleSet ss)
    {
        ExpMaterialTable ret = ExperimentService.get().createMaterialTable(ExpSchema.TableType.Materials.toString(), this);
        if (_containerFilter != null)
            ret.setContainerFilter(_containerFilter);
        ret.populate(ss, true);
        ret.overlayMetadata(ret.getPublicName(), SamplesSchema.this, new ArrayList<>());
        return ret;
    }

    public ForeignKey materialIdForeignKey(final ExpSampleSet ss)
    {
        return new LookupForeignKey("RowId")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpMaterialTable ret = ExperimentService.get().createMaterialTable(ExpSchema.TableType.Materials.toString(), SamplesSchema.this);
                ret.populate(ss, false);
                ret.setContainerFilter(ContainerFilter.EVERYTHING);
                ret.overlayMetadata(ret.getPublicName(), SamplesSchema.this, new ArrayList<>());
                return ret;
            }

            @Override
            public StringExpression getURL(ColumnInfo parent)
            {
                return super.getURL(parent, true);
            }
        };
    }

    @Override
    public String getDomainURI(String queryName)
    {
        Container container = getContainer();
        ExpSampleSet ss = getSampleSets().get(queryName);
        if (ss == null)
            throw new NotFoundException("Sample set '" + queryName + "' not found in this container '" + container.getPath() + "'.");

        return ss.getType().getTypeURI();
    }
}
