/*
 * Copyright (c) 2008 LabKey Corporation
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

import com.google.gwt.user.client.ui.ButtonBase;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.Widget;

/**
 * User: jgarms
 * Date: Jun 2, 2008
 * Time: 1:46:00 PM
 */
public class SaveButtonBar extends FlexTable
{
    private final Saveable owner;

    private final ButtonBase finishButton;
    private final ButtonBase saveButton;
    private final ButtonBase cancelButton;

    public SaveButtonBar(Saveable s)
    {
        super();
        owner = s;

        finishButton = new ImageButton("Save & Close", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                owner.finish();
            }
        });

        setWidget(0, 0, finishButton);

        saveButton = new ImageButton("Save", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                owner.save();
            }
        });
        setWidget(0, 1, saveButton);


        cancelButton = new ImageButton("Cancel", new ClickListener()
        {
            public void onClick(Widget sender)
            {
                owner.cancel();
            }
        });

        setWidget(0, 2, cancelButton);
    }

    public void setAllowSave(boolean dirty)
    {
        saveButton.setEnabled(dirty);
    }    

}
