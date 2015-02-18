package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * User: bimber
 * Date: 6/15/2014
 * Time: 12:39 PM
 */
abstract public class AbstractVariantCallingStepProvider<StepType extends VariantCallingStep> extends AbstractPipelineStepProvider<StepType>
{
    public AbstractVariantCallingStepProvider(String name, String label, String toolName, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths, @Nullable String websiteURL)
    {
        super(name, label, toolName, description, parameters, clientDependencyPaths, websiteURL);
    }
}
