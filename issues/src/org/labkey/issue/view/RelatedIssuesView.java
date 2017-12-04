/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.issue.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
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
        setFrame(FrameType.DIV);

        TableInfo issues = IssuesSchema.getInstance().getTableInfoIssues();
        SimpleFilter f = new SimpleFilter(FieldKey.fromParts("issueId"), relatedIssues, CompareType.IN);
        TableSelector ts = new TableSelector(issues, issues.getColumns( "issueDefId", "issueId", "container"), f, null);

        // Group issues by issuesListDef from the domain definition container
        Map<Integer, IssuesByListDef> issuesByListDefMap = new HashMap<>();
        ts.forEachMap((Map<String, Object> m) -> {
            Integer issueDefId = (Integer)m.get("issueDefId");
            Integer issueId = (Integer)m.get("issueId");
            String containerId = (String)m.get("container");
            Container c = ContainerManager.getForId(containerId);
            if (c == null || !c.hasPermission(getViewContext().getUser(), ReadPermission.class))
                return;

            IssueListDef d = IssueManager.getIssueListDef(c, issueDefId);
            if (d == null)
                return;

            // If the user doesn't have ReadPermission to the domain container, we won't be able to create a query
            // table in that container.  In this case, just use the issue's container.  As a consequence, any other
            // from the same domain definition issueListDef that live in different containers will appear in separate grids.
            IssueListDef domainDefinitionIssueListDef = d;
            Container defContainer = d.getDomainContainer(getViewContext().getUser());
            if (defContainer != null && !defContainer.equals(c) && defContainer.hasPermission(getViewContext().getUser(), ReadPermission.class))
            {
                IssueListDef q = IssueManager.getIssueListDef(defContainer, d.getName());
                if (q != null)
                    domainDefinitionIssueListDef = q;
            }

            IssuesByListDef issuesByListDef = issuesByListDefMap.get(domainDefinitionIssueListDef.getRowId());
            if (issuesByListDef == null)
            {
                issuesByListDef = new IssuesByListDef(domainDefinitionIssueListDef);
                issuesByListDefMap.put(domainDefinitionIssueListDef.getRowId(), issuesByListDef);
            }
            issuesByListDef.issues.add(issueId);
            issuesByListDef.containers.add(c);
        });

        for (IssuesByListDef issuesByListDef : issuesByListDefMap.values())
        {
            IssueListDef issueListDef = issuesByListDef.issueListDef;
            Set<Integer> ids = issuesByListDef.issues;
            Set<Container> containers = issuesByListDef.containers;

            // NOTE: We could probably use FrameType.TITLE, but it adds too much padding-top
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(issueListDef.lookupContainer(), issueListDef.getName());
            addView(new HtmlView("<b>Related " + PageFlowUtil.filter(names.pluralName) + "</b>"));

            ViewContext ctx = new ViewContext(context);
            ctx.setContainer(issueListDef.lookupContainer());
            QueryView view = createRelatedIssueGrid(ctx, issueListDef, ids, containers);
            addView(view);
        }
    }

    private static class IssuesByListDef
    {
        IssueListDef issueListDef;
        Set<Integer> issues = new HashSet<>();
        Set<Container> containers = new HashSet<>();

        IssuesByListDef(IssueListDef issueListDef)
        {
            this.issueListDef = issueListDef;
        }
    }

    private QueryView createRelatedIssueGrid(ViewContext ctx, IssueListDef issueListDef, Collection<Integer> ids, Collection<Container> containers)
    {
        IssuesQuerySchema querySchema = new IssuesQuerySchema(ctx.getUser(), ctx.getContainer());
        BindException errors = new NullSafeBindException(new Object(), "fake");
        QuerySettings settings = querySchema.getSettings(ctx, "related", issueListDef.getName());
        QueryView view = querySchema.createView(ctx, settings, errors);

        view.setShowTitle(false);
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

        TableInfo table = view.getTable();
        if (table instanceof ContainerFilterable)
        {
            ContainerFilterable cf = (ContainerFilterable) table;
            ContainerFilter filter = new ContainerFilter.SimpleContainerFilter(containers);
            cf.setContainerFilter(filter);
        }

        return view;
    }
}
