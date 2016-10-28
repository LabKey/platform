/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class AnalyticsProviderItem
{
    private FieldKey _fieldKey;
    private String _name;

    public AnalyticsProviderItem(FieldKey fieldKey, String name)
    {
        _fieldKey = fieldKey;
        _name = name;
    }

    public FieldKey getFieldKey()
    {
        return _fieldKey;
    }

    public void setFieldKey(FieldKey fieldKey)
    {
        _fieldKey = fieldKey;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    @NotNull
    public static List<AnalyticsProviderItem> fromURL(URLHelper urlHelper, String regionName)
    {
        List<AnalyticsProviderItem> analyticsProviderItems = new LinkedList<>();
        String prefix = regionName + "." + CustomViewInfo.ANALYTICSPROVIDER_PARAM_PREFIX + ".";

        for (Pair<String, String> paramPair : urlHelper.getParameters())
        {
            if (paramPair.getKey().startsWith(prefix))
            {
                FieldKey fieldKey = FieldKey.fromString(paramPair.getKey().substring(prefix.length()));
                String providerName = paramPair.getValue();
                analyticsProviderItems.add(new AnalyticsProviderItem(fieldKey, providerName));
            }
        }

        return analyticsProviderItems;
    }

    @NotNull
    public static List<AnalyticsProviderItem> fromURL(PropertyValues pvs, String regionName)
    {
        List<AnalyticsProviderItem> analyticsProviderItems = new LinkedList<>();
        String prefix = regionName + "." + CustomViewInfo.ANALYTICSPROVIDER_PARAM_PREFIX + ".";

        for (PropertyValue val : pvs.getPropertyValues())
        {
            if (val.getName().startsWith(prefix))
            {
                FieldKey fieldKey = FieldKey.fromString(val.getName().substring(prefix.length()));

                List<String> values = new ArrayList<>();
                if (val.getValue() instanceof String)
                    values.add((String) val.getValue());
                else
                    Collections.addAll(values, (String[]) val.getValue());

                for(String s : values)
                {
                    analyticsProviderItems.add(new AnalyticsProviderItem(fieldKey, s));
                }
            }
        }

        return analyticsProviderItems;
    }
}
