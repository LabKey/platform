package org.labkey.api.gwt.client.ui.property;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
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

    private CheckBox _maxCheckBox = new CheckBox("Max");
    private IntegerBox _limitTextBox = new IntegerBox();

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
        _limitTextBox.setWidth("50%");

        FlowPanel labelPanel = new FlowPanel();
        labelPanel.add(new InlineLabel("Max Text Length"));
        labelPanel.add(new HelpPopup("Max Length", "<p>Maximum length of text field in number of characters.</p><p>Anything over 4000 characters must use the max designation</p>"));

        flexTable.setWidget(row, LABEL_COLUMN, labelPanel);
        DOM.setElementProperty(_limitTextBox.getElement(), "id", "scale");
        DOM.setElementProperty(_maxCheckBox.getElement(),"id","isMaxText");

        VerticalPanel panel = new VerticalPanel();
        panel.add(_maxCheckBox);
        panel.add(_limitTextBox);

        flexTable.setWidget(row, INPUT_COLUMN, panel);

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
        int _size = pd.getScale();
        _maxCheckBox.setValue(_size > DEFAULT_SIZE);
        _limitTextBox.setValue(_size);
        onLimitChange();
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
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

    private void onLimitChange()
    {
        //If max is checked disable text entry
        if (_maxCheckBox.getValue().booleanValue())
        {
            //Set display text and disable entry box
            _limitTextBox.setText("");
            _limitTextBox.setEnabled(false);
            addClass(_limitTextBox, "labkey-disabled");
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
}
