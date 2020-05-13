/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.reports.report;

import org.labkey.api.module.Module;
import org.labkey.api.moduleeditor.api.ModuleEditorService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.EditModuleResourcesPermission;
import org.labkey.api.util.Path;

import java.util.List;

/**
 * User: klum
 * Date: 9/6/13
 */
public interface ModuleReportDescriptor
{
    /**
     * Returns the module that this report is contained in
     */
    Module getModule();

    /**
     * Get the path to the report resource
     */
    Path getReportPath();
    Resource getSourceFile();
    Resource getMetaDataFile();
    String getReportName();
    ReportIdentifier getReportId();

    default boolean canEdit(User user, List<ValidationError> errors)
    {
        if (null == ModuleEditorService.get().getFileForModuleResource(getModule(), getSourceFile().getPath()))
            errors.add(new SimpleValidationError("The source for this module report is not editable."));
        else if (!user.hasRootPermission(EditModuleResourcesPermission .class))
            // TODO add role name here instead of permission
            errors.add(new SimpleValidationError("You must have EditModuleResourcesPermission to edit module resources."));
        return errors.isEmpty();
    }
}
