/*
 * Copyright (c) 2010-2012 LabKey Corporation
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

package gwt.client.org.labkey.plate.designer.client;

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import org.labkey.api.gwt.client.ui.SaveButtonBar;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.util.ErrorDialogAsyncCallback;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * User: brittp
 * Date: Feb 8, 2007
 * Time: 3:35:16 PM
 */
public class StatusBar extends HorizontalPanel implements Saveable<Object>
{
    private final String _doneLink;
    private final SaveButtonBar _saveButtonBar;

    private TemplateView _view;
    private Label _statusLabel;
    private Timer _clearTimer;
    private boolean _dirty;

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

    public void save(final SaveListener<Object> listener)
    {
        _view.saveChanges(new ErrorDialogAsyncCallback()
        {
            public void onSuccess(Object result)
            {
                setDirty(false);
                if (listener != null)
                    listener.saveSuccessful(listener, PropertyUtil.getCurrentURL());
            }
        });
            
    }

    public void finish()
    {
        save(new SaveListener<Object>()
        {
            public void saveSuccessful(Object result, String designerUrl)
            {
                if (_doneLink != null && _doneLink.length() > 0)
                    WindowUtil.setLocation(_doneLink);
                else
                    cancel();
            }
        });
    }

    public String getCurrentURL()
    {
        return PropertyUtil.getCurrentURL();
    }

    public void save()
    {
        save(null);
    }

    public void setDirty(boolean dirty)
    {
        _dirty = dirty;
        _saveButtonBar.setAllowSave(dirty);
    }

    public boolean isDirty()
    {
        return _dirty;
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
