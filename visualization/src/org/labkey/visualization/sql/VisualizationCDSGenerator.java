/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.visualization.sql;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.Sort;
import org.labkey.api.data.dialect.DialectStringHandler;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.ResultSetDataIterator;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.SQLGenerationException;
import org.labkey.api.visualization.VisDataRequest;
import org.labkey.api.visualization.VisualizationSourceColumn;
import org.labkey.visualization.test.VisTestSchema;
import org.springframework.validation.BindException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.labkey.api.action.SpringActionController.ERROR_MSG;

/**
 * Created by matthew on 7/4/15.
 *
 * This class is a simplified version of VisualizationSQLGenerator for CDS queries.  The ideas here may migrate to a new
 * implementation of visualization-getData.api.
 *
 * NOTE: initial prototype just wraps VisualizationSQLGenerator, but should probably generate SQL based on underlying
 * 'model' similar to how Rolap generates SQL from a model built from mondrian schema xml file.
 */

public class VisualizationCDSGenerator
{
    private static final Logger _log = Logger.getLogger(VisualizationCDSGenerator.class);
    ViewContext _viewContext;
    VisDataRequest _request;
    UserSchema _primarySchema = null;
    List<Map<String, String>> _columnAliases;


    public VisualizationCDSGenerator(ViewContext context, VisDataRequest req)
    {
        setViewContext(context);
        fromVisDataRequest(req);
    }

    public void setViewContext(ViewContext context)
    {
        _viewContext = context;
    }

    void fromVisDataRequest(VisDataRequest req)
    {
        _request = req;
    }

    ViewContext getViewContext()
    {
        return _viewContext;
    }

    Container getContainer()
    {
        return _viewContext.getContainer();
    }

    User getUser()
    {
        return _viewContext.getUser();
    }


    public UserSchema getPrimarySchema()
    {
        if (null == _primarySchema)
        {
            _primarySchema = (UserSchema)DefaultSchema.get(getUser(), getContainer()).getSchema("study");
        }
        return _primarySchema;
    }


    public boolean isMetaDataOnly()
    {
        return _request.isMetaDataOnly();
    }


    public Sort getSort()
    {
        Sort sort = new Sort();
        // TODO
        return sort;
    }


    public List<Map<String, String>> getColumnAliases()
    {
        return _columnAliases;
    }


    public String getFilterDescription()
    {
        // TODO
        return "";
    }


    static Path pathForMeasure(VisDataRequest.Measure m)
    {
        if (StringUtils.isEmpty(m.getAxisName()))
            return new Path(m.getSchemaName(), m.getQueryName());
        else
            return new Path(m.getSchemaName(), m.getQueryName(), m.getAxisName());
    }


