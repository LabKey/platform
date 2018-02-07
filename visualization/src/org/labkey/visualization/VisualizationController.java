/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.visualization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.Action;
import org.labkey.api.action.ActionType;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiResponseWriter;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.ExtendedApiQueryResponse;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.Activity;
import org.labkey.api.data.ActivityService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.QueryLogging;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.views.DataViewProvider.EditInfo.ThumbnailType;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.ParticipantCategory;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.thumbnail.ThumbnailService.ImageType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.GenericChartReport;
import org.labkey.api.visualization.GenericChartReportDescriptor;
import org.labkey.api.visualization.SQLGenerationException;
import org.labkey.api.visualization.SvgThumbnailGenerator;
import org.labkey.api.visualization.VisDataRequest;
import org.labkey.api.visualization.VisualizationProvider;
import org.labkey.api.visualization.VisualizationProvider.MeasureSetRequest;
import org.labkey.api.visualization.VisualizationReportDescriptor;
import org.labkey.api.visualization.VisualizationService;
import org.labkey.api.visualization.VisualizationUrls;
import org.labkey.visualization.sql.VisualizationCDSGenerator;
import org.labkey.visualization.sql.VisualizationSQLGenerator;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/*
 * User: brittp
 * Date: Sep 13, 2010 10:02:53 AM
 */
public class VisualizationController extends SpringActionController
{
    public static final String NAME = "visualization";
    public static final String FILTER_DATAREGION = "Dataset";
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(VisualizationController.class);
    private static final Logger _log = Logger.getLogger(VisualizationController.class);

    public static class VisualizationUrlsImpl implements VisualizationUrls
    {
        private static final String VISUALIZATION_NAME_PARAM = "name";
        private static final String VISUALIZATION_FILTER_URL = "filterUrl";
        private static final String VISUALIZATION_EDIT_PARAM = "edit";
        private static final String VISUALIZATION_ID_PARAM = "reportId";
        private static final String VISUALIZATION_RENDERTYPE_PARAM = "renderType";

        private void addQueryParams(ActionURL url, Container container, User user, QuerySettings settings)
        {
            String queryName = settings.getQueryName();
            if (queryName != null)
                url.addParameter(QueryParam.queryName, queryName);
            String schemaName = settings.getSchemaName();
            if (schemaName != null)
                url.addParameter(QueryParam.schemaName, schemaName);
            String viewName = settings.getViewName();
            if (viewName != null)
                url.addParameter(QueryParam.viewName, viewName);

            // Get URL (column-header) filters:
            ActionURL filterURL = settings.getSortFilterURL();
            filterURL.addParameter(QueryParam.dataRegionName, settings.getDataRegionName());

            // Add the base filter as set in code:
            SimpleFilter baseFilter = settings.getBaseFilter();
            baseFilter.applyToURL(filterURL, settings.getDataRegionName());

            // Finally, add view-level filters:
            CustomView view = QueryService.get().getCustomView(user, container, user, settings.getSchemaName(), settings.getQueryName(), settings.getViewName());
            if (view != null)
                view.applyFilterAndSortToURL(filterURL, settings.getDataRegionName());

            url.addParameter(QueryParam.dataRegionName, settings.getDataRegionName());
            url.addParameter(VISUALIZATION_FILTER_URL, filterURL.getLocalURIString());
        }

        private ActionURL getBaseGenericChartURL(Container container, boolean editMode)
        {
            ActionURL url = new ActionURL(GenericChartWizardAction.class, container);
            if (editMode)
                url.addParameter(VISUALIZATION_EDIT_PARAM, true);
            return url;
        }

        @Override
        public ActionURL getTimeChartDesignerURL(Container container)
        {
            ActionURL url = getBaseGenericChartURL(container, true);
            url.addParameter(QueryParam.schemaName, "study");
            url.addParameter(VISUALIZATION_RENDERTYPE_PARAM, "time_chart");
            return url;
        }

        @Override
        public ActionURL getTimeChartDesignerURL(Container container, Report report, boolean editMode)
        {
            ActionURL url = getBaseGenericChartURL(container, editMode);
            String queryName = report.getDescriptor().getProperty(ReportDescriptor.Prop.queryName);
            if (queryName != null)
                url.addParameter(QueryParam.queryName, queryName);
            String schemaName = report.getDescriptor().getProperty(ReportDescriptor.Prop.schemaName);
            if (schemaName != null)
                url.addParameter(QueryParam.schemaName, schemaName);
            url.addParameter(VISUALIZATION_ID_PARAM, report.getDescriptor().getReportId().toString());
            return url;
        }

        @Override
        public ActionURL getGenericChartDesignerURL(Container container, User user, @Nullable QuerySettings settings, @Nullable GenericChartReport.RenderType type)
        {
            ActionURL url = getBaseGenericChartURL(container, true);

            if (settings != null)
            {
                addQueryParams(url, container, user, settings);
            }

            if (type != null)
            {
                url.addParameter(GenericChartReportDescriptor.Prop.renderType, type.getId());
            }

            return url;
        }
    }


    public VisualizationController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    public static class DimensionValuesForm extends MeasureSetRequest
    {
        private String _filterUrl;

        public String getFilterUrl()
        {
            return _filterUrl;
        }

        public void setFilterUrl(String filterUrl)
        {
            _filterUrl = filterUrl;
        }
    }


    @Action(ActionType.SelectMetaData.class)
    @RequiresPermission(ReadPermission.class)
    public class GetMeasuresAction<Form extends MeasureSetRequest> extends ApiAction<Form>
    {
        public ApiResponse execute(Form measureRequest, BindException errors) throws Exception
        {
            VisualizationService vs = ServiceRegistry.get(VisualizationService.class);
            Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> measures = vs.getMeasures(getContainer(), getUser(), measureRequest);

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("measures", vs.toJSON(measures));
            return resp;
        }
    }


    /**
     * This is exactly the same as getMeasures(), but will return the same result over-and-over for all users
     * until clear caches is called (or this particular cache is cleared).  Only apps with fixes schemas should use this
     * action.
     */

    static private final StringKeyCache _getMeasuresCache = CacheManager.getStringKeyCache(CacheManager.UNLIMITED,CacheManager.UNLIMITED,"getMeasuresStaticCache");

