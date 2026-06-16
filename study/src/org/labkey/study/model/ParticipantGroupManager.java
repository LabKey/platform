/*
 * Copyright (c) 2011-2018 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.study.CohortFilter;
import org.labkey.api.study.ParticipantCategory;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.model.ParticipantGroup;
import org.labkey.api.study.permissions.SharedParticipantGroupPermission;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;
import org.labkey.study.CohortFilterFactory;
import org.labkey.study.StudySchema;
import org.labkey.study.audit.ParticipantGroupAuditProvider;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.query.DataspaceQuerySchema;
import org.labkey.study.query.StudyQuerySchema;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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

public class ParticipantGroupManager
{
    private static final ParticipantGroupManager _instance = new ParticipantGroupManager();
    private static final List<ParticipantCategoryListener> _listeners = new CopyOnWriteArrayList<>();
    private static final String PARTICIPANT_GROUP_SESSION_KEY = "LABKEY.sharedStudyParticipantFilter.";

    private ParticipantGroupManager()
    {
    }

    public static ParticipantGroupManager getInstance()
    {
        return _instance;
    }

    public static TableInfo getTableInfoParticipantGroup()
    {
        return StudySchema.getInstance().getSchema().getTable("ParticipantGroup");
    }

    public static TableInfo getTableInfoParticipantGroupMap()
    {
        return StudySchema.getInstance().getSchema().getTable("ParticipantGroupMap");
    }

    public static TableInfo getTableInfoParticipantCategory()
    {
        return StudySchema.getInstance().getTableInfoParticipantCategory();
    }

    public @Nullable ParticipantCategoryImpl getParticipantCategory(Container c, User user, String label)
    {
        ParticipantCategoryImpl category = ParticipantGroupCache.getParticipantCategoryForLabel(c, label);
        if (category != null && category.canRead(user))
            return category;

        return null;
    }

    public boolean categoryExists(Container c, User user, String label, boolean shared)
    {
        assert label != null : "Label cannot be null";

        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        Set<String> existingCategories = new HashSet<>();
        filter.addCondition(FieldKey.fromString("OwnerId"), shared ? ParticipantCategory.OWNER_SHARED : user.getUserId());

        TableSelector selector = new TableSelector(getTableInfoParticipantCategory(), Collections.singleton("Label"), filter, null);
        for (String name : selector.getArrayList(String.class))
            existingCategories.add(name.toLowerCase());

        return existingCategories.contains(label.toLowerCase());
    }

    public List<ParticipantCategoryImpl> getParticipantCategoriesByType(final Container c, final User user, @Nullable String type)
    {
        if (type == null)
            return _getParticipantCategories(c, user);

        Collection<ParticipantCategoryImpl> types = ParticipantGroupCache.getParticipantCategoryForType(c, type);
        if (types == null)
            return Collections.emptyList();

        return types.stream().filter(category -> category != null && category.canRead(user)).toList();
    }

    public List<ParticipantCategoryImpl> getParticipantCategoriesByLabel(final Container c, final User user, @Nullable String label)
    {
        if (label == null)
            return _getParticipantCategories(c, user);

        ParticipantCategoryImpl category = getParticipantCategory(c, user, label);
        if (category != null)
            return List.of(category);

        return Collections.emptyList();
    }

    public ActionButton createParticipantGroupButton(ViewContext context, String dataRegionName, CohortFilter cohortFilter,
                                                     boolean hasCreateGroupFromSelection)
    {
        Container container = context.getContainer();
        String[] colFilterParamNames = context.getActionURL().getKeysByPrefix(dataRegionName + ".");
        Map<String, String> colFilters = new HashMap<>();
        Set<ParticipantGroup> selected = new HashSet<>();
        // Build up a case-insensitive set of all columns that are being filtered.
        // We'll use this to identify any existing participant list filters.
        for (String colFilterParamName : colFilterParamNames)
        {
            String colName = colFilterParamName.toLowerCase();
            int tildaIdx = colName.indexOf('~');
            if (tildaIdx > 0)
                colName = colName.substring(0, tildaIdx);
            colFilters.put(colName, context.getActionURL().getParameter(colFilterParamName));
        }

        Collection<ParticipantCategoryImpl> allCategories = getParticipantCategories(container, context.getUser());
        for (ParticipantCategoryImpl category : allCategories)
        {
            for (ParticipantGroup group : getParticipantGroups(container, context.getUser(), category))
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
            MenuButton button = new MenuButton("Groups");

            Collection<ParticipantCategoryImpl> classes = getParticipantCategories(container, context.getUser());

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

            button.addMenuItem("All", null, getRemoveSelectionScript(dataRegionName, study, classes), (selected.isEmpty() && cohortFilter == null));

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
                        button.addMenuItem("Enrolled", enrolledURL, null, (selected.isEmpty() && filter.equals(cohortFilter)));
                    }
                }

                NavTree cohort = new NavTree("Cohorts");
                CohortManager.getInstance().addCohortNavTree(context.getContainer(), context.getUser(), cohortFilter, dataRegionName, cohort);
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
                        Pair<FieldKey, String> filterColValue = grp.getFilterColAndValue(container);

                        NavTree child = new NavTree(grp.getLabel());
                        child.setScript(getSelectionScript(dataRegionName, filterColValue));
                        child.setSelected(selected.contains(grp));

                        item.addChild(child);
                    }
                    button.addMenuItem(item);
                    if (cls.isShared())
                    {
                        item.setImageSrc(new ResourceURL("/reports/grid.gif"));
                        item.setImageCls("fa fa-users");
                    }
                }
                else if (null != groups && groups.length == 1)
                {
                    Pair<FieldKey, String> filterColValue = groups[0].getFilterColAndValue(container);
                    NavTree item = button.addMenuItem(groups[0].getLabel(), null, getSelectionReplaceScript(dataRegionName, filterColValue, filterColValue.first.getParent().getName()), selected.contains(groups[0]));
                    if (cls.isShared())
                    {
                        item.setImageSrc(new ResourceURL("/reports/grid.gif"));
                        item.setImageCls("fa fa-users");
                    }
                }
            }

            button.addSeparator();
            if (CohortManager.getInstance().hasCohortMenu(context.getContainer(), context.getUser()) &&
                    container.hasPermission(context.getUser(), AdminPermission.class))
            {
                NavTree item = button.addMenuItem("Manage Cohorts", new ActionURL(CohortController.ManageCohortsAction.class, container));
                item.setImageCls("fa fa-cog");
            }

            if (container.hasPermission(user, ReadPermission.class) && !user.isGuest())
            {
                NavTree item = button.addMenuItem("Manage " + study.getSubjectNounSingular() + " Groups", new ActionURL(StudyController.ManageParticipantCategoriesAction.class, container));
                item.setImageCls("fa fa-cog");
                if (hasCreateGroupFromSelection)
                {
                    button.addSeparator();
                    item = new NavTree("Create " + study.getSubjectNounSingular() + " Group");
                    button.addMenuItem(item);

                    NavTree fromSelection = item.addChild("From Selected " + study.getSubjectNounPlural());
                    fromSelection.setScript(createNewParticipantGroupScript(context, dataRegionName, true));
                    // TODO: Ideally, we'd do something like "fromSelection.setRequiresSelection(true)" here, like we can with buttons, but that's not an option

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

    private String getRemoveSelectionScript(String dataRegionName, Study study, Collection<ParticipantCategoryImpl> categories)
    {
        StringBuilder script = new StringBuilder();
        script.append(DataRegion.getJavaScriptObjectReference(dataRegionName)).append("._removeCohortGroupFilters(");
        script.append(PageFlowUtil.jsString(study.getSubjectColumnName()));

        if (!categories.isEmpty())
        {
            script.append(",[");
            String sep = "";
            for (ParticipantCategoryImpl category : categories)
            {
                script.append(sep).append(PageFlowUtil.jsString(category.getLabel()));
                sep = ",";
            }
            script.append("]");
        }

        script.append(");");
        return script.toString();
    }

    private String getSelectionScript(String dataRegionName, Pair<FieldKey, String> filterColValue)
    {
        return DataRegion.getJavaScriptObjectReference(dataRegionName) +
                ".replaceFilter(" + getFilterScript(filterColValue) + ");";
    }

    private String getSelectionReplaceScript(String dataRegionName, Pair<FieldKey, String> filterColValue, String match)
    {
        return DataRegion.getJavaScriptObjectReference(dataRegionName) +
                ".replaceFilterMatch(" + getFilterScript(filterColValue) + "," +
                PageFlowUtil.jsString(match + "/") + ");";
    }

    private StringBuilder getFilterScript(Pair<FieldKey, String> filterColValue)
    {
        StringBuilder script = new StringBuilder();
        script.append("LABKEY.Filter.create(")
                .append(PageFlowUtil.jsString(filterColValue.first.toString()))
                .append(", ")
                .append(PageFlowUtil.jsString(filterColValue.second))
                .append(", LABKEY.Filter.Types.EQUAL)");
        return script;
    }

    private String createNewParticipantGroupScript(ViewContext context, String dataRegionName, boolean fromSelection)
    {
        boolean isAdmin = context.getContainer().hasPermission(context.getUser(), SharedParticipantGroupPermission.class) ||
                context.getContainer().hasPermission(context.getUser(), AdminPermission.class);

        return "LABKEY.requiresExt4ClientAPI(function() {" +
                "LABKEY.requiresScript('study/ParticipantGroup.js', function(){" +
                " Study.window.ParticipantGroup.fromDataRegion(" + PageFlowUtil.jsString(dataRegionName) + "," + fromSelection + "," + isAdmin + "); " +
                "},this);});";
    }

    /**
     * Returns the list of participant categories that the specified user is allowed to see.
     *
     * @param distinctCategories if true, returns the unique (by label) set of categories. A private category will
     *                           supersede a public category.
     */
    public List<ParticipantCategoryImpl> getParticipantCategories(Container c, User user, boolean distinctCategories)
    {
        List<ParticipantCategoryImpl> categories = _getParticipantCategories(c, user);

        if (!distinctCategories)
            return categories;

        Map<String, ParticipantCategoryImpl> categoryMap = new HashMap<>();
        for (ParticipantCategoryImpl category : categories)
        {
            if (!categoryMap.containsKey(category.getLabel()) || !category.isShared())
                categoryMap.put(category.getLabel(), category);
        }

        return new ArrayList<>(categoryMap.values());
    }

    public List<ParticipantCategoryImpl> getParticipantCategories(Container c, User user)
    {
        return getParticipantCategories(c, user, true);
    }

    private List<ParticipantCategoryImpl> _getParticipantCategories(Container c, User user)
    {
        return ParticipantGroupCache.getParticipantCategories(c)
                .stream()
                .filter(category -> category.canRead(user))
                .toList();
    }

    @Deprecated // create participant categories and groups separately
    public ParticipantCategoryImpl setParticipantCategory(Container c, User user, ParticipantCategoryImpl def, String[] participants, String participantFilters, String description) throws ValidationException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            ParticipantCategoryImpl ret;
            boolean isUpdate = !def.isNew();
            List<Throwable> errors;

            ret = _saveParticipantCategory(c, user, def);

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
            transaction.commit();
            ParticipantGroupCache.uncache(c);

            if (def.isNew())
                errors = fireCreatedCategory(user, ret);
            else
                errors = fireUpdateCategory(user, ret);

            if (!errors.isEmpty())
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException) first;
                else
                    throw new RuntimeException(first);
            }
            return ret;
        }
    }

    public ParticipantCategoryImpl setParticipantCategory(Container c, User user, ParticipantCategoryImpl def) throws ValidationException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            ParticipantCategoryImpl ret;
            List<Throwable> errors;

            ret = _saveParticipantCategory(c, user, def);

            switch (ParticipantCategory.Type.valueOf(ret.getType()))
            {
                case query:
                    throw new UnsupportedOperationException("Participant category type: query not yet supported");
                case cohort:
                    throw new UnsupportedOperationException("Participant category type: cohort not yet supported");
            }
            transaction.commit();
            ParticipantGroupCache.uncache(c);

            if (def.isNew())
                errors = fireCreatedCategory(user, ret);
            else
                errors = fireUpdateCategory(user, ret);

            if (!errors.isEmpty())
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException) first;
                else
                    throw new RuntimeException(first);
            }
            return ret;
        }
    }

    private ParticipantCategoryImpl _saveParticipantCategory(Container c, User user, ParticipantCategoryImpl def) throws ValidationException
    {
        ParticipantCategoryImpl ret;
        List<ValidationError> errors = new ArrayList<>();

        if (!def.canEdit(c, user, errors))
            throw new ValidationException(errors);

        if (categoryExists(c, user, def.getLabel(), def.isShared()))
            throw new ValidationException("There is already a category named '" + def.getLabel() + "' within this folder. Please choose a unique (case-insensitive) category name.");

        if (def.isShared())
        {
            if (!c.hasPermission(user, SharedParticipantGroupPermission.class) && !c.hasPermission(user, AdminPermission.class))
                throw new ValidationException("You must be in the Editor role or an Admin to create a shared participant category");
        }

        ParticipantGroupAuditProvider.ParticipantGroupAuditEvent event;
        if (def.isNew())
        {
            ret = Table.insert(user, StudySchema.getInstance().getTableInfoParticipantCategory(), def);
            event = ParticipantGroupAuditProvider.EventFactory.categoryChange(c, user, null, ret);
        }
        else
        {
            ParticipantCategoryImpl prev = getParticipantCategory(c, def.getRowId());
            ret = Table.update(user, StudySchema.getInstance().getTableInfoParticipantCategory(), def, def.getRowId());
            event = ParticipantGroupAuditProvider.EventFactory.categoryChange(c, user, prev, ret);
        }
        AuditLogService.get().addEvent(user, event);

        return ret;
    }

    private ParticipantGroup _setParticipantGroup(Container c, User user, ParticipantGroup group, boolean verifyCategory) throws ValidationException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            if (verifyCategory)
            {
                ParticipantCategoryImpl cat = getParticipantCategory(c, user, group.getCategoryId());

                if (cat == null)
                    throw new ValidationException("The specified category was not found.");

                // canEdit enforces the SharedParticipantGroupPermission/Admin check for shared categories AND the
                // owner check for private categories
                if (!cat.canEdit(c, user))
                    throw new ValidationException("You do not have permission to modify groups in this participant category");
            }

            ParticipantGroup ret;
            ParticipantGroupAuditProvider.ParticipantGroupAuditEvent event;
            if (group.isNew())
            {
                ret = Table.insert(user, getTableInfoParticipantGroup(), group);
                event = ParticipantGroupAuditProvider.EventFactory.groupChange(c, user, null, ret);
            }
            else
            {
                ParticipantGroup prev = getParticipantGroup(c, user, group.getRowId());
                ret = Table.update(user, getTableInfoParticipantGroup(), group, group.getRowId());
                event = ParticipantGroupAuditProvider.EventFactory.groupChange(c, user, prev, ret);
                deleteGroupParticipants(c, user, prev);
            }
            if (event != null)
                AuditLogService.get().addEvent(user, event);

            // add the mapping from group to participants
            SQLFragment sql = new SQLFragment("INSERT INTO ").append(getTableInfoParticipantGroupMap(), "");
            sql.append(" (GroupId, ParticipantId, Container) VALUES (?, ?, ?)");

            Study study = StudyManager.getInstance().getStudy(ContainerManager.getForId(group.getContainerId()));
            SqlExecutor executor = new SqlExecutor(scope);
            Map<String, String> participantIdMap = getParticipantIdMap(c, user, group);

            for (String id : group.getParticipantIds())
            {
                // don't let the database catch the invalid ptid, so we can show a more reasonable error
                if (!participantIdMap.containsKey(id))
                {
                    // issue 38130 - we may have already emptied out the participant IDs in this group and put it back in the
                    // cache
                    ParticipantGroupCache.uncache(c);
                    throw new ValidationException(String.format("The %s ID specified : %s does not exist in this study. Please enter a valid identifier.", study.getSubjectNounSingular(), id));
                }
                executor.execute(sql.getSQL(), group.getRowId(), id, participantIdMap.get(id));
            }
            ParticipantGroupCache.uncache(c);

            transaction.commit();
            return ret;
        }
    }

    /**
     * For dataspace folders, participants may come from multiple subfolders. We want to preserve this
     * So pull participants and containers via the user schema which should be properly filtered.
     * <p>
     * TODO: Really need to pass in containers for each participant rather than rely on uniqueness of ids
     *
     */
    private Map<String, String> getParticipantIdMap(Container c, User user, ParticipantGroup group)
    {
        final String subjectColumnName = StudyService.get().getSubjectColumnName(c);
        final CaseInsensitiveHashMap<String> participantIdMap = new CaseInsensitiveHashMap<>();

        StudyQuerySchema schema = (StudyQuerySchema) QueryService.get().getUserSchema(user, c, "study");

        // We want the DataspaceSchema or StudySchema, but without the session filters applied
        // TODO we need a way to ask for this w/o having to 'undo' the session filters
        schema.setSessionParticipantGroup(null);
        if (schema instanceof DataspaceQuerySchema)
            ((DataspaceQuerySchema) schema).clearSessionContainerFilter();

        TableInfo participantTable = schema.getTable(StudyService.get().getSubjectTableName(c));
        String[] participantIds = group.getParticipantIds();

        SimpleFilter participantFilter = new SimpleFilter();
        participantFilter.addInClause(FieldKey.fromParts(subjectColumnName), Arrays.asList(participantIds));
        TableSelector ts = new TableSelector(participantTable, PageFlowUtil.set(subjectColumnName, "container"), participantFilter, null);
        ts.forEachMap(m -> participantIdMap.put((String) m.get(subjectColumnName), (String) m.get("container")));

        return participantIdMap;
    }

    public void setSessionParticipantGroup(Container c, User user, HttpServletRequest request, Integer groupRowId, boolean showDataRegionMessage)
    {
        HttpSession session = request.getSession(true);
        if (session == null)
            return;

        session.setAttribute(PARTICIPANT_GROUP_SESSION_KEY + c.getRowId(), groupRowId);
        session.setAttribute(PARTICIPANT_GROUP_SESSION_KEY + c.getRowId() + ".showMessage", showDataRegionMessage);
    }

    public ParticipantGroup setSessionParticipantGroup(Container c, User user, HttpServletRequest request, ParticipantGroup group, boolean showDataRegionMessage)
    {
        assert group.isSession();
        HttpSession session = request.getSession(true);
        if (session == null)
            return null;

        session.setAttribute(PARTICIPANT_GROUP_SESSION_KEY + c.getRowId(), group);
        session.setAttribute(PARTICIPANT_GROUP_SESSION_KEY + c.getRowId() + ".showMessage", showDataRegionMessage);

        // don't MemTrack track this one, since it's going in session
        MemTracker.get().remove(group);
        return group;
    }

    public boolean getSessionParticipantGroupShowMessage(Container c, HttpServletRequest request)
    {
        Boolean b = (Boolean) SessionHelper.getAttribute(request, PARTICIPANT_GROUP_SESSION_KEY + c.getRowId() + ".showMessage", false);

        return b == Boolean.TRUE;
    }

    public ParticipantGroup getSessionParticipantGroup(Container c, User user, HttpServletRequest request)
    {
        HttpSession session = request.getSession(true);
        if (session == null)
            return null;

        ParticipantGroup group = null;
        Object o = session.getAttribute(PARTICIPANT_GROUP_SESSION_KEY + c.getRowId());
        if (o instanceof Integer)
            group = getParticipantGroup(c, user, (Integer) o);
        else if (o instanceof ParticipantGroup)
            group = (ParticipantGroup) o;
        return group;
    }

    public void deleteSessionParticipantGroup(Container c, User user, HttpServletRequest request)
    {
        HttpSession session = request.getSession(true);
        if (session == null)
            return;

        session.removeAttribute(PARTICIPANT_GROUP_SESSION_KEY + c.getRowId());
        session.removeAttribute(PARTICIPANT_GROUP_SESSION_KEY + c.getRowId() + ".showMessage");
    }


    public ParticipantGroup setParticipantGroup(Container c, User user, ParticipantGroup group) throws ValidationException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        ParticipantGroup ret;

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            ParticipantGroup curGroup = null;
            if (!group.isNew())
            {
                curGroup = getParticipantGroupFromGroupRowId(c, user, group.getRowId());
                //deleteGroupParticipants(c, user, curGroup);
            }
            ret = _setParticipantGroup(c, user, group, true);

            // clear the category->group cache if the category has been changed for this group
            if (curGroup != null && (curGroup.getCategoryId() != group.getCategoryId()))
            {
                ParticipantGroupCache.uncache(c);
            }

            transaction.commit();
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

    private enum Modification
    {ADD, REMOVE}

    private ParticipantCategoryImpl modifyParticipantCategory(Container c, User user, ParticipantCategoryImpl def, String[] participants, Modification modification) throws ValidationException
    {
        List<ValidationError> validationErrors = new ArrayList<>();

        if (!def.canEdit(c, user, validationErrors))
            throw new ValidationException(validationErrors);

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            ParticipantCategoryImpl ret;
            List<Throwable> errors;

            List<ParticipantGroup> groups = getParticipantGroups(c, user, def);
            if (groups.size() != 1)
                throw new RuntimeException("Expected one group in category " + def.getLabel());
            ParticipantGroup group = groups.get(0);

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
            transaction.commit();
            ParticipantGroupCache.uncache(c);

            //Reselect
            ret = getParticipantCategory(c, user, def.getRowId());

            errors = fireUpdateCategory(user, ret);

            if (!errors.isEmpty())
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException) first;
                else
                    throw new RuntimeException(first);
            }
            return ret;
        }
    }

    private void addGroupParticipants(Container c, User user, ParticipantGroup group, String[] participantsToAdd) throws ValidationException
    {
        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            if (group.isNew())
                throw new IllegalArgumentException("Adding participants to non-existent group.");

            // add the mapping from group to participants
            SQLFragment sql = new SQLFragment("INSERT INTO ").append(getTableInfoParticipantGroupMap(), "");
            sql.append(" (GroupId, ParticipantId, Container) VALUES (?, ?, ?)");

            Set<String> existingMembers = group.getParticipantSet();
            Study study = StudyManager.getInstance().getStudy(ContainerManager.getForId(group.getContainerId()));
            SqlExecutor executor = new SqlExecutor(scope);
            for (String id : participantsToAdd)
            {
                if (existingMembers.contains(id))
                    continue;

                Participant p = StudyManager.getInstance().getParticipant(study, id);

                // don't let the database catch the invalid ptid, so we can show a more reasonable error
                if (p == null)
                    throw new ValidationException(String.format("The %s ID specified : %s does not exist in this study. Please enter a valid identifier.", study.getSubjectNounSingular(), id));

                executor.execute(sql.getSQL(), group.getRowId(), id, group.getContainerId());
                group.addParticipantId(id);
            }
            ParticipantGroupCache.uncache(c);

            transaction.commit();
        }
    }

    private void removeGroupParticipants(Container c, User user, ParticipantGroup group, String[] participantsToRemove)
    {
        // remove the mapping from group to participants
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroupMap(), "");
        SimpleFilter filter = new SimpleFilter().addInClause(FieldKey.fromParts("ParticipantId"), Arrays.asList(participantsToRemove));
        filter.addCondition(FieldKey.fromParts("GroupId"), group.getRowId());
        sql.append(filter.getSQLFragment(StudySchema.getInstance().getSchema().getSqlDialect()));

        new SqlExecutor(StudySchema.getInstance().getSchema()).execute(sql);
        ParticipantGroupCache.uncache(c);
    }

    private void deleteGroupParticipants(Container c, User user, ParticipantGroup group)
    {
        // remove the mapping from group to participants
        SQLFragment sql = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroupMap(), "");
        sql.append(" WHERE GroupId = ?");

        SqlExecutor executor = new SqlExecutor(StudySchema.getInstance().getSchema());
        executor.execute(sql.getSQL(), group.getRowId());
        ParticipantGroupCache.uncache(c);
    }

    public @Nullable ParticipantCategoryImpl getParticipantCategory(Container c, int rowId)
    {
        return ParticipantGroupCache.getParticipantCategory(c, rowId);
    }

    public @Nullable ParticipantCategoryImpl getParticipantCategory(Container c, User user, int rowId)
    {
        ParticipantCategoryImpl category = getParticipantCategory(c, rowId);
        if (category != null && category.canRead(user))
            return category;

        return null;
    }

    public ParticipantGroup getParticipantGroup(Container container, User user, int rowId)
    {
        return ParticipantGroupCache.getParticipantGroup(container, rowId);
    }

    public ParticipantGroup getParticipantGroupFromGroupRowId(Container container, User user, int rowId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(FieldKey.fromParts("RowId"), rowId);

        TableSelector selector = new TableSelector(getTableInfoParticipantGroup(), filter, null);
        return selector.getObject(ParticipantGroup.class);
    }

    public List<ParticipantGroup> getParticipantGroups(final Container c, User user, ParticipantCategoryImpl def)
    {
        if (!def.isNew())
        {
            ParticipantCategoryImpl category = getParticipantCategory(c, def.getRowId());
            if (category != null)
                return Arrays.stream(category.getGroups()).toList();
        }
        return Collections.emptyList();
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

    private void updateListTypeDef(Container c, User user, ParticipantCategoryImpl def, boolean update, String[] participants, String filters, String description) throws ValidationException
    {
        assert !def.isNew() : "The participant category has not been created yet";

        if (!def.isNew())
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                ParticipantGroup group = new ParticipantGroup();

                group.setCategoryId(def.getRowId());
                group.setContainer(def.getContainerId());

                // updating an existing category
                if (update)
                {
                    List<ParticipantGroup> groups = getParticipantGroups(c, user, def);
                    if (groups.size() == 1)
                    {
                        group = groups.get(0);
                        deleteGroupParticipants(c, user, group);
                    }
                }
                group.setLabel(def.getLabel());
                group.setParticipantIds(participants);
                group.setFilters(filters);
                group.setDescription(description);

                group = _setParticipantGroup(c, user, group, false);
                def.setGroups(new ParticipantGroup[]{group});

                transaction.commit();
            }
        }
    }

    public void deleteParticipantCategory(Container c, User user, ParticipantCategoryImpl def) throws ValidationException
    {
        List<ValidationError> validationErrors = new ArrayList<>();
        if (def.isNew())
            throw new ValidationException("Participant category has not been saved to the database yet");

        if (!def.canDelete(c, user, validationErrors))
            throw new ValidationException(validationErrors);

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            SqlExecutor executor = new SqlExecutor(scope);

            // remove any participant group mappings from the junction table
            SQLFragment sqlMapping = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroupMap(), "").append(" WHERE GroupId IN ");
            sqlMapping.append("(SELECT RowId FROM ").append(getTableInfoParticipantGroup(), "pg").append(" WHERE CategoryId = ?)");
            executor.execute(sqlMapping.getSQL(), def.getRowId());

            // delete the participant groups
            SQLFragment sqlGroup = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroup(), "").append(" WHERE RowId IN ");
            sqlGroup.append("(SELECT RowId FROM ").append(getTableInfoParticipantGroup(), "pg").append(" WHERE CategoryId = ?)");
            executor.execute(sqlGroup.getSQL(), def.getRowId());

            // delete the participant list definition
            SQLFragment sql = new SQLFragment("DELETE FROM ").append(StudySchema.getInstance().getTableInfoParticipantCategory(), "").append(" WHERE RowId = ?");
            executor.execute(sql.getSQL(), def.getRowId());

            ParticipantGroupAuditProvider.ParticipantGroupAuditEvent event = ParticipantGroupAuditProvider.EventFactory.categoryDeleted(c, user, def);
            AuditLogService.get().addEvent(user, event);

            ParticipantGroupCache.uncache(c);
            transaction.commit();

            List<Throwable> errors = fireDeleteCategory(user, def);
            if (!errors.isEmpty())
            {
                Throwable first = errors.get(0);
                if (first instanceof RuntimeException)
                    throw (RuntimeException) first;
                else
                    throw new RuntimeException(first);
            }
        }
    }

    public void deleteParticipantGroup(Container c, User user, ParticipantGroup group) throws ValidationException
    {
        ParticipantCategoryImpl cat = getParticipantCategory(c, user, group.getCategoryId());
        if (cat == null)
            return;

        List<ValidationError> errors = new ArrayList<>();
        if (!cat.canDelete(c, user, errors))
            throw new ValidationException(errors);

        DbScope scope = StudySchema.getInstance().getSchema().getScope();

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            SqlExecutor executor = new SqlExecutor(scope);

            // remove any participant group mappings from the junction table
            SQLFragment sqlMapping = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroupMap(), "").append(" WHERE GroupId = ? ");
            executor.execute(sqlMapping.getSQL(), group.getRowId());

            // delete the participant group
            SQLFragment sqlGroup = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantGroup(), "").append(" WHERE RowId = ? ");
            executor.execute(sqlGroup.getSQL(), group.getRowId());

            // if this is a list type of group (we automatically create a category of the same name), clean up
            // the associated category if it is not referenced
            if (cat.getType().equals("list"))
            {
                List<ParticipantGroup> groups = getParticipantGroups(c, user, cat);
                if (groups.size() == 1 && groups.get(0).equals(group))
                {
                    // delete the participant category
                    SQLFragment sqlCat = new SQLFragment("DELETE FROM ").append(getTableInfoParticipantCategory(), "").append(" WHERE RowId = ? ");
                    executor.execute(sqlCat.getSQL(), cat.getRowId());
                }
            }
            ParticipantGroupAuditProvider.ParticipantGroupAuditEvent event = ParticipantGroupAuditProvider.EventFactory.groupDeleted(c, user, group);
            AuditLogService.get().addEvent(user, event);

            transaction.commit();
            ParticipantGroupCache.uncache(c);
        }
    }

    public List<String> getParticipantsFromSelection(Container container, QueryView view, Collection<String> lsids) throws SQLException
    {
        List<String> ptids = new ArrayList<>();
        TableInfo table = view.getTable();

        if (table != null)
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addClause(new SimpleFilter.InClause(FieldKey.fromParts("lsid"), lsids));
            FieldKey ptidKey = FieldKey.fromParts(StudyService.get().getSubjectColumnName(container));

            try (Results r = new TableSelector(table, table.getColumns(ptidKey.toString(), "lsid"), filter, null).getResults())
            {
                ResultSet rs = r.getResultSet();

                if (rs != null)
                {
                    ColumnInfo ptidColumnInfo = r.getFieldMap().get(ptidKey);
                    int ptidIndex = (null != ptidColumnInfo) ? ptidColumnInfo.findColumn(rs) : 0;

                    while (rs.next() && ptidIndex > 0)
                    {
                        String ptid = rs.getString(ptidIndex);
                        ptids.add(ptid);
                    }
                }
            }
        }

        return ptids;
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
        List<Throwable> errors = new ArrayList<>();

        for (ParticipantCategoryListener l : _listeners)
        {
            try
            {
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
        List<Throwable> errors = new ArrayList<>();

        for (ParticipantCategoryListener l : _listeners)
        {
            try
            {
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
        List<Throwable> errors = new ArrayList<>();

        for (ParticipantCategoryListener l : _listeners)
        {
            try
            {
                l.categoryCreated(user, category);
            }
            catch (Throwable t)
            {
                errors.add(t);
            }
        }
        return errors;
    }

    public void clearCache(Container c)
    {
        ParticipantGroupCache.uncache(c);
    }

    public static class ParticipantGroupTestCase extends AbstractContainerScopingTest
    {
        private static final ParticipantGroupManager MANAGER = ParticipantGroupManager.getInstance();
        private Container _container;
        private User _owner;
        private User _otherUser;

        @Before
        public void setupParticipantGroupFixtures() throws Exception
        {
            _container = createContainer("study");
            _owner = createUserInRole(_container, ReaderRole.class);
            _otherUser = createUserInRole(_container, ReaderRole.class);
        }

        @Test
        public void test()
        {
            ParticipantGroupManager p = new ParticipantGroupManager();
            ParticipantCategoryImpl def = new ParticipantCategoryImpl();
            p.getParticipantGroups(null, null, def);
        }

        /** The by-label getter must apply canRead(), so by-label does not disclose a private category to a non-owner. */
        @Test
        public void byLabelGetterHidesAnotherUsersPrivateCategory() throws Exception
        {
            ParticipantCategoryImpl owned = createPrivateCategory(_owner, "Private-by-label");

            ParticipantCategoryImpl asOwner = MANAGER.getParticipantCategory(_container, _owner, "Private-by-label");
            assertNotNull("Owner should resolve their own private category", asOwner);
            assertEquals("Owner should resolve the real (persisted) category", owned.getRowId(), asOwner.getRowId());
            assertFalse("Resolved category should be private", asOwner.isShared());
            assertEquals(_owner.getUserId(), asOwner.getOwnerId());

            ParticipantCategoryImpl asOther = MANAGER.getParticipantCategory(_container, _otherUser, "Private-by-label");
            assertNull("Another user must not resolve the owner's private category by label", asOther);
        }

        /** A shared category is readable by any user, so the by-label getter must still return it for a non-owner. */
        @Test
        public void byLabelGetterReturnsSharedCategoryForAnyUser() throws Exception
        {
            ParticipantCategoryImpl shared = createSharedCategory("Shared-by-label");

            ParticipantCategoryImpl asOther = MANAGER.getParticipantCategory(_container, _otherUser, "Shared-by-label");
            assertNotNull("A shared category must be readable by any user", asOther);
            assertEquals(shared.getRowId(), asOther.getRowId());
            assertTrue(asOther.isShared());
        }

        /** getParticipantCategoriesByLabel delegates to the by-label getter and must inherit the same canRead() check. */
        @Test
        public void byLabelListGetterHidesAnotherUsersPrivateCategory() throws Exception
        {
            createPrivateCategory(_owner, "Private-by-label-list");

            assertEquals("Owner should see their private category by label",
                    1, MANAGER.getParticipantCategoriesByLabel(_container, _owner, "Private-by-label-list").size());
            assertTrue("Another user must not see the owner's private category by label",
                    MANAGER.getParticipantCategoriesByLabel(_container, _otherUser, "Private-by-label-list").isEmpty());
        }

        /** getParticipantCategoriesByType must also filter by canRead(), so a private category isn't leaked by type. */
        @Test
        public void byTypeGetterHidesAnotherUsersPrivateCategory() throws Exception
        {
            ParticipantCategoryImpl owned = createPrivateCategory(_owner, "Private-by-type");
            String type = ParticipantCategory.Type.list.name();

            assertTrue("Owner should see their private category among the typed categories",
                    containsRowId(MANAGER.getParticipantCategoriesByType(_container, _owner, type), owned.getRowId()));
            assertFalse("Another user must not see the owner's private category among the typed categories",
                    containsRowId(MANAGER.getParticipantCategoriesByType(_container, _otherUser, type), owned.getRowId()));
        }

        /** The collection getter must hide another user's private category while still surfacing shared categories. */
        @Test
        public void collectionGetterHidesPrivateButReturnsShared() throws Exception
        {
            ParticipantCategoryImpl owned = createPrivateCategory(_owner, "Private-collection");
            ParticipantCategoryImpl shared = createSharedCategory("Shared-collection");

            List<ParticipantCategoryImpl> asOwner = MANAGER.getParticipantCategories(_container, _owner);
            assertTrue("Owner should see their own private category", containsRowId(asOwner, owned.getRowId()));
            assertTrue("Owner should see the shared category", containsRowId(asOwner, shared.getRowId()));

            List<ParticipantCategoryImpl> asOther = MANAGER.getParticipantCategories(_container, _otherUser);
            assertFalse("Another user must not see the owner's private category", containsRowId(asOther, owned.getRowId()));
            assertTrue("Shared categories remain visible to everyone", containsRowId(asOther, shared.getRowId()));
        }

        private static boolean containsRowId(Collection<ParticipantCategoryImpl> categories, int rowId)
        {
            return categories.stream().anyMatch(category -> category.getRowId() == rowId);
        }

        /** Create a private (owner-scoped) participant category, persisted and owned by {@code owner}. */
        private ParticipantCategoryImpl createPrivateCategory(User owner, String label) throws ValidationException
        {
            ParticipantCategoryImpl def = new ParticipantCategoryImpl();
            def.setContainer(_container.getId());
            def.setLabel(label);
            def.setType(ParticipantCategory.Type.list.name());
            def.setOwnerId(owner.getUserId());
            return MANAGER.setParticipantCategory(_container, owner, def);
        }

        /** Create a shared participant category. Created as the site admin, who may create shared categories. */
        private ParticipantCategoryImpl createSharedCategory(String label) throws ValidationException
        {
            ParticipantCategoryImpl def = new ParticipantCategoryImpl();
            def.setContainer(_container.getId());
            def.setLabel(label);
            def.setType(ParticipantCategory.Type.list.name());
            // OwnerId defaults to OWNER_SHARED
            return MANAGER.setParticipantCategory(_container, getAdmin(), def);
        }
    }

    public static class ContainerScopingTestCase extends AbstractContainerScopingTest
    {
        @Test
        public void testSetParticipantGroupRequiresOwnership() throws Exception
        {
            // setParticipantGroup() saves a group keyed by a global rowId. For a PRIVATE category, canEdit() allows only
            // the category's creator -- but the manager previously enforced only the shared-category case, so a Read
            // user could overwrite another user's private group via a guessable rowId. The fix gates _setParticipantGroup
            // on canEdit() for the private case too.
            Container folder = createContainer("A");
            StudyService.get().createStudy(folder, getAdmin(), "Study", TimepointType.VISIT, true);
            insertParticipant(folder, "P1");

            ParticipantGroupManager mgr = ParticipantGroupManager.getInstance();

            // A PRIVATE participant category + group owned by the admin (ownerId != OWNER_SHARED makes it private; the
            // admin is its creator, so only the admin may edit it).
            ParticipantCategoryImpl cat = new ParticipantCategoryImpl();
            cat.setContainer(folder.getId());
            cat.setLabel("private-category");
            cat.setType("list");
            cat.setOwnerId(getAdmin().getUserId());
            cat = mgr.setParticipantCategory(folder, getAdmin(), cat, new String[]{"P1"}, null, "private");
            ParticipantGroup group = mgr.getParticipantGroups(folder, getAdmin(), cat).get(0);

            // A different user with only Read access (not the owner, not an admin)
            User attacker = createUserInRole(folder, ReaderRole.class);

            // Saving (overwriting) the admin's private group as the attacker must be rejected.
            try
            {
                mgr.setParticipantGroup(folder, attacker, group);
                fail("A non-owner must not be able to modify another user's private participant group");
            }
            catch (ValidationException expected)
            {
            }

            // Positive control: the owner (admin) can still save their own private group -- the guard rejects only the
            // non-owner, not every caller.
            mgr.setParticipantGroup(folder, getAdmin(), group);
        }

        private void insertParticipant(Container c, String ptid)
        {
            Map<String, Object> row = new HashMap<>();
            row.put("Container", c.getId());
            row.put("ParticipantId", ptid);
            Table.insert(getAdmin(), StudySchema.getInstance().getTableInfoParticipant(), row);
        }
    }
}