    public String getSQL(BindException errors) throws SQLGenerationException, SQLException
    {
        SqlDialect dialect = getPrimarySchema().getDbSchema().getSqlDialect();

        //
        // pre analyze the request, and break down the tables into two groups
        // the non-dataset tables and the dataset tables
        //

        Set<Path> nonDatasetTablesSet = new HashSet<>();
        LinkedHashSet<Path> datasetTablesSet = new LinkedHashSet<>();

        Study study = StudyService.get().getStudy(getContainer());

        for (VisDataRequest.MeasureInfo mi : _request.getMeasures())
        {
            VisDataRequest.Measure m = mi.getMeasure();
            boolean isDataset = false;
            if (null != study && equalsIgnoreCase("study", m.getSchemaName()))
            {
                Dataset d = study.getDatasetByName(m.getQueryName());
                if (null != d && !d.isDemographicData())
                    isDataset = true;
            }
            else if (equalsIgnoreCase("vis_junit", m.getSchemaName()) &&
                    (equalsIgnoreCase("flow",m.getQueryName()) || equalsIgnoreCase("ics",m.getQueryName())))
            {
                isDataset = true;
            }
            Path p = pathForMeasure(m);
            if (!isDataset)
                nonDatasetTablesSet.add(p);
            else
                datasetTablesSet.add(p);
        }
        Path[] datasetTables = datasetTablesSet.toArray(new Path[datasetTablesSet.size()]);

        //
        // VALIDATION
        //
        // since we are wrapping the getData() API, I want to be strict and 'white-list' functionality,
        // rather than passing through options without validating the behavior
        //

        if (null != _request.getFilterQuery())
            errors.reject(ERROR_MSG, "NYI - filterQuery");
        if (null != _request.getFilterUrl())
            errors.reject(ERROR_MSG, "NYI - filterUrl");

        // disallow pivot for now (might support for some properties eventually, e.g. products?)
        _request.getMeasures().stream()
                .filter(mi -> null != mi.getDimension())
                .forEach(mi -> errors.reject(ERROR_MSG, "NYI - pivot"));

        // disallow grouping on dataset columns
        if (null != _request.getGroupBys())
        {
            _request.getGroupBys().stream()
                    .filter(m -> datasetTablesSet.contains(new Path(m.getSchemaName(), m.getQueryName())))
                    .forEach(m -> errors.reject(ERROR_MSG, "NYI - group on dataset column"));
        }

        if (errors.hasErrors())
            return null;


        // TODO code review? consider making this size() < 1, so that the one dataset case and two dataset case look more similiar
        if (datasetTablesSet.size() <= 1)
        {
            VisualizationSQLGenerator sqlGenerator = new VisualizationSQLGenerator(getViewContext(), _request);
            _columnAliases = sqlGenerator.getColumnAliases();
            return sqlGenerator.getSQL();
        }


        //
        // Prepare a VisualizationSQLGenerator call for each participating dataset
        //
        // This is clunky and it would be very nice to simply replace the call to VSQLG, howevever,
        // this works perfectly well for now
        //

        List<VisualizationSQLGenerator> generators = new ArrayList<>();
        List<String> generatedSql = new ArrayList<>();
        String containerColumnName = "Container";
        String subjectColumnName = "ParticipantId";
        String sequenceNumColumnName = "SequenceNum";
        if (null != study)
            subjectColumnName = study.getSubjectColumnName();

        DefaultSchema schema = DefaultSchema.get(getUser(), getContainer());
        for (Path datasetPath : datasetTables)
        {
            String datasetSchemaName =  datasetPath.get(0);
            String datasetQueryName = datasetPath.getName(1);
            String axisName = datasetPath.size() > 2 ? datasetPath.get(2) : null;

            Set<Path> blacklist = new HashSet<>(datasetTablesSet);
            blacklist.remove(datasetPath);
            VisDataRequest subrequest = new VisDataRequest();

            // let's collect the measures, by type
            VisDataRequest.MeasureInfo container = null;
            VisDataRequest.MeasureInfo participant = null;
            VisDataRequest.MeasureInfo sequencenum = null;
            List<VisDataRequest.MeasureInfo> datasetMeasures = new ArrayList<>();
            List<VisDataRequest.MeasureInfo> joinedMeasures = new ArrayList<>();

            for (VisDataRequest.MeasureInfo mi : _request.getMeasures())
            {
                VisDataRequest.Measure m = mi.getMeasure();
                Path measurePath = pathForMeasure(m);
                if (blacklist.contains(measurePath))
                {
                    continue;
                }
                boolean isDatasetMeasure = measurePath.equals(datasetPath);
                if (isDatasetMeasure)
                {
                    if (equalsIgnoreCase(m.getName(), containerColumnName))
                        container = mi;
                    else if (equalsIgnoreCase(m.getName(), subjectColumnName))
                        participant = mi;
                    else if (equalsIgnoreCase(m.getName(), sequenceNumColumnName))
                        sequencenum = mi;
                    else
                        datasetMeasures.add(mi);
                }
                else
                {
                    mi.getMeasure().setRequireLeftJoin(true);
                    joinedMeasures.add(mi);
                }
            }

            // Default to 'date' (same as Measure), but align ourselves with the measures if any are requested
            String timeType = "date";
            if (!datasetMeasures.isEmpty())
                timeType = datasetMeasures.get(0).getTime();

            if (null == container)
            {
                VisDataRequest.Measure cont = new VisDataRequest.Measure(datasetSchemaName, datasetQueryName, containerColumnName)
                        .setAxisName(axisName);
                container = new VisDataRequest.MeasureInfo(cont).setTime(timeType);
            }

            if (null == participant)
            {
                VisDataRequest.Measure subject = new VisDataRequest.Measure(datasetSchemaName, datasetQueryName, subjectColumnName)
                    .setAxisName(axisName);
                participant = new VisDataRequest.MeasureInfo(subject).setTime(timeType);
            }

            if (null == sequencenum)
            {
                VisDataRequest.Measure seqnum = new VisDataRequest.Measure(datasetSchemaName, datasetQueryName, sequenceNumColumnName)
                    .setAxisName(axisName);
                sequencenum = new VisDataRequest.MeasureInfo(seqnum).setTime(timeType);
            }

            // put the datasetMeasures first, so we can LEFT OUTER JOIN _starting_ from this dataset
            subrequest.addMeasure(container);
            subrequest.addMeasure(participant);
            subrequest.addMeasure(sequencenum);
            subrequest.addAll(datasetMeasures);
            subrequest.addAll(joinedMeasures);

            _request.getSorts().stream()
                    .filter(m -> !blacklist.contains(new Path(m.getSchemaName(), m.getQueryName())))
                    .forEach(subrequest::addSort);

            VisualizationSQLGenerator generator = new VisualizationSQLGenerator(getViewContext(), subrequest);
            generators.add(generator);
            generatedSql.add(generator.getSQL());

            if (_log.isDebugEnabled())
            {
                String sql = generator.getSQL();
                _log.debug("---------------------------------------\n" + datasetPath.toString() + "\n\n" + sql);
            }
        }


        //
        // Generate the UNION query
        //
        // We can get the SQL for each part of the union, but we have to rearrange columns to line up, and pad with NULLs.
        // Also we want to line up the common dataset columns (container, participant, sequencenum),
        // we the uri as the alias for these columns
        //

        // collect full set of union column names

        // this is the full list of union columns (except the shared key columns)
        LinkedHashMap<String,JdbcType> unionAliasList = new LinkedHashMap<>();

        // this is the collection for returned metadata
        List<Map<String, String>> columnAliases = new ArrayList<>();

        for (VisualizationSQLGenerator generator : generators)
        {
            List<VisualizationSourceColumn> list = generator.getColumns();
            for (VisualizationSourceColumn vcol : list)
            {
                String alias = StringUtils.defaultString(vcol.getClientAlias(), vcol.getAlias());
                if (isEmpty(alias))
                    continue;

                String columnName = vcol.getOriginalName();
                boolean isContainer = equalsIgnoreCase(columnName, containerColumnName);
                boolean isSubject = equalsIgnoreCase(columnName, subjectColumnName);
                boolean isSequenceNum = equalsIgnoreCase(columnName, sequenceNumColumnName);
                boolean isWhiteListQuery = "GridBase".equalsIgnoreCase(vcol.getQueryName());

                if ((!isContainer && !isSubject && !isSequenceNum) || isWhiteListQuery)
                {
                    if (null == unionAliasList.put(alias, vcol.getType()))
                        columnAliases.add(vcol.toJSON());
                }
            }
        }

        // for each VisualizationSQLGenerator, wrap SQL with outer SELECT to align columns

        StringBuilder fullSQL = new StringBuilder();
        String union = "";
        String datasetTableColumnName = "Dataset";
        String uriBase = "http://cpas.labkey.com/Study#";
        String selectAliasPrefix = uriBase;

        for (int i=0; i < generators.size(); i++)
        {
            Path datasetPath = datasetTables[i];
            VisualizationSQLGenerator generator = generators.get(i);
            String containerColumnAlias = null;
            String participantColumnAlias = null;
            String sequenceColumnAlias = null;

            // set of non-shared columns in this generator
            Set<String> aliasInCurrentSet = new HashSet<>();
            List<VisualizationSourceColumn> list = generator.getColumns();
            for (VisualizationSourceColumn sourceColumn : list)
            {
                String alias = StringUtils.defaultString(sourceColumn.getClientAlias(), sourceColumn.getAlias());
                if (isEmpty(alias))
                    continue;
                String schemaName = sourceColumn.getSchemaName();
                String queryName = sourceColumn.getQueryName();
                String columnName = sourceColumn.getOriginalName();

                if (datasetPath.startsWith(new Path(schemaName,queryName)))
                {
                    if (equalsIgnoreCase(columnName, containerColumnName))
                        containerColumnAlias = alias;
                    else if (equalsIgnoreCase(columnName, subjectColumnName))
                        participantColumnAlias = alias;
                    else if (equalsIgnoreCase(columnName, sequenceNumColumnName))
                        sequenceColumnAlias = alias;
                }
                aliasInCurrentSet.add(alias);
            }

            fullSQL.append(union); union = "\n  UNION ALL\n";
            fullSQL.append("SELECT ");

            // container column
            fullSQL.append(defaultString(containerColumnAlias, "CAST(NULL AS " + dialect.getGuidType() + ")")).append(" AS \"").append(selectAliasPrefix).append(containerColumnName).append("\"").append(", ");

            // subject column
            fullSQL.append(defaultString(participantColumnAlias, "NULL")).append(" AS \"").append(selectAliasPrefix).append(subjectColumnName).append("\"").append(", ");

            // sequenceNum column
            fullSQL.append(defaultString(sequenceColumnAlias, "CAST(NULL AS NUMERIC(15,4))")).append(" AS \"").append(selectAliasPrefix).append(sequenceNumColumnName).append("\"").append(", ");

            // dataset (or axis) name column
            fullSQL.append(string_quote(datasetTables[i].getName())).append(" AS \"").append(selectAliasPrefix).append(datasetTableColumnName).append("\"");

            for (Map.Entry<String,JdbcType> entry : unionAliasList.entrySet())
            {
                String alias = entry.getKey();
                JdbcType type = entry.getValue();
                if (aliasInCurrentSet.contains(alias))
                    fullSQL.append(", ").append('"').append(alias).append('"');
                else if (type != JdbcType.VARCHAR)
                    fullSQL.append(", ").append("CAST(NULL AS ").append(type.name()).append(") AS \"").append(alias).append('"');
                else
                    fullSQL.append(", ").append("NULL AS \"").append(alias).append('"');
            }
            fullSQL.append("\nFROM (").append(generatedSql.get(i)).append(") AS _").append(i);
        }

        if (_log.isDebugEnabled())
        {
            _log.debug("----------------------\nunion sql\n\n" + fullSQL.toString() + "\n\n");
            try (ResultSet rs = QueryService.get().select(schema.getSchema("study"), fullSQL.toString()))
            {
                ResultSetUtil.logData(rs, _log);
                _log.debug("\n\n");
            }
        }

        // key columns should display first in the columnAlias list
        _columnAliases = new ArrayList<>();
        _columnAliases.add(generateSharedColumnAlias(containerColumnName, uriBase, selectAliasPrefix));
        _columnAliases.add(generateSharedColumnAlias(subjectColumnName, uriBase, selectAliasPrefix));
        _columnAliases.add(generateSharedColumnAlias(sequenceNumColumnName, uriBase, selectAliasPrefix));
        _columnAliases.add(generateSharedColumnAlias(datasetTableColumnName, uriBase, selectAliasPrefix));
        _columnAliases.addAll(columnAliases);

        return fullSQL.toString();
    }


