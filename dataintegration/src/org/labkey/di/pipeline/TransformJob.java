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
package org.labkey.di.pipeline;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class TransformJob extends PipelineJob
{
    public TransformJob(@Nullable String provider, ViewBackgroundInfo info, PipeRoot root)
    {
        super(provider, info, root);
    }

    @Override
    public URLHelper getStatusHref()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getDescription()
    {
        throw new UnsupportedOperationException();
    }
}
