package org.labkey.api.sequenceanalysis.pipeline;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.Collection;
import java.util.List;

/**
 * User: bimber
 * Date: 6/14/2014
 * Time: 2:44 PM
 */
abstract public class AbstractAlignmentStepProvider<StepType extends AlignmentStep> extends AbstractPipelineStepProvider<StepType>
{
    private boolean _supportsPairedEnd;

    public AbstractAlignmentStepProvider(String name, String description, @Nullable List<ToolParameterDescriptor> parameters, @Nullable Collection<String> clientDependencyPaths, @Nullable String websiteURL, boolean supportsPairedEnd)
    {
        super(name, name, name, description, parameters, clientDependencyPaths, websiteURL);

        _supportsPairedEnd = supportsPairedEnd;
    }

    public boolean supportsPairedEnd()
    {
        return _supportsPairedEnd;
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = super.toJSON();
        json.put("supportsPairedEnd", supportsPairedEnd());

        return json;
    }
}
