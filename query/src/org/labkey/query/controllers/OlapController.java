package org.labkey.query.controllers;

import mondrian.olap.MondrianServer;
import mondrian.xmla.impl.MondrianXmlaServlet;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Category;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.data.QueryProfiler;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.Compress;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.template.PageConfig;
import org.labkey.query.olap.Olap4Js;
import org.labkey.query.olap.OlapSchemaCache;
import org.labkey.query.olap.OlapSchemaDescriptor;
import org.labkey.query.olap.QubeQuery;
import org.labkey.query.olap.ServerManager;
import org.olap4j.CellSet;
import org.olap4j.OlapConnection;
import org.olap4j.OlapStatement;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Schema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;


/**
 * Created with IntelliJ IDEA.
 * User: matthew
 * Date: 10/30/13
 * Time: 11:12 AM
 *
 * API's for querying olap (mondrian) cubes
 *
 *
 * TODO consider whether to re-enable server side cache
 *
 */
public class OlapController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(QueryController.class);

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(OlapController.class);

    public OlapController() throws Exception
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


    @RequiresPermissionClass(ReadPermission.class)
    public class GetCubeDefinitionAction extends ApiAction<OlapForm>
    {
        @Override
        public void validateForm(OlapForm form, Errors errors)
        {
            if (StringUtils.isEmpty(form.getConfigId()))
                errors.reject(ERROR_REQUIRED, "ConfigId must be provided to retrieve a cube definition.");
            if (StringUtils.isEmpty(form.getSchemaName()))
                errors.reject(ERROR_REQUIRED, "Schema name must be provided to retrieve a cube definition.");
            if (StringUtils.isEmpty(form.getCubeName()))
                errors.reject(ERROR_REQUIRED, "Cube name must be provided to retrieve a cube definition.");
        }

        @Override
        public ApiResponse execute(OlapForm form, BindException errors) throws Exception
        {
            Cube cube = getCube(form, errors);

            if (errors.hasErrors())
                return null;
            if (null == cube)
            {
                errors.reject(ERROR_MSG, "Cube not found: " + form.getCubeName());
                return null;
            }

            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("application/json");
            Olap4Js.convertCube(cube, response.getWriter());
            return null;
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


    @RequiresPermissionClass(ReadPermission.class)
    public class ExecuteMdxAction extends ApiAction<ExecuteMdxForm>
    {
        @Override
        public void validateForm(ExecuteMdxForm form, Errors errors)
        {
            if (StringUtils.isEmpty(form.getConfigId()))
                errors.reject(ERROR_REQUIRED, "ConfigId must be provided to retrieve a cube definition.");
//            if (StringUtils.isEmpty(form.getSchemaName()))
//                errors.reject(ERROR_REQUIRED, "Schema name must be provided to retrieve a cube definition.");
            super.validateForm(form, errors);
        }

        @Override
        public ApiResponse execute(ExecuteMdxForm form, BindException errors) throws Exception
        {
            String sql = StringUtils.trimToNull(form.getQuery());
            if (null == sql)
                throw new IllegalArgumentException("No value was supplied for the required parameter 'sql'.");

            try
            {
                ViewContext ctx = getViewContext();
                HttpServletResponse response = ctx.getResponse();
                String key = ctx.getContainer().getId() + ":" + sql;
//                byte[] bytes = resultsCache.get(key, form, new MDXCacheLoader());
                byte[] bytes = new MDXCacheLoader().load(key,form);

                response.setContentType(ApiJsonWriter.CONTENT_TYPE_JSON);
                response.setHeader("Content-Encoding", "gzip");
                response.getOutputStream().write(bytes);
                return null;
            }
            catch (RuntimeException x)
            {
                Category.getInstance(OlapController.class).error("mondrian error", x);
                Throwable t = x;
                while (null != t.getCause() && t != t.getCause())
                    t = t.getCause();
                errors.reject(SpringActionController.ERROR_MSG,t.getMessage());
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
                OlapSchemaDescriptor sd = OlapSchemaCache.get().getDescriptor(getContainer(), form.getConfigId());
                OlapConnection conn = controller.getConnection(sd);
                stmt = conn.createStatement();
                String query = form.getQuery();
                Category.getInstance(this.getClass()).debug("\nSTART executeOlapQuery: --------------------------    --------------------------    --------------------------\n" + query);
                long ms = System.currentTimeMillis();
                cs = stmt.executeOlapQuery(query);
                long d = System.currentTimeMillis() - ms;
                QueryProfiler.track(null, "-- MDX\n" + query, null, d, null, true);
                Category.getInstance(this.getClass()).debug("\nEND executeOlapQuery: " + DateUtil.formatDuration(d) + " --------------------------    --------------------------    --------------------------\n");

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
    @RequiresPermissionClass(ReadPermission.class)
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

            QubeQuery qquery = new QubeQuery(cube);
            qquery.fromJson(q, errors);
            if (errors.hasErrors())
                return null;
            String mdx = qquery.generateMDX(errors);
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


    @RequiresPermissionClass(ReadPermission.class)
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

            OlapSchemaDescriptor d = OlapSchemaCache.get().getDescriptor(getContainer(), form.getConfigId());

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
    @RequiresPermissionClass(AdminPermission.class)
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
    @RequiresPermissionClass(AdminPermission.class)
    public class TestMdxAction extends SimpleViewAction<OlapForm>
    {
        @Override
        public ModelAndView getView(OlapForm form, BindException errors) throws Exception
        {
            OlapSchemaDescriptor d = null;
            if (StringUtils.isNotEmpty(form.getConfigId()))
                d = OlapSchemaCache.get().getDescriptor(getContainer(), form.getConfigId());
            if (null == d && StringUtils.isNotEmpty(form.getConfigId()))
            {
                errors.reject(ERROR_MSG, "Olap configuration not found: " + form.getSchemaName());
            }
            return new JspView("/org/labkey/query/view/mdx.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }



    private Cube getCube(OlapForm form, BindException errors) throws SQLException
    {
        OlapSchemaDescriptor d = null;
        if (StringUtils.isNotEmpty(form.getConfigId()))
            d = OlapSchemaCache.get().getDescriptor(getContainer(), form.getConfigId());

        if (null == d)
        {
            if (StringUtils.isNotEmpty(form.getConfigId()))
                errors.reject(ERROR_MSG, "Olap configuration not found: " + form.getConfigId());
            else
                errors.reject(ERROR_MSG, "configId parameter is required");
            return null;
        }

        List<Schema> findSchemaList;
        if (StringUtils.isNotEmpty(form.getSchemaName()))
        {
            Schema s = d.getSchema(getConnection(d), getContainer(), getUser(), form.getSchemaName());
            if (null == s)
            {
                errors.reject(ERROR_MSG, "Schema not found: " + form.getSchemaName());
                return null;
            }
            findSchemaList = Collections.singletonList(s);
        }
        else
        {
            findSchemaList = d.getSchemas(getConnection(d), getContainer(), getUser());
        }


        Cube cube = null;
        if (StringUtils.isEmpty(form.getCubeName()))
        {
            errors.reject(ERROR_MSG, "cubeName parameter is required");
            return null;
        }
        else
        {
            for (Schema s : findSchemaList)
            {
                Cube findCube = s.getCubes().get(form.getCubeName());
                if (null != findCube)
                {
                    if (cube != null)
                    {
                        errors.reject(ERROR_MSG, "Cube is ambigious, specify schemaName: " + form.getCubeName());
                        return null;
                    }
                    cube = findCube;
                }
            }
            if (null == cube)
            {
                errors.reject(ERROR_MSG, "Cube not found: " + form.getCubeName());
                return null;
            }
            return cube;
        }
    }




    @Override
    protected void afterAction(Throwable t)
    {
        if (null != _connection)
        {
            try
            {
                _connection.close();
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

    OlapConnection getConnection(OlapSchemaDescriptor d) throws SQLException
    {
        if (null == _connection)
        {
            _connection = d.getConnection(getContainer(), getUser());
            assert MemTracker.put(_connection);
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






    /*
     * TESTS
     */

    @RequiresSiteAdmin
    public class TestCDS extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String m = null;
            try
            {
                new QubeQuery.TestCase().parseTest(getContainer(), getUser());
                m = "FINISHED";
            }
            catch (Exception x)
            {
                m = StringUtils.defaultString(x.getMessage(),x.toString());
                _log.error("test failed", x);
            }

            return new HtmlView(PageFlowUtil.filter(m));
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

}
