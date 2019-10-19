package org.labkey.api.security;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.security.AuthenticationConfiguration.SSOAuthenticationConfiguration;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public abstract class PickAuthLogoAction<F extends PickAuthLogoAction.AuthLogoForm> extends FormViewAction<F>
{
    @Override
    public void validateCommand(F form, Errors errors)
    {
    }

    @Override
    public boolean handlePost(F form, BindException errors)
    {
        Map<String, MultipartFile> fileMap = getFileMap();

        boolean changedLogos = deleteLogos(form);

        try
        {
            changedLogos |= handleLogo(form.getConfiguration(), fileMap, AuthenticationManager.HEADER_LOGO_PREFIX);
            changedLogos |= handleLogo(form.getConfiguration(), fileMap, AuthenticationManager.LOGIN_PAGE_LOGO_PREFIX);
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

        return false;  // Always reshow the page so user can view updates.  After post, second button will change to "Done".
    }

    // Returns true if a new logo is saved
    private boolean handleLogo(String configurationName, Map<String, MultipartFile> fileMap, String prefix) throws IOException, ServletException
    {
        SSOAuthenticationConfiguration configuration = AuthenticationManager.getActiveSSOConfiguration(configurationName);

        if (null == configuration)
            throw new NotFoundException("Configuration not found");

        MultipartFile file = fileMap.get(prefix + "file");

        if (null == file || file.isEmpty())
            return false;

        if (!file.getContentType().startsWith("image/"))
            throw new ServletException(file.getOriginalFilename() + " does not appear to be an image file");

        AttachmentFile aFile = new SpringAttachmentFile(file, prefix + configuration.getAuthenticationProvider().getName());
        AttachmentService.get().addAttachments(AuthenticationLogoAttachmentParent.get(), Collections.singletonList(aFile), getUser());

        return true;
    }

    // Returns true if a logo is deleted
    public boolean deleteLogos(F form)
    {
        String[] deletedLogos = form.getDeletedLogos();

        if (null == deletedLogos)
            return false;

        for (String logoName : deletedLogos)
            AttachmentService.get().deleteAttachment(AuthenticationLogoAttachmentParent.get(), logoName, getUser());

        return true;
    }

    public static abstract class AuthLogoForm extends ReturnUrlForm
    {
        private String _configuration;
        private String[] _deletedLogos;

        public String getConfiguration()
        {
            return _configuration;
        }

        public void setConfiguration(String configuration)
        {
            _configuration = configuration;
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
