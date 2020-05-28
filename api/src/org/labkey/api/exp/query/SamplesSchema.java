/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class SamplesSchema extends AbstractExpSchema
{
    public static final String SCHEMA_NAME = "samples";
    public static final SchemaKey SCHEMA_SAMPLES = SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME);
    public static final String SCHEMA_DESCR = "Contains data about the samples used in experiment runs.";
    static final Logger log = Logger.getLogger(SamplesSchema.class);

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
                // The 'samples' schema is always available, but will be hidden if there are no SampleSets
                return true;
            }

            @Override
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new SamplesSchema(schema);
            }
        });
    }

    public SamplesSchema(QuerySchema schema)
    {
        this(schema.getUser(), schema.getContainer());
        setDefaultSchema(schema.getDefaultSchema());
    }

    public SamplesSchema(User user, Container container)
    {
        this(SchemaKey.fromParts(SCHEMA_NAME), user, container);
    }

    private Map<String, ExpSampleSet> _sampleSetMap = null;

    /*package*/ SamplesSchema(SchemaKey path, User user, Container container)
    {
        super(path, SCHEMA_DESCR, user, container, ExperimentService.get().getSchema());
    }

    protected Map<String, ExpSampleSet> getSampleSets()
    {
        if (_sampleSetMap == null)
        {
            _sampleSetMap = getSampleSetMap(getContainer(), getUser());
        }
        return _sampleSetMap;
    }

    @Override
    public boolean isHidden()
    {
        return getSampleSets().isEmpty();
    }

    @Override
    public Set<String> getTableNames()
    {
        return getSampleSets().keySet();
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        ExpSampleSet ss = getSampleSets().get(name);
        if (ss == null)
            return null;
        return getSampleTable(ss, cf);
    }

    @Override
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        QueryView queryView = super.createView(context, settings, errors);

        // Use default delete button, but without showing the confirmation text
        // NOTE: custom queries in the schema don't support delete so setting this flag is ok
        queryView.setShowDeleteButtonConfirmationText(false);

        return queryView;
    }

    /** Creates a table of materials, scoped to the given sample set and including its custom columns, if provided */
    public ExpMaterialTable getSampleTable(ExpSampleSet ss, ContainerFilter cf)
    {
        if (log.isTraceEnabled())
        {
            log.trace("CREATE TABLE: " + (null==ss ? "null" : ss.getName()) + " schema=" + System.identityHashCode(this), new Throwable());
        }
        ExpMaterialTable ret = ExperimentService.get().createMaterialTable(ExpSchema.TableType.Materials.toString(), this, cf);
        ret.populate(ss, true);
        ret.overlayMetadata(ret.getPublicName(), SamplesSchema.this, new ArrayList<>());
        return ret;
    }

    /**
     * @param domainProperty the property on which the lookup is configured
     */
    public ForeignKey materialIdForeignKey(@Nullable final ExpSampleSet ss, @Nullable DomainProperty domainProperty)
    {
        final String tableName =  null == ss ? ExpSchema.TableType.Materials.toString() : ss.getName();
        final String schemaName = null == ss ? ExpSchema.SCHEMA_NAME : SamplesSchema.SCHEMA_NAME;

        return new LookupForeignKey(null, null, schemaName, tableName, "RowId", null)
        {
            @Override
            public @Nullable TableInfo getLookupTableInfo()
            {
                ContainerFilter cf = getLookupContainerFilter();
                String cacheKey = SamplesSchema.class.getName() + "/" + schemaName + "/" + tableName + "/" + (null==ss ? "" : ss.getMaterialLSIDPrefix()) + "/" + (null==domainProperty ? "" : domainProperty.getPropertyURI()) + cf.getCacheKey();
                return SamplesSchema.this.getCachedLookupTableInfo(cacheKey, this::createLookupTableInfo);
            }

            private TableInfo createLookupTableInfo()
            {
                ExpMaterialTable ret = ExperimentService.get().createMaterialTable(tableName, SamplesSchema.this, null);
                ret.populate(ss, true);
                ret.setContainerFilter(getLookupContainerFilter());
                ret.overlayMetadata(ret.getPublicName(), SamplesSchema.this, new ArrayList<>());
                if (domainProperty != null && domainProperty.getPropertyType().getJdbcType().isText())
                {
                    // Hack to support lookup via RowId or Name
                    _columnName = "Name";
                }
                ret.setLocked(true);
                return ret;
            }

            @Override
            protected ContainerFilter getLookupContainerFilter()
            {
                return new ContainerFilter.SimpleContainerFilter(ExpSchema.getSearchContainers(getContainer(), ss, domainProperty, getUser()));
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

        return ss.getDomain().getTypeURI();
    }
}
