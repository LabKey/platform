/*
 * Copyright (c) 2007 LabKey Corporation
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

package org.labkey.plate.designer.client;

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.ui.LinkButton;
import org.labkey.api.gwt.client.ui.ImageButton;

/**
 * User: brittp
 * Date: Feb 8, 2007
 * Time: 3:35:16 PM
 */
public class StatusBar extends HorizontalPanel
{
    private TemplateView _view;
    private Label _statusLabel;
    private Timer _clearTimer;
    private ImageButton _saveButton;

    public StatusBar(TemplateView view, final String doneLink)
    {
        _view = view;
        _saveButton = new ImageButton("Save Changes");
        _saveButton.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                _view.saveChanges();
            }
        });
        add(_saveButton);
        _saveButton.setEnabled(false);

        _statusLabel = new Label();
        SimplePanel spacer = new SimplePanel();
        spacer.setWidth("10px");
        add(spacer);

        Widget doneButton = new LinkButton("Done", doneLink);
        add(doneButton);
        spacer = new SimplePanel();
        spacer.setWidth("10px");
        add(spacer);

        add(_statusLabel);
        setCellVerticalAlignment(_statusLabel, ALIGN_MIDDLE);
        _clearTimer = new Timer()
        {
            public void run()
            {
                _statusLabel.setText("");
            }
        };
    }

    public void setDirty(boolean dirty)
    {
        _saveButton.setEnabled(dirty);    
    }

    public void setStatus(String status)
    {
        setStatus(status, 5);
    }

    public void setStatus(String status, int secondsToDisplay)
    {
        _statusLabel.setText(status);
        if (secondsToDisplay > 0)
            _clearTimer.schedule(secondsToDisplay * 1000);
    }
}
