/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.exp.api;

/**
 * Base interface for the different types of objects that can be the input to an {@link ExpRun}.
 * User: jeckels
 * Date: Oct 31, 2008
 */
public interface ExpRunInput extends ExpObject
{
    ExpProtocolApplication getTargetApplication();
    String getRole();
}
