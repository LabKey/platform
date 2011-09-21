/*
 * Copyright (c) 2011 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.cache.DbCache;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.permissions.SharedParticipantGroupPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.study.CohortFilter;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.StudyController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 1, 2011
 * Time: 2:26:02 PM
 */
public class ParticipantGroupManager
{
    private static final ParticipantGroupManager _instance = new ParticipantGroupManager();

    private ParticipantGroupManager(){}

    public static ParticipantGroupManager getInstance()
    {
        return _instance;
    }

    public TableInfo getTableInfoParticipantGroup()
    {
        return StudySchema.getInstance().getSchema().getTable("ParticipantGroup");
    }

    public TableInfo getTableInfoParticipantGroupMap()
    {
        return StudySchema.getInstance().getSchema().getTable("ParticipantGroupMap");
    }

    public ParticipantCategory getParticipantCategory(Container c, User user, String label)
    {
        SimpleFilter filter = new SimpleFilter("Label", label);

        ParticipantCategory[] lists = getParticipantCategories(c, user, filter);

        if (lists.length > 0)
        {
            return lists[0];
        }
        ParticipantCategory def = new ParticipantCategory();
        def.setContainer(c.getId());
        def.setLabel(label);

        return def;
    }

    public ParticipantCategory[] getParticipantCategories(Container c, User user, SimpleFilter filter)
    {
        try {
            filter.addCondition("Container", c);
            ParticipantCategory[] categories = Table.select(StudySchema.getInstance().getTableInfoParticipantCategory(), Table.ALL_COLUMNS, filter, null, ParticipantCategory.class);
            List<ParticipantCategory> filtered = new ArrayList<ParticipantCategory>();

            for (ParticipantCategory pc : categories)
            {
                if (pc.canRead(c, user))
                {
                    pc.setGroups(getParticipantGroups(c, user, pc));
                    filtered.add(pc);
                }
            }
            return filtered.toArray(new ParticipantCategory[filtered.size()]);
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public ActionButton createParticipantGroupButton(ViewContext context, String dataRegionName, CohortFilter cohortFilter,
                                                     boolean hasCreateGroupFromSelection)
    {
        Container container = context.getContainer();
        String[] colFilterParamNames = context.getActionURL().getKeysByPrefix(dataRegionName + ".");
        Map<String, String> colFilters = new HashMap<String, String>();
        Set<ParticipantGroup> selected = new HashSet<ParticipantGroup>();
        // Build up a case-insensititive set of all columns that are being filtered.
        // We'll use this to identify any existing participant list filters.
        for (String colFilterParamName : colFilterParamNames)
        {
            String colName = colFilterParamName.toLowerCase();
            int tildaIdx = colName.indexOf('~');
            if (tildaIdx > 0)
                colName = colName.substring(0, tildaIdx);
            colFilters.put(colName, context.getActionURL().getParameter(colFilterParamName));
        }

        ParticipantCategory[] allCategories = getParticipantCategories(container, context.getUser());
        for (ParticipantCategory category : allCategories)
        {
            ParticipantGroup[] allGroups = getParticipantGroups(container, context.getUser(), category);
            for (ParticipantGroup group : allGroups)
            {
                Pair<FieldKey, String> colAndValue = group.getFilterColAndValue(container);
                String parameterName = group.getURLFilterParameterName(colAndValue.getKey(), dataRegionName);
                String filteredValue = colFilters.get(parameterName.toLowerCase());
                if (filteredValue != null && filteredValue.equals(colAndValue.getValue()))
                    selected.add(group);
            }
        }
        return createParticipantGroupButton(context, dataRegionName, selected, cohortFilter, hasCreateGroupFromSelection);
    }

    private ActionButton createParticipantGroupButton(ViewContext context, String dataRegionName, Set<ParticipantGroup> selected, CohortFilter cohortFilter,
                                                      boolean hasCreateGroupFromSelection)
    {
        try {
            Container container = context.getContainer();
            StudyImpl study = StudyManager.getInstance().getStudy(container);
            MenuButton button = new MenuButton(study.getSubjectNounSingular() + " Groups");

            ParticipantCategory[] classes = getParticipantCategories(container, context.getUser());

            // Remove all ptid list filters from the URL- this lets users switch between lists via the menu (versus adding filters with each click)
            ActionURL baseURL = CohortFilter.clearURLParameters(context.cloneActionURL());

            for (ParticipantCategory cls : classes)
            {
                ParticipantGroup[] groups = cls.getGroups();
                if (groups != null)
                {
                    for (ParticipantGroup group : groups)
                        group.removeURLFilter(baseURL, container, dataRegionName);
                }
            }

            button.addMenuItem("All", baseURL.toString(), null, (selected.isEmpty() && cohortFilter == null));

            // merge in cohorts
            if (CohortManager.getInstance().hasCohortMenu(context.getContainer(), context.getUser()))
            {
                //button.addMenuItem(((MenuButton)cohortButton).getPopupMenu().getNavTree());
                NavTree cohort = new NavTree("Cohorts");
                CohortManager.getInstance().addCohortNavTree(context.getContainer(), context.getUser(), baseURL, cohortFilter, cohort);
                button.addMenuItem(cohort);
            }

            for (ParticipantCategory cls : classes)
            {
                ParticipantGroup[] groups = cls.getGroups();
                if (null != groups && groups.length > 1)
                {
                    NavTree item = new NavTree(cls.getLabel());
                    for (ParticipantGroup grp : groups)
                    {
                        ActionURL url = baseURL.clone();
                        url = grp.addURLFilter(url, container, dataRegionName);
                        NavTree child = item.addChild(grp.getLabel(), url);
                        child.setSelected(selected.contains(grp));
                    }
                    button.addMenuItem(item);
                    if (cls.isShared())
                        item.setImageSrc(context.getContextPath() + "/reports/grid_shared.gif");
                }
                else if (null != groups && groups.length == 1)
                {
                    ActionURL url = baseURL.clone();
                    url = groups[0].addURLFilter(url, container, dataRegionName);
                    NavTree item = button.addMenuItem(groups[0].getLabel(), url.toString(), null, selected.contains(groups[0]));
                    if (cls.isShared())
                        item.setImageSrc(context.getContextPath() + "/reports/grid_shared.gif");
                }
            }

            button.addSeparator();
            if (CohortManager.getInstance().hasCohortMenu(context.getContainer(), context.getUser()) &&
                    container.hasPermission(context.getUser(), AdminPermission.class))
            {
                button.addMenuItem("Manage Cohorts", new ActionURL(CohortController.ManageCohortsAction.class, container));
            }
            button.addMenuItem("Manage " + study.getSubjectNounSingular() + " Groups", new ActionURL(StudyController.ManageParticipantCategoriesAction.class, container));

            if (hasCreateGroupFromSelection)
            {
                button.addSeparator();
                button.addMenuItem("Create " + study.getSubjectNounSingular() + " Group from Selection", "#", createNewParticipantGroupScript(context, dataRegionName));
            }
            return button;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private String createNewParticipantGroupScript(ViewContext context, String dataRegionName)
    {
        Container container = context.getContainer();
        Study study = StudyManager.getInstance().getStudy(container);

        boolean isAdmin = container.hasPermission(context.getUser(), SharedParticipantGroupPermission.class) ||
                container.hasPermission(context.getUser(), AdminPermission.class);

        StringBuilder sb = new StringBuilder();

        sb.append("var dataRegion = LABKEY.DataRegions[").append(PageFlowUtil.jsString(dataRegionName)).append("];");
        sb.append("if (dataRegion) {");
        sb.append("     var checked = dataRegion.getChecked();");
        sb.append("     if (checked.length > 0) {");
        sb.append("         var dataRegionEl = Ext.get(").append(PageFlowUtil.jsString(dataRegionName)).append(");");
        sb.append("         dataRegionEl.mask('getting selections...', 'x-mask-loading');");
        sb.append("         Ext.Ajax.request({");
        sb.append("             url: LABKEY.ActionURL.buildURL('participant-group', 'getParticipantsFromSelection'),");
        sb.append("             jsonData: {selections:checked},");
        sb.append("             method: 'post', scope: this,");
        sb.append("             failure: function(res, opt){dataRegionEl.unmask(); LABKEY.Utils.displayAjaxErrorResponse(res, opt);},");
        sb.append("             success: function(res, opt) {");
        sb.append("                 dataRegionEl.unmask();");
        sb.append("                 var o = eval('var $=' + res.responseText + ';$;');");
        sb.append("                 if (o.success && o.ptids) {");
        sb.append("                     var dlg = new LABKEY.study.ParticipantGroupDialog({");
        sb.append("                             subject: {");
        sb.append("                                 nounSingular:").append(PageFlowUtil.jsString(study.getSubjectNounSingular())).append(',');
        sb.append("                                 nounPlural:").append(PageFlowUtil.jsString(study.getSubjectNounPlural()));
        sb.append("                             },");
        sb.append("                             categoryParticipantIds: o.ptids,");
        sb.append("                             canEdit:true, hideDataRegion:true,");
        sb.append("                             isAdmin:").append(isAdmin);
        sb.append("                     });");
        sb.append("                     dlg.on('aftersave', function(c){dataRegion.clearSelected(); dataRegion.refresh();}, this);");
        sb.append("                     dlg.show(this);");
        sb.append("                 }");
        sb.append("             }});");
        sb.append("     } else {");
        sb.append("         Ext.MessageBox.alert('Selection Error', 'At least one ").append(study.getSubjectNounSingular()).append(" must be selected from the checkboxes in order to use this feature.');");
        sb.append("     }");
        sb.append("}");

        return sb.toString();
    }

    public ParticipantCategory[] getParticipantCategories(Container c, User user)
    {
        return getParticipantCategories(c, user, new SimpleFilter());
    }

    public ParticipantCategory setParticipantCategory(Container c, User user, ParticipantCategory def, String[] participants)
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();
            ParticipantCategory ret;
            boolean isUpdate = !def.isNew();

            if (!def.canEdit(c, user))
                throw new RuntimeException("You don't have permission to create or edit this participant category");
            
            if (def.isNew())
            {
                ret = Table.insert(user, StudySchema.getInstance().getTableInfoParticipantCategory(), def);
            }
            else
            {
                ret = Table.update(user, StudySchema.getInstance().getTableInfoParticipantCategory(), def, def.getRowId());
            }

            switch (ParticipantCategory.Type.valueOf(ret.getType()))
            {
                case list:
                    updateListTypeDef(c, user, ret, isUpdate, participants);
                    break;
                case query:
                    throw new UnsupportedOperationException("Participant category type: query not yet supported");
                case cohort:
                    throw new UnsupportedOperationException("Participant category type: cohort not yet supported");
            }
            scope.commitTransaction();
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private ParticipantGroup setParticipantGroup(User user, ParticipantGroup group)
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();

            ParticipantGroup ret;
            if (group.isNew())
                ret = Table.insert(user, getTableInfoParticipantGroup(), group);
            else
                ret = Table.update(user, getTableInfoParticipantGroup(), group, group.getRowId());

            // add the mapping from group to participants
            SQLFragment sql = new SQLFragment("INSERT INTO ").append(getTableInfoParticipantGroupMap(), "");
            sql.append(" (GroupId, ParticipantId, Container) VALUES (?, ?, ?)");

            Study study = StudyManager.getInstance().getStudy(ContainerManager.getForId(group.getContainerId()));
            for (String id : group.getParticipantIds())
            {
                Participant p = StudyManager.getInstance().getParticipant(study, id);

                // don't let the database catch the invalid ptid, so we can show a more reasonable error
                if (p == null)
                    throw new IllegalArgumentException(String.format("The %s ID specified : %s does not exist in this study. Please enter a valid identifier.", study.getSubjectNounSingular(), id));

                Table.execute(StudySchema.getInstance().getSchema(), sql.getSQL(), group.getRowId(), id, group.getContainerId());
            }
            DbCache.remove(StudySchema.getInstance().getTableInfoParticipantCategory(), getCacheKey(group.getCategoryId()));

            scope.commitTransaction();
            return ret;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private void deleteGroupParticipants(Container c, User user, ParticipantGroup group)
    {
        try {
            // remove the mapping from group to participants
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroupMap(), "");
            sql.append(" WHERE GroupId = ?");

            Table.execute(StudySchema.getInstance().getSchema(), sql.getSQL(), group.getRowId());
            DbCache.remove(StudySchema.getInstance().getTableInfoParticipantCategory(), getCacheKey(group.getCategoryId()));
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public ParticipantCategory getParticipantCategory(Container c, User user, int rowId)
    {
        SimpleFilter filter = new SimpleFilter("RowId", rowId);

        ParticipantCategory[] categories = getParticipantCategories(c, user, filter);

        if (categories.length > 0)
            return categories[0];
        return null;
    }

    public ParticipantGroup getParticipantGroup(Container container, User user, int rowId)
    {
        SimpleFilter filter = new SimpleFilter("RowId", rowId);
        filter.addCondition("Container", container);
        ResultSet rs = null;
        try
        {
            rs = Table.select(getTableInfoParticipantGroup(), Collections.singleton("CategoryId"), filter, null);
            if (rs.next())
            {
                ParticipantCategory category = getParticipantCategory(container, user, rs.getInt("CategoryId"));
                if (category != null)
                {
                    // Use getParticipantGroups here to pull the entire category into the cache- this is more expensive up-front,
                    // but will save us time later.
                    ParticipantGroup[] groups = getParticipantGroups(container, user, category);
                    for (ParticipantGroup group : groups)
                    {
                        if (group.getRowId() == rowId)
                            return group;
                    }
                }
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }
        return null;
    }

    public ParticipantGroup[] getParticipantGroups(Container c, User user, ParticipantCategory def)
    {
        if (!def.isNew())
        {
            String cacheKey = getCacheKey(def);
            ParticipantGroup[] groups = (ParticipantGroup[]) DbCache.get(StudySchema.getInstance().getTableInfoParticipantCategory(), cacheKey);

            if (groups != null)
                return groups;

            SQLFragment sql = new SQLFragment("SELECT * FROM (");
            sql.append("SELECT pg.label, pg.rowId FROM ").append(StudySchema.getInstance().getTableInfoParticipantCategory(), "pc");
            sql.append(" JOIN ").append(getTableInfoParticipantGroup(), "pg").append(" ON pc.rowId = pg.categoryId WHERE pg.categoryId = ?) jr ");
            sql.append(" JOIN ").append(getTableInfoParticipantGroupMap(), "gm").append(" ON jr.rowId = gm.groupId;");

            ParticipantGroupMap[] maps = new ParticipantGroupMap[0];
            try
            {
                maps = Table.executeQuery(StudySchema.getInstance().getSchema(), sql.getSQL(), new Object[]{def.getRowId()}, ParticipantGroupMap.class);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            Map<Integer, ParticipantGroup> groupMap = new HashMap<Integer, ParticipantGroup>();

            for (ParticipantGroupMap pg : maps)
            {
                if (!groupMap.containsKey(pg.getGroupId()))
                {
                    ParticipantGroup group = new ParticipantGroup();
                    group.setCategoryId(def.getRowId());
                    group.setCategoryLabel(def.getLabel());
                    group.setLabel(pg.getLabel());
                    group.setContainer(pg.getContainerId());
                    group.setRowId(pg.getRowId());

                    groupMap.put(pg.getGroupId(), group);
                }
                groupMap.get(pg.getGroupId()).addParticipantId(pg.getParticipantId());
            }
            groups = groupMap.values().toArray(new ParticipantGroup[groupMap.size()]);

            DbCache.put(StudySchema.getInstance().getTableInfoParticipantCategory(), cacheKey, groups);
            return groups;
        }
        return new ParticipantGroup[0];
    }

    public static class ParticipantGroupMap extends ParticipantGroup
    {
        private String _participantId;
        private int _groupId;

        public String getParticipantId()
        {
            return _participantId;
        }

        public void setParticipantId(String participantId)
        {
            _participantId = participantId;
        }

        public int getGroupId()
        {
            return _groupId;
        }

        public void setGroupId(int groupId)
        {
            _groupId = groupId;
        }
    }

    private void updateListTypeDef(Container c, User user, ParticipantCategory def, boolean update, String[] participants) throws SQLException
    {
        assert !def.isNew() : "The participant category has not been created yet";

        if (!def.isNew())
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try {
                scope.ensureTransaction();
                ParticipantGroup group = new ParticipantGroup();

                group.setCategoryId(def.getRowId());
                group.setContainer(def.getContainerId());

                // updating an existing category
                if (update)
                {
                    ParticipantGroup[] groups = getParticipantGroups(c, user, def);
                    if (groups.length == 1)
                    {
                        group = groups[0];
                        deleteGroupParticipants(c, user, group);
                    }
                }
                group.setLabel(def.getLabel());
                group.setParticipantIds(participants);

                group = setParticipantGroup(user, group);
                def.setGroups(new ParticipantGroup[]{group});

                scope.commitTransaction();
            }
            finally
            {
                scope.closeConnection();
            }
        }
    }

    public void deleteParticipantCategory(Container c, User user, ParticipantCategory def)
    {
        if (def.isNew())
            throw new IllegalArgumentException("Participant category has not been saved to the database yet");

        if (!def.canDelete(c, user))
            throw new RuntimeException("You must either be an administrator, editor or the owner to delete a participant group");

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();

            // remove any participant group mappings from the junction table
            SQLFragment sqlMapping = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroupMap(), "").append(" WHERE GroupId IN ");
            sqlMapping.append("(SELECT RowId FROM ").append(getTableInfoParticipantGroup(), "pg").append(" WHERE CategoryId = ?)");
            Table.execute(StudySchema.getInstance().getSchema(), sqlMapping.getSQL(), def.getRowId());

            // delete the participant groups
            SQLFragment sqlGroup = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroup(), "").append(" WHERE RowId IN ");
            sqlGroup.append("(SELECT RowId FROM ").append(getTableInfoParticipantGroup(), "pg").append(" WHERE CategoryId = ?)");
            Table.execute(StudySchema.getInstance().getSchema(), sqlGroup.getSQL(), def.getRowId());

            // delete the participant list definition
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(StudySchema.getInstance().getTableInfoParticipantCategory(), "").append(" WHERE RowId = ?");
            Table.execute(StudySchema.getInstance().getSchema(), sql.getSQL(), def.getRowId());

            DbCache.remove(StudySchema.getInstance().getTableInfoParticipantCategory(), getCacheKey(def));
            scope.commitTransaction();
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
        finally
        {
            scope.closeConnection();
        }
    }

    private String getCacheKey(ParticipantCategory def)
    {
        return "ParticipantCategory-" + def.getRowId();
    }

    private String getCacheKey(int categoryId)
    {
        return "ParticipantCategory-" + categoryId;
    }
}
