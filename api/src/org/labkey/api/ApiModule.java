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
package org.labkey.api;

import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.LookAndFeelResourceType;
import org.labkey.api.attachments.SecureDocumentType;
import org.labkey.api.data.SqlScanner;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.exp.api.ExpRunAttachmentType;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.reports.report.ReportType;
import org.labkey.api.security.ApiKeyManager;
import org.labkey.api.security.ApiKeyManager.ApiKeyMaintenanceTask;
import org.labkey.api.security.AuthenticationLogoType;
import org.labkey.api.security.AvatarType;
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
        // TODO: why is this here instead of in ExperimentModule?
        if (ModuleLoader.getInstance().hasModule("Experiment"))
            AttachmentService.get().registerAttachmentType(ExpRunAttachmentType.get());
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
    }

    @Override
    public @NotNull Set<Class> getUnitTests()
    {
        return ImmutableSet.of(
            Constants.TestCase.class,
            DataIteratorUtil.TestCase.class,
            SqlScanner.TestCase.class
        );
    }

    @Override
    public @NotNull Set<Class> getIntegrationTests()
    {
        return ImmutableSet.of(
            ApiKeyManager.TestCase.class
        );
    }
}
