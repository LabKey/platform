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

package gwt.client.org.labkey.study.designer.client;

import com.google.gwt.user.client.ui.TextArea;
import gwt.client.org.labkey.study.designer.client.ActivatingLabel;

/**
 * User: Mark Igra
 * Date: Dec 21, 2006
 * Time: 12:10:05 AM
 */
public class DescriptionWidget extends ActivatingLabel
{
    public DescriptionWidget()
    {
        super(new TextArea(), "Click to edit description");
        getWidget().setWidth("60em");
    }
}
