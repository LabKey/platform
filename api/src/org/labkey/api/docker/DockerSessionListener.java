/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.docker;

import org.jetbrains.annotations.NotNull;

import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;

// Moved from git, see history at https://github.com/LabKey/docker/commits/release18.1/api-src/org/labkey/api/docker/DockerSessionListener.java

/**
 * User: tgaluhn
 * Date: 6/13/2016
 */
public class DockerSessionListener implements HttpSessionBindingListener
{
    private final String _containerId;

    public DockerSessionListener(@NotNull String containerId)
    {
        _containerId = containerId;
    }

    @Override
    public void valueBound(HttpSessionBindingEvent httpSessionBindingEvent)
    {
        // Nothing to do
    }

    @Override
    public void valueUnbound(HttpSessionBindingEvent httpSessionBindingEvent)
    {
        preStop();

        try
        {
            DockerService.get().stop(_containerId);
        }
        catch (Exception ex)
        {
            // oh well
        }
    }

    protected void preStop() {}
}
