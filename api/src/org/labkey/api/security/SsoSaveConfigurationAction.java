package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager.AuthLogoType;
import org.labkey.api.security.AuthenticationSettingsAuditTypeProvider.AuthSettingsAuditEvent;
import org.labkey.api.security.SsoSaveConfigurationAction.SsoSaveConfigurationForm;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public abstract class SsoSaveConfigurationAction<F extends SsoSaveConfigurationForm, AC extends SSOAuthenticationConfiguration<?>> extends SaveConfigurationAction<F, AC>
{
    @Override
    public void save(F form, @Nullable User user, BindException errors)
    {
        super.save(form, user, errors);
        handleLogos(form, errors);  // Always reshow the page so user can view updates. After post, second button will change to "Done".
    }

    protected void handleLogos(F form, BindException errors)
    {
        SSOAuthenticationConfiguration<?> configuration = AuthenticationManager.getSSOConfiguration(form.getRowId());
        Map<String, MultipartFile> fileMap = getFileMap();
        boolean changedLogos = deleteLogos(getUser(), form, configuration);

        try
        {
            changedLogos |= handleLogo(getUser(), configuration, fileMap, AuthLogoType.HEADER);
            changedLogos |= handleLogo(getUser(), configuration, fileMap, AuthLogoType.LOGIN_PAGE);
        }
        catch (Exception e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
        }

        // If user changed one or both logos then...
        if (changedLogos)
        {
            // Clear the image cache so the web server sends the new logo
            AttachmentCache.clearAuthLogoCache();
            // Bump the look & feel revision to force browsers to retrieve new logo
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
        }
    }

    // Returns true if a new logo is saved
    private boolean handleLogo(User user, SSOAuthenticationConfiguration<?> configuration, Map<String, MultipartFile> fileMap, @NotNull AuthLogoType logoType) throws IOException, ServletException
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

        logLogoAction(user, configuration, logoType, "saved");

        return true;
    }

    // Returns true if a logo is deleted
    private boolean deleteLogos(User user, F form, SSOAuthenticationConfiguration<?> configuration)
    {
        String deletedLogos = form.getDeletedLogos();

        if (null == deletedLogos)
            return false;

        for (String logoName : deletedLogos.split(","))
        {
            AttachmentService.get().deleteAttachment(configuration, logoName, getUser());
            AuthLogoType logoType = AuthLogoType.getForFilename(logoName);
            logLogoAction(user, configuration, logoType, "deleted");
        }

        return true;
    }

    // TODO: restore to private and non-static once savePlaceholderLogos() upgrade code (from 21.008) is no longer needed
    public static void logLogoAction(User user, SSOAuthenticationConfiguration<?> configuration, @NotNull AuthLogoType logoType, String action)
    {
        AuthSettingsAuditEvent event = new AuthSettingsAuditEvent(logoType.getLabel() + " logo for " + configuration.getAuthenticationProvider().getName() + " authentication configuration \"" + configuration.getDescription() + "\" (" + configuration.getRowId() + ") was " + action);
        event.setChanges(logoType.getLabel() + " logo " + action);
        AuditLogService.get().addEvent(user, event);
    }

    @Override
    protected Map<String, Object> getConfigurationMap(int rowId)
    {
        AC configuration = getFromCache(rowId);
        return AuthenticationManager.getSsoConfigurationMap(configuration);
    }

    public static abstract class SsoSaveConfigurationForm extends SaveConfigurationForm
    {
        private boolean _autoRedirect = false;
        private String _deletedLogos;  // If non-null, this is a comma-separated list of logo names

        @SuppressWarnings("unused") // Accessed via reflection in Table.insert()/update()
        public boolean isAutoRedirect()
        {
            return _autoRedirect;
        }

        @SuppressWarnings("unused") // Accessed via reflection in Table.insert()/update()
        public void setAutoRedirect(boolean autoRedirect)
        {
            _autoRedirect = autoRedirect;
        }

        public String getDeletedLogos()
        {
            return _deletedLogos;
        }

        @SuppressWarnings("unused")
        public void setDeletedLogos(String deletedLogos)
        {
            _deletedLogos = deletedLogos;
        }
    }
}
