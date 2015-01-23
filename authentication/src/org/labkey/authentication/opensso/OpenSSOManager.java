/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

package org.labkey.authentication.opensso;

import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.PropertyMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

@Deprecated
// No longer used or supported, but may be useful for
public class OpenSSOManager
{
    private static final OpenSSOManager _instance = new OpenSSOManager();

    public static OpenSSOManager get()
    {
        return _instance;
    }


    public void activate() throws Exception
    {
        Properties props = loadProps("AMClient.properties");
        replaceDefaults(props, getSystemSettings());  // System settings will replace values in static properties file
//        SystemProperties.initializeProperties(props);
    }


    private Properties loadProps(String filename) throws IOException
    {
        InputStream is = null;

        try
        {
            is = OpenSSOManager.class.getResourceAsStream(filename);  // TODO: Fix this so it works better with Tomcat reload
            Properties props = new Properties();
            props.load(is);
            return props;
        }
        finally
        {
            if (null != is)
                is.close();
        }
    }


    private void replaceDefaults(Properties props, Map<String, String> replacements)
    {
        Set keys = props.keySet();

        for (Object o : keys)
        {
            String key = (String)o;
            String value = props.getProperty(key);

            if (value.startsWith("@") && value.endsWith("@"))
            {
                String defaultKey = value.substring(1, value.length() - 1);
                String defaultValue = replacements.get(defaultKey);
                props.setProperty(key, defaultValue);
            }
        }
    }


    private static final String OPENSSO_PROPERTIES_KEY = "OpenSSO";

    public Map<String, String> getSystemSettings() throws IOException
    {
        Properties fileProps = loadProps("clientDefault.properties");
        // dbProps will be null if settings have never been saved
        Map<String, String> dbProps = PropertyManager.getProperties(OPENSSO_PROPERTIES_KEY);
        // Map we will return -- sort by key
        Map<String, String> map = new TreeMap<>();
        Set<Object> keys = fileProps.keySet();

        for (Object o : keys)
        {
            String key = (String)o;
            String value = dbProps.get(key);
            if (null != value)
                map.put(key, value);
            else
                map.put(key, fileProps.getProperty(key));
        }

        // TODO: Eliminate some of the properties we don't care about

        return map;
    }


    public void writeSystemSettings(Map<String, String> newSettings)
    {
        PropertyMap map = PropertyManager.getWritableProperties(OPENSSO_PROPERTIES_KEY, true);
        map.clear();
        map.putAll(newSettings);
        map.save();
    }


    private static final String OPENSSO_SETTINGS = "OpenSSO_Settings";
    private static final String REFERRER_PREFIX = "Referrer_Prefix";
    private static String _referrerPrefix;

    static
    {
        loadReferrerPrefix();
    }

    private static void loadReferrerPrefix()
    {
        Map<String, String> map = PropertyManager.getProperties(OPENSSO_SETTINGS);
        _referrerPrefix = map.get(REFERRER_PREFIX);
    }

    public void saveReferrerPrefix(String prefix)
    {
        PropertyMap map = PropertyManager.getWritableProperties(OPENSSO_SETTINGS, true);
        map.put(REFERRER_PREFIX, prefix);
        map.save();
        loadReferrerPrefix();
    }

    public String getReferrerPrefix()
    {
        return _referrerPrefix;
    }
}