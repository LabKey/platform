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

import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Feb 6, 2008
 */
public class PropertyPane extends FlexTable
{
    private TextBox _formatTextBox = new TextBox();
    private HelpPopup _formatHelpPopup = new HelpPopup("Format Strings", "");

    private TextArea _descriptionTextArea = new TextArea();
    private CheckBox _requiredCheckBox = new CheckBox();

    private GWTPropertyDescriptor _currentPD;

    private static final String FORMAT_HELP_NUMBER = "The format string for numbers " +
    "must be compatible with the format that the java class <a href=\"http://java.sun.com/j2se/1.4.2/docs/api/java/text/DecimalFormat.html\" target=\"blank\"><code>DecimalFormat</code></a> accepts.<br/>" +
            "<table class=\"labkey-format-helper\">" +
            "<tr class=\"labkey-format-helper-header\"><th align=left>Symbol<th align=left>Location<th align=left>Localized?<th align=left>Meaning</tr>" +
            "<tr valign=top><td><code>0</code><td>Number<td>Yes<td>Digit</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>#</code><td>Number<td>Yes<td>Digit, zero shows as absent</tr>" +
            "<tr valign=top><td><code>.</code><td>Number<td>Yes<td>Decimal separator or monetary decimal separator</tr>" +
            "<tr valign=top class=\"labkey-alternate-row\"><td><code>-</code><td>Number<td>Yes<td>Minus sign</tr>" +
            "<tr valign=top><td><code>,</code><td>Number<td>Yes<td>Grouping separator</tr></table><br/><br/>";
    private static final String FORMAT_HELP_DATE = "The format string for dates must be compatible with the format that the java class " +
    "<a href=\"http://java.sun.com/j2se/1.4.2/docs/api/java/text/SimpleDateFormat.html\" target=\"blank\"><code>SimpleDateFormat</code></a> accepts.<br/>" +
            "<table class=\"labkey-format-helper\">" +
            "<tr class=\"labkey-format-helper-header\"><th align=left>Letter<th align=left>Date or Time Component<th align=left>Examples</tr>" +
            "<tr><td><code>G</code><td>Era designator<td><code>AD</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>y</code><td>Year<td><code>1996</code>; <code>96</code></tr>" +
            "<tr><td><code>M</code><td>Month in year<td><code>July</code>; <code>Jul</code>; <code>07</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>w</code><td>Week in year<td><code>27</code></td></tr>" +
            "<tr><td><code>W</code><td>Week in month<td><code>2</code></td></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>D</code><td>Day in year<td><code>189</code></td></tr>" +
            "<tr><td><code>d</code><td>Day in month<td><code>10</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>F</code><td>Day of week in month<td><code>2</code></tr>" +
            "<tr><td><code>E</code><td>Day in week<td><code>Tuesday</code>; <code>Tue</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>a</code><td>Am/pm marker<td><code>PM</code></tr>" +
            "<tr><td><code>H</code><td>Hour in day (0-23)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>k</code><td>Hour in day (1-24)<td><code>24</code></tr>" +
            "<tr><td><code>K</code><td>Hour in am/pm (0-11)<td><code>0</code></tr>" +
            "<tr class=\"labkey-alternate-row\"><td><code>h</code><td>Hour in am/pm (1-12)<td><code>12</code></tr></table>";
    private final Element _backgroundElement;
    private PropertiesEditor _propertiesEditor;
    private List _changeListeners = new ArrayList();
    private boolean _editable;

    private Image _spacerImage;
    private int _spacerHeight;

