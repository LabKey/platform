package org.labkey.experiment.api.data;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.experiment.api.ExperimentServiceImpl;

import java.util.List;

public class LineageHelper
{
    static ExpRunItem getStart(String lsid)
    {
        if (lsid == null)
            return null;

        ExperimentServiceImpl svc = ExperimentServiceImpl.get();
        ExpRunItem start = svc.getExpMaterial(lsid);
        if (start == null)
            start = svc.getExpData(lsid);

        if (start == null || svc.isUnknownMaterial(start))
            return null;

        return start;
    }

    static SQLFragment createExperimentTreeSQLLsidSeeds(ExpRunItem start, ExpLineageOptions options)
    {
        if (start == null)
            return new SQLFragment("(1 = 2)");

        ExperimentServiceImpl svc = ExperimentServiceImpl.get();
        List<String> runsToInvestigate = svc.collectRunsToInvestigate(start, options);
        if (runsToInvestigate.isEmpty())
            return new SQLFragment("(1 = 2)");

        return svc.generateExperimentTreeSQLLsidSeeds(runsToInvestigate, options);
    }

    static ExpLineageOptions createChildOfOptions(int depth)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setForLookup(true);
        options.setParents(false);
        options.setChildren(true);
        options.setDepth(depth);
        return options;
    }

    static ExpLineageOptions createParentOfOptions(int depth)
    {
        ExpLineageOptions options = new ExpLineageOptions();
        options.setForLookup(true);
        options.setParents(true);
        options.setChildren(false);
        options.setDepth(depth);
        return options;
    }

    static SQLFragment createInSQL(SQLFragment fieldKeyFrag, SQLFragment lsidFrag, ExpLineageOptions options)
    {
        String lsidStr = lsidFrag.getRawSQL();

        // FIXME remove the quote workaround from client and here when parser is able to parse parts correctly
        // https://www.labkey.org/home/Developer/issues/issues-details.view?issueId=39996
        // our parser is currently not able to parse a where clause whose value contains special character such as :
        // Fail to parse
                // (ExpChildOf(resultsample.LSID, urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522) OR ExpChildOf(Run.runsample.LSID, urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522))
        // Workaround on client side (add quotes) so it can be parsed
                // (ExpChildOf(resultsample.LSID, 'urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522') OR ExpChildOf(Run.runsample.LSID, 'urn:lsid:labkey.com:Data.Folder-123:1aec9396-3fa2-1038-86f4-495e1672e522'))
        if (lsidStr != null)
        {
            if ((lsidStr.startsWith("'") && lsidStr.endsWith("'")) || ((lsidStr.startsWith("\"") && lsidStr.endsWith("\""))))
            {
                lsidStr = lsidStr.substring(1, lsidStr.length() - 1);
            }
        }

        ExpRunItem start = getStart(lsidStr);
        SQLFragment tree = createExperimentTreeSQLLsidSeeds(start, options);

        SQLFragment sql = new SQLFragment();
        sql.append("(").append(fieldKeyFrag).append(") IN (");
        sql.append("SELECT ").append(getLsidColumn()).append(" FROM (");
        sql.append(tree);
        sql.append(") AS X)");

        return sql;
    }

    static String getLsidColumn()
    {
        return "lsid";
    }

}
