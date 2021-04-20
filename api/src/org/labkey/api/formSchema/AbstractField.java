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

public abstract class AbstractField<T> implements Field
{
    private String _name;
    private String _label;
    private String _placeholder;
    private String _helpText;
    private String _helpTextHref;
    private Boolean _required;
    private T _defaultValue;

    public AbstractField(String name, String label, String placeholder, Boolean required, T defaultValue)
    {
        _name = name;
        _label = label;
        _placeholder = placeholder;
        _required = required;
        _defaultValue = defaultValue;
    }

    public AbstractField(String name, String label, String placeholder, Boolean required, T defaultValue, String helpText)
    {
        this(name, label, placeholder, required, defaultValue);
        _helpText = helpText;
    }

    public AbstractField(String name, String label, String placeholder, Boolean required, T defaultValue, String helpText, String helpTextHref)
    {
        this(name, label, placeholder, required, defaultValue, helpText);
        _helpTextHref = helpTextHref;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    @Override
    public String getHelpText()
    {
        return _helpText;
    }

    public void setHelpText(String helpText)
    {
        _helpText = helpText;
    }

    @Override
    public String getHelpTextHref()
    {
        return _helpTextHref;
    }

    public void setHelpTextHref(String helpTextHref)
    {
        _helpTextHref = helpTextHref;
    }

    @Override
    public Boolean getRequired()
    {
        return _required;
    }

    public void setRequired(Boolean required)
    {
        _required = required;
    }

    @Override
    public T getDefaultValue()
    {
        return _defaultValue;
    }

    public void setDefaultValue(T defaultValue)
    {
        this._defaultValue = defaultValue;
    }

    @Override
    public String getPlaceholder()
    {
        return _placeholder;
    }

    public void setPlaceholder(String placeholder)
    {
        this._placeholder = placeholder;
    }

    @Override
    public abstract String getType();
}
