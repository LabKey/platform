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

package org.labkey.experiment.controllers.property;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.AbstractFileUploadAction;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerService;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.DomainKindDesign;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainTemplate;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.assay.model.GWTPropertyDescriptorMixin;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.ExcelFormatException;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SessionTempFileHolder;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.writer.PrintWriters;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;

public class PropertyController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(PropertyController.class);

    public static final String UNRECOGNIZED_FILE_TYPE_ERROR = "Unrecognized file type. Please upload a .xls, .xlsx, .tsv, .csv or .txt file";

    public PropertyController()
    {
        setActionResolver(_actionResolver);
    }

    static void configureObjectMapper(ObjectMapper om, @Nullable SimpleBeanPropertyFilter filter)
    {
        SimpleBeanPropertyFilter gwtDomainPropertiesFilter;
        if(null == filter)
        {
            gwtDomainPropertiesFilter = SimpleBeanPropertyFilter.serializeAll();
        }
        else
        {
            gwtDomainPropertiesFilter = filter;
        }

        FilterProvider gwtDomainFilterProvider = new SimpleFilterProvider()
                .addFilter("listDomainsActionFilter", gwtDomainPropertiesFilter);
        om.setFilterProvider(gwtDomainFilterProvider);
        om.addMixIn(GWTDomain.class, GWTDomainMixin.class);
        om.addMixIn(GWTPropertyDescriptor.class, GWTPropertyDescriptorMixin.class);
        om.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }

    @RequiresNoPermission //Real permissions will be enforced by the DomainKind
    public class EditDomainAction extends SimpleViewAction<DomainForm>
    {
        private Domain _domain;

        public ModelAndView getView(DomainForm form, BindException errors)
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

                // save the domain so that we ensure it exists before we try to edit it
                try (var ignored = SpringActionController.ignoreSqlUpdates())
                {
                    _domain.save(getUser());
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw UnexpectedException.wrap(e);
                }

                // re-fetch the domain so that we can redirect using the saved domainId
                _domain = PropertyService.get().getDomain(getContainer(), domainURI);
                ActionURL redirectURL = PageFlowUtil.urlProvider(ExperimentUrls.class).getDomainEditorURL(getContainer(), _domain);
                URLHelper returnURL = getViewContext().getActionURL().getReturnURL();
                if (returnURL != null)
                    redirectURL.addReturnURL(returnURL);
                throw new RedirectException(redirectURL);
            }
            else
            {
                if (!_domain.getDomainKind().canEditDefinition(getUser(), _domain))
                {
                    throw new UnauthorizedException();
                }
            }

            return ModuleHtmlView.get(ModuleLoader.getInstance().getModule("experiment"), "domainDesigner");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("propertyFields");
            _domain.getDomainKind().appendNavTrail(root, getContainer(), getUser());
            root.addChild("Edit Fields in " + _domain.getLabel());
            return root;
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class) //Real permissions will be enforced later on by the DomainKind
    public class CreateDomainAction extends MutatingApiAction<DomainApiForm>
    {
        //Keeping both request and response object mappers to avoid serialization/deserialization issues
        //as not sure if request object mapper is needed
        @Override
        protected ObjectMapper createRequestObjectMapper()
        {
            ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();
            configureObjectMapper(mapper, null);
            return mapper;
        }

        @Override
        protected ObjectMapper createResponseObjectMapper()
        {
            return this.createRequestObjectMapper();
        }

        public ApiResponse execute(DomainApiForm form, BindException errors) throws Exception
        {
            Map<String, Object> options = new HashMap<>();
            GWTDomain newDomain = form.getDomainDesign();
            Domain domain = null;
            List<Domain> domains = null;

            String kindName = form.getKind() == null ? form.getDomainKind() : form.getKind();
            String domainGroup = form.getDomainGroup();
            String domainName = form.getDomainName();

            if (domainGroup != null)
            {
                String moduleName = form.getModule();
                String domainTemplate = form.getDomainTemplate();

                boolean createDomain = form.isCreateDomain();
                boolean importData = form.isImportData();

                DomainTemplateGroup templateGroup;
                if (moduleName != null)
                {
                    Module module = ModuleLoader.getInstance().getModule(moduleName);
                    if (module == null)
                        throw new NotFoundException("Module '" + moduleName + "' for domain template group not found");
                    templateGroup = DomainTemplateGroup.get(module, domainGroup);
                }
                else
                    templateGroup = DomainTemplateGroup.get(getContainer(), domainGroup);

                if (templateGroup == null)
                    throw new NotFoundException("Domain template group '" + domainGroup + "' not found");

                if (domainTemplate != null)
                {
                    DomainTemplate template = templateGroup.getTemplate(domainTemplate, kindName, true);
                    if (template == null)
                        throw new NotFoundException("Domain template '" + domainTemplate + "' " + (kindName != null ? "of kind '" + kindName + "' " : "") + "not found in template group '" + domainGroup + "'");

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
                JSONObject jsOptions = form.getOptions();
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
                Map<String, Object> map = convertDomainToApiResponse(DomainUtil.getDomainDescriptor(getUser(), domain));
                resp.putAll(map);
            }
            else if (domains != null)
            {
                List<Map<String, Object>> resps = new ArrayList<>();
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

    /**
     * DO NOT USE. This action has been deprecated in 20.3 in favor of GetDomainKindAction.
     * Only here for backwards compatibility to resolve requests and redirect.
     * //TODO: Deprecate/update docs
     */
    @Deprecated
    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class GetDomainAction extends ReadOnlyApiAction<DomainApiForm>
    {
        @Override
        protected ObjectMapper createResponseObjectMapper()
        {
            ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();
            configureObjectMapper(mapper, null);
            return mapper;
        }

        public Object execute(DomainApiForm form, BindException errors)
        {
            String queryName = form.getQueryName();
            String schemaName = form.getSchemaName();
            Integer domainId = form.getDomainId();

            return getDomain(schemaName, queryName, domainId, getContainer(), getUser());
        }
    }

    /**
     * If DomainKind specific properties are available, then json response is of this format:
     *  {
     *      "domainKindName":
     *      "options": {}
     *      "domainDesign": {}
     *  }
     *  Otherwise, json response is of this format:
     *  {
     *      "domainDesign": {}
     *  }
     */
    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class GetDomainDetailsAction  extends ReadOnlyApiAction<DomainApiForm>
    {
        @Override
        protected ObjectMapper createResponseObjectMapper()
        {
            ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();
            configureObjectMapper(mapper, null);
            return mapper;
        }

        public Object execute(DomainApiForm form, BindException errors)
        {
            String queryName = form.getQueryName();
            String schemaName = form.getSchemaName();
            Integer domainId = form.getDomainId();

            GWTDomain gwtDomain = getDomain(schemaName, queryName, domainId, getContainer(), getUser());
            Domain domain = PropertyService.get().getDomain(getContainer(), gwtDomain.getDomainURI());

            if (null != domain && null != domain.getDomainKind())
            {
                DomainKindDesign domainKindDesign = new DomainKindDesign();
                domainKindDesign.setDomainDesign(gwtDomain);
                domainKindDesign.setDomainKindName(domain.getDomainKind().getKindName());
                domainKindDesign.setOptions(domain.getDomainKind().getDomainKindProperties(gwtDomain, getContainer(), getUser()));
                return domainKindDesign;
            }
            return gwtDomain;
        }
    }

    /**
     * If DomainKind specific properties are available, then json response is of this format:
     *  {
     *      "domainKindName":
     *      "options": {}
     *      "domainDesign": {}
     *  }
     *  Otherwise, json response is of this format:
     *  {
     *      "domainDesign": {}
     *  }
     */
    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class GetDomainDetailsByKindAction  extends ReadOnlyApiAction<DomainApiForm>
    {
        @Override
        protected ObjectMapper createResponseObjectMapper()
        {
            ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();
            configureObjectMapper(mapper, null);
            return mapper;
        }

        public Object execute(DomainApiForm form, BindException errors)
        {
            String queryName = form.getQueryName();
            String schemaName = form.getSchemaName();
            Integer domainId = form.getDomainId();

            GWTDomain gwtDomain = getDomain(schemaName, queryName, domainId, getContainer(), getUser());
            Domain domain = PropertyService.get().getDomain(getContainer(), gwtDomain.getDomainURI());

            if (null != domain && null != domain.getDomainKind())
            {
                DomainKindDesign domainKindDesign = new DomainKindDesign();
                domainKindDesign.setDomainDesign(gwtDomain);
                domainKindDesign.setDomainKindName(domain.getDomainKind().getKindName());
                domainKindDesign.setOptions(domain.getDomainKind().getDomainKindProperties(gwtDomain, getContainer(), getUser()));
                return domainKindDesign;
            }
            return gwtDomain;
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class) //Real permissions will be enforced later on by the DomainKind
    public class SaveDomainAction extends MutatingApiAction<DomainApiForm>
    {
        //Keeping both request and response object mappers to avoid serialization/deserialization issues
        //as not sure if request object mapper is needed
        @Override
        protected ObjectMapper createRequestObjectMapper()
        {
            ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();
            configureObjectMapper(mapper, null);
            return mapper;
        }

        @Override
        protected ObjectMapper createResponseObjectMapper()
        {
            return this.createRequestObjectMapper();
        }

        public Object execute(DomainApiForm form, BindException errors)
        {
            GWTDomain newDomain = form.getDomainDesign();
            if (newDomain.getDomainId() == -1 || newDomain.getDomainURI() == null)
                throw new IllegalArgumentException("Domain id and URI are required");

            GWTDomain originalDomain = getDomain(form.getSchemaName(), form.getQueryName(), form.getDomainId(), getContainer(), getUser());

            boolean includeWarnings = form.includeWarnings();
            boolean hasErrors = false;

            ValidationException updateErrors = updateDomain(originalDomain, newDomain, form.getOptions(), getContainer(), getUser(), includeWarnings);

            for (ValidationError ve : updateErrors.getErrors())
            {
                if (ve.getSeverity().equals(ValidationException.SEVERITY.ERROR))
                    hasErrors = true;
            }

            if (hasErrors || includeWarnings)
                updateErrors.setBindExceptionErrors(errors, ERROR_MSG, includeWarnings);

            Domain domain = PropertyService.get().getDomain(getContainer(), newDomain.getDomainURI());
            ApiSimpleResponse resp = new ApiSimpleResponse();
            if (domain != null)
            {
                Map<String, Object> map = convertDomainToApiResponse(DomainUtil.getDomainDescriptor(getUser(), domain));
                resp.putAll(map);
            }

            resp.put("success", true);
            return resp;
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class DeleteDomainAction extends MutatingApiAction<DomainApiForm>
    {
        public Object execute(DomainApiForm form, BindException errors)
        {
            String queryName = form.getQueryName();
            String schemaName = form.getSchemaName();

            deleteDomain(schemaName, queryName, getContainer(), getUser());
            return success("Domain deleted");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DomainApiForm {
        private String kind;
        private String domainKind;
        private String domainName;
        private String module;
        private String domainGroup;
        private String domainTemplate;
        private boolean createDomain = true;
        private boolean importData = true;
        private GWTDomain domainDesign;
        private JSONObject options;
        private String containerPath;
        private String schemaName;
        private String queryName;
        private Integer domainId;
        private boolean includeWarnings;

        public Integer getDomainId()
        {
            return domainId;
        }

        public void setDomainId(Integer domainId)
        {
            this.domainId = domainId;
        }

        public String getSchemaName()
        {
            return schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            this.schemaName = schemaName;
        }

        public String getQueryName()
        {
            return queryName;
        }

        public void setQueryName(String queryName)
        {
            this.queryName = queryName;
        }

        public String getKind()
        {
            return kind;
        }

        public void setKind(String kind)
        {
            this.kind = kind;
        }

        public String getDomainKind()
        {
            return domainKind;
        }

        public void setDomainKind(String domainKind)
        {
            this.domainKind = domainKind;
        }

        public String getDomainName()
        {
            return domainName;
        }

        public void setDomainName(String domainName)
        {
            this.domainName = domainName;
        }

        public String getModule()
        {
            return module;
        }

        public void setModule(String module)
        {
            this.module = module;
        }

        public String getDomainGroup()
        {
            return domainGroup;
        }

        public void setDomainGroup(String domainGroup)
        {
            this.domainGroup = domainGroup;
        }

        public String getDomainTemplate()
        {
            return domainTemplate;
        }

        public void setDomainTemplate(String domainTemplate)
        {
            this.domainTemplate = domainTemplate;
        }

        public boolean isCreateDomain()
        {
            return createDomain;
        }

        public void setCreateDomain(boolean createDomain)
        {
            this.createDomain = createDomain;
        }

        public boolean isImportData()
        {
            return importData;
        }

        public void setImportData(boolean importData)
        {
            this.importData = importData;
        }

        public GWTDomain getDomainDesign()
        {
            return domainDesign;
        }

        public void setDomainDesign(GWTDomain domainDesign)
        {
            this.domainDesign = domainDesign;
        }

        public JSONObject getOptions()
        {
            return options;
        }

        public void setOptions(JSONObject options)
        {
            this.options = options;
        }

        public String getContainerPath()
        {
            return containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            this.containerPath = containerPath;
        }

        public boolean includeWarnings()
        {
            return includeWarnings;
        }

        public void setIncludeWarnings(boolean includeWarnings)
        {
            this.includeWarnings = includeWarnings;
        }
    }

    /**
     * Infer the fields from the uploaded file and return the array of fields in a format that can
     * be used in the CreateDomainAction.
     */
    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    @ActionNames("inferDomain, getFilePreview")
    public class InferDomainAction extends ReadOnlyApiAction<InferDomainForm>
    {
        @Override
        public void validateForm(InferDomainForm form, Errors errors)
        {
            // expect to either have a file posted or a param with the file id/path
            if (!(getViewContext().getRequest() instanceof MultipartHttpServletRequest) && form.getFile() == null)
                errors.reject(ERROR_REQUIRED, "Either a file is required to be posted or the id/path to a file that exists on the server must be supplied.");
        }

        //Keeping both request and response object mappers to avoid serialization/deserialization issues
        //as not sure if request object mapper is needed
        @Override
        protected ObjectMapper createRequestObjectMapper()
        {
            ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();
            configureObjectMapper(mapper, null);
            return mapper;
        }

        @Override
        protected ObjectMapper createResponseObjectMapper()
        {
            return this.createRequestObjectMapper();
        }

        @Override
        public Object execute(InferDomainForm form, BindException errors) throws Exception
        {
            Map<String, MultipartFile> fileMap = getFileMap();
            File file = form.getFile() != null ? (File) ConvertUtils.convert(form.getFile().toString(), File.class) : null;
            DataLoader loader = null;

            PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(getContainer());
            // Allow preview only for files under the pipeline root.
            if (file != null && file.exists() && pipelineRoot != null && pipelineRoot.isUnderRoot(file) )
            {
                loader = DataLoader.get().createLoader(file, null, true, null, null);
            }
            else if (fileMap.size() == 1)
            {
                Optional<MultipartFile> opt = fileMap.values().stream().findAny();
                MultipartFile postedFile = opt.orElse(null);
                if (postedFile != null)
                    loader = DataLoader.get().createLoader(postedFile, true, null, null);
            }
            else
            {
                throw new IllegalArgumentException("Unable to find a posted file or the file for the posted id/path.");
            }

            return getInferDomainResponse(loader, form.getNumLinesToInclude());
        }

        private ApiSimpleResponse getInferDomainResponse(DataLoader loader, Integer numLinesToInclude) throws IOException
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            List<GWTPropertyDescriptor> fields = new ArrayList<>();

            if (loader != null)
            {
                ColumnDescriptor[] columns = loader.getColumns();
                for (ColumnDescriptor col : columns)
                {
                    GWTPropertyDescriptor prop = new GWTPropertyDescriptor(col.getColumnName(), col.getRangeURI());
                    prop.setContainer(getContainer().getId());
                    prop.setMvEnabled(col.isMvEnabled());

                    fields.add(prop);
                }

                if (numLinesToInclude != null)
                {
                    response.put("data", loader.getFirstNLines(numLinesToInclude));
                }
            }

            response.put("fields", fields);
            return response;
        }
    }

    public static class InferDomainForm
    {
        // TODO should -1 allow you to get all data?
        private Integer _numLinesToInclude;
        private Object _file;

        public Integer getNumLinesToInclude()
        {
            return _numLinesToInclude;
        }

        public void setNumLinesToInclude(Integer numLinesToInclude)
        {
            _numLinesToInclude = numLinesToInclude;
        }

        public Object getFile()
        {
            return _file;
        }

        public void setFile(Object file)
        {
            _file = file;
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

        @Override
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

        @Override
        public String getResponse(FileUploadForm form, Map<String, Pair<File, String>> files) throws UploadException
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
        @Override
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
    private static ValidationException updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update,
                                            @Nullable JSONObject options, Container container, User user, boolean includeWarnings)
    {
        DomainKind kind = PropertyService.get().getDomainKind(original.getDomainURI());
        if (kind == null)
            throw new IllegalArgumentException("No domain kind matches URI '" + original.getDomainURI() + "'.");

        Domain domain = PropertyService.get().getDomain(container, original.getDomainURI());
        if (domain == null)
            throw new NotFoundException("No domain matches URI '" + original.getDomainURI() + "'.");

        if (!kind.canEditDefinition(user, domain))
            throw new UnauthorizedException("You don't have permission to edit this domain.");

        if (JSONObject.class == kind.getTypeClass())
        {
            return kind.updateDomain(original, update, options, container, user, includeWarnings);
        }
        else
        {
            Object domainKindProps = null;
            if (null != options)
            {
                ObjectMapper mapper = new ObjectMapper();
                domainKindProps = mapper.convertValue(options, kind.getTypeClass());
            }
            return kind.updateDomain(original, update, domainKindProps, container, user, includeWarnings);
        }
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
    private static GWTDomain getDomain(String schemaName, String queryName, Integer domainId, @NotNull Container container, @NotNull User user) throws NotFoundException
    {
        if ((schemaName == null || queryName == null) && domainId == null)
        {
            throw new IllegalArgumentException("domainId or schemaName and queryName are required" );
        }

        GWTDomain domain;
        if (domainId != null)
        {
            Domain dom = PropertyService.get().getDomain(domainId);
            if (dom == null)
                throw new NotFoundException("Could not find domain for " + domainId);

            if (!container.equals(dom.getContainer())) // issue 38502
                throw new NotFoundException("Could not find domain for " + domainId + " in container '" + container.getPath() + "'.");

            domain = DomainUtil.getDomainDescriptor(user, dom);
        }
        else
        {
            String domainURI = PropertyService.get().getDomainURI(schemaName, queryName, container, user);
            domain = DomainUtil.getDomainDescriptor(user, domainURI, container);

            if (domain == null)
                throw new NotFoundException("Could not find domain for schemaName=" + schemaName + ", queryName=" + queryName);
        }

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

    private static Map<String, Object> convertDomainToApiResponse(@NotNull GWTDomain domain)
    {
        ObjectMapper om = new ObjectMapper();
        configureObjectMapper(om, null);
        try
        {
            return om.convertValue(domain, Map.class);
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public static String convertDomainToJson(@NotNull GWTDomain domain)
    {
        ObjectMapper om = new ObjectMapper();
        configureObjectMapper(om, null);
        try
        {
            return om.writeValueAsString(domain);
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
        public ModelAndView getView(DomainForm form, BindException errors)
        {
            // NOTE could use just domainId or domainURI, but SaveDomain expects schema and query
            String schema = form.getSchemaName();
            String query = form.getQueryName();
            if (StringUtils.isBlank(schema) || StringUtils.isBlank(query))
                throw new IllegalArgumentException("schemaName and queryName required");

            GWTDomain gwt = getDomain(schema, query, null, getContainer(), getUser());
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
            return new JspView<>("/org/labkey/experiment/controllers/property/templateUpdate.jsp", model);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    @Marshal(Marshaller.Jackson)
    public class ListDomainsAction extends ReadOnlyApiAction<ContainerDomainForm>
    {
        boolean includeFields;
        boolean includeProjectAndShared;

        @Override
        protected ObjectMapper createRequestObjectMapper()
        {
            ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();
            configureObjectMapper(mapper, null);
            return mapper;
        }

        @Override
        protected ObjectMapper createResponseObjectMapper()
        {
            ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();

            if (!includeFields)
            {
               configureObjectMapper(mapper, SimpleBeanPropertyFilter.serializeAllExcept("fields","indices"));
            }
            else
            {
                configureObjectMapper(mapper, null);
            }
            return mapper;
        }

        @Override
        public Object execute(ContainerDomainForm containerDomainForm, BindException errors)
        {
            includeFields = containerDomainForm.isIncludeFields();
            includeProjectAndShared = containerDomainForm.isIncludeProjectAndShared();

            Container c = containerDomainForm.getContainerPath() == null ? getContainer():
                    ContainerService.get().getForPath(containerDomainForm.getContainerPath());

            List<GWTDomain> domains = listDomains(c, getUser(), containerDomainForm, includeProjectAndShared);

            return success(listDomains(c, getUser(), containerDomainForm, includeProjectAndShared));
        }
    }

    private List<GWTDomain> listDomains(Container c, User user, ContainerDomainForm form, boolean includeProjectAndShared)
    {
        Stream<? extends Domain> domains;
        if (form.getDomainKinds() != null && !form.getDomainKinds().isEmpty())
        {
            domains = PropertyService.get().getDomainsStream(c, user, form.getDomainKinds(), includeProjectAndShared);
        }
        else
        {
            domains = PropertyService.get().getDomainsStream(c, user, emptySet(), includeProjectAndShared);
        }
        return domains.map(d -> DomainUtil.getDomainDescriptor(getUser(), d)).collect(Collectors.toList());
    }



    public static class ContainerDomainForm
    {
        boolean includeFields = false;
        boolean includeProjectAndShared = false;
        String containerPath;
        Set<String> domainKinds;
        String apiVersion;

        public String getApiVersion()
        {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion)
        {
            this.apiVersion = apiVersion;
        }

        public boolean isIncludeFields()
        {
            return includeFields;
        }

        public void setIncludeFields(boolean includeFields)
        {
            this.includeFields = includeFields;
        }

        public String getContainerPath()
        {
            return containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            this.containerPath = containerPath;
        }

        public Set<String> getDomainKinds()
        {
            return domainKinds;
        }

        public void setDomainKinds(Set<String> domainKinds)
        {
            this.domainKinds = domainKinds;
        }

        public boolean isIncludeProjectAndShared()
        {
            return includeProjectAndShared;
        }

        public void setIncludeProjectAndShared(boolean includeProjectAndShared)
        {
            this.includeProjectAndShared = includeProjectAndShared;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    @Marshal(Marshaller.Jackson)
    public class GetPropertiesAction extends ReadOnlyApiAction<GetPropertiesForm>
    {
        @Override
        public Object execute(GetPropertiesForm form, BindException errors) throws Exception
        {
            Stream<PropertyDescriptor> properties;
            if (form.getPropertyIds() != null && !form.getPropertyIds().isEmpty())
            {
                properties = form.getPropertyIds().stream()
                        .filter(Objects::nonNull)
                        .map(OntologyManager::getPropertyDescriptor)
                        .filter(Objects::nonNull)
                        .filter(pd -> getContainer().equals(pd.getContainer()));
            }
            else if (form.getPropertyURIs() != null && !form.getPropertyURIs().isEmpty())
            {
                properties = form.getPropertyURIs().stream()
                        .filter(Objects::nonNull)
                        .map(uri -> OntologyManager.getPropertyDescriptor(uri, getContainer()))
                        .filter(Objects::nonNull);
            }
            else
            {
                throw new ApiUsageException("Expected propertyIds or propertyURIs");
            }

            List<GWTPropertyDescriptor> gwtProps = properties
                    .map(DomainUtil::getPropertyDescriptor)
                    .collect(Collectors.toList());

            return success(gwtProps);
        }
    }

    public static class GetPropertiesForm
    {
        private List<Integer> propertyIds;
        private List<String> propertyURIs;

        public List<Integer> getPropertyIds()
        {
            return propertyIds;
        }

        public void setPropertyIds(List<Integer> propertyIds)
        {
            this.propertyIds = propertyIds;
        }

        public List<String> getPropertyURIs()
        {
            return propertyURIs;
        }

        public void setPropertyURIs(List<String> propertyURIs)
        {
            this.propertyURIs = propertyURIs;
        }

    }


    public static class TestCase extends Assert
    {
        @Test
        public void testGetDomains() throws ValidationException
        {
            Container c = JunitUtil.getTestContainer();
            User user = TestContext.get().getUser();
            GWTDomain mockDomain = new GWTDomain();
            String domainName = "TestVocabularyDomain" + GUID.makeGUID();
            mockDomain.setName(domainName);
            mockDomain.setDescription("This is a mock vocabulary");

            List<GWTPropertyDescriptor> gwtProps = new ArrayList<>();

            GWTPropertyDescriptor prop1 = new GWTPropertyDescriptor();
            prop1.setRangeURI("int");
            prop1.setName("testIntField");

            GWTPropertyDescriptor prop2 = new GWTPropertyDescriptor();
            prop2.setRangeURI("string");
            prop2.setName("testStringField");

            gwtProps.add(prop1);
            gwtProps.add(prop2);

            mockDomain.setFields(gwtProps);

            Domain createdDomain = DomainUtil.createDomain("Vocabulary", mockDomain, null, c, user, domainName, null);

            Set<String> domainKinds = new HashSet<>();
            domainKinds.add("Vocabulary");

            List<? extends Domain> addedDomains = PropertyService.get().getDomains(c, user, domainKinds, false).
                    stream().filter(d ->  d.getDomainKind().getKindName().equals("Vocabulary")).collect(Collectors.toList());

            assertEquals("Vocabulary Domain Not found.", createdDomain.getName(), mockDomain.getName());
        }
    }
}
