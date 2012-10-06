/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.svg.PDFTranscoder;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.ExtendedApiQueryResponse;
import org.labkey.api.action.RedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.attachments.DocumentConversionService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ShowRows;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySchema;
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
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.DataSetTable;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.ParticipantCategory;
import org.labkey.api.study.Visit;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UniqueID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.visualization.GenericChartReport;
import org.labkey.api.visualization.GenericChartReportDescriptor;
import org.labkey.api.visualization.SvgThumbnailGenerator;
import org.labkey.api.visualization.VisualizationReportDescriptor;
import org.labkey.api.visualization.VisualizationUrls;
import org.labkey.visualization.report.TimeChartReportImpl;
import org.labkey.visualization.sql.StudyVisualizationProvider;
import org.labkey.visualization.sql.VisualizationProvider;
import org.labkey.visualization.sql.VisualizationSQLGenerator;
import org.labkey.visualization.sql.VisualizationSourceColumn;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
        private static final String VISUALIZATION_SCHEMA_PARAM = "schemaName";
        private static final String VISUALIZATION_FILTER_URL = "filterUrl";
        private static final String VISUALIZATION_QUERY_PARAM = "queryName";
        private static final String VISUALIZATION_EDIT_PARAM = "edit";
        private static final String VISUALIZATION_ID_PARAM = "reportId";

        @Override
        public ActionURL getTimeChartDesignerURL(Container container)
        {
            return getBaseTimeChartURL(container, true);
        }

        @Override
        public ActionURL getTimeChartDesignerURL(Container container, User user, QuerySettings settings)
        {
            ActionURL url = getBaseTimeChartURL(container, true);
            addQueryParams(url, container, user, settings);

            return url;
        }

        protected void addQueryParams(ActionURL url, Container container, User user, QuerySettings settings)
        {
            String queryName = settings.getQueryName();
            if (queryName != null)
                url.addParameter(VISUALIZATION_QUERY_PARAM, queryName);
            String schemaName = settings.getSchemaName();
            if (schemaName != null)
                url.addParameter(VISUALIZATION_SCHEMA_PARAM, schemaName);

            // Get URL (column-header) filters:
            ActionURL filterURL = settings.getSortFilterURL();

            // Add the base filter as set in code:
            SimpleFilter baseFilter = settings.getBaseFilter();
            baseFilter.applyToURL(filterURL, FILTER_DATAREGION);

            // Finally, add view-level filters:
            CustomView view = QueryService.get().getCustomView(user, container, settings.getSchemaName(), settings.getQueryName(), settings.getViewName());
            if (view != null)
                view.applyFilterAndSortToURL(filterURL, FILTER_DATAREGION);

            url.addParameter(QueryParam.dataRegionName, settings.getDataRegionName());
            url.addParameter(VISUALIZATION_FILTER_URL, filterURL.getLocalURIString());
        }

        @Override
        public ActionURL getTimeChartDesignerURL(Container container, Report report)
        {
            ActionURL url = getBaseTimeChartURL(container, true);
            String queryName = report.getDescriptor().getProperty(ReportDescriptor.Prop.queryName);
            if (queryName != null)
                url.addParameter(VISUALIZATION_QUERY_PARAM, queryName);
            String schemaName = report.getDescriptor().getProperty(ReportDescriptor.Prop.schemaName);
            if (schemaName != null)
                url.addParameter(VISUALIZATION_SCHEMA_PARAM, schemaName);
            url.addParameter(VISUALIZATION_ID_PARAM, report.getDescriptor().getReportId().toString());
            url.addParameter(VISUALIZATION_NAME_PARAM, report.getDescriptor().getReportName());
            return url;
        }

        @Override
        public ActionURL getViewerURL(Container container, Report report)
        {
            ActionURL url = getBaseTimeChartURL(container, false);
            String queryName = report.getDescriptor().getProperty(ReportDescriptor.Prop.queryName);
            if (queryName != null)
                url.addParameter(VISUALIZATION_QUERY_PARAM, queryName);
            String schemaName = report.getDescriptor().getProperty(ReportDescriptor.Prop.schemaName);
            if (schemaName != null)
                url.addParameter(VISUALIZATION_SCHEMA_PARAM, schemaName);
            url.addParameter(VISUALIZATION_ID_PARAM, report.getDescriptor().getReportId().toString());
            url.addParameter(VISUALIZATION_NAME_PARAM, report.getDescriptor().getReportName());
            return url;
        }

        private ActionURL getBaseTimeChartURL(Container container, boolean editMode)
        {
            ActionURL url = new ActionURL(TimeChartWizardAction.class, container);
            url.addParameter(VISUALIZATION_EDIT_PARAM, editMode);
            return url;
        }

        private ActionURL getBaseGenericChartURL(Container container, boolean editMode)
        {
            ActionURL url = new ActionURL(GenericChartWizardAction.class, container);
            url.addParameter(VISUALIZATION_EDIT_PARAM, editMode);
            return url;
        }

        @Override
        public ActionURL getGenericChartDesignerURL(Container container, User user, @Nullable QuerySettings settings, GenericChartReport.RenderType type)
        {
            ActionURL url = getBaseGenericChartURL(container, true);

            if (settings != null)
            {
                addQueryParams(url, container, user, settings);
            }
            url.addParameter(GenericChartReportDescriptor.Prop.renderType, type.getId());

            return url;
        }
    }


    public VisualizationController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    public static class DimensionsForm extends MeasuresForm
    {
        private boolean _includeDemographics;

        public boolean isIncludeDemographics()
        {
            return _includeDemographics;
        }

        public void setIncludeDemographics(boolean includeDemographics)
        {
            _includeDemographics = includeDemographics;
        }
    }

    public static class DimensionValuesForm extends MeasuresForm
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

    public static class MeasuresForm
    {
        private String[] _filters = new String[0];
        private String _schemaName;
        private String _queryName;
        private String _name;
        private boolean _dateMeasures;
        private boolean _allColumns;
        private boolean _showHidden;

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

        public String[] getFilters()
        {
            return _filters;
        }

        public void setFilters(String[] filters)
        {
            _filters = filters;
        }

        public boolean isDateMeasures()
        {
            return _dateMeasures;
        }

        public void setDateMeasures(boolean dateMeasures)
        {
            _dateMeasures = dateMeasures;
        }

        public boolean isAllColumns()
        {
            return _allColumns;
        }

        public void setAllColumns(boolean allColumns)
        {
            _allColumns = allColumns;
        }

        public boolean isShowHidden()
        {
            return _showHidden;
        }

        public void setShowHidden(boolean showHidden)
        {
            _showHidden = showHidden;
        }
    }

    public static Map<String, ? extends VisualizationProvider> createVisualizationProviders()
    {
        return Collections.singletonMap("study", new StudyVisualizationProvider());
    }

    public static VisualizationProvider createVisualizationProvider(String schemaName)
    {
        if ("study".equalsIgnoreCase(schemaName))
            return new StudyVisualizationProvider();
        else
            throw new IllegalArgumentException("No visualization provider registered for schema: " + schemaName);
    }

    public enum QueryType {
        builtIn,
        custom,
        datasets,
        all,
    }

    public static class MeasureFilter
    {
        private String _schema;
        private String _query;
        private QueryType _queryType = QueryType.all;

        public MeasureFilter(String filter)
        {
            parse(filter);
        }

        protected void parse(String filter)
        {
            String[] parts = filter.split("\\|");

            assert(parts.length >= 2) : "Invalid filter value";

            _schema = parts[0];

            if (!parts[1].equals("~"))
                _query = parts[1];

            if (parts.length >= 3)
                _queryType = QueryType.valueOf(parts[2]);
        }

        public String getSchema()
        {
            return _schema;
        }

        public String getQuery()
        {
            return _query;
        }

        public QueryType getQueryType()
        {
            return _queryType;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends RedirectAction
    {
        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return PageFlowUtil.urlProvider(VisualizationUrls.class).getTimeChartDesignerURL(getContainer());
        }

        @Override
        public boolean doAction(Object o, BindException errors) throws Exception
        {
            return true;
        }

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }
    }


    private static boolean isDemographicQueryDefinition(QueryDefinition q)
    {
        if (!StringUtils.equalsIgnoreCase("study", q.getSchemaName()) || !q.isTableQueryDefinition())
            return false;

        try
        {
            TableInfo t = q.getTable(null, false);
            if (!(t instanceof DataSetTable))
                return false;
            return ((DataSetTable)t).getDataSet().isDemographicData();
        }
        catch (QueryException qe)
        {
            return false;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class GetMeasuresAction<Form extends MeasuresForm> extends ApiAction<Form>
    {
        public ApiResponse execute(Form form, BindException errors) throws Exception
        {
            Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> measures = new HashMap<Pair<FieldKey, ColumnInfo>, QueryDefinition>();
            if (form.getFilters() != null && form.getFilters().length > 0)
            {
                for (String filter : form.getFilters())
                {
                    MeasureFilter mf = new MeasureFilter(filter);
                    VisualizationProvider provider = createVisualizationProvider(mf.getSchema());
                    if (provider == null)
                    {
                        errors.reject(ERROR_MSG, "No measure provider found for schema " + mf.getSchema());
                        return null;
                    }

                    if (form.isDateMeasures())
                    {
                        if (mf.getQuery() != null)
                            measures.putAll(provider.getDateMeasures(getViewContext(), mf.getQuery()));
                        else
                            measures.putAll(provider.getDateMeasures(getViewContext(), mf.getQueryType()));

                    }
                    else if (form.isAllColumns())
                    {
                        if (mf.getQuery() != null)
                            measures.putAll(provider.getAllColumns(getViewContext(), mf.getQuery(), form.isShowHidden()));
                        else
                            measures.putAll(provider.getAllColumns(getViewContext(), mf.getQueryType(), form.isShowHidden()));
                    }
                    else
                    {
                        if (mf.getQuery() != null)
                            measures.putAll(provider.getMeasures(getViewContext(), mf.getQuery()));
                        else
                            measures.putAll(provider.getMeasures(getViewContext(), mf.getQueryType()));
                    }
                }
            }
            else
            {
                // get all tables in this container
                for (VisualizationProvider provider : createVisualizationProviders().values())
                {
                    if (form.isDateMeasures())
                        measures.putAll(provider.getDateMeasures(getViewContext(), QueryType.all));
                    else
                        measures.putAll(provider.getMeasures(getViewContext(), QueryType.all));
                }
            }

            ApiSimpleResponse resp = new ApiSimpleResponse();
            List<Map<String, Object>> measuresJSON = getColumnResponse(measures);
            resp.put("success", true);
            resp.put("measures", measuresJSON);

            return resp;
        }

        protected List<Map<String, Object>> getColumnResponse(Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> cols)
        {
            List<Map<String, Object>> measuresJSON = new ArrayList<Map<String, Object>>();
            int count = 1;
            for (Map.Entry<Pair<FieldKey, ColumnInfo>, QueryDefinition> entry : cols.entrySet())
            {
                QueryDefinition query = entry.getValue();
                boolean isDemographic = isDemographicQueryDefinition(query);
                // add measure properties
                FieldKey fieldKey = entry.getKey().first;
                ColumnInfo column = entry.getKey().second;
                Map<String, Object> props = getColumnProps(fieldKey, column, query);
                props.put("schemaName", query.getSchema().getName());
                props.put("queryName", query.getName());
                props.put("isUserDefined", !query.isTableQueryDefinition());
                props.put("isDemographic", isDemographic);
                props.put("id", count++);

                measuresJSON.add(props);
            }
            return measuresJSON;
        }

        protected Map<String, Object> getColumnProps(FieldKey fieldKey, ColumnInfo col, QueryDefinition query)
        {
            Map<String, Object> props = new HashMap<String, Object>();

            props.put("name", fieldKey.toString());
            props.put("label", col.getLabel());
            List<QueryException> errors = new ArrayList<QueryException>();
            TableInfo table = query.getTable(errors, false);
            String queryName;
            if (table instanceof DataSetTable && errors.isEmpty())
                queryName = ((DataSetTable) table).getDataSet().getLabel();
            else
                queryName = query.getName();
            props.put("longlabel", col.getLabel() + " (" + queryName + ")");
            props.put("type", col.getJdbcType().name());
            props.put("description", StringUtils.trimToEmpty(col.getDescription()));
            props.put("alias", VisualizationSourceColumn.getAlias(query.getSchemaName(), query.getName(), col.getName()));

            return props;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetDimensionsAction extends GetMeasuresAction<DimensionsForm>
    {
        public ApiResponse execute(DimensionsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            if (form.getSchemaName() != null && form.getQueryName() != null)
            {
                VisualizationProvider provider = createVisualizationProvider(form.getSchemaName());
                if (provider == null)
                {
                    errors.reject(ERROR_MSG, "No measure provider found for schema " + form.getSchemaName());
                    return null;
                }
                Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> dimensions = provider.getDimensions(getViewContext(), form.getQueryName());
                List<Map<String, Object>> dimensionJSON = getColumnResponse(dimensions);
                resp.put("success", true);
                resp.put("dimensions", dimensionJSON);
            }
            else
                throw new IllegalArgumentException("schemaName and queryName are required parameters");

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetDimensionValues extends GetMeasuresAction<DimensionValuesForm>
    {
        public ApiResponse execute(DimensionValuesForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            if (form.getName() != null && form.getSchemaName() != null && form.getQueryName() != null)
            {
                QuerySchema schema = DefaultSchema.get(getUser(), getContainer()).getSchema(form.getSchemaName());
                List<Map<String, String>> values = new ArrayList<Map<String, String>>();

                if (schema instanceof UserSchema)
                {
                    UserSchema uschema = (UserSchema)schema;
                    TableInfo tinfo = uschema.getTable(form.getQueryName());

                    if (tinfo != null)
                    {
                        FieldKey dimensionKey = FieldKey.fromString(form.getName());
                        Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(tinfo, Collections.singleton(dimensionKey));
                        ColumnInfo col = cols.get(dimensionKey);
                        if (col != null)
                        {
                            Table.TableResultSet rs = null;
                            try {
                                SimpleFilter filter = null;
                                String filterUrlString = form.getFilterUrl();
                                if (filterUrlString != null)
                                {
                                    ActionURL filterUrl = new ActionURL(filterUrlString);
                                    filter = new SimpleFilter(filterUrl, VisualizationController.FILTER_DATAREGION);
                                }
                                SQLFragment sql = QueryService.get().getSelectSQL(tinfo, Collections.singleton(col), filter, null, Table.ALL_ROWS, Table.NO_OFFSET, false);

                                rs = Table.executeQuery(uschema.getDbSchema(), sql.getSQL().replaceFirst("SELECT", "SELECT DISTINCT"), sql.getParamsArray());

                                while (rs.next())
                                {
                                    Object o = rs.getObject(1);
                                    if (o != null)
                                        values.add(Collections.singletonMap("value", ConvertUtils.convert(o)));
                                }
                            }
                            finally
                            {
                                ResultSetUtil.close(rs);
                            }
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

    @RequiresPermissionClass(ReadPermission.class)
    public class GetVisualizationTypes extends ApiAction
    {
        Map<String, Object> getBaseTypeProperties(Study study)
        {
            Map<String, Object> properties = new HashMap<String, Object>();
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

            List<Map<String, Object>> types = new ArrayList<Map<String, Object>>();
/*  NOT SUPPORTED FOR 10.3
            // motion chart
            Map<String, Object> motion = getBaseTypeProperties();
            motion.put("type", "motion");
            motion.put("label", "Motion Chart");
            motion.put("icon", getViewContext().getContextPath() + "/reports/output_motionchart.jpg");
            types.add(motion);
*/
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

            List<Map<String, String>> lineAxis = new ArrayList<Map<String, String>>();
            lineAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select the date measurement for the x-axis", "multiSelect", "false", "timeAxis", "true"));
            lineAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            line.put("axis", lineAxis);

            line.put("enabled", true);
            types.add(line);

/*  NOT SUPPORTED FOR 10.3
            // scatter chart
            Map<String, Object> scatter = getBaseTypeProperties();
            scatter.put("type", "scatter");
            scatter.put("label", "Scatter Plot");
            scatter.put("icon", getViewContext().getContextPath() + "/reports/output_scatterplot.jpg");

            List<Map<String, String>> scatterAxis = new ArrayList<Map<String, String>>();
            scatterAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select data type for x-axis", "multiSelect", "false"));
            scatterAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            scatter.put("axis", scatterAxis);

            scatter.put("enabled", true);
            types.add(scatter);
*/
            // data grid
            Map<String, Object> data = getBaseTypeProperties(study);
            data.put("type", "dataGridTime");
            data.put("label", "Data Grid (Time)");
            data.put("icon", getViewContext().getContextPath() + "/reports/output_grid.jpg");

            List<Map<String, String>> dataAxis = new ArrayList<Map<String, String>>();
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

            List<Map<String, String>> dataScatterAxis = new ArrayList<Map<String, String>>();
            dataScatterAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select data type for x-axis", "multiSelect", "false"));
            dataScatterAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            dataScatter.put("axis", dataScatterAxis);
            dataScatter.put("isGrid", true);
            dataScatter.put("enabled", true);

            types.add(dataScatter);
/*  NOT SUPPORTED FOR 10.3
            // excel data export
            Map<String, Object> excel = getBaseTypeProperties();
            excel.put("type", "excelExport");
            excel.put("label", "Excel Data Export");
            excel.put("icon", getViewContext().getContextPath() + "/reports/output_excel.jpg");
            types.add(excel);

            // TSV data export
            Map<String, Object> tsv = getBaseTypeProperties();
            tsv.put("type", "tsvExport");
            tsv.put("label", "Tab-delimited Data Export");
            tsv.put("icon", getViewContext().getContextPath() + "/reports/output_text.jpg");
            types.add(tsv);
*/
            resp.put("types", types);

            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetDataAction extends ApiAction<VisualizationSQLGenerator>
    {
        @Override
        public ApiResponse execute(VisualizationSQLGenerator sqlGenerator, BindException errors) throws Exception
        {
            String sql;
            try
            {
                sql = sqlGenerator.getSQL();
            }
            catch (VisualizationSQLGenerator.GenerationException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                _log.warn("Unable to generate visualization SQL.", e);
                return null;
            }

            ApiQueryResponse response = getApiResponse(getViewContext(), sqlGenerator.getPrimarySchema(), sql, sqlGenerator.isMetaDataOnly(), sqlGenerator.getSort(), errors);

            // Note: extra properties can only be gathered after the query has executed, since execution populates the name maps.
            Map<String, Object> extraProperties = new HashMap<String, Object>();
            extraProperties.put("measureToColumn", sqlGenerator.getColumnMapping());
            extraProperties.put("columnAliases", sqlGenerator.getColumnAliases());
            extraProperties.put("visitMap", getVisitMetaData());
            String filterDescription = sqlGenerator.getFilterDescription();
            if (filterDescription != null && filterDescription.length() > 0)
                extraProperties.put("filterDescription", filterDescription);
            response.setExtraReturnProperties(extraProperties);

            return response;
        }

        private Map<String, Map<String, Object>> getVisitMetaData()
        {
            Map<String, Map<String, Object>> metaData = new HashMap<String, Map<String, Object>>();
            Study study = StudyService.get().getStudy(getContainer());

            int i=1;
            for (Visit visit : study.getVisits(Visit.Order.DISPLAY))
            {
                Map<String, Object> visitInfo = new HashMap<String, Object>();

                visitInfo.put("displayOrder", i++);
                visitInfo.put("displayName", visit.getDisplayString());
                visitInfo.put("sequenceNumMin", visit.getSequenceNumMin());
                visitInfo.put("sequenceNumMax", visit.getSequenceNumMax());

                metaData.put(visit.getId().toString(), visitInfo);
            }
            return metaData;
        }

        private ApiQueryResponse getApiResponse(ViewContext context, UserSchema schema, String sql, boolean metaDataOnly, Sort sort, BindException errors) throws Exception
        {
            String schemaName = schema.getName();
            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query

            QueryDefinition def = QueryService.get().saveSessionQuery(context, context.getContainer(), schemaName, sql);

            QuerySettings settings = new QuerySettings(getViewContext(), "visualization", def.getName());

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseQuery(false);
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

            return new ExtendedApiQueryResponse(view, getViewContext(), false,
                    false, schemaName, def.getName(), 0, null, metaDataOnly);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetZeroDateAction extends GetMeasuresAction<MeasuresForm>
    {
        public ApiResponse execute(MeasuresForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> measures = new HashMap<Pair<FieldKey, ColumnInfo>, QueryDefinition>();
            if (form.getFilters() != null && form.getFilters().length > 0)
            {
                for (String filter : form.getFilters())
                {
                    MeasureFilter mf = new MeasureFilter(filter);
                    VisualizationProvider provider = createVisualizationProvider(mf.getSchema());
                    if (provider == null)
                    {
                        errors.reject(ERROR_MSG, "No measure provider found for schema " + mf.getSchema());
                        return null;
                    }
                    measures.putAll(provider.getZeroDateMeasures(getViewContext(), mf.getQueryType()));
                }
            }
            else
            {
                // get all tables in this container
                for (VisualizationProvider provider : createVisualizationProviders().values())
                    measures.putAll(provider.getZeroDateMeasures(getViewContext(), QueryType.all));
            }

            List<Map<String, Object>> measuresJSON = getColumnResponse(measures);
            resp.put("success", true);
            resp.put("measures", measuresJSON);

            return resp;
        }
    }

    /**
     * Expects an HTTP post with no parameters, the post body carrying an SVG XML document.
     * Alternately a form-encoded post with a parameter called svg to allow JavaScript clients to access it
     */
    @RequiresPermissionClass(ReadPermission.class)
    abstract class ExportSVGAction extends BaseViewAction
    {
        String _svgSource;

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
                _svgSource = PageFlowUtil.getStreamContentsAsString(request.getInputStream());
            else
                _svgSource = request.getParameter("svg");

            return filterSVGSource(_svgSource);
        }
    }

    /**
     * Expects an HTTP post with no parameters, the post body carrying an SVG XML document.
     * Content-type of request must be text/xml, not any kind of multipart
     * Returns a PNG image.
     */
    @RequiresPermissionClass(ReadPermission.class)
    public class ExportImageAction extends ExportSVGAction
    {
        @Override
        public ModelAndView handleRequest() throws Exception
        {
            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("image/png");
            response.addHeader("Content-Disposition", "attachment; filename=visualization.png");

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
    @RequiresPermissionClass(ReadPermission.class)
    public class ExportPDFAction extends ExportSVGAction
    {
        @Override
        public ModelAndView handleRequest() throws Exception
        {
            HttpServletResponse response = getViewContext().getResponse();
            response.setContentType("application/pdf");
            response.addHeader("Content-Disposition", "attachment; filename=visualization.pdf");

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

    public static class SaveVisualizationForm extends GetVisualizationForm
    {
        private String _description;
        private String _json;
        private String _type;
        private boolean _replace;
        private boolean _shared = true;
        private String _thumbnailType;
        private String _svg;

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
                _thumbnailType = DataViewProvider.EditInfo.ThumbnailType.AUTO.name();
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
    }

    public static class GetVisualizationForm
    {
        private String _schemaName;
        private String _queryName;
        private String _name;
        private String _renderType;
        private ReportIdentifier _reportId;
        private boolean _allowToggleMode = false; // edit vs. view mode

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

        public String getRenderType()
        {
            return _renderType;
        }

        public void setRenderType(String renderType)
        {
            _renderType = renderType;
        }

        public boolean allowToggleMode()
        {
            return _allowToggleMode;
        }

        public void setAllowToggleMode(boolean allowToggleMode)
        {
            _allowToggleMode = allowToggleMode;
        }
    }

    private String getReportKey(GetVisualizationForm form)
    {
        String schema = form.getSchemaName();
        if (schema == null)
            schema = "none";
        String query = form.getQueryName();
        if (query == null)
            query = "none";
        return ReportUtil.getReportKey(schema, query);
    }

    private Report getReport(GetVisualizationForm form) throws SQLException
    {
        try {

            ReportIdentifier reportId = form.getReportId();
            if (reportId != null)
            {
                Report report = reportId.getReport(getViewContext());
                if (report != null)
                    return report;
            }

            // try to match on report name if we don't have a valid report identifier
            Report[] currentReports;

            if (form.getSchemaName() != null && form.getQueryName() != null)
                currentReports = ReportService.get().getReports(getUser(), getContainer(), getReportKey(form));
            else
                currentReports = ReportService.get().getReports(getUser(), getContainer());

            for (Report report : currentReports)
            {
                if (report.getDescriptor().getReportName() != null &&
                    report.getDescriptor().getReportName().equals(form.getName()))
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

    @RequiresPermissionClass(ReadPermission.class)
    public class GetVisualizationAction extends ApiAction<GetVisualizationForm>
    {
        private ReportDescriptor descriptor;

        @Override
        public void validateForm(GetVisualizationForm form, Errors errors)
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

            if (report.getDescriptor().getOwner() != null && report.getDescriptor().getOwner() != getUser().getUserId())
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
        public ApiResponse execute(GetVisualizationForm form, BindException errors) throws Exception
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
            resp.put("shared", vizDescriptor.getOwner() == null);
            resp.put("ownerId", vizDescriptor.getOwner() != null ? vizDescriptor.getOwner() : null);
            resp.put("createdBy", vizDescriptor.getCreatedBy());
            resp.put("reportProps", vizDescriptor.getReportProps());
            resp.put("thumbnailURL", PageFlowUtil.urlProvider(ReportUrls.class).urlThumbnail(getContainer(), getReport(form)));
            return resp;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
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

            try
            {
                _currentReport = getReport(form);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }

            if (_currentReport != null)
            {
                if(!_currentReport.canEdit(getUser(), getContainer()))
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
            ReportDescriptor descriptor = _currentReport.getDescriptor();
            if (!(descriptor instanceof VisualizationReportDescriptor))
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
            vizDescriptor.setReportKey(getReportKey(form));
            vizDescriptor.setJSON(form.getJson());
            vizDescriptor.setContainer(getContainer().getId());
            vizDescriptor.setReportDescription(form.getDescription());
            if (form.getSchemaName() != null)
                vizDescriptor.setProperty(ReportDescriptor.Prop.schemaName, form.getSchemaName());
            if (form.getQueryName() != null)
                vizDescriptor.setProperty(ReportDescriptor.Prop.queryName, form.getQueryName());
            if (_currentReport.getDescriptor().getReportId() != null)
                vizDescriptor.setReportId(_currentReport.getDescriptor().getReportId());
            vizDescriptor.setOwner(form.isShared() ? null : getViewContext().getUser().getUserId());
            int reportId = ReportService.get().saveReport(getViewContext(), vizDescriptor.getReportKey(), _currentReport);

            // Re-select the saved report to make sure it has an entityId
            Report report = ReportService.get().getReport(reportId);

            if (report instanceof SvgThumbnailGenerator)
            {
                ThumbnailService svc = ServiceRegistry.get().getService(ThumbnailService.class);

                if (null != svc)
                {
                    SvgThumbnailGenerator tcReport = (SvgThumbnailGenerator)report;
                    String svg = form.getSvg();

                    if (form.getThumbnailType().equals(DataViewProvider.EditInfo.ThumbnailType.NONE.name()))
                    {
                        // User checked the "no thumbnail" checkbox... need to proactively delete the thumbnail
                        svc.deleteThumbnail(tcReport);
                        ReportPropsManager.get().setPropertyValue(tcReport.getEntityId(), getContainer(), "thumbnailType", DataViewProvider.EditInfo.ThumbnailType.NONE.name());
                    }
                    else if (form.getThumbnailType().equals(DataViewProvider.EditInfo.ThumbnailType.AUTO.name()) && svg != null)
                    {
                        // Generate and save the thumbnail (in the background)
                        tcReport.setSvg(form.getSvg());
                        svc.queueThumbnailRendering(tcReport);
                        ReportPropsManager.get().setPropertyValue(tcReport.getEntityId(), getContainer(), "thumbnailType", DataViewProvider.EditInfo.ThumbnailType.AUTO.name());
                    }
                }
            }

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("visualizationId", reportId);
            resp.put("name", _currentReport.getDescriptor().getReportName());
            return resp;
        }
    }

    public static final String TITLE = "Time Chart Wizard";

    @RequiresPermissionClass(ReadPermission.class)
    public class TimeChartWizardAction extends SimpleViewAction<GetVisualizationForm>
    {
        String _navTitle = TITLE;

        @Override
        public ModelAndView getView(GetVisualizationForm form, BindException errors) throws Exception
        {
            form.setAllowToggleMode(true);
            JspView timeChartWizard = new JspView<GetVisualizationForm>("/org/labkey/visualization/views/timeChartWizard.jsp", form);

            timeChartWizard.setTitle(TITLE);
            timeChartWizard.setFrame(WebPartView.FrameType.NONE);

            VBox boxView = new VBox(timeChartWizard);

            Report report = getReport(form);

            if (report != null)
            {
                _navTitle = report.getDescriptor().getReportName();

                // check if the report is shared and if not, whether the user has access to the report
                if (report.getDescriptor().getOwner() == null || (report.getDescriptor().getOwner() != null && report.getDescriptor().getOwner() == getUser().getUserId()))
                {
                    String title = "Discuss report - " + report.getDescriptor().getReportName();
                    DiscussionService.Service service = DiscussionService.get();
                    HttpView discussion = service.getDisussionArea(getViewContext(), report.getEntityId(), getViewContext().getActionURL(), title, true, false);
                    boxView.addView(discussion);
                }
            }

            return boxView;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_navTitle);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GenericChartWizardAction extends SimpleViewAction<GenericReportForm>
    {
        private GenericChartReport.RenderType _renderType;

        @Override
        public ModelAndView getView(GenericReportForm form, BindException errors) throws Exception
        {
            form.setAllowToggleMode(true);
            _renderType = GenericChartReport.getRenderType(form.getRenderType());

            if (_renderType != null)
            {
                form.setComponentId("generic-report-panel-" + UniqueID.getRequestScopedUID(getViewContext().getRequest()));
                JspView view = new JspView<GenericReportForm>("/org/labkey/visualization/views/genericChartWizard.jsp", form);

                view.setTitle(_renderType.getName() + " Report");
                view.setFrame(WebPartView.FrameType.PORTAL);

                if (getViewContext().hasPermission(InsertPermission.class))
                {
                    NavTree menu = new NavTree();
                    menu.addChild("Manage Views", PageFlowUtil.urlProvider(ReportUrls.class).urlManageViews(getContainer()));
                    view.setNavMenu(menu);
                }
                return view;
            }
            else
                return new HtmlView("No renderer for specified type: " + PageFlowUtil.filter(form.getRenderType()));
/*
            Report report = getReport(form);

            if (report != null)
            {
                String title = "Discuss report - " + report.getDescriptor().getReportName();
                DiscussionService.Service service = DiscussionService.get();
                HttpView discussion = service.getDisussionArea(getViewContext(), report.getEntityId(), getViewContext().getActionURL(), title, true, false);
                view.addView(discussion);
            }
*/
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            if (null != _renderType)
                root.addChild(_renderType.getName() + " Report");
            return root;
        }
    }

    @RequiresLogin @RequiresPermissionClass(ReadPermission.class)
    public class SaveGenericReportAction extends ApiAction<GenericReportForm>
    {
        @Override
        public void validateForm(GenericReportForm form, Errors errors)
        {
            List<ValidationError> reportErrors = new ArrayList<ValidationError>();

            if (form.getName() == null)
                errors.reject(ERROR_MSG, "A report name is required");

            if (form.getRenderType() == null)
                errors.reject(ERROR_MSG, "A report render type is required");

            try {
                // check for duplicates on new reports
                if (form.getReportId() == null)
                {
                    String key = ReportUtil.getReportKey(form.getSchemaName(), form.getQueryName());
                    for (Report report : ReportService.get().getReports(getUser(), getContainer(), key))
                    {
                        if (form.getName().equalsIgnoreCase(report.getDescriptor().getReportName()))
                            errors.reject(ERROR_MSG, "Another report with the same name already exists.");
                    }

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
        public ApiResponse execute(GenericReportForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            String key = ReportUtil.getReportKey(form.getSchemaName(), form.getQueryName());
            Report report = getGenericReport(form);

            int rowId = ReportService.get().saveReport(getViewContext(), key, report);
            ReportIdentifier reportId = ReportService.get().getReportIdentifier(String.valueOf(rowId));

            response.put("success", true);
            response.put("reportId", reportId);

            return response;
        }

        private Report getGenericReport(GenericReportForm form) throws Exception
        {
            Report report;

            if (form.getReportId() != null)
                report = form.getReportId().getReport(getViewContext());
            else
                report = ReportService.get().createReportInstance(GenericChartReport.TYPE);

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

    @RequiresPermissionClass(ReadPermission.class)
    public class GetGenericReportAction extends ApiAction<GenericReportForm>
    {
        @Override
        public ApiResponse execute(GenericReportForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Report report = null;
            if (form.getReportId() != null)
                report = form.getReportId().getReport(getViewContext());

            if (report != null)
            {
                response.put("reportConfig", GenericReportForm.toJSON(getUser(), getContainer(), report));
                response.put("success", true);
            }
            else
                throw new IllegalStateException("Unable to find specified report");

            return response;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetGenericReportColumnsAction extends ApiAction<ColumnListForm>
    {
        @Override
        public ApiResponse execute(ColumnListForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
            List<String> columns = new ArrayList<String>();

            if (schema != null)
            {
                TableInfo table = schema.getTable(form.getQueryName());
                if (table != null)
                {
                    Study study = StudyService.get().getStudy(getContainer());

                    if (study != null)
                    {
                        if (form.isIncludeCohort())
                        {
                            FieldKey cohort = FieldKey.fromParts(study.getSubjectColumnName(), "Cohort");
                            columns.add(cohort.toString());
                        }

                        if (form.isIncludeParticipantCategory())
                        {
                            for (ParticipantCategory category : study.getParticipantCategories(getUser()))
                            {
                                FieldKey cohort = FieldKey.fromParts(study.getSubjectColumnName(), category.getLabel());
                                columns.add(cohort.toString());
                            }
                        }
                    }

                    if (form.isIncludeDefault())
                    {
                        for (FieldKey fk : table.getDefaultVisibleColumns())
                            columns.add(fk.toString());
                    }
                }
            }
            response.put("columns", columns);

            return response;
        }
    }

    public static class ColumnListForm extends ReportUtil.JsonReportForm
    {
        private boolean _includeDefault;
        private boolean _includeCohort;
        private boolean _includeParticipantCategory;

        public boolean isIncludeDefault()
        {
            return _includeDefault;
        }

        public void setIncludeDefault(boolean includeDefault)
        {
            _includeDefault = includeDefault;
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

    public static class GenericReportForm extends ReportUtil.JsonReportForm
    {
        private String _renderType;
        private String _dataRegionName;
        private String _jsonData;
        private String _autoColumnYName;
        private String _autoColumnXName;
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

            Object json = props.get("jsonData");
            if (json != null)
                _jsonData = json.toString();
        }

        public static JSONObject toJSON(User user, Container container, Report report)
        {
            JSONObject json = ReportUtil.JsonReportForm.toJSON(user, container, report);
            ReportDescriptor descriptor = report.getDescriptor();

            json.put("renderType", descriptor.getProperty(GenericChartReportDescriptor.Prop.renderType));
            json.put("dataRegionName", descriptor.getProperty(ReportDescriptor.Prop.dataRegionName));
            json.put("jsonData", descriptor.getProperty(ReportDescriptor.Prop.json));

            return json;
        }
    }
}
