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

public interface Field<T>
{
    /**
     * The name of the field to be rendered in the client.
     * @return the name of the field
     */
    public String getName();

    /**
     * The label rendered by the client.
     * @return The label for the field
     */
    public String getLabel();

    /**
     * The placeholder text used when a field has not been filled out yet.
     * @return placeholder text
     */
    public String getPlaceholder();

    /**
     * The text used to explain what the field is used for, or what the expected value should be.
     * @return text used for help tooltips
     */
    public String getHelpText();

    /**
     * The href used to link to documentation, rendered with helpText.
     * @return href pointing to documentation
     */
    public String getHelpTextHref();

    /**
     * Whether or not a field is required. Required fields cannot be empty or null.
     * @return required status of the field
     */
    public Boolean getRequired();

    /**
     * The default value to use if the user enters nothing in the field.
     * @return the default value of the field
     */
    public T getDefaultValue();

    /**
     * The type of field, this is used by the client to determine what type of input to render. While this is typically
     * a unique value, you could create a custom type that re-uses an input type, for example you could create a
     * YesNoMaybe field that returns "select" for its type, and always has the Options Yes, No, and Maybe.
     * @return the type of input the client should render
     */
    public String getType();
}
