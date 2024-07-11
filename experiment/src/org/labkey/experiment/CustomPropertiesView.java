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

package org.labkey.experiment;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExpSampleTypeImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Collections.emptyList;

/**
 * User: jeckels
 * Date: Nov 21, 2007
 */
public class CustomPropertiesView extends JspView<CustomPropertiesView.CustomPropertiesBean>
{
    private static final CustomPropertyRenderer DEFAULT_RENDERER = new DefaultCustomPropertyRenderer();
    private static final Map<String, CustomPropertyRenderer> _renderers = new HashMap<String, CustomPropertyRenderer>()
    {
        @Override
        public CustomPropertyRenderer get(Object key)
        {
            CustomPropertyRenderer result = super.get(key);
            if (result == null)
            {
                return DEFAULT_RENDERER;
            }
            return result;
        }
    };

    static
    {
        _renderers.put(ExternalDocsURLCustomPropertyRenderer.URI, new ExternalDocsURLCustomPropertyRenderer());
        _renderers.put(ExternalDocsLabelCustomPropertyRenderer.URI, new ExternalDocsLabelCustomPropertyRenderer());
    }

    public static class CustomPropertiesBean
    {
        private final Map<String, ObjectProperty> _customProperties;
        private final Map<String, CustomPropertyRenderer> _renderers;
        private final List<Pair<String, ActionURL>> _attachments;

        public CustomPropertiesBean(Map<String, ObjectProperty> customProperties, Map<String, CustomPropertyRenderer> renderers, List<Pair<String, ActionURL>> attachments)
        {
            _customProperties = customProperties;
            _renderers = renderers;
            _attachments = attachments;
        }

        public Map<String, ObjectProperty> getCustomProperties()
        {
            return _customProperties;
        }

        public Map<String, CustomPropertyRenderer> getRenderers()
        {
            return _renderers;
        }

        public List<Pair<String, ActionURL>> getAttachments()
        {
            return _attachments;
        }
    }

    public CustomPropertiesView(String parentLSID, Container c)
    {
        this(parentLSID, c, emptyList());
    }

    public CustomPropertiesView(String parentLSID, Container c, List<Pair<String, ActionURL>> attachments)
    {
        super("/org/labkey/experiment/CustomProperties.jsp");
        setTitle("Custom Properties");
        Map<String, ObjectProperty> props = OntologyManager.getPropertyObjects(c, parentLSID);
        Map<String, ObjectProperty> map = new TreeMap<>();
        for (Map.Entry<String, ObjectProperty> entry : props.entrySet())
        {
            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(entry.getKey(), c);
            if (pd != null && pd.isShownInDetailsView())
            {
                map.put(pd.getName(), entry.getValue());
            }
        }

        setModelBean(new CustomPropertiesBean(map, _renderers, attachments));
    }

    public CustomPropertiesView(ExpMaterialImpl m, Container c, User u)
    {
        super("/org/labkey/experiment/CustomProperties.jsp");
        setTitle("Custom Properties");

        String parentLSID = m.getLSID();
        Map<String, ObjectProperty> props = OntologyManager.getPropertyObjects(c, parentLSID);
        Map<String, ObjectProperty> map = new TreeMap<>();
        for (Map.Entry<String, ObjectProperty> entry : props.entrySet())
        {
            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(entry.getKey(), c);
            if (pd != null && pd.isShownInDetailsView())
            {
                map.put(pd.getName(), entry.getValue());
            }
        }
        ExpSampleTypeImpl st = (ExpSampleTypeImpl)m.getSampleType();
        if (null != st)
        {
            Domain d = st.getDomain();
            UserSchema schema = QueryService.get().getUserSchema(u, c, SamplesSchema.SCHEMA_NAME);
            TableInfo queryTable = schema.getTable(st.getName());

            if (null != queryTable)
            {
                SimpleFilter filter = new SimpleFilter("lsid", parentLSID);
                Map<String,Object> tableProps = new TableSelector(queryTable, filter, null).getMap();
                for (DomainProperty dp : d.getProperties())
                {
                    Object value = tableProps.get(dp.getName());
                    if (null != value)
                        map.put(dp.getName(), new ObjectProperty(parentLSID, c, dp.getPropertyURI(), value));
                }
                // include calculated fields from the domain / query as well
                List<ColumnInfo> cols = queryTable.getColumns().stream()
                        .filter(ColumnInfo::isShownInDetailsView)
                        .filter(ColumnInfo::isValueExpressionColumn)
                        .toList();
                for (ColumnInfo column : cols)
                {
                    Object value = tableProps.get(column.getName());
                    if (null != value)
                        map.put(column.getName(), new ObjectProperty(parentLSID, c, column.getName(), value, column.getLabel()));
                }
            }
        }
        setModelBean(new CustomPropertiesBean(map, _renderers, emptyList()));
    }

    public boolean hasProperties()
    {
        return !(getModelBean()._customProperties.isEmpty() && getModelBean()._attachments.isEmpty());
    }
}
