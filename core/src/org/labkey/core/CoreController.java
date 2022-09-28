/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.core;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormApiAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AbstractFolderContext.ExportType;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.CoreUrls;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.FolderImporter;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.assay.AssayQCService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentCache;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.LookAndFeelResourceAttachmentParent;
import org.labkey.api.data.CacheableWriter;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.ContainerTypeRegistry;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.NormalContainerType;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.OntologyObject;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.IgnoresForbiddenProjectCheck;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.qc.AbstractDeleteDataStateAction;
import org.labkey.api.qc.AbstractManageDataStatesForm;
import org.labkey.api.qc.AbstractManageQCStatesAction;
import org.labkey.api.qc.AbstractManageQCStatesBean;
import org.labkey.api.qc.DataStateHandler;
import org.labkey.api.qc.DeleteDataStateForm;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.usageMetrics.SimpleMetricsService;
import org.labkey.api.util.Compress;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.PageFlowUtil.Content;
import org.labkey.api.util.PageFlowUtil.NoContent;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResponseHelper;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.element.CsrfInput;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.WarningService;
import org.labkey.api.view.template.Warnings;
import org.labkey.api.webdav.WebdavResolver;
import org.labkey.api.webdav.WebdavResource;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiRenderingService;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.api.writer.Writer;
import org.labkey.api.writer.ZipUtil;
import org.labkey.core.metrics.WebSocketConnectionManager;
import org.labkey.core.portal.ProjectController;
import org.labkey.core.qc.CoreQCStateHandler;
import org.labkey.core.reports.ExternalScriptEngineDefinitionImpl;
import org.labkey.core.security.SecurityController;
import org.labkey.core.workbook.CreateWorkbookBean;
import org.labkey.core.workbook.MoveWorkbooksBean;
import org.labkey.core.workbook.WorkbookFolderType;
import org.labkey.folder.xml.FolderDocument;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.script.ScriptEngineFactory;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

import static org.labkey.api.view.template.WarningService.SESSION_WARNINGS_BANNER_KEY;

/**
 * User: jeckels
 * Date: Jan 4, 2007
 */
public class CoreController extends SpringActionController
{
    private static final Map<Container, Content> _customStylesheetCache = new ConcurrentHashMap<>();
    private static final Logger _log = LogHelper.getLogger(CoreController.class, "Attachment icon warnings");
    private static final ActionResolver _actionResolver = new DefaultActionResolver(CoreController.class);

    public CoreController()
    {
        setActionResolver(_actionResolver);
    }

    public static class CoreUrlsImpl implements CoreUrls
    {
        private ActionURL getRevisionURL(Class<? extends Controller> actionClass, Container c)
        {
            ActionURL url = new ActionURL(actionClass, c);
            url.addParameter("revision", AppProps.getInstance().getLookAndFeelRevision());
            return url;
        }

        @Override
        public ActionURL getCustomStylesheetURL()
        {
            return getCustomStylesheetURL(ContainerManager.getRoot());
        }

        @Override
        public ActionURL getCustomStylesheetURL(Container c)
        {
            Container settingsContainer = LookAndFeelProperties.getSettingsContainer(c);
            Content css;
            try
            {
                css = getCustomStylesheetContent(settingsContainer);
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }

            if (css instanceof NoContent)
                return null;
            return getRevisionURL(CustomStylesheetAction.class, settingsContainer);
        }

        @Override
        public ActionURL getDownloadFileLinkBaseURL(Container container, PropertyDescriptor pd)
        {
            return new ActionURL(DownloadFileLinkAction.class, container).addParameter("propertyId", pd.getPropertyId());
        }

        @Override
        public ActionURL getAttachmentIconURL(Container c, String filename)
        {
            ActionURL url = new ActionURL(GetAttachmentIconAction.class, c);

            if (null != filename)
            {
                int dotPos = filename.lastIndexOf(".");
                if (dotPos > -1 && dotPos < filename.length() - 1)
                    url.addParameter("extension", filename.substring(dotPos + 1).toLowerCase());
            }

            return url;
        }

        @Override
        public ActionURL getProjectsURL(Container c)
        {
            return new ActionURL(ProjectsAction.class, c);
        }

        @Override
        public ActionURL getPermissionsURL(@NotNull Container c)
        {
            return new ActionURL(SecurityController.PermissionsAction.class, c);
        }

        @Override
        public ActionURL getDismissWarningsActionURL(ViewContext viewContext)
        {
            return new ActionURL(DismissWarningsAction.class, viewContext.getContainer());
        }

        @Override
        public ActionURL getDisplayWarningsActionURL(ViewContext viewContext)
        {
            return new ActionURL(DisplayWarningsAction.class, viewContext.getContainer());
        }

        @Override
        public ActionURL getStyleGuideURL(@NotNull Container container)
        {
            return new ActionURL(CoreController.StyleGuideAction.class, container);
        }

        @Override
        public ActionURL getManageQCStatesURL(@NotNull Container container)
        {
            return new ActionURL(CoreController.ManageQCStatesAction.class, container);
        }
    }

    abstract class BaseStylesheetAction extends ExportAction
    {
        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            // Stylesheets can be retrieved always by anyone.  This do-nothing override is even more permissive than
            //  using @RequiresNoPermission and @IgnoresTermsOfUse since it also allows access in the root container even
            //  when impersonation is limited to a specific project.
        }

        @Override
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();
            Content content = getContent(request, response);

            // No custom stylesheet for this container
            if (content instanceof NoContent)
                return;

