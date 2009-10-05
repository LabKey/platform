/*
 * Copyright (c) 2006-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.filecontent;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentDirectory;
import org.labkey.api.attachments.AttachmentForm;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.HomeTemplate;
import org.labkey.api.view.template.PageConfig;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class FileContentController extends SpringActionController
{
   public enum RenderStyle
   {
       DEFAULT,     // call defaultRenderStyle
       FRAME,       // <iframe>                       (*)
       INLINE,      // use INCLUDE (INLINE is confusing, does NOT mean content-disposition:inline)
       INCLUDE,     // include html                   (text/html)
       PAGE,        // content-disposition:inline     (*)
       ATTACHMENT,  // content-disposition:attachment (*)
       TEXT,        // filtered                       (text/*)
       IMAGE        // <img>                          (image/*)
   }

   static DefaultActionResolver _actionResolver = new DefaultActionResolver(FileContentController.class);

   public FileContentController() throws Exception
   {
       super();
       setActionResolver(_actionResolver);
   }


/*   // UNDONE: need better way to set right pane
   protected ModelAndView getTemplate(ViewContext context, ModelAndView mv, Controller action, PageConfig page)
   {
       ModelAndView t = super.getTemplate(context, mv, action, page);
       if (action instanceof BeginAction && t instanceof HomeTemplate)
       {
           HomeTemplate template = (HomeTemplate)t;
           template.setView("right", new FileSetsWebPart(getContainer()));
       }
       return t;
   }
*/
    
    @RequiresPermission(ACL.PERM_READ)
    public class SendFileAction extends SimpleViewAction<SendFileForm>
    {
        File file;

        public ModelAndView getView(SendFileForm form, BindException errors) throws Exception
        {
            if (null == form.getFileName())
                throw new NotFoundException();

            String fileSet = StringUtils.trimToNull(form.getFileSet());
            AttachmentDirectory p;
            if (null == fileSet)
                p = AttachmentService.get().getMappedAttachmentDirectory(getContainer(), false);
            else
                p = AttachmentService.get().getRegisteredDirectory(getContainer(), form.getFileSet());

            if (null == p)
                throw new NotFoundException();

            File dir = p.getFileSystemDirectory();
            if (null == dir)
            {
                if (getUser().isAdministrator())
                    return HttpView.redirect("showAdmin.view");
                else
                    throw new NotFoundException();
            }

            file = new File(dir, form.getFileName());
            //Double check to make sure file is really a direct child of the parent (no escape from the configured tree...)
            // Also, check that it's not a directory
            if (!NetworkDrive.exists(file) || !file.getParentFile().equals(dir) || file.isDirectory())
                throw new NotFoundException();

            RenderStyle style = form.getRenderStyle();
            MimeMap mimeMap = new MimeMap();
            String mimeType = StringUtils.defaultString(mimeMap.getContentTypeFor(file.getName()), "");

            if (style == RenderStyle.DEFAULT)
            {
                style = defaultRenderStyle(file.getName());
            }
            else
            {
                // verify legal RenderStyle
                boolean canInline = mimeType.startsWith("text/") || mimeMap.isInlineImageFor(file.getName());
                if (!canInline && !(RenderStyle.ATTACHMENT == style || RenderStyle.PAGE == style))
                    style = RenderStyle.PAGE;
                if (RenderStyle.IMAGE == style && !mimeType.startsWith("image/"))
                    style = RenderStyle.PAGE;
                if (RenderStyle.TEXT == style && !mimeType.startsWith("text/"))
                    style = RenderStyle.PAGE;
            }

            //FIX: 5523 - if renderAs is null and mimetype is HTML, default style to inline
            if (null == form.getRenderAs())
            {
                if ("text/html".equalsIgnoreCase(mimeType))
                    style = RenderStyle.INCLUDE;
            }

            switch (style)
            {
                case ATTACHMENT:
                case PAGE:
                {
                    getPageConfig().setTemplate(PageConfig.Template.None);
                    PageFlowUtil.streamFile(getViewContext().getResponse(), file, RenderStyle.ATTACHMENT==style);
                    return null;
                }
                case FRAME:
                {
                    URLHelper url = new URLHelper(HttpView.getContextURL());
                    url.replaceParameter("renderAs", FileContentController.RenderStyle.PAGE.toString());
                    HttpView iframeView = new IFrameView(url.getLocalURIString());
                    return iframeView;
                }
                case INCLUDE:
                case INLINE:
                {
                    String fileContents = PageFlowUtil.getFileContentsAsString(file);
                    HtmlView webPart = new HtmlView(file.getName(), fileContents);
                    webPart.setFrame(WebPartView.FrameType.DIV);
                    return webPart;
                }
                case TEXT:
                {
                    String fileContents = PageFlowUtil.getFileContentsAsString(file);
                    String html = PageFlowUtil.filter(fileContents, true, true);
                    HttpView webPart = new HtmlView(file.getName(), html);
                    return webPart;
                }
                case IMAGE:
                {
                    URLHelper url = new URLHelper(HttpView.getContextURL());
                    url.replaceParameter("renderAs", FileContentController.RenderStyle.PAGE.toString());
                    HttpView imgView = new ImgView(url.getLocalURIString());
                    return imgView;
                }
                default:
                    return null;
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String name = file == null ? "<not found>" : file.getName();
            return (new BeginAction()).appendNavTrail(root)
                    .addChild(name);
        }
    }


   public static class SrcForm
   {
       private String src;

       public String getSrc()
       {
           return src;
       }

       public void setSrc(String src)
       {
           this.src = src;
       }
   }


   @RequiresPermission(ACL.PERM_READ)
   public class FrameAction extends SimpleViewAction<SrcForm>
   {
       public ModelAndView getView(SrcForm srcForm, BindException errors) throws Exception
       {
           String src = srcForm.getSrc();
           return new IFrameView(src);
       }

       public NavTree appendNavTrail(NavTree root)
       {
           return root;
       }
   }


    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction<FileContentForm>
    {
        public ModelAndView getView(FileContentForm form, BindException errors) throws Exception
        {
            FilesWebPart part;
            if (null == form.getFileSetName())
                part = new ManageWebPart(getContainer());
            else
                part = new ManageWebPart(getContainer(), form.getFileSetName());
            part.setFrame(WebPartView.FrameType.NONE);
            part.setWide(true);
            return part;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Manage Files", new ActionURL(BeginAction.class, getContainer()));
            return root;
        }
    }

   private static class FileSetsWebPart extends WebPartView
   {
       private Container c;
       public FileSetsWebPart(Container c)
       {
           this.c = c;
           setTitle("File Sets");
       }


       @Override
       protected void renderView(Object model, PrintWriter out) throws Exception
       {
           AttachmentDirectory main = AttachmentService.get().getMappedAttachmentDirectory(c, false);
           if (null != main && null != main.getFileSystemDirectory())
               out.write("<a href='begin.view'>Default</a><br>");

           for (AttachmentDirectory attDir : AttachmentService.get().getRegisteredDirectories(c))
               out.write("<a href='begin.view?fileSetName=" + PageFlowUtil.filter(attDir.getLabel()) + "'>" + PageFlowUtil.filter(attDir.getLabel()) + "</a><br>");

           if (HttpView.currentContext().getUser().isAdministrator())
               out.write("<br>[<a href='showAdmin.view'>Configure</a>]");
       }
   }



   @RequiresPermission(ACL.PERM_INSERT)
   public class AddAttachmentAction extends FormViewAction<AttachmentForm>
   {
       HttpView _closeView = null;

       public void validateCommand(AttachmentForm target, Errors errors)
       {
       }

       public ModelAndView getView(AttachmentForm form, boolean reshow, BindException errors) throws Exception
       {
           getPageConfig().setTemplate(PageConfig.Template.None);

           try
           {
                return AttachmentService.get().getAddAttachmentView(getContainer(), getAttachmentParent(form), errors);
           }
           catch (NotFoundException x)
           {
                return ExceptionUtil.getErrorView(HttpServletResponse.SC_NOT_FOUND, x.getMessage(), x, getViewContext().getRequest(), false, true);
           }
       }

       public boolean handlePost(AttachmentForm form, BindException errors) throws Exception
       {
           Map<String, MultipartFile> fileMap = getFileMap();
           if (fileMap.size() > 0 && !UserManager.mayWriteScript(getUser()))
           {
               for (MultipartFile formFile : fileMap.values())
               {
                   String contentType = new MimeMap().getContentTypeFor(formFile.getName());
                   if (formFile.getContentType().contains("html") || (null != contentType && contentType.contains("html")))
                   {
                       //This relies on storing whole file in memory. Generally OK for text files like this.
                       String html = new String(formFile.getBytes(),"UTF-8");
                       List<String> validateErrors = new ArrayList<String>();
                       List<String> safetyWarnings = new ArrayList<String>();
                       PageFlowUtil.validateHtml(html, validateErrors, safetyWarnings);
                       if (safetyWarnings.size() > 0)
                       {
                           addError(errors, "HTML Pages cannot contain script unless uploaded by a site administrator.");
                           //                            ActionURL reshow = getViewContext().cloneActionURL().setAction("showAddAttachment.view").replaceParameter("entityId", form.getEntityId());
                           //                            sb.append(PageFlowUtil.generateButton("Try Again", reshow));
                           //                            return includeView(new DialogTemplate(new HtmlView(sb.toString())));
                           return false;
                       }
                   }
               }
           }

           try
           {
               _closeView = AttachmentService.get().add(getUser(), getAttachmentParent(form), SpringAttachmentFile.createList(fileMap));
           }
           catch (NotFoundException x)
           {
               _closeView = ExceptionUtil.getErrorView(HttpServletResponse.SC_NOT_FOUND, x.getMessage(), x, getViewContext().getRequest(), false, true);
           }

           return true;
       }

       public ModelAndView getSuccessView(AttachmentForm attachmentForm)
       {
           getPageConfig().setTemplate(PageConfig.Template.None);
           return _closeView;
       }

       public ActionURL getSuccessURL(AttachmentForm attachmentForm)
       {
           return null;
       }

       public NavTree appendNavTrail(NavTree root)
       {
           return null;
       }
   }


   @RequiresPermission(ACL.PERM_DELETE)
   public class DeleteAttachmentAction extends ConfirmAction<AttachmentForm>
   {
       HttpView _closeView = null;

       public String getConfirmText()
       {
           return "Delete";
       }

       @Override
       public boolean isPopupConfirmation()
       {
           return true;
       }

       public ModelAndView getConfirmView(AttachmentForm form, BindException errors) throws Exception
       {
           getPageConfig().setShowHeader(false);
           getPageConfig().setTitle("Delete File?");
           return new HtmlView("Delete file " + form.getName() + "?");
           //return AttachmentService.get().getConfirmDeleteView(attachmentParent, form);
       }

       public boolean handlePost(AttachmentForm form, BindException errors) throws Exception
       {
           AttachmentDirectory dir;
           try
           {
               _closeView = AttachmentService.get().delete(getUser(), getAttachmentParent(form), form.getName());
           }
           catch (NotFoundException e)
           {
               _closeView = ExceptionUtil.getErrorView(HttpServletResponse.SC_NOT_FOUND, e.getMessage(), e, getViewContext().getRequest(), false, true);
           }
           return true;
       }

       public void validateCommand(AttachmentForm target, Errors errors)
       {
       }

       public ActionURL getSuccessURL(AttachmentForm attachmentForm)
       {
           return null;
       }

       public ModelAndView getSuccessView(AttachmentForm form)
       {
           getPageConfig().setTemplate(PageConfig.Template.None);
           return _closeView;
       }

       public ActionURL getFailURL(AttachmentForm attachmentForm, BindException errors)
       {
           return null;
       }
   }




   @RequiresSiteAdmin
   public class ShowAdminAction extends FormViewAction<FileContentForm>
   {
       public ShowAdminAction()
       {
       }

       public ShowAdminAction(ViewContext context)
       {
           setViewContext(context);
       }

       public ModelAndView getView(FileContentForm form, boolean reshow, BindException errors) throws Exception
       {
           File attachmentServiceRoot = AttachmentService.get().getWebRoot(getContainer());
           if (null == form.getRootPath() && null != attachmentServiceRoot)
           {
               String path = attachmentServiceRoot.getPath();
               try
               {
                   NetworkDrive.ensureDrive(path);
                   path = attachmentServiceRoot.getCanonicalPath();
               }
               catch (Exception e)
               {
                   logger.error("Could not get canonical path for " + attachmentServiceRoot.getPath() + ", using path as entered.", e);
               }
               form.setPath(path);
           }
           JspView adminView = new JspView<FileContentForm>("/org/labkey/filecontent/view/configure.jsp", form, errors);
           return adminView;
       }


       public void validateCommand(FileContentForm target, Errors errors)
       {
       }

       public boolean handlePost(FileContentForm fileContentForm, BindException errors) throws Exception
       {
           return false;
       }

       public ActionURL getSuccessURL(FileContentForm fileContentForm)
       {
           return null;
       }

       public NavTree appendNavTrail(NavTree root)
       {
           return (new BeginAction()).appendNavTrail(root)
                   .addChild("Administer File System Access");
       }
   }


   @RequiresSiteAdmin
   public class AddAttachmentDirectoryAction extends ShowAdminAction
   {
       public static final int MAX_NAME_LENGTH = 80;
       public static final int MAX_PATH_LENGTH = 255;
       public boolean handlePost(FileContentForm form, BindException errors) throws Exception
       {
           String name = StringUtils.trimToNull(form.getFileSetName());
           String path = StringUtils.trimToNull(form.getPath());

           if (null == name)
              	errors.reject(SpringActionController.ERROR_MSG, "Please enter a label for the file set. ");
           else if (name.length() > MAX_NAME_LENGTH)
                errors.reject(SpringActionController.ERROR_MSG, "Name is too long, should be less than " + MAX_NAME_LENGTH +" characters.");
           else
           {
               AttachmentDirectory attDir = AttachmentService.get().getRegisteredDirectory(getContainer(), name);
               if (null != attDir)
                   errors.reject(SpringActionController.ERROR_MSG, "A file set named "  + name + " already exists.");
           }
           if (null == path)
               errors.reject(SpringActionController.ERROR_MSG, "Please enter a full path to the file set.");
           else if (path.length() > MAX_PATH_LENGTH)
                errors.reject(SpringActionController.ERROR_MSG, "File path is too long, should be less than " + MAX_PATH_LENGTH + " characters.");

		   String message = "";
           if (errors.getErrorCount() == 0)
           {
               AttachmentService.get().registerDirectory(getContainer(), name, path, false);
               message = "Directory successfully registered.";
               File dir = new File(path);
               if (!dir.exists())
                   message += " NOTE: Directory does not currently exist. An administrator will have to create it before it can be used.";
               form.setPath(null);
               form.setFileSetName(null);
           }
           File webRoot = AttachmentService.get().getWebRoot(getContainer().getProject());
           form.setRootPath(webRoot == null ? null : webRoot.getCanonicalPath());
           form.setMessage(StringUtils.trimToNull(message));
           setReshow(true);
           return errors.getErrorCount() == 0;
       }
   }


   @RequiresSiteAdmin
   public class DeleteAttachmentDirectoryAction extends ShowAdminAction
   {
       public boolean handlePost(FileContentForm form, BindException errors) throws Exception
       {
           String name = StringUtils.trimToNull(form.getFileSetName());
           if (null == name)
		   {
               errors.reject(SpringActionController.ERROR_MSG, "No name for fileset supplied.");
		       return false;
		   }
           AttachmentDirectory attDir = AttachmentService.get().getRegisteredDirectory(getContainer(), name);
           if (null == attDir)
		   {
               form.setMessage("Attachment directory named " + name + " not found");
		   }
		   else
           {
               AttachmentService.get().unregisterDirectory(getContainer(), form.getFileSetName());
               form.setMessage("Directory was removed from list. Files were not deleted.");
               form.setPath(null);
               form.setFileSetName(null);
           }
           File webRoot = AttachmentService.get().getWebRoot(getContainer().getProject());
           form.setRootPath(webRoot == null ? null : webRoot.getCanonicalPath());
           setReshow(true);
		   return true;
       }
   }


   @RequiresSiteAdmin
   public class SaveRootAction extends ShowAdminAction
   {
       public boolean handlePost(FileContentForm form, BindException errors) throws Exception
       {
           String filePath = StringUtils.trimToNull(form.getRootPath());
           if (null == filePath)
           {
               AttachmentService.get().setWebRoot(getContainer(),  null);
			   form.message = "Path successfully cleared.";
			   setReshow(true);
			   return true;
           }
           File f = new File(filePath);
           if (!f.exists() || !f.isDirectory())
           {
               errors.reject(SpringActionController.ERROR_MSG, "Path " + filePath + " does not appear to be a valid directory accessible to the server at " + getViewContext().getRequest().getServerName() + ".");
           }
           else
           {
               AttachmentService.get().setWebRoot(getContainer(), f);
               form.message = "Path successfully saved.";
           }
		   setReshow(true);
           return errors.getErrorCount() == 0;
       }
   }


   public static class FileContentForm
   {
       private String rootPath;
       private String message;
       private String fileSetName;
       private String path;

       public String getRootPath()
       {
           return rootPath;
       }

       public void setRootPath(String rootPath)
       {
           this.rootPath = rootPath;
       }

       public String getMessage()
       {
           return message;
       }

       public void setMessage(String message)
       {
           this.message = message;
       }

       public String getFileSetName()
       {
           return fileSetName;
       }

       public void setFileSetName(String fileSetName)
       {
           this.fileSetName = fileSetName;
       }

       public void setPath(String path)
       {
           this.path = path;
       }

       public String getPath()
       {
           return path;
       }
   }

   public static class SendFileForm
   {
       private String fileName;
       private String renderAs;
       private String fileSet;

       public String getFileName()
       {
           return fileName;
       }

       public void setFileName(String fileName)
       {
           this.fileName = fileName;
       }

       public String getRenderAs()
       {
           return renderAs;
       }

       public void setRenderAs(String renderAs)
       {
           this.renderAs = renderAs;
       }

       public RenderStyle getRenderStyle()
       {
           if (null == renderAs)
               return FileContentController.RenderStyle.PAGE;

           //Will throw illegal argument exception for other values...
           return FileContentController.RenderStyle.valueOf(renderAs.toUpperCase());
       }

       public String getFileSet()
       {
           return fileSet;
       }

       public void setFileSet(String fileSet)
       {
           this.fileSet = fileSet;
       }
   }

   public static class CustomizeWebPartForm
   {
       private String pageId;
       private int index;
       private String fileSet;
       private String path;
       private boolean useFileSet;

       public CustomizeWebPartForm()
       {

       }

       public CustomizeWebPartForm(Portal.WebPart webPart)
       {
           pageId = webPart.getPageId();
           index = webPart.getIndex();
           fileSet = webPart.getPropertyMap().get("fileSet");
           path = webPart.getPropertyMap().get("path");
           useFileSet = fileSet != null;
       }

       public String getPageId()
       {
           return pageId;
       }

       public void setPageId(String pageId)
       {
           this.pageId = pageId;
       }

       public int getIndex()
       {
           return index;
       }

       public void setIndex(int index)
       {
           this.index = index;
       }

       public String getFileSet()
       {
           return fileSet;
       }

       public void setFileSet(String fileSet)
       {
           this.fileSet = fileSet;
       }

       public String getPath()
       {
           return path;
       }

       public void setPath(String path)
       {
           this.path = path;
       }

       public boolean isUseFileSet()
       {
           return useFileSet;
       }

       public void setUseFileSet(boolean useFileSet)
       {
           this.useFileSet = useFileSet;
       }
   }


    private static class IFrameView extends JspView<String>
    {
        public IFrameView(String url)
        {
			super(FileContentController.class, "view/iframe.jsp", url);
        }
    }


    private static class ImgView extends HtmlView
    {
        public ImgView(String url)
        {
            super("\n<img src=\"" + PageFlowUtil.filter(url) + "\">");
        }
    }


    void addError(BindException errors, String msg)
    {
        errors.addError(new ObjectError("form", new String[] {"Error"}, new Object[]{msg}, msg));
    }


    private AttachmentDirectory getAttachmentParent(AttachmentForm form) throws NotFoundException
    {
        AttachmentDirectory attachmentParent;
        try
        {
            if (null == form.getEntityId() || form.getEntityId().equals(getContainer().getId()))
                attachmentParent = AttachmentService.get().getMappedAttachmentDirectory(getContainer(), true);
            else
                attachmentParent = AttachmentService.get().getRegisteredDirectoryFromEntityId(getContainer(), form.getEntityId());
        }
        catch (AttachmentService.UnsetRootDirectoryException e)
        {
            throw new NotFoundException("The web root for this project is not set. Please contact an administrator.", e);
        }
        catch (AttachmentService.MissingRootDirectoryException e)
        {
            throw new NotFoundException("The web root for this project is set to a non-existent directory. Please contact an administrator", e);
        }

        boolean exists = false;
        try
        {
            exists = attachmentParent.getFileSystemDirectory().exists();
        }
        catch (AttachmentService.MissingRootDirectoryException ex)
        {
            /* */
        }
        if (!exists)
            throw new NotFoundException("Directory for saving file does not exist. Please contact an administrator.");

        return attachmentParent;
    }

    static MimeMap mimeMap = new MimeMap();
    
    public static RenderStyle defaultRenderStyle(String name)
    {
        if (mimeMap.isInlineImageFor(name))
            return RenderStyle.IMAGE;
        if (name.endsWith(".body"))
            return RenderStyle.INCLUDE;
        String mimeType = StringUtils.defaultString(mimeMap.getContentTypeFor(name),"");
        if (mimeType.equals("text/html"))
            return RenderStyle.INCLUDE;
        if (mimeType.startsWith("text/"))
            return RenderStyle.TEXT;
        return RenderStyle.PAGE;
    }
}
