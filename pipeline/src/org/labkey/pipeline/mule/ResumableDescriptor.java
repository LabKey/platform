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
package org.labkey.pipeline.mule;

import org.mule.umo.UMODescriptor;

/**
 * User: jeckels
 * Date: Aug 19, 2008
 */
public interface ResumableDescriptor
{
    /**
     * Resume all of the jobs from this descriptor. They may already be running, or might
     * have been previously attempted. Implementations are responsible for not double-running
     * the jobs.
     * @param descriptor Mule configuration for this descriptor
     */
    public void resume(UMODescriptor descriptor);
}