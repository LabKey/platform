package org.labkey.issue;

import org.labkey.api.util.GUID;

import java.util.ArrayList;
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

    public static String getBuildSummaryContentHTML(BuildSummaryBean bean)
    {
        StringBuilder builder = new StringBuilder();
        //TODO add css

        // javascript
        builder.append("<script type=\"text/javascript\">\n");
        builder.append("function expandCollapse(id,showClassName){\n");
        builder.append("var imgId = 'img_'+ id;");
        builder.append("if(document.getElementById(id).className == 'collapsed'){\n");
        builder.append("document.getElementById(id).className = 'expanded';\n");
        builder.append("document.getElementById(imgId).src=LABKEY.contextPath + \"/_images/minus.gif\";\n");
        builder.append("}\n");
        builder.append("else{\n");
        builder.append("document.getElementById(id).className = 'collapsed';\n");
        builder.append("document.getElementById(imgId).src = LABKEY.contextPath + \"/_images/plus.gif\";\n");
        builder.append("}\n");
        builder.append("}\n");
        builder.append("</script>");

        // header info
        builder.append("<p>Note: This list is missing secure issues. If you’re waiting for one of these issues to get fixed, please follow up with the LabKey support team </p>");

        // issues expando
        if (bean.getVerifiedAreaIssues().keySet().size() == 0
                && bean.getUnverifiedAreaIssues().keySet().size() == 0)
            return "";

        builder.append("<div style=\"display:block; margin-top:10px\"></div>\n");
        String sectionExpandoId = "summary_" + GUID.makeGUID();
        builder.append(buildExpandoScript(sectionExpandoId));
        builder.append("<h3>Issues</h3>\n");
        builder.append("<br style=\"clear:both\">\n");

        builder.append("<div class=\"collapsed\" style=\"margin-top:-20px\" id=\"");
        builder.append(sectionExpandoId);
        builder.append("\">\n");
        builder.append("<ul style=\"list-style-type: none;\">\n");
        String verifiedExpandoId = "verified_" + GUID.makeGUID();
        String unverifiedExpandoId = "unverified_" + GUID.makeGUID();
        builder.append(buildIssueTypeSection(bean.getVerifiedAreaIssues(), verifiedExpandoId, "Verified Issues"));
        builder.append(buildIssueTypeSection(bean.getUnverifiedAreaIssues(), unverifiedExpandoId, "Unverified Issues"));
        builder.append("</ul>\n");
        builder.append("</div>\n");

        return builder.toString();
    }

    public static String buildIssueTypeSection(Map<String, List<BuildIssue>> areaIssues, String typeId, String typeTitle)
    {
        if (areaIssues == null || areaIssues.isEmpty())
            return "";

        StringBuilder builder = new StringBuilder();

        builder.append("<li>\n");
        builder.append(buildExpandoScript(typeId));
        builder.append("<h3>").append(typeTitle).append("</h3>\n");
        builder.append("<br style=\"clear:both\">\n");

        builder.append("<div class=\"collapsed\" style=\"margin-top:-20px\" id=\"");
        builder.append(typeId);
        builder.append("\">\n");
        builder.append("<ul style=\"list-style-type: none;\">\n");
        for (String areaTitle : areaIssues.keySet())
        {
            String issueExpandoId = "issue_" + GUID.makeGUID();
            builder.append(buildIssueAreaSection(areaIssues.get(areaTitle), issueExpandoId, areaTitle));
        }
        builder.append("</ul>\n");
        builder.append("</div>\n");
        builder.append("</li>\n");
        return builder.toString();
    }

    public static String buildIssueAreaSection(List<BuildIssue> issues, String areaId, String areaTitle)
    {
        if (issues.isEmpty())
            return "";
        StringBuilder builder = new StringBuilder();

        builder.append("<li>\n");
        builder.append(buildExpandoScript(areaId));
        builder.append("<h3>").append(areaTitle).append("</h3>\n");
        builder.append("<br style=\"clear:both\">\n");

        builder.append("<div class=\"collapsed\" style=\"margin-top:-20px\" id=\"");
        builder.append(areaId);
        builder.append("\">\n");
        builder.append("<ul class=\"collapsiblelist\">\n");
        for (BuildIssue issue : issues)
        {
            builder.append("<li>");
            builder.append(issue.getTitle());
            builder.append("</li>\n");
        }
        builder.append("</ul>\n");
        builder.append("</div>\n");

        builder.append("</li>\n");
        return builder.toString();
    }

    public static String buildExpandoScript(String sectionId)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<a href=\"javascript:expandCollapse('");
        builder.append(sectionId);
        builder.append("');\">\n");
        builder.append("<img style=\"padding-top:4px;padding-right:5px\" border=\"0\" align=\"left\" id=\"");
        builder.append("img_").append(sectionId);
        builder.append("\" src=\"/labkey/_images/plus.gif\">\n");
        builder.append("</a>\n");
        return builder.toString();
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
        summaryBean.setVerifiedAreaIssues(verifiedIssues);
        summaryBean.setUnverifiedAreaIssues(unverifiedIssues);
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
