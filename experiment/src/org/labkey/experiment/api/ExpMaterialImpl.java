/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.experiment.api;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.RemapCache;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpDataTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.qc.DataState;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.JobRunner;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.experiment.CustomProperties;
import org.labkey.experiment.CustomPropertyRenderer;
import org.labkey.experiment.ExperimentModule;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.labkey.experiment.samples.SampleStatusManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ExpMaterialImpl extends AbstractRunItemImpl<Material> implements ExpMaterial
{
    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("material", "Material/Sample");

    static public List<ExpMaterialImpl> fromMaterials(Collection<Material> materials)
    {
        return materials.stream().map(ExpMaterialImpl::new).collect(Collectors.toList());
    }

    // For serialization
    protected ExpMaterialImpl() {}
    
    public ExpMaterialImpl(Material material)
    {
        super(material);
    }

    @Override
    public void setName(String name)
    {
        if (null != getLSID() && !name.equals(new Lsid(getLSID()).getObjectId()))
            throw new IllegalStateException("name="+name + " lsid="+getLSID());
        super.setName(name);
    }

    @Override
    public void setLSID(String lsid)
    {
        if (null != getName() && !getName().equals(new Lsid(lsid).getObjectId()))
            throw new IllegalStateException("name="+getName() + " lsid="+lsid);
        super.setLSID(lsid);
    }

    @Override
    public void setLSID(Lsid lsid)
    {
        if (null != getName() && !getName().equals(lsid.getObjectId()))
            throw new IllegalStateException("name=" + getName() + " lsid=" + lsid);
        super.setLSID(lsid);
    }

    @Override
    public ActionURL detailsURL()
    {
        return _object.detailsURL();
    }


    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        ExpSampleType st = getSampleType();
        if (st != null)
            return new QueryRowReference(getContainer(), SamplesSchema.SCHEMA_SAMPLES, st.getName(), FieldKey.fromParts(ExpDataTable.Column.RowId), getRowId());
        else
            return new QueryRowReference(getContainer(), ExpSchema.SCHEMA_EXP, ExpSchema.TableType.Materials.name(), FieldKey.fromParts(ExpDataTable.Column.RowId), getRowId());
    }

    @Nullable @Override
    public ExpSampleType getSampleType()
    {
        String type = _object.getCpasType();
        if (!ExpMaterialImpl.DEFAULT_CPAS_TYPE.equals(type) && !"Sample".equals(type))
        {
            // try current container first (uses cache)
            return SampleTypeService.get().getSampleTypeByType(type, getContainer());
        }
        else
        {
            return null;
        }
    }

    @Override
    public List<ExpProtocolApplicationImpl> getTargetApplications()
    {
        return getTargetApplications(new SimpleFilter(FieldKey.fromParts("MaterialId"), getRowId()), ExperimentServiceImpl.get().getTinfoMaterialInput());
    }

    @Override
    public String getCpasType()
    {
        String result = _object.getCpasType();
        return result == null ? ExpMaterialImpl.DEFAULT_CPAS_TYPE : result;
    }

    @Override
    public String getRootMaterialLSID()
    {
        return _object.getRootMaterialLSID();
    }

    @Override
    public String getAliquotedFromLSID()
    {
        return _object.getAliquotedFromLSID();
    }

    public Integer getStatusId()
    {
        return _object.getStatus();
    }

    @Override
    public DataState getDataState()
    {
        if (getStatusId() == null)
            return null;
        return SampleStatusManager.getInstance().getStateForRowId(getContainer(), getStatusId());
    }

    @Override
    public boolean isOperationPermitted(ExperimentService.SampleOperations operation)
    {
        if (!ExperimentModule.isSampleStatusEnabled()) // permit everything if feature not enabled
            return true;

        DataState status = getDataState();
        // no status means you can do all operations
        if (status == null)
            return true;

        return ExpSchema.SampleStatusType.isOperationPermitted(status.getStateType(), operation);
    }

    @Override
    @NotNull
    public Collection<String> getAliases()
    {
        TableInfo mapTi = ExperimentService.get().getTinfoMaterialAliasMap();
        TableInfo ti = ExperimentService.get().getTinfoAlias();
        SQLFragment sql = new SQLFragment()
                .append("SELECT a.name FROM ").append(mapTi, "m")
                .append(" JOIN ").append(ti, "a")
                .append(" ON m.alias = a.RowId WHERE m.lsid = ? ");
        sql.add(getLSID());
        ArrayList<String> aliases = new SqlSelector(mapTi.getSchema(), sql).getArrayList(String.class);
        return Collections.unmodifiableList(aliases);
    }

    /** Get the ObjectId of the ExpSampleType that this ExpMaterial belongs to. */
    @Override
    @Nullable
    public Integer getParentObjectId()
    {
        ExpSampleType st = getSampleType();
        if (st == null)
            return null;

        return st.getObjectId();
    }

    @Override
    public void save(User user)
    {
        save(user, (ExpSampleTypeImpl) getSampleType());
    }

    public void save(User user, ExpSampleTypeImpl st)
    {
        save(user, ExperimentServiceImpl.get().getTinfoMaterial(), true);
        if (null != st)
        {
            TableInfo ti = st.getTinfo();
            if (null != ti)
            {
                new SqlExecutor(ti.getSchema()).execute("INSERT INTO " + ti + " (lsid) SELECT ? WHERE NOT EXISTS (SELECT lsid FROM " + ti + " WHERE lsid = ?)", getLSID(), getLSID());
            }
        }
        index(null);
    }

    @Override
    protected void save(User user, TableInfo table, boolean ensureObject)
    {
        assert ensureObject;
        boolean isInsert = false;
        if (getRowId() == 0)
        {
            isInsert = true;
            long longId = DbSequenceManager.getPreallocatingSequence(ContainerManager.getRoot(), ExperimentService.get().getTinfoMaterial().getDbSequenceName("RowId")).next();
            if (longId > Integer.MAX_VALUE)
                throw new OutOfRangeException(longId, 0, Integer.MAX_VALUE);
            setRowId((int) longId);
        }
        super.save(user, table, true, isInsert);
    }

    @Override
    public void delete(User user)
    {
        ExperimentServiceImpl.get().deleteMaterialByRowIds(user, getContainer(), Collections.singleton(getRowId()), true, getSampleType(), false);
        // Deleting from search index is handled inside deleteMaterialByRowIds()
    }

    @Override
    public List<ExpRunImpl> getTargetRuns()
    {
        return getTargetRuns(ExperimentServiceImpl.get().getTinfoMaterialInput(), "MaterialId");
    }

    private static final Map<String, CustomPropertyRenderer> RENDERER_MAP = new HashMap<>()
    {
        @Override
        public CustomPropertyRenderer get(Object key)
        {
            // Special renderer used only for indexing material custom properties
            return new CustomPropertyRenderer()
            {
                @Override
                public boolean shouldRender(ObjectProperty prop, List<ObjectProperty> siblingProperties)
                {
                    Object value = prop.value();

                    // For now, index only non-null Strings and Integers
                    return (value instanceof String || value instanceof Integer);
                }

                @Override
                public String getDescription(ObjectProperty prop, List<ObjectProperty> siblingProperties)
                {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(prop.getPropertyURI(), prop.getContainer());
                    String name = prop.getName();
                    if (pd != null)
                        name = pd.getLabel() != null ? pd.getLabel() : pd.getName();
                    return name;
                }

                @Override
                public String getValue(ObjectProperty prop, List<ObjectProperty> siblingProperties, Container c)
                {
                    return prop.value().toString();
                }
            };
        }
    };

    public void index(SearchService.IndexTask task)
    {
        // Big hack to prevent study specimens from being indexed as
        if (StudyService.SPECIMEN_NAMESPACE_PREFIX.equals(getLSIDNamespacePrefix()))
        {
            return;
        }
        if (task == null)
        {
            SearchService ss = SearchService.get();
            if (null == ss)
                return;
            task = ss.defaultTask();
        }

        // do the least possible amount of work here
        final SearchService.IndexTask indexTask = task;
        var document = createIndexDocument();
        indexTask.addResource(document, SearchService.PRIORITY.item);
    }


    @NotNull
    public WebdavResource createIndexDocument()
    {
        ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getMaterialDetailsURL(this);
        url.setExtraPath(getContainer().getId());

        Map<String, Object> props = new HashMap<>();
        Set<String> identifiersHi = new HashSet<>();

        // Name is identifier with highest weight
        identifiersHi.add(getName());

        // Add aliases in parenthesis in the title
        StringBuilder title = new StringBuilder("Sample - " + getName());
        Collection<String> aliases = this.getAliases();
        if (!aliases.isEmpty())
        {
            title.append(" (").append(StringUtils.join(aliases, ", ")).append(")");
            identifiersHi.addAll(aliases);
        }


        props.put(SearchService.PROPERTY.categories.toString(), searchCategory.toString());
        props.put(SearchService.PROPERTY.title.toString(), title.toString());
        props.put(SearchService.PROPERTY.keywordsLo.toString(), "Sample");      // Treat the word "Sample" a low priority keyword
        props.put(SearchService.PROPERTY.identifiersHi.toString(), StringUtils.join(identifiersHi, " "));

        StringBuilder body = new StringBuilder();

        // Append the interesting standard properties: "Source Experiment Run", "Source Protocol", and "Source Protocol Application"
        append(body, getDescription());
        append(body, getRun());
        append(body, getSourceProtocol());
        append(body, getSourceApplication());

        // Add all String and Integer custom property descriptions and values to body
        CustomProperties.iterate(getContainer(), getObjectProperties().values(), RENDERER_MAP, (indent, description, value) ->
        {
            append(body, description);
            append(body, value);
        });

        ExpSampleType st = getSampleType();
        if (null != st)
        {
            String sampleTypeName = st.getName();
            ActionURL show = new ActionURL(ExperimentController.ShowSampleTypeAction.class, getContainer()).addParameter("rowId", st.getRowId());
            NavTree t = new NavTree("SampleType - " + sampleTypeName, show);
            String nav = NavTree.toJS(Collections.singleton(t), null, false, true).toString();
            props.put(SearchService.PROPERTY.navtrail.toString(), nav);

            // Add sample type name to body, if it's not already present
            if (-1 == body.indexOf(sampleTypeName))
                append(body, sampleTypeName);
        }

        return new SimpleDocumentResource(new Path(getDocumentId()), getDocumentId(),
                getContainer().getId(), "text/plain",
                body.toString(), url,
                getCreatedBy(), getCreated(),
                getModifiedBy(), getModified(),
                props)
        {
            @Override
            public void setLastIndexed(long ms, long modified)
            {
                // setLastIndexed() can get called very rapidly after a bulk insert/update
                // so we'll collect these instead of updating one at a time
                synchronized (updateLastIndexedList)
                {
                    boolean wasEmpty = updateLastIndexedList.isEmpty();
                    updateLastIndexedList.add(new Pair<>(getRowId(), ms));
                    if (wasEmpty)
                    {
                        JobRunner.getDefault().execute(1000, () ->
                        {
                            List<Pair<Integer, Long>> copy = null;
                            synchronized (updateLastIndexedList)
                            {
                                copy = List.copyOf(updateLastIndexedList);
                                updateLastIndexedList.clear();
                            }
                            ExperimentServiceImpl.get().setMaterialLastIndexed(copy);
                        });
                    }
                }
            }
        };
    }

    static final List<Pair<Integer,Long>> updateLastIndexedList = new ArrayList<>();

    private static void append(StringBuilder sb, @Nullable Identifiable identifiable)
    {
        if (null != identifiable)
            append(sb, identifiable.getName());
    }

    private static void append(StringBuilder sb, @Nullable String value)
    {
        if (null != value)
        {
            if (sb.length() > 0)
                sb.append(" ");

            sb.append(value);
        }
    }

    @Override
    public String getDocumentId()
    {
        return "material:" + getRowId();
    }

    /* This is expensive! consider using getProperties(SampleType st) */
    @Override
    public Map<String, Object> getProperties()
    {
        return getProperties((ExpSampleTypeImpl) getSampleType());
    }

    public Map<String,Object> getProperties(ExpSampleTypeImpl st)
    {
        var ret = super.getProperties();
        var ti = null == st ? null : st.getTinfo();
        if (null != ti)
        {
            new SqlSelector(ti.getSchema(),"SELECT * FROM " + ti + " WHERE lsid=?",  getLSID()).forEach(rs ->
            {
                for (ColumnInfo c : ti.getColumns())
                {
                    if (c.getPropertyURI() == null)
                        continue;
                    Object value = c.getValue(rs);
                    ret.put(c.getPropertyURI(), value);
                }
            });
        }
        return ret;
    }

    @Override
    public Map<PropertyDescriptor, Object> getPropertyValues()
    {
        ExpSampleTypeImpl sampleType = (ExpSampleTypeImpl) getSampleType();
        Map<String,Object> uriMap = getProperties(sampleType);
        Map<PropertyDescriptor, Object> values = new HashMap<>();
        for (DomainProperty pd : sampleType.getDomain().getProperties())
        {
            values.put(pd.getPropertyDescriptor(), uriMap.get(pd.getPropertyURI()));
        }
        return values;
    }

    @Override
    public Map<String, ObjectProperty> getObjectProperties()
    {
        return getObjectProperties((ExpSampleTypeImpl) getSampleType());
    }

    public Map<String, ObjectProperty> getObjectProperties(ExpSampleTypeImpl st)
    {
        HashMap<String,ObjectProperty> ret = new HashMap<>(super.getObjectProperties());
        var ti = null == st ? null : st.getTinfo();
        if (null != ti)
        {
            new SqlSelector(ti.getSchema(),"SELECT * FROM " + ti + " WHERE lsid=?",  getLSID()).forEach(rs ->
            {
                for (ColumnInfo c : ti.getColumns())
                {
                    if (c.getPropertyURI() == null || StringUtils.equalsIgnoreCase("lsid", c.getName()) || StringUtils.equalsIgnoreCase("genId", c.getName()))
                        continue;
                    if (c.isMvIndicatorColumn())
                        continue;
                    Object value = c.getValue(rs);
                    String mvIndicator = null;
                    if (null != c.getMvColumnName())
                    {
                        ColumnInfo mv = ti.getColumn(c.getMvColumnName());
                        mvIndicator = null==mv ? null : (String)mv.getValue(rs);
                    }
                    if (null == value && null == mvIndicator)
                        continue;
                    if (null != mvIndicator)
                        value = null;
                    var prop = new ObjectProperty(getLSID(), getContainer(), c.getPropertyURI(), value, null==c.getPropertyType()? PropertyType.getFromJdbcType(c.getJdbcType()) : c.getPropertyType(), c.getName());
                    if (null != mvIndicator)
                        prop.setMvIndicator(mvIndicator);
                    ret.put(c.getPropertyURI(), prop);
                }
            });
        }
        return ret;
    }

    @Override
    public Object getProperty(DomainProperty prop)
    {
        return super.getProperty(prop);
    }

    @Override
    public Object getProperty(PropertyDescriptor pd)
    {
        var map = getProperties();
        return map.get(pd.getPropertyURI());
    }

    @Override
    public void setProperty(User user, PropertyDescriptor pd, Object value, boolean insertNullValues) throws ValidationException
    {
        if (null == pd.getStorageColumnName())
            super.setProperty(user, pd, value, insertNullValues);
        else
            setProperties(user, Collections.singletonMap(pd.getName(), value), insertNullValues);
    }

    public void setProperties(User user, Map<String,?> values_, boolean insertNullValues) throws ValidationException
    {
        ExpSampleTypeImpl st = (ExpSampleTypeImpl) getSampleType();
        Map<String, Object> values = new HashMap<>(values_);
        Map<String,Object> converted = new HashMap<>();
        RemapCache cache = new RemapCache();

        TableInfo ti = null==st ? null : st.getTinfo();
        if (null != ti)
        {
            Domain d = st.getDomain();
            for (DomainProperty dp : d.getProperties())
            {
                String key;
                Object value;
                if (values.containsKey(dp.getName()))
                    value = values.get(key = dp.getName());
                else if (values.containsKey(dp.getPropertyURI()))
                    value = values.get(key = dp.getPropertyURI());
                else
                    continue;
                if (value instanceof ObjectProperty)
                {
                    // NOTE: ExpObjectImpl.setProperty() does not support MvIndicator and neither does Table.update().
                    // we could handle it here if we need to
                    value = ((ObjectProperty) value).value();
                }
                try
                {
                    value = dp.getJdbcType().convert(value);
                }
                catch (ConversionException x)
                {
                    // Attempt to resolve lookups by display value
                    boolean skipError = false;
                    if (dp.getLookup() != null)
                    {
                        Container container = dp.getLookup().getContainer() != null ? dp.getLookup().getContainer() : getContainer();
                        Object remappedValue = cache.remap(SchemaKey.fromParts(dp.getLookup().getSchemaName()), dp.getLookup().getQueryName(), user, container, ContainerFilter.Type.CurrentPlusProjectAndShared, String.valueOf(value));
                        if (remappedValue != null)
                        {
                            value = remappedValue;
                            skipError = true;
                        }
                    }

                    if (!skipError)
                        throw new ValidationException(ConvertHelper.getStandardConversionErrorMessage(value, dp.getName(), dp.getPropertyDescriptor().getPropertyType().getJavaType()));
                }
                converted.put(dp.getName(), value);
                values.remove(key);
            }
            Table.update(user, st.getTinfo(), converted, getLSID());
        }
        for (var entry : values.entrySet())
        {
            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(entry.getKey(), st.getContainer());
            if (null != pd)
                super.setProperty(user, pd, entry.getValue(), insertNullValues);
        }
    }
}
