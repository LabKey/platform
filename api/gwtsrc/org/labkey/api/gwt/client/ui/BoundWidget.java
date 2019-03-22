/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.api.gwt.client.ui;

/**
 * User: matthewb
 * Date: May 5, 2010
 * Time: 9:13:07 AM
 *
 * This is a marker interface for data bound widgets.
 * onBlur() can be simulated by
 *
 *  validate();
 *  pushValue();
 *
 * but without firing change and blur event
 *
 * NOTE: this was created because of some poor behavior with ext-gwt and selenium.
 * this is a way to make sure the bound controls push their changed data.
 */
public interface BoundWidget
{
    boolean validate(); // see (gxt) Field.validate()
    void pushValue();
    void pullValue();
}
