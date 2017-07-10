package org.labkey.api.settings;

import org.apache.commons.lang3.BooleanUtils;
import org.labkey.api.data.PropertyManager;

import java.util.Map;

/**
 * Created by marty on 7/5/2017.
 */
public interface TemplateProperties
{
    String getDisplayConfigs();
    String getDisplayPropertyName();
    String getModulePropertyName();
    String getFileName();

    default boolean isDisplay()
    {
        String showFooter = "TRUE"; // default is to show the footer
        Map<String, String> map = PropertyManager.getProperties(getDisplayConfigs());
        if (!map.isEmpty())
        {
            showFooter = map.get(getDisplayPropertyName());
        }
        return BooleanUtils.toBoolean(showFooter);
    }

    default void setDisplay(boolean isDisplay)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(getDisplayConfigs(), true);
        map.put(getDisplayPropertyName(), BooleanUtils.toStringTrueFalse(isDisplay));
        map.save();
    }

    default String getModule()
    {
        Map<String, String> map = PropertyManager.getProperties(getDisplayConfigs());
        return map.get(getModulePropertyName());
    }

    default void setModule(String module)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(getDisplayConfigs(), true);
        map.put(getModulePropertyName(), module);
        map.save();
    }
}
