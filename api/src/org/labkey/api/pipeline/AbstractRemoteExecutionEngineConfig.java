package org.labkey.api.pipeline;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.pipeline.file.PathMapper;
import org.labkey.api.pipeline.file.PathMapperImpl;

import java.util.Collections;
import java.util.Set;

/**
 * Created by: jeckels
 * Date: 11/18/15
 */
public class AbstractRemoteExecutionEngineConfig implements PipelineJobService.RemoteExecutionEngineConfig
{
    @NotNull
    private final String _type;
    @NotNull
    private final String _location;
    @NotNull
    private final Set<String> _queues;
    @NotNull
    private final PathMapper _mapper;

    protected AbstractRemoteExecutionEngineConfig(@NotNull String type, @NotNull String location)
    {
        this(type, location, Collections.emptySet(), new PathMapperImpl());
    }

    protected AbstractRemoteExecutionEngineConfig(@NotNull String type, @NotNull String location, @NotNull Set<String> queues, @NotNull PathMapper mapper)
    {
        _type = type;
        _location = location;
        _queues = Collections.unmodifiableSet(queues);
        _mapper = mapper;
    }

    @NotNull
    @Override
    public String getLocation()
    {
        return _location;
    }

    @NotNull
    @Override
    public String getType()
    {
        return _type;
    }

    @NotNull
    @Override
    public PathMapper getPathMapper()
    {
        return _mapper;
    }

}