            PageFlowUtil.sendContent(request, response, content, getContentType());
        }

        String getContentType()
        {
            return "text/css";
        }

        abstract Content getContent(HttpServletRequest request, HttpServletResponse response) throws Exception;
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Configuration, "views and scripting", new ActionURL(ConfigureReportsAndScriptsAction.class, ContainerManager.getRoot()));
    }

    @RequiresPermission(ReadPermission.class)
    public class ProjectsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            Portal.WebPart config = new Portal.WebPart();
            config.setIndex(1);
            config.setRowId(-1);
            JspView<Portal.WebPart> view = new JspView<>("/org/labkey/core/project/projects.jsp", config);
            view.setTitle("Projects");
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DownloadFileLinkAction extends SimpleViewAction<DownloadFileLinkForm>
    {
        @Override
        public ModelAndView getView(DownloadFileLinkForm form, BindException errors) throws Exception
        {
            if (form.getPropertyId() == null)
            {
                throw new NotFoundException("No propertyId specified");
            }
            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(form.getPropertyId().intValue());
            if (pd == null)
                throw new NotFoundException();

            if (pd.getPropertyType() != PropertyType.FILE_LINK)
                throw new IllegalArgumentException("Property not file link type");

            OntologyObject obj = null;
            File file;
            if (form.getObjectId() != null || form.getObjectURI() != null)
            {
                if (form.getObjectId() != null)
                {
                    obj = OntologyManager.getOntologyObject(form.getObjectId().intValue());
                }
                else if (form.getObjectURI() != null)
                {
                    // Don't filter by container - we'll redirect to the correct container ourselves
                    obj = OntologyManager.getOntologyObject(null, form.getObjectURI());
                }
                if (obj == null)
                    throw new NotFoundException("No matching ontology object found");

                if (!obj.getContainer().equals(getContainer()))
                {
                    ActionURL correctedURL = getViewContext().getActionURL().clone();
                    Container objectContainer = obj.getContainer();
                    if (objectContainer == null)
                        throw new NotFoundException();
                    correctedURL.setContainer(objectContainer);
                    throw new RedirectException(correctedURL);
                }

                Map<String, ObjectProperty> properties = OntologyManager.getPropertyObjects(obj.getContainer(), obj.getObjectURI());
                ObjectProperty fileProperty = properties.get(pd.getPropertyURI());
                if (fileProperty == null || fileProperty.getPropertyType() != PropertyType.FILE_LINK || fileProperty.getStringValue() == null)
                    throw new NotFoundException();
                file = new File(fileProperty.getStringValue());
            }
            else if (form.getSchemaName() != null && form.getQueryName() != null && form.getPk() != null)
            {
                UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
                if (schema == null)
                    throw new NotFoundException("Schema not found");

                TableInfo table = schema.getTable(form.getQueryName(), false);
                if (table == null)
                    throw new NotFoundException("Query not found in schema");

                List<ColumnInfo> pkCols = table.getPkColumns();
                if (pkCols.size() != 1)
                    throw new NotFoundException("Query must have only one pk column");
                ColumnInfo pkCol = pkCols.get(0);

                ColumnInfo col = table.getColumn(pd.getName());
                if (col == null)
                    throw new NotFoundException("PropertyColumn not found on table");

                try
                {
                    Object pkVal = ConvertUtils.convert(form.getPk(), pkCol.getJavaClass());
                    SimpleFilter filter = new SimpleFilter(pkCol.getFieldKey(), pkVal);
                    try (Results results = QueryService.get().select(table, Collections.singletonList(col), filter, null))
                    {
                        if (results.getSize() != 1 || !results.next())
                            throw new NotFoundException("Row not found for primary key");

                        String filename = results.getString(col.getFieldKey());
                        if (filename == null)
                            throw new NotFoundException();

                        file = new File(filename);
                    }
                }
                catch (ConversionException e)
                {
                    throw new NotFoundException("Invalid value specified for PK, could not convert to " + pkCol.getJavaClass());
                }
            }
            else
            {
                throw new NotFoundException("objectURI or schemaName, queryName, and pk required.");
            }

            // For security reasons, make sure the user hasn't tried to download a file that's not under
            // the pipeline root.  Otherwise, they could get access to any file on the server.
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
            if (root == null)
                throw new NotFoundException("No pipeline root for container " + getContainer().getPath());

            if (!root.hasPermission(getContainer(), getUser(), ReadPermission.class))
                throw new UnauthorizedException();

            if (!root.isUnderRoot(file))
                throw new NotFoundException("Cannot download file that isn't under the pipeline root for container " + getContainer().getPath());

            if (!file.exists())
            {
                Identifiable identifiable = null;
                if (obj != null)
                    identifiable = LsidManager.get().getObject(obj.getObjectURI());
                if (identifiable != null && identifiable.getName() != null)
                {
                    throw new NotFoundException("The file '" + file.getName() + "' attached to the object '" + identifiable.getName() + "' cannot be found. It may have been deleted.");
                }
                throw new NotFoundException("File " + file.getPath() + " does not exist on the server file system. It may have been deleted.");
            }

            if (file.isDirectory())
                ZipUtil.zipToStream(getViewContext().getResponse(), file, false);
            else
            {
                // If the URL has requested that the content be sent inline or not (instead of as an attachment), respect that
                // Otherwise, default to sending as attachment
                MimeMap.MimeType mime = (new MimeMap()).getMimeTypeFor(file.getName());
                boolean canInline = mime != null && mime.canInline() && mime != MimeMap.MimeType.HTML;
                PageFlowUtil.streamFile(getViewContext().getResponse(), file.toPath(), !canInline || form.getInline() == null || !form.getInline().booleanValue());
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException("Not Yet Implemented");
        }
    }

    public static class DownloadFileLinkForm
    {
        private Integer _propertyId;
        private Integer _objectId;
        private String _objectURI;
        private SchemaKey _schemaName;
        private String _queryName;
        private String _pk;
        private Boolean _inline;

        public Integer getObjectId()
        {
            return _objectId;
        }

        public void setObjectId(Integer objectId)
        {
            _objectId = objectId;
        }

        public Integer getPropertyId()
        {
            return _propertyId;
        }

        public void setPropertyId(Integer propertyId)
        {
            _propertyId = propertyId;
        }

        public String getObjectURI()
        {
            return _objectURI;
        }

        public void setObjectURI(String objectURI)
        {
            _objectURI = objectURI;
        }

        public SchemaKey getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(SchemaKey schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public String getPk()
        {
            return _pk;
        }

        public void setPk(String pk)
        {
            _pk = pk;
        }

        public Boolean getInline()
        {
            return _inline;
        }

        public void setInline(Boolean inline)
        {
            _inline = inline;
        }
    }

    @RequiresNoPermission
    @IgnoresTermsOfUse
    @AllowedDuringUpgrade
    public class CustomStylesheetAction extends BaseStylesheetAction
    {
        @Override
        Content getContent(HttpServletRequest request, HttpServletResponse response) throws Exception
        {
            return getCustomStylesheetContent(getContainer());
        }
    }


    private static Content getCustomStylesheetContent(Container c) throws IOException, ServletException
    {
        Content content = _customStylesheetCache.get(c);
        Integer dependsOn = AppProps.getInstance().getLookAndFeelRevision();

        if (null == content || !dependsOn.equals(content.dependencies))
        {
            AttachmentParent parent = new LookAndFeelResourceAttachmentParent(c);
            Attachment cssAttachment = AttachmentCache.lookupCustomStylesheetAttachment(parent);

            if (null == cssAttachment)
            {
                content = new NoContent(dependsOn);
            }
            else
            {
                CacheableWriter writer = new CacheableWriter();
                AttachmentService.get().writeDocument(writer, parent, cssAttachment.getName(), false);
                content = new Content(new String(writer.getBytes()));
                content.dependencies = dependsOn;
                content.compressed = compressCSS(content.content);
            }

            _customStylesheetCache.put(c, content);
        }

        return content;
    }

    private static byte[] compressCSS(String s)
    {
        String c = s;

        try
        {
            if (!StringUtilsLabKey.isText(s))
            {
                c = "\n/* CSS FILE CONTAINS NON-PRINTABLE CHARACTERS */\n";
            }
            else
            {
                c = c.replaceAll("/\\*(?:.|[\\n\\r])*?\\*/", "");
                c = c.replaceAll("(?:\\s|[\\n\\r])+", " ");
                c = c.replaceAll("\\s*}\\s*", "}\r\n");
            }
        }
        catch (StackOverflowError e)
        {
            // replaceAll() can blow up
        }
        return Compress.compressGzip(c.trim());
    }

    public static class GetAttachmentIconForm
    {
        private String _extension;

        public String getExtension()
        {
            return _extension;
        }

        public void setExtension(String extension)
        {
            _extension = extension;
        }
    }

    @RequiresNoPermission
    public class GetAttachmentIconAction extends SimpleViewAction<GetAttachmentIconForm>
    {
        @Override
        public ModelAndView getView(GetAttachmentIconForm form, BindException errors) throws Exception
        {
            String path = Attachment.getFileIcon(StringUtils.trimToEmpty(form.getExtension()));

            if (path != null)
            {
                //open the file and stream it back to the client
                HttpServletResponse response = getViewContext().getResponse();
                response.setContentType(PageFlowUtil.getContentTypeFor(path));
                ResponseHelper.setPublic(response);

                WebdavResolver staticFiles = ServiceRegistry.get().getService(WebdavResolver.class);
                WebdavResource file = staticFiles.lookup(Path.parse(path));

                if (file != null)
                {
                    try (OutputStream os = response.getOutputStream(); InputStream is = file.getInputStream())
                    {
                        if (null != is)
                            FileUtil.copyData(is, os);
                    }
                }
                else
                {
                    _log.warn("Unable to retrieve icon file: " + path);
                }
            }
            else
            {
                _log.warn("No icon file found for extension: " + StringUtils.trimToEmpty(form.getExtension()));
            }
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    // Requires at least insert permission. Will check for admin if needed
    @RequiresPermission(InsertPermission.class)
    public class CreateContainerAction extends MutatingApiAction<SimpleApiJsonForm>
    {
        @Override
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors)
        {
            JSONObject json = form.getNewJsonObject();
            if (json == null)
            {
                throw new NotFoundException("No JSON posted");
            }
            String name = StringUtils.trimToNull(json.optString("name"));
            String title = StringUtils.trimToNull(json.optString("title"));
            String description = StringUtils.trimToNull(json.optString("description"));
            String typeName = StringUtils.trimToNull(json.optString("type"));
            boolean isWorkbook = false;
            if (typeName == null)
            {
                isWorkbook = json.optBoolean("isWorkbook");
                typeName = isWorkbook ? WorkbookContainerType.NAME : NormalContainerType.NAME;
            }
            ContainerType type = ContainerTypeRegistry.get().getType(typeName);
            if (type == null)
                throw new ApiUsageException("Unknown container type: " + typeName);

            Class<? extends Permission> permClass = type.getPermissionNeededToCreate();
            if (!getContainer().hasPermission(getUser(), permClass))
            {
                Permission perm = RoleManager.getPermission(permClass);
                throw new UnauthorizedException("Insufficient permissions to create subfolders. " + perm.getName() + " permission required.");
            }

            if (name != null && getContainer().getChild(name) != null)
            {
                throw new ApiUsageException("A child container with the name '" + name + "' already exists");
            }

            try
            {
                String folderTypeName = json.optString("folderType", isWorkbook ? WorkbookFolderType.NAME : null);

                FolderType folderType = null;
                if (folderTypeName != null)
                {
                    folderType = FolderTypeManager.get().getFolderType(folderTypeName);
                }

                if (null != folderType && Container.hasRestrictedModule(folderType) && !getContainer().hasEnableRestrictedModules(getUser()))
                {
                    throw new UnauthorizedException("The folder type requires a restricted module for which you do not have permission.");
                }

                Set<Module> ensureModules = new HashSet<>();
                if (json.has("ensureModules") && !json.isNull("ensureModules"))
                {
                    List<String> requestedModules = StreamSupport.stream(json.getJSONArray("ensureModules").spliterator(), false)
                        .map(Object::toString).toList();
                    for (String moduleName : requestedModules)
                    {
                        Module module = ModuleLoader.getInstance().getModule(moduleName);
                        if (module == null)
                            throw new NotFoundException("'" + moduleName + "' was not found.");
                        else if (module.getRequireSitePermission() && !getContainer().hasEnableRestrictedModules(getUser()))
                            throw new UnauthorizedException("'" + moduleName + "' is a restricted module for which you do not have permission.");
                        else
                            ensureModules.add(module);
                    }
                }

                Container newContainer = ContainerManager.createContainer(getContainer(), name, title, description, typeName, getUser());
                if (folderType != null)
                {
                    newContainer.setFolderType(folderType, getUser(), errors);
                }
                if (!ensureModules.isEmpty())
                {
                    ensureModules.addAll(newContainer.getActiveModules());
                    newContainer.setActiveModules(ensureModules);
                }

                return new ApiSimpleResponse(newContainer.toJSON(getUser()));
            }
            catch (IllegalArgumentException e)
            {
                throw new ApiUsageException(e);
            }
        }
    }

    // Requires at least delete permission. Will check for admin if needed
    @RequiresPermission(DeletePermission.class)
    public class DeleteContainerAction extends MutatingApiAction<SimpleApiJsonForm>
    {
        private Container target;

        @Override
        public void validateForm(SimpleApiJsonForm form, Errors errors)
        {
            target = getContainer();

            if (!ContainerManager.isDeletable(target))
                errors.reject(ERROR_MSG, "The path " + target.getPath() + " is not deletable.");
        }

        @Override
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors)
        {
            Class<? extends Permission> permClass = getContainer().getPermissionNeededToDelete();
            if (!target.hasPermission(getUser(), permClass))
            {
                Permission perm = RoleManager.getPermission(permClass);
                throw new UnauthorizedException("Insufficient permissions to delete folder. " + perm.getName() + " permission required.");
            }

            ContainerManager.deleteAll(target, getUser());

            return new ApiSimpleResponse();
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class MoveContainerAction extends MutatingApiAction<SimpleApiJsonForm>
    {
        private Container target;
        private Container parent;
        
        @Override
        public void validateForm(SimpleApiJsonForm form, Errors errors)
        {
            JSONObject object = form.getNewJsonObject();
            String targetIdentifier = object.getString("container");

            if (null == targetIdentifier)
            {
                errors.reject(ERROR_MSG, "A target container must be specified for move operation.");
                return;
            }

            String parentIdentifier = object.getString("parent");

            if (null == parentIdentifier)
            {
                errors.reject(ERROR_MSG, "A parent container must be specified for move operation.");
                return;
            }

            // Worry about escaping
            Path path = Path.parse(targetIdentifier);
            target = ContainerManager.getForPath(path);            

            if (null == target)
            {
                target = ContainerManager.getForId(targetIdentifier);
                if (null == target)
                {
                    errors.reject(ERROR_MSG, "Container '" + targetIdentifier + "' does not exist.");
                    return;
                }
            }

            // This covers /home and /shared
            if (target.isProject() || target.isRoot())
            {
                errors.reject(ERROR_MSG, "Cannot move project/root Containers.");
                return;
            }

            Path parentPath = Path.parse(parentIdentifier);
            parent = ContainerManager.getForPath(parentPath);

            if (null == parent)
            {
                parent = ContainerManager.getForId(parentIdentifier);
                if (null == parent)
                {
                    errors.reject(ERROR_MSG, "Parent container '" + parentIdentifier + "' does not exist.");
                    return;
                }
            }

            // Check children
            if (parent.hasChildren())
            {
                List<Container> children = parent.getChildren();
                for (Container child : children)
                {
                    if (child.getName().toLowerCase().equals(target.getName().toLowerCase()))
                    {
                        errors.reject(ERROR_MSG, "Subfolder of '" + parent.getPath() + "' with name '" +
                                target.getName() + "' already exists.");
                        return;
                    }
                }
            }

            // Make sure not attempting to make parent a child. Might need to do this with permission bypass.
            List<Container> children = ContainerManager.getAllChildren(target, getUser()); // assumes read permission
            if (children.contains(parent))
            {
                errors.reject(ERROR_MSG, "The container '" + parentIdentifier + "' is not a valid parent folder.");
                return;
            }
        }

        @Override
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            // Check if parent is unchanged
            if (target.getParent().getPath().equals(parent.getPath()))
            {
                return new ApiSimpleResponse("success", true);
            }

            // Prepare aliases
            JSONObject object = form.getNewJsonObject();
            Boolean addAlias = (Boolean) object.get("addAlias");
            
            List<String> aliasList = new ArrayList<>();
            aliasList.addAll(ContainerManager.getAliasesForContainer(target));
            aliasList.add(target.getPath());
            
            // Perform move
            ContainerManager.move(target, parent, getUser());

            Container afterMoveTarget = ContainerManager.getForId(target.getId());
            if (null != afterMoveTarget)
            {
                // Save aliases
                if (addAlias)
                    ContainerManager.saveAliasesForContainer(afterMoveTarget, aliasList, getUser());

                // Prepare response
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("newPath", afterMoveTarget.getPath());
                return new ApiSimpleResponse(response);                
            }
            return new ApiSimpleResponse();
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class CreateWorkbookAction extends SimpleViewAction<CreateWorkbookBean>
    {
        @Override
        public ModelAndView getView(CreateWorkbookBean bean, BindException errors)
        {
            if (bean.getTitle() == null)
            {
                //suggest a name
                //per spec it should be "<user-display-name> YYYY-MM-DD"
                bean.setTitle(getUser().getDisplayName(getUser()) + " " + DateUtil.formatDateISO8601());
            }

            return new JspView<>("/org/labkey/core/workbook/createWorkbook.jsp", bean, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Create New Workbook");
        }
    }

    public static class UpdateDescriptionForm
    {
        private String _description;

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class UpdateDescriptionAction extends MutatingApiAction<UpdateDescriptionForm>
    {
        @Override
        public ApiResponse execute(UpdateDescriptionForm form, BindException errors) throws Exception
        {
            String description = StringUtils.trimToNull(form.getDescription());
            ContainerManager.updateDescription(getContainer(), description, getUser());
            return new ApiSimpleResponse("description", description);
        }
    }

    public static class UpdateTitleForm
    {
        private String _title;

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class UpdateTitleAction extends MutatingApiAction<UpdateTitleForm>
    {
        @Override
        public ApiResponse execute(UpdateTitleForm form, BindException errors) throws Exception
        {
            String title = StringUtils.trimToNull(form.getTitle());
            ContainerManager.updateTitle(getContainer(), title, getUser());
            return new ApiSimpleResponse("title", title);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class MoveWorkbooksAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            Container parentContainer = getContainer();
            Set<String> ids = DataRegionSelection.getSelected(getViewContext(), true);
            if (ids.isEmpty())
                throw new RedirectException(parentContainer.getStartURL(getUser()));

            MoveWorkbooksBean bean = new MoveWorkbooksBean();
            for (String id : ids)
            {
                Container wb = ContainerManager.getForId(id);
                if (null != wb)
                    bean.addWorkbook(wb);
            }

            return new JspView<>("/org/labkey/core/workbook/moveWorkbooks.jsp", bean, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Move Workbooks");
        }
    }

    public static class ExtContainerTreeForm
    {
        private int _node;
        private boolean _move = false;
        private boolean _showContainerTabs = false;
        private boolean _useTitles = false;
        private boolean _annotateLeaf = false;
        private String _requiredPermission;

        public int getNode()
        {
            return _node;
        }

        public void setNode(int node)
        {
            _node = node;
        }

        public boolean isMove()
        {
            return _move;
        }

        public void setMove(boolean move)
        {
            _move = move;
        }
        
        public String getRequiredPermission()
        {
            return _requiredPermission;
        }

        public void setRequiredPermission(String requiredPermission)
        {
            _requiredPermission = requiredPermission;
        }

        public boolean isShowContainerTabs()
        {
            return _showContainerTabs;
        }

        public void setShowContainerTabs(boolean showContainerTabs)
        {
            _showContainerTabs = showContainerTabs;
        }

        public boolean isUseTitles()
        {
            return _useTitles;
        }

        public void setUseTitles(boolean useTitles)
        {
            _useTitles = useTitles;
        }

        public boolean isAnnotateLeaf()
        {
            return _annotateLeaf;
        }

        public void setAnnotateLeaf(boolean annotateLeaf)
        {
            _annotateLeaf = annotateLeaf;
        }
    }

    private enum AccessType
    {
        /** User shouldn't see the folder at all */
        none,
        /** User has permission to access the folder itself */
        direct,
        /** User doesn't have permission to access the folder, but it still needs to be displayed because they have access to a subfolder */
        indirect
    }

    @RequiresPermission(ReadPermission.class)
    public class GetExtContainerTreeAction extends ReadOnlyApiAction<ExtContainerTreeForm>
    {
        protected Class<? extends Permission> _reqPerm = ReadPermission.class;
        protected boolean _move = false;
        
        @Override
        public ApiResponse execute(ExtContainerTreeForm form, BindException errors) throws Exception
        {
            User user = getUser();
            JSONArray children = new JSONArray();
            _move = form.isMove();

            Container parent = ContainerManager.getForRowId(form.getNode());
            if (null != parent)
            {
                if (!form.isShowContainerTabs() && parent.isContainerTab())
                    parent = parent.getParent();            // Don't show container tab, show parent

                //determine which permission should be required for a child to show up
                if (null != form.getRequiredPermission())
                {
                    Permission perm = RoleManager.getPermission(form.getRequiredPermission());
                    if (null != perm)
                        _reqPerm = perm.getClass();
                }

                for (Container child : parent.getChildren())
                {
                    // Don't show workbook and don't show containerTabs if we're told not to
                    if (child.includePropertiesAsChild(form.isShowContainerTabs()))
                    {
                        AccessType accessType = getAccessType(child, user, _reqPerm);
                        if (accessType != AccessType.none)
                        {
                            JSONObject childProps = getContainerProps(child, form);
                            if (accessType == AccessType.indirect)
                            {
                                // Disable so they can't act on it directly, since they have no permission
                                childProps.put("disabled", true);
                            }
                            children.put(childProps);
                        }
                    }
                }
            }

            HttpServletResponse resp = getViewContext().getResponse();
            resp.setContentType("application/json");
            resp.getWriter().write(children.toString());

            return null;
        }

        /**
         * Determine if the user can access the folder directly, or only because they have permission to a subfolder,
         * or not at all
         */
        protected AccessType getAccessType(Container container, User user, Class<? extends Permission> perm)
        {
            if (container.hasPermission(user, perm))
            {
                return AccessType.direct;
            }
            // If no direct permission, check if they have permission to a subfolder
            for (Container child : container.getChildren())
            {
                AccessType childAccess = getAccessType(child, user, perm);
                if (childAccess == AccessType.direct || childAccess == AccessType.indirect)
                {
                    // They can access a subfolder, so give them indirect access so they can see but not use it
                    return AccessType.indirect;
                }
            }
            // No access to the folder or any of its subfolders
            return AccessType.none;
        }

        protected JSONObject getContainerProps(Container c, ExtContainerTreeForm form)
        {
            JSONObject props = new JSONObject();
            props.put("id", c.getRowId());
            props.put("text", form.isUseTitles() ? c.getTitle() : c.getName());
            props.put("containerPath", c.getPath());
            props.put("expanded", false);
            props.put("iconCls", "x4-tree-icon-parent");
            props.put("isContainerTab", c.isContainerTab());
            props.put("folderTypeHasContainerTabs", c.getFolderType().hasContainerTabs());
            props.put("containerTabTypeOveridden", ContainerManager.isContainerTabTypeThisOrChildrenOverridden(c));      // also set if child container tab overridden

            // default to exclude leaf boolean because you cannot 'drop' on a leaf as an append action
            if (form.isAnnotateLeaf())
                props.put("leaf", !c.hasChildren());

            return props;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetExtSecurityContainerTreeAction extends GetExtContainerTreeAction
    {
        @Override
        protected JSONObject getContainerProps(Container c, ExtContainerTreeForm form)
        {
            JSONObject props = super.getContainerProps(c, form);
            String text = c.getName();
            if (!c.getPolicy().getResourceId().equals(c.getResourceId()))
                text += "*";
            if (c.equals(getContainer()))
                props.put("cls", "tree-node-selected");

            props.put("text", text);

            ActionURL url = new ActionURL(SecurityController.PermissionsAction.class, c);
            props.put("href", url.getLocalURIString());

            //if the current container is an ancestor of the request container
            //recurse into the children so that we can show the request container
            if (getContainer().isDescendant(c))
            {
                JSONArray childrenProps = new JSONArray();
                for (Container child : c.getChildren())
                {
                    if (child.includePropertiesAsChild(form.isShowContainerTabs()))
                    {
                        AccessType accessType = getAccessType(child, getUser(), _reqPerm);
                        if (accessType != AccessType.none)
                        {
                            JSONObject childProps = getContainerProps(child, form);
                            if (accessType == AccessType.indirect)
                            {
                                // Disable so they can't act on it directly, since they have no permission
                                childProps.put("disabled", true);
                            }
                            childProps.put("expanded", getContainer().isDescendant(child));
                            childrenProps.put(childProps);
                        }
                    }
                }
                props.put("children", childrenProps);
                props.put("expanded", true);
            }
            
            return props;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetExtMWBContainerTreeAction extends GetExtContainerTreeAction
    {
        @Override
        protected JSONObject getContainerProps(Container c, ExtContainerTreeForm form)
        {
            JSONObject props = super.getContainerProps(c, form);
            if (c.equals(getContainer()))
                props.put("disabled", true);
            return props;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetExtContainerAdminTreeAction extends GetExtContainerTreeAction
    {
        @Override
        protected JSONObject getContainerProps(Container c, ExtContainerTreeForm form)
        {
            JSONObject props = super.getContainerProps(c, form);
            if (c.equals(getContainer()))
            {
                props.put("cls", "x-tree-node-current");
                if (_move)
                    props.put("hidden", true);
            }

            props.put("isProject", c.isProject());

            if (c.equals(ContainerManager.getHomeContainer()) || c.equals(ContainerManager.getSharedContainer()) ||
                    c.equals(ContainerManager.getRoot()))
            {
                props.put("notModifiable", true);
            }

            //if the current container is an ancestor of the request container
            //recurse into the children so that we can show the request container
            if (getContainer().isDescendant(c))
            {
                JSONArray childrenProps = new JSONArray();
                for (Container child : c.getChildren())
                {
                    if (child.includePropertiesAsChild(form.isShowContainerTabs()))
                    {
                        AccessType accessType = getAccessType(child, getUser(), _reqPerm);
                        if (accessType != AccessType.none)
                        {
                            JSONObject childProps = getContainerProps(child, form);
                            //childProps.put("expanded", true);
                            if (accessType == AccessType.indirect)
                            {
                                // Disable so they can't act on it directly, since they have no permission
                                childProps.put("disabled", true);
                            }
                            childrenProps.put(childProps);
                        }
                    }
                }
                props.put("children", childrenProps);
                props.put("expanded", true);
            }

            return props;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class GetContainerTreeRootInfoAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            response.put("current", getContainerProps(getContainer()));

            Container p = getContainer().getProject();
            if (p != null)
                response.put("project", getContainerProps(p));

            return new ApiSimpleResponse(response);
        }

        private Map<String, Object> getContainerProps(Container c)
        {
            Map<String, Object> props = new HashMap<>();
            props.put("id", c.getRowId());
            props.put("title", c.getTitle());
            props.put("path", c.getPath());
            props.put("isDataspace", c.isDataspace());
            return props;
        }
    }
    
    public static class MoveWorkbookForm
    {
        public int _workbookId = -1;
        public int _newParentId = -1;

        public int getNewParentId()
        {
            return _newParentId;
        }

        public void setNewParentId(int newParentId)
        {
            _newParentId = newParentId;
        }

        public int getWorkbookId()
        {

            return _workbookId;
        }

        public void setWorkbookId(int workbookId)
        {
            _workbookId = workbookId;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class MoveWorkbookAction extends MutatingApiAction<MoveWorkbookForm>
    {
        @Override
        public ApiResponse execute(MoveWorkbookForm form, BindException errors) throws Exception
        {
            if (form.getWorkbookId() < 0)
                throw new IllegalArgumentException("You must supply a workbookId parameter!");
            if (form.getNewParentId() < 0)
                throw new IllegalArgumentException("You must specify a newParentId parameter!");

            Container wb = ContainerManager.getForRowId(form.getWorkbookId());
            if (null == wb || !(wb.isWorkbook()) || !(wb.isDescendant(getContainer())))
                throw new IllegalArgumentException("No workbook found with id '" + form.getWorkbookId() + "'");

            Container newParent = ContainerManager.getForRowId(form.getNewParentId());
            if (null == newParent || newParent.isWorkbook())
                throw new IllegalArgumentException("No folder found with id '" + form.getNewParentId() + "'");

            if (wb.getParent().equals(newParent))
                throw new IllegalArgumentException("Workbook is already in the target folder.");

            //user must be allowed to create workbooks in the new parent folder
            if (!newParent.hasPermission(getUser(), InsertPermission.class))
                throw new UnauthorizedException("You do not have permission to move workbooks to the folder '" + newParent.getName() + "'.");

            //workbook name must be unique within parent
            if (newParent.hasChild(wb.getName()))
                throw new RuntimeException("Can't move workbook '" + wb.getTitle() + "' because another workbook or subfolder in the target folder has the same name.");

            ContainerManager.move(wb, newParent, getUser());

            return new ApiSimpleResponse("moved", true);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetFolderTypesAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors)
        {
            Map<String, Object> folderTypes = new HashMap<>();
            for (FolderType folderType : FolderTypeManager.get().getEnabledFolderTypes(true))
            {
                Map<String, Object> folderTypeJSON = new HashMap<>();
                folderTypeJSON.put("name", folderType.getName());
                folderTypeJSON.put("description", folderType.getDescription());
                folderTypeJSON.put("defaultModule", folderType.getDefaultModule() == null ? null : folderType.getDefaultModule().getName());
                folderTypeJSON.put("label", folderType.getLabel());
                folderTypeJSON.put("workbookType", folderType.isWorkbookType());
                folderTypeJSON.put("hasRestrictedModule", Container.hasRestrictedModule(folderType));
                folderTypeJSON.put("isProjectOnlyType", folderType.isProjectOnlyType());
                List<String> activeModulesJSON = new ArrayList<>();
                for (Module module : folderType.getActiveModules())
                {
                    activeModulesJSON.add(module.getName());
                }
                folderTypeJSON.put("activeModules", activeModulesJSON);
                folderTypeJSON.put("requiredWebParts", toJSON(folderType.getRequiredWebParts()));
                folderTypeJSON.put("preferredWebParts", toJSON(folderType.getPreferredWebParts()));
                folderTypes.put(folderType.getName(), folderTypeJSON);
            }
            return new ApiSimpleResponse(folderTypes);
        }

        private List<Map<String, Object>> toJSON(List<Portal.WebPart> webParts)
        {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Portal.WebPart webPart : webParts)
            {
                Map<String, Object> webPartJSON = new HashMap<>();
                webPartJSON.put("name", webPart.getName());
                webPartJSON.put("properties", webPart.getPropertyMap());
                result.add(webPartJSON);
            }
            return result;
        }
    }

    @RequiresPermission(UpdatePermission.class) @RequiresLogin
    public class GetModulePropertiesAction extends ReadOnlyApiAction<ModulePropertiesForm>
    {
        @Override
        public ApiResponse execute(ModulePropertiesForm form, BindException errors)
        {
            JSONObject ret = new JSONObject();

            if (form.getModuleName() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the name of the module");
                return null;
            }

            Module m = ModuleLoader.getInstance().getModule(form.getModuleName());
            if (m == null)
            {
                errors.reject(ERROR_MSG, "Unknown module: " + form.getModuleName());
                return null;
            }

            List<ModuleProperty> included = new ArrayList<>();
            if(form.getProperties() == null)
            {
                included.addAll(m.getModuleProperties().values());
            }
            else
            {
                for (String name : form.getProperties())
                    included.add(m.getModuleProperties().get(name));
            }

            if(form.isIncludePropertyValues())
            {
                JSONObject siteValues = new JSONObject();
                for (ModuleProperty mp : included)
                {
                    JSONObject record = new JSONObject();

                    Container c = mp.isCanSetPerContainer() ? getContainer() :  ContainerManager.getRoot();
                    User propUser = PropertyManager.SHARED_USER;   //currently user-specific props not supported
                    int propUserId = propUser.getUserId();

                    Map<Container, Map<Integer, String>> propValues = PropertyManager.getPropertyValueAndAncestors(propUser, c, mp.getCategory(), mp.getName(), true);
                    List<JSONObject> containers = new ArrayList<>();
                    for (Container ct : propValues.keySet())
                    {
                        JSONObject o = new JSONObject();
                        o.put("value", propValues.get(ct) != null && propValues.get(ct).get(propUserId) != null ? propValues.get(ct).get(propUserId) : "");
                        o.put("container", ct.toJSON(getUser()));
                        boolean canEdit = true;
                        for (Class<? extends Permission> p : mp.getEditPermissions())
                        {
                            if (!ct.hasPermission(getUser(), p))
                            {
                                canEdit = false;
                                break;
                            }
                        }
                        o.put("canEdit", canEdit);
                        if (mp.isOptionsByContainer() && null != mp.getOptionsSupplier())
                        {
                            o.put("options", ModuleProperty.toOptionMaps(mp.getOptionsSupplier().get(ct)));
                        }
                        containers.add(o);
                        ct = ct.getParent();
                    }
                    record.put("effectiveValue", mp.getEffectiveValue(getContainer()));
                    Collections.reverse(containers);  //reverse so root first
                    record.put("siteValues", containers);

                    siteValues.put(mp.getName(), record);
                }
                ret.put("values", siteValues);
            }

            if(form.isIncludePropertyDescriptors())
            {
                Map<String, JSONObject> pds = new LinkedHashMap<>();
                for (ModuleProperty mp : included)
                {
                    pds.put(mp.getName(), mp.toJson(getContainer()));
                }

                ret.put("properties", pds);
            }

            return new ApiSimpleResponse(ret);
        }
    }

    static class ModulePropertiesForm
    {
        private String _moduleName;
        private String[] _properties;
        private boolean _includePropertyDescriptors;
        private boolean _includePropertyValues;

        public String getModuleName()
        {
            return _moduleName;
        }

        public void setModuleName(String moduleName)
        {
            _moduleName = moduleName;
        }

        public String[] getProperties()
        {
            return _properties;
        }

        public void setProperties(String[] properties)
        {
            _properties = properties;
        }

        public boolean isIncludePropertyDescriptors()
        {
            return _includePropertyDescriptors;
        }

        public void setIncludePropertyDescriptors(boolean includePropertyDescriptors)
        {
            _includePropertyDescriptors = includePropertyDescriptors;
        }

        public boolean isIncludePropertyValues()
        {
            return _includePropertyValues;
        }

        public void setIncludePropertyValues(boolean includePropertyValues)
        {
            _includePropertyValues = includePropertyValues;
        }
    }

    //Note: ModuleProperty.saveValue() performs additional permissions check
    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class SaveModulePropertiesAction extends MutatingApiAction<SaveModulePropertiesForm>
    {
        @Override
        public ApiResponse execute(SaveModulePropertiesForm form, BindException errors)
        {
            ViewContext ctx = getViewContext();
            JSONObject formData = form.getNewJsonObject();
            JSONArray a = formData.getJSONArray("properties");
            try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
            {
                for (int i = 0 ; i < a.length(); i++)
                {
                    JSONObject row = a.getJSONObject(i);
                    String moduleName = row.getString("moduleName");
                    String name = row.getString("propName");
                    if (moduleName == null)
                        throw new IllegalArgumentException("Missing moduleName for property: " + name);
                    if (name == null)
                        throw new IllegalArgumentException("Missing property name");

                    Module m = ModuleLoader.getInstance().getModule(moduleName);
                    if (m == null)
                        throw new IllegalArgumentException("Unknown module: " + moduleName);

                    ModuleProperty mp = m.getModuleProperties().get(name);
                    if (mp == null)
                        throw new IllegalArgumentException("Invalid module property: " + name);

                    String containerId = row.optString("container", null);
                    Container ct = null != containerId ? ContainerManager.getForId(containerId) : null;
                    if (ct == null && row.getBoolean("currentContainer"))
                        ct = getContainer();
                    if (ct == null)
                        throw new IllegalArgumentException("Invalid container: " + row.getString("container"));

                    mp.saveValue(ctx.getUser(), ct, row.optString("value", null));
                }
                transaction.commit();
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }

            JSONObject ret = new JSONObject();
            ret.put("success", errors.getErrorCount() == 0);
            return new ApiSimpleResponse(ret);
        }
    }

    public static class SaveModulePropertiesForm extends SimpleApiJsonForm
    {
        String moduleName;
        String properties;

        public String getModuleName()
        {
            return moduleName;
        }

        public void setModuleName(String moduleName)
        {
            this.moduleName = moduleName;
        }

        public String getProperties()
        {
            return properties;
        }

        public void setProperties(String properties)
        {
            this.properties = properties;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @IgnoresTermsOfUse  // Used by folder management, which is used to configure important terms/compliance settings (e.g., active modules)
    public class GetContainerInfoAction extends ReadOnlyApiAction<ContainerInfoForm>
    {
        @Override
        public ApiResponse execute(ContainerInfoForm form, BindException errors)
        {
            // Provide information about container, specifically an array of child tab folders that were deleted
            Container container = form.getContainerPath() != null ? ContainerManager.getForPath(form.getContainerPath()) : getContainer();
            JSONArray deletedFolders = new JSONArray();
            for (FolderTab folderTab : container.getDeletedTabFolders(form.getNewFolderType()))
            {
                JSONObject deletedFolder = new JSONObject();
                deletedFolder.put("label", folderTab.getCaption(getViewContext()));
                deletedFolder.put("name", folderTab.getName());
                deletedFolders.put(deletedFolder);
            }
            JSONObject ret = new JSONObject();
            ret.put("deletedFolders", deletedFolders);
            ret.put("success", errors.getErrorCount() == 0);
            return new ApiSimpleResponse(ret);
        }
    }

    public static class ContainerInfoForm
    {
        private String _containerPath;
        private String _newFolderType;

        public String getContainerPath()
        {
            return _containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            _containerPath = containerPath;
        }

        public String getNewFolderType()
        {
            return _newFolderType;
        }

        public void setNewFolderType(String newFolderType)
        {
            _newFolderType = newFolderType;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetRegisteredFolderWritersAction extends ReadOnlyApiAction<FolderWriterForm>
    {
        @Override
        public ApiResponse execute(FolderWriterForm form, BindException errors)
        {
            FolderSerializationRegistry registry = FolderSerializationRegistry.get();
            if (null == registry)
            {
                throw new RuntimeException();
            }

            Collection<FolderWriter> registeredWriters = registry.getRegisteredFolderWriters();
            List<Map<String, Object>> writerChildrenMap = new ArrayList<>();

            for (FolderWriter writer : registeredWriters)
            {
                Map<String, Object> writerMap = new HashMap<>();
                String dataType = writer.getDataType();
                boolean excludeForDataspace = "Study".equals(dataType) && shouldExcludeStudyForDataspace();
                boolean excludeForTemplate = form.isForTemplate() && !writer.includeWithTemplate();

                if (dataType != null && writer.show(getContainer()) && !excludeForDataspace && !excludeForTemplate)
                {
                    writerMap.put("name", dataType);
                    writerMap.put("selectedByDefault", writer.selectedByDefault(form.getExportType()));

                    Collection<Writer<?, ?>> childWriters = writer.getChildren(true, form.isForTemplate());
                    if (!childWriters.isEmpty())
                    {
                        List<String> children = new ArrayList<>();
                        for (Writer<?, ?> child : childWriters)
                        {
                            dataType = child.getDataType();
                            if (dataType != null)
                                children.add(dataType);
                        }

                        if (children.size() > 0)
                            writerMap.put("children", children);
                    }

                    writerChildrenMap.add(writerMap);
                }
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("writers", writerChildrenMap);
            return response;
        }

        private boolean shouldExcludeStudyForDataspace()
        {
            Study study = StudyService.get().getStudy(getContainer());
            if (study == null)
                return false;

            return !study.allowExport(getUser());
        }
    }

    public static class FolderWriterForm
    {
        private String _exportType;
        private boolean _forTemplate;

        public ExportType getExportType()
        {
            if ("study".equalsIgnoreCase(_exportType))
                return ExportType.STUDY;

            return ExportType.ALL;
        }

        public void setExportType(String exportType)
        {
            _exportType = exportType;
        }

        public boolean isForTemplate()
        {
            return _forTemplate;
        }

        public void setForTemplate(boolean forTemplate)
        {
            _forTemplate = forTemplate;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetRegisteredFolderImportersAction extends ReadOnlyApiAction<FolderImporterForm>
    {
        @Override
        public ApiResponse execute(FolderImporterForm form, BindException errors) throws Exception
        {
            FolderSerializationRegistry registry = FolderSerializationRegistry.get();
            if (null == registry)
                throw new RuntimeException();

            List<FolderImporter> registeredImporters = new ArrayList<>(registry.getRegisteredFolderImporters());
            if (form.isSortAlpha())
                registeredImporters.sort(new ImporterAlphaComparator());

            List<Map<String, Object>> selectableImporters = isCloudArchive(form) ?
                    getCloudArchiveImporters(form, registeredImporters) :
                    getSelectableImporters(form, registeredImporters);

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("importers", selectableImporters);
            return response;
        }

        private static final String DATATYPE_KEY = "dataType";
        private static final String DESCRIPTION_KEY = "description";
        private static final String IS_VALID_FOR_ARCHIVE_KEY = "isValidForImportArchive";

        private List<Map<String, Object>> getCloudArchiveImporters(FolderImporterForm form, List<FolderImporter> registeredImporters) throws Exception
        {
            return getSelectableImporters(form, registeredImporters, true, null);
        }

        private boolean isCloudArchive(FolderImporterForm form)
        {
            return FileUtil.hasCloudScheme(form.getArchiveFilePath());
        }

        private List<Map<String, Object>> getSelectableImporters(FolderImporterForm form, List<FolderImporter> registeredImporters) throws Exception
        {
            FolderImportContext folderImportCtx = getFolderImportContext(form);
            boolean isZipArchive = isZipArchive(form); // if archive is a zip, we can't tell what objects it has at this point

            return getSelectableImporters(form, registeredImporters, isZipArchive, folderImportCtx);
        }

        private List<Map<String, Object>> getSelectableImporters(FolderImporterForm form, List<FolderImporter> registeredImporters, boolean isZipOrCloudArchive, @Nullable FolderImportContext folderImportCtx) throws Exception
        {
            List<Map<String, Object>> selectableImporters = new ArrayList<>();
            for (FolderImporter importer : registeredImporters)
            {
                if (importer.getDataType() != null)
                {
                    selectableImporters.add(getImporterProps(form, importer, isZipOrCloudArchive, folderImportCtx));
                }
            }

            return selectableImporters;
        }

        private Map<String, Object> getImporterProps(FolderImporterForm form, FolderImporter importer, boolean isZipOrCloudArchive, @Nullable FolderImportContext folderImportCtx) throws Exception
        {
            Map<String, Object> importerMap = new HashMap<>();
            importerMap.put(DATATYPE_KEY, importer.getDataType());
            importerMap.put(DESCRIPTION_KEY, importer.getDescription());
            importerMap.put(IS_VALID_FOR_ARCHIVE_KEY, isZipOrCloudArchive || (folderImportCtx != null && importer.isValidForImportArchive(folderImportCtx)));

            Map<String, Boolean> childrenDataTypes = importer.getChildrenDataTypes(form.getArchiveFilePath(), getUser(), getContainer());
            if (childrenDataTypes != null)
            {
                importerMap.put("children", getChildProps(childrenDataTypes, isZipOrCloudArchive));
            }

            return importerMap;
        }

        private List<Map<String, Object>> getChildProps(Map<String, Boolean> childrenDataTypes, boolean isZipArchive)
        {
            List<Map<String, Object>> childrenProps = new ArrayList<>();
            for (Map.Entry<String, Boolean> entry : childrenDataTypes.entrySet())
            {
                Map<String, Object> props = new HashMap<>();
                props.put(DATATYPE_KEY, entry.getKey());
                props.put(IS_VALID_FOR_ARCHIVE_KEY, isZipArchive || entry.getValue());
                childrenProps.add(props);
            }

            return childrenProps;
        }
    }

    private FolderImportContext getFolderImportContext(FolderImporterForm form) throws IOException
    {
        VirtualFile vf = getArchiveFileParent(form.getArchiveFilePath());
        if (vf != null)
        {
            XmlObject folderXml = vf.getXmlBean("folder.xml");
            if (folderXml instanceof FolderDocument)
                return new FolderImportContext(getUser(), getContainer(), (FolderDocument) folderXml, null, null, vf);
        }

        return null;
    }

    private boolean isZipArchive(FolderImporterForm form) throws IOException
    {
        // consider this a zip archive if the file ends with .zip and isn't sitting next to a folder.xml
        if (form.getArchiveFilePath() != null && form.getArchiveFilePath().toLowerCase().endsWith(".zip"))
        {
            VirtualFile vf = getArchiveFileParent(form.getArchiveFilePath());
            return null != vf && vf.getXmlBean("folder.xml") == null;
        }
        return false;
    }

    private VirtualFile getArchiveFileParent(String archiveFilePath)
    {
        if (archiveFilePath != null)
        {
            java.nio.file.Path archiveFile = FileUtil.stringToPath(getContainer(), archiveFilePath);
            if (Files.exists(archiveFile) && Files.isRegularFile(archiveFile))
            {
                return new FileSystemFile(archiveFile.getParent());
            }
        }

        return null;
    }

    private static class ImporterAlphaComparator implements Comparator<FolderImporter>
    {
        @Override
        public int compare(FolderImporter o1, FolderImporter o2)
        {
            if (o1.getDataType() == null && o2.getDataType() == null)
                return 0;
            else if (o1.getDataType() == null)
                return -1;
            else
                return o1.getDataType().compareTo(o2.getDataType());
        }
    }

    public static class FolderImporterForm
    {
        private boolean _sortAlpha;
        private String _archiveFilePath;

        public boolean isSortAlpha()
        {
            return _sortAlpha;
        }

        public void setSortAlpha(boolean sortAlpha)
        {
            _sortAlpha = sortAlpha;
        }

        public String getArchiveFilePath()
        {
            return _archiveFilePath;
        }

        public void setArchiveFilePath(String archiveFilePath)
        {
            _archiveFilePath = archiveFilePath;
        }
    }

    public static class LoadLibraryForm
    {
        private String[] _library;

        public String[] getLibrary()
        {
            return _library;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setLibrary(String[] library)
        {
            _library = library;
        }
    }

    @RequiresNoPermission
    @IgnoresForbiddenProjectCheck // Skip the "forbidden project" check since it disallows root. See #43278.
    public class LoadLibraryAction extends ReadOnlyApiAction<LoadLibraryForm>
    {
        @Override
        public void validateForm(LoadLibraryForm form, Errors errors)
        {
            if (form.getLibrary() == null || form.getLibrary().length == 0)
            {
                errors.reject(ERROR_MSG, "Specify a \"library\" to load.");
            }
        }

        @Override
        public Object execute(LoadLibraryForm form, BindException errors)
        {
            String[] requestLibraries = form.getLibrary();
            JSONObject libraries = new JSONObject();

            for (String library : requestLibraries)
            {
                if (!StringUtils.isBlank(library))
                {
                    ClientDependency cd = ClientDependency.fromPath(library);

                    // only allow libs
                    if (cd != null && ClientDependency.TYPE.lib.equals(cd.getPrimaryType()))
                    {
                        Set<String> dependencies = cd.getCssPaths(getContainer());
                        dependencies.addAll(PageFlowUtil.getExtJSStylesheets(getContainer(), Collections.singleton(cd)));
                        dependencies.addAll(cd.getJsPaths(getContainer()));
                        libraries.put(library, new JSONArray(dependencies));
                    }
                }
            }

            ApiSimpleResponse response = new ApiSimpleResponse("success", true);
            response.put("libraries", libraries);

            return response;
        }
    }


    // use of illegal filename chars is intentional
    private static final String TOKEN_PREFIX = "upload://";

    static File getUploadDir(User user, String session)
    {
        if (user.isGuest())
            throw new UnauthorizedException();
        File tmpDir = FileUtil.getTempDirectory();
        File userDir = new File(tmpDir,"loadingDock/" + session);
        userDir.mkdirs();
        return userDir;
    }


    public static File getUploadedFileForUser(User user, String session, String token)
    {
        if (!token.startsWith(TOKEN_PREFIX))
            return null;
        token = token.substring(TOKEN_PREFIX.length());

        File uploadDir = getUploadDir(user, session);
        File tokenDir = new File(uploadDir, token);
        if (!tokenDir.isDirectory())
            return null;
        File[] files = tokenDir.listFiles();
        if (null == files || files.length != 1)
            return null;
        return files[0];
    }


    //@RequiresLogin
    @RequiresSiteAdmin
    public class PreUploadAction extends FormApiAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            // only for testing!
            if (!AppProps.getInstance().isDevMode() || !getUser().hasRootAdminPermission())
                throw new UnauthorizedException("under development");
            return new HtmlView("<form method=\"POST\" enctype=\"multipart/form-data\">"+
                    "<input name=file type=file><input type=submit>" +
                    new CsrfInput(getViewContext()) +
                    "</form>");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }

        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            if (!AppProps.getInstance().isDevMode() || !getUser().hasRootAdminPermission())
                throw new UnauthorizedException("under development");

            JSONObject ret = new JSONObject();

            HttpServletRequest request = getViewContext().getRequest();
            if (!(request instanceof MultipartHttpServletRequest))
                throw new BadRequestException("Expected multi-part form");
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
            Map<String, MultipartFile> map = multipartRequest.getFileMap();
            if (map.size() == 0)
                return ret;
            if (map.size() > 1)
                throw new BadRequestException("Expected one file");

            // TODO cleanup on server shutdown/startup
            // TODO register session cleanup event
            // TODO check quota
            File uploadDir = getUploadDir(getUser(), request.getSession().getId());
            File location;
            do
            {
                String uniq = GUID.makeHash();
                location = new File(uploadDir, uniq);
            }
            while (location.exists());    // pretty unlikely to have a collision...
            location.mkdir();
            location.deleteOnExit();

            Map.Entry<String, MultipartFile> entry = map.entrySet().iterator().next();
            MultipartFile mp = entry.getValue();
            File target = new File(location, mp.getOriginalFilename());
            target.deleteOnExit();

            boolean copied = false;
            if (entry.getValue() instanceof CommonsMultipartFile)
            {
                CommonsMultipartFile mpf = (CommonsMultipartFile)entry.getValue();
                if (mpf.getFileItem() instanceof DiskFileItem)
                {
                    DiskFileItem dfi = (DiskFileItem)mpf.getFileItem();
                    if (!dfi.isInMemory() && dfi.getStoreLocation().isFile())
                        copied = dfi.getStoreLocation().renameTo(target);
                }
            }
            if (!copied)
                FileUtil.copyData(mp.getInputStream(), target);

            ret.put("success", true);
            ret.put("token", TOKEN_PREFIX + location.getName());
            return ret;
        }
    }


    @RequiresNoPermission
    public class StyleGuideAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/core/view/styleGuide.jsp", o, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("LabKey Style Guide");
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ConfigureReportsAndScriptsAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView("/org/labkey/core/view/configReportsAndScripts.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("configureScripting");
            urlProvider(AdminUrls.class).addAdminNavTrail(root, "Views and Scripting Configuration", getClass(), getContainer());
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ScriptEnginesSummaryAction extends ReadOnlyApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            List<Map<String, Object>> views = new ArrayList<>();

            LabKeyScriptEngineManager manager = LabKeyScriptEngineManager.get();

            for (ScriptEngineFactory factory : manager.getEngineFactories())
            {
                Map<String, Object> record = new HashMap<>();

                record.put("name", factory.getEngineName());
                record.put("extensions", StringUtils.join(factory.getExtensions(), ','));
                record.put("languageName", factory.getLanguageName());
                record.put("languageVersion", factory.getLanguageVersion());

                boolean isExternal = factory instanceof ExternalScriptEngineFactory;
                record.put("external", String.valueOf(isExternal));

                LabKeyScriptEngineManager svc = LabKeyScriptEngineManager.get();
                record.put("enabled", String.valueOf(svc.isFactoryEnabled(factory)));

                if (isExternal)
                {
                    // extra metadata for external engines
                    ExternalScriptEngineDefinition def = ((ExternalScriptEngineFactory)factory).getDefinition();

                    //Skip remote engines if Premium module isn't available.   //TODO: should this be further qualified with engine type?
                    if (def.isRemote() && !PremiumService.get().isRemoteREnabled())
                        continue;

                    record.put("rowId", def.getRowId());
                    if (def.getType() != null)
                        record.put("type", def.getType().name());
                    record.put("remote", def.isRemote());

                    record.put("exePath", def.getExePath());
                    record.put("exeCommand", def.getExeCommand());
                    record.put("outputFileName", def.getOutputFileName());
                    record.put("pandocEnabled", String.valueOf(def.isPandocEnabled()));
                    record.put("docker", String.valueOf(def.isDocker()));
                    record.put("dockerImageRowId", def.getDockerImageRowId());
                    record.put("dockerImageConfig", def.getDockerImageConfig());
                    record.put("default", String.valueOf(def.isDefault()));
                    record.put("sandboxed", String.valueOf(def.isSandboxed()));

                    if (def.isRemote())
                    {
                        record.put("machine", def.getMachine());
                        record.put("port", String.valueOf(def.getPort()));

                        PathMapper pathMap = def.getPathMap();
                        if (pathMap != null)
                            record.put("pathMap", pathMap.toJSON());
                        else
                            record.put("pathMap", null);

                        record.put("user", def.getUser());
                        // don't send down password
                        //record.put("password", def.getPassword());
                    }
                }
                views.add(record);
            }
            return new ApiSimpleResponse("views", views);
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ScriptEnginesSaveAction extends MutatingApiAction<ExternalScriptEngineDefinitionImpl>
    {
        @Override
        public void validateForm(ExternalScriptEngineDefinitionImpl def, Errors errors)
        {
            // validate definition
            if (StringUtils.isEmpty(def.getName()))
                errors.rejectValue("name", ERROR_MSG, "The Name field cannot be empty");

            if (def.isExternal())
            {
                //
                // If the engine is remote then don't validate the exe and command line values
                //
                if (!def.isRemote())
                {
                    File rexe = new File(def.getExePath());
                    if (!rexe.exists())
                        errors.rejectValue("exePath", ERROR_MSG, "The program location: '" + def.getExePath() + "' does not exist");
                    if (rexe.isDirectory())
                        errors.rejectValue("exePath", ERROR_MSG, "Please specify the entire path to the program, not just the directory (e.g., 'c:/Program Files/R/R-2.7.1/bin/R.exe')");
                }
                else
                {
                    // see if we had any bind errors (currently only filled in from the remote path mapper)
                    if (def.getPathMap() != null )
                    {
                        ValidationException validationException = def.getPathMap().getValidationErrors();
                        if (validationException != null && validationException.hasErrors())
                        {
                            List<ValidationError> validationErrors = validationException.getErrors();
                            for (ValidationError v : validationErrors)
                            {
                                errors.rejectValue("pathMap", ERROR_MSG, v.getMessage());
                            }
                        }
                    }
                }

                SimpleFilter filter = new SimpleFilter();
                filter.addCondition(FieldKey.fromParts("name"), def.getName());
                if (def.getRowId() != null)
                    filter.addCondition(FieldKey.fromParts("rowid"), def.getRowId(), CompareType.NEQ);
                if (new TableSelector(CoreSchema.getInstance().getTableInfoReportEngines(), filter, null).exists())
                {
                    errors.rejectValue("Name", ERROR_MSG, "Unable to save duplicate engine name: " + def.getName());
                }
            }
        }

        @Override
        public ApiResponse execute(ExternalScriptEngineDefinitionImpl def, BindException errors) throws Exception
        {
            LabKeyScriptEngineManager svc = LabKeyScriptEngineManager.get();
            if (def.isDocker())
                def.saveDockerImageConfig(getUser());
            svc.saveDefinition(getUser(), def);

            // update default R engine
            if (def.getType() == ExternalScriptEngineDefinition.Type.R && def.isDefault())
            {
                List<ExternalScriptEngineDefinition> rDefs = svc.getEngineDefinitions(ExternalScriptEngineDefinition.Type.R);
                for (ExternalScriptEngineDefinition rDef : rDefs)
                {
                    if ((!rDef.getName().equals(def.getName())) && rDef.isDefault())
                    {
                        ExternalScriptEngineDefinitionImpl newDef = (ExternalScriptEngineDefinitionImpl) rDef;
                        newDef.setDefault(false);
                        newDef.updateConfiguration(); // update config json
                        svc.saveDefinition(getUser(), newDef);
                    }
                }
            }
            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresNoPermission
    @AllowedDuringUpgrade
    public class DismissWarningsAction extends MutatingApiAction
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            HttpSession session = getViewContext().getRequest().getSession(true);
            session.setAttribute(SESSION_WARNINGS_BANNER_KEY, false);
            return success();
        }
    }

    @RequiresNoPermission
    @AllowedDuringUpgrade
    public class DisplayWarningsAction extends MutatingApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            // Reset Session Attribute so warnings will display on next page load
            ViewContext context = getViewContext();
            HttpSession session = context.getRequest().getSession(true);
            session.setAttribute(SESSION_WARNINGS_BANNER_KEY, true);

            JSONObject json = new JSONObject();
            json.put("success", true);

            // Collect server-side warnings
            Warnings warnings = WarningService.get().getWarnings(context);

            if (!warnings.isEmpty())
            {
                // Send warnings content for optional client-side consumption
                json.put("warningsHtml", WarningService.get().getWarningsHtml(warnings, context).toString());
            }

            return new ApiSimpleResponse(json);
        }
    }

    @RequiresLogin
    public class WebSocketConnectionAction extends MutatingApiAction<WebSocketConnectionForm>
    {
        @Override
        public Object execute(WebSocketConnectionForm form, BindException errors)
        {
            WebSocketConnectionManager.getInstance().incrementCounter(form.isConnected());
            return success();
        }
    }

    public static class WebSocketConnectionForm
    {
        private boolean _connected;

        public boolean isConnected()
        {
            return _connected;
        }

        public void setConnected(boolean connected)
        {
            _connected = connected;
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ScriptEnginesDeleteAction extends MutatingApiAction<ExternalScriptEngineDefinitionImpl>
    {
        @Override
        public ApiResponse execute(ExternalScriptEngineDefinitionImpl def, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            LabKeyScriptEngineManager svc = LabKeyScriptEngineManager.get();
            ExternalScriptEngineDefinition savedDef = svc.getEngineDefinition(def.getRowId(), def.getType());
            if (savedDef != null)
            {
                svc.deleteDefinition(getUser(), savedDef);
                response.put("success", true);
            }
            else
                response.put("success", false);
            return response;
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        @Test
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            CoreController controller = new CoreController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user, false,
                controller.new ProjectsAction(),
                controller.new DownloadFileLinkAction(),
                controller.new GetExtContainerTreeAction(),
                controller.new GetExtSecurityContainerTreeAction(),
                controller.new GetExtMWBContainerTreeAction(),
                controller.new GetExtContainerAdminTreeAction(),
                controller.new GetFolderTypesAction(),
                controller.new SaveModulePropertiesAction(),
                controller.new GetContainerInfoAction(),
                controller.new GetRegisteredFolderWritersAction(),
                controller.new GetRegisteredFolderImportersAction()
            );

            // @RequiresPermission(InsertPermission.class)
            assertForInsertPermission(user,
                controller.new CreateContainerAction(),
                controller.new CreateWorkbookAction()
            );

            // @RequiresPermission(UpdatePermission.class)
            assertForUpdateOrDeletePermission(user,
                controller.new UpdateDescriptionAction(),
                controller.new UpdateTitleAction(),
                controller.new GetModulePropertiesAction()
            );

            // @RequiresPermission(DeletePermission.class)
            assertForUpdateOrDeletePermission(user,
                controller.new DeleteContainerAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                controller.new MoveContainerAction(),
                controller.new MoveWorkbooksAction(),
                controller.new GetContainerTreeRootInfoAction(),
                controller.new MoveWorkbookAction()
            );
        }
    }

    public class ManageQCStatesBean extends AbstractManageQCStatesBean
    {
        ManageQCStatesBean(ActionURL returnUrl)
        {
            super(returnUrl);
            _qcStateHandler = new CoreQCStateHandler();
            _manageAction = new ManageQCStatesAction();
            _deleteAction = DeleteQCStateAction.class;
            _noun = "assay";
            _dataNoun = "assay";
        }
    }

    public static class ManageQCStatesForm extends AbstractManageDataStatesForm
    {
        private Integer _defaultQCState;
        private boolean _requireCommentOnQCStateChange;

        public Integer getDefaultQCState()
        {
            return _defaultQCState;
        }

        public void setDefaultQCState(Integer defaultQCState)
        {
            _defaultQCState = defaultQCState;
        }

        public boolean isRequireCommentOnQCStateChange()
        {
            return _requireCommentOnQCStateChange;
        }

        public void setRequireCommentOnQCStateChange(boolean requireCommentOnQCStateChange)
        {
            _requireCommentOnQCStateChange = requireCommentOnQCStateChange;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ManageQCStatesAction extends AbstractManageQCStatesAction<ManageQCStatesForm>
    {
        public ManageQCStatesAction()
        {
            super(new CoreQCStateHandler(), ManageQCStatesForm.class);
        }

        @Override
        public boolean hasQcStateDefaultsPanel()
        {
            return true;
        }

        @Override
        public String getQcStateDefaultsPanel(Container container, DataStateHandler qcStateHandlerAbstract)
        {
            CoreQCStateHandler qcStateHandler = (CoreQCStateHandler)qcStateHandlerAbstract;

            StringBuilder panelHtml = new StringBuilder();
            panelHtml.append("  <table class=\"lk-fields-table\">");
            panelHtml.append("      <tr>");
            panelHtml.append("          <td colspan=\"2\">These settings allow different default QC states depending on data source.");
            panelHtml.append("              If set, all imported data without an explicit QC state will have the selected state automatically assigned.</td>");
            panelHtml.append("      </tr>");
            panelHtml.append("      <tr>");
            panelHtml.append("          <th align=\"right\" width=\"300px\">Default QC state:</th>");
            panelHtml.append(getQcStateHtml(container, qcStateHandler, "defaultQCState", qcStateHandler.getDefaultQCState(container)));
            panelHtml.append("      </tr>");
            panelHtml.append("  </table>");

            return panelHtml.toString();
        }

        @Override
        public boolean hasDataVisibilityPanel()
        {
            return false;
        }

        @Override
        public String getDataVisibilityPanel(Container container, DataStateHandler qcStateHandler)
        {
            throw new IllegalStateException("This action does not support a data visibility panel.");
        }

        @Override
        public boolean hasRequiresCommentPanel()
        {
            return true;
        }

        @Override
        public String getRequiresCommentPanel(Container container, DataStateHandler qcStateHandler)
        {
            StringBuilder panelHtml = new StringBuilder();
            panelHtml.append("  <table class=\"lk-fields-table\">");
            panelHtml.append("      <tr>");
            panelHtml.append("          <td colspan=\"2\">This setting determines whether a comment is required when updating an assay run QC state.");
            panelHtml.append("      </tr>");
            panelHtml.append("      <tr>");
            panelHtml.append("          <th align=\"right\" width=\"300px\">Require Comment on QC State Change:</th>");
            panelHtml.append("          <td>");
            panelHtml.append("              <select name=\"requireCommentOnQCStateChange\">");
            panelHtml.append("                  <option value=\"false\">No</option>");
            String selectedText = (qcStateHandler.isRequireCommentOnQCStateChange(container)) ? " selected" : "";
            panelHtml.append("                  <option value=\"true\"").append(selectedText).append(">Yes</option>");
            panelHtml.append("              </select>");
            panelHtml.append("          </td>");
            panelHtml.append("      </tr>");
            panelHtml.append("  </table>");

            return panelHtml.toString();
        }

        @Override
        public ModelAndView getView(ManageQCStatesForm manageQCStatesForm, boolean reshow, BindException errors)
        {
            // currently only assays support management of QC states (outside of study)
            if (AssayQCService.getProvider().supportsQC())
            {
                return new JspView<>("/org/labkey/api/qc/view/manageQCStates.jsp",
                        new ManageQCStatesBean(manageQCStatesForm.getReturnActionURL()), errors);
            }
            else
            {
                return new HtmlView("An Assay QC provider is not configured for this server.");
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("manageQC");
            root.addChild("Manage Assay QC States");
        }

        @Override
        public URLHelper getSuccessURL(ManageQCStatesForm manageQCStatesForm)
        {
            return getSuccessURL(manageQCStatesForm, ManageQCStatesAction.class, ProjectController.BeginAction.class);  // TODO: fix last class
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteQCStateAction extends AbstractDeleteDataStateAction
    {
        public DeleteQCStateAction()
        {
            super();
            _dataStateHandler = new CoreQCStateHandler();
        }

        @Override
        public DataStateHandler getDataStateHandler()
        {
            return _dataStateHandler;
        }

        @Override
        public ActionURL getSuccessURL(DeleteDataStateForm form)
        {
            ActionURL returnUrl = new ActionURL(ManageQCStatesAction.class, getContainer());
            if (form.getManageReturnUrl() != null)
                returnUrl.addParameter(ActionURL.Param.returnUrl, form.getManageReturnUrl());
            return returnUrl;
        }
    }

    public static class TransformWikiForm
    {
        private String _body;
        private String _fromFormat;
        private String _toFormat;

        public String getBody()
        {
            return _body;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setBody(String body)
        {
            _body = body;
        }

        public String getFromFormat()
        {
            return _fromFormat;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setFromFormat(String fromFormat)
        {
            _fromFormat = fromFormat;
        }

        public String getToFormat()
        {
            return _toFormat;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setToFormat(String toFormat)
        {
            _toFormat = toFormat;
        }
    }

    @SuppressWarnings("unused") // Called from JavaScript: discuss.js, wikiEdit.js
    @RequiresNoPermission
    public class TransformWikiAction extends MutatingApiAction<TransformWikiForm>
    {
        @Override
        public ApiResponse execute(TransformWikiForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            String newBody = form.getBody();

            if (StringUtils.equals(WikiRendererType.HTML.name(), form.getToFormat()))
            {
                WikiRendererType fromType = WikiRendererType.valueOf(form.getFromFormat());
                newBody = WikiRenderingService.get().getFormattedHtml(fromType, newBody).toString();
            }

            response.put("toFormat", form.getToFormat());
            response.put("fromFormat", form.getFromFormat());
            response.put("body", newBody);

            return response;
        }
    }

    @RequiresLogin
    public class IncrementClientSideMetricCountAction extends MutatingApiAction<ClientSideMetricForm>
    {
        @Override
        public void validateForm(ClientSideMetricForm form, Errors errors)
        {
            if (StringUtils.isEmpty(form.getFeatureArea()) || StringUtils.isEmpty(form.getMetricName()))
                errors.reject(ERROR_MSG, "Must provide both a featureArea and metricName.");
        }

        @Override
        public ApiResponse execute(ClientSideMetricForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            String featureArea = form.getFeatureArea();
            String metricName = form.getMetricName();
            response.put("featureArea", featureArea);
            response.put("metricName", metricName);
            response.put("count", SimpleMetricsService.get().increment(form.getModuleName(), featureArea, metricName));
            return response;
        }
    }

    public static class ClientSideMetricForm
    {
        private String _moduleName = CoreModule.CORE_MODULE_NAME;
        private String _featureArea;
        private String _metricName;

        public String getFeatureArea()
        {
            return _featureArea;
        }

        public void setFeatureArea(String featureArea)
        {
            _featureArea = featureArea;
        }

        public String getMetricName()
        {
            return _metricName;
        }

        public void setMetricName(String metricName)
        {
            _metricName = metricName;
        }

        public String getModuleName()
        {
            return _moduleName;
        }

        public void setModuleName(String moduleName)
        {
            _moduleName = moduleName;
        }
    }
}
