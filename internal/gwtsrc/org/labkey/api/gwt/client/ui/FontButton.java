/*
 * Copyright (c) 2015-2017 LabKey Corporation
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

import com.google.gwt.user.client.ui.HTML;

/**
 * Created by klum on 9/8/2015.
 */
public class FontButton extends HTML
{
    public FontButton(String fontClass)
    {
        super("<span class='fa labkey-link " + fontClass + "'></span>");

        setStyleName("gwt-FontImage gwt-PushButton");
    }

    public void setEnabled(boolean enabled)
    {
        if (enabled)
            removeStyleName("gwt-PushButton-up-disabled");
        else
            addStyleName("gwt-PushButton-up-disabled");
    }
}
