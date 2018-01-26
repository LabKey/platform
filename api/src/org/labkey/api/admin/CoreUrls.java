/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.admin;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.view.ActionURL;

/**
 * User: adam
 * Date: Aug 12, 2008
 * Time: 6:35:01 AM
 */
public interface CoreUrls extends UrlProvider
{
    ActionURL getCustomStylesheetURL();
    ActionURL getCustomStylesheetURL(Container c);

    // TODO: Delete? Unused...
    ActionURL getAttachmentIconURL(Container c, String filename);

    ActionURL getProjectsURL(Container c);

    /** Still needs objectURI parameter and value tacked on */
    ActionURL getDownloadFileLinkBaseURL(Container container, PropertyDescriptor pd);

    @NotNull String getFeedbackURL();

    ActionURL getPermissionsURL(@NotNull Container c);
}
