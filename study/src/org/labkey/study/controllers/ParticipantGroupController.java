/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.study.controllers;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Table;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.ParticipantCategory;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.ParticipantCategoryImpl;
import org.labkey.study.model.ParticipantGroup;
import org.labkey.study.model.ParticipantGroupManager;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DataspaceQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;

import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: May 30, 2011
 * Time: 2:58:38 PM
 */
public class ParticipantGroupController extends BaseStudyController
{
    enum GroupType {
        participantGroup,
        cohort,
        participantCategory,
    }

    private static final ActionResolver ACTION_RESOLVER = new DefaultActionResolver(ParticipantGroupController.class);

    public ParticipantGroupController()
    {
        super();
        setActionResolver(ACTION_RESOLVER);
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class CreateParticipantCategory extends MutatingApiAction<ParticipantCategorySpecification>
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            form.setContainer(getContainer().getId());

            ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form, form.getParticipantIds(), form.getFilters(), form.getDescription());

            resp.put("success", true);
            resp.put("category", category.toJSON());

            return resp;
        }
    }

    /**
     * Bean used to create and update participant categories
     */
    public static class ParticipantCategorySpecification extends ParticipantCategoryImpl
    {
        private String[] _participantIds = new String[0];
        private String _filters;
        private String _description;

        public String[] getParticipantIds()
        {
            return _participantIds;
        }

        public void setParticipantIds(String[] participantIds)
        {
            _participantIds = participantIds;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getFilters()
        {
            return _filters;
        }

        public void setFilters(String filters)
        {
            _filters = filters;
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class UpdateParticipantCategory extends MutatingApiAction<ParticipantCategorySpecification>
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            if (form.isNew())
            {
                throw new IllegalArgumentException("The specified category does not exist, you must pass in the RowId");
            }

            ParticipantCategoryImpl category  = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), form.getRowId());
            if (category != null)
            {
                form.copySpecialFields(category);
                category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form, form.getParticipantIds(), form.getFilters(), form.getDescription());

                resp.put("success", true);
                resp.put("category", category.toJSON());

                return resp;
            }
            else
                throw new RuntimeException("Unable to update the category with rowId: " + form.getRowId());
        }
    }

    enum Modification {ADD, REMOVE};

    private abstract class ModifyCategoryParticipants extends MutatingApiAction<ParticipantCategorySpecification>
    {
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors, Modification modification) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            if (form.isNew())
            {
                throw new IllegalArgumentException("The specified category does not exist, you must pass in the RowId");
            }

            ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), form.getRowId());
            if (category != null)
            {
                ParticipantCategoryImpl def = category;
                form.copySpecialFields(def);

                if (modification == Modification.ADD)
                    category = ParticipantGroupManager.getInstance().addCategoryParticipants(getContainer(), getUser(), def, form.getParticipantIds());
                else
                    category = ParticipantGroupManager.getInstance().removeCategoryParticipants(getContainer(), getUser(), def, form.getParticipantIds());

                resp.put("success", true);
                resp.put("category", category.toJSON());

                return resp;
            }
            else
                throw new RuntimeException("Unable to update the category with rowId: " + form.getRowId());
        }

    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class AddParticipantsToCategory extends ModifyCategoryParticipants
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            return super.execute(form, errors, Modification.ADD);
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class RemoveParticipantsFromCategory extends ModifyCategoryParticipants
    {
        @Override
        public ApiResponse execute(ParticipantCategorySpecification form, BindException errors) throws Exception
        {
            return super.execute(form, errors, Modification.REMOVE);
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class GetParticipantCategory extends ApiAction<ParticipantCategoryImpl>
    {
        @Override
        public ApiResponse execute(ParticipantCategoryImpl form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), form.getLabel());

            resp.put("success", true);
            resp.put("category", category.toJSON());

            return resp;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetParticipantCategories extends ApiAction<GetParticipantCategoriesForm>
    {
        @Override
        public ApiResponse execute(GetParticipantCategoriesForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            Collection<ParticipantCategoryImpl> categories;
            if (form.getCategoryType() != null && form.getCategoryType().equals("manual"))
            {
                categories = ParticipantGroupManager.getInstance().getParticipantCategoriesByType(getContainer(), getUser(), form.getCategoryType());
            }
            else
            {
                categories = ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser());
            }

            JSONArray defs = new JSONArray();

            for (ParticipantCategoryImpl pc : categories)
            {
                defs.put(pc.toJSON());
            }
            resp.put("success", true);
            resp.put("categories", defs);

            return resp;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetParticipantGroupsWithLiveFilters extends ApiAction
    {
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            Container c = getContainer();
            User u = getUser();
            JSONArray jsonGroups = new JSONArray();
            ApiSimpleResponse resp = new ApiSimpleResponse();

            for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(c, u))
            {
                for (ParticipantGroup group : ParticipantGroupManager.getInstance().getParticipantGroups(c, u, category))
                {
                    if (group.hasLiveFilter())
                    {
                        jsonGroups.put(group.toJSON(false /*includeParticipants*/));
                    }
                }
            }

            resp.put("success", true);
            resp.put("participantGroups", jsonGroups);
            return resp;
        }
    }

    public static class GetParticipantCategoriesForm
    {
        private String _categoryType;

        public String getCategoryType()
        {
            return _categoryType;
        }

        public void setCategoryType(String categoryType)
        {
            _categoryType = categoryType;
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class DeleteParticipantCategory extends MutatingApiAction<ParticipantCategoryImpl>
    {
        @Override
        public ApiResponse execute(ParticipantCategoryImpl form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ParticipantCategoryImpl category = form;

            if (form.isNew())
            {
                // try to match a single category by label/container
                List<ParticipantCategoryImpl> defs = ParticipantGroupManager.getInstance().getParticipantCategoriesByLabel(getContainer(), getUser(), form.getLabel());
                if (defs.size() == 1)
                    category = defs.get(0);
            }
            else
            {
                category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), form.getRowId());
            }

            if (category != null)
            {
                ParticipantGroupManager.getInstance().deleteParticipantCategory(getContainer(), getUser(), category);
                resp.put("success", true);
            }
            return resp;
        }
    }

    @RequiresPermission(DeletePermission.class)
    /**
     * A little confusing to have two actions to delete categories. This is a temporary measure to allow categories to be
     * deleted through the schema browser query views, until we can implement a UI to handle management of categories
     * and groups.
     */
    public class DeleteParticipantCategories extends FormHandlerAction<QueryForm>
    {
        private ActionURL _returnURL;

        @Override
        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            _returnURL = form.getReturnActionURL();

            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (String survey : DataRegionSelection.getSelected(getViewContext(), true))
                {
                    int rowId = NumberUtils.toInt(survey);
                    ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), rowId);

                    if (category != null)
                        ParticipantGroupManager.getInstance().deleteParticipantCategory(getContainer(), getUser(), category);
                }
                transaction.commit();
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(QueryForm form)
        {
            return _returnURL;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetParticipantsFromSelectionAction extends MutatingApiAction<ParticipantSelection>
    {
        @Override
        public ApiResponse execute(ParticipantSelection form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            try
            {
                Set<String> ptids = new LinkedHashSet<>();

                QuerySettings settings = form.getQuerySettings();
                ActionURL url = new ActionURL();
                url.setRawQuery(form.getRequestURL());
                form.getQuerySettings().setSortFilterURL(url);
                settings.setMaxRows(Table.ALL_ROWS);

                UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
                if (schema == null)
                {
                    errors.reject(ERROR_MSG, "schema not found");
                    return null;
                }

                QueryView view = schema.createView(getViewContext(), settings, errors);
                if (view == null)
                {
                    errors.reject(ERROR_MSG, "view not found");
                    return null;
                }

                if (form.isSelectAll())
                {
                    for (String ptid : StudyController.generateParticipantList(view))
                        ptids.add(ptid);
                }
                else
                {
                    List<String> participants = ParticipantGroupManager.getInstance().getParticipantsFromSelection(getContainer(), view, Arrays.asList(form.getSelections()));
                    ptids.addAll(participants);
                }
                resp.put("ptids", ptids);
                resp.put("success", true);
            }
            catch (SQLException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
            return resp;
        }
    }

    public static class ParticipantSelection extends QueryForm
    {
        private String[] _selections;
        private boolean _selectAll;
        private String _requestURL;

        public String getRequestURL()
        {
            return _requestURL;
        }

        public void setRequestURL(String requestURL)
        {
            _requestURL = requestURL;
        }

        public String[] getSelections()
        {
            return _selections;
        }

        public void setSelections(String[] selections)
        {
            _selections = selections;
        }

        public boolean isSelectAll()
        {
            return _selectAll;
        }

        public void setSelectAll(boolean selectAll)
        {
            _selectAll = selectAll;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BrowseParticipantGroups extends ApiAction<BrowseGroupsForm>
    {
        private Collection<String> _allParticipants;

        @Override
        public void validateForm(BrowseGroupsForm form, Errors errors)
        {
            if (null == getStudy())
                errors.reject(ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(BrowseGroupsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            List<JSONObject> groups = new ArrayList<>();

            for (String type : form.getType())
            {
                GroupType groupType = GroupType.valueOf(type);
                Set<String> selectedParticipants = new HashSet<>();
                switch(groupType)
                {
                    case participantGroup:
                        // the api will support either requesting a specific participant category/group or all of
                        // the categories (and groups)
                        if (form.getCategoryId() != -1)
                        {
                            ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), form.getCategoryId());
                            addCategory(form, category, groups);
                        }
                        else if (form.getGroupId() != -1)
                        {
                            // NOTE: this can expose the participant group information to a user that can't otherwise see it via the standard UI in the study module
                            ParticipantGroup group = ParticipantGroupManager.getInstance().getParticipantGroupFromGroupRowId(getContainer(), getUser(), form.getGroupId());
                            if (group != null)
                            {
                                ParticipantCategoryImpl category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), group.getCategoryId());
                                JSONGroup jsonGroup = new JSONGroup(group, category);
                                groups.add(jsonGroup.toJSON(getViewContext()));
                            }
                        }
                        else
                        {
                            for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), form.isDistinctCategories()))
                                addCategory(form, category, groups);
                        }
                        break;
                    case participantCategory:
                        for (ParticipantCategoryImpl category : ParticipantGroupManager.getInstance().getParticipantCategories(getContainer(), getUser(), form.isDistinctCategories()))
                        {
                            // omit orphaned categories
                            if (category.getGroups().length > 0)
                                groups.add(category.toJSON());
                        }
                        break;
                    case cohort:
                        if (!_study.isDataspaceStudy())
                        {
                            for (CohortImpl cohort : getCohorts())
                            {
                                selectedParticipants.addAll(cohort.getParticipantSet());
                                JSONGroup jsonGroup = new JSONGroup(cohort);
                                if (form.includeParticipantIds())
                                    jsonGroup.setParticipantIds(cohort.getParticipantSet());

                                groups.add(jsonGroup.toJSON(getViewContext()));
                            }

                            if (form.isIncludeUnassigned() && hasUnassignedParticipants(selectedParticipants))
                            {
                                JSONGroup notInCohortGroup = new JSONGroup(-1, -1, "Not in any cohort", GroupType.cohort, null);
                                // Issue 18435: treat "Not in any cohort" as an unenrolled cohort when selecting initial state for subjects webpart
                                notInCohortGroup._enrolled = false;
                                groups.add(notInCohortGroup.toJSON(getViewContext()));
                            }
                        }
                        break;
                }
            }
            resp.put("success", true);
            resp.put("groups", groups);

            return resp;
        }

        private void addCategory(BrowseGroupsForm form, ParticipantCategoryImpl category, List<JSONObject> groups)
        {
            if (form.isIncludePrivateGroups() || category.isShared())
            {
                Set<String> selectedParticipants = new HashSet<>();

                for (ParticipantGroup group : category.getGroups())
                {
                    selectedParticipants.addAll(group.getParticipantSet());
                    JSONGroup jsonGroup = new JSONGroup(group, category);
                    if (form.includeParticipantIds())
                        jsonGroup.setParticipantIds(group.getParticipantSet());

                    groups.add(jsonGroup.toJSON(getViewContext()));
                }

                if (category.getGroups().length > 0)
                {
                    Collection<String> unassigned = getParticipantIdsNotInGroupCategory(category);
                    if (form.isIncludeUnassigned() && (unassigned.size() > 0))
                        groups.add(new JSONGroup(-1, category.getRowId(), "Not in any group", GroupType.participantGroup, category).toJSON(getViewContext()));
                }
            }
        }

        /**
         * Determines if the specified set of participants represents all available participants for this folder
         * @param selectedParticipants
         * @return
         */
        private boolean hasUnassignedParticipants(Set<String> selectedParticipants)
        {
            if (_allParticipants == null)
                _allParticipants = getParticipantIds();

            Set<String> participants = new HashSet<>();
            participants.addAll(_allParticipants);

            return !CollectionUtils.isEqualCollection(selectedParticipants, _allParticipants);
        }
    }


    static class JSONGroup
    {
        private int _groupId;
        private String _label;
        private boolean _enrolled = true;
        private GroupType _type;
        private int _categoryId;
        private String _filters;
        private String _description;
        private Set<String> _participantIds = new HashSet<>();
        private ParticipantCategoryImpl _category;
        private Integer _createdBy;
        private Date _created;
        private Integer _modifiedBy;
        private Date _modified;

        public JSONGroup(ParticipantGroup group, ParticipantCategoryImpl category)
        {
            _groupId = group.getRowId();
            _categoryId = group.getCategoryId();
            _label = group.getLabel();
            _filters = group.getFilters();
            _description = group.getDescription();
            _createdBy = group.getCreatedBy();
            _created = group.getCreated();
            _modifiedBy = group.getModifiedBy();
            _modified = group.getModified();
            _category = category;
            _type = GroupType.participantGroup;
        }

        public JSONGroup(CohortImpl cohort)
        {
            _groupId = cohort.getRowId();
            _categoryId = cohort.getRowId();
            _label = cohort.getLabel();
            _enrolled = cohort.isEnrolled();
            _type = GroupType.cohort;
        }

        public JSONGroup(int groupId, int categoryId, String label, GroupType type, ParticipantCategoryImpl category)
        {
            _groupId = groupId;
            _categoryId = categoryId;
            _label = label;
            _type = type;
            _category = category;
        }

        public void setParticipantIds(Set<String> participantIds)
        {
            _participantIds = participantIds;
        }

        public JSONObject toJSON(ViewContext context)
        {
            JSONObject json = new JSONObject();

            json.put("id", _groupId);
            json.put("label", _label);
            json.put("enrolled", _enrolled);
            json.put("type", _type);
            json.put("categoryId", _categoryId);
            if (_filters != null)
                json.put("filters", _filters);
            if (_description != null)
                json.put("description", _description);
            json.put("participantIds", _participantIds);
            if (_category != null)
                json.put("category", _category.toJSON());
            if (_createdBy != null)
                json.put("createdBy", getUserJSON(context, _createdBy));
            if (_created != null)
                json.put("created", _created);
            if (_modifiedBy != null)
                json.put("modifiedBy", getUserJSON(context, _modifiedBy));
            if (_modified != null)
                json.put("modified", _modified);

            return json;
        }

        private JSONObject getUserJSON(ViewContext context, int id)
        {
            JSONObject json = new JSONObject();
            User currentUser = context.getUser();
            User user = UserManager.getUser(id);
            json.put("value", id);
            json.put("displayValue", user != null ? user.getDisplayName(currentUser) : null);

            return json;
        }
    }

    public static class BrowseGroupsForm
    {
        private String[] _type = new String[0];
        private boolean _includeParticipantIds = false;
        private boolean _includePrivateGroups = true;
        private boolean _includeUnassigned = true;
        private boolean _distinctCategories = true;
        private int _categoryId = -1;
        private int _groupId = -1;

        public boolean isIncludePrivateGroups()
        {
            return _includePrivateGroups;
        }

        public void setIncludePrivateGroups(boolean includePrivateGroups)
        {
            _includePrivateGroups = includePrivateGroups;
        }

        public String[] getType()
        {
            return _type;
        }

        public void setType(String[] type)
        {
            _type = type;
        }

        public boolean includeParticipantIds()
        {
            return _includeParticipantIds;
        }

        public void setIncludeParticipantIds(boolean includeParticipantIds)
        {
            _includeParticipantIds = includeParticipantIds;
        }

        public int getCategoryId()
        {
            return _categoryId;
        }

        public void setCategoryId(int categoryId)
        {
            _categoryId = categoryId;
        }

        public boolean isIncludeUnassigned()
        {
            return _includeUnassigned;
        }

        public void setIncludeUnassigned(boolean includeUnassigned)
        {
            _includeUnassigned = includeUnassigned;
        }

        public boolean isDistinctCategories()
        {
            return _distinctCategories;
        }

        public void setDistinctCategories(boolean distinctCategories)
        {
            _distinctCategories = distinctCategories;
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

    public static class GroupsForm implements CustomApiForm
    {
        private List<Group> _groups = new ArrayList<>();

        public List<Group> getGroups()
        {
            return _groups;
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            Object groups = props.get("groups");

            if (groups instanceof JSONArray)
            {
                JSONArray groupArr = (JSONArray)groups;

                for (int i=0; i < groupArr.length(); i++)
                {
                    JSONObject group = groupArr.getJSONObject(i);

                    GroupType type = GroupType.valueOf(group.getString("type"));

                    int id = group.getInt("id");
                    int categoryId = id;

                    // prior to 12.3 the api didn't return a categoryId for cohorts, now the categoryId is the
                    // same as the cohort id but because of saved reports we can't assume we will always get one.
                    if (group.has("categoryId"))
                        categoryId = group.getInt("categoryId");

                    _groups.add(new Group(type, id, categoryId));
                }
            }
        }

        static class Group
        {
            GroupType type;
            int id;
            int categoryId;

            public Group(GroupType type, int id, int categoryId)
            {
                this.type = type;
                this.id = id;
                this.categoryId = categoryId;
            }
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetSubjectsFromGroups extends ApiAction<GroupsForm>
    {
        private StudyImpl _study;

        @Override
        public void validateForm(GroupsForm groupsForm, Errors errors)
        {
            _study = getStudy(getContainer());
            if (_study == null)
                errors.reject(ERROR_MSG, "A study does not exist in this folder");
        }

        @Override
        public ApiResponse execute(GroupsForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            Map<String, Set<String>> categoryToSubjectMap = new HashMap<>();

            for (GroupsForm.Group group : form.getGroups())
            {
                String key = group.type.name();
                if (group.type != GroupType.cohort)
                    key = group.type.name() + "|" + group.categoryId;

                if (!categoryToSubjectMap.containsKey(key))
                    categoryToSubjectMap.put(key, new HashSet<String>());

                Set<String> participants = categoryToSubjectMap.get(key);

                switch (group.type)
                {
                    case participantGroup:
                        if (group.id == -1)
                            participants.addAll(getParticipantIdsNotInGroupCategory(group.categoryId));
                        else
                        {
                            ParticipantGroup pg = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), group.id);
                            if (pg != null)
                                participants.addAll(pg.getParticipantSet());
                        }
                        break;

                    case cohort:
                        if (group.id == -1)
                            participants.addAll(getParticipantIdsNotInCohorts());
                        else
                            participants.addAll(getParticipantIdsForCohort(group.id));
                        break;
                }
            }

            // we want to OR subjects within a category and AND them across categories
            // NOTE we start with the subjects==null so we can distinguish between the first time through the loop
            // when null==subjects vs. no subjects due to being filtered when true==subjects.isEmpty()
            Collection<String> subjects = null;
            Set<String> cohortParticipants = null;
            for (String key : categoryToSubjectMap.keySet())
            {
                if (key.equals("cohort"))
                {
                    cohortParticipants = categoryToSubjectMap.get(key);
                    continue;
                }

                Set<String> participants = categoryToSubjectMap.get(key);
                if (null == subjects)
                {
                    subjects = new ArrayList<>(participants);
                }
                else
                {
                    subjects = CollectionUtils.intersection(subjects, participants);
                    if (subjects.isEmpty())
                        break;
                }
            }

            // Issue 18697: since cohorts are allowed to be empty (i.e. have no ptids in it) and ptid groups are not, go through the ptids groups first
            if (cohortParticipants != null)
            {
                if (null == subjects)
                    subjects = new ArrayList<>(cohortParticipants);
                else
                    subjects = CollectionUtils.intersection(subjects, cohortParticipants);
            }

            List<String> sortedSubjects = new ArrayList<>();
            if (null != subjects)
                sortedSubjects.addAll(subjects);

            Collections.sort(sortedSubjects);

            resp.put("success", true);
            resp.put("subjects", sortedSubjects);

            return resp;
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class SaveParticipantGroup extends MutatingApiAction<ParticipantGroupSpecification>
    {
        ParticipantGroup _prevGroup;

        @Override
        public void validateForm(ParticipantGroupSpecification form, Errors errors)
        {
            form.setContainerId(getContainer().getId());
            if (!form.getParticipantCategorySpecification().isNew())
            {
                List<ParticipantGroup> participantGroups = ParticipantGroupManager.getInstance().getParticipantGroups(getContainer(), getUser(), form.getParticipantCategorySpecification());
                Set<String> formParticipants = new HashSet<>(Arrays.asList(form.getParticipantIds()));

                for (ParticipantGroup group : participantGroups)
                {
                    if (group.getRowId() != form.getRowId())
                    {
                        String[] participants = group.getParticipantIds();
                        for (String ptid : participants)
                        {
                            if (formParticipants.contains(ptid))
                            {
                               errors.reject(ERROR_MSG, "The group " + group.getLabel() + " already contains " + ptid + ". Participants can only be in one group within a category.");
                            }
                        }
                    }
                }
            }

            if (form.getRowId() != 0)
            {
                // updating an existing group
                _prevGroup = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), form.getRowId());
                if (_prevGroup == null)
                    errors.reject(ERROR_MSG, "The group " + form.getLabel() + " no longer exists in the system, update failed.");
            }
        }

        @Override
        public ApiResponse execute(ParticipantGroupSpecification form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            form.setContainer(getContainer().getId());

            ParticipantCategoryImpl category;
            ParticipantGroup group;

            if (!form.isNew())
                form.copySpecialFields(_prevGroup);

            DbScope scope = StudySchema.getInstance().getSchema().getScope();
            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                // this is a new category being created
                if (form.getCategoryId() == 0)
                {
                    if (form.getCategoryType().equals(ParticipantCategory.Type.list.name()))
                    {
                        // No category selected, create new category with type 'list'.
                        if (form.isNew())
                        {
                            category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form.getParticipantCategorySpecification(), form.getParticipantIds(), form.getFilters(), form.getDescription());
                            group = category.getGroups()[0];
                        }
                        else
                        {
                            category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form.getParticipantCategorySpecification());
                            form.setCategoryId(category.getRowId());
                            group = ParticipantGroupManager.getInstance().setParticipantGroup(getContainer(), getUser(), form);
                        }
                    }
                    else
                    {
                        // New category specified. Create category with type 'manual' and create new participant group.
                        Integer oldCategoryId = null;
                        if (!form.isNew())
                            oldCategoryId = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), form.getRowId()).getCategoryId();

                        category = ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), form.getParticipantCategorySpecification());
                        form.setCategoryId(category.getRowId());
                        group = ParticipantGroupManager.getInstance().setParticipantGroup(getContainer(), getUser(), form);

                        deleteImplicitCategory(oldCategoryId, category);
                    }
                }
                else
                {
                    Integer oldCategoryId = null;
                    if (!form.isNew())
                        oldCategoryId = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), form.getRowId()).getCategoryId();

                    group = ParticipantGroupManager.getInstance().setParticipantGroup(getContainer(), getUser(), form);
                    category = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), group.getCategoryId());

                    // if the category shared bit has changed, resave the category
                    if (form.getCategoryOwnerId() != category.getOwnerId())
                    {
                        category.setOwnerId(form.getCategoryOwnerId());
                        ParticipantGroupManager.getInstance().setParticipantCategory(getContainer(), getUser(), category);
                    }

                    deleteImplicitCategory(oldCategoryId, category);
                }
                transaction.commit();
            }
            resp.put("success", true);
            resp.put("group", group.toJSON());
            resp.put("category", category.toJSON());

            return resp;
        }

        /**
         * Code to check whether an implicitly created category needs to be deleted so we don't accumulate orpaned list type
         * categories.
         *
         * @param prevCategoryId
         * @param current
         * @throws ValidationException
         */
        private void deleteImplicitCategory(Integer prevCategoryId, ParticipantCategoryImpl current) throws ValidationException
        {
            if (prevCategoryId != null)
            {
                ParticipantCategoryImpl oldCategory = ParticipantGroupManager.getInstance().getParticipantCategory(getContainer(), getUser(), prevCategoryId);
                if (oldCategory.getType().equals("list") && !current.getType().equals("list"))
                {
                    ParticipantGroupManager.getInstance().deleteParticipantCategory(getContainer(), getUser(), oldCategory);
                }
            }
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class DeleteParticipantGroup extends MutatingApiAction<ParticipantGroup>
    {
        @Override
        public ApiResponse execute(ParticipantGroup form, BindException errors) throws Exception
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            ParticipantGroup group = ParticipantGroupManager.getInstance().getParticipantGroupFromGroupRowId(getContainer(), getUser(), form.getRowId());
            if (group == null)
                throw new NotFoundException("The specified group does not exist, it may have already been deleted: " + form.getRowId());

            ParticipantGroupManager.getInstance().deleteParticipantGroup(getContainer(), getUser(), group);
            return resp;
        }
    }

    private static class UpdateParticipantGroupForm
    {
        private int _rowId;
        private String _label;
        private String _description;
        private String _filters;
        private String[] _participantIds;
        private String[] _ensureParticipantIds;
        private String[] _deleteParticipantIds;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getFilters()
        {
            return _filters;
        }

        public void setFilters(String filters)
        {
            _filters = filters;
        }

        public String[] getParticipantIds()
        {
            return _participantIds;
        }

        public void setParticipantIds(String[] participantIds)
        {
            _participantIds = participantIds;
        }

        public String[] getEnsureParticipantIds()
        {
            return _ensureParticipantIds;
        }

        public void setEnsureParticipantIds(String[] ensureParticipantIds)
        {
            _ensureParticipantIds = ensureParticipantIds;
        }

        public String[] getDeleteParticipantIds()
        {
            return _deleteParticipantIds;
        }

        public void setDeleteParticipantIds(String[] deleteParticipantIds)
        {
            _deleteParticipantIds = deleteParticipantIds;
        }
    }

    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class UpdateParticipantGroupAction extends MutatingApiAction<UpdateParticipantGroupForm>
    {
        @Override
        public ApiResponse execute(UpdateParticipantGroupForm form, BindException errors) throws Exception
        {
            ParticipantGroup participantGroup = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), form.getRowId());
            if (participantGroup == null)
            {
                throw new NotFoundException("Could not find participant group with rowId " + form.getRowId());
            }
            Set<String> participantIds = new HashSet<>(Arrays.asList(form.getParticipantIds() == null ? participantGroup.getParticipantIds() : form.getParticipantIds()));
            if (form.getEnsureParticipantIds() != null)
            {
                participantIds.addAll(Arrays.asList(form.getEnsureParticipantIds()));
            }
            if (form.getDeleteParticipantIds() != null)
            {
                participantIds.removeAll(Arrays.asList(form.getDeleteParticipantIds()));
            }
            participantGroup.setParticipantSet(participantIds);

            if (form.getDescription() != null)
            {
                participantGroup.setDescription(form.getDescription());
            }
            if (form.getLabel() != null)
            {
                participantGroup.setLabel(form.getLabel());
            }
            if (form.getFilters() != null)
            {
                participantGroup.setFilters(form.getFilters());
            }

            ParticipantGroupManager.getInstance().setParticipantGroup(getContainer(), getUser(), participantGroup);


            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("group", participantGroup.toJSON());
            return resp;
        }
    }

    @RequiresPermission(DeletePermission.class)
    /**
     * A little confusing to have two actions to delete groups. This is a temporary measure to allow groups to be
     * deleted through the schema browser query views, until we can implement a UI to handle management of categories
     * and groups.
     */
    public class DeleteParticipantGroups extends FormHandlerAction<QueryForm>
    {
        private ActionURL _returnURL;

        @Override
        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            _returnURL = form.getReturnActionURL();

            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (String survey : DataRegionSelection.getSelected(getViewContext(), true))
                {
                    int rowId = NumberUtils.toInt(survey);

                    ParticipantGroup group = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), rowId);
                    if (group != null)
                        ParticipantGroupManager.getInstance().deleteParticipantGroup(getContainer(), getUser(), group);
                }
                transaction.commit();
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(QueryForm form)
        {
            return _returnURL;
        }
    }

    public static class ParticipantGroupSpecification extends ParticipantGroup
    {
        private String[] _participantIds = new String[0];
        private String _filters;
        private String _description;
        private int _categoryId;
        private String _categoryLabel;
        private String _categoryType;
        private int _categoryOwnerId = ParticipantCategory.OWNER_SHARED;

        public int getCategoryOwnerId()
        {
            return _categoryOwnerId;
        }

        public void setCategoryOwnerId(int categoryOwner)
        {
            _categoryOwnerId = categoryOwner;
        }

        public String getCategoryType()
        {
            return _categoryType;
        }

        public void setCategoryType(String categoryType)
        {
            _categoryType = categoryType;
        }

        public String getCategoryLabel()
        {
            return _categoryLabel;
        }

        public void setCategoryLabel(String categoryLabel)
        {
            _categoryLabel = categoryLabel;
        }

        public String[] getParticipantIds()
        {
            return _participantIds;
        }

        public void setParticipantIds(String[] participantIds)
        {
            _participantIds = participantIds;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getFilters()
        {
            return _filters;
        }

        public void setFilters(String filters)
        {
            _filters = filters;
        }

        public void setCategoryId(int id)
        {
            _categoryId = id;
        }

        public int getCategoryId(){
            return _categoryId;
        }

        public ParticipantCategorySpecification getParticipantCategorySpecification()
        {
            ParticipantCategorySpecification category = new ParticipantCategorySpecification();

            category.setRowId(getCategoryId());
            category.setParticipantIds(getParticipantIds());
            category.setFilters(getFilters());
            if (getCategoryLabel() == null)
            {
                category.setLabel(getLabel());
            }
            else
            {
                category.setLabel(getCategoryLabel());
            }
            category.setType(getCategoryType());
            category.setOwnerId(getCategoryOwnerId());
            category.setDescription(getDescription());
            category.setContainerId(getContainerId());

            return category;
        }
    }

    // CONSIDER: Merge with UpdateParticipantGroupAction
    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class SessionParticipantGroupAction extends ApiAction<UpdateParticipantGroupForm>
    {
        public SessionParticipantGroupAction()
        {
            super();
            setSupportedMethods(new String[] { "GET", "POST", "DELETE" });
        }

        @Override
        public Object execute(UpdateParticipantGroupForm form, BindException errors) throws Exception
        {
            HttpSession session = getViewContext().getSession();
            if (session == null)
                throw new IllegalStateException("Session required");

            ParticipantGroup group;
            if (isPost())
            {
                if (form.getRowId() > 0)
                {
                    ParticipantGroup existing = ParticipantGroupManager.getInstance().getParticipantGroup(getContainer(), getUser(), form.getRowId());
                    if (existing == null)
                        throw new NotFoundException("Could not find participant group with rowId " + form.getRowId());

                    ParticipantGroupManager.getInstance().setSessionParticipantGroup(getContainer(), getUser(), getViewContext().getRequest(), existing.getRowId());
                    return success(existing);
                }
                else
                {
                    group = ParticipantGroupManager.getInstance().getSessionParticipantGroup(getContainer(), getUser(), getViewContext().getRequest());
                    if (group == null)
                    {
                        group = new ParticipantGroup();
                        group.setSession(true);
                    }
                    else if (!group.isSession())
                    {
                        group = new ParticipantGroup();
                        group.setSession(true);
                        group.setCategoryId(group.getCategoryId());
                        group.setCategoryLabel(group.getCategoryLabel());
                        group.setDescription(group.getDescription());
                        group.setFilters(group.getFilters());
                        group.setLabel(group.getLabel());
                        group.setParticipantIds(group.getParticipantIds());
                        group.setParticipantSet(group.getParticipantSet());
                        group.setContainerId(group.getContainerId());
                        group.setCreated(group.getCreated());
                        group.setCreatedBy(group.getCreatedBy());
                        group.setModified(group.getModified());
                        group.setModifiedBy(group.getModifiedBy());
                        ParticipantGroupManager.getInstance().setSessionParticipantGroup(getContainer(), getUser(), getViewContext().getRequest(), group);
                    }

                    Set<String> participantIds = new HashSet<>(Arrays.asList(form.getParticipantIds() == null ? group.getParticipantIds() : form.getParticipantIds()));
                    if (form.getEnsureParticipantIds() != null)
                        participantIds.addAll(Arrays.asList(form.getEnsureParticipantIds()));
                    if (form.getDeleteParticipantIds() != null)
                        participantIds.removeAll(Arrays.asList(form.getDeleteParticipantIds()));

                    group.setParticipantSet(participantIds);

                    if (form.getDescription() != null)
                        group.setDescription(form.getDescription());

                    if (form.getLabel() != null)
                        group.setLabel(form.getLabel());

                    if (form.getFilters() != null)
                        group.setFilters(form.getFilters());

                    group = ParticipantGroupManager.getInstance().setSessionParticipantGroup(getContainer(), getUser(), getViewContext().getRequest(), group);
                    return success(group);
                }
            }
            else if (isDelete())
            {
                ParticipantGroupManager.getInstance().deleteSessionParticipantGroup(getContainer(), getUser(), getViewContext().getRequest());
                return success();
            }
            else
            {
                group = ParticipantGroupManager.getInstance().getSessionParticipantGroup(getContainer(), getUser(), getViewContext().getRequest());
                return success(group);
            }
        }
    }



    //// Study manager wrappers


    @Nullable
    ContainerFilter getDefaultContainerFilter()
    {
        if (!getStudy().isDataspaceStudy())
            return null;
        DataspaceQuerySchema dqs = new DataspaceQuerySchema(getStudy(), getUser(), true);
        return dqs.getDefaultContainerFilter();
    }


    List<CohortImpl> getCohorts()
    {
        return StudyManager.getInstance().getCohorts(getContainer(), getUser());
    }

    Collection<String> getParticipantIds()
    {
        return  Arrays.asList(StudyManager.getInstance().getParticipantIds(getStudy(), getUser(), getDefaultContainerFilter(), -1));
    }

    Collection<String> getParticipantIdsNotInGroupCategory(ParticipantCategoryImpl category)
    {
        return Arrays.asList(StudyManager.getInstance().getParticipantIdsNotInGroupCategory(getStudy(), getUser(), getDefaultContainerFilter(), category.getRowId()));
    }

    Collection<String> getParticipantIdsNotInGroupCategory(int categoryId)
    {
        return Arrays.asList(StudyManager.getInstance().getParticipantIdsNotInGroupCategory(getStudy(), getUser(),  getDefaultContainerFilter(), categoryId));
    }

    Collection<String> getParticipantIdsNotInCohorts()
    {
        if (getStudy().isDataspaceStudy())
            return Collections.emptyList();
        return Arrays.asList(StudyManager.getInstance().getParticipantIdsNotInCohorts(_study));
    }

    Collection<String> getParticipantIdsForCohort(int cohortid)
    {
        if (getStudy().isDataspaceStudy())
            return Collections.emptyList();
        return Arrays.asList(StudyManager.getInstance().getParticipantIdsForCohort(getStudy(), cohortid, -1));
    }
}
