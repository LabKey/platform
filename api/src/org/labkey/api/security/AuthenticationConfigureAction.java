package org.labkey.api.security;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Table;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

public abstract class AuthenticationConfigureAction<F extends AuthenticationConfigureForm<AC>, AC extends AuthenticationConfiguration> extends FormViewAction<F>
{
    protected @Nullable AC _configuration = null;

    @Override
    protected String getCommandClassMethodName()
    {
        return "getConfigureView";
    }

    private void initializeConfiguration(F form)
    {
        Integer rowId = form.getConfiguration();

        if (null != rowId)
        {
            _configuration = (AC)AuthenticationConfigurationCache.getConfiguration(AuthenticationConfiguration.class, rowId);

            if (null == _configuration)
                throw new NotFoundException("Configuration not found");
        }
    }

    @Override
    public final ModelAndView getView(F form, boolean reshow, BindException errors)
    {
        initializeConfiguration(form);

        // On first show, replace the form defaults with the saved configuration values
        if (!reshow && null != _configuration)
            form.setAuthenticationConfiguration(_configuration);

        return getConfigureView(form, reshow, errors);
    }

    public abstract ModelAndView getConfigureView(F form, boolean reshow, BindException errors);

    @Override
    public final void validateCommand(F form, Errors errors)
    {
        initializeConfiguration(form);
        if (StringUtils.isBlank(form.getDescription()))
        {
            errors.reject(ERROR_MSG, "Invalid description: description cannot be blank");
        }
        validateForm(form, errors);
    }

    protected abstract void validateForm(F form, Errors errors);

    @Override
    public boolean handlePost(F form, BindException errors)
    {
        if (null == form.getRowId())
        {
            Table.insert(getUser(), CoreSchema.getInstance().getTableInfoAuthenticationConfigurations(), form);
        }
        else
        {
            Table.update(getUser(), CoreSchema.getInstance().getTableInfoAuthenticationConfigurations(), form, form.getRowId());
        }

        AuthenticationConfigurationCache.clear();

        return true;
    }
}
