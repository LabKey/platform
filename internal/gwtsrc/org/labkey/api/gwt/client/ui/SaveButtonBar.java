/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.*;

/**
 * User: jgarms
 * Date: Jun 2, 2008
 * Time: 1:46:00 PM
 */
public class SaveButtonBar extends HorizontalPanel
{
    private final Saveable owner;

    private final ButtonBase finishButton;
    private final ButtonBase saveButton;
    private final ButtonBase cancelButton;

    public SaveButtonBar(Saveable s)
    {
        super();
        owner = s;
                
        getTable().setClassName("gwt-ButtonBar");

        finishButton = new ImageButton("Save & Close", new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                owner.finish();
            }
        });

        add(finishButton);

        saveButton = new ImageButton("Save", new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                owner.save();
            }
        });
        add(saveButton);


        cancelButton = new ImageButton("Cancel", new ClickHandler()
        {
            public void onClick(ClickEvent e)
            {
                owner.cancel();
            }
        });

        add(cancelButton);
    }

    public void disableAll()
    {
        saveButton.setEnabled(false);
        finishButton.setEnabled(false);
        cancelButton.setEnabled(false);
    }

    public void setAllowSave(boolean dirty)
    {
        // For unknown reasons, GWT ignores the button click if an already-enabled button is re-enabled
        // during its click handling. Specifically, this happens if the dirty state is set as part of a blur handler
        // when a form element is losing focus because a user is clicking on the save or cancel buttons
        if (saveButton.isEnabled() != dirty)
            saveButton.setEnabled(dirty);
        if (!cancelButton.isEnabled())
            cancelButton.setEnabled(true);
        if (!finishButton.isEnabled())
            finishButton.setEnabled(true);
    }

}
