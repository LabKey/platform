package org.labkey.api.security;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Table;
import org.labkey.api.security.AuthenticationSettingsAuditTypeProvider.AuthSettingsAuditEvent;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

public abstract class SaveConfigurationAction<F extends SaveConfigurationForm, AC extends AuthenticationConfiguration<?>> extends MutatingApiAction<F>
{
    protected @Nullable AC _configuration = null;

    // TODO: Consider removing this -- _configuration is never used (??)
    private void initializeConfiguration(F form)
    {
        Integer rowId = form.getConfiguration();

        if (null != rowId)
        {
            _configuration = getFromCache(rowId);

            if (null == _configuration)
                throw new NotFoundException("Configuration not found");
        }
    }

    @Override
    public final void validateForm(F form, Errors errors)
    {
        initializeConfiguration(form);
        if (StringUtils.isBlank(form.getDescription()))
        {
            errors.reject(ERROR_MSG, "Invalid description: description cannot be blank");
        }
        validate(form, errors);
    }

    @Override
    protected String getCommandClassMethodName()
    {
        return "validate";
    }

    // Force subclasses to override -- this method determines the form type
    public abstract void validate(F form, Errors errors);

    @Override
    public Object execute(F form, BindException errors) throws Exception
    {
        if (errors.hasErrors())
            return null;
        save(form, getUser(), errors);
        Map<String, Object> map = getConfigurationMap(form.getRowId());
        return new ApiSimpleResponse(Map.of("success", true, "configuration", map));
    }

    public void save(F form, @Nullable User user, BindException errors)
    {
        saveForm(form, user);
    }

    public static <F extends SaveConfigurationForm> void saveForm(F form, @Nullable User user)
    {
        if (null == form.getRowId())
        {
            Table.insert(user, CoreSchema.getInstance().getTableInfoAuthenticationConfigurations(), form);
            AuthenticationConfigurationCache.clear();
            AuthSettingsAuditEvent event = new AuthSettingsAuditEvent(form.getProvider() + " authentication configuration \"" + form.getDescription() + "\" was created");
            event.setChanges("created");
            AuditLogService.get().addEvent(user, event);
        }
        else
        {
            AuthenticationConfiguration<?> oldConfiguration = AuthenticationConfigurationCache.getConfiguration(AuthenticationConfiguration.class, form.getRowId());
            Table.update(user, CoreSchema.getInstance().getTableInfoAuthenticationConfigurations(), form, form.getRowId());
            AuthenticationConfigurationCache.clear();
            AuthenticationConfiguration<?> newConfiguration = AuthenticationConfigurationCache.getConfiguration(AuthenticationConfiguration.class, form.getRowId());

            if (null != oldConfiguration && null != newConfiguration)
            {
                String whatChanged = whatChanged(oldConfiguration, newConfiguration);

                if (!whatChanged.isEmpty())
                {
                    AuthSettingsAuditEvent event = new AuthSettingsAuditEvent(form.getProvider() + " authentication configuration \"" + form.getDescription() + "\" (" + form.getRowId() + ") was updated");
                    event.setChanges(whatChanged);
                    AuditLogService.get().addEvent(user, event);
                }
            }
        }
    }

    // TODO: Move to LabKeyStringUtils or similar
    private static @NotNull String whatChanged(AuthenticationConfiguration<?> oldConfiguration, AuthenticationConfiguration<?> newConfiguration)
    {
        MapDifference<String, Object> difference = Maps.difference(oldConfiguration.getLoggingProperties(), newConfiguration.getLoggingProperties());

        List<String> list = new LinkedList<>();

        difference.entriesOnlyOnLeft().entrySet().stream()
            .map(e->e.getKey() + ": " + truncate(e.getValue()) + " » ")
            .forEach(list::add);

        difference.entriesOnlyOnRight().entrySet().stream()
            .map(e->e.getKey() + ": » " + truncate(e.getValue()))
            .forEach(list::add);

        difference.entriesDiffering().entrySet().stream()
            .map(e->e.getKey() + ": " + truncate(e.getValue().leftValue()) + " » " + truncate(e.getValue().rightValue()))
            .forEach(list::add);

        return String.join(", ", list);
    }

    private static String truncate(Object o)
    {
        String s = String.valueOf(o);
        return s.length() > 50 ? s.substring(0, 47) + "..." : s;
    }

    protected AC getFromCache(int rowId)
    {
        //noinspection unchecked
        return (AC)AuthenticationConfigurationCache.getConfiguration(AuthenticationConfiguration.class, rowId);
    }

    protected Map<String, Object> getConfigurationMap(int rowId)
    {
        AC configuration = getFromCache(rowId);
        return AuthenticationManager.getConfigurationMap(configuration);
    }

    // Remove after we no longer upgrade from 20.1
    public static void saveOldProperties(@Nullable SaveConfigurationForm form, @Nullable User user)
    {
        if (null != form)
        {
            form.setEnabled(true);
            form.setDescription(form.getProvider() + " Configuration");
            saveForm(form, user);
        }
    }
}
