/*
 * Copyright (c) 2021 LabKey Corporation
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

package org.labkey.api.formSchema;

/**
 * Used for SelectField and RadioField classes. Options are used to define the values that users see in a form. The
 * value attribute is what will be sent to the server, the label attribute is what is rendered for the user.
 * @param <T>
 */
public class Option<T>
{
    private final T _value;
    private final String _label;

    public Option(T value, String label)
    {
        _value = value;
        _label = label;
    }

    public T getValue()
    {
        return _value;
    }

    public String getLabel()
    {
        return _label;
    }
}
