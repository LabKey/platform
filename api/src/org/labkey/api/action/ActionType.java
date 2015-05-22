/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.api.action;

/**
 * User: adam
 * Date: 2/12/14
 * Time: 5:47 PM
 */
public interface ActionType
{
    // Standard ActionType classes. Modules are free to use these or define and respect custom ActionType classes.
    class Configure implements ActionType {}
    class Export implements ActionType {}
    class SelectData implements ActionType {}
    class SelectMetaData implements ActionType {}
}
