package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.annotations.RemoveIn20_1;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.security.AuthenticationManager.AuthLogoType;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@RemoveIn20_1
public abstract class SSOConfigureAction<F extends SSOConfigureAction.SSOConfigureForm<AC>, AC extends SSOAuthenticationConfiguration<?>> extends AuthenticationConfigureAction<F, AC>
{
    @Override
    public boolean handlePost(F form, BindException errors)
    {
        super.handlePost(form, errors);
        return handleLogos(form, errors);  // Always reshow the page so user can view updates. After post, second button will change to "Done".
    }

    protected boolean handleLogos(F form, BindException errors)
    {
        SSOAuthenticationConfiguration<?> configuration = AuthenticationManager.getSSOConfiguration(form.getRowId());
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

        return true;
    }

    // Returns true if a new logo is saved
    private boolean handleLogo(SSOAuthenticationConfiguration<?> configuration, Map<String, MultipartFile> fileMap, AuthLogoType logoType) throws IOException, ServletException
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
    public boolean deleteLogos(F form, SSOAuthenticationConfiguration<?> configuration)
    {
        String[] deletedLogos = form.getDeletedLogos();

        if (null == deletedLogos)
            return false;

        for (String logoName : deletedLogos)
            AttachmentService.get().deleteAttachment(configuration, logoName, getUser());

        return true;
    }

    @RemoveIn20_1
    public static abstract class SSOConfigureForm<AC extends SSOAuthenticationConfiguration<?>> extends SaveConfigurationForm<AC>
    {
        private boolean _autoRedirect = false;
        private String[] _deletedLogos;

        @Override
        public void setAuthenticationConfiguration(@NotNull AC authenticationConfiguration)
        {
            super.setAuthenticationConfiguration(authenticationConfiguration);

            _autoRedirect = authenticationConfiguration.isAutoRedirect();
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
