package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Table;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import java.util.Map;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

public abstract class SaveConfigurationAction<F extends AuthenticationConfigureForm<AC>, AC extends AuthenticationConfiguration<?>> extends MutatingApiAction<F>
{
    protected @Nullable AC _configuration = null;

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

    public static <F extends AuthenticationConfigureForm<?>> void saveForm(F form, @Nullable User user)
    {
        if (null == form.getRowId())
        {
            Table.insert(user, CoreSchema.getInstance().getTableInfoAuthenticationConfigurations(), form);
        }
        else
        {
            Table.update(user, CoreSchema.getInstance().getTableInfoAuthenticationConfigurations(), form, form.getRowId());
        }

        AuthenticationConfigurationCache.clear();
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
    public static void saveOldProperties(@Nullable AuthenticationConfigureForm<?> form, @Nullable User user)
    {
        if (null != form)
        {
            form.setEnabled(true);
            form.setDescription(form.getProvider() + " Configuration");
            saveForm(form, user);
        }
    }
}
