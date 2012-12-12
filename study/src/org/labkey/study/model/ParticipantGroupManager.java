/*
 * Copyright (c) 2011-2012 LabKey Corporation
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

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.cache.DbCache;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.ParticipantCategory;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.permissions.SharedParticipantGroupPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.study.CohortFilter;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.StudyController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 1, 2011
 * Time: 2:26:02 PM
 */
public class ParticipantGroupManager
{
    private static final ParticipantGroupManager _instance = new ParticipantGroupManager();
    private static final List<ParticipantCategoryListener> _listeners = new CopyOnWriteArrayList<ParticipantCategoryListener>();

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

    public TableInfo getTableInfoParticipantCategory()
    {
        return StudySchema.getInstance().getTableInfoParticipantCategory();
    }

    public ParticipantCategoryImpl getParticipantCategory(Container c, User user, String label)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("Label"), label);

        ParticipantCategoryImpl[] lists = getParticipantCategories(c, user, filter);

        if (lists.length > 0)
        {
            return lists[0];
        }
        ParticipantCategoryImpl def = new ParticipantCategoryImpl();
        def.setContainer(c.getId());
        def.setLabel(label);

        return def;
    }

    public boolean categoryExists(Container c, String label)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("Label"), label);
        filter.addCondition(FieldKey.fromString("Container"), c);

        try
        {
            ParticipantCategoryImpl[] categories = Table.select(StudySchema.getInstance().getTableInfoParticipantCategory(), Table.ALL_COLUMNS, filter, null, ParticipantCategoryImpl.class);

            return categories.length > 0;
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
        }
    }

    public ParticipantCategoryImpl[] getParticipantCategories(Container c, User user, SimpleFilter filter)
    {
        try {
            filter.addCondition(FieldKey.fromString("Container"), c);
            ParticipantCategoryImpl[] categories = Table.select(StudySchema.getInstance().getTableInfoParticipantCategory(), Table.ALL_COLUMNS, filter, null, ParticipantCategoryImpl.class);
            List<ParticipantCategoryImpl> filtered = new ArrayList<ParticipantCategoryImpl>();

            for (ParticipantCategoryImpl pc : categories)
            {
                if (pc.canRead(c, user))
                {
                    pc.setGroups(getParticipantGroups(c, user, pc));
                    filtered.add(pc);
                }
            }
            return filtered.toArray(new ParticipantCategoryImpl[filtered.size()]);
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

        ParticipantCategoryImpl[] allCategories = getParticipantCategories(container, context.getUser());
        for (ParticipantCategoryImpl category : allCategories)
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
        try
        {
            Container container = context.getContainer();
            User user = context.getUser();
            StudyImpl study = StudyManager.getInstance().getStudy(container);
            MenuButton button = new MenuButton(study.getSubjectNounSingular() + " Groups");

            ParticipantCategoryImpl[] classes = getParticipantCategories(container, context.getUser());

            // TODO: Move all cohort menu generation into CohortFilterFactory
            // Remove all ptid list filters from the URL- this lets users switch between lists via the menu (versus adding filters with each click)
            ActionURL baseURL = CohortFilterFactory.clearURLParameters(study, context.cloneActionURL(), dataRegionName);
            for (ParticipantCategoryImpl cls : classes)
            {
                ParticipantGroup[] groups = cls.getGroups();
                if (groups != null)
                {
                    for (ParticipantGroup group : groups)
                        group.removeURLFilter(baseURL, container, dataRegionName);
                }
            }
            baseURL.setReadOnly();

            button.addMenuItem("All", baseURL.toString(), null, (selected.isEmpty() && cohortFilter == null));

            // merge in cohorts
            if (CohortManager.getInstance().hasCohortMenu(container, user))
            {
                // Add "Enrolled" menu item, if both enrolled and unenrolled cohorts exist
                if (study.isAdvancedCohorts())
                {
                    NavTree tree = new NavTree("Enrolled");

                    for (CohortFilter.Type type : CohortFilter.Type.values())
                    {
                        CohortFilter filter = CohortFilterFactory.getEnrolledCohortFilter(container, user, type);

                        // Should check for both enrolled & unenrolled earlier, and skip the "Enrolled" nav tree / loop entirely
                        if (null == filter)
                            break;

                        ActionURL url = filter.addURLParameters(study, baseURL.clone(), dataRegionName);

                        NavTree typeItem = new NavTree(type.getTitle(), url);
                        typeItem.setId("Enrolled:" + typeItem.getText());
                        if (filter.equals(cohortFilter))
                            typeItem.setSelected(true);

                        tree.addChild(typeItem);
                    }

                    if (tree.hasChildren())
                        button.addMenuItem(tree);
                }
                else
                {
                    CohortFilter filter = CohortFilterFactory.getEnrolledCohortFilter(container, user, CohortFilter.Type.PTID_CURRENT);

                    if (null != filter)
                    {
                        ActionURL enrolledURL = filter.addURLParameters(study, baseURL.clone(), dataRegionName);
                        button.addMenuItem("Enrolled", enrolledURL.toString(), null, (selected.isEmpty() && filter.equals(cohortFilter)));
                    }
                }

                //button.addMenuItem(((MenuButton)cohortButton).getPopupMenu().getNavTree());
                NavTree cohort = new NavTree("Cohorts");
                CohortManager.getInstance().addCohortNavTree(context.getContainer(), context.getUser(), baseURL, cohortFilter, dataRegionName, cohort);
                button.addMenuItem(cohort);
            }

            for (ParticipantCategoryImpl cls : classes)
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

            if (container.hasPermission(user, ReadPermission.class) && !user.isGuest())
            {
                button.addMenuItem("Manage " + study.getSubjectNounSingular() + " Groups", new ActionURL(StudyController.ManageParticipantCategoriesAction.class, container));
                if (hasCreateGroupFromSelection)
                {
                    button.addSeparator();
                    NavTree item = new NavTree("Create " + study.getSubjectNounSingular() + " Group");
                    button.addMenuItem(item);

                    NavTree fromSeletion = item.addChild("From Selected " + study.getSubjectNounPlural());
                    fromSeletion.setScript(createNewParticipantGroupScript(context, dataRegionName, true));

                    NavTree fromGrid = item.addChild("From All " + study.getSubjectNounPlural());
                    fromGrid.setScript(createNewParticipantGroupScript(context, dataRegionName, false));
                }
            }
            return button;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private String createNewParticipantGroupScript(ViewContext context, String dataRegionName, boolean fromSelection)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("LABKEY.requiresScript('study/ParticipantGroup.js', true, function(){");
        sb.append(createNewParticipantGroupCallback(context, dataRegionName, fromSelection));
        sb.append("},this);");

        return sb.toString();
    }

    private String createNewParticipantGroupCallback(ViewContext context, String dataRegionName, boolean fromSelection)
    {
        Container container = context.getContainer();
        Study study = StudyManager.getInstance().getStudy(container);

        boolean isAdmin = container.hasPermission(context.getUser(), SharedParticipantGroupPermission.class) ||
                container.hasPermission(context.getUser(), AdminPermission.class);

        StringBuilder sb = new StringBuilder();

        sb.append("var dataRegion = LABKEY.DataRegions[").append(PageFlowUtil.jsString(dataRegionName)).append("];");
        sb.append("if (dataRegion) {");

        if (fromSelection)
        {
            sb.append("     var checked = dataRegion.getChecked();");
            sb.append("     if (checked.length <= 0) {");
            sb.append("         Ext.MessageBox.alert('Selection Error', 'At least one ").append(study.getSubjectNounSingular()).append(" must be selected from the checkboxes in order to use this feature.');");
            sb.append("         return;");
            sb.append("     }");
        }

        sb.append("         var dataRegionEl = Ext.get(").append(PageFlowUtil.jsString(dataRegionName)).append(");");
        sb.append("         dataRegionEl.mask('getting selections...', 'x-mask-loading');");
        sb.append("         Ext.Ajax.request({");
        sb.append("             url: LABKEY.ActionURL.buildURL('participant-group', 'getParticipantsFromSelection', null, LABKEY.ActionURL.getParameters(dataRegion.requestURL)),");

        // ask for either the selected participants or all the participants in the view
        if (fromSelection)
        {
            sb.append("         jsonData: {selections:checked, schemaName: dataRegion.schemaName, queryName: dataRegion.queryName, ");
            sb.append("                     viewName: dataRegion.viewName, dataRegionName: dataRegion.name, requestURL: dataRegion.requestURL},");
        }
        else
        {
            sb.append("         jsonData: {selectAll:true, schemaName: dataRegion.schemaName, queryName: dataRegion.queryName, ");
            sb.append("                     viewName: dataRegion.viewName, dataRegionName: dataRegion.name, requestURL: dataRegion.requestURL},");
        }
        sb.append("             method: 'post', scope: this,");
        sb.append("             failure: function(res, opt){dataRegionEl.unmask(); LABKEY.Utils.displayAjaxErrorResponse(res, opt);},");
        sb.append("             success: function(res, opt) {");
        sb.append("                 dataRegionEl.unmask();");
        sb.append("                 var o = eval('var $=' + res.responseText + ';$;');");
        sb.append("                 if (o.success && o.ptids) {");
        sb.append("                     var stringPtids = '';");
        sb.append("                       for(var i = 0; i < o.ptids.length; i++){" +
                "                              if(i != o.ptids.length-1) stringPtids += o.ptids[i] + ', ';" +
                "                              else stringPtids += o.ptids[i]; }");
        sb.append("                     var dlg = Ext4.create('Study.window.ParticipantGroup', {");
        sb.append("                             subject: {");
        sb.append("                                 nounSingular:").append(PageFlowUtil.jsString(study.getSubjectNounSingular())).append(',');
        sb.append("                                 nounPlural:").append(PageFlowUtil.jsString(study.getSubjectNounPlural()));
        sb.append("                             },");
        sb.append("                             categoryParticipantIds: stringPtids,");
        sb.append("                             canEdit: LABKEY.Security.currentUser.canUpdate,");
        sb.append("                             hideDataRegion:true,");
        sb.append("                             isAdmin:").append(isAdmin);
        sb.append("                     });");
        sb.append("                     dlg.on('aftersave', function(c){dataRegion.clearSelected(); dataRegion.refresh();}, this);");
        sb.append("                     dlg.show(this);");
        sb.append("                 }");
        sb.append("             }});");
        sb.append("}");

        return sb.toString();
    }

    public ParticipantCategoryImpl[] getParticipantCategories(Container c, User user)
    {
        return getParticipantCategories(c, user, new SimpleFilter());
    }

    /**
     * @deprecated create participant categories and groups separately
     */
    public ParticipantCategoryImpl setParticipantCategory(Container c, User user, ParticipantCategoryImpl def, String[] participants, String participantFilters, String description) throws ValidationException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();
            ParticipantCategoryImpl ret;
            boolean isUpdate = !def.isNew();
            List<Throwable> errors;

            if (!def.canEdit(c, user))
                throw new ValidationException("You don't have permission to create or edit this participant category");
            
            if (def.isNew())
            {
                ParticipantCategoryImpl previous = getParticipantCategory(c, user, def.getLabel());
                if (!previous.isNew())
                    throw new ValidationException("There is aready a group named: " + def.getLabel() + " within this study. Please choose a unique group name.");
                ret = Table.insert(user, StudySchema.getInstance().getTableInfoParticipantCategory(), def);
            }
            else
            {
                ret = Table.update(user, StudySchema.getInstance().getTableInfoParticipantCategory(), def, def.getRowId());
            }

            switch (ParticipantCategory.Type.valueOf(ret.getType()))
            {
                case list:
                    updateListTypeDef(c, user, ret, isUpdate, participants, participantFilters, description);
                    break;
                case query:
                    throw new UnsupportedOperationException("Participant category type: query not yet supported");
                case cohort:
                    throw new UnsupportedOperationException("Participant category type: cohort not yet supported");
                case manual:
                    throw new UnsupportedOperationException("Participant category type: manual cannot be created using this API, you must use " +
                            "the API to create categories and groups separately.");
            }
            scope.commitTransaction();

            if (def.isNew())
                errors = fireCreatedCategory(user, ret);
            else
                errors = fireUpdateCategory(user, ret);

            if (errors.size() != 0)
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException)first;
                else
                    throw new RuntimeException(first);
            }
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

    public ParticipantCategoryImpl setParticipantCategory(Container c, User user, ParticipantCategoryImpl def) throws ValidationException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try
        {
            scope.ensureTransaction();
            ParticipantCategoryImpl ret;
            List<Throwable> errors;

            if (!def.canEdit(c, user))
                throw new ValidationException("You don't have permission to create or edit this participant category");

            if (def.isNew())
            {
                ParticipantCategoryImpl previous = getParticipantCategory(c, user, def.getLabel());
                if (!previous.isNew())
                    throw new ValidationException("There is aready a category named: " + def.getLabel() + " within this study. Please choose a unique category name.");
                ret = Table.insert(user, StudySchema.getInstance().getTableInfoParticipantCategory(), def);
            }
            else
            {
                ret = Table.update(user, StudySchema.getInstance().getTableInfoParticipantCategory(), def, def.getRowId());
            }

            switch (ParticipantCategory.Type.valueOf(ret.getType()))
            {
                case query:
                    throw new UnsupportedOperationException("Participant category type: query not yet supported");
                case cohort:
                    throw new UnsupportedOperationException("Participant category type: cohort not yet supported");
            }

            scope.commitTransaction();

            if (def.isNew())
                errors = fireCreatedCategory(user, ret);
            else
                errors = fireUpdateCategory(user, ret);

            if (errors.size() != 0)
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException)first;
                else
                    throw new RuntimeException(first);
            }
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

    private ParticipantGroup setParticipantGroup(User user, ParticipantGroup group) throws ValidationException
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
                    throw new ValidationException(String.format("The %s ID specified : %s does not exist in this study. Please enter a valid identifier.", study.getSubjectNounSingular(), id));

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

    public ParticipantGroup setParticipantGroup(Container c, User user, ParticipantGroup group) throws SQLException, ValidationException
    {
        ParticipantCategoryImpl cat = getParticipantCategory(c, user, group.getCategoryId());

        if (cat == null)
        {
            throw new ValidationException("The specified category was not found.");
        }

        if (!cat.canEdit(c, user))
        {
            throw new ValidationException("You don't have permission to create or edit participant groups in this category");
        }

        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        ParticipantGroup ret;

        try
        {
            scope.ensureTransaction();
            if (!group.isNew())
            {
                ParticipantGroup savedGroup = getParticipantGroupFromGroupRowId(c, user, group.getRowId());
                deleteGroupParticipants(c, user, savedGroup);
            }
            ret = setParticipantGroup(user, group);
            scope.commitTransaction();
        }
        finally
        {
            scope.closeConnection();
        }
        
        return ret;
    }

    public ParticipantCategoryImpl addCategoryParticipants(Container c, User user, ParticipantCategoryImpl def, String[] participants) throws ValidationException
    {
        return modifyParticipantCategory(c, user, def, participants, Modification.ADD);
    }

    public ParticipantCategoryImpl removeCategoryParticipants(Container c, User user, ParticipantCategoryImpl def, String[] participants) throws ValidationException
    {
        return modifyParticipantCategory(c, user, def, participants, Modification.REMOVE);
    }

    private enum Modification {ADD, REMOVE}
    private ParticipantCategoryImpl modifyParticipantCategory(Container c, User user, ParticipantCategoryImpl def, String[] participants, Modification modification) throws ValidationException
    {
        if (!def.canEdit(c, user))
            throw new ValidationException("You don't have permission to edit this participant category");

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();
            ParticipantCategoryImpl ret;
            List<Throwable> errors;

            ParticipantGroup[] groups = getParticipantGroups(c, user, def);
            if (groups.length != 1)
                throw new RuntimeException("Expected one group in category " + def.getLabel());
            ParticipantGroup group = groups[0];

            switch (ParticipantCategory.Type.valueOf(def.getType()))
            {
                case list:
                    if (modification == Modification.REMOVE)
                        removeGroupParticipants(c, user, group, participants);
                    else
                        addGroupParticipants(c, user, group, participants);
                    break;
                case query:
                    throw new UnsupportedOperationException("Participant category type: query not yet supported");
                case cohort:
                    throw new UnsupportedOperationException("Participant category type: cohort not yet supported");
            }
            scope.commitTransaction();
            DbCache.remove(StudySchema.getInstance().getTableInfoParticipantCategory(), getCacheKey(group.getCategoryId()));

            //Reselect
            ret = getParticipantCategory(c, user, def.getRowId());

            errors = fireUpdateCategory(user, ret);

            if (errors.size() != 0)
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException)first;
                else
                    throw new RuntimeException(first);
            }
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

    private void addGroupParticipants(Container c, User user, ParticipantGroup group, String[] participantsToAdd) throws ValidationException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try {
            scope.ensureTransaction();

            if (group.isNew())
                throw new IllegalArgumentException("Adding participants to non-existent group.");

            // add the mapping from group to participants
            SQLFragment sql = new SQLFragment("INSERT INTO ").append(getTableInfoParticipantGroupMap(), "");
            sql.append(" (GroupId, ParticipantId, Container) VALUES (?, ?, ?)");

            Set<String> existingMembers = group.getParticipantSet();
            Study study = StudyManager.getInstance().getStudy(ContainerManager.getForId(group.getContainerId()));
            for (String id : participantsToAdd)
            {
                if (existingMembers.contains(id))
                    continue;

                Participant p = StudyManager.getInstance().getParticipant(study, id);

                // don't let the database catch the invalid ptid, so we can show a more reasonable error
                if (p == null)
                    throw new ValidationException(String.format("The %s ID specified : %s does not exist in this study. Please enter a valid identifier.", study.getSubjectNounSingular(), id));

                Table.execute(StudySchema.getInstance().getSchema(), sql.getSQL(), group.getRowId(), id, group.getContainerId());

                group.addParticipantId(id);
            }
            DbCache.remove(StudySchema.getInstance().getTableInfoParticipantCategory(), getCacheKey(group.getCategoryId()));

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


    private void removeGroupParticipants(Container c, User user, ParticipantGroup group, String[] participantsToRemove)
    {
        try {
            // remove the mapping from group to participants
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroupMap(), "");
            SimpleFilter filter = new SimpleFilter().addInClause("ParticipantId", Arrays.asList(participantsToRemove));
            filter.addCondition("GroupId", group.getRowId());
            sql.append(filter.getSQLFragment(StudySchema.getInstance().getSchema().getSqlDialect()));

            Table.execute(StudySchema.getInstance().getSchema(), sql);
            DbCache.remove(StudySchema.getInstance().getTableInfoParticipantCategory(), getCacheKey(group.getCategoryId()));
        }
        catch (SQLException x)
        {
            throw new RuntimeSQLException(x);
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

    public ParticipantCategoryImpl getParticipantCategory(Container c, User user, int rowId)
    {
        SimpleFilter filter = new SimpleFilter("RowId", rowId);

        ParticipantCategoryImpl[] categories = getParticipantCategories(c, user, filter);

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
                ParticipantCategoryImpl category = getParticipantCategory(container, user, rs.getInt("CategoryId"));
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

    public ParticipantGroup getParticipantGroupFromGroupRowId(Container container, User user, int rowId)
    {
        ParticipantGroup[] groups;
        ResultSet rs = null;
        SimpleFilter filter = new SimpleFilter("Container", container);
        filter.addCondition("RowId", rowId);
        
        try
        {
            groups = Table.select(getTableInfoParticipantGroup(), Table.ALL_COLUMNS, filter, null, ParticipantGroup.class);
        }
        catch(SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        finally
        {
            if (rs != null)
                try { rs.close(); } catch (SQLException e) { /* fall through */ }
        }

        if(groups.length == 1)
        {
            return groups[0];
        }
        else
        {
            return null;
        }
    }

    public String[] getAllGroupedParticipants(Container container)
    {
        SQLFragment sql = new SQLFragment("SELECT DISTINCT ParticipantId FROM ");
        sql.append(getTableInfoParticipantGroupMap(), "GroupMap");
        sql.append(" WHERE Container = ? ORDER BY ParticipantId");
        sql.add(container);
        try
        {
            return Table.executeArray(StudySchema.getInstance().getSchema(), sql, String.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public ParticipantGroup[] getParticipantGroups(Container c, User user, ParticipantCategoryImpl def)
    {
        if (!def.isNew())
        {
            String cacheKey = getCacheKey(def);
            ParticipantGroup[] groups = (ParticipantGroup[]) DbCache.get(StudySchema.getInstance().getTableInfoParticipantCategory(), cacheKey);

            if (groups != null)
                return groups;

            SQLFragment sql = new SQLFragment("SELECT * FROM (");

            // TODO, refactor this so that we initialize the participantGroup bean using a select on rowId from the participantGroupTable
            sql.append("SELECT pg.label, pg.rowId, pg.filters, pg.description, pg.created, pg.createdBy, pg.modified, pg.modifiedBy FROM ");
            sql.append(StudySchema.getInstance().getTableInfoParticipantCategory(), "pc");
            sql.append(" JOIN ").append(getTableInfoParticipantGroup(), "pg").append(" ON pc.rowId = pg.categoryId WHERE pg.categoryId = ?) jr ");
            sql.append(" JOIN ").append(getTableInfoParticipantGroupMap(), "gm").append(" ON jr.rowId = gm.groupId");
            sql.append(" ORDER BY gm.groupId, gm.ParticipantId;");

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
                    group.setFilters(pg.getFilters());
                    group.setDescription(pg.getDescription());
                    group.setModified(pg.getModified());
                    group.setModifiedBy(pg.getModifiedBy());
                    group.setCreated(pg.getCreated());
                    group.setCreatedBy(pg.getCreatedBy());

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

    private void updateListTypeDef(Container c, User user, ParticipantCategoryImpl def, boolean update, String[] participants, String filters, String description) throws SQLException, ValidationException
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
                group.setFilters(filters);
                group.setDescription(description);

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

    public void deleteParticipantCategory(Container c, User user, ParticipantCategoryImpl def) throws ValidationException
    {
        if (def.isNew())
            throw new ValidationException("Participant category has not been saved to the database yet");

        if (!def.canDelete(c, user))
            throw new ValidationException("You must either be an administrator, editor or the owner to delete a participant group");

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

            List<Throwable> errors = fireDeleteCategory(user, def);
            if (errors.size() != 0)
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException)first;
                else
                    throw new RuntimeException(first);
            }
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

    public void deleteParticipantGroup(Container c, User user, ParticipantGroup group) throws ValidationException
    {
        ParticipantCategoryImpl cat = getParticipantCategory(c, user, group.getCategoryId());

        if (!cat.canDelete(c, user))
            throw new ValidationException("You must either be an administrator, editor or the owner to delete a participant group");

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try
        {
            scope.ensureTransaction();

            // remove any participant group mappings from the junction table
            SQLFragment sqlMapping = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroupMap(), "").append(" WHERE GroupId = ? ");
            Table.execute(StudySchema.getInstance().getSchema(), sqlMapping.getSQL(), group.getRowId());

            // delete the participant group
            SQLFragment sqlGroup = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroup(), "").append(" WHERE RowId = ? ");
            Table.execute(StudySchema.getInstance().getSchema(), sqlGroup.getSQL(), group.getRowId());

            if(cat.getType().equals("list"))
            {
                // delete the participant category
                SQLFragment sqlCat = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantCategory(), "").append(" WHERE RowId = ? ");
                Table.execute(StudySchema.getInstance().getSchema(), sqlCat.getSQL(), cat.getRowId());
            }

            DbCache.remove(StudySchema.getInstance().getTableInfoParticipantCategory(), getCacheKey(cat));

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

    public List<String> getParticipantsFromSelection(Container container, QueryView view, Collection<String> lsids) throws SQLException
    {
        List<String> ptids = new ArrayList<String>();
        TableInfo table = view.getTable();

        if (table != null)
        {
            ResultSet rs = null;

            try
            {
                StringBuilder whereClause = new StringBuilder();
                whereClause.append("lsid IN (");
                Object[] params = new Object[lsids.size()];
                String comma = "";
                int i = 0;

                for (String lsid : lsids)
                {
                    whereClause.append(comma);
                    whereClause.append("?");
                    params[i++] = lsid;
                    comma = ",";
                }

                whereClause.append(")");
                SimpleFilter filter = new SimpleFilter();
                filter.addWhereClause(whereClause.toString(), params);

                FieldKey ptidKey = new FieldKey(null, StudyService.get().getSubjectColumnName(container));
                Results r = Table.select(table, table.getColumns(ptidKey.toString(), "lsid"), filter, null);
                rs = r.getResultSet();

                if (rs != null)
                {
                    ColumnInfo ptidColumnInfo = r.getFieldMap().get(ptidKey);

                    int ptidIndex = (null != ptidColumnInfo) ? rs.findColumn(ptidColumnInfo.getAlias()) : 0;
                    while (rs.next() && ptidIndex > 0)
                    {
                        String ptid = rs.getString(ptidIndex);
                        ptids.add(ptid);
                    }
                }
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }
        return ptids;
    }

    private String getCacheKey(ParticipantCategoryImpl def)
    {
        return "ParticipantCategory-" + def.getRowId();
    }

    private String getCacheKey(int categoryId)
    {
        return "ParticipantCategory-" + categoryId;
    }

    public static void addCategoryListener(ParticipantCategoryListener listener)
    {
        _listeners.add(listener);
    }

    public static void removeCategoryListener(ParticipantCategoryListener listener)
    {
        _listeners.remove(listener);
    }

    private static List<Throwable> fireDeleteCategory(User user, ParticipantCategoryImpl category)
    {
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ParticipantCategoryListener l : _listeners)
        {
            try {
                l.categoryDeleted(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    private static List<Throwable> fireUpdateCategory(User user, ParticipantCategoryImpl category)
    {
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ParticipantCategoryListener l : _listeners)
        {
            try {
                l.categoryUpdated(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    private static List<Throwable> fireCreatedCategory(User user, ParticipantCategoryImpl category)
    {
        List<Throwable> errors = new ArrayList<Throwable>();

        for (ParticipantCategoryListener l : _listeners)
        {
            try {
                l.categoryCreated(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    public static class ParticipantGroupTestCase extends Assert
    {

        @Test
        public void test()
        {
            ParticipantGroupManager p = new ParticipantGroupManager();

            User u = new User();
//            ParticipantGroup g = new ParticipantGroup();
//            g.setContainer("14944030-c56c-102e-8297-ca47709443a1");
//            g.setLabel("pie2");

            ParticipantCategoryImpl def = new ParticipantCategoryImpl();
            p.getParticipantGroups(null, null, def);
        }

    }
}
