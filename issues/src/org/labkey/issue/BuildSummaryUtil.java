package org.labkey.issue;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.util.GUID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.labkey.issue.IssuesController.*;
/**
 * Created by xingyang on 9/22/16.
 */
public class BuildSummaryUtil
{
    public static BuildSummaryBean populateBuildSummaryBean(BuildSummaryBean inputBean)
    {
        List<IssuesController.BuildIssue> allIssues = getFilteredBuildIssues(inputBean);
        return constructBuildSummaryBean(allIssues);
    }

    public static BuildSummaryBean constructBuildSummaryBean(List<BuildIssue> allIssues)
    {
        BuildSummaryBean summaryBean = new BuildSummaryBean();
        Map<String, List<BuildIssue>> verifiedIssues = new HashMap<>();
        Map<String, List<BuildIssue>> unverifiedIssues = new HashMap<>();

        for (BuildIssue issue: allIssues)
        {
            if ("closed".equalsIgnoreCase(issue.getStatus()))
            {
                if (!verifiedIssues.containsKey(issue.getArea()))
                {
                    List<BuildIssue> areaIssues = new ArrayList<>();
                    verifiedIssues.put(issue.getArea(), areaIssues);
                }
                verifiedIssues.get(issue.getArea()).add(issue);
            }
            else
            {
                if (!unverifiedIssues.containsKey(issue.getArea()))
                {
                    List<BuildIssue> areaIssues = new ArrayList<>();
                    unverifiedIssues.put(issue.getArea(), areaIssues);
                }
                unverifiedIssues.get(issue.getArea()).add(issue);
            }
        }

        Map<String, Map<String, List<BuildIssue>>> summarizedIssues = new HashMap<>();
        if (!verifiedIssues.isEmpty())
        {
            summarizedIssues.put("Verified Issues", verifiedIssues);
        }

        if (!unverifiedIssues.isEmpty())
        {
            summarizedIssues.put("unverified Issues", unverifiedIssues);
        }
        summaryBean.setSummarizedIssues(summarizedIssues);
        return summaryBean;
    }

    public static List<BuildIssue> getFilteredBuildIssues(BuildSummaryBean inputBean)
    {
        //TODO remove dummy test data and switch to query
        List<BuildIssue> allIssues = new ArrayList<>();
//        BuildIssue issue1 = new BuildIssue(1, "bug", "ITN", "Data finder not working", "closed", "16.3", null, null, "ITN");
//        BuildIssue issue2 = new BuildIssue(2, "bug", "ITN", "Publications missing", "closed", "16.2", null, null, "ITN");
//        BuildIssue issue3 = new BuildIssue(3, "bug", "ITN", "Studies missing", "closed", "16.3", null, null, "ITN");
//        BuildIssue issue4 = new BuildIssue(4, "bug", "ITN", "ITN Study finder not working", "resolved", "16.3", null, null, "ITN");
//        BuildIssue issue5 = new BuildIssue(5, "bug", "ITN", "Bad styling", "resolved", "16.3", null, null, "ITN");
//        BuildIssue issue6 = new BuildIssue(6, "bug", "ITN", "Page takes too long to load", "resolved", "16.2", null, null, "ITN");
//        BuildIssue issue7 = new BuildIssue(7, "bug", "CDS", "Info pane finder not working", "closed", "16.3", null, null, "CDS");
//        BuildIssue issue8 = new BuildIssue(8, "bug", "CDS", "Learn about not working in iE", "closed", "16.3", null, null, "CDS");
//        BuildIssue issue9 = new BuildIssue(9, "bug", "CDS", "Learn about performance issue", "closed", "16.2", null, null, "CDS");
//        allIssues.add(issue1);
//        allIssues.add(issue2);
//        allIssues.add(issue3);
//        allIssues.add(issue4);
//        allIssues.add(issue5);
//        allIssues.add(issue6);
//        allIssues.add(issue7);
//        allIssues.add(issue8);
//        allIssues.add(issue9);

        return allIssues;


        //            TableInfo table = IssuesSchema.getInstance().getTableInfoIssues();
//            Collection<ColumnInfo> columns = table.getColumns();
//            Filter idFilter = new SimpleFilter(FieldKey.fromString("IssueDefId"), issueListDef.getRowId());
//            TableSelector selector = new TableSelector(table, columns, idFilter, null);
//            ArrayList<BuildIssue> buildIssues;
//            Container c = getContainer();
//            User user = getUser();
//
//            try (TableResultSet rs = selector.getResultSet())
//            {
//                buildIssues = new ArrayList<>(rs.getSize());
//                while (rs.next())
//                {
//                    Issue issue = IssueManager.getIssue(c, user, rs.getInt("IssueId"));
//                    Date resolvedDate = issue.getResolved();
//                    Date closedDate = issue.getClosed();
//                    Instant resolvedInstant = (resolvedDate != null) ? resolvedDate.toInstant() : null;
//                    Instant closedInstant = (closedDate != null) ? closedDate.toInstant() : null;
//                    String client = (String)issue.getProperties().get("client");
//
//                    if((client != null) && (client.equals("ITN")))
//                    {
//                        final BuildIssue buildIssue = new BuildIssue(
//                                issue.getIssueId(),
//                                issue.getType(),
//                                issue.getArea(),
//                                issue.getTitle(),
//                                issue.getStatus(),
//                                issue.getMilestone(),
//                                resolvedInstant,
//                                closedInstant,
//                                client
//                        );
//                        buildIssues.add(buildIssue);
//                    }
//                }
//            }

    }


}