    private static Map<String,String> generateSharedColumnAlias(String columnName, String uriBase, String selectAliasPrefix)
    {
        return generateColumnAlias(uriBase + columnName, columnName, selectAliasPrefix + columnName);
    }

    private static Map<String, String> generateColumnAlias(String alias, String measureName, String columnName)
    {
        Map<String, String> colAlias = new HashMap<>();

        colAlias.put("alias", alias);
        colAlias.put("columnName", columnName);
        colAlias.put("measureName", measureName);

        return colAlias;
    }

    DialectStringHandler sh = null;

    private String string_quote(String s)
    {
        if (null == sh)
            sh = getPrimarySchema().getDbSchema().getSqlDialect().getStringHandler();
        return sh.quoteStringLiteral(s);
    }


    public static class TestCase extends Assert
    {
        final ViewContext context;

        public TestCase()
        {
            ViewContext ctx = new ViewContext();
            ctx.setContainer(JunitUtil.getTestContainer());
            ctx.setUser( TestContext.get().getUser());
            this.context = ctx;
        }

        Results getResults(VisDataRequest q) throws SQLGenerationException, SQLException
        {
            VisualizationCDSGenerator gen = new VisualizationCDSGenerator(context, q);
            UserSchema schema = new VisTestSchema(context.getUser(), context.getContainer());
            BindException errors = new NullSafeBindException(q,"query");
            String sql = gen.getSQL(errors);
            assertFalse(errors.hasErrors());
            return QueryService.get().selectResults(schema, sql, null, null, true, true);
        }

