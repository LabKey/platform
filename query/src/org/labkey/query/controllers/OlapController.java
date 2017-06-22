/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.query.controllers;

import mondrian.olap.Annotated;
import mondrian.olap.Annotation;
import mondrian.olap.MondrianException;
import mondrian.olap.MondrianServer;
import mondrian.xmla.impl.MondrianXmlaServlet;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.app.SinglePageAppUrls;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.PropertyStore;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.module.Module;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryParseExceptionUnresolvedField;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.AdminReadPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.DataspaceContainerFilter;
import org.labkey.api.util.Compress;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UpdateView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.template.PageConfig;
import org.labkey.query.olap.BitSetQueryImpl;
import org.labkey.query.olap.CustomOlapSchemaDescriptor;
import org.labkey.query.olap.MdxQueryImpl;
import org.labkey.query.olap.Olap4Js;
import org.labkey.query.olap.OlapDef;
import org.labkey.query.olap.OlapSchemaDescriptor;
import org.labkey.query.olap.QubeQuery;
import org.labkey.query.olap.ServerManager;
import org.labkey.query.olap.metadata.CachedCube;
import org.labkey.query.olap.rolap.RolapCubeDef;
import org.labkey.query.olap.rolap.RolapReader;
import org.labkey.query.persist.QueryManager;
import org.olap4j.CellSet;
import org.olap4j.OlapConnection;
import org.olap4j.OlapException;
import org.olap4j.OlapStatement;
import org.olap4j.OlapWrapper;
import org.olap4j.metadata.Cube;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * User: matthew
 * Date: 10/30/13
 * Time: 11:12 AM
 *
 * APIs for querying olap (mondrian) cubes
 *
 *
 * TODO consider whether to re-enable server side cache
 *
 */
