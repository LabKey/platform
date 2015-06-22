/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 2:44 PM
 */
abstract public class AbstractAlignmentStepProvider<StepType extends AlignmentStep> extends AbstractPipelineStepProvider<StepType>
{
    public static String SUPPORT_MERGED_UNALIGNED = "supportsMergeUnaligned";

    private boolean _supportsPairedEnd;
    private boolean _supportsMergeUnaligned;

    public AbstractAlignmentStepProvider(String name, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths, @Nullable String websiteURL, boolean supportsPairedEnd, boolean supportsMergeUnaligned)
    {
        super(name, name, name, description, getParamList(parameters, supportsMergeUnaligned), clientDependencyPaths, websiteURL);

        _supportsPairedEnd = supportsPairedEnd;
        _supportsMergeUnaligned = supportsMergeUnaligned;
    }

    private static List<ToolParameterDescriptor> getParamList(List<ToolParameterDescriptor> list, boolean supportsMergeUnaligned)
    {
        List<ToolParameterDescriptor> parameters = new ArrayList<>();
        if (list != null)
        {
            parameters.addAll(list);
        }

        if (supportsMergeUnaligned)
        {
            parameters.add(ToolParameterDescriptor.create(SUPPORT_MERGED_UNALIGNED, "Merge Unaligned Reads", "If checked, the pipeline will attempt to merge unaligned reads into the final BAM file.  This is generally a good idea since it ensures information is not lost; however, in some situations you may know upfront that you do not need these reads.", "checkbox", new JSONObject(){{
                put("checked", true);
            }}, true));
        }
        else
        {
            parameters.add(ToolParameterDescriptor.create(SUPPORT_MERGED_UNALIGNED, "Merge Unaligned Reads", "If checked, the pipeline will attempt to merge unaligned reads into the final BAM file.  This is generally a good idea since it ensures information is not lost; however, in some situations you may know upfront that you do not need these reads.", "hidden", null, false));
        }

        return parameters;
    }

    public boolean supportsPairedEnd()
    {
        return _supportsPairedEnd;
    }

    public boolean isSupportsMergeUnaligned()
    {
        return _supportsMergeUnaligned;
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = super.toJSON();
        json.put("supportsPairedEnd", supportsPairedEnd());
        json.put("supportsMergeUnaligned", isSupportsMergeUnaligned());

        return json;
    }
}
