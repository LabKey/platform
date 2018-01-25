/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.UrlProvider;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.data.Container;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import java.io.File;

/**
 * <code>PipelineUrls</code> a UrlProvider for the pipeline UI.
 */
public interface PipelineUrls extends UrlProvider
{
    ActionURL urlBrowse(Container container);
    ActionURL urlBrowse(Container container, @Nullable URLHelper returnUrl);
    ActionURL urlBrowse(Container container, @Nullable URLHelper returnUrl, @Nullable String path);

    ActionURL urlSetup(Container container);

    ActionURL urlBegin(Container container);

    ActionURL urlActions(Container container);

    ActionURL urlStartFolderImport(Container container, @NotNull File archiveFile, boolean asStudy, @Nullable ImportOptions options, boolean fromTemplateSourceFolder);

    ActionURL urlCreatePipelineTrigger(Container container, String pipelineId, @Nullable ActionURL returnUrl);
}
