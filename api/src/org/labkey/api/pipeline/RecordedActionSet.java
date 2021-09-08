/*
 * Copyright (c) 2008-2018 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A collection of all the recorded actions performed in the context of a single pipeline job.
 * User: jeckels
 * Date: Aug 8, 2008
 */
public class RecordedActionSet
{
    private final Set<RecordedAction> _actions;
    @JsonSerialize(keyUsing = StringKeySerialization.Serializer.class)
    @JsonDeserialize(keyUsing = StringKeySerialization.URIDeserializer.class)
    private final Map<URI, String> _otherInputs;

    @JsonCreator
    private RecordedActionSet(@JsonProperty("_actions") Set<RecordedAction> actions)
    {
        _actions = actions;
        _otherInputs = new LinkedHashMap<>();
    }

    public RecordedActionSet()
    {
        this(Collections.emptyList());
    }

    public RecordedActionSet(RecordedAction... actions)
    {
        this(Arrays.asList(actions));
    }

    public RecordedActionSet(Iterable<RecordedAction> actions)
    {
        _actions = new LinkedHashSet<>();
        for (RecordedAction action : actions)
        {
            _actions.add(action);
        }
        _otherInputs = new LinkedHashMap<>();
    }

    public RecordedActionSet(RecordedActionSet actionSet)
    {
        this();
        add(actionSet);
    }

    public Set<RecordedAction> getActions()
    {
        return _actions;
    }

    public Map<URI, String> getOtherInputs()
    {
        return _otherInputs;
    }

    public void add(Path inputFile, String inputRole)
    {
        _otherInputs.put(inputFile.toUri(), inputRole);
    }

    public void add(RecordedActionSet set)
    {
        _actions.addAll(set.getActions());
        _otherInputs.putAll(set.getOtherInputs());
    }

    public void add(RecordedAction action)
    {
        _actions.add(action);
    }
}
