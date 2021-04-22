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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
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
    static final Logger log = LogManager.getLogger(SamplesSchema.class);

    static private Map<String, ExpSampleType> getSampleTypeMap(Container container, User user)
    {
        Map<String, ExpSampleType> map = new CaseInsensitiveTreeMap<>();
        // User can be null if we're running in a background thread, such as doing a study export
        for (ExpSampleType st : SampleTypeService.get().getSampleTypes(container, user, user != null))
        {
            map.put(st.getName(), st);
        }
        return map;
    }

    static public void register(final Module module)
    {
        DefaultSchema.registerProvider(SCHEMA_NAME, new DefaultSchema.SchemaProvider(module) {
            @Override
            public boolean isAvailable(DefaultSchema schema, Module module)
            {
                // The 'samples' schema is always available, but will be hidden if there are no SampleTypes
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

    private Map<String, ExpSampleType> _sampleTypeMap = null;

    /*package*/ SamplesSchema(SchemaKey path, User user, Container container)
    {
        super(path, SCHEMA_DESCR, user, container, ExperimentService.get().getSchema());
    }

    protected Map<String, ExpSampleType> getSampleTypes()
    {
        if (_sampleTypeMap == null)
        {
            _sampleTypeMap = getSampleTypeMap(getContainer(), getUser());
        }
        return _sampleTypeMap;
    }

    @Override
    public boolean isHidden()
    {
        return getSampleTypes().isEmpty();
    }

    @Override
    public Set<String> getTableNames()
    {
        return getSampleTypes().keySet();
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        ExpSampleType st = getSampleTypes().get(name);
        if (st == null)
            return null;
        return getSampleTable(st, cf);
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

    /** Creates a table of materials, scoped to the given sample type and including its custom columns, if provided */
    public ExpMaterialTable getSampleTable(@Nullable ExpSampleType st, ContainerFilter cf)
    {
        if (log.isTraceEnabled())
        {
            log.trace("CREATE TABLE: " + (null==st ? "null" : st.getName()) + " schema=" + System.identityHashCode(this), new Throwable());
        }
        ExpMaterialTable ret = ExperimentService.get().createMaterialTable(ExpSchema.TableType.Materials.toString(), this, cf);
        ret.populate(st, true);
        ret.overlayMetadata(ret.getPublicName(), SamplesSchema.this, new ArrayList<>());
        return ret;
    }

    /**
     * @param domainProperty the property on which the lookup is configured
     */
    public ForeignKey materialIdForeignKey(@Nullable final ExpSampleType st, @Nullable DomainProperty domainProperty)
    {
        final String tableName =  null == st ? ExpSchema.TableType.Materials.toString() : st.getName();
        final String schemaName = null == st ? ExpSchema.SCHEMA_NAME : SamplesSchema.SCHEMA_NAME;

        return new LookupForeignKey(null, null, schemaName, tableName, "RowId", null)
        {
            @Override
            public @Nullable TableInfo getLookupTableInfo()
            {
                ContainerFilter cf = getLookupContainerFilter();
                String cacheKey = SamplesSchema.class.getName() + "/" + schemaName + "/" + tableName + "/" + (null==st ? "" : st.getMaterialLSIDPrefix()) + "/" + (null==domainProperty ? "" : domainProperty.getPropertyURI()) + cf.getCacheKey();
                return SamplesSchema.this.getCachedLookupTableInfo(cacheKey, this::createLookupTableInfo);
            }

            private TableInfo createLookupTableInfo()
            {
                ExpMaterialTable ret = ExperimentService.get().createMaterialTable(tableName, SamplesSchema.this, null);
                ret.populate(st, true);
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
                return new ContainerFilter.SimpleContainerFilter(ExpSchema.getSearchContainers(getContainer(), st, domainProperty, getUser()));
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
        ExpSampleType st = getSampleTypes().get(queryName);
        if (st == null)
            throw new NotFoundException("Sample type '" + queryName + "' not found in this container '" + container.getPath() + "'.");

        return st.getDomain().getTypeURI();
    }
}
