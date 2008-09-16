/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import org.labkey.api.gwt.client.ui.SaveButtonBar;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * User: brittp
 * Date: Feb 8, 2007
 * Time: 3:35:16 PM
 */
public class StatusBar extends HorizontalPanel implements Saveable
{
    private final String _doneLink;
    private final SaveButtonBar _saveButtonBar;

    private TemplateView _view;
    private Label _statusLabel;
    private Timer _clearTimer;

    public StatusBar(TemplateView view, final String doneLink)
    {
        _doneLink = doneLink;

        _view = view;

        _saveButtonBar = new SaveButtonBar(this);
        add(_saveButtonBar);

        _statusLabel = new Label();
        SimplePanel spacer = new SimplePanel();
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
        setDirty(false);
    }

    public void cancel()
    {
        // We're already listening for navigation if the dirty bit is set,
        // so no extra handling is needed.
        String loc = PropertyUtil.getContextPath() + "/Project" + PropertyUtil.getContainerPath() + "/begin.view";
        WindowUtil.setLocation(loc);
    }

    public void finish()
    {
        save(new AsyncCallback()
        {
            public void onFailure(Throwable caught)
            {
                // do nothing -- error is already displayed
            }

            public void onSuccess(Object result)
            {
                WindowUtil.setLocation(_doneLink);
            }
        });
    }

    public void save()
    {
        save(new AsyncCallback()
        {
            public void onFailure(Throwable caught)
            {
                // do nothing
            }

            public void onSuccess(Object result)
            {
                // do nothing
            }
        });
    }

    private void save(AsyncCallback callback)
    {
        _view.saveChanges(callback);
    }

    public void setDirty(boolean dirty)
    {
        _saveButtonBar.setAllowSave(dirty);
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
