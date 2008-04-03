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
