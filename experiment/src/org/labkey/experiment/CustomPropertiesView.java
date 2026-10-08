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
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.experiment.api.ExpMaterialImpl;
import org.labkey.experiment.api.ExpSampleTypeImpl;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.util.Collections.emptyList;

/**
 * User: jeckels
 * Date: Nov 21, 2007
 */
public class CustomPropertiesView extends JspView<CustomPropertiesView.CustomPropertiesBean>
{
    public static class CustomPropertiesBean
    {
        private final Map<String, ObjectProperty> _customProperties;
        private final List<Pair<String, ActionURL>> _attachments;

        public CustomPropertiesBean(Map<String, ObjectProperty> customProperties, List<Pair<String, ActionURL>> attachments)
        {
            _customProperties = customProperties;
            _attachments = attachments;
        }

        public Map<String, ObjectProperty> getCustomProperties()
        {
            return _customProperties;
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

        setModelBean(new CustomPropertiesBean(map, attachments));
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
        ExpSampleTypeImpl st = m.getSampleType();
        if (null != st)
        {
            Domain d = st.getDomain();
            UserSchema schema = QueryService.get().getUserSchema(u, c, SamplesSchema.SCHEMA_NAME);
            TableInfo queryTable = schema.getTable(st.getName());

            if (null != queryTable)
            {
                Set<String> propertyUris = new HashSet<>();

                for (DomainProperty property : d.getProperties())
                {
                    propertyUris.add(property.getPropertyURI());
                }

                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("lsid"), parentLSID);
                Map<String,Object> tableProps = new TableSelector(queryTable, filter, null).getMap();
                // include calculated fields from the domain / query as well
                List<ColumnInfo> cols = queryTable.getColumns().stream()
                        .filter(ColumnInfo::isShownInDetailsView)
                        .filter(col -> propertyUris.contains(col.getPropertyURI()) || col.isValueExpressionColumn())
                        .toList();
                for (ColumnInfo column : cols)
                {
                    Object value = column.getValue(tableProps);
                    PropertyType type = column.getPropertyType();
                    // Calculated fields don't store an explicit property type
                    if (type == null)
                    {
                        if (value == null)
                        {
                            type = PropertyType.STRING;
                        }
                        else
                        {
                            type = PropertyType.getFromClass(value.getClass());
                        }
                    }
                    map.put(column.getName(), new ObjectProperty(parentLSID, c, column.getName(), value, type, column.getLabel()));
                }
            }
        }
        setModelBean(new CustomPropertiesBean(map, emptyList()));
    }

    public boolean hasProperties()
    {
        return !(getModelBean()._customProperties.isEmpty() && getModelBean()._attachments.isEmpty());
    }
}
