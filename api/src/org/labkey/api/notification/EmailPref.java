/*
 * Copyright (c) 2010-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.notification;

/**
 * Represents a preference for email subscriptions, such as getting daily digests or individual messages.
 * User: klum
 * Date: Apr 23, 2010
 */
public abstract class EmailPref
{
    /**
     * Specifies the key name to store this preference under.
     */
    public abstract String getId();
    public abstract String getDefaultValue();

    /**
     * Returns a value based on either the specified value or a passed in default value. The preference is
     * asked to interpret whether a specified value is the same as a default value and if so return the
     * default value.
     */
    public abstract String getValue(String value, String defaultValue);
}
