package org.labkey.query.reports;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExperimentService;
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

import java.sql.ResultSet;
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

    public static class MeasureFilter
    {
        public enum QueryType {
            builtIn,
            custom,
            all,
        }

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
            ApiSimpleResponse resp = new ApiSimpleResponse();
            List<Map<String, Object>> measures = new ArrayList<Map<String, Object>>();
            int count = 1;

            for (Map.Entry<String, Map<String, QueryView>> schemaEntry : getViews(form).entrySet())
            {
                for (Map.Entry<String, QueryView> tableEntry : schemaEntry.getValue().entrySet())
                {
                    QueryDefinition def = QueryService.get().getQueryDef(getUser(), getContainer(), schemaEntry.getKey(), tableEntry.getKey());

                    for (ColumnInfo col : getMeasureColumns(tableEntry.getValue(), def, form))
                    {
                        // add measure properties
                        Map<String, Object> props = getColumnProps(col);

                        props.put("schemaName", schemaEntry.getKey());
                        props.put("queryName", tableEntry.getKey());
                        props.put("isUserDefined", (def != null && !def.isTableQueryDefinition()));
                        props.put("id", count++);

                        measures.add(props);
                    }
                }
            }
            resp.put("success", true);
            resp.put("measures", measures);

            return resp;
        }

        protected List<ColumnInfo> getMeasureColumns(QueryView view, QueryDefinition def, Form form)
        {
            List<ColumnInfo> columns = new ArrayList<ColumnInfo>();

            for (DisplayColumn dc : view.getDisplayColumns())
            {
                ColumnInfo col = dc.getColumnInfo();

                if (col != null)
                {
                    // ignore hidden columns
                    if (col.isHidden()) continue;

                    if (form.isDateMeasures())
                    {
                        if (col.isDateTimeType())
                            columns.add(col);
                    }
                    else
                    {
                        if (col.isMeasure())
                            columns.add(col);
                    }
                }
            }

            TableInfo tinfo = view.getTable();
            if (form.isDateMeasures() && "study".equalsIgnoreCase(tinfo.getSchema().getName()))
            {
                // for visit based studies, we will look for the participantVisit.VisitDate column and
                // if found, return that as a date measure
                Study study = StudyService.get().getStudy(getContainer());
                if (study != null && study.getTimepointType().isVisitBased())
                {
                    String visitColName = StudyService.get().getSubjectVisitColumnName(getContainer());
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
                                columns.add(visitDate);
                            }
                        }
                    }
                }
            }
            return columns;
        }

        protected Map<String, Map<String, TableInfo>> getTables(MeasuresForm form)
        {
            Map<String, Map<String, TableInfo>> tables = new HashMap<String, Map<String, TableInfo>>();
            DefaultSchema defSchema = DefaultSchema.get(getUser(), getContainer());

            if (form.getFilters() != null && form.getFilters().length > 0)
            {
                for (String filter : form.getFilters())
                {
                    MeasureFilter mf = new MeasureFilter(filter);
                    QuerySchema schema = defSchema.getSchema(mf.getSchema());
                    if (schema instanceof UserSchema)
                    {
                        UserSchema uschema = (UserSchema)schema;

                        if (mf.getQuery() == null)
                        {
                            // add all tables from this schema
                            getTablesFromSchema(uschema, mf.getQueryType(), tables);
                        }
                        else
                            // add a specific table
                            addTable(uschema, mf.getQuery(), tables);
                    }
                }
            }
            else
            {
                // get all tables in this container
                for (String schemaName : defSchema.getUserSchemaNames())
                {
                    QuerySchema schema = defSchema.getSchema(schemaName);
                    if (schema instanceof UserSchema)
                    {
                        getTablesFromSchema((UserSchema)schema, MeasureFilter.QueryType.all, tables);
                    }
                }
            }
            return tables;
        }

        protected Map<String, Map<String, QueryView>> getViews(MeasuresForm form)
        {
            Map<String, Map<String, QueryView>> tables = new HashMap<String, Map<String, QueryView>>();
            DefaultSchema defSchema = DefaultSchema.get(getUser(), getContainer());

            if (form.getFilters() != null && form.getFilters().length > 0)
            {
                for (String filter : form.getFilters())
                {
                    MeasureFilter mf = new MeasureFilter(filter);
                    QuerySchema schema = defSchema.getSchema(mf.getSchema());
                    if (schema instanceof UserSchema)
                    {
                        UserSchema uschema = (UserSchema)schema;

                        if (mf.getQuery() == null)
                        {
                            // add all tables from this schema
                            getViewsFromSchema(uschema, mf.getQueryType(), tables);
                        }
                        else
                            // add a specific table
                            addView(uschema, mf.getQuery(), tables);
                    }
                }
            }
            else
            {
                // get all tables in this container
                for (String schemaName : defSchema.getUserSchemaNames())
                {
                    QuerySchema schema = defSchema.getSchema(schemaName);
                    if (schema instanceof UserSchema)
                    {
                        getViewsFromSchema((UserSchema)schema, MeasureFilter.QueryType.all, tables);
                    }
                }
            }
            return tables;
        }

        private void getTablesFromSchema(UserSchema schema, MeasureFilter.QueryType type, Map<String, Map<String, TableInfo>> schemas)
        {
            if (type == MeasureFilter.QueryType.all || type == MeasureFilter.QueryType.custom)
            {
                Map<String,QueryDefinition> queryDefMap = QueryService.get().getQueryDefs(getUser(), getContainer(), schema.getSchemaName());
                for (Map.Entry<String,QueryDefinition> entry : queryDefMap.entrySet())
                {
                    QueryDefinition qdef = entry.getValue();
                    if (!qdef.isHidden())
                    {
                        addTable(schema, qdef.getName(), schemas);
                    }
                }
            }

            // built in tables
            if (type == MeasureFilter.QueryType.all || type == MeasureFilter.QueryType.builtIn)
            {
                for (String name : schema.getTableNames())
                {
                    addTable(schema, name, schemas);
                }
            }
        }

        private void getViewsFromSchema(UserSchema schema, MeasureFilter.QueryType type, Map<String, Map<String, QueryView>> schemas)
        {
            if (type == MeasureFilter.QueryType.all || type == MeasureFilter.QueryType.custom)
            {
                Map<String,QueryDefinition> queryDefMap = QueryService.get().getQueryDefs(getUser(), getContainer(), schema.getSchemaName());
                for (Map.Entry<String,QueryDefinition> entry : queryDefMap.entrySet())
                {
                    QueryDefinition qdef = entry.getValue();
                    if (!qdef.isHidden())
                    {
                        addView(schema, qdef.getName(), schemas);
                    }
                }
            }

            // built in tables
            if (type == MeasureFilter.QueryType.all || type == MeasureFilter.QueryType.builtIn)
            {
                for (String name : schema.getTableNames())
                {
                    addView(schema, name, schemas);
                }
            }
        }

        private void addTable(UserSchema schema, String tableName, Map<String, Map<String, TableInfo>> schemas)
        {
            TableInfo tinfo = schema.getTable(tableName);
            if (tinfo != null)
            {
                if (!schemas.containsKey(schema.getSchemaName()))
                    schemas.put(schema.getSchemaName(), new HashMap<String, TableInfo>());

                Map<String, TableInfo> tables = schemas.get(schema.getSchemaName());
                if (!tables.containsKey(tableName))
                    tables.put(tableName, tinfo);
            }
        }

        private void addView(UserSchema schema, String tableName, Map<String, Map<String, QueryView>> schemas)
        {
            QuerySettings settings = schema.getSettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, tableName);
            QueryView view = new QueryView(schema, settings, null);

            if (view != null)
            {
                if (!schemas.containsKey(schema.getSchemaName()))
                    schemas.put(schema.getSchemaName(), new HashMap<String, QueryView>());

                Map<String, QueryView> tables = schemas.get(schema.getSchemaName());
                if (!tables.containsKey(tableName))
                    tables.put(tableName, view);
            }
        }

        protected Set<String> getSchemaNames(MeasuresForm form)
        {
            if (form.getSchemaName() != null)
                return Collections.singleton(form.getSchemaName());
            else
            {
                DefaultSchema defSchema = DefaultSchema.get(getUser(), getContainer());
                return defSchema.getUserSchemaNames();
            }
        }

        protected Set<String> getTableNames(MeasuresForm form, UserSchema schema)
        {
            if (form.getQueryName() != null)
                return Collections.singleton(form.getQueryName());
            else
                return schema.getTableNames();
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
            List<Map<String, Object>> dimensions = new ArrayList<Map<String, Object>>();

            if (form.getSchemaName() != null && form.getQueryName() != null)
            {
                QuerySchema schema = DefaultSchema.get(getUser(), getContainer()).getSchema(form.getSchemaName());

                if (schema instanceof UserSchema)
                {
                    UserSchema uschema = (UserSchema)schema;
                    TableInfo tinfo = uschema.getTable(form.getQueryName());

                    getDimensions(tinfo, form.getSchemaName(), form.getQueryName(), dimensions);
                }

                if (form.isIncludeDemographics())
                {
                    // include dimensions from demographic data sources, probably only relevant for studies
                    Study study = StudyService.get().getStudy(getContainer());
                    if (study != null)
                    {
                        for (DataSet ds : study.getDataSets())
                        {
                            if (ds.isDemographicData())
                            {
                                getDimensions(ds.getTableInfo(getUser()), "study", ds.getName(), dimensions);
                            }
                        }
                    }
                }
                resp.put("success", true);
                resp.put("dimensions", dimensions);
            }
            else
                throw new IllegalArgumentException("schemaName and queryName are required parameters");

            return resp;
        }

        protected void getDimensions(TableInfo tinfo, String schema, String query, List<Map<String, Object>> dimensions)
        {
            if (tinfo != null)
            {
                QueryDefinition def = QueryService.get().getQueryDef(getUser(), getContainer(), schema, query);

                for (ColumnInfo col : tinfo.getColumns())
                {
                    if (col.isDimension())
                    {
                        // add measure properties
                        Map<String, Object> props = getColumnProps(col);

                        props.put("schemaName", schema);
                        props.put("queryName", query);
                        props.put("isUserDefined", (def != null && !def.isTableQueryDefinition()));

                        dimensions.add(props);
                    }
                }
            }
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
        @Override
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);

            List<Map<String, Object>> types = new ArrayList<Map<String, Object>>();

            // motion chart
            Map<String, Object> motion = new HashMap<String, Object>();
            motion.put("type", "motion");
            motion.put("label", "Motion Chart");
            motion.put("icon", getViewContext().getContextPath() + "/reports/output_motionchart.jpg");
            types.add(motion);

            // line chart
            Map<String, Object> line = new HashMap<String, Object>();
            line.put("type", "line");
            line.put("label", "Time Chart");
            line.put("icon", getViewContext().getContextPath() + "/reports/output_linechart.jpg");

            List<Map<String, String>> lineAxis = new ArrayList<Map<String, String>>();
            lineAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select the date measurement for the x-axis", "multiSelect", "false", "timeAxis", "true"));
            lineAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            line.put("axis", lineAxis);

            line.put("enabled", true);
            types.add(line);

            // scatter chart
            Map<String, Object> scatter = new HashMap<String, Object>();
            scatter.put("type", "scatter");
            scatter.put("label", "Scatter Plot");
            scatter.put("icon", getViewContext().getContextPath() + "/reports/output_scatterplot.jpg");

            List<Map<String, String>> scatterAxis = new ArrayList<Map<String, String>>();
            scatterAxis.add(PageFlowUtil.map("name", "x-axis", "label", "Select data type for x-axis", "multiSelect", "false"));
            scatterAxis.add(PageFlowUtil.map("name", "y-axis", "label", "Select data type for y-axis", "multiSelect", "false"));
            scatter.put("axis", scatterAxis);

            scatter.put("enabled", true);
            types.add(scatter);

            // data grid
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("type", "dataGrid");
            data.put("label", "Data Grid");
            data.put("icon", getViewContext().getContextPath() + "/reports/output_grid.jpg");
            types.add(data);

            // excel data export
            Map<String, Object> excel = new HashMap<String, Object>();
            excel.put("type", "excelExport");
            excel.put("label", "Excel Data Export");
            excel.put("icon", getViewContext().getContextPath() + "/reports/output_excel.jpg");
            types.add(excel);

            // TSV data export
            Map<String, Object> tsv = new HashMap<String, Object>();
            tsv.put("type", "tsvExport");
            tsv.put("label", "Tab-delimited Data Export");
            tsv.put("icon", getViewContext().getContextPath() + "/reports/output_text.jpg");
            types.add(tsv);

            resp.put("types", types);

            return resp;
        }
    }

    public abstract static class VisualizationMetadata
    {
        private String _name;
        private String _queryName;
        private String _schemaName;
        protected Container _container;
        protected User _user;

        public VisualizationMetadata(Container container, User user, Map<String, Object> properties)
        {
            _name = (String) properties.get("name");
            _queryName = (String) properties.get("queryName");
            _schemaName = (String) properties.get("schemaName");
            _user = user;
            _container = container;
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

        public String getSchemaName()
        {
            return _schemaName;
        }

        public String getName()
        {
            return _name;
        }

        public Set<String> getSelectColumns()
        {
            Set<String> cols = new HashSet<String>();
            cols.add(getName().replace('/', '.'));
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

    public static class Dimension extends VisualizationMetadata
    {
        private Object[] _values;

        public Dimension(Container container, User user, Map<String, Object> properties, Object[] values)
        {
            super(container, user, properties);
            _values = values;
        }

        public Object[] getValues()
        {
            return _values;
        }
    }

    public static class Measure extends VisualizationMetadata
    {
        private Dimension[] _dimensions;
        private TableInfo _tableInfo;
        private List<Measure> _additionalColumns = new ArrayList<Measure>();
        private List<Measure> _dependentMeasures = new ArrayList<Measure>();
        private Map<String, String> _measureNameToColumnName;

        public Measure(Container container, User user, Map<String, Object> measureInfo)
        {
            this(container, user, measureInfo, null, null);
        }

        public Measure(Container container, User user, Map<String, Object> measureInfo, Map<String, Object> dimensionInfo, Object[] dimensionValues)
        {
            super(container, user, measureInfo);
            if (dimensionInfo != null)
                _dimensions = new Dimension[] { new Dimension(container, user, dimensionInfo, dimensionValues) };
        }

        @Override
        public TableInfo getTableInfo()
        {
            if (_tableInfo == null)
            {
                TableInfo tinfo = super.getTableInfo();

                Dimension[] dimensions = getDimensions();
                if (dimensions != null && dimensions.length > 0)
                {
                    if (dimensions.length > 1)
                        throw new IllegalArgumentException("Only one dimension is currently supported per measure; found " + dimensions.length);
                    Dimension dimension = dimensions[0];
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
        public Set<String> getSelectColumns()
        {
            Dimension[] dimensions = getDimensions();
            Set<String> cols;
            if (dimensions != null && dimensions.length > 0)
            {
                // Don't add _additionalColumns here- they've already been added when we created the crosstabtableinfo
                // to pivot by dimension.
                cols = getTableInfo().getColumnNameSet();
            }
            else
            {
                cols = super.getSelectColumns();
                for (VisualizationMetadata metadata : _additionalColumns)
                    cols.add(metadata.getName().replace('/', '.'));
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

        public Dimension[] getDimensions()
        {
            return _dimensions;
        }
    }

    public static class GetDataForm implements CustomApiForm, HasViewContext
    {
        private List<Measure> _measures;
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
            _measures = new ArrayList<Measure>();
            Object measuresProp = props.get("measures");
            if (measuresProp != null)
            {
                for (Map<String, Object> measureInfo : ((JSONArray) measuresProp).toJSONObjectArray())
                {
                    Map<String, Object> axisInfo = (Map<String, Object>) measureInfo.get("axis");
                    Map<String, Object> measureProperties = (Map<String, Object>) measureInfo.get("measure");

                    Map<String, Object> dimensionProperties = (Map<String, Object>) measureInfo.get("dimension");
                    JSONArray dimensionValues = (JSONArray) measureInfo.get("dimensionValues");
                    Object[] valuesArray = null;
                    if (dimensionValues != null)
                    {
                        valuesArray = new Object[dimensionValues.length()];
                        for (int i = 0; i < dimensionValues.length(); i++)
                            valuesArray[i] = dimensionValues.get(i);
                    }

                    Measure measure = new Measure(_context.getContainer(), _context.getUser(), measureProperties, dimensionProperties, valuesArray);

                    Object timeAxis = axisInfo.get("timeAxis");
                    if (timeAxis instanceof String && Boolean.parseBoolean((String) timeAxis))
                    {
                        Map<String, Object> dateOptions = (Map<String, Object>) measureInfo.get("dateOptions");
                        Measure zeroDateMeasure = new Measure(_context.getContainer(), _context.getUser(), (Map<String, Object>) dateOptions.get("zeroDateCol"), null, null)
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
        }

        public List<Measure> getMeasures()
        {
            return _measures;
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

        @Override
        public ApiResponse execute(GetDataForm getDataForm, BindException errors) throws Exception
        {
            Map<Measure, Measure> childToParent = new HashMap<Measure, Measure>();
            // First we flatten, to ensure that all requested columns are considered when we call 'collapseMeasures'
            List<Measure> measures = flattenMeasures(getDataForm.getMeasures(), childToParent);
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
                    sql.append(sep).append(tableName).append(".").append(column);

                    String alias = column;
                    if (alias.contains("."))
                        alias = alias.replaceAll("\\.", "_");
                    // It may be necessary to disambiguate column names, since the same name ("Result", for example) may exist
                    // in multiple tables:
                    if (selectedCols.contains(alias))
                        alias = tableName + "_" + alias;

                    if (!alias.equals(column))
                        sql.append(" AS ").append(alias);

                    selectedCols.add(alias);
                    sep = ", ";
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

            VisualizationUserSchema schema = new VisualizationUserSchema(getUser(), getContainer(), tableNameToMetadata);
            ApiQueryResponse response = getApiResponse(schema, sql.toString(), errors);

            // Note: extra properties can only be gathered after the query has executed, since execution populates the name maps.
            Map<String, Object> extraProperties = new HashMap<String, Object>();
            Map<String, String> measureNameToColumnName = new HashMap<String, String>();
            extraProperties.put("measureToColumn", measureNameToColumnName);
            for (Measure measure : measures)
                measureNameToColumnName.putAll(measure.getMeasureNameToColumnNameMap());

            Map<String, String> nameRemap = new HashMap<String, String>();
            nameRemap.put("ParticipantVisit_VisitDate", "ParticipantVisit/VisitDate");
            nameRemap.put("ParticipantVisit.VisitDate", "ParticipantVisit/VisitDate");
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
            TempQuerySettings settings = new TempQuerySettings(schemaName, sql, getViewContext().getContainer());

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
            List<Map<String, Object>> measures = new ArrayList<Map<String, Object>>();

            getDateMeasures(measures);

            resp.put("success", true);
            resp.put("measures", measures);

            return resp;
        }

        /**
         * For 10.3 make the assumption that date measures consist only of date columns from study demographics
         * datasets
         * @param measures
         */
        private void getDateMeasures(List<Map<String, Object>> measures)
        {
            Study study = StudyService.get().getStudy(getContainer());
            if (study != null)
            {
                for (DataSet ds : study.getDataSets())
                {
                    if (ds.isDemographicData())
                    {
                        DefaultSchema defSchema = DefaultSchema.get(getUser(), getContainer());
                        UserSchema schema = (UserSchema)defSchema.getSchema("study");
                        QuerySettings settings = schema.getSettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, ds.getName());
                        QueryView view = new QueryView(schema, settings, null);

                        if (view != null)
                        {
                            for (DisplayColumn dc : view.getDisplayColumns())
                            {
                                ColumnInfo col = dc.getColumnInfo();

                                if (col != null && col.isDateTimeType())
                                {
                                    Map<String, Object> props = getColumnProps(col);

                                    props.put("schemaName", "study");
                                    props.put("queryName", ds.getName());

                                    measures.add(props);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