        List<Map<String,String>> getColumnAliases(VisDataRequest q) throws SQLGenerationException, SQLException
        {
            VisualizationCDSGenerator gen = new VisualizationCDSGenerator(context, q);
            UserSchema schema = new VisTestSchema(context.getUser(), context.getContainer());
            BindException errors = new NullSafeBindException(q,"query");
            gen.getSQL(errors);
            assertFalse(errors.hasErrors());
            return gen.getColumnAliases();
        }

        Stream<Double> streamDoubles(ResultSet rs, int i) throws Exception
        {
            ArrayList<Double> l = new ArrayList<>();
            rs.beforeFirst();
            while (rs.next())
            {
                double d = rs.getDouble(i);
                l.add(rs.wasNull() ? null : d);
            }
            rs.beforeFirst();
            return l.stream();
        }
        Stream<String> streamStrings(ResultSet rs, int i) throws Exception
        {
            ArrayList<String> l = new ArrayList<>();
            rs.beforeFirst();
            while (rs.next())
                l.add(rs.getString(i));
            rs.beforeFirst();
            return l.stream();
        }
        List<Map<String,Object>> toList(ResultSet rs) throws Exception
        {
            rs.beforeFirst();
            DataIterator di = ResultSetDataIterator.wrap(rs, new DataIteratorContext());
            return di.stream().collect(Collectors.toList());
            // closes result set
        }

