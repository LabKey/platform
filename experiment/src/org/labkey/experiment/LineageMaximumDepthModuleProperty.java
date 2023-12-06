package org.labkey.experiment;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleProperty;
import org.labkey.api.security.User;

public class LineageMaximumDepthModuleProperty extends ModuleProperty
{
    private final int MINIMUM_DEPTH = 1;
    private final int MAXIMUM_DEPTH = 1_000;

    public LineageMaximumDepthModuleProperty(Module module)
    {
        super(module, ExperimentService.LINEAGE_DEFAULT_MAXIMUM_DEPTH_PROPERTY_NAME);

        setLabel("Lineage Default Maximum Depth");
        setDescription(String.format("Default maximum depth lineage queries will recurse (%d-%d). Defaults to %d.", MINIMUM_DEPTH, MAXIMUM_DEPTH, ExpLineageOptions.LINEAGE_DEFAULT_MAXIMUM_DEPTH));
        setShowDescriptionInline(true);
    }

    @Override
    public void validate(@Nullable User user, Container c, @Nullable String value)
    {
        if (StringUtils.isEmpty(value))
            return;

        try
        {
            var depth = Integer.parseInt(value);
            if (depth < MINIMUM_DEPTH)
                throw new IllegalArgumentException(String.format("Invalid value for \"%s\". Minimum value is %d.", getLabel(), MINIMUM_DEPTH));
            else if (depth > MAXIMUM_DEPTH)
                throw new IllegalArgumentException(String.format("Invalid value for \"%s\". Maximum value is %d.", getLabel(), MAXIMUM_DEPTH));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(String.format("Invalid value for \"%s\". Must be a number.", getLabel()));
        }
    }
}