    @Action(ActionType.SelectMetaData.class)
    @RequiresPermission(ReadPermission.class)
    public class GetMeasuresStaticAction<Form extends MeasureSetRequest> extends ApiAction<Form>
    {
        public ApiResponse execute(Form measureRequest, BindException errors) throws Exception
        {
            String key = getContainer().getId() + ":" + measureRequest.getCacheKey();
            ActivityService activityService = ServiceRegistry.get(ActivityService.class);
            if (activityService != null)
            {
                Activity activity = activityService.getCurrentActivity(getViewContext());
                if (activity != null && activity.getPHI() != null)
                    key += ":" + activity.getPHI();
            }

            List<Map<String,Object>> json = (List<Map<String,Object>>)_getMeasuresCache.get(key);
            if (json == null)
            {
                VisualizationService vs = ServiceRegistry.get(VisualizationService.class);
                Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> measures = vs.getMeasures(getContainer(), getUser(), measureRequest);
                 json = vs.toJSON(measures);
                _getMeasuresCache.put(key,json);
            }
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("measures", json);
            return resp;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ClearMeasuresCacheAction extends ApiAction<Object>
    {
        public ApiResponse execute(Object measureRequest, BindException errors) throws Exception
        {
            _getMeasuresCache.clear();
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            return resp;
        }
    }



    @Action(ActionType.SelectMetaData.class)
    @RequiresPermission(ReadPermission.class)
    public class GetDimensionsAction extends GetMeasuresAction<MeasureSetRequest>
    {
        @Override
        public void validateForm(MeasureSetRequest measureRequest, Errors errors)
        {
            if (measureRequest.getSchemaName() == null || measureRequest.getQueryName() == null)
            {
                throw new IllegalArgumentException("schemaName and queryName are required parameters");
            }
        }

        public ApiResponse execute(MeasureSetRequest measureRequest, BindException errors) throws Exception
        {
            VisualizationService vs = ServiceRegistry.get(VisualizationService.class);
            Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> dimensions = vs.getDimensions(getContainer(), getUser(), measureRequest);

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("dimensions", vs.toJSON(dimensions));

            return resp;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class GetDimensionValues extends GetMeasuresAction<DimensionValuesForm>
    {
        public ApiResponse execute(DimensionValuesForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            if (form.getName() != null && form.getSchemaName() != null && form.getQueryName() != null)
            {
                UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
                final List<Map<String, String>> values = new ArrayList<>();

                if (schema != null)
                {
                    TableInfo tinfo = schema.getTable(form.getQueryName());

                    if (tinfo != null)
                    {
                        FieldKey dimensionKey = FieldKey.fromString(form.getName());
                        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(tinfo, Collections.singleton(dimensionKey));
                        ColumnInfo col = cols.get(dimensionKey);

                        if (col != null)
                        {
                            SimpleFilter filter = null;
                            String filterUrlString = form.getFilterUrl();
                            if (filterUrlString != null)
                            {
                                ActionURL filterUrl = new ActionURL(filterUrlString);

                                String dataRegionName = filterUrl.getParameter(QueryParam.dataRegionName);
                                if (dataRegionName == null)
                                    dataRegionName = VisualizationController.FILTER_DATAREGION;

                                filter = new SimpleFilter(filterUrl, dataRegionName);
                            }

                            QueryLogging queryLogging = new QueryLogging();
                            SQLFragment sql = QueryService.get().getSelectSQL(tinfo, Collections.singleton(col), filter, null, Table.ALL_ROWS, Table.NO_OFFSET, false, queryLogging);
                            SQLFragment distinctSql = new SQLFragment(sql);
                            int i = StringUtils.indexOf(sql.getSqlCharSequence(), "SELECT");
                            if (i >= 0)
                                distinctSql.insert(i + "SELECT".length(), " DISTINCT");

                            new SqlSelector(schema.getDbSchema().getScope(), distinctSql, queryLogging).forEach(new Selector.ForEachBlock<ResultSet>()
                            {
                                @Override
                                public void exec(ResultSet rs) throws SQLException
                                {
                                    Object o = rs.getObject(1);
                                    if (o != null)
                                        values.add(Collections.singletonMap("value", ConvertUtils.convert(o)));
                                }
                            });
                        }
                    }
                }
                resp.put("success", true);
                resp.put("values", values);
            }
            else
                throw new IllegalArgumentException("name, schemaName and queryName are required parameters");

            return resp;
        }
    }

    @Action(ActionType.SelectMetaData.class)
    @RequiresPermission(ReadPermission.class)
    public class GetZeroDateAction extends GetMeasuresAction<MeasureSetRequest>
    {
        @Override
        public void validateForm(MeasureSetRequest measureSetRequest, Errors errors)
        {
            measureSetRequest.setZeroDateMeasures(true);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetVisualizationTypes extends ApiAction
    {
        Map<String, Object> getBaseTypeProperties(Study study)
        {
            Map<String, Object> properties = new HashMap<>();
            properties.put("subjectColumn", StudyService.get().getSubjectColumnName(getContainer()));
            properties.put("subjectNounSingular", StudyService.get().getSubjectNounSingular(getContainer()));
            properties.put("subjectNounPlural", StudyService.get().getSubjectNounPlural(getContainer()));
            properties.put("TimepointType", study.getTimepointType().toString().toLowerCase());
            return properties;
        }

        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);

            List<Map<String, Object>> types = new ArrayList<>();
            Study study = StudyService.get().getStudy(getContainer());
            if (study == null)
            {
                throw new NotFoundException("No study available in " + getContainer().getPath());
            }

            // line chart
            Map<String, Object> line = getBaseTypeProperties(study);
            line.put("type", "line");
            line.put("label", "Time Chart");
            line.put("icon", getViewContext().getContextPath() + "/reports/output_linechart.jpg");

            List<Map<String, String>> lineAxis = new ArrayList<>();
            lineAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select the date measurement for the x-axis", "multiSelect", "false", "timeAxis", "true"));
            lineAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            line.put("axis", lineAxis);

            line.put("enabled", true);
            types.add(line);

            // data grid
            Map<String, Object> data = getBaseTypeProperties(study);
            data.put("type", "dataGridTime");
            data.put("label", "Data Grid (Time)");
            data.put("icon", getViewContext().getContextPath() + "/reports/output_grid.jpg");

            List<Map<String, String>> dataAxis = new ArrayList<>();
            dataAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select the date measurement for the x-axis", "multiSelect", "false", "timeAxis", "true"));
            dataAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            data.put("axis", dataAxis);
            data.put("isGrid", true);
            data.put("enabled", true);

            types.add(data);

            // data grid
            Map<String, Object> dataScatter = getBaseTypeProperties(study);
            dataScatter.put("type", "dataGridScatter");
            dataScatter.put("label", "Data Grid (X/Y)");
            dataScatter.put("icon", getViewContext().getContextPath() + "/reports/output_grid.jpg");

            List<Map<String, String>> dataScatterAxis = new ArrayList<>();
            dataScatterAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select data type for x-axis", "multiSelect", "false"));
            dataScatterAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            dataScatter.put("axis", dataScatterAxis);
            dataScatter.put("isGrid", true);
            dataScatter.put("enabled", true);

            types.add(dataScatter);
            resp.put("types", types);

            return resp;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    @Marshal(Marshaller.Jackson)
    public class GetDataAction extends ApiAction<VisDataRequest>
    {
        @Override
        protected ObjectReader getObjectReader(Class c)
        {
            ObjectReader r = super.getObjectReader(c);
            return r.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }

        @Override
        public ApiResponse execute(VisDataRequest form, BindException errors) throws Exception
        {
            VisualizationSQLGenerator sqlGenerator = new VisualizationSQLGenerator();
            sqlGenerator.setViewContext(getViewContext());
            sqlGenerator.fromVisDataRequest(form);

            String sql;
            try
            {
                sql = sqlGenerator.getSQL();
            }
            catch (SQLGenerationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                _log.warn("Unable to generate visualization SQL. " + e.getMessage());
                return null;
            }

            ApiQueryResponse response = getApiResponse(getViewContext(), sqlGenerator.getPrimarySchema(), sql, sqlGenerator.isMetaDataOnly(), sqlGenerator.getSort(), errors);

            // Note: extra properties can only be gathered after the query has executed, since execution populates the name maps.
            Map<String, Object> extraProperties = new HashMap<>();
            extraProperties.put("measureToColumn", sqlGenerator.getColumnMapping());
            extraProperties.put("columnAliases", sqlGenerator.getColumnAliases());
            sqlGenerator.getPrimarySchema().createVisualizationProvider().addExtraResponseProperties(extraProperties);
            String filterDescription = sqlGenerator.getFilterDescription();
            if (filterDescription != null && filterDescription.length() > 0)
                extraProperties.put("filterDescription", filterDescription);
            response.setExtraReturnProperties(extraProperties);

            return response;
        }

        private ApiQueryResponse getApiResponse(ViewContext context, UserSchema schema, String sql, boolean metaDataOnly, Sort sort, BindException errors) throws Exception
        {
            String schemaName = schema.getName();
            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query

            QueryDefinition def = QueryService.get().saveSessionQuery(context, context.getContainer(), schemaName, sql);

            QuerySettings settings = new QuerySettings(context, "visualization", def.getName());

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);
            settings.setBaseSort(sort);

            //by default, return all rows
            settings.setShowRows(ShowRows.ALL);

            //build a query view using the schema and settings
            QueryView view = new QueryView(schema, settings, errors);
            view.setShowRecordSelectors(false);
            view.setShowExportButtons(false);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

            return new ExtendedApiQueryResponse(view, false,
                    false, schemaName, def.getName(), 0, null, metaDataOnly, false, false);
        }
    }


    @RequiresSiteAdmin
    public class TestGetDataAction extends SimpleViewAction<Void>
    {
        @Override
        public ModelAndView getView(Void noform, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/visualization/test/test.jsp");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    @Marshal(Marshaller.Jackson)
    public class cdsGetDataAction extends GetDataAction
    {
        @Override
        protected ObjectReader getObjectReader(Class c)
        {
            ObjectReader r = super.getObjectReader(c);
            return r.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }

        @Override
        public ApiResponse execute(VisDataRequest form, BindException errors) throws Exception
        {
            VisualizationCDSGenerator sqlGenerator = new VisualizationCDSGenerator(getViewContext(), form);

            String sql;
            try
            {
                sql = sqlGenerator.getSQL(errors);
            }
            catch (SQLGenerationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                _log.warn("Unable to generate visualization SQL. " + e.getMessage());
                return null;
            }

            ApiQueryResponse response = getApiResponse(getViewContext(), sqlGenerator.getPrimarySchema(), sql, sqlGenerator.isMetaDataOnly(), sqlGenerator.getSort(), errors);

            // Note: extra properties can only be gathered after the query has executed, since execution populates the name maps.
            Map<String, Object> extraProperties = new HashMap<>();
            extraProperties.put("columnAliases", sqlGenerator.getColumnAliases());
            sqlGenerator.getPrimarySchema().createVisualizationProvider().addExtraResponseProperties(extraProperties);

            String filterDescription = sqlGenerator.getFilterDescription();
            if (filterDescription.length() > 0)
                extraProperties.put("filterDescription", filterDescription);
            response.setExtraReturnProperties(extraProperties);

            return response;
        }


        private ApiQueryResponse getApiResponse(ViewContext context, UserSchema schema, String sql, boolean metaDataOnly, Sort sort, BindException errors) throws Exception
        {
            String schemaName = schema.getName();
            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query

            QueryDefinition def = QueryService.get().saveSessionQuery(context, context.getContainer(), schemaName, sql);

            QuerySettings settings = new QuerySettings(context, "visualization", def.getName());

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);
            settings.setBaseSort(sort);

            //by default, return all rows
            settings.setShowRows(ShowRows.ALL);

            //build a query view using the schema and settings
            QueryView view = new QueryView(schema, settings, errors);
            view.setShowRecordSelectors(false);
            view.setShowExportButtons(false);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

            return new ExtendedApiQueryResponse(view, false,
                    false, schemaName, def.getName(), 0, null, metaDataOnly, false, false);
        }
    }


    @RequiresSiteAdmin
    public class cdsTestGetDataAction extends SimpleViewAction<Void>
    {
        @Override
        public ModelAndView getView(Void noform, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/visualization/test/test.jsp");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }





    /**
     * Expects an HTTP post with no parameters, the post body carrying an SVG XML document.
     * Alternately a form-encoded post with a parameter called svg to allow JavaScript clients to access it
     */
    @RequiresPermission(ReadPermission.class)
    @CSRF
    abstract class ExportSVGAction extends BaseViewAction
    {
        private String _svgSource;

        @Override
        protected String getCommandClassMethodName()
        {
            return "execute";
        }

        @Override
        public void validate(Object o, Errors errors)
        {
            return; //TODO: Validate XML
        }

        // Throws NotFoundException if SVG source is not posted
        protected @NotNull String getSVGSource() throws Exception
        {
            if (null != _svgSource)
                return _svgSource;

            HttpServletRequest request = getViewContext().getRequest();

            String contentType = request.getContentType();

            if (null != contentType && contentType.startsWith("text/xml"))
                _svgSource = PageFlowUtil.getReaderContentsAsString(request.getReader());
            else
                _svgSource = request.getParameter("svg");

            return filterSVGSource(_svgSource);
        }

        protected @NotNull String getTitle()
        {
            HttpServletRequest request = getViewContext().getRequest();
            if (!StringUtils.isEmpty(request.getParameter("title")))
                return request.getParameter("title");
            return "visualization";
        }

        // Make sure the filename doesn't foul up the header
        protected final @NotNull String getFilename(String extension)
        {
            return (getTitle() + "." + extension).replaceAll("\"", "-");
        }
    }

    /**
     * Expects an HTTP post with no parameters, the post body carrying an SVG XML document.
     * Content-type of request must be text/xml, not any kind of multipart
     * Returns a PNG image.
     */
    @RequiresPermission(ReadPermission.class)
    public class ExportImageAction extends ExportSVGAction
    {
        @Override
        public ModelAndView handleRequest() throws Exception
        {
            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("image/png");
            response.addHeader("Content-Disposition", "attachment; filename=\"" + getFilename("png") + "\"");

            DocumentConversionService svc = ServiceRegistry.get().getService(DocumentConversionService.class);

            if (null != svc)
                svc.svgToPng(getSVGSource(), response.getOutputStream());

            return null;
        }
    }

    /**
     * Expects an HTTP post with no parameters, the post body carrying an SVG XML document.
     * Content-type of request must be text/xml, not any kind of multipart
     * Returns a PDF document containing the visualization as a scalable vector graphic
     */
    @RequiresPermission(ReadPermission.class)
    public class ExportPDFAction extends ExportSVGAction
    {
        @Override
        public ModelAndView handleRequest() throws Exception
        {
            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("application/pdf");
            response.addHeader("Content-Disposition", "attachment; filename=\"" + getFilename("pdf") + "\"");

            TranscoderInput xIn = new TranscoderInput(new StringReader(getSVGSource()));
            TranscoderOutput xOut = new TranscoderOutput(response.getOutputStream());

            new PDFTranscoder().transcode(xIn, xOut);

            return null;
        }
    }

    public static String filterSVGSource(String svg) throws NotFoundException
    {
        if (StringUtils.isBlank(svg))
            throw new NotFoundException("SVG source was not posted");

        //svg MUST have right namespace for Batik, but svg generated by browser doesn't necessarily declare it
        //Since changing namespace recursively for all nodes not supported by DOM impl, just poke it in here.
        if (!svg.contains("xmlns=\"" + SVGDOMImplementation.SVG_NAMESPACE_URI + "\"") && !svg.contains("xmlns='" + SVGDOMImplementation.SVG_NAMESPACE_URI + "'"))
            svg = svg.replace("<svg", "<svg xmlns='" + SVGDOMImplementation.SVG_NAMESPACE_URI + "'");

        // remove xlink:title to prevent org.apache.batik.transcoder.TranscoderException (issue #16173)
        svg = svg.replaceAll("xlink:title", "title");

        return svg;
    }

    public static class SaveVisualizationForm
    {
        private String _type;
        private String _name;
        private String _description;
        private String _json;
        private boolean _replace;
        private boolean _shared = true;
        private String _thumbnailType;
        private String _svg;
        private String _schemaName;
        private String _queryName;
        private ReportIdentifier _reportId;

        public String getJson()
        {
            return _json;
        }

        public void setJson(String json)
        {
            _json = json;
        }

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public boolean isReplace()
        {
            return _replace;
        }

        public void setReplace(boolean replace)
        {
            _replace = replace;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public boolean isShared()
        {
            return _shared;
        }

        public void setShared(boolean shared)
        {
            _shared = shared;
        }

        public String getThumbnailType()
        {
            if (_thumbnailType == null)
                _thumbnailType = ThumbnailType.AUTO.name();
            return _thumbnailType;
        }

        public void setThumbnailType(String thumbnailType)
        {
            _thumbnailType = thumbnailType;
        }

        public String getSvg()
        {
            return _svg;
        }

        public void setSvg(String svg)
        {
            _svg = svg;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
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

        public ReportIdentifier getReportId()
        {
            return _reportId;
        }

        public void setReportId(ReportIdentifier reportId)
        {
            _reportId = reportId;
        }
    }

    private String getReportKey(String schema, String query)
    {
        if (schema == null)
            schema = "none";

        if (query == null)
            query = "none";

        return ReportUtil.getReportKey(schema, query);
    }

    private Report getReport(SaveVisualizationForm form) throws SQLException
    {
        return getReport(form.getReportId(), form.getName(), form.getSchemaName(), form.getQueryName());
    }

    private Report getReport(ChartWizardReportForm form) throws SQLException
    {
        return getReport(form.getReportId(), form.getName(), form.getSchemaName(), form.getQueryName());
    }

    private Report getReport(ReportIdentifier reportId, String reportName, String schemaName, String queryName) throws SQLException
    {
        try
        {
            if (reportId != null)
            {
                Report report = reportId.getReport(getViewContext());
                if (report != null)
                    return report;
            }

            // try to match on report name if we don't have a valid report identifier
            Collection<Report> currentReports;

            if (schemaName != null && queryName != null)
                currentReports = ReportService.get().getReports(getUser(), getContainer(), getReportKey(schemaName, queryName));
            else
                currentReports = ReportService.get().getReports(getUser(), getContainer());

            for (Report report : currentReports)
            {
                if (report.getDescriptor().getReportName() != null &&
                    report.getDescriptor().getReportName().equals(reportName))
                {
                    return report;
                }
            }
        }
        catch (Exception e)
        {
            throw new SQLException(e);
        }
        return null;
    }

    @RequiresPermission(ReadPermission.class)
    public class GetVisualizationAction extends ApiAction<ChartWizardReportForm>
    {
        private ReportDescriptor descriptor;

        @Override
        public void validateForm(ChartWizardReportForm form, Errors errors)
        {
            if (form.getName() == null && form.getReportId() == null)
            {
                errors.reject(ERROR_MSG, "The 'name' or 'reportId' property is required to get a saved visualization.");
                return;
            }

            Report report = null;
            try
            {
                report = getReport(form);
            }
            catch (SQLException e)
            {
                errors.reject(ERROR_MSG, "Visualization \"" + form.getName() + "\" does not exist in " + getContainer().getPath() + ".");
            }

            if (report == null || !report.getDescriptor().getContainerId().equals(getContainer().getId()))
            {
                errors.reject(ERROR_MSG, "Visualization \"" + form.getName() + "\" does not exist in " + getContainer().getPath() + ".");
                return;
            }

            if (!report.getDescriptor().isShared() && report.getDescriptor().getOwner() != getUser().getUserId())
            {
                errors.reject(ERROR_MSG, "You do not have permissions to view this private report.");
                return;
            }

            descriptor = report.getDescriptor();
            if (!(descriptor instanceof VisualizationReportDescriptor))
            {
                errors.reject(ERROR_MSG, "Report type \"" + descriptor.getReportType() + "\" is not an available visualization type.");
                return;
            }
        }

        @Override
        public ApiResponse execute(ChartWizardReportForm form, BindException errors) throws Exception
        {
            VisualizationReportDescriptor vizDescriptor = (VisualizationReportDescriptor) descriptor;
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("reportId", vizDescriptor.getReportId());
            resp.put("name", vizDescriptor.getReportName());
            resp.put("visualizationConfig", vizDescriptor.getJSON());
            resp.put("description", vizDescriptor.getReportDescription());
            resp.put("schemaName", vizDescriptor.getProperty(ReportDescriptor.Prop.schemaName));
            resp.put("queryName", vizDescriptor.getProperty(ReportDescriptor.Prop.queryName));
            resp.put("type", vizDescriptor.getReportType());
            resp.put("shared", vizDescriptor.isShared());
            resp.put("ownerId", !vizDescriptor.isShared() ? vizDescriptor.getOwner() : null);
            resp.put("createdBy", vizDescriptor.getCreatedBy());
            resp.put("reportProps", vizDescriptor.getReportProps());
            resp.put("thumbnailURL", ReportUtil.getThumbnailUrl(getContainer(), getReport(form)));
            return resp;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @RequiresLogin
    public class SaveVisualizationAction extends ApiAction<SaveVisualizationForm>
    {
        private Report _currentReport;

        @Override
        public void validateForm(SaveVisualizationForm form, Errors errors)
        {
            super.validateForm(form, errors);
            if (form.getName() == null)
                errors.reject(ERROR_MSG, "Name must be specified when saving a report.");

            Report report;

            try
            {
                report = getReport(form);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            if (report != null)
            {
                _currentReport = report.clone();

                if (!_currentReport.canEdit(getUser(), getContainer()))
                {
                    errors.reject(ERROR_MSG, "You do not have permission to save shared reports.");
                }

                if (!form.isReplace())
                {
                    errors.reject(ERROR_MSG, "A report by the name \"" + form.getName() + "\" already exists.  " +
                            "To update, set the 'replace' parameter to true.");
                }
            }
            else
            {
                if (form.getType() == null)
                {
                    errors.reject(ERROR_MSG, "Report type must be specified.");
                }

                _currentReport = ReportService.get().createReportInstance(form.getType());
                if (_currentReport == null)
                {
                    errors.reject(ERROR_MSG, "Report type \"" + form.getType() + "\" is not recognized.");
                }
            }

            if (_currentReport != null && !(_currentReport.getDescriptor() instanceof VisualizationReportDescriptor))
            {
                errors.reject(ERROR_MSG, "Report type \"" + form.getType() + "\" is not an available visualization type.");
            }
        }

        @Override
        public ApiResponse execute(SaveVisualizationForm form, BindException errors) throws Exception
        {
            if (_currentReport == null)
                throw new IllegalStateException("_currentReport should always be set in validateForm");
            VisualizationReportDescriptor vizDescriptor = (VisualizationReportDescriptor) _currentReport.getDescriptor();
            vizDescriptor.setReportName(form.getName());
            vizDescriptor.setReportKey(getReportKey(form.getSchemaName(), form.getQueryName()));
            vizDescriptor.setJSON(form.getJson());
            vizDescriptor.setContainer(getContainer().getId());
            vizDescriptor.setReportDescription(form.getDescription());
            if (form.getSchemaName() != null)
                vizDescriptor.setProperty(ReportDescriptor.Prop.schemaName, form.getSchemaName());
            if (form.getQueryName() != null)
                vizDescriptor.setProperty(ReportDescriptor.Prop.queryName, form.getQueryName());
            if (_currentReport.getDescriptor().getReportId() != null)
                vizDescriptor.setReportId(_currentReport.getDescriptor().getReportId());
            vizDescriptor.setOwner(form.isShared() ? null : getUser().getUserId());
            int reportId = ReportService.get().saveReport(getViewContext(), vizDescriptor.getReportKey(), _currentReport);

            // Re-select the saved report to make sure it has an entityId
            Report report = ReportService.get().getReport(getContainer(), reportId);

            if (report instanceof SvgThumbnailGenerator)
            {
                saveSVGThumbnail((SvgThumbnailGenerator)report, form.getSvg(), form.getThumbnailType());
            }

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("visualizationId", reportId);
            resp.put("name", _currentReport.getDescriptor().getReportName());
            return resp;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class TimeChartWizardAction extends SimpleViewAction<ChartWizardReportForm>
    {
        String _navTitle = "Chart Wizard";

        @Override
        public ModelAndView getView(ChartWizardReportForm form, BindException errors) throws Exception
        {
            form.setAllowToggleMode(true);
            form.setRenderType("time_chart");

            // issue 27439: allow chart wizard report lookup by name if reportId not provided
            Report report = getReport(form);
            if (form.getReportId() == null && report != null && report.getDescriptor() != null)
                form.setReportId(report.getDescriptor().getReportId());

            JspView timeChartWizard = new JspView<>("/org/labkey/visualization/views/chartWizard.jsp", form);
            timeChartWizard.setTitle(_navTitle);
            timeChartWizard.setFrame(WebPartView.FrameType.NONE);
            VBox boxView = new VBox(timeChartWizard);

            if (report != null)
            {
                _navTitle = report.getDescriptor().getReportName();

                // check if the report is shared and if not, whether the user has access to the report
                if (report.getDescriptor().isShared() || (report.getDescriptor().getOwner() == getUser().getUserId()))
                {
                    String title = "Discuss report - " + report.getDescriptor().getReportName();
                    DiscussionService service = DiscussionService.get();
                    HttpView discussion = service.getDiscussionArea(getViewContext(), report.getEntityId(), getViewContext().getActionURL(), title, true, false);
                    boxView.addView(discussion);
                }
            }

            return boxView;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("timeChart");
            return root.addChild(_navTitle);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class) // TODO rename to just ChartWizardAction
    public class GenericChartWizardAction extends SimpleViewAction<ChartWizardReportForm>
    {
        String _navTitle = "Chart Wizard";

        @Override
        public ModelAndView getView(ChartWizardReportForm form, BindException errors) throws Exception
        {
            form.setAllowToggleMode(true);

            // issue 27439: allow chart wizard report lookup by name if reportId not provided
            Report report = getReport(form);
            if (form.getReportId() == null && report != null && report.getDescriptor() != null)
                form.setReportId(report.getDescriptor().getReportId());

            JspView view = new JspView<>("/org/labkey/visualization/views/chartWizard.jsp", form);
            view.setTitle(_navTitle);
            view.setFrame(WebPartView.FrameType.NONE);

            if (report != null)
                _navTitle = report.getDescriptor().getReportName();

            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic("reportsAndViews");
            return root.addChild(_navTitle);
        }
    }

    @RequiresLogin @RequiresPermission(ReadPermission.class)
    public class SaveGenericReportAction extends ApiAction<ChartWizardReportForm>
    {
        @Override
        public void validateForm(ChartWizardReportForm form, Errors errors)
        {
            List<ValidationError> reportErrors = new ArrayList<>();

            if (form.getName() == null)
                errors.reject(ERROR_MSG, "A report name is required");

            if (form.getRenderType() == null)
                errors.reject(ERROR_MSG, "A report render type is required");

            try {
                // check for duplicates on new reports
                if (form.getReportId() == null)
                {
                    if (ReportUtil.doesReportNameExist(getContainer(), getUser(), form.getSchemaName(), form.getQueryName(), form.getName()))
                        errors.reject(ERROR_MSG, "Another report with the same name already exists.");

                    if (form.isPublic())
                    {
                        Report report = getGenericReport(form);
                        if (!report.canShare(getUser(), getContainer(), reportErrors))
                            ReportUtil.addErrors(reportErrors, errors);
                    }
                }
                else
                {
                    Report report = form.getReportId().getReport(getViewContext());

                    if (report != null)
                    {
                        if (!report.canEdit(getUser(), getContainer(), reportErrors))
                            ReportUtil.addErrors(reportErrors, errors);

                        if (form.isPublic() && !report.canShare(getUser(), getContainer(), reportErrors))
                            ReportUtil.addErrors(reportErrors, errors);
                    }
                }
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }

        @Override
        public ApiResponse execute(ChartWizardReportForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            String key = ReportUtil.getReportKey(form.getSchemaName(), form.getQueryName());
            Report report = getGenericReport(form);

            int rowId = ReportService.get().saveReport(getViewContext(), key, report);
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(String.valueOf(rowId));
            report = ReportService.get().getReport(getContainer(), rowId);
            saveSVGThumbnail((SvgThumbnailGenerator) report, form.getSvg(), form.getThumbnailType());
            response.put("success", true);
            response.put("reportId", reportId);

            return response;
        }

        private Report getGenericReport(ChartWizardReportForm form) throws Exception
        {
            Report report;

            if (form.getReportId() != null)
            {
                report = form.getReportId().getReport(getViewContext());

                // We don't want to mutate reports in the cache. It's bad general practice, plus it breaks hasContentModified()
                if (null != report)
                    report = report.clone();
            }
            else
            {
                report = ReportService.get().createReportInstance(GenericChartReport.TYPE);
            }

            if (report != null)
            {
                ReportDescriptor descriptor = report.getDescriptor();

                if (form.getName() != null)
                    descriptor.setReportName(form.getName());
                if (form.getDescription() != null)
                    descriptor.setReportDescription(form.getDescription());
                if (form.getSchemaName() != null)
                    descriptor.setProperty(ReportDescriptor.Prop.schemaName, form.getSchemaName());
                if (form.getQueryName() != null)
                    descriptor.setProperty(ReportDescriptor.Prop.queryName, form.getQueryName());
                if (form.getViewName() != null)
                    descriptor.setProperty(ReportDescriptor.Prop.viewName, form.getViewName());
                if (form.getDataRegionName() != null)
                    descriptor.setProperty(ReportDescriptor.Prop.dataRegionName, form.getDataRegionName());
                if (form.getRenderType() != null)
                    descriptor.setProperty(GenericChartReportDescriptor.Prop.renderType, form.getRenderType());
                if (form.getJsonData() != null)
                    descriptor.setProperty(ReportDescriptor.Prop.json, form.getJsonData());

                if (!form.isPublic())
                    descriptor.setOwner(getUser().getUserId());
                else
                    descriptor.setOwner(null);
            }
            return report;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public class GetGenericReportColumnsAction extends ApiAction<ColumnListForm>
    {
        @Override
        public ApiResponse execute(ColumnListForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            HashSet<String> baseColumns = new HashSet<>();
            HashSet<String> cohortColumns = new HashSet<>();
            HashSet<String> subjectGroupColumns = new HashSet<>();
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());

           if(schema != null)
            {
                TableInfo table = schema.getTable(form.getQueryName());
                QuerySettings querySettings = schema.getSettings(getViewContext(), form.getDataRegionName(), form.getQueryName(), form.getViewName());
                QueryView queryView = schema.createView(getViewContext(), querySettings, errors);

                if (table != null)
                {
                    Study study = StudyService.get().getStudy(getContainer());

                    if (study != null)
                    {
                        // We need the subject info from the study in order to set up the sort order for measure choices.
                        Map<String, String> subjectInfo = new HashMap<>();
                        subjectInfo.put("nounSingular", study.getSubjectNounSingular());
                        subjectInfo.put("nounPlural", study.getSubjectNounPlural());
                        subjectInfo.put("column", study.getSubjectColumnName());
                        response.put("subject", subjectInfo);

                        if (form.isIncludeCohort())
                        {
                            FieldKey cohort = FieldKey.fromParts(study.getSubjectColumnName(), "Cohort");
                            cohortColumns.add(cohort.toString());
                        }

                        if (form.isIncludeParticipantCategory())
                        {
                            for (ParticipantCategory category : study.getParticipantCategories(getUser()))
                            {
                                FieldKey group = FieldKey.fromParts(study.getSubjectColumnName(), category.getLabel());
                                subjectGroupColumns.add(group.toString());
                            }
                        }
                    }
                }

                if (queryView != null)
                {
                    for (DisplayColumn column : queryView.getDisplayColumns()){
                        ColumnInfo colInfo = column.getColumnInfo();
                        if (colInfo != null)
                        {
                            baseColumns.add(colInfo.getFieldKey().toString());
                        }
                    }
                }
            }

            Map<String, HashSet<String>> columns = new HashMap<>();
            columns.put("base", baseColumns);
            columns.put("cohort", cohortColumns);
            columns.put("subjectGroup", subjectGroupColumns);

            // keep for backwards compatibility
            HashSet<String> allColumns = new HashSet<>();
            allColumns.addAll(baseColumns);
            allColumns.addAll(cohortColumns);
            allColumns.addAll(subjectGroupColumns);
            columns.put("all", allColumns);

            response.put("columns", columns);
            return response;
        }
    }

    public static class ColumnListForm extends ReportUtil.JsonReportForm
    {
        private boolean _includeCohort;
        private boolean _includeParticipantCategory;
        private String _dataRegionName;

        public String getDataRegionName()
        {
            return _dataRegionName;
        }

        public void setDataRegionName(String dataRegionName)
        {
            _dataRegionName = dataRegionName;
        }

        public boolean isIncludeCohort()
        {
            return _includeCohort;
        }

        public void setIncludeCohort(boolean includeCohort)
        {
            _includeCohort = includeCohort;
        }

        public boolean isIncludeParticipantCategory()
        {
            return _includeParticipantCategory;
        }

        public void setIncludeParticipantCategory(boolean includeParticipantCategory)
        {
            _includeParticipantCategory = includeParticipantCategory;
        }
    }

    public static class ChartWizardReportForm extends ReportUtil.JsonReportForm
    {
        private String _renderType;
        private String _dataRegionName;
        private String _jsonData;
        private String _autoColumnName;
        private String _autoColumnYName;
        private String _autoColumnXName;
        private String _svg;
        private String _thumbnailType;
        private boolean _allowToggleMode = false; // view vs. edit mode

        public String getRenderType()
        {
            return _renderType;
        }

        public void setRenderType(String renderType)
        {
            _renderType = renderType;
        }

        public String getDataRegionName()
        {
            return _dataRegionName;
        }

        public void setDataRegionName(String dataRegionName)
        {
            _dataRegionName = dataRegionName;
        }

        public String getJsonData()
        {
            return _jsonData;
        }

        public void setJsonData(String jsonData)
        {
            _jsonData = jsonData;
        }

        public String getAutoColumnName()
        {
            return _autoColumnName;
        }

        public void setAutoColumnName(String autoColumnName)
        {
            _autoColumnName = autoColumnName;
        }

        public String getAutoColumnYName()
        {
            return _autoColumnYName;
        }

        public void setAutoColumnYName(String autoColumnYName)
        {
            _autoColumnYName = autoColumnYName;
        }

        public String getAutoColumnXName()
        {
            return _autoColumnXName;
        }

        public void setAutoColumnXName(String autoColumnXName)
        {
            _autoColumnXName = autoColumnXName;
        }

        public String getSvg()
        {
            return _svg;
        }

        public void setSvg(String svg)
        {
            _svg = svg;
        }

        public String getThumbnailType()
        {
            if (_thumbnailType == null)
                _thumbnailType = ThumbnailType.AUTO.name();
            return _thumbnailType;
        }

        public void setThumbnailType(String thumbnailType)
        {
            _thumbnailType = thumbnailType;
        }

        public boolean allowToggleMode()
        {
            return _allowToggleMode;
        }

        public void setAllowToggleMode(boolean allowToggleMode)
        {
            _allowToggleMode = allowToggleMode;
        }        

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            super.bindProperties(props);

            _renderType = (String)props.get("renderType");
            _dataRegionName = (String)props.get("dataRegionName");
            _svg = (String)props.get("svg");
            _thumbnailType = (String)props.get("thumbnailType");

            Object json = props.get("jsonData");
            if (json != null)
                _jsonData = json.toString();
        }
    }

    private void saveSVGThumbnail(SvgThumbnailGenerator generator, String svg, String thumbnailType) throws Exception
    {
        ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

        if (null != svc)
        {
            if (thumbnailType.equals(ThumbnailType.NONE.name()))
            {
                // User checked the "no thumbnail" checkbox... need to proactively delete the thumbnail
                svc.deleteThumbnail(generator, ImageType.Large);
                ReportPropsManager.get().setPropertyValue(generator.getEntityId(), getContainer(), "thumbnailType", ThumbnailType.NONE.name());
            }
            else if (thumbnailType.equals(ThumbnailType.AUTO.name()) && svg != null)
            {
                // Generate and save the thumbnail (in the background)
                generator.setSvg(svg);
                svc.queueThumbnailRendering(generator, ImageType.Large, ThumbnailType.AUTO);
                ReportPropsManager.get().setPropertyValue(generator.getEntityId(), getContainer(), "thumbnailType", ThumbnailType.AUTO.name());
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class GetSourceCountsAction extends ApiAction<SourceCountForm>
    {
        @Override
        public ApiResponse execute(SourceCountForm form, BindException errors) throws Exception
        {
            JSONObject json = form.getProps();
            JSONArray members = !json.isNull("members") ? json.getJSONArray("members") : null;
            JSONArray sources = json.getJSONArray("sources");
            String schemaName = json.getString("schema");
            String colName = json.getString("colName");

            UserSchema userSchema = QueryService.get().getUserSchema(getUser(), getContainer(), schemaName);
            if (userSchema == null)
            {
                throw new IllegalArgumentException("No such schema: " + schemaName);
            }

            VisualizationProvider provider = userSchema.createVisualizationProvider();
            if (provider == null)
            {
                throw new IllegalArgumentException("No provider available for schema: " + userSchema.getSchemaPath());
            }

            ApiResponseWriter writer = new ApiJsonWriter(getViewContext().getResponse());
            writer.startResponse();

            writer.startMap("counts");
            ResultSet rs = null;
            try
            {
                rs = QueryService.get().select(userSchema, provider.getSourceCountSql(sources, members, colName));

                Map<String, Integer> values = new HashMap<>();
                while (rs.next())
                {
                    values.put(rs.getString("label"), rs.getInt("value"));
                }

                String key;
                for (int i=0; i < sources.length(); i++)
                {
                    key = sources.getString(i);
                    if (values.containsKey(key))
                        writer.writeProperty(key, values.get(key));
                    else
                        writer.writeProperty(key, 0);
                }
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
            writer.endMap();
            writer.endResponse();

            return null;
        }
    }

    public static class SourceCountForm implements CustomApiForm
    {
        private Map<String, Object> _props;

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            _props = props;
        }

        public JSONObject getProps()
        {
            return (JSONObject) _props;
        }
    }






    public static class TestCase extends Assert
    {
        @Test
        public void testJacksonBinding() throws Exception
        {
            ObjectReader r = new ObjectMapper().reader(VisDataRequest.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            String measure1 = "{\"allowNullResults\":true, \"aggregate\":\"MAX\", \"alias\":\"table_column\", " +
                    "\"inNotNullSet\":true, \"name\":\"column\", \"nsvalues\":\"whatisthis\"," +
                    "\"queryName\":\"table\", \"requireLeftJoin\":true, \"schemaName\":\"schema\", \"values\":[1,2,3]}";
            String measure2 = "{\"allowNullResults\":false, \"aggregate\":\"MAX\", \"alias\":\"table_column2\", " +
                    "\"inNotNullSet\":false, \"name\":\"column2\"," +
                    "\"queryName\":\"table\", \"requireLeftJoin\":false, \"schemaName\":\"schema\"}";
            String measure3 = "{\"alias\":\"table_column3\", " +
                    "\"name\":\"column3\", \"notafieldICareAbout\":[2]," +
                    "\"queryName\":\"table\", \"schemaName\":\"schema\", \"values\":[\"a\",\"b\"]}";
            String measure4 = "{\"alias\":\"dem_column4\", " +
                    "\"name\":\"column4\", \"notafieldICareAbout\":[2]," +
                    "\"queryName\":\"dem\", \"schemaName\":\"schema\"}";
            String measureDateCol = "{\"alias\":\"table_visitday\", " +
                    "\"name\":\"visitday\", \"notafieldICareAbout\":[2]," +
                    "\"queryName\":\"table\", \"schemaName\":\"schema\"}";
            String measureStartDateCol = "{\"alias\":\"dem_enrolldate\", " +
                    "\"name\":\"enrolldate\", \"notafieldICareAbout\":[2]," +
                    "\"queryName\":\"dem\", \"schemaName\":\"schema\"}";

            String dateOptions = "{\"interval\":\"day\", \"dateCol\":" + measureDateCol + ", \"zeroDateCol\":"+ measureStartDateCol +", \"zeroDayVisitTag\":\"ZERO\", \"useProtocolDay\":false}";
            String jsonA =
                    "{" +
                            "\"ignoreMePlease\":true," +
                            "\"metaDataOnly\":true, " +
                            "\"joinToFirst\":true, " +
                            "\"measures\":[{\"measure\":" + measure1 + ", \"dimension\":" + measure2 + ", \"dateOptions\":"+dateOptions+", \"filterArray\":[] }], " +
                            "\"sorts\":[" + measure3 + "], " +
                            "\"limit\":99, " +
                            "\"filterUrl\":\"x=5\", " +
                            "\"filterQuery\":\"y=7\", " +
                            "\"groupBys\":[" + measure4 + "] " +
                            "}";

            VisDataRequest vs = r.readValue(jsonA);
            assertNotNull(vs);
            assertTrue(vs.isMetaDataOnly());
            assertTrue(vs.isJoinToFirst());
            assertEquals(1, vs.getMeasures().size());
            VisDataRequest.MeasureInfo mi = vs.getMeasures().get(0);
            {
                VisDataRequest.Measure m = mi.getMeasure();
                assertNotNull(m);
                assertFalse(m.isEmpty());
                assertTrue(m.getAllowNullResults());
                assertEquals("MAX", m.getAggregate());
                assertEquals("table_column", m.getAlias());
                assertTrue(m.getInNotNullSet());
                assertEquals("column", m.getName());
                assertEquals("whatisthis", m.getNsvalues());
                assertEquals("table", m.getQueryName());
                assertTrue(m.getRequireLeftJoin());
                assertEquals("schema", m.getSchemaName());
                List<Object> values = m.getValues();
                assertNotNull(values);
                assertEquals(3, values.size());
                assertEquals("1", values.get(0).toString());
                assertEquals(2, ((Number) values.get(1)).intValue());
                assertEquals("3", values.get(2).toString());
            }
            {
                VisDataRequest.Measure m = mi.getDimension();
                assertNotNull(m);
                assertFalse(m.isEmpty());
                assertFalse(m.getAllowNullResults());
                assertFalse(m.getInNotNullSet());
                assertFalse(m.getRequireLeftJoin());
            }
            VisDataRequest.DateOptions dopt = mi.getDateOptions();
            assertNotNull(dopt);
            assertEquals("day",dopt.getInterval());
            assertEquals("visitday", dopt.getDateCol().getName());
            assertEquals("enrolldate", dopt.getZeroDateCol().getName());
            assertEquals("ZERO", dopt.getZeroDayVisitTag());
            assertFalse(dopt.isUseProtocolDay());
            assertEquals(0,mi.getFilterArray().size());
            assertEquals(1, vs.getSorts().size());
            assertNotNull(vs.getLimit());
            assertEquals(99,vs.getLimit().intValue());
            assertEquals("x=5",vs.getFilterUrl());
            assertEquals("y=7",vs.getFilterQuery());
            assertEquals(1,vs.getGroupBys().size());
        }
    } /* TestCase */

}
