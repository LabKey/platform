/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.cloud;

import org.labkey.api.data.Container;

import java.util.Date;
import java.util.Map;

/**
 * This interface is the minimal set of properties to setup a cloud watcher
 * NOTE: CustomConfig map should include properties for SQSUrl (target for receiving event Messages) and CloudAccount (LKS name for Cloud credentials to use)
 */
public interface CloudWatcherConfig
{
    String getLocation();
    Map<String, Object> getCustomConfig();
    boolean isEnabled();
    Date getLastChecked();
    Container lookupContainer();
    int getRowId();
    String getFilePattern();
    int getDelay();

    String getName();
}
