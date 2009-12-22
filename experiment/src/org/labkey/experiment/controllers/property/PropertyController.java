/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
import org.labkey.api.action.*;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.SessionTempFileHolder;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.*;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.util.*;

public class PropertyController extends SpringActionController
{
    private static DefaultActionResolver _actionResolver = new DefaultActionResolver(PropertyController.class);

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
            _domain = form.getDomain();

            if (null == _domain)
            {
                HttpView.throwNotFound();
            }

            if (null == _domain.getDomainKind() || !_domain.getDomainKind().canEditDefinition(getUser(), _domain))
            {
                HttpView.throwUnauthorized();
            }

            Map<String, String> props = new HashMap<String, String>();
            ActionURL returnURL = _domain.getDomainKind().urlEditDefinition(_domain);
            props.put("typeURI", _domain.getTypeURI());
            props.put("returnURL", returnURL.toString());
            props.put("allowFileLinkProperties", String.valueOf(form.getAllowFileLinkProperties()));
            props.put("allowAttachmentProperties", String.valueOf(form.getAllowAttachmentProperties()));
            props.put("showDefaultValueSettings", String.valueOf(form.isShowDefaultValueSettings()));

            return new GWTView("org.labkey.experiment.property.Designer", props);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Edit Fields in " + _domain.getLabel());
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

            GWTDomain originalDomain = getDomain(schema, query, getContainer(), getUser());

            DomainUtil.updateDomainDescriptor(originalDomain, newDomain, getContainer(), getUser());

            return new ApiSimpleResponse();
        }
    }

    /**
     * Stores a file sent by the client in a temp file and puts it in the session
     * for later use by gwt services
     */
    @RequiresPermissionClass(AdminPermission.class)
    public class UploadFileForInferencingAction extends AbstractFileUploadAction
    {
        private static final String SESSION_ATTR_NAME = "org.labkey.domain.tempFile";

        protected File getTargetFile(String filename) throws IOException
        {
            int dotIndex = filename.lastIndexOf(".");
            if (dotIndex < 0)
            {
                throw new UploadException("Unrecognized file type. Please upload a .xls, .tsv, .csv or .txt file", HttpServletResponse.SC_BAD_REQUEST);
            }
            String suffix = filename.substring(dotIndex + 1).toLowerCase();
            String prefix = filename.substring(0, dotIndex);

            File tempFile = File.createTempFile(prefix, suffix);
            tempFile.deleteOnExit();
            return tempFile;
        }

        protected String handleFile(File file, String originalName)
        {
            // Store the file in the session, and delete it when the session expires
            HttpSession session = getViewContext().getSession();

            // If we've already got one in the session, delete the temp file
            SessionTempFileHolder oldFileHolder =
                (SessionTempFileHolder)session.getAttribute(SESSION_ATTR_NAME);
            if (oldFileHolder != null)
                oldFileHolder.getFile().delete();

            session.setAttribute(SESSION_ATTR_NAME, new SessionTempFileHolder(file));

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

            OutputStream out = response.getOutputStream();

            OutputStreamWriter writer = new OutputStreamWriter(out);

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
                writer.close();
            }
            catch (Exception e)
            {
                error(writer, e.getMessage());
            }
        }

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
                error(writer, "Unrecognized file type. Please upload a .xls, .tsv, .csv or .txt file");
                return null;
            }
            String suffix = filename.substring(dotIndex + 1).toLowerCase();
            String prefix = filename.substring(0, dotIndex);

            File tempFile = File.createTempFile(prefix, suffix);
            InputStream input = file.getInputStream();
            OutputStream output = new FileOutputStream(tempFile);
            DataLoader dataLoader = null;
            try
            {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = input.read(buffer)) > 0)
                    output.write(buffer, 0, len);

                output.flush();
                output.close();
                input.close();

                dataLoader = getDataLoader(tempFile, suffix);
                if (dataLoader == null)
                {
                    error(writer, "Unrecognized file type. Please upload a .xls, .tsv, .csv or .txt file");
                    return null;
                }

                return getData(dataLoader);
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
                if (dataLoader != null)
                    dataLoader.close();
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
            writer.close();
        }

        private String getStringValue(Object o)
        {
            if (o == null)
                return "";
            return o.toString();
        }

        private DataLoader getDataLoader(File tempFile, String suffix) throws IOException
        {
            DataLoader dataLoader = null;
            if (suffix.equals("xls"))
            {
                dataLoader = new ExcelLoader(tempFile, true);
            }
            else if (suffix.equals("tsv") || suffix.equals("txt"))
            {
                dataLoader = new TabLoader(tempFile, true);
            }
            else if (suffix.equals("csv"))
            {
                TabLoader loader = new TabLoader(tempFile, true);
                loader.parseAsCSV();
                dataLoader = loader;
            }
            if (dataLoader == null) // unknown document type
                return null;

            return dataLoader;
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

    @NotNull
    private static GWTDomain getDomain(String schemaName, String queryName, Container container, User user)
    {
        UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
        if (schema == null)
            throw new IllegalArgumentException("Schema '" + schemaName + "' does not exist.");

        String domainURI = schema.getDomainURI(queryName);
        if (domainURI == null)
            throw new UnsupportedOperationException(queryName + " in " + schemaName + " does not support reading domain information");

        GWTDomain domain = DomainUtil.getDomainDescriptor(user, domainURI, container);

        if (domain == null)
            throw new RuntimeException("Could not find domain for " + domainURI);

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

        public List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update)
        {
            try
            {
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

        domain.setDomainId(jsonDomain.getInt("domainId"));

        domain.setName(jsonDomain.getString("name"));
        domain.setDomainURI(jsonDomain.getString("domainURI"));

        // Description can be null
        domain.setDescription((String)jsonDomain.get("description"));

        JSONArray jsonFields = jsonDomain.getJSONArray("fields");
        List<GWTPropertyDescriptor> props = new ArrayList<GWTPropertyDescriptor>();
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
