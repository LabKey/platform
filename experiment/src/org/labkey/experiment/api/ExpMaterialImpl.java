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

package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.FieldKey;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.webdav.SimpleDocumentResource;
import org.labkey.experiment.CustomProperties;
import org.labkey.experiment.CustomPropertyRenderer;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExpMaterialImpl extends AbstractProtocolOutputImpl<Material> implements ExpMaterial
{
    public static final SearchService.SearchCategory searchCategory = new SearchService.SearchCategory("material", "Material/Sample");

    static public List<ExpMaterialImpl> fromMaterials(List<Material> materials)
    {
        return materials.stream().map(ExpMaterialImpl::new).collect(Collectors.toList());
    }

    public ExpMaterialImpl(Material material)
    {
        super(material);
    }


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
            throw new IllegalStateException("name="+getName() + " lsid="+lsid.toString());
        super.setLSID(lsid);
    }


    public URLHelper detailsURL()
    {
        ActionURL ret = new ActionURL(ExperimentController.ShowMaterialAction.class, getContainer());
        ret.addParameter("rowId", Integer.toString(getRowId()));
        return ret;
    }

    @Nullable
    public ExpSampleSet getSampleSet()
    {
        String type = _object.getCpasType();
        if (!"Material".equals(type) && !"Sample".equals(type))
        {
            return ExperimentService.get().getSampleSet(type);
        }
        else
        {
            return null;
        }
    }

    public Map<PropertyDescriptor, Object> getPropertyValues()
    {
        ExpSampleSet sampleSet = getSampleSet();
        if (sampleSet == null)
        {
            return Collections.emptyMap();
        }
        Map<PropertyDescriptor, Object> values = new HashMap<>();
        for (DomainProperty pd : sampleSet.getType().getProperties())
        {
            values.put(pd.getPropertyDescriptor(), getProperty(pd));
        }
        return values;
    }

    public List<ExpProtocolApplicationImpl> getTargetApplications()
    {
        return getTargetApplications(new SimpleFilter(FieldKey.fromParts("MaterialId"), getRowId()), ExperimentServiceImpl.get().getTinfoMaterialInput());
    }

    public String getCpasType()
    {
        String result = _object.getCpasType();
        return result == null ? "Material" : result;
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

    public void save(User user)
    {
        save(user, ExperimentServiceImpl.get().getTinfoMaterial());
        index(null);
    }

    public void delete(User user)
    {
        ExperimentServiceImpl.get().deleteMaterialByRowIds(user, getContainer(), Collections.singleton(getRowId()));
        // Deleting from search index is handled inside deleteMaterialByRowIds()
    }

    public List<ExpRunImpl> getTargetRuns()
    {
        return getTargetRuns(ExperimentServiceImpl.get().getTinfoMaterialInput(), "MaterialId");
    }

    private static final Map<String, CustomPropertyRenderer> RENDERER_MAP = new HashMap<String, CustomPropertyRenderer>()
    {
        public CustomPropertyRenderer get(Object key)
        {
            // Special renderer used only for indexing material custom properties
            return new CustomPropertyRenderer() {
                @Override
                public boolean shouldRender(ObjectProperty prop, List<ObjectProperty> siblingProperties)
                {
                    Object value = prop.value();

                    // For now, index only non-null Strings and Integers
                    return null != value && (value instanceof String || value instanceof Integer);
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
        final ExpMaterialImpl me = this;
        indexTask.addRunnable(
            () -> {
                ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getMaterialDetailsURL(me);
                url.setExtraPath(getContainer().getId());

                Map<String, Object> props = new HashMap<>();
                props.put(SearchService.PROPERTY.categories.toString(), searchCategory.toString());
                props.put(SearchService.PROPERTY.title.toString(), "Sample - " + getName());
                props.put(SearchService.PROPERTY.keywordsMed.toString(), "Sample");      // Treat the word "Sample" a medium priority keyword
                props.put(SearchService.PROPERTY.identifiersMed.toString(), getName());  // Treat the name as a medium priority identifier

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

                ExpSampleSet ss = getSampleSet();
                if (null != ss)
                {
                    String sampleSetName = ss.getName();
                    ActionURL show = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, getContainer()).addParameter("rowId", ss.getRowId());
                    NavTree t = new NavTree("SampleSet - " + sampleSetName, show);
                    String nav = NavTree.toJS(Collections.singleton(t), null, false).toString();
                    props.put(SearchService.PROPERTY.navtrail.toString(), nav);

                    // Add sample set name to body, if it's not already present
                    if (-1 == body.indexOf(sampleSetName))
                        append(body, sampleSetName);
                }

                SimpleDocumentResource sdr = new SimpleDocumentResource(new Path(getDocumentId()), getDocumentId(), getContainer().getId(), "text/plain", body.toString(), url, props)
                {
                    @Override
                    public void setLastIndexed(long ms, long modified)
                    {
                        ExperimentServiceImpl.get().setMaterialLastIndexed(getRowId(), ms);
                    }
                };

                indexTask.addResource(sdr, SearchService.PRIORITY.item);
            }
            , SearchService.PRIORITY.bulk
        );
    }

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

    public String getDocumentId()
    {
        return "material:" + getRowId();
    }
}