        void dump(ResultSet rs) throws SQLException
        {
            rs.beforeFirst();
            ResultSetUtil.logData(rs);
        }

        VisDataRequest.Measure m(String query, String name)
        {
            // NOTE code/data for vis_junit schema can be found here VisTestSchema.java
            return new VisDataRequest.Measure().setSchemaName("vis_junit").setQueryName(query).setName(name).setAlias(("vis_junit_" + query + "_" + name).toLowerCase());
        }
        VisDataRequest.MeasureInfo mi(String query, String name, String time)
        {
            VisDataRequest.MeasureInfo mi = new VisDataRequest.MeasureInfo();
            mi.setMeasure(m(query, name)).setTime(time);
            return mi;
        }

        @Test
        public void twoDataset() throws Exception
        {
            VisDataRequest.MeasureInfo age = mi("demographics","age","visit");
            VisDataRequest.MeasureInfo study = mi("demographics","study","visit");
            VisDataRequest.MeasureInfo gender = mi("demographics","gender","visit");
            VisDataRequest.MeasureInfo measure1 = mi("flow","cellcount","visit");
            VisDataRequest.MeasureInfo measure2 = mi("ics","mfi","visit");

            VisDataRequest q = new VisDataRequest();
            q.addMeasure(study).addMeasure(gender);
            q.addMeasure(measure1); q.addMeasure(measure2);

            List<Map<String,String>> metadata = getColumnAliases(q);
            Map<String,Map<String,String>> metaMap = new TreeMap<>();
            metadata.stream().forEach(map -> metaMap.put(StringUtils.defaultString(map.get("alias"), map.get("columnName")), map));
            metadata.stream().forEach(map -> metaMap.put(StringUtils.defaultString(map.get("alias"),map.get("columnName")), map));
            assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#Container"));
            assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#ParticipantId"));
            assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#SequenceNum"));
            assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#Dataset"));
            assertTrue(metaMap.containsKey("vis_junit_flow_cellcount"));
            assertTrue(metaMap.containsKey("vis_junit_demographics_study"));
            assertTrue(metaMap.containsKey("vis_junit_demographics_gender"));
            assertTrue(metaMap.containsKey("vis_junit_ics_mfi"));

            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(384, r.getSize()); /* 2*192 */
                assertTrue(streamStrings(r, r.findColumn(new FieldKey(null, "http://cpas.labkey.com/Study#Dataset")))
                        .allMatch(s -> s.equals("flow") || s.equals("ics")));
            }
        }


