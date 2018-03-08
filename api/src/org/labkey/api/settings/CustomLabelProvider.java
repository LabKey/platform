package org.labkey.api.settings;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;

import java.util.Map;

public interface CustomLabelProvider
{
    /**
     * @param container
     * @return the set of label key/value
     */
    Map<String, String> getCustomLabels(@Nullable Container container);

    Map<String, String> getSiteCustomLabels();

    String getName();
}
