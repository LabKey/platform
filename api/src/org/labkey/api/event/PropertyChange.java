/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.event;

/**
 * Represents a property change similar to java.beans.PropertyChangeEvent but uses an Enum for the property type.
 *
 * User: kevink
 * Date: 4/17/13
 */
public interface PropertyChange<P extends Enum<P>, V>
{
    /**
     * @return the Property that has changed or null if more than one property has changed.
     */
    P getProperty();

    /**
     * @return the previous version of the value or null if more than one property has changed.
     */
    V getOldValue();

    /**
     * @return the new version of the value or null if more than one property has changed.
     */
    V getNewValue();
}
