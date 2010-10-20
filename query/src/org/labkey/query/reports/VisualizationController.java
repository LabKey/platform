package org.labkey.query.reports;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.exp.property.Type;
import org.labkey.api.query.*;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.*;

/**
 * Copyright (c) 2010 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Sep 13, 2010 10:02:53 AM
 */
public class VisualizationController extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(VisualizationController.class);

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

    public static class MeasuresForm
    {
        private String[] _filters = new String[0];
        private String _schemaName;
        private String _queryName;
        private String _name;
        private boolean _dateMeasures;

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
    }

    protected Map<String, ? extends VisualizationProvider> getVisualizationProviders()
    {
        return Collections.singletonMap("study", new StudyVisualizationProvider());
    }

    public static class VisualizationProvider
    {
        protected static enum ColumnMatchType
        {
            DATETIME_COLS()
                    {
                        @Override
                        public boolean match(ColumnInfo col)
                        {
                            return !col.isHidden() && col.isDateTimeType();
                        }
                    },
            CONFIGURED_MEASURES()
                    {
                        @Override
                        public boolean match(ColumnInfo col)
                        {
                            return !col.isHidden() && col.isMeasure();
                        }
                    },
            CONFIGURED_DIMENSIONS()
                    {
                        @Override
                        public boolean match(ColumnInfo col)
                        {
                            return !col.isHidden() && col.isDimension();
                        }};

            public abstract boolean match(ColumnInfo col);
        }

        private String _schemaName;

        public VisualizationProvider(String userSchemaName)
        {
            _schemaName = userSchemaName;
        }

        protected UserSchema getUserSchema(Container container, User user)
        {
            DefaultSchema defSchema = DefaultSchema.get(user, container);
            QuerySchema schema = defSchema.getSchema(_schemaName);
            if (!(schema instanceof UserSchema))
            {
                if (schema == null)
                    throw new IllegalStateException("No schema found with name " + _schemaName);
                else
                    throw new IllegalStateException("Unexpected schema type: " + schema.getClass().getSimpleName());
            }
            return (UserSchema) schema;
        }

        protected QueryView getQueryView(ViewContext context, ColumnMatchType matchType, String queryName)
        {
            UserSchema schema = getUserSchema(context.getContainer(), context.getUser());
            // built in tables
            if (schema.getTableNames().contains(queryName))
            {
                QueryView view = getView(context, schema, queryName);
                if (isValid(view, matchType))
                    return view;
            }

            // custom queries:
            QueryDefinition qdef = QueryService.get().getQueryDef(context.getUser(), context.getContainer(), _schemaName, queryName);
            if (!qdef.isHidden())
            {
                QueryView view = getView(context, schema, qdef.getName());
                if (isValid(view, matchType))
                    return view;
            }
            return null;
        }

        protected Collection<QueryView> getQueryViews(ViewContext context, QueryType queryType, ColumnMatchType matchType)
        {
            Map<String, QueryView> views = new HashMap<String, QueryView>();
            UserSchema schema = getUserSchema(context.getContainer(), context.getUser());
            if (queryType == QueryType.all || queryType == QueryType.custom)
            {
                Map<String, QueryDefinition> queryDefMap = QueryService.get().getQueryDefs(context.getUser(), context.getContainer(), _schemaName);
                for (Map.Entry<String, QueryDefinition> entry : queryDefMap.entrySet())
                {
                    QueryDefinition qdef = entry.getValue();
                    if (!qdef.isHidden())
                    {
                        QueryView view = getView(context, schema, qdef.getName());
                        if (isValid(view, matchType))
                            views.put(qdef.getName(), view);
                    }
                }
            }

            // built in tables
            if (queryType == QueryType.all || queryType == QueryType.builtIn)
            {
                for (String name : schema.getTableNames())
                {
                    QueryView view = getView(context, schema, name);
                    if (isValid(view, matchType))
                        views.put(name, view);
                }
            }
            return views.values();
        }

        protected boolean isValid(QueryView view, ColumnMatchType type)
        {
            return true;
        }

        protected QueryView getView(ViewContext context, UserSchema schema, String queryName)
        {
            QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, queryName);
            return new QueryView(schema, settings, null);
        }

        protected Map<ColumnInfo, QueryView> getMatchingColumns(Container container, Collection<QueryView> views, ColumnMatchType columnMatchType)
        {
            Map<ColumnInfo, QueryView> matches = new HashMap<ColumnInfo, QueryView>();
            for (QueryView view : views)
            {
                for (DisplayColumn dc : view.getDisplayColumns())
                {
                    ColumnInfo col = dc.getColumnInfo();

                    if (col != null)
                    {
                        // ignore hidden columns
                        if (columnMatchType.match(col))
                            matches.put(col, view);
                    }
                }
            }
            return matches;
        }

        protected Map<ColumnInfo, QueryView> getMatchingColumns(ViewContext context, ColumnMatchType matchType, String queryName)
        {
            QueryView view = getQueryView(context, matchType, queryName);
            return getMatchingColumns(context.getContainer(), Collections.singleton(view), matchType);
        }

        protected Map<ColumnInfo, QueryView> getMatchingColumns(ViewContext context, QueryType queryType, ColumnMatchType matchType)
        {
            Collection<QueryView> views = getQueryViews(context, queryType, matchType);
            return getMatchingColumns(context.getContainer(), views, matchType);
        }

        public Map<ColumnInfo, QueryView> getMeasures(ViewContext context, QueryType queryType)
        {
            return getMatchingColumns(context, queryType, ColumnMatchType.CONFIGURED_MEASURES);
        }

        public Map<ColumnInfo, QueryView> getMeasures(ViewContext context, String queryName)
        {
            return getMatchingColumns(context, ColumnMatchType.CONFIGURED_MEASURES, queryName);
        }

        public Map<ColumnInfo, QueryView> getDateMeasures(ViewContext context, QueryType queryType)
        {
            return getMatchingColumns(context, queryType, ColumnMatchType.DATETIME_COLS);
        }

        public Map<ColumnInfo, QueryView> getDateMeasures(ViewContext context, String queryName)
        {
            return getMatchingColumns(context, ColumnMatchType.DATETIME_COLS, queryName);
        }

        public Map<ColumnInfo, QueryView> getZeroDateMeasures(ViewContext context, QueryType queryType)
        {
            // By default, assume that any date can be a measure date or a zero date.
            return getDateMeasures(context, queryType);
        }

        public Map<ColumnInfo, QueryView> getDimensions(ViewContext context, String queryName)
        {
            return getMatchingColumns(context, ColumnMatchType.CONFIGURED_DIMENSIONS, queryName);
        }
    }

    public static class StudyVisualizationProvider extends VisualizationProvider
    {
        public StudyVisualizationProvider()
        {
            super("study");
        }

        @Override
        protected boolean isValid(QueryView view, ColumnMatchType type)
        {
            if (type == ColumnMatchType.CONFIGURED_MEASURES)
            {
                TableInfo tinfo = view.getTable();
                return tinfo != null && tinfo.getColumnNameSet().contains("ParticipantSequenceKey");
            }
            else
                return super.isValid(view, type);
        }

        protected Map<ColumnInfo, QueryView> getMatchingColumns(Container container, Collection<QueryView> views, ColumnMatchType type)
        {
            Map<ColumnInfo, QueryView> matches = super.getMatchingColumns(container, views, type);
            if (type == ColumnMatchType.DATETIME_COLS)
            {
                Study study = StudyService.get().getStudy(container);
                // for visit based studies, we will look for the participantVisit.VisitDate column and
                // if found, return that as a date measure
                if (study != null && study.getTimepointType().isVisitBased())
                {
                    for (QueryView view : views)
                    {
                        TableInfo tinfo = view.getTable();
                        String visitColName = StudyService.get().getSubjectVisitColumnName(container);
                        ColumnInfo visitCol = tinfo.getColumn(visitColName);
                        if (visitCol != null)
                        {
                            TableInfo visitTable = visitCol.getFkTableInfo();
                            if (visitTable != null)
                            {
                                ColumnInfo visitDate = visitTable.getColumn("visitDate");
                                if (visitDate != null)
                                {
                                    visitDate.setFieldKey(FieldKey.fromParts(visitColName, visitDate.getName()));
                                    matches.put(visitDate, view);
                                }
                            }
                        }
                    }
                }
            }
            return matches;
        }

        @Override
        public Map<ColumnInfo, QueryView> getZeroDateMeasures(ViewContext context, QueryType queryType)
        {
            // For studies, valid zero date columns are found in demographic datasets only:
            Map<ColumnInfo, QueryView> measures = new HashMap<ColumnInfo, QueryView>();
            Study study = StudyService.get().getStudy(context.getContainer());
            if (study != null)
            {
                for (DataSet ds : study.getDataSets())
                {
                    if (ds.isDemographicData())
                    {
                        DefaultSchema defSchema = DefaultSchema.get(context.getUser(), context.getContainer());
                        UserSchema schema = (UserSchema)defSchema.getSchema("study");
                        QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, ds.getName());
                        QueryView view = new QueryView(schema, settings, null);

                        for (DisplayColumn dc : view.getDisplayColumns())
                        {
                            ColumnInfo col = dc.getColumnInfo();
                            if (col != null && ColumnMatchType.DATETIME_COLS.match(col))
                               measures.put(col, view);
                        }
                    }
                }
            }
            return measures;
        }

        private static final boolean INCLUDE_DEMOGRAPHIC_DIMENSIONS = false;
        @Override
        public Map<ColumnInfo, QueryView> getDimensions(ViewContext context, String queryName)
        {
            Map<ColumnInfo, QueryView> dimensions = super.getDimensions(context, queryName);
            if (INCLUDE_DEMOGRAPHIC_DIMENSIONS)
            {
                // include dimensions from demographic data sources
                Study study = StudyService.get().getStudy(context.getContainer());
                if (study != null)
                {
                    for (DataSet ds : study.getDataSets())
                    {
                        if (ds.isDemographicData())
                            dimensions.putAll(super.getDimensions(context, ds.getName()));
                    }
                }
            }
            return dimensions;
        }
    }
    
    public enum QueryType {
        builtIn,
        custom,
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
    public class GetMeasuresAction<Form extends MeasuresForm> extends ApiAction<Form>
    {
        public ApiResponse execute(Form form, BindException errors) throws Exception
        {
            Map<ColumnInfo, QueryView> measures = new HashMap<ColumnInfo, QueryView>();
            if (form.getFilters() != null && form.getFilters().length > 0)
            {
                for (String filter : form.getFilters())
                {
                    MeasureFilter mf = new MeasureFilter(filter);
                    VisualizationProvider provider = getVisualizationProviders().get(mf.getSchema());
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
                for (VisualizationProvider provider : getVisualizationProviders().values())
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

        protected List<Map<String, Object>> getColumnResponse(Map<ColumnInfo, QueryView> cols)
        {
            List<Map<String, Object>> measuresJSON = new ArrayList<Map<String, Object>>();
            int count = 1;
            for (Map.Entry<ColumnInfo, QueryView> entry : cols.entrySet())
            {
                // add measure properties
                Map<String, Object> props = getColumnProps(entry.getKey());

                QueryView queryView = entry.getValue();
                props.put("schemaName", queryView.getSchema().getName());
                props.put("queryName", queryView.getQueryDef().getName());
                props.put("isUserDefined", !queryView.getQueryDef().isTableQueryDefinition());
                props.put("id", count++);

                measuresJSON.add(props);
            }
            return measuresJSON;
        }

        protected Map<String, Object> getColumnProps(ColumnInfo col)
        {
            Map<String, Object> props = new HashMap<String, Object>();

            props.put("name", col.getName());
            props.put("label", col.getLabel());
            props.put("type", col.getSqlTypeName());
            props.put("description", StringUtils.trimToEmpty(col.getDescription()));

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
                VisualizationProvider provider = getVisualizationProviders().get(form.getSchemaName());
                if (provider == null)
                {
                    errors.reject(ERROR_MSG, "No measure provider found for schema " + form.getSchemaName());
                    return null;
                }
                Map<ColumnInfo, QueryView> dimensions = provider.getDimensions(getViewContext(), form.getQueryName());
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
    public class GetDimensionValues extends GetMeasuresAction
    {
        public ApiResponse execute(MeasuresForm form, BindException errors) throws Exception
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
                        ColumnInfo col = tinfo.getColumn(form.getName());
                        if (col != null)
                        {
                            Table.TableResultSet rs = null;
                            try {
                                SQLFragment sql = QueryService.get().getSelectSQL(tinfo, Collections.singleton(col), null, null, Table.ALL_ROWS, 0);

                                rs = Table.executeQuery(uschema.getDbSchema(), sql.getSQL().replaceFirst("SELECT", "SELECT DISTINCT"), sql.getParamsArray());
                                Iterator<Map<String, Object>> it = rs.iterator();

                                while (it.hasNext())
                                {
                                    Map<String, Object> row = it.next();

                                    if (row.containsKey(col.getName()))
                                    {
                                        Object o = row.get(col.getName());
                                        if (o != null)
                                            values.add(Collections.singletonMap("value", ConvertUtils.convert(o)));
                                    }
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
        Map<String, Object> getBaseTypeProperties()
        {
            Map<String, Object> properties = new HashMap<String, Object>();
            properties.put("subjectColumn", StudyService.get().getSubjectColumnName(getContainer()));
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
            // line chart
            Map<String, Object> line = getBaseTypeProperties();
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
            Map<String, Object> data = getBaseTypeProperties();
            data.put("type", "dataGridTime");
            data.put("label", "Data Grid (Time)");
            data.put("icon", getViewContext().getContextPath() + "/reports/output_grid.jpg");

            List<Map<String, String>> dataAxis = new ArrayList<Map<String, String>>();
            dataAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select the date measurement for the x-axis", "multiSelect", "false", "timeAxis", "true"));
            dataAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            data.put("axis", lineAxis);
            data.put("isGrid", true);
            data.put("enabled", true);

            types.add(data);

            // data grid
            Map<String, Object> dataScatter = getBaseTypeProperties();
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

    public static class VisualizationMetadata
    {
        private String _name;
        private String _queryName;
        private String _schemaName;
        private Type _type;
        private Set<Object> _values;
        protected Container _container;
        protected User _user;

        public VisualizationMetadata(Container container, User user, Map<String, Object> properties)
        {
            _name = (String) properties.get("name");
            _queryName = (String) properties.get("queryName");
            _schemaName = (String) properties.get("schemaName");
            _type = Type.getTypeBySqlTypeName((String) properties.get("type"));
            _user = user;
            _container = container;
            JSONArray values = (JSONArray) properties.get("values");
            if (values != null)
            {
                _values = new HashSet<Object>();
                for (int i = 0; i < values.length(); i++)
                    _values.add(values.get(i));
            }
        }

        public TableInfo getTableInfo()
        {
            UserSchema schema = QueryService.get().getUserSchema(_user, _container, _schemaName);
            return schema.getTable(_queryName);
        }

        public DbSchema getDbSchema()
        {
            UserSchema schema = QueryService.get().getUserSchema(_user, _container, _schemaName);
            return schema.getDbSchema();
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public Type getType()
        {
            return _type;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public String getName()
        {
            return _name;
        }

        public Set<Object> getValues()
        {
            return _values;
        }

        protected String getSelectName()
        {
            return getName().replaceAll("/", "\\.");

        }

        public List<String> getSelectColumns()
        {
            List<String> cols = new ArrayList<String>();
            cols.add(getSelectName());
            return cols;
        }

        public boolean isSameTable(VisualizationMetadata other)
        {
            return this._schemaName.equals(other._schemaName) &&
                    this._queryName.equals(other._queryName);
        }

        public String getJoinColumn()
        {
            if ("study".equals(_schemaName))
                return "ParticipantSequenceKey";
            else
                throw new IllegalArgumentException("Cannot join non-study tables: " + _schemaName + "." + _queryName);
        }
    }

    public static class Measure extends VisualizationMetadata
    {
        private Measure[] _dimensions;
        private TableInfo _tableInfo;
        private List<Measure> _additionalColumns = new ArrayList<Measure>();
        private List<Measure> _dependentMeasures = new ArrayList<Measure>();
        private Map<String, String> _measureNameToColumnName;

        public Measure(Container container, User user, Map<String, Object> measureInfo)
        {
            this(container, user, measureInfo, null);
        }

        public Measure(Container container, User user, Map<String, Object> measureInfo, Map<String, Object> dimensionInfo)
        {
            super(container, user, measureInfo);
            if (dimensionInfo != null)
                _dimensions = new Measure[] { new Measure(container, user, dimensionInfo) };
        }

        @Override
        public TableInfo getTableInfo()
        {
            if (_tableInfo == null)
            {
                TableInfo tinfo = super.getTableInfo();

                VisualizationMetadata[] dimensions = getDimensions();
                if (dimensions != null && dimensions.length > 0)
                {
                    if (dimensions.length > 1)
                        throw new IllegalArgumentException("Only one dimension is currently supported per measure; found " + dimensions.length);
                    VisualizationMetadata dimension = dimensions[0];
                    ColumnInfo col = tinfo.getColumn(getName());
                    CrosstabSettings settings = new CrosstabSettings(tinfo);
                    settings.addMeasure(col.getFieldKey(), CrosstabMeasure.AggregateFunction.MAX);
                    String dimensionColumnName = dimension.getName();

                    CrosstabDimension columnDimension = settings.getColumnAxis().addDimension(tinfo.getColumn(dimensionColumnName).getFieldKey());

                    // We need to group by all join and non-dimension value columns, including those of merged measures:
                    Set<String> rowCols = new HashSet<String>();
                    addAxisDimension(settings.getRowAxis(), tinfo, getJoinColumn(), rowCols);
                    for (Measure metadata : _additionalColumns)
                    {
                        addAxisDimension(settings.getRowAxis(), tinfo, metadata.getName(), rowCols);
                        addAxisDimension(settings.getRowAxis(), tinfo, metadata.getJoinColumn(), rowCols);
                        for (Measure dependentMeasure : metadata.getDependentMeasures())
                            addAxisDimension(settings.getRowAxis(), tinfo, dependentMeasure.getJoinColumn(), rowCols);
                    }

                    List<CrosstabMember> members = new ArrayList<CrosstabMember>();
                    for (Object value : dimension.getValues())
                        members.add(new CrosstabMember(value, columnDimension));
                    tinfo = new CrosstabTableInfo(settings, members);
                    _measureNameToColumnName = ((CrosstabTableInfo) tinfo).getMeasureNameToColumnNameMap();
                }
                else
                {
                    _measureNameToColumnName = new HashMap<String, String>();
                    for (String selectCol : getSelectColumns())
                        _measureNameToColumnName.put(selectCol, selectCol);
                }
                _tableInfo = tinfo;
            }
            return _tableInfo;
        }

        public Map<String, String> getMeasureNameToColumnNameMap()
        {
            return _measureNameToColumnName;
        }

        private void addAxisDimension(CrosstabAxis axis, TableInfo tinfo, String colName, Set<String> previouslyAdded)
        {
            if (!previouslyAdded.contains(colName))
            {
                FieldKey key = FieldKey.fromString(colName);
                axis.addDimension(key);
                previouslyAdded.add(colName);
            }
        }

        public void addAdditionalColumn(Measure measure)
        {
            if (!measure.isSameTable(this))
                throw new IllegalArgumentException("Can only merge metadata from the same table.");
            _additionalColumns.add(measure);
        }

        public void addDependentMeasure(Measure measure)
        {
            _dependentMeasures.add(measure);
        }

        public List<Measure> getDependentMeasures()
        {
            return Collections.unmodifiableList(_dependentMeasures);
        }

        @Override
        public List<String> getSelectColumns()
        {
            VisualizationMetadata[] dimensions = getDimensions();
            List<String> cols;
            if (dimensions != null && dimensions.length > 0)
            {
                // Don't add _additionalColumns here- they've already been added when we created the crosstabtableinfo
                // to pivot by dimension.
                cols = new ArrayList<String>(getTableInfo().getColumnNameSet());
            }
            else
            {
                cols = super.getSelectColumns();
                for (VisualizationMetadata metadata : _additionalColumns)
                    cols.add(metadata.getSelectName());
            }
            return cols;
       }

        public List<Measure> getAdditionalColumns()
        {
            return Collections.unmodifiableList(_additionalColumns);
        }

        public boolean hasDimensions()
        {
            return _dimensions != null && _dimensions.length > 0;
        }

        public VisualizationMetadata[] getDimensions()
        {
            return _dimensions;
        }

        public List<String> getSortColumns()
        {
            List<String> sorts = new ArrayList<String>();
            // this is a select column, so we don't sort on ourselves.  Additional columns from this table may be sorts,
            // however, so we iterate them here.
            for (Measure possibleSort : _additionalColumns)
            {
                // Column names may have been mapped to different names if we did a pivot on dimension.  Correct the
                // additional column names to match those that will actually be in the query here:
                for (String sortCol : possibleSort.getSortColumns())
                {
                    // Column names are separated by dots, while measure names are separated by slashes.  Map from original select column name
                    // to select measure name before mapping from original measure name to final selected column name:
                    String colName;
                    if (_measureNameToColumnName != null)
                    {
                        sortCol = sortCol.replaceAll("\\.", "/");
                        colName = _measureNameToColumnName.get(sortCol);
                        if (colName == null)
                            throw new IllegalStateException("Expected to find all selected columns in the measure to column map.  Didn't find: " + sortCol);
                    }
                    else
                        colName = sortCol;

                    sorts.add(colName);
                }
            }
            return sorts;
        }
    }

    public static class Sort extends Measure
    {
        public Sort(Container container, User user, Map<String, Object> sortInfo)
        {
            super(container, user, sortInfo);
        }

        @Override
        public List<String> getSortColumns()
        {
            List<String> sorts = new ArrayList<String>();
            sorts.add(getSelectName());
            sorts.addAll(super.getSortColumns());
            return sorts;
        }
    }

    public static class GetDataForm implements CustomApiForm, HasViewContext
    {
        private List<Measure> _measures = new ArrayList<Measure>();
        private List<Measure> _sorts = new ArrayList<Measure>();
        private ViewContext _context;

        @Override
        public void setViewContext(ViewContext context)
        {
            _context = context;
        }

        @Override
        public ViewContext getViewContext()
        {
            return _context;
        }

        public void bindProperties(Map<String, Object> props)
        {
            Object measuresProp = props.get("measures");
            if (measuresProp != null)
            {
                for (Map<String, Object> measureInfo : ((JSONArray) measuresProp).toJSONObjectArray())
                {
                    Map<String, Object> axisInfo = (Map<String, Object>) measureInfo.get("axis");
                    Map<String, Object> measureProperties = (Map<String, Object>) measureInfo.get("measure");

                    Map<String, Object> dimensionProperties = (Map<String, Object>) measureInfo.get("dimension");

                    Measure measure = new Measure(_context.getContainer(), _context.getUser(), measureProperties, dimensionProperties);

                    Object timeAxis = axisInfo.get("timeAxis");
                    if (timeAxis instanceof String && Boolean.parseBoolean((String) timeAxis))
                    {
                        Map<String, Object> dateOptions = (Map<String, Object>) measureInfo.get("dateOptions");
                        Measure zeroDateMeasure = new Measure(_context.getContainer(), _context.getUser(),(Map<String, Object>) dateOptions.get("zeroDateCol"))
                        {
                            @Override
                            public String getJoinColumn()
                            {
                                return StudyService.get().getSubjectColumnName(_context.getContainer());
                            }
                        };
                        measure.addDependentMeasure(zeroDateMeasure);
                    }
                    _measures.add(measure);
                }
            }

            Object sortsProp = props.get("sorts");
            if (sortsProp != null)
            {
                for (Map<String, Object> sortInfo : ((JSONArray) sortsProp).toJSONObjectArray())
                {
                    Sort sortMeasure = new Sort(_context.getContainer(), _context.getUser(), sortInfo);
                    _sorts.add(sortMeasure);
                }
            }
        }

        public List<Measure> getMeasures()
        {
            return _measures;
        }

        public List<Measure> getSorts()
        {
            return _sorts;
        }
    }

    private static class VisualizationUserSchema extends UserSchema
    {
        private Map<String, Measure> _tableNameToMeasure;
        public static final String SCHEMA_NAME = "VizTempUserSchema";
        public static final String SCHEMA_DESCRIPTION = "Temporary user schema to allow resolution of crosstab tableinfo objects";

        public VisualizationUserSchema(User user, Container container, Map<String, Measure> tableNameToMeasure)
        {
            super(SCHEMA_NAME, SCHEMA_DESCRIPTION, user, container, DefaultSchema.get(user, container).getDbSchema());
            _tableNameToMeasure = tableNameToMeasure;
        }

        @Override
        public QuerySchema getSchema(String name)
        {
            if (VisualizationSchema.SCHEMA_NAME.equals(name))
                return new VisualizationSchema(getDbSchema(), getUser(), getContainer(), _tableNameToMeasure);
            else
                return super.getSchema(name);
        }

        @Override
        protected TableInfo createTable(String name)
        {
            return null;
        }

        @Override
        public Set<String> getTableNames()
        {
            return Collections.emptySet();
        }
    }

    private static class VisualizationSchema extends AbstractSchema
    {
        private Map<String, Measure> _tableNameToMeasure;
        public static final String SCHEMA_NAME = "VizTempSchema";
        public static final String SCHEMA_DESCRIPTION = "Temporary schema to allow resolution of crosstab tableinfo objects";

        public VisualizationSchema(DbSchema dbSchema, User user, Container container, Map<String, Measure> tableNameToMeasure)
        {
            super(dbSchema, user, container);
            _tableNameToMeasure = tableNameToMeasure;
        }

        @Override
        public TableInfo getTable(String name)
        {
            VisualizationMetadata metadata = _tableNameToMeasure.get(name);
            if (metadata != null)
                return metadata.getTableInfo();
            return null;
        }

        @Override
        public String getName()
        {
            return SCHEMA_NAME;
        }

        @Override
        public String getDescription()
        {
            return SCHEMA_DESCRIPTION;
        }

    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetDataAction extends ApiAction<GetDataForm>
    {
        private List<Measure> flattenMeasures(Collection<Measure> metadatas, Map<Measure, Measure> childToParent)
        {
            List<Measure> flattened = new ArrayList<Measure>();
            flattenMeasures(metadatas, flattened, null, childToParent);
            return flattened;
        }

        private void flattenMeasures(Collection<Measure> metadatas, List<Measure> flattened, Measure parent, Map<Measure, Measure> childToParent)
        {
            for (Measure measure : metadatas)
            {
                if (parent != null)
                    childToParent.put(measure, parent);
                flattened.add(measure);
            }

            for (Measure measure : metadatas)
            {
                if (measure.getDependentMeasures() != null)
                    flattenMeasures(measure.getDependentMeasures(), flattened, measure, childToParent);
            }
        }

        private void loadTableNames(List<Measure> collapsedMeasures,
                                  Map<String, Measure> tableNameToMetadata)
        {
            for (Measure measure :  collapsedMeasures)
            {
                String name = ColumnInfo.legalNameFromName(measure.getQueryName());
                while (tableNameToMetadata.containsKey(name))
                    name = name + "X";
                tableNameToMetadata.put(name, measure);
            }
        }

        private List<Measure> collapseMeasures(List<Measure> originalMeasures)
        {
            // This method looks through the available measures for pairs that come from the same table.
            // If a pair is found, and if one if sufficiently simple (i.e., without dimensions), it will be
            // folded into the more complex dimension so we only query the table once.
            Map<String, List<Measure>> tableMeasures = new HashMap<String, List<Measure>>();
            for (Measure measure : originalMeasures)
            {
                String tableName = measure.getSchemaName() + "." + measure.getQueryName();
                List<Measure> sameTableMeasures = tableMeasures.get(tableName);
                if (sameTableMeasures == null)
                {
                    sameTableMeasures = new ArrayList<Measure>();
                    tableMeasures.put(tableName, sameTableMeasures);
                }
                sameTableMeasures.add(measure);
            }

            List<Measure> returnMeasures = new ArrayList<Measure>(originalMeasures);
            for (List<Measure> measures : tableMeasures.values())
            {
                if (measures.size() > 1)
                {
                    Measure mostComplex = null;
                    for (Measure measure : measures)
                    {
                        if (mostComplex == null || (!mostComplex.hasDimensions() && measure.hasDimensions()))
                            mostComplex = measure;
                    }
                    for (Measure measure : measures)
                    {
                        if (measure != mostComplex && !measure.hasDimensions())
                        {
                            mostComplex.addAdditionalColumn(measure);
                            returnMeasures.remove(measure);
                        }
                    }
                }
            }
            return returnMeasures;
        }

        private String getColumnAlias(String tableName, String column)
        {
            if (column.contains("."))
                column = column.replaceAll("\\.", "_");
            // disambiguate column names, since the same name ("Result", for example) may exist in multiple tables:
            return tableName + "_" + column;
        }

        @Override
        public ApiResponse execute(GetDataForm getDataForm, BindException errors) throws Exception
        {
            Map<Measure, Measure> childToParent = new HashMap<Measure, Measure>();
            // First we flatten, to ensure that all requested columns are considered when we call 'collapseMeasures'
            List<Measure> measures = flattenMeasures(getDataForm.getMeasures(), childToParent);
            // sorts are simple measures, so we can add them after flattening:
            measures.addAll(getDataForm.getSorts());
            // Collapse measures where possible to eliminate unnecessary self-joins:
            measures = collapseMeasures(measures);

            // For the measures that remain, find the set of tables that we'll have to query to get all data:
            Map<String, Measure> tableNameToMetadata = new LinkedHashMap<String, Measure>();
            loadTableNames(measures, tableNameToMetadata);

            Map<Measure, String> metadataToTableName = new HashMap<Measure, String>();
            for (Map.Entry<String, Measure> entry : tableNameToMetadata.entrySet())
            {
                Measure measure = entry.getValue();
                String tableName = entry.getKey();
                metadataToTableName.put(measure, tableName);
                if (measure.getAdditionalColumns() != null)
                {
                    for (Measure additionalColumn : measure.getAdditionalColumns())
                        metadataToTableName.put(additionalColumn, tableName);
                }
            }

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ");
            String sep = "";
            Set<String> selectedCols = new HashSet<String>();
            for (Map.Entry<String, Measure> entry : tableNameToMetadata.entrySet())
            {
                String tableName = entry.getKey();
                for (String column : entry.getValue().getSelectColumns())
                {
                    String alias = getColumnAlias(tableName, column);
                    if (!selectedCols.contains(alias))
                    {
                        sql.append(sep).append(tableName).append(".").append(column);
                        sql.append(" AS ").append(alias);
                        selectedCols.add(alias);
                        sep = ", ";
                    }
                }
                sql.append("\n");
            }

            sql.append("FROM ");

            Map.Entry<String, Measure> previousEntry = null;
            for (Map.Entry<String, Measure> entry : tableNameToMetadata.entrySet())
            {
                if (previousEntry != null)
                    sql.append("\nJOIN\n");

                String tableName = entry.getKey();
                sql.append(VisualizationSchema.SCHEMA_NAME).append(".").append(tableName).append(" AS ").append(tableName);

                if (previousEntry != null)
                {
                    Measure joinToMeasure = childToParent.get(entry.getValue());
                    if (joinToMeasure == null)
                        joinToMeasure = previousEntry.getValue();
                    String prevTablename = metadataToTableName.get(joinToMeasure);
                    String joinColumn = entry.getValue().getJoinColumn();
                    sql.append("\n\tON ").append(tableName).append(".").append(joinColumn);
                    sql.append(" = ");
                    sql.append(prevTablename).append(".").append(joinColumn);
                }
                previousEntry = entry;
            }

            Map<String, Set<String>> filters = new HashMap<String, Set<String>>();
            for (Map.Entry<Measure, String> entry : metadataToTableName.entrySet())
            {
                Set<Object> filterValues = entry.getKey().getValues();
                if (filterValues != null && !filterValues.isEmpty())
                {
                    Set<String> formattedFilterValues = new HashSet<String>();
                    Type type = entry.getKey().getType();
                    for (Object value : filterValues)
                    {
                        if (type.isNumeric() || type == Type.BooleanType)
                            formattedFilterValues.add(value.toString());
                        else
                            formattedFilterValues.add("'" + value.toString() + "'");
                    }
                    filters.put(entry.getValue() + "." + entry.getKey().getSelectName(), formattedFilterValues);
                }
            }

            if (!filters.isEmpty())
            {
                sql.append("\nWHERE ");
                sep = "";
                for (Map.Entry<String, Set<String>> entry : filters.entrySet())
                {
                    String subsep = "";
                    sql.append(sep).append(entry.getKey()).append(" IN (");
                    for (String value : entry.getValue())
                    {
                        sql.append(subsep).append(value);
                        subsep = ", ";
                    }
                    sql.append(")\n");
                    sep = "AND";
                }
            }

            StringBuilder sortClause = new StringBuilder();
            sep = "";
            for (Map.Entry<String, Measure> entry : tableNameToMetadata.entrySet())
            {
                String tableName = entry.getKey();
                for (String column : entry.getValue().getSortColumns())
                {
                    sortClause.append(sep).append(tableName).append(".").append(column);
                    sep = ", ";
                }
            }

            if (sortClause.length() > 0)
                sql.append("\nORDER BY ").append(sortClause);


            VisualizationUserSchema schema = new VisualizationUserSchema(getUser(), getContainer(), tableNameToMetadata);
            ApiQueryResponse response = getApiResponse(schema, sql.toString(), errors);

            // Note: extra properties can only be gathered after the query has executed, since execution populates the name maps.
            Map<String, Object> extraProperties = new HashMap<String, Object>();
            Map<String, String> measureNameToColumnName = new HashMap<String, String>();
            extraProperties.put("measureToColumn", measureNameToColumnName);
            for (Measure measure : measures)
            {
                for (Map.Entry<String, String> entry : measure.getMeasureNameToColumnNameMap().entrySet())
                {
                    String measureName = entry.getKey();
                    String columnName = entry.getValue();
                    String tableName = metadataToTableName.get(measure);
                    String columnAlias = getColumnAlias(tableName, columnName);
                    measureNameToColumnName.put(measureName, columnAlias);
                }
            }

            Map<String, String> nameRemap = new HashMap<String, String>();
            String subjectVisitColumn = StudyService.get().getSubjectVisitColumnName(getContainer());
            nameRemap.put(subjectVisitColumn + "_VisitDate", subjectVisitColumn + "/VisitDate");
            nameRemap.put(subjectVisitColumn + ".VisitDate", subjectVisitColumn + "/VisitDate");
            // Hack to deal with the fact that crosstabtableinfo returns a less friendly name for the VisitDate column:
            for (Map.Entry<String, String> names : nameRemap.entrySet())
            {
                String oldName = names.getKey();
                String newName = names.getValue();
                if (measureNameToColumnName.containsKey(oldName))
                {
                    String value = measureNameToColumnName.remove(oldName);
                    measureNameToColumnName.put(newName, value);
                }
            }
            response.setExtraReturnProperties(extraProperties);
            return response;
        }

        private ApiQueryResponse getApiResponse(VisualizationUserSchema schema, String sql, BindException errors) throws Exception
        {
            String schemaName = schema.getName();
            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query
            TempQuerySettings settings = new TempQuerySettings(sql, getViewContext().getContainer());

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);

            //by default, return all rows
            settings.setShowRows(ShowRows.ALL);

            //apply optional settings (maxRows, offset)
            boolean metaDataOnly = false;

            //build a query view using the schema and settings
            QueryView view = new QueryView(schema, settings, errors);
            view.setShowRecordSelectors(false);
            view.setShowExportButtons(false);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

            return new ExtendedApiQueryResponse(view, getViewContext(), false,
                    false, schemaName, "sql", 0, null, metaDataOnly);
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetZeroDateAction extends GetMeasuresAction<MeasuresForm>
    {
        public ApiResponse execute(MeasuresForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            Map<ColumnInfo, QueryView> measures = new HashMap<ColumnInfo, QueryView>();
            if (form.getFilters() != null && form.getFilters().length > 0)
            {
                for (String filter : form.getFilters())
                {
                    MeasureFilter mf = new MeasureFilter(filter);
                    VisualizationProvider provider = getVisualizationProviders().get(mf.getSchema());
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
                for (VisualizationProvider provider : getVisualizationProviders().values())
                    measures.putAll(provider.getZeroDateMeasures(getViewContext(), QueryType.all));
            }

            List<Map<String, Object>> measuresJSON = getColumnResponse(measures);
            resp.put("success", true);
            resp.put("measures", measuresJSON);

            return resp;
        }
    }
}
