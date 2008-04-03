package org.labkey.experiment;

import org.labkey.api.data.Container;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.view.JspView;
import org.labkey.api.view.ActionURL;

import java.sql.SQLException;
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
    private static Map<String, CustomPropertyRenderer> _renderers = new HashMap<String, CustomPropertyRenderer>()
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
        private final ActionURL _url;

        public CustomPropertiesBean(Map<String, ObjectProperty> customProperties, Map<String, CustomPropertyRenderer> renderers, ActionURL url)
        {
            _customProperties = customProperties;
            _renderers = renderers;
            _url = url;
        }

        public Map<String, ObjectProperty> getCustomProperties()
        {
            return _customProperties;
        }

        public Map<String, CustomPropertyRenderer> getRenderers()
        {
            return _renderers;
        }

        public ActionURL getUrlHelper()
        {
            return _url;
        }
    }

    public CustomPropertiesView(String parentLSID, ActionURL url, Container c) throws SQLException
    {
        super("/org/labkey/experiment/CustomProperties.jsp");
        setTitle("Custom Properties");
        Map<String, ObjectProperty> props = OntologyManager.getPropertyObjects(parentLSID);
        Map<String, ObjectProperty> map = new TreeMap<String, ObjectProperty>();
        for (String uri : props.keySet())
        {
            String name = OntologyManager.getPropertyName(uri, c);
            map.put(name, props.get(uri));
        }
        setModelBean(new CustomPropertiesBean(map, _renderers, url));
    }

    public boolean hasProperties()
    {
        return !getModelBean()._customProperties.isEmpty();
    }
}
