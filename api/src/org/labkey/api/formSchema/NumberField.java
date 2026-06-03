/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
 * Used to render a number input in the client.
 */
public class NumberField extends AbstractField<Double>
{
    public static final String TYPE = "number";

    public NumberField(String name, String label, String placeholder, Boolean required, Double defaultValue)
    {
        super(name, label, placeholder, required, defaultValue);
    }

    public NumberField(String name, String label, String placeholder, Boolean required, Double defaultValue, String helpText)
    {
        super(name, label, placeholder, required, defaultValue, helpText);
    }

    public NumberField(String name, String label, String placeholder, Boolean required, Double defaultValue, String helpText, String helpTextHref)
    {
        super(name, label, placeholder, required, defaultValue, helpText, helpTextHref);
    }

    @Override
    public String getType()
    {
        return TYPE;
    }
}
