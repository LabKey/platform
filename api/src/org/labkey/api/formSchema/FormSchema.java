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

import java.util.List;

/**
 * Class used to instruct the client how to render a form. LabKey provides a React component that can render forms
 * given a FormSchema, but any client can use a FormSchema to render a form.
 */
public class FormSchema
{
    private final List<Field<?>> _fields; // The fields to render on the client

    public FormSchema (List<Field<?>> fields)
    {
        _fields = fields;
    }

    public List<Field<?>> getFields()
    {
        return _fields;
    }
}
