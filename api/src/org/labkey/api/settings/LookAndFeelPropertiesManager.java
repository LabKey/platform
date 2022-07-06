package org.labkey.api.settings;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.CoreUrls;
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
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Link.LinkBuilder;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.SafeToRenderEnum;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletException;
import java.io.IOException;
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

    // TODO: Merge with TemplateResourceHandler?
    public enum ResourceType implements StartupProperty, SafeToRenderEnum
    {
        logoImage
        {
            @Override
            public String getShortLabel()
            {
                return "Header logo";
            }

            @Override
            public String getDescription()
            {
                return "Logo that appears in the header of every page when the page width is 768px or greater";
            }

            @Override
            public HtmlString getHelpPopup()
            {
                return HtmlString.unsafe(PageFlowUtil.helpPopup("Header Logo", "Appears in the header on every page when the page width is 768px or greater.<br><br>Recommend size: 100px x 30px", true, 300));
            }

            @Override
            public LinkBuilder getViewLink(Container c)
            {
                return PageFlowUtil.link("view logo").href(TemplateResourceHandler.LOGO.getURL(c));
            }

            @Override
            public void delete(Container c, User user)
            {
                deleteExistingLogo(c, user);
            }

            @Override
            public String getFileName(Resource resource) throws ServletException
            {
                return getLogoFileName(resource.getName(), getAttachmentName());
            }

            @Override
            public void save(MultipartFile file, Container c, User user) throws ServletException, IOException
            {
                String logoFileName = getLogoFileName(file.getOriginalFilename(), getAttachmentName());
                save(new SpringAttachmentFile(file, logoFileName), c, user);
            }

            @Override
            public boolean isSet(Container c)
            {
                return null != AttachmentCache.lookupLogoAttachment(c);
            }

            @Override
            protected String getAttachmentName()
            {
                return AttachmentCache.LOGO_FILE_NAME_PREFIX;
            }

            @Override
            protected void clearCache()
            {
                AttachmentCache.clearLogoCache();
            }
        },
        logoMobileImage
        {
            @Override
            public String getShortLabel()
            {
                return "Responsive logo";
            }

            @Override
            public String getDescription()
            {
                return "Logo that appears in the header of every page when the page width is less than 768px";
            }

            @Override
            public HtmlString getHelpPopup()
            {
                return HtmlString.unsafe(PageFlowUtil.helpPopup("Responsive Logo", "Appears in the header on every page when the page width is less than 768px.<br><br>Recommend size: 30px x 30px", true, 300));
            }

            @Override
            public LinkBuilder getViewLink(Container c)
            {
                return PageFlowUtil.link("view logo").href(TemplateResourceHandler.LOGO_MOBILE.getURL(c));
            }

            @Override
            public void delete(Container c, User user)
            {
                deleteExistingLogo(c, user);
            }

            @Override
            public String getFileName(Resource resource) throws ServletException
            {
                return getLogoFileName(resource.getName(), getAttachmentName());
            }

            @Override
            public void save(MultipartFile file, Container c, User user) throws ServletException, IOException
            {
                String logoFileName = getLogoFileName(file.getOriginalFilename(), getAttachmentName());
                save(new SpringAttachmentFile(file, logoFileName), c, user);
            }

            @Override
            public boolean isSet(Container c)
            {
                return null != AttachmentCache.lookupMobileLogoAttachment(c);
            }

            @Override
            protected String getAttachmentName()
            {
                return AttachmentCache.MOBILE_LOGO_FILE_NAME_PREFIX;
            }

            @Override
            protected void clearCache()
            {
                AttachmentCache.clearLogoMobileCache();
            }
        },
        iconImage
        {
            @Override
            public String getShortLabel()
            {
                return "Favicon";
            }

            @Override
            public String getDescription()
            {
                return "Favorite icon displayed in browser tabs, favorites, and bookmarks. .ico files only.";
            }

            @Override
            public HtmlString getHelpPopup()
            {
                return HtmlString.unsafe(PageFlowUtil.helpPopup("Favicon", "Displayed in user's favorites or bookmarks, .ico file only", false, 300));
            }

            @Override
            public LinkBuilder getViewLink(Container c)
            {
                return PageFlowUtil.link("view icon").href(TemplateResourceHandler.FAVICON.getURL(c));
            }

            @Override
            protected String getAttachmentName()
            {
                return AttachmentCache.FAVICON_FILE_NAME;
            }

            @Override
            public void delete(Container c, User user)
            {
                deleteResource(c, user, this);
                clearCache();
            }

            @Override
            public String getFileName(Resource resource) throws ServletException
            {
                verifyIconFileName(resource.getName());
                return getAttachmentName();
            }

            @Override
            public void save(MultipartFile file, Container c, User user) throws ServletException, IOException
            {
                verifyIconFileName(file.getOriginalFilename());

                AttachmentFile attachmentFile = new SpringAttachmentFile(file, getAttachmentName());
                save(attachmentFile, c, user);
            }

            @Override
            public boolean isSet(Container c)
            {
                return null != AttachmentCache.lookupFavIconAttachment(new LookAndFeelResourceAttachmentParent(c));
            }

            @Override
            protected void clearCache()
            {
                AttachmentCache.clearFavIconCache();
            }
        },
        customStylesheet
        {
            @Override
            public String getShortLabel()
            {
                return "Stylesheet";
            }

            @Override
            public String getDescription()
            {
                return "Custom stylesheet. .css file only.";
            }

            @Override
            public LinkBuilder getViewLink(Container c)
            {
                return PageFlowUtil.link("view CSS").href(PageFlowUtil.urlProvider(CoreUrls.class).getCustomStylesheetURL(c));
            }

            @Override
            public String getDeleteText()
            {
                return "Delete custom stylesheet";
            }

            @Override
            public String getDefaultText()
            {
                return "No custom stylesheet";
            }

            @Override
            protected String getAttachmentName()
            {
                return AttachmentCache.STYLESHEET_FILE_NAME;
            }

            @Override
            public void delete(Container c, User user)
            {
                deleteResource(c, user, this);
                // This custom stylesheet is still cached in CoreController, but look & feel revision checking should ensure
                // that it gets cleared out on the next request.
            }

            @Override
            public String getFileName(Resource resource)
            {
                return getAttachmentName();
            }

            @Override
            public void save(MultipartFile file, Container c, User user) throws IOException
            {
                AttachmentFile attachmentFile = new SpringAttachmentFile(file, getAttachmentName());
                save(attachmentFile, c, user);
            }

            @Override
            public boolean isSet(Container c)
            {
                return null != AttachmentCache.lookupCustomStylesheetAttachment(new LookAndFeelResourceAttachmentParent(c));
            }

            @Override
            protected void clearCache()
            {
                // Don't need to clear cache -- lookAndFeelRevision gets checked on retrieval
            }
        };

        public abstract String getShortLabel();

        public String getLongLabel()
        {
            return getShortLabel();
        }
        public HtmlString getHelpPopup()
        {
            return HtmlString.EMPTY_STRING;
        }

        public abstract LinkBuilder getViewLink(Container c);

        public String getDeleteText()
        {
            return "Reset " + getShortLabel().toLowerCase() + " to default";
        }

        public String getDefaultText()
        {
            return "Currently using the default " + getShortLabel().toLowerCase();
        }

        public abstract void delete(Container c, User user);
        public abstract String getFileName(Resource resource) throws ServletException;
        public abstract void save(MultipartFile file, Container c, User user) throws ServletException, IOException;
        public abstract boolean isSet(Container c);

        // Returns either a prefix or a full filename, depending on type
        protected abstract String getAttachmentName();
        protected abstract void clearCache();

        protected final void save(AttachmentFile attachmentFile, Container c, User user) throws IOException
        {
            delete(c, user);
            LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
            AttachmentService.get().addAttachments(parent, Collections.singletonList(attachmentFile), user);
            clearCache();
        }

        protected void deleteExistingLogo(Container c, User user)
        {
            LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);

            for (Attachment attachment : AttachmentService.get().getAttachments(parent))
            {
                if (attachment.getName().startsWith(getAttachmentName()))
                {
                    AttachmentService.get().deleteAttachment(parent, attachment.getName(), user);
                    clearCache();
                }
            }
        }

        protected void deleteResource(Container c, User user, @NotNull ResourceType type)
        {
            LookAndFeelResourceAttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
            AttachmentService.get().deleteAttachment(parent, type.getAttachmentName(), user);
        }

        protected String getLogoFileName(String name, String attachmentPrefix) throws ServletException
        {
            int index = name.lastIndexOf(".");
            if (index == -1)
                throw new ServletException("No file extension on the uploaded image");

            // Set the name to something we'll recognize as a logo file
            return attachmentPrefix + name.substring(name.lastIndexOf("."));
        }

        protected void verifyIconFileName(String name) throws ServletException
        {
            if (!name.toLowerCase().endsWith(".ico"))
                throw new ServletException("FavIcon must be a .ico file");
        }
    }

    public @Nullable SiteResourceHandler getResourceHandler(@NotNull ResourceType type)
    {
        return (resource, c, user) -> {
            AttachmentFile attachmentFile = new InputStreamAttachmentFile(resource.getInputStream(), type.getFileName(resource));
            type.save(attachmentFile, c, user);
        };
    }

    @FunctionalInterface
    public interface SiteResourceHandler
    {
        void accept(Resource resource, Container container, User user) throws ServletException, IOException;
    }
}
