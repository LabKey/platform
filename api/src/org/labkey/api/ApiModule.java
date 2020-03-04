/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.api;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.LookAndFeelResourceType;
import org.labkey.api.attachments.SecureDocumentType;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.SqlScanner;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.jsp.LabKeyJspFactory;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.reports.report.ReportType;
import org.labkey.api.security.ApiKeyManager;
import org.labkey.api.security.ApiKeyManager.ApiKeyMaintenanceTask;
import org.labkey.api.security.AuthenticationLogoType;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AvatarType;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.view.WebPartFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * {@link org.labkey.api.module.Module} implementation for the API module itself, registering some of the basic
 * resource types within LabKey Server.
 *
 * Created by susanh on 1/19/17.
 */
public class ApiModule extends CodeOnlyModule
{
    @Override
    protected void init()
    {
        AttachmentService.get().registerAttachmentType(ReportType.get());
        AttachmentService.get().registerAttachmentType(LookAndFeelResourceType.get());
        AttachmentService.get().registerAttachmentType(AuthenticationLogoType.get());
        AttachmentService.get().registerAttachmentType(AvatarType.get());
        AttachmentService.get().registerAttachmentType(SecureDocumentType.get());

        if (AppProps.getInstance().isDevMode())
        {
            // Avoid doing this on pipeline remote servers to avoid the need for a dependency on the JSP API JAR.
            // See issue 39242
            LabKeyJspFactory.register();
        }
    }

    @NotNull
    @Override
    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void doStartup(ModuleContext moduleContext)
    {
        SystemMaintenance.addTask(new ApiKeyMaintenanceTask());
        AuthenticationManager.registerMetricsProvider();
    }

    @Override
    public @NotNull Set<Class> getUnitTests()
    {
        return ImmutableSet.of(
            Constants.TestCase.class,
            DataIteratorUtil.TestCase.class,
            SqlScanner.TestCase.class,
            FieldKey.TestCase.class,
            SchemaKey.TestCase.class,
            SessionHelper.TestCase.class,
            Pair.TestCase.class
        );
    }

    @Override
    public @NotNull Set<Class> getIntegrationTests()
    {
        return ImmutableSet.of(
            ApiKeyManager.TestCase.class,
            WorkbookContainerType.TestCase.class,
            ExcelColumn.TestCase.class
        );
    }
}