        @Test
        public void sameDatasetTwice() throws Exception
        {
            VisDataRequest q = new VisDataRequest();

            // same dataset on both axes, unfiltered
            {
                VisDataRequest.MeasureInfo age = mi("demographics", "age", "visit");
                VisDataRequest.MeasureInfo study = mi("demographics", "study", "visit");
                VisDataRequest.MeasureInfo gender = mi("demographics", "gender", "visit");
                VisDataRequest.MeasureInfo measureX = mi("flow", "cellcount", "visit");
                measureX.getMeasure().setAxisName("x");
                VisDataRequest.MeasureInfo measureY = mi("flow", "cellcount", "visit");
                measureY.getMeasure().setAxisName("y");

                q.addMeasure(study).addMeasure(gender);
                q.addMeasure(measureX);
                q.addMeasure(measureY);

                List<Map<String, String>> metadata = getColumnAliases(q);
                Map<String, Map<String, String>> metaMap = new TreeMap<>();
                metadata.stream().forEach(map -> metaMap.put(StringUtils.defaultString(map.get("alias"), map.get("columnName")), map));
                assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#Container"));
                assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#ParticipantId"));
                assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#SequenceNum"));
                assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#Dataset"));
                assertTrue(metaMap.containsKey("vis_junit_flow_cellcount"));
                assertTrue(metaMap.containsKey("vis_junit_demographics_study"));
                assertTrue(metaMap.containsKey("vis_junit_demographics_gender"));

                try (ResultsImpl r = (ResultsImpl) getResults(q))
                {
                    ;
                    assertEquals(384, r.getSize()); /* 2*192 */
                    assertTrue(streamStrings(r, r.findColumn(new FieldKey(null, "http://cpas.labkey.com/Study#Dataset")))
                            .allMatch(s -> s.equals("x") || s.equals("y")));
                    assertTrue(streamStrings(r, r.findColumn(new FieldKey(null,"http://cpas.labkey.com/Study#Container")))
                            .allMatch(StringUtils::isNotEmpty));
                    assertTrue(streamStrings(r, r.findColumn(new FieldKey(null,"http://cpas.labkey.com/Study#ParticipantId")))
                            .allMatch(StringUtils::isNotEmpty));
                    assertTrue(streamStrings(r, r.findColumn(new FieldKey(null,"http://cpas.labkey.com/Study#SequenceNum")))
                            .allMatch(StringUtils::isNotEmpty));
                }
            }

            // different filters on the two axes
            {
                VisDataRequest.MeasureInfo measureXpop = mi("flow", "population", "visit");
                measureXpop.getMeasure().setAxisName("x");
                measureXpop.getMeasure().setValues(Arrays.asList("CD4"));

                VisDataRequest.MeasureInfo measureYpop = mi("flow", "population", "visit");
                measureYpop.getMeasure().setAxisName("y");
                measureYpop.getMeasure().setValues(Arrays.asList("CD8"));

                q.addMeasure(measureXpop).addMeasure(measureYpop);

                List<Map<String, String>> metadata = getColumnAliases(q);
                Map<String, Map<String, String>> metaMap = new TreeMap<>();
                metadata.stream().forEach(map -> metaMap.put(StringUtils.defaultString(map.get("alias"), map.get("columnName")), map));
                assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#Container"));
                assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#ParticipantId"));
                assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#SequenceNum"));
                assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#Dataset"));
                assertTrue(metaMap.containsKey("vis_junit_flow_cellcount"));
                assertTrue(metaMap.containsKey("vis_junit_flow_population"));
                assertTrue(metaMap.containsKey("vis_junit_demographics_study"));
                assertTrue(metaMap.containsKey("vis_junit_demographics_gender"));

                try (ResultsImpl r = (ResultsImpl) getResults(q))
                {
                    assertEquals(192, r.getSize()); /* 2*192 */
                    assertTrue(streamStrings(r, r.findColumn(new FieldKey(null,"http://cpas.labkey.com/Study#Dataset")))
                            .allMatch(s -> s.equals("x") || s.equals("y")));

                    ColumnInfo dsCol = r.getColumnInfo(r.findColumn(new FieldKey(null,"http://cpas.labkey.com/Study#Dataset")));
                    List<Map<String, Object>> list = toList(r);
                    assertTrue(list.stream()
                            .filter(m -> StringUtils.equals((String) m.get(dsCol.getAlias()), "x"))
                            .allMatch(m -> StringUtils.equals((String) m.get("vis_junit_flow_population"), "CD4")));
                    assertTrue(list.stream()
                            .filter(m -> StringUtils.equals((String) m.get(dsCol.getAlias()), "y"))
                            .allMatch(m -> StringUtils.equals((String) m.get("vis_junit_flow_population"), "CD8")));
                }
            }
        }
    }
}
