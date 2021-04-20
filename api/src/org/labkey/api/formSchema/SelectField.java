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

public class SelectField<T> extends AbstractField<T>
{
    public static final String TYPE = "select";

    private List<Option<T>> _options;

    public SelectField(String name, String label, String placeholder, Boolean required, T defaultValue, List<Option<T>> options)
    {
        super(name, label, placeholder, required, defaultValue);
        _options = options;
    }

    public SelectField(String name, String label, String placeholder, Boolean required, T defaultValue, List<Option<T>> options, String helpText)
    {
        super(name, label, placeholder, required, defaultValue, helpText);
        _options = options;
    }

    public SelectField(String name, String label, String placeholder, Boolean required, T defaultValue, List<Option<T>> options, String helpText, String helpTextHref)
    {
        super(name, label, placeholder, required, defaultValue, helpText, helpTextHref);
        _options = options;
    }

    public List<Option<T>> getOptions()
    {
        return _options;
    }

    public void setOptions(List<Option<T>> options)
    {
        _options = options;
    }

    @Override
    public String getType()
    {
        return TYPE;
    }
}
