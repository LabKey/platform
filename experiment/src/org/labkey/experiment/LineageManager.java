package org.labkey.experiment;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpProtocolOutput;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.Pair;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LineageManager
{
    static final String exp_graph_sql2;
    static final String exp_graph_sql2_for_lookup="";

    static
    {
        try
        {
            String sql = IOUtils.toString(ExperimentServiceImpl.class.getResourceAsStream("ExperimentRunGraph2.sql"), "UTF-8");
            if (DbSchema.get("exp", DbSchemaType.Module).getSqlDialect().isPostgreSQL())
                exp_graph_sql2 = StringUtils.replace(StringUtils.replace(sql, "$RECURSIVE$", "RECURSIVE"), "$VARCHAR$", "VARCHAR");
            else
                exp_graph_sql2 = StringUtils.replace(StringUtils.replace(StringUtils.replace(sql, "$RECURSIVE$", ""), "$VARCHAR$", "NVARCHAR"), "||", "+");

//            sql = IOUtils.toString(ExperimentServiceImpl.class.getResourceAsStream("ExperimentRunGraphForLookup2.sql"), "UTF-8");
//            if (DbSchema.get("exp", DbSchemaType.Module).getSqlDialect().isPostgreSQL())
//                exp_graph_sql2_for_lookup = StringUtils.replace(StringUtils.replace(sql, "$RECURSIVE$", "RECURSIVE"), "$VARCHAR$", "VARCHAR");
//            else
//                exp_graph_sql2_for_lookup = StringUtils.replace(StringUtils.replace(StringUtils.replace(sql, "$RECURSIVE$", ""), "$VARCHAR$", "NVARCHAR"), "||", "+");
        }
        catch (IOException x)
        {
            throw new ConfigurationException("Cannot read file ExperimentRunGraph.sql");
        }
    }


//    public static ExpLineage getLineage2(ExpProtocolOutput start, ExpLineageOptions options)
//    {
//        if (ExperimentServiceImpl.get().isUnknownMaterial(start))
//            return new ExpLineage(start);
//
//        Pair<Map<String, String>, Map<String, String>> pair = ExperimentServiceImpl.get().collectRunsAndRolesToInvestigate(start, options);
//        List<String> runLsids = new ArrayList<>(pair.first.size() + pair.second.size());
//        runLsids.addAll(pair.first.keySet());
//        runLsids.addAll(pair.second.keySet());
//        if (runLsids.isEmpty())
//            return new ExpLineage(start);
//
//        SQLFragment sqlf = generateExperimentTreeSQL(runLsids, options);
//        Set<Integer> dataids = new HashSet<>();
//        Set<Integer> materialids = new HashSet<>();
//        Set<Integer> runids = new HashSet<>();
//        Set<ExpLineage.Edge> edges = new HashSet<>();
//
//        // add edges for initial runs and roles up
//        for (Map.Entry<String, String> runAndRole : pair.first.entrySet())
//            edges.add(new ExpLineage.Edge(runAndRole.getKey(), start.getLSID(), runAndRole.getValue()));
//
//        // add edges for initial runs and roles down
//        for (Map.Entry<String, String> runAndRole : pair.second.entrySet())
//            edges.add(new ExpLineage.Edge(start.getLSID(), runAndRole.getKey(), runAndRole.getValue()));
//
//
//    }


}
