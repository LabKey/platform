/*
 * Copyright (c) 2015 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.ResultsImpl;
import org.labkey.api.data.Sort;
import org.labkey.api.data.dialect.DialectStringHandler;
import org.labkey.api.query.DefaultSchema;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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


    public String getSQL(BindException errors) throws SQLGenerationException, SQLException
    {
        // pre analyze the request, and break down the tables into two groups
        // the non-dataset tables and the dataset tables
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
            if (isDataset)
                datasetTablesSet.add(new Path(m.getSchemaName(), m.getQueryName()));
            else
                nonDatasetTablesSet.add(new Path(m.getSchemaName(), m.getQueryName()));
        }
        Path[] datasetTables = datasetTablesSet.toArray(new Path[datasetTablesSet.size()]);

        // VALIDATION

        // since we are wrapping the getData() API, I want to be strict and 'white-list' functionality,
        // rather than passing through options without validating the behavior
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

        if (datasetTablesSet.size() <= 1)
        {
            VisualizationSQLGenerator sqlGenerator = new VisualizationSQLGenerator(getViewContext(), _request);

            // use the default columnAliases
            _columnAliases = sqlGenerator.getColumnAliases();

            return sqlGenerator.getSQL();
        }

        List<VisualizationSQLGenerator> generators = new ArrayList<>();
        List<String> generatedSql = new ArrayList<>();
        String containerColumnName = "Container";
        String subjectColumnName = "ParticipantId";
        String sequenceNumColumnName = "SequenceNum";
        if (null != study)
            subjectColumnName = study.getSubjectColumnName();

        DefaultSchema schema = DefaultSchema.get(getUser(), getContainer());
        for (Path datasetPath : datasetTablesSet)
        {
            String datasetQueryName = datasetPath.getName();
            String datasetSchemaName = datasetPath.getParent().getName();

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
                if (blacklist.contains(new Path(m.getSchemaName(), m.getQueryName())))
                {
                    continue;
                }
                boolean isDatasetMeasure = equalsIgnoreCase(m.getSchemaName(), datasetSchemaName) && equalsIgnoreCase(m.getQueryName(), datasetQueryName);
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
                        .setAlias((datasetSchemaName + "_" + datasetQueryName + "_" + containerColumnName).toLowerCase());
                container = new VisDataRequest.MeasureInfo(cont).setTime(timeType);
            }

            if (null == participant)
            {
                VisDataRequest.Measure subject = new VisDataRequest.Measure(datasetSchemaName, datasetQueryName, subjectColumnName)
                        .setAlias((datasetSchemaName + "_" + datasetQueryName + "_" + subjectColumnName).toLowerCase());
                participant = new VisDataRequest.MeasureInfo(subject).setTime(timeType);
            }

            if (null == sequencenum)
            {
                VisDataRequest.Measure seqnum = new VisDataRequest.Measure(datasetSchemaName, datasetQueryName, sequenceNumColumnName)
                        .setAlias((datasetSchemaName + "_" + datasetQueryName + "_" + sequenceNumColumnName).toLowerCase());
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
                _log.debug("\n" + sql);
//                try (ResultSet rs = QueryService.get().select(schema.getSchema("study"), sql);)
//                {
//                    ResultSetUtil.logData(rs, _log);
//                    _log.debug("\n\n");
//                }
            }
        }

        // now comes the annoying part
        // we can get the SQL for each part of the union, but we have to rearrange columns to line up, and pad with NULLs
        LinkedHashSet<String> fullAliasList = new LinkedHashSet<>();
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
                Path queryPath = new Path(vcol.getSchemaName(), vcol.getQueryName());

                if (!datasetTablesSet.contains(queryPath) || (
                        !equalsIgnoreCase(columnName, containerColumnName) &&
                        !equalsIgnoreCase(columnName, subjectColumnName) &&
                        !equalsIgnoreCase(columnName, sequenceNumColumnName)))
                {
                    if (fullAliasList.add(alias))
                        columnAliases.add(vcol.toJSON());
                }
            }
        }

        StringBuilder fullSQL = new StringBuilder();
        String union = "";
        String datasetTableColumnName = "Dataset";
        String URI = "http://cpas.labkey.com/Study#";
        List<Map<String, String>> keyColumnAlias = new ArrayList<>();

        for (int i=0; i < generators.size(); i++)
        {
            VisualizationSQLGenerator generator = generators.get(i);
            String containerColumnAlias = null;
            String participantColumnAlias = null;
            String sequenceColumnAlias = null;

            Set<String> aliasSet = new HashSet<>();
            List<VisualizationSourceColumn> list = generator.getColumns();
            for (VisualizationSourceColumn sourceColumn : list)
            {
                String alias = StringUtils.defaultString(sourceColumn.getClientAlias(), sourceColumn.getAlias());
                if (isEmpty(alias))
                    continue;
                String columnName = sourceColumn.getOriginalName();

                if (null == containerColumnAlias && equalsIgnoreCase(columnName, containerColumnName))
                    containerColumnAlias = alias;
                else if (null == participantColumnAlias && equalsIgnoreCase(columnName, subjectColumnName))
                    participantColumnAlias = alias;
                else if (null == sequenceColumnAlias && equalsIgnoreCase(columnName, sequenceNumColumnName))
                    sequenceColumnAlias = alias;

                aliasSet.add(alias);
            }

            fullSQL.append(union); union = "\n  UNION ALL\n";
            fullSQL.append("SELECT ");

            // container column
            fullSQL.append(defaultString(containerColumnAlias, "NULL")).append(" AS \"").append(URI).append(containerColumnName).append("\"").append(", ");

            // subject column
            fullSQL.append(defaultString(participantColumnAlias, "NULL")).append(" AS \"").append(URI).append(subjectColumnName).append("\"").append(", ");

            // sequenceNum column
            fullSQL.append(defaultString(sequenceColumnAlias, "NULL")).append(" AS \"").append(URI).append(sequenceNumColumnName).append("\"").append(", ");

            // dataset name column
            fullSQL.append(string_quote(datasetTables[i].getName())).append(" AS \"").append(URI).append(datasetTableColumnName).append("\"");

            // only need to gather alias information for the initial generator
            if (keyColumnAlias.isEmpty())
            {
                // container column
                keyColumnAlias.add(generateColumnAlias(URI + containerColumnName, containerColumnName));

                // subject column
                keyColumnAlias.add(generateColumnAlias(URI + subjectColumnName, subjectColumnName));

                // sequenceNum column
                keyColumnAlias.add(generateColumnAlias(URI + sequenceNumColumnName, sequenceNumColumnName));

                // dataset name column
                keyColumnAlias.add(generateColumnAlias(URI + datasetTableColumnName, datasetTableColumnName));
            }

            for (String alias : fullAliasList)
            {
                if (aliasSet.contains(alias))
                    fullSQL.append(", ").append('"').append(alias).append('"');
                else
                    fullSQL.append(", ").append("NULL AS \"").append(alias).append('"');
            }
            fullSQL.append(" FROM (").append(generatedSql.get(i)).append(") AS _").append(i);
        }

        if (_log.isDebugEnabled())
        {
            _log.debug(fullSQL.toString());
            try (ResultSet rs = QueryService.get().select(schema.getSchema("study"), fullSQL.toString()))
            {
                ResultSetUtil.logData(rs, _log);
                _log.debug("\n\n");
            }
        }

        // key columns should display first in the columnAlias list
        _columnAliases = new ArrayList<>();
        _columnAliases.addAll(keyColumnAlias);
        _columnAliases.addAll(columnAliases);

        return fullSQL.toString();
    }


    private static Map<String, String> generateColumnAlias(String columnName, String measureName)
    {
        Map<String, String> colAlias = new HashMap<>();

        colAlias.put("alias", columnName);
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
        public void tryme() throws Exception
        {
            VisDataRequest.MeasureInfo age = mi("demographics","age","visit");
            VisDataRequest.MeasureInfo study = mi("demographics","study","visit");
            VisDataRequest.MeasureInfo gender = mi("demographics","gender","visit");
            VisDataRequest.MeasureInfo measure1 = mi("flow","cellcount","visit");      // shouldn't need to specify time=visit
            VisDataRequest.MeasureInfo measure2 = mi("ics","mfi","visit");      // shouldn't need to specify time=visit

            VisDataRequest q = new VisDataRequest();
            q.addMeasure(study).addMeasure(gender);
            q.addMeasure(measure1); q.addMeasure(measure2);

            try (ResultsImpl r = (ResultsImpl)getResults(q))
            {
                assertEquals(384, r.getSize()); /* 2*192 */
            }

            List<Map<String,String>> metadata = getColumnAliases(q);
            Map<String,Map<String,String>> metaMap = new TreeMap<>();
            metadata.stream().forEach(map -> metaMap.put(StringUtils.defaultString(map.get("alias"),map.get("columnName")), map));
            assertEquals(9, metaMap.size());
            assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#Container"));
            assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#ParticipantId"));
            assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#SequenceNum"));
            assertTrue(metaMap.containsKey("http://cpas.labkey.com/Study#Dataset"));
            assertTrue(metaMap.containsKey("vis_junit_demographics_participantid"));
            assertTrue(metaMap.containsKey("vis_junit_flow_cellcount"));
            assertTrue(metaMap.containsKey("vis_junit_demographics_study"));
            assertTrue(metaMap.containsKey("vis_junit_demographics_gender"));
            assertTrue(metaMap.containsKey("vis_junit_ics_mfi"));
        }
    }
}
