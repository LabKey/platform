/*
 * Copyright (c) 2008 LabKey Corporation
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
 * User: jeckels
 * Date: Aug 8, 2008
 */
public class RecordedActionSet
{
    private final List<RecordedAction> _actions;
    private final Map<URI, String> _otherInputs;

    public RecordedActionSet()
    {
        this(new ArrayList<RecordedAction>());
    }

    public RecordedActionSet(List<RecordedAction> actions)
    {
        _actions = actions;
        _otherInputs = new LinkedHashMap<URI, String>();
    }

    public RecordedActionSet(RecordedActionSet actionSet)
    {
        this();
        add(actionSet);
    }

    public List<RecordedAction> getActions()
    {
        return _actions;
    }

    public Map<URI, String> getOtherInputs()
    {
        return _otherInputs;
    }

    public void add(List<RecordedAction> actions)
    {
        _actions.addAll(actions);
    }

    public void add(File inputFile, String inputRole)
    {
        _otherInputs.put(inputFile.toURI(), inputRole);
    }

    public void add(RecordedActionSet set)
    {
        add(set.getActions());
        _otherInputs.putAll(set.getOtherInputs());
    }
}