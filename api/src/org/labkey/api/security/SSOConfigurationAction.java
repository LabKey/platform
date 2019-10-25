package org.labkey.api.security;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Table;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager.AuthLogoType;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public abstract class SSOConfigurationAction<F extends SSOConfigurationAction.SSOConfigurationForm<AC>, AC extends SSOAuthenticationConfiguration> extends FormViewAction<F>
{
    protected @Nullable AC _configuration = null;

    @Override
    protected String getCommandClassMethodName()
    {
        return "getView";
    }

    @Override
    public void validateCommand(F form, Errors errors)
    {
        Integer rowId = form.getConfiguration();

        if (null != rowId)
        {
            _configuration = (AC)AuthenticationManager.getSSOConfiguration(rowId);

            if (null == _configuration)
                throw new NotFoundException("Configuration not found");
        }
    }

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

        SSOAuthenticationConfiguration configuration = AuthenticationManager.getSSOConfiguration(form.getRowId());
        Map<String, MultipartFile> fileMap = getFileMap();
        boolean changedLogos = deleteLogos(form, configuration);

        try
        {
            changedLogos |= handleLogo(configuration, fileMap, AuthLogoType.HEADER);
            changedLogos |= handleLogo(configuration, fileMap, AuthLogoType.LOGIN_PAGE);
        }
        catch (Exception e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            return false;
        }

        // If user changed one or both logos then...
        if (changedLogos)
        {
            // Clear the image cache so the web server sends the new logo
            AttachmentCache.clearAuthLogoCache();
            // Bump the look & feel revision to force browsers to retrieve new logo
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
        }

        return true;  // Always reshow the page so user can view updates. After post, second button will change to "Done".
    }

    // Returns true if a new logo is saved
    private boolean handleLogo(SSOAuthenticationConfiguration configuration, Map<String, MultipartFile> fileMap, AuthLogoType logoType) throws IOException, ServletException
    {
        if (null == configuration)
            throw new NotFoundException("Configuration not found");

        MultipartFile file = fileMap.get(logoType.getFileName());

        if (null == file || file.isEmpty())
            return false;

        if (!file.getContentType().startsWith("image/"))
            throw new ServletException(file.getOriginalFilename() + " does not appear to be an image file");

        AttachmentFile aFile = new SpringAttachmentFile(file, logoType.getFileName());
        AttachmentService.get().addAttachments(configuration, Collections.singletonList(aFile), getUser());

        return true;
    }

    // Returns true if a logo is deleted
    public boolean deleteLogos(F form, SSOAuthenticationConfiguration configuration)
    {
        String[] deletedLogos = form.getDeletedLogos();

        if (null == deletedLogos)
            return false;

        for (String logoName : deletedLogos)
            AttachmentService.get().deleteAttachment(configuration, logoName, getUser());

        return true;
    }

    public static abstract class SSOConfigurationForm<AC extends SSOAuthenticationConfiguration> extends ReturnUrlForm
    {
        private Integer _configuration;
        private AC _authenticationConfiguration;
        protected String _description;
        private boolean _enabled = true;
        private boolean _autoRedirect = false;
        private String[] _deletedLogos;

        public Integer getRowId()
        {
            return _configuration;
        }

        public void setRowId(Integer rowId)
        {
            _configuration = rowId;
        }

        public @Nullable Integer getConfiguration()
        {
            return _configuration;
        }

        public void setConfiguration(Integer configuration)
        {
            _configuration = configuration;
        }

        public @Nullable AC getAuthenticationConfiguration()
        {
            return _authenticationConfiguration;
        }

        public void setAuthenticationConfiguration(AC authenticationConfiguration)
        {
            _authenticationConfiguration = authenticationConfiguration;

            if (null != _authenticationConfiguration)
            {
                _description = _authenticationConfiguration.getDescription();
                _enabled = _authenticationConfiguration.isEnabled();
                _autoRedirect = _authenticationConfiguration.isAutoRedirect();
            }
        }

        public abstract String getProvider();

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public boolean isEnabled()
        {
            return _enabled;
        }

        public void setEnabled(boolean enabled)
        {
            _enabled = enabled;
        }

        public boolean isAutoRedirect()
        {
            return _autoRedirect;
        }

        public void setAutoRedirect(boolean autoRedirect)
        {
            _autoRedirect = autoRedirect;
        }

        public String[] getDeletedLogos()
        {
            return _deletedLogos;
        }

        @SuppressWarnings("unused")
        public void setDeletedLogos(String[] deletedLogos)
        {
            _deletedLogos = deletedLogos;
        }
    }
}
