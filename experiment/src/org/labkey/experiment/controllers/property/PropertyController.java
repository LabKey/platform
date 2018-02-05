/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.experiment.controllers.property;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.AbstractFileUploadAction;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainTemplate;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.ExcelFormatException;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.JdbcUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SessionTempFileHolder;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.PrintWriters;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class PropertyController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(PropertyController.class);

    public static final String UNRECOGNIZED_FILE_TYPE_ERROR = "Unrecognized file type. Please upload a .xls, .xlsx, .tsv, .csv or .txt file";

    public PropertyController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresNoPermission
    public class EditDomainAction extends SimpleViewAction<DomainForm>
    {
        private Domain _domain;

        public ModelAndView getView(DomainForm form, BindException errors) throws Exception
        {
            // Try to get existing domain from form.
            _domain = form.getDomain();

            if (_domain == null)
            {
                if (!form.isCreateOrEdit())
                {
                    throw new NotFoundException("Domain not found");
                }

                // Domain wasn't found, let's create a new one.
                String domainURI = form.getDomainURI();
                if (domainURI == null && form.getSchemaName() != null && form.getQueryName() != null)
                {
                    domainURI = PropertyService.get().getDomainURI(form.getSchemaName(), form.getQueryName(), getContainer(), getUser());

                    if (domainURI == null)
                    {
                        throw new NotFoundException("The query '" + form.getQueryName() + "' in the '" + form.getSchemaName() + "' schema does not support editable fields.");
                    }
                }

                if (domainURI == null)
                {
                    throw new NotFoundException("Can't create domain without DomainURI or schemaName/queryName pair.");
                }

                DomainKind kind = PropertyService.get().getDomainKind(domainURI);
                if (kind == null)
                {
                    throw new NotFoundException("Domain kind not found");
                }

                if (!kind.canCreateDefinition(getUser(), getContainer()))
                {
                    throw new UnauthorizedException();
                }
                Lsid domainLSID = new Lsid(domainURI);
                _domain = PropertyService.get().createDomain(getContainer(), domainURI, form.getQueryName() != null ? form.getQueryName() : domainLSID.getObjectId());
            }
            else
            {
                if (!_domain.getDomainKind().canEditDefinition(getUser(), _domain))
                {
                    throw new UnauthorizedException();
                }
            }

            Map<String, String> props = new HashMap<>();
            ActionURL defaultReturnURL = _domain.getDomainKind().urlShowData(_domain, getViewContext());
            ActionURL returnURL = form.getReturnActionURL(defaultReturnURL);
            props.put("typeURI", _domain.getTypeURI());
            if (returnURL != null)
            {
                props.put(ActionURL.Param.returnUrl.name(), returnURL.toString());
            }
            props.put("allowFileLinkProperties", String.valueOf(form.getAllowFileLinkProperties()));
            props.put("allowAttachmentProperties", String.valueOf(form.getAllowAttachmentProperties()));
            props.put("showDefaultValueSettings", String.valueOf(form.isShowDefaultValueSettings()));
            props.put("instructions", _domain.getDomainKind().getDomainEditorInstructions());
            if (null != form.getSchemaName())
                props.put("schemaName", form.getSchemaName());
            if (null != form.getQueryName())
                props.put("queryName", form.getQueryName());

            return new GWTView("org.labkey.experiment.property.Designer", props);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("propertyFields");
            _domain.getDomainKind().appendNavTrail(root, getContainer(), getUser());
            root.addChild("Edit Fields in " + _domain.getLabel());
            return root;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class PropertyServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new PropertyServiceImpl(getViewContext());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CreateDomainAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm getForm, BindException errors) throws Exception
        {
            JSONObject jsonObj = getForm.getJsonObject();
            Map<String, Object> options = new HashMap<>();
            GWTDomain newDomain;
            Domain domain = null;
            List<Domain> domains = null;

            String kindName = jsonObj.optString("kind", jsonObj.getString("domainKind"));
            String domainGroup = jsonObj.getString("domainGroup");
            String domainName = jsonObj.optString("domainName", null);

            if (domainGroup != null)
            {
                String module = jsonObj.optString("module", null);
                String domainTemplate = jsonObj.optString("domainTemplate", null);

                boolean createDomain = jsonObj.optBoolean("createDomain", true);
                boolean importData = jsonObj.optBoolean("importData", true);

                DomainTemplateGroup templateGroup;
                if (module != null)
                    templateGroup = DomainTemplateGroup.get(ModuleLoader.getInstance().getModule(module), domainGroup);
                else
                    templateGroup = DomainTemplateGroup.get(getContainer(), domainGroup);

                if (templateGroup == null)
                    throw new NotFoundException("Domain template group '" + domainGroup + "' not found");

                if (domainTemplate != null)
                {
                    DomainTemplate template = templateGroup.getTemplate(domainTemplate, kindName, true);
                    if (template == null)
                        throw new NotFoundException("Domain template '" + domainTemplate + "' " + (kindName != null ? "of kind '" + kindName + "'" : "") + "not found in template group '" + domainGroup + "'");

                    if (template.hasErrors())
                    {
                        errors.reject(ERROR_MSG, "Domain template '" + domainTemplate + "' has errors: " + StringUtils.join(template.getErrors(), "\n"));
                        return null;
                    }

                    // CONSIDER: Include imported row count in response
                    domain = template.createAndImport(getContainer(), getUser(), domainName, createDomain, importData);
                }
                else
                {
                    if (templateGroup.hasErrors())
                    {
                        errors.reject(ERROR_MSG, "Domain template group '" + domainGroup + "' has errors: " + StringUtils.join(templateGroup.getErrors(), "\n"));
                        return null;
                    }

                    domains = templateGroup.createAndImport(getContainer(), getUser(), /*TODO: allow specifying a domain name for each template?, */ createDomain, importData);
                }
            }
            else if (kindName != null)
            {
                newDomain = ExperimentService.get().convertJsonToDomain(jsonObj);
                JSONObject jsOptions = jsonObj.optJSONObject("options");
                if (jsOptions == null)
                    jsOptions = new JSONObject();

                // Convert JSONObject to a Map, unpacking JSONArray as we go.
                // XXX: There must be utility for this somewhere?
                for (String key : jsOptions.keySet())
                {
                    Object value = jsOptions.get(key);
                    if (value instanceof JSONArray)
                        options.put(key, ((JSONArray) value).toArray());
                    else
                        options.put(key, value);
                }

                domain = DomainUtil.createDomain(kindName, newDomain, options, getContainer(), getUser(), domainName, null);
            }
            else
            {
                throw new ApiUsageException("Domain template or domain design required");
            }


            ApiSimpleResponse resp = new ApiSimpleResponse();
            if (domain != null)
            {
                resp = convertDomainToApiResponse(DomainUtil.getDomainDescriptor(getUser(), domain));
            }
            else if (domains != null)
            {
                List<ApiSimpleResponse> resps = new ArrayList<>();
                for (Domain d : domains)
                {
                    resps.add(convertDomainToApiResponse(DomainUtil.getDomainDescriptor(getUser(), d)));
                }
                resp.put("domains", resps);
            }
            else
            {
                // Domain will be null if we requested to import data only
                // CONSIDER: Include imported row count in response
                resp = new ApiSimpleResponse();
            }

            resp.put("success", true);
            return resp;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetDomainAction extends ApiAction<GetForm>
    {
        public ApiResponse execute(GetForm form, BindException errors) throws Exception
        {
            String queryName = form.getQueryName();
            String schemaName = form.getSchemaName();

            return convertDomainToApiResponse(getDomain(schemaName, queryName, getContainer(), getUser()));
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SaveDomainAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm getForm, BindException errors) throws Exception
        {
            JSONObject jsonObj = getForm.getJsonObject();
            String schema = jsonObj.getString("schemaName");
            String query = jsonObj.getString("queryName");

            GWTDomain newDomain = ExperimentService.get().convertJsonToDomain(jsonObj);
            if (newDomain.getDomainId() == -1 || newDomain.getDomainURI() == null)
                throw new IllegalArgumentException("Domain id and URI are required");

            GWTDomain originalDomain = getDomain(schema, query, getContainer(), getUser());

            List<String> updateErrors = updateDomain(originalDomain, newDomain, getContainer(), getUser());
            for (String msg : updateErrors)
                errors.reject(ERROR_MSG, msg);

            return new ApiSimpleResponse();
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class DeleteDomainAction extends ApiAction<GetForm>
    {
        public Object execute(GetForm form, BindException errors) throws Exception
        {
            String queryName = form.getQueryName();
            String schemaName = form.getSchemaName();

            deleteDomain(schemaName, queryName, getContainer(), getUser());
            return success("Domain deleted");
        }
    }

    /**
     * Stores a file sent by the client in a temp file and puts it in the session
     * for later use by gwt services
     */
    @RequiresPermission(AdminPermission.class)
    public class UploadFileForInferencingAction extends AbstractFileUploadAction<AbstractFileUploadAction.FileUploadForm>
    {
        private static final String SESSION_ATTR_NAME = "org.labkey.domain.tempFile";

        protected File getTargetFile(String filename) throws IOException
        {
            int dotIndex = filename.lastIndexOf(".");
            if (dotIndex < 0)
            {
                throw new UploadException(UNRECOGNIZED_FILE_TYPE_ERROR, HttpServletResponse.SC_BAD_REQUEST);
            }
            String suffix = filename.substring(dotIndex).toLowerCase();
            String prefix = filename.substring(0, dotIndex);
            if (prefix.length() < 3)
            {
                // File.createTempFile() requires that the prefix be at least three characters long
                prefix = "prefix-" + prefix;  
            }
            File tempFile = File.createTempFile(prefix, suffix);
            tempFile.deleteOnExit();
            return tempFile;
        }

        protected String getResponse(Map<String, Pair<File, String>> files, FileUploadForm form) throws UploadException
        {
            if (files.isEmpty())
            {
                throw new UploadException("No file(s) uploaded, or the uploaded file was empty", 400);
            }
            if (files.size() > 1)
            {
                StringBuilder message = new StringBuilder();
                String separator = "";
                for (Pair<File, String> fileStringPair : files.values())
                {
                    message.append(separator);
                    separator = ", ";
                    message.append(fileStringPair.getValue());
                }
                throw new UploadException("Only one file is supported, but " + files.size() + " were uploaded: " + message, 400);
            }
            // Store the file in the session, and delete it when the session expires
            HttpSession session = getViewContext().getSession();

            // If we've already got one in the session, delete the temp file
            SessionTempFileHolder oldFileHolder =
                (SessionTempFileHolder)session.getAttribute(SESSION_ATTR_NAME);
            if (oldFileHolder != null)
                oldFileHolder.getFile().delete();

            session.setAttribute(SESSION_ATTR_NAME, new SessionTempFileHolder(files.values().iterator().next().getKey()));

            return "Success";
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class InferPropertiesAction extends ExportAction<InferForm>
    {
        public void export(InferForm inferForm, HttpServletResponse response, BindException errors) throws Exception
        {
            response.reset();
            response.setContentType("text/html");

            try (OutputStream out = response.getOutputStream(); PrintWriter writer = PrintWriters.getPrintWriter(out))
            {
                String data;

                try
                {
                    if ("file".equals(inferForm.getSource()))
                    {
                        data = getDataFromFile(writer);
                        if (data == null)
                            return; // We'll already have written out the error
                    }
                    else // tsv
                    {
                        TabLoader dataLoader = new TabLoader(inferForm.getTsvText(), true);
                        data = getData(dataLoader);
                    }

                    writer.write("Success:");
                    data = data.replaceAll("\r|\n", "<br>");
                    data = data.replaceAll("\t", "<hr>");
                    writer.write(data);
                    writer.flush();
                }
                catch (Exception e)
                {
                    error(writer, e.getMessage());
                }
            }
        }

        // Note: caller must close writer
        private String getDataFromFile(Writer writer) throws IOException
        {
            HttpServletRequest basicRequest = getViewContext().getRequest();

            if (! (basicRequest instanceof MultipartHttpServletRequest))
            {
                error(writer, "No file uploaded");
                return null;
            }

            MultipartHttpServletRequest request = (MultipartHttpServletRequest)basicRequest;

            //noinspection unchecked
            Iterator<String> nameIterator = request.getFileNames();
            String formElementName = nameIterator.next();
            MultipartFile file = request.getFile(formElementName);
            String filename = file.getOriginalFilename();
            int dotIndex = filename.lastIndexOf(".");
            if (dotIndex < 0)
            {
                error(writer, UNRECOGNIZED_FILE_TYPE_ERROR);
                return null;
            }
            String suffix = filename.substring(dotIndex).toLowerCase();
            String prefix = filename.substring(0, dotIndex);

            File tempFile = File.createTempFile(prefix, suffix);
            tempFile.deleteOnExit();

            try
            {
                try (InputStream input = file.getInputStream(); OutputStream output = new FileOutputStream(tempFile))
                {
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = input.read(buffer)) > 0)
                        output.write(buffer, 0, len);

                    output.flush();
                }

                try (DataLoader dataLoader = getDataLoader(tempFile))
                {
                    if (dataLoader == null)
                    {
                        error(writer, UNRECOGNIZED_FILE_TYPE_ERROR);
                        return null;
                    }

                    return getData(dataLoader);
                }
            }
            catch (ExcelFormatException efe)
            {
                error(writer, efe.getMessage());
                return null;
            }
            catch (IOException ioe)
            {
                ExceptionUtil.logExceptionToMothership(request, ioe);
                error(writer, ioe.getMessage());
                return null;
            }
            finally
            {
                tempFile.delete();
            }
        }

        private String getData(DataLoader dataLoader) throws IOException
        {
            ColumnDescriptor[] columns = dataLoader.getColumns();
            StringBuilder sb = new StringBuilder();

            sb.append("Property").append("\t");
            sb.append("RangeURI").append("\t\n");

            for (ColumnDescriptor column : columns)
            {
                sb.append(getStringValue(column.name)).append("\t");
                sb.append(column.getRangeURI()).append("\t\n");
            }
            sb.setLength(sb.length() - 1); // remove last return char
            return sb.toString();
        }

        private void error(Writer writer, String message) throws IOException
        {
            writer.write(message);
            writer.flush();
        }

        private String getStringValue(Object o)
        {
            if (o == null)
                return "";
            return o.toString();
        }

        private DataLoader getDataLoader(File tempFile) throws IOException
        {
            DataLoaderFactory factory = DataLoader.get().findFactory(tempFile, null);
            if (factory == null)
                return null;

            return factory.createLoader(tempFile, true);
        }
    }

    public static class InferForm
    {
        private String source;
        private String tsvText;

        public String getSource()
        {
            return source;
        }

        public void setSource(String source)
        {
            this.source = source;
        }

        public String getTsvText()
        {
            return tsvText;
        }

        public void setTsvText(String tsvText)
        {
            this.tsvText = tsvText;
        }
    }

    /** @return Errors encountered during the save attempt */
    @NotNull
    private static List<String> updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update, Container container, User user)
    {
        DomainKind kind = PropertyService.get().getDomainKind(original.getDomainURI());
        if (kind == null)
            throw new IllegalArgumentException("No domain kind matches URI '" + original.getDomainURI() + "'");

        return kind.updateDomain(original, update, container, user);
    }

    private static void deleteDomain(String schemaName, String queryName, Container container, User user)
    {
        String domainURI = PropertyService.get().getDomainURI(schemaName, queryName, container, user);
        if (domainURI == null)
            throw new NotFoundException("Could not find domain for schemaName=" + schemaName + ", queryName=" + queryName);

        DomainKind kind = PropertyService.get().getDomainKind(domainURI);
        if (kind == null)
            throw new IllegalArgumentException("No domain kind matches URI '" + domainURI + "'");

        Domain d = PropertyService.get().getDomain(container, domainURI);
        if (d == null)
            throw new NotFoundException("Could not find domain for " + domainURI);

        if (!kind.canDeleteDefinition(user, d))
            throw new UnauthorizedException("You don't have permission to delete this domain");

        kind.deleteDomain(user, d);
    }

    @NotNull
    private static GWTDomain getDomain(String schemaName, String queryName, Container container, User user) throws NotFoundException
    {
        String domainURI = PropertyService.get().getDomainURI(schemaName, queryName, container, user);
        GWTDomain domain = DomainUtil.getDomainDescriptor(user, domainURI, container);

        if (domain == null)
            throw new NotFoundException("Could not find domain for " + domainURI);

        return domain;
    }

    public static class GetForm
    {
        private String schemaName;
        private String queryName;

        public String getQueryName() {return queryName;}
        public void setQueryName(String queryName) {this.queryName = queryName;}

        public String getSchemaName() {return schemaName;}
        public void setSchemaName(String schemaName) {this.schemaName = schemaName;}
    }

    class PropertyServiceImpl extends DomainEditorServiceBase implements org.labkey.experiment.property.client.PropertyService
    {
        public PropertyServiceImpl(ViewContext context)
        {
            super(context);
        }

        public List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update, boolean create)
        {
            try
            {
                if (create)
                {
                    String domainURI = update.getDomainURI();
                    if (domainURI == null)
                        throw new IllegalArgumentException("domainURI required to create domain");

                    DomainKind kind = PropertyService.get().getDomainKind(domainURI);
                    if (kind == null)
                        throw new IllegalArgumentException("domain kind not found for domainURI");

                    Domain d = PropertyService.get().createDomain(getContainer(), domainURI, update.getName());
                    d.save(getUser());

                    // this _create_ code path is a bit odd, why don't we create the domain before we start editing it?
                    // refetch the domain to get the new timestamp can remove reselect if/when Table.insert reselects timestamp columns
                    d = PropertyService.get().getDomain(getContainer(), domainURI);
                    if (null != d)
                        orig.set_Ts(JdbcUtil.rowVersionToString(d.get_Ts()));
                }

                return super.updateDomainDescriptor(orig, update);
            }
            catch (ChangePropertyDescriptorException e)
            {
                throw UnexpectedException.wrap(e);
            }
        }

        @Override
        public GWTDomain getDomainDescriptor(String typeURI)
        {
            GWTDomain domain = super.getDomainDescriptor(typeURI);
            if (domain == null)
                return null;
            
            domain.setDefaultValueOptions(new DefaultValueType[]
                    { DefaultValueType.FIXED_EDITABLE, DefaultValueType.LAST_ENTERED }, DefaultValueType.FIXED_EDITABLE);
            return domain;
        }
    }

    @SuppressWarnings("unchecked")
    private static ApiSimpleResponse convertDomainToApiResponse(@NotNull GWTDomain domain)
    {
        ApiSimpleResponse response = new ApiSimpleResponse();
        try
        {
            response.putBean(domain, "domainId", "name", "domainURI", "description");
            response.putBeanList("fields", domain.getFields());
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }

        return response;
    }

    public static String convertDomainToJson(@NotNull GWTDomain domain)
    {
        ApiSimpleResponse response = new ApiSimpleResponse();
        try
        {
            response.putBean(domain, "domainId", "name", "domainURI", "description");
            response.putBeanList("fields", domain.getFields());
            return JSONObject.valueToString(response.getProperties());
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public static class CompareWithTemplateModel
    {
        public String queryName;
        public String schemaName;
        public GWTDomain domain;
        public GWTDomain template;
        public TemplateInfo info;
    }

    @RequiresPermission(AdminPermission.class)
    public class CompareWithTemplateAction extends SimpleViewAction<DomainForm>
    {
        @Override
        public ModelAndView getView(DomainForm form, BindException errors) throws Exception
        {
            // NOTE could use just domainId or domainURI, but SaveDomain expects schema and query
            String schema = form.getSchemaName();
            String query = form.getQueryName();
            if (StringUtils.isBlank(schema) || StringUtils.isBlank(query))
                throw new IllegalArgumentException("schemaName and queryName required");

            GWTDomain gwt = getDomain(schema, query, getContainer(), getUser());
            Domain domain = form.getDomain();

            if (null == domain)
                throw new NotFoundException("Domain not found");
            if (!domain.getContainer().hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException();

            DomainKind kind = domain.getDomainKind();
            if (kind == null)
                throw new NotFoundException("Domain kind not found for domain '" + domain.getName() + "': " + domain.getTypeURI());

            String kindName = kind.getKindName();
            TemplateInfo info = domain.getTemplateInfo();

            DomainTemplate template = DomainTemplate.findTemplate(info, kindName);
            GWTDomain gwtFromTemplate = null;
            if (template != null)
                gwtFromTemplate = template.getDomain();

            CompareWithTemplateModel model = new CompareWithTemplateModel();
            model.schemaName = form.getSchemaName();
            model.queryName = form.getQueryName();
            model.domain = gwt;
            model.template = gwtFromTemplate;
            model.info = info;
            return new JspView<>(PropertyController.class, "templateUpdate.jsp", model);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }
}
