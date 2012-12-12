/*
 * Copyright (c) 2012 LabKey Corporation
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

package org.labkey.survey;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.survey.query.SurveyQuerySchema;
import org.springframework.validation.BindException;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SurveyModule extends DefaultModule
{
    @Override
    public String getName()
    {
        return "Survey";
    }

    @Override
    public double getVersion()
    {
        return 12.30;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<WebPartFactory>(Arrays.asList(
                new SurveyDesignWebPartFactory(),
                new SurveysWebPartFactory()
        ));
    }

    @Override
    protected void init()
    {
        addController("survey", SurveyController.class);

        DefaultSchema.registerProvider("survey", new DefaultSchema.SchemaProvider()
        {
            public QuerySchema getSchema(DefaultSchema schema)
            {
                return new SurveyQuerySchema(schema.getUser(), schema.getContainer());
            }
        });
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new SurveyContainerListener());
    }

    @Override
    public Collection<String> getSummary(Container c)
    {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton("survey");
    }

    @Override
    @NotNull
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(SurveySchema.getInstance().getSchema());
    }

    private static class SurveyDesignWebPartFactory extends BaseWebPartFactory
    {
        public SurveyDesignWebPartFactory()
        {
            super("Survey Designs", WebPartFactory.LOCATION_BODY);
        }

        public WebPartView getWebPartView(ViewContext context, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            if (!context.hasPermission(AdminPermission.class))
                return new HtmlView("Survey Designs", "You do not have permission to see this data");

            VBox view = new VBox();
            view.setTitle("Survey Designs");
            view.setFrame(WebPartView.FrameType.PORTAL);

            BindException errors = new NullSafeBindException(this, "form");
            UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), SurveyQuerySchema.SCHEMA_NAME);
            QuerySettings settings = schema.getSettings(context, QueryView.DATAREGIONNAME_DEFAULT, SurveyQuerySchema.SURVEY_DESIGN_TABLE_NAME);
            settings.setReturnUrl(context.getActionURL().clone());

            QueryView queryView = schema.createView(context, settings, errors);
            view.addView(queryView);

            return view;
        }
    }

    private static class SurveysWebPartFactory extends BaseWebPartFactory
    {
        public SurveysWebPartFactory()
        {
            super("Surveys", WebPartFactory.LOCATION_BODY, true, true);
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
        {
            return new JspView<Portal.WebPart>("/org/labkey/survey/view/customizeSurveysWebPart.jsp", webPart);
        }

        public WebPartView getWebPartView(ViewContext context, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            if (!context.hasPermission(ReadPermission.class) || context.getUser().isGuest())
                return new HtmlView("Surveys", "You do not have permission to see this data");

            Map<String, String> props = webPart.getPropertyMap();

            String designIdStr = props.get("surveyDesignId");
            Integer designId;
            if (null == designIdStr)
                return new HtmlView("Surveys", "There is no survey design selected to be displayed in this webpart");
            else
                designId = Integer.parseInt(designIdStr);

            try
            {
                VBox view = new VBox();
                view.setTitle(getSurveysWebPartTitle(designId));
                view.setFrame(WebPartView.FrameType.PORTAL);

                BindException errors = new NullSafeBindException(this, "form");
                UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), SurveyQuerySchema.SCHEMA_NAME);
                QuerySettings settings = schema.getSettings(context, "surveys" + designId, SurveyQuerySchema.SURVEYS_TABLE_NAME);
                settings.setBaseFilter(new SimpleFilter(FieldKey.fromParts("SurveyDesignId"), designId));
                settings.setAllowChooseQuery(false);

                QueryView queryView = schema.createView(context, settings, errors);
                queryView.setShowImportDataButton(false);
                queryView.setShowRecordSelectors(true);
                view.addView(queryView);

                return view;
            }
            catch (NumberFormatException e)
            {
                return new HtmlView("Surveys", "Survey Design id is invalid");
            }
        }

        private String getSurveysWebPartTitle(Integer designId)
        {
            // get the survey design label for the given ID and use if for the webpart label
            String title = "Surveys";
            if (designId != null)
            {
                TableInfo ti = SurveySchema.getInstance().getSurveyDesignsTable();
                Collection<ColumnInfo> columns = new HashSet<ColumnInfo>();
                columns.add(ti.getColumn("Label"));
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), designId);
                TableSelector selector = new TableSelector(ti, columns, filter, null);
                String label = selector.getObject(String.class);
                title += ": " + label;
            }

            return title;
        }
    }
}