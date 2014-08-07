package org.labkey.di.steps;

import java.util.HashMap;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 8/7/2014
 */
public abstract class StepProviderImpl implements StepProvider
{
    @Override
    public Map<String, StepProvider> getNameProviderMap()
    {
        Map<String, StepProvider> map = new HashMap<>();
        map.put(getName(), this);
        for (String legacyName : getLegacyNames())
        {
            map.put(legacyName, this);
        }

        return map;
    }
}
