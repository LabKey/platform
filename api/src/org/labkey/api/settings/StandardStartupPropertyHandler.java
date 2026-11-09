/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.api.settings;

import java.util.Arrays;

public abstract class StandardStartupPropertyHandler<T extends Enum<T> & StartupProperty> extends MapBasedStartupPropertyHandler<T>
{
    /**
     * @param scope The scope name
     * @param type An enum that defines possible properties in this scope. The enum constants are used to validate
     *             property entries and to document available properties. The order of constant definitions determines
     *             the order they'll be displayed in the "Available" section on the "Startup Properties" admin console
     *             page.
     */
    protected StandardStartupPropertyHandler(String scope, Class<T> type)
    {
        super(scope, type.getName(), Arrays.stream(type.getEnumConstants()));
    }
}