public class OlapController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(OlapController.class);

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(OlapController.class);

    public OlapController()
    {
        setActionResolver(_actionResolver);
    }


    public static class OlapForm
    {
        private String configId;
        private String schemaName;
        private String cubeName;

        public String getConfigId()
        {
            return configId;
        }

        public void setConfigId(String configId)
        {
            this.configId = configId;
        }

        public String getSchemaName()
        {
            return schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            this.schemaName = schemaName;
        }

        public String getCubeName()
        {
            return cubeName;
        }

        public void setCubeName(String cubeName)
        {
            this.cubeName = cubeName;
        }
    }


    public static class CubeForm extends OlapForm
    {
        private boolean includeMembers = true;
        private String[] memberExclusionFields = null;
        private String contextName;

        public boolean isIncludeMembers()
        {
            return includeMembers;
        }

        public void setIncludeMembers(boolean includeMembers)
        {
            this.includeMembers = includeMembers;
        }

        public String[] getMemberExclusionFields()
        {
            return memberExclusionFields;
        }

        public void setMemberExclusionFields(String[] memberExclusionFields)
        {
            this.memberExclusionFields = memberExclusionFields;
        }

        public String getContextName()
        {
            return contextName;
        }

        public void setContextName(String contextName)
        {
            this.contextName = contextName;
        }
    }



    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public class GetCubeDefinitionAction extends ApiAction<CubeForm>
    {
        @Override
        public void validateForm(CubeForm form, Errors errors)
        {
            if (StringUtils.isEmpty(form.getConfigId()))
                errors.reject(ERROR_REQUIRED, "ConfigId must be provided to retrieve a cube definition.");
            if (StringUtils.isEmpty(form.getSchemaName()))
                errors.reject(ERROR_REQUIRED, "Schema name must be provided to retrieve a cube definition.");
            if (StringUtils.isEmpty(form.getCubeName()))
                errors.reject(ERROR_REQUIRED, "Cube name must be provided to retrieve a cube definition.");
        }

        @Override
        public ApiResponse execute(CubeForm form, BindException errors) throws Exception
        {
            Cube cube = null;
            RolapCubeDef rolap = null;

            try
            {
                cube = getCube(form, errors);
            }
            catch (OlapException|MondrianException x)
            {
                rethrowOlapException(x);
            }

            String schemaName = getAnnotation(cube,"SchemaName");
            if (null != schemaName)
            {
                UserSchema schema = (UserSchema)DefaultSchema.get(getUser(), getContainer()).getSchema(schemaName);
                if (null == schema)
                    throw new ConfigurationException("Schema from olap configuration file not found : " + schemaName);
                schema.checkCanReadSchemaOlap();
            }

            if (errors.hasErrors())
                return null;
            if (null == cube)
            {
                errors.reject(ERROR_MSG, "Cube not found: " + form.getCubeName());
                return null;
            }

            OlapSchemaDescriptor d = ServerManager.getDescriptor(getContainer(), form.getConfigId());

            if (null == d)
            {
                errors.reject(ERROR_MSG, "Cube descriptor not found: " + form.getCubeName());
                return null;
            }

            if (d.usesRolap())
            {
                rolap = d.getRolapCubeDefinitionByName(cube.getName());
            }

            Map<String, Object> context = null;
            if (StringUtils.isNotBlank(form.getContextName()))
            {
                context = getSinglePageAppContext(getContainer(), form.getContextName());
            }

            // Write out a response in this format:
            // {
            //   cube: { ... },
            //   context: {
            //     defaults: { },
            //     values: { }
            //   }
            // }
            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("application/json");
            Writer writer = response.getWriter();
            writer.write("{");
            writer.write("\"cube\":");
            Collection<String> memberExclusionFields = null;
            if (form.getMemberExclusionFields() != null)
            {
                memberExclusionFields = Arrays.asList(form.getMemberExclusionFields());
            }
            Olap4Js.convertCube(cube, rolap, form.isIncludeMembers(), true, memberExclusionFields, writer);

            if (context != null)
            {
                writer.write(",");

                writer.write("\"context\": ");
                writer.write(JSONObject.valueToString(context));
            }

            writer.write("}");

            return null;
        }
    }


    public static class CustomOlapDescriptorForm extends BeanViewForm<OlapDef>
    {
        public CustomOlapDescriptorForm()
        {
            super(OlapDef.class, QueryManager.get().getTableInfoOlapDef());
        }

        public void validate(Errors errors)
        {
            OlapDef d = getBean();

            // Validate the module is installed
            try
            {
                Module m = d.lookupModule();
            }
            catch (NotFoundException e)
            {
                errors.rejectValue("module", ERROR_MSG, e.getMessage());
            }

            try
            {
                // Instantiating the RolapReader will validate the definition and throw IllegalArgumentException
                RolapReader rr = new RolapReader(new StringReader(d.getDefinition()));
            }
            catch (IllegalArgumentException | IOException e)
            {
                errors.rejectValue("definition", ERROR_MSG, e.getMessage());
            }
        }
    }

    protected abstract class InsertUpdateDefinitionAction extends FormViewAction<CustomOlapDescriptorForm>
    {
        @Override
        public void validateCommand(CustomOlapDescriptorForm form, Errors errors)
        {
            form.validate(errors);
        }

        protected abstract DataView createView(CustomOlapDescriptorForm form, Errors errors);

        @Override
        public ModelAndView getView(CustomOlapDescriptorForm form, boolean reshow, BindException errors) throws Exception
        {
            DataView view = createView(form, errors);
            //view.setInitialValues();

            // The hidden rowId parameter in the update form is good enough
            ActionURL insertUpdateURL = getViewContext().getActionURL().clone().deleteParameter("rowId");
            view.getDataRegion().setFormActionUrl(insertUpdateURL);

            ActionButton submitButton = new ActionButton("Submit", insertUpdateURL);
            submitButton.setActionType(ActionButton.Action.POST);

            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);
            bb.add(submitButton);

            bb.add(new ActionButton("Cancel", form.getReturnActionURL() != null ? form.getReturnActionURL() : new ActionURL(TestBrowserAction.class, getContainer())));

            view.getDataRegion().setButtonBar(bb);

            return view;
        }

        protected abstract boolean doAction(CustomOlapDescriptorForm form, Errors errors) throws ServletException, SQLException;

        @Override
        public boolean handlePost(CustomOlapDescriptorForm form, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                return false;

            if (!doAction(form, errors))
                return false;

            // Clear the cached descriptors in this container
            CustomOlapSchemaDescriptor d = new CustomOlapSchemaDescriptor(form.getBean());
            ServerManager.olapSchemaDescriptorChanged(d);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(CustomOlapDescriptorForm form)
        {
            return form.getReturnURLHelper() != null ? form.getReturnActionURL() : new ActionURL(TestBrowserAction.class, getContainer());
        }

    }

    @RequiresPermission(AdminPermission.class)
    public class CreateDefinitionAction extends InsertUpdateDefinitionAction
    {
        @Override
        protected DataView createView(CustomOlapDescriptorForm form, Errors errors)
        {
            return new InsertView(form, (BindException)errors);
        }

        @Override
        protected boolean doAction(CustomOlapDescriptorForm form, Errors errors) throws ServletException, SQLException
        {
            try
            {
                form.doInsert();
            }
            catch (RuntimeSQLException e)
            {
                if (e.isConstraintException())
                {
                    errors.reject(ERROR_MSG, "A cube by that name is already defined in this folder");
                    return false;
                }

                throw e;
            }

            return true;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root
                    .addChild("OLAP Browser", new ActionURL(TestBrowserAction.class, getContainer()))
                    .addChild("Create Custom OLAP Definition");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class EditDefinitionAction extends InsertUpdateDefinitionAction
    {
        @Override
        protected DataView createView(CustomOlapDescriptorForm form, Errors errors)
        {
            return new UpdateView(form, (BindException)errors);
        }

        @Override
        protected boolean doAction(CustomOlapDescriptorForm form, Errors errors) throws ServletException, SQLException
        {
            form.doUpdate();
            return true;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root
                    .addChild("OLAP Browser", new ActionURL(TestBrowserAction.class, getContainer()))
                    .addChild("Update Custom OLAP Definition");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteDefinitionAction extends ConfirmAction<CustomOlapDescriptorForm>
    {
        @Override
        public void validateCommand(CustomOlapDescriptorForm form, Errors errors)
        {
            try
            {
                form.refreshFromDb();
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            OlapDef def = form.getBean();
            if (def == null)
                throw new NotFoundException("Custom olap definition not found");

            if (!getContainer().equals(def.lookupContainer()))
                throw new IllegalArgumentException("Incorrect container");
        }

        @Override
        public ModelAndView getConfirmView(CustomOlapDescriptorForm form, BindException errors) throws Exception
        {
            return new HtmlView("Are you sure you want do delete the custom olap definition '" + PageFlowUtil.filter(form.getBean().getName()) + "'?");
        }

        @Override
        public boolean handlePost(CustomOlapDescriptorForm form, BindException errors) throws Exception
        {
            form.doDelete();

            // Clear the cached descriptors in this container
            ServerManager.cubeDataChanged(getContainer());

            return true;
        }

        @NotNull
        @Override
        public URLHelper getSuccessURL(CustomOlapDescriptorForm form)
        {
            return form.getReturnURLHelper() != null ?
                    form.getReturnURLHelper() :
                    new ActionURL(TestBrowserAction.class, getContainer());
        }
    }

    public static class ExecuteMdxForm extends OlapForm
    {
        String _query = null;

        public String getQuery()
        {
            return _query;
        }

        public void setQuery(String query)
        {
            _query = query;
        }
    }



    /*
    // TODO Can't enable this action for non-site admins until we can check
    // AllowMDX correctly, also note we can't support cubes that require container based filters to enforce permissions
    */
    @RequiresSiteAdmin
    @Action(ActionType.SelectData.class)
    public class ExecuteMdxAction extends ApiAction<ExecuteMdxForm>
    {
        @Override
        public void validateForm(ExecuteMdxForm form, Errors errors)
        {
            if (StringUtils.isEmpty(form.getConfigId()))
                errors.reject(ERROR_REQUIRED, "configId");

            if (!"GET".equals(getViewContext().getRequest().getMethod()))
            {
                String sql = StringUtils.trimToNull(form.getQuery());
                if (null == sql)
                    errors.reject(ERROR_REQUIRED, "query");
            }
            super.validateForm(form, errors);
        }


        @Override
        public ApiResponse execute(ExecuteMdxForm form, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                return null;

            OlapSchemaDescriptor sd = ServerManager.getDescriptor(getContainer(), form.getConfigId());
            if (null == sd)
            {
                errors.reject(ERROR_MSG, "olap cube config not found: " + form.getConfigId());
                return null;
            }

            String sql = StringUtils.trimToNull(form.getQuery());

            try
            {
                ViewContext ctx = getViewContext();
                HttpServletResponse response = ctx.getResponse();
                String key = ctx.getContainer().getId() + ":" + sql;

                byte[] bytes = new MDXCacheLoader().load(key,form);

                response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);
                response.setHeader("Content-Encoding", "gzip");
                response.getOutputStream().write(bytes);
                return null;
            }
            catch (RuntimeException x)
            {
                Logger.getLogger(OlapController.class).error("mondrian error", x);
                Throwable t = x;
                while (null != t.getCause() && t != t.getCause())
                    t = t.getCause();
                errors.reject(SpringActionController.ERROR_MSG,StringUtils.defaultString(t.getMessage(),t.toString()));
                return null;
            }
            catch (Error err)
            {
                errors.reject(err.getMessage());
                return null;
            }
        }
    }



    class MDXCacheLoader implements CacheLoader<String,byte[]>
    {
        @Override
        public byte[] load(String key, @Nullable Object argument)
        {
            ExecuteMdxForm form = (ExecuteMdxForm)argument;
            OlapController controller = OlapController.this;

            OlapStatement stmt = null;
            CellSet cs = null;
            try
            {
                OlapSchemaDescriptor sd = ServerManager.getDescriptor(getContainer(), form.getConfigId());
                OlapConnection conn = controller.getConnection(sd);
                stmt = conn.createStatement();
                String query = form.getQuery();
                QueryProfiler.getInstance().ensureListenerEnvironment();
                OlapController._log.debug("\nSTART executeOlapQuery: --------------------------    --------------------------    --------------------------\n" + query);
                long ms = System.currentTimeMillis();
                cs = stmt.executeOlapQuery(query);
                long d = System.currentTimeMillis() - ms;
                QueryProfiler.getInstance().track(null, "-- MDX\n" + query + sd.getQueryTag(), null, d, null, true, QueryLogging.emptyQueryLogging());
                OlapController._log.debug("\nEND executeOlapQuery: " + DateUtil.formatDuration(d) + " --------------------------    --------------------------    --------------------------\n");

                StringWriter sw = new StringWriter();
                Olap4Js.convertCellSet(cs, sw);
                return Compress.compressGzip(sw.toString());
            }
            catch (RuntimeException x)
            {
                throw x;
            }
            catch (Exception x)
            {
                throw new RuntimeException(x);
            }
            finally
            {
                ResultSetUtil.close(cs);
                ResultSetUtil.close(stmt);
            }
        }
    }



    public static class JsonQueryForm extends OlapForm implements CustomApiForm
    {
        JSONObject json;

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            if (props instanceof JSONObject)
                json = (JSONObject)props;
            else
                json = new JSONObject(props);

            // TODO do regular binding for schemaName, cubeName, etc.
            //ApiAction.JsonPropertyValues values = new ApiAction.JsonPropertyValues(json);
            //BaseViewAction.defaultBindParameters(this, "json", values);

            if (null != json.get("schemaName"))
                setSchemaName(String.valueOf(json.get("schemaName" )));
            if (null != json.get("configId"))
                setConfigId(String.valueOf(json.get("configId" )));
            if (null != json.get("cubeName"))
                setCubeName(String.valueOf(json.get("cubeName" )));
        }
    }


    /**
     * NOT PART OF OFFICIAL CLIENT API
     * the particulars of the JSON format may change, and is very tied to the dataspace implementation
     */
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class JsonQueryAction extends ApiAction<JsonQueryForm>
    {
        @Override
        public ApiResponse execute(JsonQueryForm form, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                return null;

            Cube cube = getCube(form, errors);
            if (errors.hasErrors())
                return null;

            JSONObject q = (JSONObject)form.json.get("query");
            if (null == q)
            {
                errors.reject(ERROR_MSG, "query not specified");
                return null;
            }

            OlapSchemaDescriptor sd = ServerManager.getDescriptor(getContainer(), form.getConfigId());
            if (null == sd)
            {
                errors.reject(ERROR_MSG, "olap cube config not found: " + form.getConfigId());
                return null;
            }

            ContainerFilter cf = null;
            String schemaName = getAnnotation(cube,"SchemaName");
            if (null != schemaName)
            {
                UserSchema schema = (UserSchema)DefaultSchema.get(getUser(), getContainer()).getSchema(schemaName);
                if (null == schema)
                    throw new ConfigurationException("Schema from olap configuration file not found : " + schemaName);
                schema.checkCanReadSchemaOlap();
                schema.checkCanExecuteMDX();
                cf = schema.getOlapContainerFilter();
            }

            // does not override schema.checkCanExecuteMDX()
            String allowMDX = getAnnotation(cube, "AllowMDX");
            if (null != allowMDX && Boolean.FALSE == ConvertUtils.convert(allowMDX, Boolean.class))
            {
                errors.reject(ERROR_MSG, "this cube does not allow mdx queries: " + cube.getName());
                return null;
            }

            QubeQuery qquery = new QubeQuery(cube);
            qquery.fromJson(q, errors);
            // TODO
            // if (null != cf)
            //    mdx.setContainerFilter(cf);
            if (errors.hasErrors())
                return null;

            String mdx =  new MdxQueryImpl(qquery, errors).generateMDX();
            if (errors.hasErrors())
                return null;

            ExecuteMdxForm mdxForm = new ExecuteMdxForm();
            mdxForm.setQuery(mdx);
            mdxForm.setConfigId(form.getConfigId());
            mdxForm.setSchemaName(form.getSchemaName());
            mdxForm.setCubeName(form.getCubeName());

            ExecuteMdxAction action = new ExecuteMdxAction();
            action.setViewContext(getViewContext());
            return action.execute(mdxForm, errors);
        }
    }


/**
 * NOT PART OF OFFICIAL CLIENT API
 * the particulars of the JSON format may change, and is very tied to the dataspace implementation
 *
 *
 * config
 *
 * onRows:{}
 * A set of members to select on the rows of the returned cellset
 * (required)
 *
 * onColumns:{}
 * A set of members to select on the columns of the returned cellset
 * (optional)
 *
 * countDistinctLevel : ""
 * name of the level that contains the members we are counting in the query result (e.g. Participant)
 * (required)
 *
 * countFilter : []
 * This filter is used to specify a subset of members in the countDistinctLevel to be counted in the query result
 * (optional)
 *
 * joinLevel : ""
 * name of the level that relates the row, column, and dataFilter results (e.g. ParticipantVisit).  In some sense,
 * the whereFilter applies directly to all the rows of the fact table, but the whole point of the cube/olap strategry
 * is to reduce the data-size as much as possible.  In some sense, the joinLevel indicates the useful level of
 * granularity for the fact table filter.
 * if not specified, this is the same as the countDistinctLevel
 * (optional)
 *
 * whereFilter : []
 * This filter is is used the data processed by the query.  The result will be a set of members of
 * the joinLevel.  If joinLevel==countDistinctLevel then this will be functionally equivalent to the countFilter:[]
 * e.g. (ParticipantVisit)
 * (optional)
 *
 * filter : []
 *
 * TODO document set selection syntax (operator, memberQuery etc.)
 */

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    @Deprecated // same as countFilter
    public class CountDistinctQueryAction extends ApiAction<JsonQueryForm>
    {
        @Override
        public ApiResponse execute(JsonQueryForm form, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                return null;

            Cube cube = getCube(form, errors);
            if (errors.hasErrors())
                return null;

            JSONObject q = (JSONObject)form.json.get("query");
            if (null == q)
            {
                errors.reject(ERROR_MSG, "query not specified");
                return null;
            }

            OlapSchemaDescriptor sd = ServerManager.getDescriptor(getContainer(), form.getConfigId());
            if (null == sd)
            {
                errors.reject(ERROR_MSG, "olap cube config not found: " + form.getConfigId());
                return null;
            }

            ContainerFilter cf = null;
            String schemaName = getAnnotation(cube,"SchemaName");
            if (null != schemaName)
            {
                UserSchema schema = (UserSchema)DefaultSchema.get(getUser(), getContainer()).getSchema(schemaName);
                if (null == schema)
                    throw new ConfigurationException("Schema from olap configuration file not found : " + schemaName);
                schema.checkCanReadSchemaOlap();
                cf = schema.getOlapContainerFilter();
            }
            String containerFilterName = getAnnotation(cube,"ContainerFilter");
            if (null != containerFilterName)
            {
                cf = ContainerFilter.getContainerFilterByName(containerFilterName, getUser());
                if (null == cf)
                    throw new ConfigurationException("Container filter from olap configuration file not found : " + containerFilterName);
            }

            CellSet cs = null;
            StringWriter sw = new StringWriter();
            try
            {
                QubeQuery qquery = new QubeQuery(cube);
                qquery.fromJson(q, errors);
                if (errors.hasErrors())
                    return null;

                QueryProfiler.getInstance().ensureListenerEnvironment();
                long start = 0, end = 0;
                try
                {
                    start = System.currentTimeMillis();
                    BitSetQueryImpl bitsetquery = new BitSetQueryImpl(getContainer(), getUser(), sd, cube, null, qquery, errors);
                    if (null != cf)
                        bitsetquery.setContainerFilter(getContainerCollection(cf));
                    cs = bitsetquery.executeQuery();
                    end = System.currentTimeMillis();
                }
                finally
                {
                    QueryProfiler.getInstance().track(null, "-- CountDistinctQuery \n" + q.toString() + "\n" + sd.getQueryTag(),
                            null, (0 == start || 0 == end) ? 0 : (end - start), null, true, QueryLogging.noValidationNeededQueryLogging());
                    _log.debug("bitsetquery.executeQuery() took " + DateUtil.formatDuration(end - start));
                }

                Olap4Js.convertCellSet(cs, sw);
            }
            catch (OlapException|MondrianException ex)
            {
                rethrowOlapException(ex);
            }
            finally
            {
                ResultSetUtil.close(cs);
            }

            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);
            response.getWriter().write(sw.toString());
            return null;
        }
    }


    static void rethrowOlapException(Exception ex) throws Exception
    {
        Throwable t = ex;
        while (null != t.getCause() && t != t.getCause())
        {
            Throwable cause = t.getCause();
            if (cause instanceof QueryParseException)
            {
                // probably a configuration problem with mismatch cube/table schemas
                String advice = null;
                if (cause instanceof QueryParseExceptionUnresolvedField)
                    advice = "Check that field [" + ((QueryParseExceptionUnresolvedField)cause).getFieldKey().toDisplayString() + "] is defined.";
                String message = "Error executing query, check that cube and table schemas match:\n" + cause.getMessage();
                throw new ConfigurationException(message, advice, cause);
            }
            t = cause;
        }
        // always unwrap OlapException which is pretty useless
        if (ex.getCause() instanceof MondrianException)
            throw (MondrianException) ex.getCause();
        throw ex;
    }



    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class XmlaAction extends SimpleViewAction<OlapForm>
    {
        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        @Override
        public ModelAndView getView(OlapForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            final Dictionary<String,String> parameters = new Hashtable<>();
            ServletConfig config = new ServletConfig(){
                @Override
                public String getServletName()
                {
                    return "xmla";
                }

                @Override
                public ServletContext getServletContext()
                {
                    return ViewServlet.getViewServletContext();
                }

                @Override
                public String getInitParameter(String s)
                {
                    return parameters.get(s);
                }

                @Override
                public Enumeration getInitParameterNames()
                {
                    return parameters.elements();
                }
            };

            OlapSchemaDescriptor d = ServerManager.getDescriptor(getContainer(), form.getConfigId());

            // TODO cache servlet, maybe in session?
            MondrianXmlaServlet servlet = new MondrianXmlaServlet()
            {
                {
                    this.server = getServer();
                }
            };
            servlet.init(config);
            servlet.service(getViewContext().getRequest(), getViewContext().getResponse());
            servlet.destroy();
            return null;
        }
    }




    // for testing
    @RequiresPermission(AdminPermission.class)
    @Action(ActionType.SelectData.class)
    public class TestBrowserAction extends SimpleViewAction<OlapForm>
    {
        @Override
        public ModelAndView getView(OlapForm form, BindException errors) throws Exception
        {
            Cube cube = null;
            if (StringUtils.isNotEmpty(form.getCubeName()))
                cube = getCube(form, errors);

            return new JspView("/org/labkey/query/view/cube.jsp", cube, errors);
        }


        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    // for testing
    @RequiresPermission(AdminPermission.class)
    @Action(ActionType.SelectData.class)
    public class TestMdxAction extends SimpleViewAction<OlapForm>
    {
        @Override
        public ModelAndView getView(OlapForm form, BindException errors) throws Exception
        {
            OlapSchemaDescriptor d = null;
            if (StringUtils.isNotEmpty(form.getConfigId()))
                d = ServerManager.getDescriptor(getContainer(), form.getConfigId());
            if (null == d && StringUtils.isNotEmpty(form.getConfigId()))
            {
                errors.reject(ERROR_MSG, "Olap configuration not found: " + form.getConfigId());
            }
            return new JspView<>("/org/labkey/query/view/mdx.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    // for testing
    @RequiresPermission(AdminPermission.class)
    @Action(ActionType.SelectData.class)
    public class TestJsonAction extends SimpleViewAction<OlapForm>
    {
        @Override
        public ModelAndView getView(OlapForm form, BindException errors) throws Exception
        {
            OlapSchemaDescriptor d = null;
            if (StringUtils.isNotEmpty(form.getConfigId()))
                d = ServerManager.getDescriptor(getContainer(), form.getConfigId());
            if (null == d && StringUtils.isNotEmpty(form.getConfigId()))
            {
                errors.reject(ERROR_MSG, "Olap configuration not found: " + form.getConfigId());
            }
            return new JspView<>("/org/labkey/query/view/json.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }



    // TODO move these annotations out of the schema.xml file, and into a separate .config.xml file
    @Nullable
    private String getAnnotation(Cube cube, String name)  throws SQLException
    {
        Annotated annotated = cube instanceof Annotated ? (Annotated)cube :
                cube instanceof OlapWrapper ? ((OlapWrapper)cube).unwrap(Annotated.class) :
                null;
        if (null != annotated)
        {
            Map<String,Annotation> annotations = annotated.getAnnotationMap();
            if (null == annotations)
                return null;
            Annotation a = annotations.get(name);
            return null==a ? null : null == a.getValue() ? null : String.valueOf(a.getValue());
        }
        else if (cube instanceof CachedCube)
        {
            return ((CachedCube) cube).getAnnotationMap().get(name);
        }
        return null;
    }



    private Cube _cube = null;

    private Cube getCube(OlapForm form, BindException errors) throws SQLException, IOException
    {
        if (null != _cube)
            return _cube;

        OlapSchemaDescriptor d = null;
        if (StringUtils.isNotEmpty(form.getConfigId()))
            d = ServerManager.getDescriptor(getContainer(), form.getConfigId());

        if (null == d)
        {
            if (StringUtils.isNotEmpty(form.getConfigId()))
                errors.reject(ERROR_MSG, "Olap configuration not found: " + form.getConfigId());
            else
                errors.reject(ERROR_MSG, "configId parameter is required");
            return null;
        }

        OlapConnection conn = null;
        if (d.usesMondrian())
            conn = getConnection(d);
        Cube cube = ServerManager.getCachedCube(d, conn, getContainer(), BitSetQueryImpl.getOlapServiceUser(), form.getSchemaName(), form.getCubeName(), errors);
        if (errors.hasErrors())
        {
            return null;
        }
        _cube = cube;
        return _cube;
    }


    @Override
    protected void afterAction(Throwable t)
    {
        if (null != _connection)
        {
            try
            {
                _connection.close();
                _connection = null;
            }
            catch (SQLException x)
            {
                ;
            }
        }
        if (null != _server)
        {
            _server.shutdown();
        }
        super.afterAction(t);
    }


    OlapConnection _connection = null;

    @Nullable
    OlapConnection getConnection(OlapSchemaDescriptor d) throws SQLException
    {
        if (d == null || d.usesRolap())
            return null;

        if (null == _connection)
        {
            _connection = d.getConnection(getContainer(), getUser());
            MemTracker.getInstance().put(_connection);
        }

        return _connection;
    }

    MondrianServer _server = null;

    MondrianServer getServer() throws SQLException
    {
        if (null == _server)
        {
            _server = ServerManager.getMondrianServer(getContainer(), getUser());
        }
        return _server;
    }


    private Collection<String> getContainerCollection(ContainerFilter cf)
    {
        if (cf instanceof DataspaceContainerFilter)
        {
            DataspaceContainerFilter dscf = (DataspaceContainerFilter)cf;
            Collection<GUID> guids = dscf.getIds(getContainer(),ReadPermission.class, null);
            List<String> ret = guids.stream().map(GUID::toString).collect(Collectors.toList());
            return Collections.unmodifiableCollection(ret);
        }
        // TODO optimize, this is round-about since cf probabaly implements getIds() internally
        DbSchema core = CoreSchema.getInstance().getSchema();
        SQLFragment sqlf = new SQLFragment("SELECT entityid FROM core.containers WHERE ");
        sqlf.append(cf.getSQLFragment(core, new FieldKey(null, "entityid"), getContainer(), new HashMap<FieldKey,ColumnInfo>()));
        ArrayList<String> list = new SqlSelector(core, sqlf).getArrayList(String.class);
        return Collections.unmodifiableCollection(list);
    }

    // TODO: Move all this app stuff somewhere else out of olap land
    //
    // AppContext actions
    //

    private static final String APP_CONTEXT_CATEGORY = "appcontext";
    private static final String APP_CONTEXT_TYPE = "type";
    private static final String APP_CONTEXT_DEFAULTS = "defaults";
    private static final String APP_CONTEXT_VALUES = "values";

    public static final int APP_CONTEXT_JSON_INDENT = 2;

    public static class SinglePageAppUrlsImpl implements SinglePageAppUrls
    {
        @Override
        public ActionURL getManageAppURL(Container container)
        {
            return new ActionURL(ManageAppsAction.class, container);
        }
    }


    /**
     * Returns an app context object:
     * {
     *     name: "context name"
     *     defaults: { },
     *     values: { }
     * }
     */
    @Nullable
    private Map<String, Object> getSinglePageAppContext(Container c, String contextName)
    {
        PropertyStore store = PropertyManager.getNormalStore();
        Map<String, String> context = store.getProperties(c, APP_CONTEXT_CATEGORY + ":" + contextName);
        if (!context.isEmpty())
        {
            String defaults = context.get(APP_CONTEXT_DEFAULTS);
            JSONObject defaultsJSON = null;
            if (defaults != null)
                defaultsJSON = new JSONObject(defaults);

            String values = context.get(APP_CONTEXT_VALUES);
            JSONObject valuesJSON = null;
            if (values != null)
                valuesJSON = new JSONObject(values);

            Map<String, Object> ret = new HashMap<>();
            ret.put("name", contextName);
            ret.put("defaults", defaultsJSON);
            ret.put("values", valuesJSON);
            return ret;
        }

        return null;
    }

    private void updateSinglePageAppContext(Container c, String contextName, JSONObject defaults, JSONObject values)
    {
        PropertyManager.PropertyMap map = PropertyManager.getWritableProperties(c, APP_CONTEXT_CATEGORY + ":" + contextName, true);
        if (defaults != null)
            map.put(APP_CONTEXT_DEFAULTS, defaults.toString(APP_CONTEXT_JSON_INDENT));

        if (values != null)
            map.put(APP_CONTEXT_VALUES, values.toString(APP_CONTEXT_JSON_INDENT));

        map.save();

        PropertyManager.PropertyMap allContexts = PropertyManager.getWritableProperties(c, APP_CONTEXT_CATEGORY, true);
        allContexts.put(contextName, contextName);
        allContexts.save();
    }

    @NotNull
    private void deleteSinglePageAppContexts(Container c, String contextName)
    {
        // Delete the context settings
        PropertyManager.getNormalStore().deletePropertySet(c, APP_CONTEXT_CATEGORY + ":" + contextName);
    }

    @NotNull
    private List<String> getSinglePageAppContexts(Container c)
    {
        List<String> contextNames = new ArrayList<>();
        for (String category : PropertyManager.getCategoriesByPrefix(PropertyManager.SHARED_USER, c, APP_CONTEXT_CATEGORY + ":"))
        {
            assert category.startsWith(APP_CONTEXT_CATEGORY + ":");
            String contextName = category.substring((APP_CONTEXT_CATEGORY + ":").length());
            if (!contextNames.contains(contextName))
                contextNames.add(contextName);
        }
        return contextNames;
    }

    @RequiresPermission(AdminReadPermission.class)
    public class ListAppsAction extends ApiAction<Object>
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            List<Map<String, String>> appContexts = new ArrayList<>();
            for (String name : getSinglePageAppContexts(getContainer()))
            {
                Map<String, String> props = new HashMap<>();
                props.put("name", name);
                appContexts.add(props);
            }
            return Collections.singletonMap("apps", appContexts);
        }
    }

    public static class AppForm
    {
        private String contextName;
        private String configId;
        private String name;
        private String schemaName;

        public String getContextName()
        {
            return contextName;
        }

        public void setContextName(String contextName)
        {
            this.contextName = contextName;
        }

        public String getConfigId()
        {
            return configId;
        }

        public void setConfigId(String configId)
        {
            this.configId = configId;
        }

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getSchemaName()
        {
            return schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            this.schemaName = schemaName;
        }
    }

    public static class InsertUpdateAppForm extends AppForm
    {
        private JSONObject defaults;
        private JSONObject values;

        public JSONObject getDefaults()
        {
            return defaults;
        }

        public void setDefaults(JSONObject defaults)
        {
            this.defaults = defaults;
        }

        public JSONObject getValues()
        {
            return values;
        }

        public void setValues(JSONObject values)
        {
            this.values = values;
        }
    }

    public static class ManageAppForm extends AppForm{
        private List<String> allContextNames;

        public List<String> getAllContextNames()
        {
            return allContextNames;
        }

        public void setAllContextNames(List<String> allContextNames)
        {
            this.allContextNames = allContextNames;
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(AdminPermission.class)
    @ActionNames("insertApp,updateApp")
    public class InsertAppAction extends MutatingApiAction<InsertUpdateAppForm>
    {
        @Override
        public void validateForm(InsertUpdateAppForm form, Errors errors)
        {
            if (form == null)
                return;

            if (StringUtils.isBlank(form.getContextName()))
                errors.rejectValue("contextName", ERROR_MSG, "contextName is required");
        }

        @Override
        public Object execute(InsertUpdateAppForm form, BindException errors) throws Exception
        {
            updateSinglePageAppContext(getContainer(), form.getContextName(), form.getDefaults(), form.getValues());
            return getSinglePageAppContext(getContainer(), form.getContextName());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class DeleteAppAction extends MutatingApiAction<AppForm>
    {
        @Override
        public void validateForm(AppForm form, Errors errors)
        {
            if (StringUtils.isBlank(form.getContextName()))
                errors.rejectValue("contextName", ERROR_MSG, "contextName is required");
        }

        @Override
        public Object execute(AppForm form, BindException errors) throws Exception
        {
            deleteSinglePageAppContexts(getContainer(), form.getContextName());
            return Collections.singletonMap("success", true);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ManageAppsAction extends SimpleViewAction<ManageAppForm>
    {
        @Override
        public ModelAndView getView(ManageAppForm form, BindException errors) throws Exception
        {
            Map<String, String> activeAppConfig = getActiveAppConfig(getContainer());
            if (activeAppConfig != null && !activeAppConfig.isEmpty())
            {
                form.setName(activeAppConfig.get("name"));
                form.setSchemaName(activeAppConfig.get("schemaName"));
                form.setConfigId(activeAppConfig.get("configId"));
                form.setContextName(activeAppConfig.get("contextName"));
            }
            form.setAllContextNames(getSinglePageAppContexts(getContainer()));

            return new JspView<>("/org/labkey/query/view/manageApps.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Manage Application");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class EditAppAction extends SimpleViewAction<AppForm>
    {
        String _contextName;

        @Override
        public ModelAndView getView(AppForm form, BindException errors) throws Exception
        {
            _contextName = StringUtils.trimToNull(form.getContextName());

            Map<String, Object> context = null;
            if (_contextName != null)
                context = getSinglePageAppContext(getContainer(), form.getContextName());

            // Create a default app context
            if (context == null)
            {
                context = new HashMap<>();
                if (_contextName != null)
                    context.put("name", _contextName);
            }

            return new JspView<>("/org/labkey/query/view/editApp.jsp", context, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root
                    .addChild("Manage Application", new ActionURL(ManageAppsAction.class, getContainer()))
                    .addChild(_contextName == null ? "Insert New App Context" : "Update App Context '" + _contextName + "'");
        }
    }

    private static final String APP_ACTIVE_CONFIG_CATEGORY = "appActiveConfig";

    @RequiresPermission(AdminPermission.class)
    public class SetActiveAppConfigAction extends MutatingApiAction<AppForm>
    {
        @Override
        public Object execute(AppForm form, BindException errors) throws Exception
        {
            PropertyManager.PropertyMap activeAppConfig = PropertyManager.getWritableProperties(getContainer(), APP_ACTIVE_CONFIG_CATEGORY, true);
            activeAppConfig.put("configId", form.getConfigId());
            activeAppConfig.put("name", form.getName());
            activeAppConfig.put("schemaName", form.getSchemaName());
            activeAppConfig.put("contextName", form.getContextName());
            activeAppConfig.save();

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("config", getActiveAppConfig(getContainer()));
            response.put("success", true);
            return response;
        }
    }

    @RequiresNoPermission
    public class GetActiveAppConfigAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("config", getActiveAppConfig(getContainer()));
            response.put("success", true);
            return response;
        }
    }

    private Map<String, String> getActiveAppConfig(Container c)
    {
        PropertyStore store = PropertyManager.getNormalStore();
        Map<String, String> activeAppConfig = store.getProperties(c, APP_ACTIVE_CONFIG_CATEGORY);
        if (!activeAppConfig.isEmpty())
        {
            return activeAppConfig;
        }
        return null;
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.isInSiteAdminGroup());

            OlapController controller = new OlapController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user,
                controller.new GetCubeDefinitionAction(),
                controller.new JsonQueryAction(),
                controller.new CountDistinctQueryAction(),
                controller.new XmlaAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                controller.new CreateDefinitionAction(),
                controller.new EditDefinitionAction(),
                controller.new DeleteDefinitionAction(),
                controller.new TestBrowserAction(),
                controller.new TestMdxAction(),
                controller.new TestJsonAction(),
                controller.new InsertAppAction(),
                controller.new DeleteAppAction(),
                controller.new ManageAppsAction(),
                controller.new EditAppAction(),
                controller.new SetActiveAppConfigAction(),
                controller.new ListAppsAction()
            );

            // @RequiresSiteAdmin
            assertForRequiresSiteAdmin(user,
                controller.new ExecuteMdxAction()
            );
        }
    }
}
