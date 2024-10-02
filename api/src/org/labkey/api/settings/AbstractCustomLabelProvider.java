package org.labkey.api.settings;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyManager.WritablePropertyMap;
import org.labkey.api.data.PropertyStore;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

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

    public void saveLabels(HashMap<String, String> updatedLabels, @Nullable Container container) throws ValidationException
    {
        saveLabels(updatedLabels, container, null);
    }

    @Override
    public void saveLabels(HashMap<String, String> updatedLabels, @Nullable Container container, @Nullable User auditUser) throws ValidationException
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

        AuditTypeEvent event = null;
        if (auditUser != null)
            event = getUpdateLabelEvent(labelContainer, sanitizedLabels);

        WritablePropertyMap labelStore = labelContainer == null ? _normalStore.getWritableProperties(getLabelGroup(), true) : _normalStore.getWritableProperties(labelContainer, getLabelGroup(), true);
        labelStore.putAll(sanitizedLabels);
        labelStore.save();

        if (event != null)
            AuditLogService.get().addEvent(auditUser, event);
    }

    public void resetLabels(@Nullable Container container)
    {
        resetLabels(container, null);
    }

    @Override
    public void resetLabels(@Nullable Container container, @Nullable User auditUser)
    {
        Container labelContainer = getLabelContainer(container);
        if (labelContainer == null)
            _normalStore.deletePropertySet(getLabelGroup());
        else
            _normalStore.deletePropertySet(labelContainer, getLabelGroup());

        if (auditUser != null)
        {
            SiteSettingsAuditProvider.SiteSettingsAuditEvent event = new SiteSettingsAuditProvider.SiteSettingsAuditEvent(labelContainer.getId(), getProviderLabel() + " labels have been reset to default.");
            AuditLogService.get().addEvent(auditUser, event);
        }
    }

    private AuditTypeEvent getUpdateLabelEvent(Container labelContainer, Map<String, String> sanitizedLabels)
    {
        Map<String, String> defaultLabels = new HashMap<>();
        for(CustomLabel defaultLabel : getDefaultLabels())
            defaultLabels.put(defaultLabel.key(), defaultLabel.defaultLabel());
        Map<String, String> existingLabels = getCustomLabels(labelContainer);
        Map<String, Pair<String, String>> changedLabels = new HashMap<>();
        for (Map.Entry<String, String> entry : sanitizedLabels.entrySet())
        {
            String updatedLabel = entry.getValue();
            String existingLabel = existingLabels.get(entry.getKey());
            if (StringUtils.isEmpty(updatedLabel))
                updatedLabel = defaultLabels.get(entry.getKey());
            if (updatedLabel != null && !updatedLabel.equals(existingLabel))
                changedLabels.put(entry.getKey(), new Pair<>(existingLabel, updatedLabel));
        }

        if (changedLabels.isEmpty())
            return null;

        StringBuilder html = new StringBuilder("<table>");
        for (Map.Entry<String, Pair<String, String>> entry : changedLabels.entrySet())
        {
            html.append("<tr><td class='labkey-form-label'>");
            html.append(PageFlowUtil.filter(entry.getKey()));
            html.append("</td><td>");
            html.append(PageFlowUtil.filter(entry.getValue().first));
            html.append("&nbsp;&raquo;&nbsp;");
            html.append(PageFlowUtil.filter(entry.getValue().second));
            html.append("</td></tr>");
        }
        html.append("</table>");

        SiteSettingsAuditProvider.SiteSettingsAuditEvent event = new SiteSettingsAuditProvider.SiteSettingsAuditEvent(labelContainer.getId(), getProviderLabel() + " labels have been updated.");
        event.setChanges(html.toString());

        return event;
    }

    public String getProviderLabel()
    {
        return getName();
    }

}