    public PropertyPane(Element backgroundElement, PropertiesEditor propertiesEditor)
    {
        _backgroundElement = backgroundElement;
        _propertiesEditor = propertiesEditor;
        int row = 0;

        _spacerImage = new Image();
        _spacerImage.setWidth("1px");
        _spacerImage.setHeight("1px");
        _spacerHeight = 1;

        setWidget(row++, 0, _spacerImage);

        getFlexCellFormatter().setHorizontalAlignment(row, 0, HasHorizontalAlignment.ALIGN_CENTER);
        getFlexCellFormatter().setColSpan(row, 0, 2);
        setWidget(row, 0, new HTML("<b>Additional Properties</b>"));

        row++;

        propertiesEditor.addChangeListener(new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                if (_currentPD != null)
                {
                    refreshFormatBox(_currentPD);
                }
            }
        });

        ChangeListener changeListener = new ChangeListener()
        {
            public void onChange(Widget sender)
            {
                copyValuesToPropertyDescriptor();
            }
        };

        KeyboardListener keyboardListener = new KeyboardListenerAdapter()
        {
            public void onKeyUp(Widget sender, char keyCode, int modifiers)
            {
                copyValuesToPropertyDescriptor();
            }

            public void onKeyPress(Widget sender, char keyCode, int modifiers)
            {
            }
        };

        ClickListener clickListener = new ClickListener()
        {
            public void onClick(Widget sender)
            {
                copyValuesToPropertyDescriptor();
            }
        };

        _formatTextBox.addChangeListener(changeListener);
        _descriptionTextArea.addChangeListener(changeListener);

        _formatTextBox.addKeyboardListener(keyboardListener);
        _descriptionTextArea.addKeyboardListener(keyboardListener);

        _requiredCheckBox.addClickListener(clickListener);
        _requiredCheckBox.addKeyboardListener(keyboardListener);

        setWidget(row, 0, new Label("Format"));
        DOM.setElementProperty(_formatTextBox.getElement(), "id", "propertyFormat");
        HorizontalPanel formatPanel = new HorizontalPanel();
        formatPanel.add(_formatTextBox);
        formatPanel.add(_formatHelpPopup);
        setWidget(row, 1, formatPanel);

        row++;

        setWidget(row, 0, new Label("Required"));
        setWidget(row, 1, _requiredCheckBox);

        row++;

        setWidget(row, 0, new Label("Description"));
        DOM.setElementProperty(_descriptionTextArea.getElement(), "id", "propertyDescription");
        _descriptionTextArea.setSize("240px", "50px");
        setWidget(row, 1, _descriptionTextArea);
    }

    public void copyValuesToPropertyDescriptor()
    {
        if (_currentPD != null)
        {
            boolean changed = false;
            if (_formatTextBox.isEnabled())
            {
                changed = changed || !nullSafeEquals(_currentPD.getFormat(), trimValue(_formatTextBox.getText())); 
                _currentPD.setFormat(trimValue(_formatTextBox.getText()));
            }
            if (_descriptionTextArea.isEnabled())
            {
                changed = changed || !nullSafeEquals(_currentPD.getDescription(), trimValue(_descriptionTextArea.getText()));
                _currentPD.setDescription(trimValue(_descriptionTextArea.getText()));
            }
            if (_requiredCheckBox.isEnabled())
            {
                changed = changed || !_currentPD.isRequired() == _requiredCheckBox.isChecked();  
                _currentPD.setRequired(_requiredCheckBox.isChecked());
            }
            if (changed)
            {
                fireChangeListeners();
            }
        }
    }

    public boolean nullSafeEquals(Object o1, Object o2)
    {
        if (o1 == o2)
        {
            return true;
        }

        if (o1 != null ? !o1.equals(o2) : o2 != null)
        {
            return false;
        }

        return true;
    }

    private void fireChangeListeners()
    {
        for (int i = 0; i < _changeListeners.size(); i++)
        {
            ((ChangeListener)_changeListeners.get(i)).onChange(this);
        }
    }

    public void addChangeListener(ChangeListener cl)
    {
        _changeListeners.add(cl);
    }



    private boolean canFormat(String rangeURI)
    {
        // for now, we only support format strings for datetime and numeric types
        if (rangeURI.equals(TypePicker.xsdDateTime) ||
            rangeURI.equals(TypePicker.xsdDouble) ||
            rangeURI.equals(TypePicker.xsdInt))
        {
            return true;
        }
        return false;
    }
    
    private String trimValue(String text)
    {
        if (text == null)
        {
            return null;
        }
        text = text.trim();
        if (text.length() == 0)
        {
            return null;
        }
        return text;
    }

    private void setEnabled(boolean enabled)
    {
        _formatTextBox.setEnabled(enabled);
        _descriptionTextArea.setEnabled(enabled);
        _requiredCheckBox.setEnabled(enabled);
    }

    public void showPropertyDescriptor(GWTPropertyDescriptor newPD, boolean editable)
    {
        showPropertyDescriptor(newPD, editable, 0);
    }

    public void showPropertyDescriptor(GWTPropertyDescriptor newPD, boolean editable, int rowAbsoluteY)
    {
        copyValuesToPropertyDescriptor();
        
        int newSpacerHeight = Math.max(0, rowAbsoluteY - getAbsoluteTop() - 25);
        int bottomOfEditor = _propertiesEditor.getMainPanel().getOffsetHeight() - 5;
        if (newSpacerHeight + (getOffsetHeight() - _spacerHeight) > bottomOfEditor)
        {
            newSpacerHeight = Math.max(0, bottomOfEditor - (getOffsetHeight() - _spacerHeight));
        }
        _spacerImage.setHeight(newSpacerHeight + "px");
        _spacerHeight = newSpacerHeight;

        _currentPD = newPD;
        _editable = editable;

        setEnabled(editable);

        if (_currentPD != null)
        {
            refreshFormatBox(newPD);

            _descriptionTextArea.setText(newPD.getDescription());

            _requiredCheckBox.setChecked(newPD.isRequired());

            DOM.setStyleAttribute(getElement(), "visibility", "visible");
            DOM.setStyleAttribute(_backgroundElement, "backgroundColor", "#eeeeee");
        }
        else
        {
            DOM.setStyleAttribute(getElement(), "visibility", "hidden");
            DOM.setStyleAttribute(_backgroundElement, "backgroundColor", "#ffffff");
        }
    }

    private void refreshFormatBox(GWTPropertyDescriptor newPD)
    {
        _formatTextBox.setText(newPD.getFormat());
        if (!canFormat(newPD.getRangeURI()))
        {
            _formatTextBox.setEnabled(false);
            _formatTextBox.setText("<no format set>");
            _formatHelpPopup.setVisible(false);
        }
        else
        {
            _formatTextBox.setEnabled(_editable);
            _formatHelpPopup.setVisible(true);
            _formatHelpPopup.setBody(newPD.getRangeURI().equals(TypePicker.xsdDateTime) ? FORMAT_HELP_DATE : FORMAT_HELP_NUMBER);
        }
    }
}
