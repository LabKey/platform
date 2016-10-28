/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.view.JspView;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * User: jeckels
 * Date: Nov 21, 2007
 */
public class CustomPropertiesView extends JspView<CustomPropertiesView.CustomPropertiesBean>
{
    private static final CustomPropertyRenderer DEFAULT_RENDERER = new DefaultCustomPropertyRenderer();
    private static final Map<String, CustomPropertyRenderer> _renderers = new HashMap<String, CustomPropertyRenderer>()
    {
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

        public CustomPropertiesBean(Map<String, ObjectProperty> customProperties, Map<String, CustomPropertyRenderer> renderers)
        {
            _customProperties = customProperties;
            _renderers = renderers;
        }

        public Map<String, ObjectProperty> getCustomProperties()
        {
            return _customProperties;
        }

        public Map<String, CustomPropertyRenderer> getRenderers()
        {
            return _renderers;
        }
    }

    public CustomPropertiesView(String parentLSID, Container c)
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
        setModelBean(new CustomPropertiesBean(map, _renderers));
    }

    public boolean hasProperties()
    {
        return !getModelBean()._customProperties.isEmpty();
    }
}
