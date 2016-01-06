package org.labkey.api.gwt.client;

import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertiesEditor;
import org.labkey.api.gwt.client.ui.SaveButtonBar;
import org.labkey.api.gwt.client.util.PropertyUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by davebradlee on 12/12/15
 */
public abstract class AbstractDesignerMainPanel extends VerticalPanel
{
    protected final RootPanel _rootPanel;
    protected String _returnURL;
    protected String _designerURL;
    protected HTML _statusLabel = new HTML("<br/>");
    protected static final String STATUS_SUCCESSFUL = "Save successful.<br/>";
    protected boolean _dirty;
    protected SaveButtonBar saveBarTop;
    protected SaveButtonBar saveBarBottom;
    protected boolean _saveInProgress = false;
    final protected List<PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>> _domainEditors = new ArrayList<PropertiesEditor<GWTDomain<GWTPropertyDescriptor>, GWTPropertyDescriptor>>();

    public AbstractDesignerMainPanel(RootPanel rootPanel)
    {
        _rootPanel = rootPanel;
        _returnURL = PropertyUtil.getReturnURL();
        _designerURL = Window.Location.getHref();
    }

    protected void addErrorMessage(String message)
    {
        VerticalPanel mainPanel = new VerticalPanel();
        mainPanel.add(new Label(message));
        _rootPanel.add(mainPanel);
    }

    public void setDirty(boolean dirty)
    {
        if (dirty && _statusLabel.getText().equalsIgnoreCase(STATUS_SUCCESSFUL))
            _statusLabel.setHTML("<br/>");

        setAllowSave(dirty);

        _dirty = dirty;
    }

    public boolean isDirty()
    {
        return _dirty;
    }

    protected void setAllowSave(boolean dirty)
    {
        if (_saveInProgress)
        {
            if (saveBarTop != null)
                saveBarTop.disableAll();
            if (saveBarBottom != null)
                saveBarBottom.disableAll();
        }
        else
        {
            if (saveBarTop != null)
                saveBarTop.setAllowSave(dirty);
            if (saveBarBottom != null)
                saveBarBottom.setAllowSave(dirty);
        }
    }

    public String getCurrentURL()
    {
        return _designerURL;
    }

    public class DesignerClosingListener implements Window.ClosingHandler
    {
        public void onWindowClosing(Window.ClosingEvent event)
        {
            boolean dirty = _dirty;
            for (int i = 0; i < _domainEditors.size() && !dirty; i++)
            {
                dirty = _domainEditors.get(i).isDirty();
            }
            if (dirty)
                event.setMessage("Changes have not been saved and will be discarded.");
        }
    }

}
