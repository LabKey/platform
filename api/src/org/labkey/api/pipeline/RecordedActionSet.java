/*
 * Copyright (c) 2008-2016 LabKey Corporation
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

import java.util.*;
import java.io.File;
import java.net.URI;

/**
 * A collection of all the recorded actions performed in the context of a single pipeline job.
 * User: jeckels
 * Date: Aug 8, 2008
 */
public class RecordedActionSet
{
    private final Set<RecordedAction> _actions;
    private final Map<URI, String> _otherInputs;

    // No-args constructor to support de-serialization in Java 7
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

    public void add(File inputFile, String inputRole)
    {
        _otherInputs.put(inputFile.toURI(), inputRole);
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