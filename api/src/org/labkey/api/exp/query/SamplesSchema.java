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

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
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
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

public class SamplesSchema extends AbstractExpSchema
{
    public static final String SCHEMA_NAME = "samples";
    public static final String STUDY_LINKED_SCHEMA_NAME = "~~STUDY.LINKED.SAMPLE~~";

    // AllSampleTypes.query.xml metadata file is applied to all Sample Types in a container
    public static final String SCHEMA_METADATA_NAME = "AllSampleTypes";
    public static final SchemaKey SCHEMA_SAMPLES = SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME);
    public static final String SCHEMA_DESCR = "Contains data about the samples used in experiment runs.";
    static final Logger log = LogHelper.getLogger(SamplesSchema.class, "Samples Schema");

    boolean withLinkToStudyColumns = true;
    boolean supportTableRules = true;
    Set<Role> contextualRoles = Set.of();


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

    public SamplesSchema(QuerySchema schema, boolean studyLinkedSamples)
    {
        this(schema.getUser(), schema.getContainer());
        setDefaultSchema(schema.getDefaultSchema());

        if (studyLinkedSamples)
        {
            this.setContainerFilter(ContainerFilter.EVERYTHING);
            this.withLinkToStudyColumns = false;
            this.supportTableRules = false;
            this.contextualRoles = Set.of(RoleManager.getRole(ReaderRole.class));
        }
    }

    public SamplesSchema(User user, Container container)
    {
        this(SchemaKey.fromParts(SCHEMA_NAME), user, container);
    }

    @Override
    public boolean canReadSchema()
    {
        return super.canReadSchema() || getContainer().hasPermission(getUser(), ReadPermission.class, contextualRoles) ;
    }

    @Override
    public QuerySchema getSchema(String name)
    {
        if (STUDY_LINKED_SCHEMA_NAME.equals(name))
        {
            // users of this could just hide the link-to-study columns, but they are kinda expensive to create
            return new SamplesSchema(getDefaultSchema(), true);
        }
        return super.getSchema(name);
    }

    private Map<String, ExpSampleType> _sampleTypeMap = null;

    /*package*/ SamplesSchema(SchemaKey path, User user, Container container)
    {
        super(path, SCHEMA_DESCR, user, container, ExperimentService.get().getSchema());
    }

    public Map<String, ExpSampleType> getSampleTypes()
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

        ExpMaterialTable tableInfo = createSampleTable(st, cf);

        // Get linked to study columns
        StudyPublishService studyPublishService = StudyPublishService.get();
        if (withLinkToStudyColumns && studyPublishService != null)
        {
            int rowId = st.getRowId();
            String rowIdNameString = ExpMaterialTable.Column.RowId.toString();
            studyPublishService.addLinkedToStudyColumns((AbstractTableInfo)tableInfo, Dataset.PublishSource.SampleType, true, rowId, rowIdNameString, getUser());
        }
        if (!supportTableRules)
        {
            tableInfo.setSupportTableRules(false);
        }
        
        return tableInfo;
    }

    @Override
    public @NotNull QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        QueryView queryView = super.createView(context, settings, errors);

        // Use default delete button, but without showing the confirmation text
        // NOTE: custom queries in the schema don't support delete so setting this flag is ok
        queryView.setShowDeleteButtonConfirmationText(false);

        return queryView;
    }

    /** Convenience method that takes a sample type instead of a string to provide a more robust mapping */
    public ExpMaterialTable getTable(@NotNull ExpSampleType st, @Nullable ContainerFilter cf)
    {
        return (ExpMaterialTable) getTable(st.getName(), cf);
    }

    /**
     * Creates a table of materials, scoped to the given sample type and including its custom columns, if provided.
     * Does not include any linked-to-study columns or apply XML metadata overrides
     */
    public ExpMaterialTable createSampleTable(@Nullable ExpSampleType st, ContainerFilter cf)
    {
        if (log.isTraceEnabled())
            log.trace("CREATE TABLE: " + (null==st ? "null" : st.getName()) + " schema=" + System.identityHashCode(this), new Throwable());

        ExpMaterialTable ret = ExperimentService.get().createMaterialTable(this, cf, st);
        ret.populate();
        return ret;
    }

    /**
     * @param domainProperty the property on which the lookup is configured
     */
    public ForeignKey materialIdForeignKey(@Nullable final ExpSampleType st, @Nullable DomainProperty domainProperty)
    {
        return materialIdForeignKey(st, domainProperty, null);
    }

    public ForeignKey materialIdForeignKey(@Nullable final ExpSampleType st, @Nullable DomainProperty domainProperty, @Nullable ContainerFilter cfParent)
    {
        final String tableName = null == st ? ExpSchema.TableType.Materials.toString() : st.getName();
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
                ExpMaterialTable ret = ExperimentService.get().createMaterialTable(SamplesSchema.this, getLookupContainerFilter(), st);
                ret.populate();
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
                // If the lookup is configured to target a specific container,
                // then respect that setting and ignore the supplied container filter.
                boolean isTargetLookup = domainProperty != null && domainProperty.getLookup() != null && domainProperty.getLookup().getContainer() != null;
                if (!isTargetLookup && cfParent != null)
                    return cfParent;
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

    @Override
    public NavTree getSchemaBrowserLinks(User user)
    {
        NavTree root = super.getSchemaBrowserLinks(user);
        if (getContainer().hasPermission(user, ReadPermission.class))
            root.addChild("Manage SampleTypes", ExperimentUrls.get().getShowSampleTypeListURL(getContainer()));
        return root;
    }

    @Override
    public boolean hasRegisteredSchemaLinks()
    {
        return true;
    }
}
