/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.ActionButton;
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
                // The 'samples' schema is always available, but will be hidden if there are no SampleSets
                return true;
            }

            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new SamplesSchema(schema.getUser(), schema.getContainer());
            }
        });
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

    @Override
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        if (settings.getQueryName() != null && getTableNames().contains(settings.getQueryName()))
        {
            return new QueryView(this, settings, errors)
            {
                @Override
                public ActionButton createDeleteButton()
                {
                    // Use default delete button, but without showing the confirmation text
                    ActionButton button = super.createDeleteButton();
                    if (button != null)
                    {
                        button.setRequiresSelection(true);
                    }
                    return button;
                }
            };
        }

        return super.createView(context, settings, errors);
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

    /**
     * @param domainProperty the property on which the lookup is configured
     */
    public ForeignKey materialIdForeignKey(@Nullable final ExpSampleSet ss, @Nullable DomainProperty domainProperty)
    {
        final String tableName =  null == ss ? ExpSchema.TableType.Materials.toString() : ss.getName();
        final String schemaName = null == ss ? ExpSchema.SCHEMA_NAME : SamplesSchema.SCHEMA_NAME;

        return new LookupForeignKey(null, null, schemaName, tableName, "RowId", null)
        {
            public TableInfo getLookupTableInfo()
            {
                ExpMaterialTable ret = ExperimentService.get().createMaterialTable(tableName, SamplesSchema.this);
                ret.populate(ss, true);
                ret.setContainerFilter(new ContainerFilter.SimpleContainerFilter(ExpSchema.getSearchContainers(getContainer(), ss, domainProperty, getUser())));
                ret.overlayMetadata(ret.getPublicName(), SamplesSchema.this, new ArrayList<>());
                if (domainProperty != null && domainProperty.getPropertyType().getJdbcType().isText())
                {
                    // Hack to support lookup via RowId or Name
                    _columnName = "Name";
                }
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
