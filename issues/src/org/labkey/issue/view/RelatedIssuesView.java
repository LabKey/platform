package org.labkey.issue.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.query.AllIssuesTable;
import org.labkey.issue.query.IssuesQuerySchema;
import org.springframework.validation.BindException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 1/23/17
 */
public class RelatedIssuesView extends VBox
{
    public RelatedIssuesView(@NotNull ViewContext context, @NotNull Set<Integer> relatedIssues)
    {
        setFrame(WebPartView.FrameType.DIV);
        setBodyClass("labkey-indented");
        setIncludeBreak(false);

        // Get the issue def types from the related issues
        IssuesQuerySchema querySchema = new IssuesQuerySchema(getViewContext().getUser(), getViewContext().getContainer());
        AllIssuesTable issues = (AllIssuesTable)querySchema.getTable(IssuesQuerySchema.ALL_ISSUE_TABLE);
        issues.setContainerFilter(new ContainerFilter.AllFolders(context.getUser()));
        SimpleFilter f = new SimpleFilter();
        f.addCondition(FieldKey.fromParts("issueId"), relatedIssues, CompareType.IN);
        TableSelector ts = new TableSelector(issues, issues.getColumns( "issueDefId", "issueId", "container"), f, null);

        Map<Integer, IssueListDef> issueListDefs = new HashMap<>();
        Map<Integer, Set<Integer>> byIssueDef = new HashMap<>();
        ts.forEachMap(m -> {
            Integer issueDefId = (Integer)m.get("issueDefId");
            Integer issueId = (Integer)m.get("issueId");
            String containerId = (String)m.get("container");
            Container c = ContainerManager.getForId(containerId);

            IssueListDef issueListDef = issueListDefs.computeIfAbsent(issueDefId, id -> {
                IssueListDef d = IssueManager.getIssueListDef(c, id);
                if (d == null)
                    return null;

                // Associate all issues with the issueListDef from the definition container
                Container defContainer = d.getDomainContainer(getViewContext().getUser());
                if (defContainer != null)
                {
                    if (!defContainer.equals(c))
                        d = IssueManager.getIssueListDef(defContainer, d.getName());
                }
                return d;
            });

            if (issueListDef == null)
                return;

            Set<Integer> ids = byIssueDef.computeIfAbsent(issueListDef.getRowId(), x -> new HashSet<>());
            ids.add(issueId);
        });

        for (Integer issueDefId : byIssueDef.keySet())
        {
            IssueListDef issueListDef = issueListDefs.get(issueDefId);
            Set<Integer> ids = byIssueDef.get(issueDefId);

            // NOTE: We could probably use FrameType.TITLE, but it adds too much padding-top
            addView(new HtmlView("<br><b>Related " + PageFlowUtil.filter(issueListDef.getLabel()) + "</b>"));

            QueryView view = createRelatedIssueGrid(context, querySchema, issueListDef, ids);
            addView(view);
        }
    }

    private QueryView createRelatedIssueGrid(ViewContext context, IssuesQuerySchema schema, IssueListDef issueListDef, Collection<Integer> ids)
    {
        BindException errors = new NullSafeBindException(new Object(), "fake");
        QuerySettings settings = schema.getSettings(context, "related", issueListDef.getName());
        settings.setContainerFilterName(ContainerFilter.Type.AllFolders.name());
        QueryView view = schema.createView(context, settings, errors);

        view.setShowTitle(true);
        // NOTE: We could probably use FrameType.TITLE, but it adds too much padding-top
//        view.setTitle("Related " + issueListDef.getLabel());
//        view.setFrame(WebPartView.FrameType.TITLE);
        view.setFrame(FrameType.NONE);

        view.setShadeAlternatingRows(true);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
        view.setShowPagination(false);
        view.setShowBorders(true);
        view.setShowRecordSelectors(false);
        view.setShowExportButtons(false);
        view.getSettings().getBaseFilter().addCondition(FieldKey.fromParts("issueId"), ids, CompareType.IN);
        view.getSettings().setMaxRows(Table.ALL_ROWS);
        view.getSettings().setAllowChooseQuery(false);
        view.getSettings().setAllowChooseView(false);
        view.getSettings().setAllowCustomizeView(false);

        return view;
    }
}
