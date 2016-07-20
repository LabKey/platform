/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.pipeline.validators;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.sitevalidation.SiteValidationProvider;
import org.labkey.api.admin.sitevalidation.SiteValidationResult;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.List;

/**
 * User: tgaluhn
 * Date: 10/26/2015
 *
 * Validates pipeline root for container.
 */
public class PipelineSetupValidator implements SiteValidationProvider
{
    @Override
    public String getName()
    {
        return "Pipeline Validator";
    }

    @Override
    public String getDescription()
    {
        return "Validate pipeline roots";
    }

    @Override
    public boolean isSiteScope()
    {
        return false;
    }

    @Nullable
    @Override
    public SiteValidationResultList runValidation(Container c, User u)
    {
        PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(c);
        if (null != pipeRoot)
        {
            List<String> errors = pipeRoot.validate();
            if (!errors.isEmpty())
            {
                PipelineUrls pipelineUrls = PageFlowUtil.urlProvider(PipelineUrls.class);
                SiteValidationResultList results = new SiteValidationResultList();
                for (String error : errors)
                {

                    if (null != pipelineUrls)
                    {
                        SiteValidationResult result = results.addError(error, pipelineUrls.urlSetup(c));
                        result.append(" Click link to go to pipeline setup for this folder.");
                    }
                    else
                    {
                        results.addError(error);
                    }
                }
                return results;
            }
        }
        return null;
    }
}
