/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.study.dataset;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.views.DataViewInfo;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.data.views.DefaultViewInfo;
import org.labkey.api.data.views.ProviderType;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.settings.ResourceURL;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Apr 2, 2012
 */
public class DatasetViewProvider implements DataViewProvider
{
    public static final DataViewProvider.Type TYPE = new ProviderType("datasets", "Provides a view of Study Datasets", true);

    public DataViewProvider.Type getType()
    {
        return TYPE;
    }

    @Override
    public boolean isVisible(Container container, User user)
    {
        Study study = StudyService.get().getStudy(container);

        return study != null;
    }

    @Override
    public void initialize(ContainerUser context) throws Exception
    {
        Container c = context.getContainer();
        User user = context.getUser();

        ReportPropsManager.get().ensureProperty(c, user, "status", "Status", PropertyType.STRING);
        ReportPropsManager.get().ensureProperty(c, user, "author", "Author", PropertyType.INTEGER);
        ReportPropsManager.get().ensureProperty(c, user, "refreshDate", "RefreshDate", PropertyType.DATE_TIME);
    }


    @Override
    public List<DataViewInfo> getViews(ViewContext context) throws Exception
    {
        List<DataViewInfo> datasets = new ArrayList<>();
        Container container = context.getContainer();
        User user = context.getUser();

        if (isVisible(container, user))
        {
            Study study = StudyService.get().getStudy(container);

            if (null != study)
            {
                for (Dataset ds : study.getDatasets())
                {
                    if (ds.canRead(user))
                    {
                        DefaultViewInfo view = new DefaultViewInfo(TYPE, ds.getEntityId(), ds.getLabel(), ds.getContainer());

                        if (ds.getViewCategory() != null)
                        {
                            view.setCategory(ds.getViewCategory());
                        }
                        else
                        {
                            view.setCategory(ReportUtil.getDefaultCategory(container, null, null));
                        }
                        view.setType("Dataset");
                        view.setDescription(ds.getDescription());
                        view.setIconUrl(new ResourceURL("/reports/grid.gif"));

                        if(QueryService.get().isQuerySnapshot(container, "study", ds.getName()))
                            view.setIconCls("fa fa-camera");
                        else
                            view.setIconCls("fa fa-table");
                        view.setVisible(ds.isShowByDefault());

                        ActionURL runUrl = new ActionURL(StudyController.DefaultDatasetReportAction.class, container).addParameter("datasetId", ds.getDatasetId());
                        view.setRunUrl(runUrl);
                        view.setDetailsUrl(runUrl);

                        // Always return link to a static image for now. See ReportViewProvider for an example of a dynamic thumbnail provider.
                        view.setThumbnailUrl(new ResourceURL("/study/dataset.png"));
                        view.setModified(ds.getModified());

                        view.setTags(ReportPropsManager.get().getProperties(ds.getEntityId(), container));

                        view.setSchemaName("study");
                        view.setQueryName(ds.getName());

                        view.setDisplayOrder(ds.getDisplayOrder());  // NOTE: not used currently in UI, but populating in case we need it someday

                        datasets.add(view);
                    }
                }
            }
        }
        return datasets;
    }

    @Override
    public DataViewProvider.EditInfo getEditInfo()
    {
        return new EditInfoImpl();
    }

    public static class EditInfoImpl implements DataViewProvider.EditInfo
    {
        private static final String[] _editableProperties = {
                Property.viewName.name(),
                Property.description.name(),
                Property.category.name(),
                Property.visible.name(),
                Property.author.name(),
                Property.refreshDate.name(),
                Property.status.name(),
        };

        private static final Actions[] _actions = {
                Actions.update
        };

        @Override
        public String[] getEditableProperties(Container container, User user)
        {
            return _editableProperties;
        }

        @Override
        public void validateProperties(Container container, User user, String id, Map<String, Object> props) throws ValidationException
        {
            StudyImpl study = StudyManager.getInstance().getStudy(container);
            List<ValidationError> errors = new ArrayList<>();

            if (study != null)
            {
                DatasetDefinition dsDef = StudyManager.getInstance().getDatasetDefinitionByEntityId(study, id);
                if (dsDef == null)
                    errors.add(new SimpleValidationError("Unable to locate the dataset for the specified ID"));
            }
            else
                errors.add(new SimpleValidationError("No study defined for this folder"));

            if (!errors.isEmpty())
                throw new ValidationException(errors);
        }

        @Override
        public void updateProperties(ViewContext context, String id, Map<String, Object> props) throws Exception
        {
            DbScope scope = StudySchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
                if (study != null)
                {
                    DatasetDefinition dsDef = StudyManager.getInstance().getDatasetDefinitionByEntityId(study, id);
                    if (dsDef != null)
                    {
                        ViewCategory category = null;

                        // save the category information then the dataset information
                        if (props.containsKey(Property.category.name()))
                        {
                            int categoryId = NumberUtils.toInt(String.valueOf(props.get(Property.category.name())));
                            category = ViewCategoryManager.getInstance().getCategory(context.getContainer(), categoryId);
                        }

                        boolean dirty = false;
                        dsDef = dsDef.createMutable();
                        if (category != null)
                        {
                            dirty = dsDef.getCategoryId() == null || (category.getRowId() != dsDef.getCategoryId());
                            dsDef.setCategoryId(category.getRowId());
                        }
                        else
                        {
                            dirty = dsDef.getCategoryId() != null;
                            dsDef.setCategoryId(null);
                        }

                        if (props.containsKey(Property.viewName.name()))
                        {
                            String newLabel = StringUtils.trimToNull(String.valueOf(props.get(Property.viewName.name())));
                            dirty = dirty || !StringUtils.equals(dsDef.getLabel(), newLabel);
                            dsDef.setLabel(newLabel);
                        }

                        if (props.containsKey(Property.description.name()))
                        {
                            String newDescription = StringUtils.trimToNull(String.valueOf(props.get(Property.description.name())));
                            dirty = dirty || !StringUtils.equals(dsDef.getDescription(), newDescription);
                            dsDef.setDescription(newDescription);
                        }

                        if (props.containsKey(Property.visible.name()))
                        {
                            boolean visible = BooleanUtils.toBoolean(String.valueOf(props.get(Property.visible.name())));
                            dirty = dirty || (dsDef.isShowByDefault() != visible);
                            dsDef.setShowByDefault(visible);
                        }

                        if (dirty)
                            dsDef.save(context.getUser());

                        if (props.containsKey(Property.author.name()))
                            ReportPropsManager.get().setPropertyValue(id, context.getContainer(), Property.author.name(), props.get(Property.author.name()));
                        if (props.containsKey(Property.status.name()))
                            ReportPropsManager.get().setPropertyValue(id, context.getContainer(), Property.status.name(), props.get(Property.status.name()));
                        if (props.containsKey(Property.refreshDate.name()))
                            ReportPropsManager.get().setPropertyValue(id, context.getContainer(), Property.refreshDate.name(), props.get(Property.refreshDate.name()));
                    }
                }
                transaction.commit();
            }
        }

        @Override
        public Actions[] getAllowableActions(Container container, User user)
        {
            return _actions;
        }

        @Override
        public void deleteView(Container container, User user, String id) throws ValidationException
        {
            throw new UnsupportedOperationException();
        }
    }
}
