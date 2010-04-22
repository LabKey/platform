package org.labkey.search.model;

import org.labkey.api.data.PropertyManager;

import java.util.Map;

/**
 * User: adam
 * Date: Apr 20, 2010
 * Time: 7:01:16 PM
 */
public class ExternalIndexManager
{
    private static final String CATEGORY = "ExternalSearchIndex";
    private static final String PATH = "path";
    private static final String ANALYZER = "analyzer";

    public static ExternalIndexProperties get()
    {
        Map<String, String> map = PropertyManager.getProperties(CATEGORY);
        final String path = map.get(PATH);
        final String analyzer = map.get(ANALYZER);

        return new ExternalIndexProperties() {
            public String getExternalIndexPath()
            {
                return path;
            }

            public String getAnalyzer()
            {
                return analyzer;
            }

            public boolean hasProperties()
            {
                return null != path && null != analyzer;
            }
        };
    }

    public static void save(ExternalIndexProperties props)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(CATEGORY, true);
        map.put(PATH, props.getExternalIndexPath());
        map.put(ANALYZER, props.getAnalyzer());
        PropertyManager.saveProperties(map);
    }

    public static void clear()
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(CATEGORY, true);
        map.clear();
        PropertyManager.saveProperties(map);
    }
}
