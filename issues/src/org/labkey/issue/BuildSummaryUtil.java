package org.labkey.issue;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.labkey.api.view.ActionURL;
import org.labkey.issue.IssuesController.*;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.query.IssuesQuerySchema;

public class BuildSummaryUtil
{
    public static List<String> excludedIssueTypes;
    static
    {
        excludedIssueTypes = new ArrayList<>();
        excludedIssueTypes.add("Documentation");
        excludedIssueTypes.add("Specification");
        excludedIssueTypes.add("Spec issue");
        excludedIssueTypes.add("Test");
    }

    public static BuildSummaryBean populateBuildSummaryBean(IssueListDef issueListDef, ActionURL inputUrl, Container c, User user) throws SQLException
    {
        List<IssuesController.BuildIssue> allIssues = getBuildIssues(issueListDef, inputUrl, c, user);
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
            summarizedIssues.put("Unverified Issues", unverifiedIssues);
        }
        summaryBean.setSummarizedIssues(summarizedIssues);
        return summaryBean;
    }

    public static List<BuildIssue> getBuildIssues(IssueListDef issueListDef, ActionURL inputUrl, @Nullable Container c, User user)
    {
        String targetClient = inputUrl.getParameter("client");
        BuildBean currentBuild = new BuildBean(inputUrl.getParameter("currentBuildType"),
                inputUrl.getParameter("currentBuildDate"),
                inputUrl.getParameter("currentMilestone"),
                null,
                inputUrl.getParameter("currentSprintEndDate"));
        if (currentBuild.getMilestone().getReleaseVersion() == null)
            throw new IllegalArgumentException("invalid currentMilestone");

        BuildBean previousBuild = new BuildBean(inputUrl.getParameter("previousBuildType"),
                inputUrl.getParameter("previousBuildDate"),
                inputUrl.getParameter("previousMilestone"),
                inputUrl.getParameter("previousFirstSprintStartDate"),
                null);

        if (previousBuild.getMilestone().getReleaseVersion() == null)
            throw new IllegalArgumentException("invalid previousMilestone");

        String previousOfficialReleaseDateStr = inputUrl.getParameter("previousOfficialReleaseDate");
        Instant previousOfficialReleaseDate = previousOfficialReleaseDateStr == null ? null : Instant.parse(previousOfficialReleaseDateStr);

        List<BuildIssue> issues = new ArrayList<>();
        UserSchema userSchema = QueryService.get().getUserSchema(user, c, IssuesQuerySchema.SCHEMA_NAME);
        TableInfo table = userSchema.getTable(issueListDef.getName());

        SimpleFilter filter = buildFilter(previousOfficialReleaseDate);

        if (table != null)
        {
            try (Results rs = QueryService.get().select(table, table.getColumns(), filter, null, null, false))
            {
                while (rs.next())
                {
                    Issue issue = new Issue();
                    Map<String, Object> rowMap = new CaseInsensitiveHashMap<>();
                    for (String colName : table.getColumnNameSet())
                    {
                        Object value = rs.getObject(FieldKey.fromParts(colName));
                        if (value != null)
                            rowMap.put(colName, value);
                        if (colName.equalsIgnoreCase("IssueId"))
                            issue.setIssueId((Integer) value);;
                    }
                    issue.setProperties(rowMap);


                    String client = (String)issue.getProperties().get("Client");
                    if((client == null || client.equals(targetClient)) && isIssueIncluded(issue, previousBuild, currentBuild))
                    {
                        Date resolvedDate = issue.getResolved();
                        Date closedDate = issue.getClosed();
                        Instant resolvedInstant = (resolvedDate != null) ? resolvedDate.toInstant() : null;
                        Instant closedInstant = (closedDate != null) ? closedDate.toInstant() : null;

                        BuildIssue buildIssue = new BuildIssue(
                                issue.getIssueId(),
                                issue.getType(),
                                issue.getArea(),
                                issue.getTitle(),
                                issue.getStatus(),
                                issue.getMilestone(),
                                resolvedInstant,
                                closedInstant,
                                client
                        );
                        issues.add(buildIssue);
                    }

                }

            }
            catch (SQLException e)
            {
                throw new RuntimeException(e);
            }
        }
        else
            return issues;

        return issues;
    }

    public static SimpleFilter buildFilter(Instant previousOfficialReleaseDate)
    {
        SimpleFilter filters = new SimpleFilter();
        List<String> issueStatuses = new ArrayList<>();
        issueStatuses.add(Issue.statusRESOLVED);
        issueStatuses.add(Issue.statusCLOSED);
        filters.addInClause(FieldKey.fromString("Status"), issueStatuses);
        filters.addCondition(FieldKey.fromString("Type"), excludedIssueTypes, CompareType.NOT_IN);
        filters.addCondition(FieldKey.fromString("Story"), null, CompareType.ISBLANK);
        if (previousOfficialReleaseDate == null)
            throw new IllegalArgumentException("previousOfficialReleaseDate cannot be null");
        filters.addCondition(FieldKey.fromString("Resolved"), previousOfficialReleaseDate, CompareType.DATE_GT);
        //TODO security issues should be automatically filtered out based on container, need to verify.

        return filters;
    }

    private static boolean isIssueIncluded(Issue issue, BuildBean previousBuild, BuildBean currentBuild)
    {
        Milestone issueMilestone = new Milestone(issue.getMilestone());
        if (issueMilestone.compareTo(currentBuild.getMilestone()) > 0)
            return false;

        Date lastResolvedClosed = issue.getClosed() != null ? issue.getClosed() : issue.getResolved();
        if (previousBuild.isStableBuild())
        {
            if (previousBuild.getbuildDate() == null || currentBuild.getbuildDate() == null)
                throw new IllegalArgumentException("previousBuildDate and currentBuildDate cannot be null");
            if (lastResolvedClosed.toInstant().compareTo(previousBuild.getbuildDate()) > 0 && issueMilestone.compareTo(previousBuild.getMilestone()) == 0)
                return true;
            if (issue.getResolved().toInstant().compareTo(currentBuild.getbuildDate()) < 0 && issueMilestone.compareTo(previousBuild.getMilestone()) > 0)
                return true;

            return false;
        }
        else
        {
            if (previousBuild.getBuildType() == BuildType.beta)
            {
                if (previousBuild.getbuildDate() == null || currentBuild.getbuildDate() == null)
                    throw new IllegalArgumentException("previousFirstSprintStartDate and currentSprintEndDate cannot be null");
                if (! (lastResolvedClosed.toInstant().compareTo(previousBuild.getFirstSprintStartDate()) > 0 || issue.getResolved().toInstant().compareTo(currentBuild.getLastSprintEndDate()) < 0))
                    return false;
            }
            else
            {
                if (previousBuild.getbuildDate() == null || currentBuild.getbuildDate() == null)
                    throw new IllegalArgumentException("previousBuildDate and currentSprintEndDate cannot be null");
                if (! (lastResolvedClosed.toInstant().compareTo(previousBuild.getbuildDate()) > 0 || issue.getResolved().toInstant().compareTo(currentBuild.getLastSprintEndDate()) < 0))
                    return false;
            }
        }

        return true;
    }

    public static String getReleaseNum(String milestone)
    {
        if (milestone == null)
            return null;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < milestone.length(); i++)
        {
            char c = milestone.charAt(i);
            if (Character.isDigit(c))
                builder.append(c);
            else if (c == '.')
                builder.append(c);
            else
                break;
        }
        return builder.toString();
    }

    public static class BuildBean
    {
        private BuildType buildType;
        private Instant buildDate;
        private Milestone milestone;
        private Instant firstSprintStartDate;
        private Instant lastSprintEndDate;

        BuildBean(String buildType, String buildDateStr, String milestone, String firstSprintStartStr, String lastSprintEndStr)
        {
            this.buildType = BuildType.getBuildType(buildType);
            this.buildDate = buildDateStr == null ? null : Instant.parse(buildDateStr);
            this.milestone = new Milestone(milestone);
            this.firstSprintStartDate = firstSprintStartStr == null ? null : Instant.parse(firstSprintStartStr);
            this.lastSprintEndDate = lastSprintEndStr == null ? null : Instant.parse(lastSprintEndStr);

        }

        public BuildType getBuildType()
        {
            return buildType;
        }

        public void setBuildType(BuildType buildType)
        {
            this.buildType = buildType;
        }

        public Instant getbuildDate()
        {
            return buildDate;
        }

        public void setbuildDate(Instant buildTime)
        {
            this.buildDate = buildTime;
        }

        public Milestone getMilestone()
        {
            return milestone;
        }

        public void setMilestone(Milestone milestone)
        {
            this.milestone = milestone;
        }

        public boolean isStableBuild()
        {
            return getBuildType() == BuildType.release || getBuildType() == BuildType.release_modules;
        }

        public Instant getFirstSprintStartDate()
        {
            return firstSprintStartDate;
        }

        public void setFirstSprintStartDate(Instant firstSprintDate)
        {
            this.firstSprintStartDate = firstSprintDate;
        }

        public Instant getLastSprintEndDate()
        {
            return lastSprintEndDate;
        }

        public void setLastSprintEndDate(Instant lastSprintEndDate)
        {
            this.lastSprintEndDate = lastSprintEndDate;
        }
    }

    public static boolean isStableBuild(String buildType)
    {
        return BuildType.release.getLabel().equalsIgnoreCase(buildType) || BuildType.release_modules.getLabel().equalsIgnoreCase(buildType);
    }

    public enum BuildType
    {
        release("release"),
        release_modules("release_modules"),
        beta("beta"),
        sprint("sprint"),
        trunk("trunk");

        private String label;

        BuildType(String label)
        {
            this.label = label;
        }

        public String getLabel()
        {
            return label;
        }

        public static BuildType getBuildType(String label)
        {
            if (label == null)
                return null;
            for (BuildType buildType : BuildType.values())
            {
                if (buildType.getLabel().equals(label))
                    return buildType;
            }
            return null;
        }
    }

    public static class Milestone implements Comparable<Milestone>
    {
        private String milestone;
        private Double releaseVersion;
        private String releaseType;

        Milestone(String milestoneStr)
        {
            this.milestone = milestoneStr;
            this.init();
        }

        public void init()
        {
            String releaseStr = getReleaseNum(this.milestone);
            if (releaseStr != null)
            {
                this.releaseVersion = Double.parseDouble(releaseStr);
                this.releaseType = this.milestone.replaceFirst(releaseStr, "");
            }
        }

        public Double getReleaseVersion()
        {
            return releaseVersion;
        }

        public void setReleaseVersion(Double releaseVersion)
        {
            this.releaseVersion = releaseVersion;
        }

        public String getReleaseType()
        {
            return releaseType;
        }

        public void setReleaseType(String releaseType)
        {
            this.releaseType = releaseType;
        }

        public String getMilestone()
        {
            return milestone;
        }

        public void setMilestone(String milestone)
        {
            this.milestone = milestone;
        }

        @Override
        public int compareTo(Milestone o)
        {
            if (this.getReleaseVersion() == null || o.getReleaseVersion() == null)
                return 0;
            if (this.getReleaseVersion().compareTo(o.getReleaseVersion()) != 0)
                return this.getReleaseVersion().compareTo(o.getReleaseVersion());
            else
            {
                if (this.getReleaseType() != null && o.getReleaseType() != null)
                    return this.getReleaseType().compareTo(o.getReleaseType());
                else if (this.getReleaseType() != null)
                    return 1;
                else if (o.getReleaseType() != null)
                    return -1;
                else
                    return 0;
            }
        }
    }

}
