/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.study.model;

import org.labkey.api.study.Dataset;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: adam
 * Date: 2/1/13
 * Time: 10:51 PM
 */
public class DatasetManager
{
    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    private static final List<DatasetListener> _listeners = new CopyOnWriteArrayList<>();

    public static void addDatasetListener(DatasetListener listener)
    {
        _listeners.add(listener);
    }

    public static List<DatasetListener> getListeners()
    {
        return _listeners;
    }

    public interface DatasetListener
    {
        void datasetChanged(Dataset def);
    }
}
