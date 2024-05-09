package org.labkey.api.settings;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStore;
import org.labkey.api.query.ValidationException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractCustomLabelProvider implements CustomLabelProvider
{
    public static final PropertyStore _normalStore = PropertyManager.getNormalStore();

    public record CustomLabel(String key, String defaultLabel, String description, String tooltip)
    {}

    public Map<String, String> getLabels(@Nullable Container container)
    {
        Container labelContainer = getLabelContainer(container);
        Map<String, String> consolidatedLabels = new HashMap<>();
        Map<String, String> savedLabels = labelContainer == null ? _normalStore.getProperties(getLabelGroup()) : _normalStore.getProperties(labelContainer, getLabelGroup());
        for (CustomLabel defaultLabel : getDefaultLabels())
        {
            if (defaultLabel != null)
            {
                String key = defaultLabel.key();
                String consolidatedLabel = savedLabels.containsKey(key) && !StringUtils.isEmpty(savedLabels.get(key))? savedLabels.get(key) : defaultLabel.defaultLabel();
                if (!StringUtils.isEmpty(consolidatedLabel))
                    consolidatedLabels.put(key, consolidatedLabel);
            }
        }
        return consolidatedLabels;
    }

    public int getUpdatedLabelCount(@Nullable Container container)
    {
        Container labelContainer = getLabelContainer(container);
        Map<String, String> savedLabels = labelContainer == null ? _normalStore.getProperties(getLabelGroup()) : _normalStore.getProperties(labelContainer, getLabelGroup());
        int updatedLabelsCount = 0;
        if (savedLabels.isEmpty())
            return updatedLabelsCount;

        for (CustomLabel defaultLabel : getDefaultLabels())
        {
            if (defaultLabel != null)
            {
                String key = defaultLabel.key();
                boolean isUpdated = savedLabels.containsKey(key) && !StringUtils.isEmpty(savedLabels.get(key)) && !defaultLabel.defaultLabel().equals(savedLabels.get(key));
                if (isUpdated)
                    updatedLabelsCount++;
            }
        }
        return updatedLabelsCount;
    }

    protected abstract String getLabelGroup();

    protected abstract List<CustomLabel> getDefaultLabels();


    public @Nullable Container getLabelContainer(@Nullable Container container)
    {
        return container;
    }

    @Override
    public void saveLabels(HashMap<String, String> updatedLabels, @Nullable Container container) throws ValidationException
    {
        Map<String, String> sanitizedLabels = new HashMap<>();
        for (Map.Entry<String, String> labelEntry: updatedLabels.entrySet())
        {
            String rawLabel = labelEntry.getValue();
            if (rawLabel == null)
                sanitizedLabels.put(labelEntry.getKey(), "");
            else
            {
                String label = rawLabel.trim();
                if (label.length() > 400)
                    throw new ValidationException("Label cannot be longer than 400 characters.");
                sanitizedLabels.put(labelEntry.getKey(), label);
            }
        }
        if (sanitizedLabels.isEmpty())
            return;

        Container labelContainer = getLabelContainer(container);
        PropertyManager.PropertyMap labelStore = labelContainer == null ? _normalStore.getWritableProperties(getLabelGroup(), true) : _normalStore.getWritableProperties(labelContainer, getLabelGroup(), true);
        labelStore.putAll(sanitizedLabels);
        labelStore.save();
    }

    @Override
    public void resetLabels(@Nullable Container container)
    {
        Container labelContainer = getLabelContainer(container);
        if (labelContainer == null)
            _normalStore.deletePropertySet(getLabelGroup());
        else
            _normalStore.deletePropertySet(labelContainer, getLabelGroup());
    }
}
