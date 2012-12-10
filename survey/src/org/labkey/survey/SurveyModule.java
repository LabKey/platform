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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
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
                new SurveyDesignWebPartFactory()));
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
            settings.setAllowChooseQuery(false);

            QueryView queryView = schema.createView(context, settings, errors);
            queryView.setShowDeleteButton(true);
            queryView.setShowInsertNewButton(true);
            queryView.setShowUpdateColumn(true);
            queryView.setShowExportButtons(false);
            queryView.setShowReports(false);
            queryView.setShowRecordSelectors(true);

            view.addView(queryView);

            return view;
        }
    }
}