package org.labkey.api.gwt.client.ui.property;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.dom.client.KeyUpHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.ui.FlexTable;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.ui.PropertyPane;

/**
 * Created by iansigmon on 11/9/15.
 */
public class MaxLengthItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private final int DEFAULT_SIZE = 4000;
    private final String ERROR_MESSAGE = "Value >4000, Max will be used";

    private CheckBox _maxCheckBox = new CheckBox("Max");
    private IntegerBox _limitTextBox = new IntegerBox();
    private FlowPanel _label;  //Label widget
    private Panel _panel;  //Panel hosting widget controls

    private boolean domainIsProvisioned = true;

    public MaxLengthItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        _maxCheckBox.setValue(false);
        _limitTextBox.setValue(DEFAULT_SIZE);
    }

    @Override
    public int addToTable(FlexTable flexTable, int row)
    {
        _maxCheckBox.addValueChangeHandler(new MaxClicked_ChangeHandler());
        _limitTextBox.addChangeHandler(new ValueChanged_ChangeHandler());
        _limitTextBox.addKeyUpHandler(new LimitKeyUpHandler());
        _limitTextBox.setWidth("50%");

        FlowPanel labelPanel = new FlowPanel();
        labelPanel.add(new InlineLabel("Max Text Length"));
        labelPanel.add(new HelpPopup("Max Length", "<p>Maximum length of text field in number of characters.</p><p>Anything over 4000 characters must use the max designation</p>"));
        _label = labelPanel;

        flexTable.setWidget(row, LABEL_COLUMN, labelPanel);
        DOM.setElementProperty(_limitTextBox.getElement(), "id", "scale");
        DOM.setElementProperty(_maxCheckBox.getElement(), "id", "isMaxText");

        _panel = new VerticalPanel();
        _panel.add(_maxCheckBox);
        _panel.add(_limitTextBox);

        flexTable.setWidget(row, INPUT_COLUMN, _panel);
        return row + 2;
    }

    @Override
    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        if (field == null)
            return false;

        int value = _maxCheckBox.getValue().booleanValue() ? Integer.MAX_VALUE : _limitTextBox.getValue();
        boolean changed = field.getScale() != value;

        //Only set value if it has changed
        if (changed)
            field.setScale(value);

        return changed;
    }

    @Override
    public void enabledChanged()
    {
        if (!isEnabled())
        {
            addClass(_maxCheckBox, "labkey-disabled");
            addClass(_limitTextBox, "labkey-disabled");
            _maxCheckBox.setEnabled(false);
            _limitTextBox.setEnabled(false);
        }
        else
        {
            removeClass(_maxCheckBox, "labkey-disabled");
            removeClass(_limitTextBox, "labkey-disabled");
            _maxCheckBox.setEnabled(true);
            _limitTextBox.setEnabled(true);
        }
    }

    @Override
    public void showPropertyDescriptor(DomainType domainType, FieldType pd)
    {
        domainIsProvisioned = domainType != null && domainType.isProvisioned();

        //Only show controls if type is a
        boolean enabled = isStringAndEditable(pd) && domainIsProvisioned;
        setVisible(enabled);

        if (!enabled)
            return;

        int _size = pd.getScale();
        _maxCheckBox.setValue(_size > DEFAULT_SIZE);
        _limitTextBox.setValue(_size);
        onLimitChange();
    }

    /**
     * Check if type is correct and editable
     * @param pd
     * @return
     */
    private boolean isStringAndEditable(FieldType pd)
    {
        boolean isStringType = (pd.getRangeURI().equalsIgnoreCase("http://www.w3.org/2001/XMLSchema#string")
                || pd.getRangeURI().equalsIgnoreCase("http://www.w3.org/2001/XMLSchema#multiLine"))
                //TODO: Tie size of lookups to FK column size
                //Lookups are stored as strings, but FK isn't enforced by DB so disable size change for Lookups
                && pd.getLookupContainer() == null;

        return isStringType && pd.isTypeEditable();
    }

    @Override
    public void propertyDescriptorChanged(FieldType pd)
    {
        //Change visibility only if type is editable and domain is provisioned
        if (pd.isTypeEditable() && domainIsProvisioned)
        {
            //Check if still a string type
            boolean isStringType = isStringAndEditable(pd);
            setVisible(isStringType);

            if (!isStringType)
                return;
        }

        onLimitChange();
    }

    private class MaxClicked_ChangeHandler implements ValueChangeHandler
    {
        public void onValueChange(ValueChangeEvent e)
        {
            //If max is checked set to max, otherwise set it to the default
            _limitTextBox.setValue(_maxCheckBox.getValue().booleanValue() ?
                Integer.MAX_VALUE :
                DEFAULT_SIZE);

            onLimitChange();
        }
    }

    private class ValueChanged_ChangeHandler implements ChangeHandler
    {
        public void onChange(ChangeEvent e)
        {
            onLimitChange();
        }
    }

    private class LimitKeyUpHandler implements KeyUpHandler
    {
        public void onKeyUp(KeyUpEvent event)
        {
            //Do some limit validation on entered value
            int val = _limitTextBox.getValue();
            if (val > DEFAULT_SIZE)
                showWarning(); //Apply warning
            else if (val < 0)
                _limitTextBox.setValue(0); //Hardcap at 0
            else
                hideWarning(); //Value is correct range hide error if present
        }
    }

    private void showWarning()
    {
        addClass(_limitTextBox, "labkey-textbox-warning");
        _limitTextBox.setTitle(ERROR_MESSAGE);
    }

    private void hideWarning()
    {
        removeClass(_limitTextBox, "labkey-textbox-warning");
        _limitTextBox.setTitle(null);
    }

    private void onLimitChange()
    {
        //If max is checked disable text entry
        if (_maxCheckBox.getValue().booleanValue())
        {
            //Set display text and disable entry box
            _limitTextBox.setText("");
            _limitTextBox.setEnabled(false);
            addClass(_limitTextBox, "labkey-disabled");
            hideWarning();
        }
        else
        {
            if (isEnabled())
            //Enable entry box
            {
                _limitTextBox.setEnabled(true);
                removeClass(_limitTextBox,"labkey-disabled");
            }
        }
    }

    private void setVisible(boolean visibility)
    {
        _label.setVisible(visibility);
        _panel.setVisible(visibility);
    }
}
