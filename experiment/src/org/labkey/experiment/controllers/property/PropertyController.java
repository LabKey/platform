/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.AbstractFileUploadAction;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.GWTServiceAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.ExcelFormatException;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SessionTempFileHolder;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
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
import java.io.OutputStreamWriter;
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


    @RequiresPermissionClass(ReadPermission.class)
    public class PropertyServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new PropertyServiceImpl(getViewContext());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class CreateDomainAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm getForm, BindException errors) throws Exception
        {
            JSONObject jsonObj = getForm.getJsonObject();

            String kindName = jsonObj.getString("kind");
            GWTDomain newDomain = convertJsonToDomain(jsonObj);
            JSONObject jsOptions = jsonObj.optJSONObject("options");
            if (jsOptions == null)
                jsOptions = new JSONObject();

            // Convert JSONObject to a Map, unpacking JSONArray as we go.
            // XXX: There must be utility for this somewhere?
            Map<String, Object> options = new HashMap<>();
            for (String key : jsOptions.keySet())
            {
                Object value = jsOptions.get(key);
                if (value instanceof JSONArray)
                    options.put(key, ((JSONArray) value).toArray());
                else
                    options.put(key, value);
            }

            createDomain(kindName, newDomain, options, getContainer(), getUser());

            return new ApiSimpleResponse();
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetDomainAction extends ApiAction<GetForm>
    {
        public ApiResponse execute(GetForm form, BindException errors) throws Exception
        {
            String queryName = form.getQueryName();
            String schemaName = form.getSchemaName();

            return convertDomainToApiResponse(getDomain(schemaName, queryName, getContainer(), getUser()));
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SaveDomainAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm getForm, BindException errors) throws Exception
        {
            JSONObject jsonObj = getForm.getJsonObject();
            String schema = jsonObj.getString("schemaName");
            String query = jsonObj.getString("queryName");

            GWTDomain newDomain = convertJsonToDomain(jsonObj);
            if (newDomain.getDomainId() == -1 || newDomain.getDomainURI() == null)
                throw new IllegalArgumentException("Domain id and URI are required");

            GWTDomain originalDomain = getDomain(schema, query, getContainer(), getUser());

            List<String> updateErrors = updateDomain(originalDomain, newDomain, getContainer(), getUser());
            for (String msg : updateErrors)
                errors.reject(ERROR_MSG, msg);

            return new ApiSimpleResponse();
        }
    }

    /**
     * Stores a file sent by the client in a temp file and puts it in the session
     * for later use by gwt services
     */
    @RequiresPermissionClass(AdminPermission.class)
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

    @RequiresPermissionClass(ReadPermission.class)
    public class InferPropertiesAction extends ExportAction<InferForm>
    {
        public void export(InferForm inferForm, HttpServletResponse response, BindException errors) throws Exception
        {
            response.reset();
            response.setContentType("text/html");

            try (OutputStream out = response.getOutputStream(); OutputStreamWriter writer = new OutputStreamWriter(out))
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
                    data = data.replaceAll("\t", "<tab>");
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

    private static Domain createDomain(String kindName, GWTDomain domain, Map<String, Object> arguments, Container container, User user)
    {
        DomainKind kind = PropertyService.get().getDomainKindByName(kindName);
        if (kind == null)
            throw new IllegalArgumentException("No domain kind matches name '" + kindName + "'");

        if (!kind.canCreateDefinition(user, container))
            throw new UnauthorizedException("You don't have permission to create a new domain");

        Domain created = kind.createDomain(domain, arguments, container, user);
        if (created == null)
            throw new RuntimeException("Failed to created domain");
        return created;
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
    private static ApiResponse convertDomainToApiResponse(@NotNull GWTDomain domain)
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

    @SuppressWarnings("unchecked")
    private static GWTDomain convertJsonToDomain(JSONObject obj) throws JSONException
    {
        GWTDomain domain = new GWTDomain();
        JSONObject jsonDomain = obj.getJSONObject("domainDesign");

        domain.setDomainId(jsonDomain.optInt("domainId", -1));

        domain.setName(jsonDomain.getString("name"));
        domain.setDomainURI(jsonDomain.optString("domainURI", null));
        domain.setContainer(jsonDomain.getString("container"));

        // Description can be null
        domain.setDescription(jsonDomain.optString("description", null));

        JSONArray jsonFields = jsonDomain.getJSONArray("fields");
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        for (int i=0; i<jsonFields.length(); i++)
        {
            JSONObject jsonProp = jsonFields.getJSONObject(i);
            GWTPropertyDescriptor prop = convertJsonToPropertyDescriptor(jsonProp);
            props.add(prop);
        }

        domain.setFields(props);

        return domain;
    }

    private static GWTPropertyDescriptor convertJsonToPropertyDescriptor(JSONObject obj) throws JSONException
    {
        GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
        prop.setName(obj.getString("name"));
        prop.setRangeURI(obj.getString("rangeURI"));

        // Other fields can be null
        Integer propId = (Integer)obj.get("propertyId");
        if (propId != null)
            prop.setPropertyId(propId.intValue());

        Boolean required = (Boolean)obj.get("required");
        if (required != null)
            prop.setRequired(required.booleanValue());

        prop.setPropertyURI((String)obj.get("propertyURI"));
        prop.setOntologyURI((String)obj.get("ontologyURI"));
        prop.setDescription((String)obj.get("description"));
        prop.setConceptURI((String)obj.get("conceptURI"));
        prop.setLabel((String)obj.get("label"));
        prop.setSearchTerms((String)obj.get("searchTerms"));
        prop.setSemanticType((String)obj.get("semanticType"));
        prop.setFormat((String)obj.get("format"));
        prop.setLookupContainer((String)obj.get("lookupContainer"));
        prop.setLookupSchema((String)obj.get("lookupSchema"));
        prop.setLookupQuery((String)obj.get("lookupQuery"));
        prop.setImportAliases((String)obj.get("importAliases"));
        prop.setURL((String)obj.get("url"));

        return prop;
    }
}
