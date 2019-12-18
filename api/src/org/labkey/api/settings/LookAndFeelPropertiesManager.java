package org.labkey.api.settings;

import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.InputStreamAttachmentFile;
import org.labkey.api.attachments.LookAndFeelResourceAttachmentParent;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class LookAndFeelPropertiesManager
{
    private static final LookAndFeelPropertiesManager INSTANCE = new LookAndFeelPropertiesManager();

    private LookAndFeelPropertiesManager()
    {}

    public static LookAndFeelPropertiesManager get()
    {
        return INSTANCE;
    }

    public void handleLogoFile(MultipartFile file, Container c, User user) throws ServletException, IOException
    {
        String logoFileName = getLogoFileName(file.getOriginalFilename());
        AttachmentFile renamed = new SpringAttachmentFile(file, logoFileName);
        handleLogoFile(renamed, c, user);
    }

    public void handleLogoFile(Resource resource, Container c, User user) throws ServletException, IOException
    {
        String logoFileName = getLogoFileName(resource.getName());
        AttachmentFile attachmentFile = new InputStreamAttachmentFile(resource.getInputStream(), logoFileName);
        handleLogoFile(attachmentFile, c, user);
    }

    private String getLogoFileName(String name) throws ServletException
    {
        verifyLogoFileName(name);

        // Set the name to something we'll recognize as a logo file
        return AttachmentCache.LOGO_FILE_NAME_PREFIX + name.substring(name.lastIndexOf("."));
    }

    private void verifyLogoFileName(String name) throws ServletException
    {
        int index = name.lastIndexOf(".");
        if (index == -1)
            throw new ServletException("No file extension on the uploaded image");
    }

    private void handleLogoFile(AttachmentFile file, Container c, User user) throws IOException
    {
        deleteExistingLogo(c, user);

        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        AttachmentService.get().addAttachments(parent, Collections.singletonList(file), user);
        AttachmentCache.clearLogoCache();
    }

    public void deleteExistingLogo(Container c, User user)
    {
        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        Collection<Attachment> attachments = AttachmentService.get().getAttachments(parent);
        for (Attachment attachment : attachments)
        {
            if (attachment.getName().startsWith(AttachmentCache.LOGO_FILE_NAME_PREFIX))
            {
                AttachmentService.get().deleteAttachment(parent, attachment.getName(), user);
                AttachmentCache.clearLogoCache();
            }
        }
    }

    public void handleIconFile(MultipartFile file, Container c, User user) throws IOException, ServletException
    {
        verifyIconFileName(file.getOriginalFilename());

        AttachmentFile attachmentFile = new SpringAttachmentFile(file, AttachmentCache.FAVICON_FILE_NAME);
        handleIconFile(attachmentFile, c, user);
    }

    public void handleIconFile(Resource resource, Container c, User user) throws IOException, ServletException
    {
        verifyIconFileName(resource.getName());

        AttachmentFile attachmentFile = new InputStreamAttachmentFile(resource.getInputStream(), AttachmentCache.FAVICON_FILE_NAME);
        handleIconFile(attachmentFile, c, user);
    }

    private void verifyIconFileName(String name) throws ServletException
    {
        if (!name.toLowerCase().endsWith(".ico"))
            throw new ServletException("FavIcon must be a .ico file");
    }

    private void handleIconFile(AttachmentFile file, Container c, User user) throws IOException
    {
        deleteExistingFavicon(c, user);

        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        AttachmentService.get().addAttachments(parent, Collections.singletonList(file), user);
        AttachmentCache.clearFavIconCache();
    }

    public void deleteExistingFavicon(Container c, User user)
    {
        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        AttachmentService.get().deleteAttachment(parent, AttachmentCache.FAVICON_FILE_NAME, user);
        AttachmentCache.clearFavIconCache();
    }

    public void handleCustomStylesheetFile(MultipartFile file, Container c, User user) throws IOException
    {
        AttachmentFile attachmentFile = new SpringAttachmentFile(file, AttachmentCache.STYLESHEET_FILE_NAME);
        handleCustomStylesheetFile(attachmentFile, c, user);
    }

    public void handleCustomStylesheetFile(Resource resource, Container c, User user) throws IOException
    {
        AttachmentFile attachmentFile = new InputStreamAttachmentFile(resource.getInputStream(), AttachmentCache.STYLESHEET_FILE_NAME);
        handleCustomStylesheetFile(attachmentFile, c, user);
    }

    private void handleCustomStylesheetFile(AttachmentFile file, Container c, User user) throws IOException
    {
        deleteExistingCustomStylesheet(c, user);

        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        AttachmentService.get().addAttachments(parent, Collections.singletonList(file), user);

        // Don't need to clear cache -- lookAndFeelRevision gets checked on retrieval
    }

    public void deleteExistingCustomStylesheet(Container c, User user)
    {
        LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
        AttachmentService.get().deleteAttachment(parent, AttachmentCache.STYLESHEET_FILE_NAME, user);

        // This custom stylesheet is still cached in CoreController, but look & feel revision checking should ensure
        // that it gets cleared out on the next request.
    }
}
