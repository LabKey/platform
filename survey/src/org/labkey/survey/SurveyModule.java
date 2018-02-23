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

package org.labkey.survey;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
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
import org.labkey.api.survey.SurveyService;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.survey.model.SurveyServiceImpl;
import org.labkey.survey.query.SurveyQuerySchema;
import org.labkey.survey.query.SurveyQuerySettings;
import org.labkey.survey.query.SurveyQueryView;
import org.labkey.survey.query.SurveyTableDomainKind;
import org.springframework.validation.BindException;

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
        return 18.10;
    }

    @Override
    public boolean hasScripts()
    {
        return true;
    }

    @NotNull
    @Override
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Arrays.asList(
                new SurveyDesignWebPartFactory(),
                new SurveysWebPartFactory()
        ));
    }

    @Override
    protected void init()
    {
        addController("survey", SurveyController.class);

        SurveyService.setInstance(new SurveyServiceImpl());

        DefaultSchema.registerProvider("survey", new DefaultSchema.SchemaProvider(this)
        {
            public QuerySchema createSchema(DefaultSchema schema, Module module)
            {
                return new SurveyQuerySchema(schema.getUser(), schema.getContainer());
            }
        });

        PropertyService.get().registerDomainKind(new SurveyTableDomainKind());
    }

    @Override
    public void doStartup(ModuleContext moduleContext)
    {
        // add a container listener so we'll know when our container is deleted:
        ContainerManager.addContainerListener(new SurveyContainerListener());
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        Collection<String> results = new ArrayList<>();

        SurveyDesign[] surveyDesigns = SurveyManager.get().getSurveyDesigns(c, ContainerFilter.CURRENT);
        if(surveyDesigns.length > 0)
        {
            results.add(StringUtilsLabKey.pluralize(surveyDesigns.length, " survey design"));
        }

        Survey[] surveys = SurveyManager.get().getSurveys(c);
        if(surveys.length > 0)
        {
            results.add(StringUtilsLabKey.pluralize(surveys.length, " survey"));
        }

        return results;
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return Collections.singleton(SurveySchema.DB_SCHEMA_NAME);
    }

    private static class SurveyDesignWebPartFactory extends BaseWebPartFactory
    {
        public SurveyDesignWebPartFactory()
        {
            super("Survey Designs", WebPartFactory.LOCATION_BODY);
        }

        public WebPartView getWebPartView(@NotNull ViewContext context, @NotNull Portal.WebPart webPart)
        {
            if (!context.hasPermission(AdminPermission.class))
                return new HtmlView("Survey Designs", "You do not have permission to see this data");

            VBox view = new VBox();
            view.setTitle("Survey Designs");
            view.setFrame(WebPartView.FrameType.PORTAL);

            BindException errors = new NullSafeBindException(this, "form");
            UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), SurveyQuerySchema.SCHEMA_NAME);
            if (schema == null)
                return new HtmlView("Survey Designs", "The 'survey' schema is not enabled for this folder.");

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
            super("Surveys", true, true, WebPartFactory.LOCATION_BODY);
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
        {
            return new JspView<>("/org/labkey/survey/view/customizeSurveysWebPart.jsp", webPart);
        }

        public WebPartView getWebPartView(@NotNull ViewContext context, @NotNull Portal.WebPart webPart)
        {
            if (!context.hasPermission(ReadPermission.class) || context.getUser().isGuest())
                return new HtmlView("Surveys", "You do not have permission to see this data");

            Map<String, String> props = webPart.getPropertyMap();

            String designIdStr = props.get("surveyDesignId");
            SurveyDesign surveyDesign;
            if (null == designIdStr)
                return new HtmlView("Surveys", "There is no survey design selected to be displayed in this webpart.");
            else
            {
                surveyDesign = SurveyManager.get().getSurveyDesign(context.getContainer(), context.getUser(), Integer.parseInt(designIdStr));

                if (surveyDesign == null)
                    return new HtmlView("Surveys", "The survey design configured for this webpart cannot be found and may have been deleted.");
            }

            try
            {
                VBox view = new VBox();
                view.setTitle("Surveys: " + surveyDesign.getLabel());
                view.setFrame(WebPartView.FrameType.PORTAL);

                BindException errors = new NullSafeBindException(this, "form");
                UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), SurveyQuerySchema.SCHEMA_NAME);
                SurveyQuerySettings settings = (SurveyQuerySettings)schema.getSettings(context, SurveyQueryView.DATA_REGION + webPart.getIndex(), SurveyQuerySchema.SURVEYS_TABLE_NAME);
                settings.setSurveyDesignId(surveyDesign.getRowId());
                settings.setReturnUrl(context.getActionURL().clone());

                // set base filter to the given survey design id
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("SurveyDesignId"), surveyDesign.getRowId());
                //filter.addCondition(FieldKey.fromParts("Submitted"), null, CompareType.ISBLANK);
                settings.setBaseFilter(filter);

                QueryView queryView = schema.createView(context, settings, errors);
                view.addView(queryView);

                return view;
            }
            catch (NumberFormatException e)
            {
                return new HtmlView("Surveys", "Survey Design id is invalid");
            }
        }
    }

    @NotNull
    @Override
    public Set<Class> getUnitTests()
    {
        Set<Class> set = new HashSet<>();
        set.add(SurveyManager.TestCase.class);
        return set;
    }
}
