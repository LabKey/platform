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
package org.labkey.api.gwt.client.ui.property;

import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.gwt.client.ui.ImageButton;

import java.util.ArrayList;
import java.util.List;

/*
* User: Karl Lum
* Date: Aug 15, 2008
* Time: 6:20:49 PM
*/
public class RangeValidatorDialog extends ValidatorDialog
{
    private Pair _firstType = new Pair("~eq", "");
    private Pair _secondType = new Pair("", "");
    private BoundTextBox _secondRangeValue;

    public RangeValidatorDialog(GWTPropertyValidator prop)
    {
        _oldProp = prop;
        init(prop);
        createPanel();
    }

    private void init(GWTPropertyValidator prop)
    {
        String expression = prop.getExpression();
        if (expression != null)
        {
            String[] parts = expression.split("&");
            if (parts.length >= 1)
                parsePart(parts[0], _firstType);

            if (parts.length >= 2)
                parsePart(parts[1], _secondType);
        }
    }

    private void parsePart(String expression, Pair range)
    {
        String[] parts = expression.split("=");
        if (parts.length == 2)
        {
            range.setKey(parts[0]);
            range.setValue(parts[1]);
        }
    }

    private String getExpression()
    {
        StringBuffer sb = new StringBuffer();

        sb.append(_firstType.getKey());
        sb.append('=');
        sb.append(_firstType.getValue());

        if (_secondType.getKey() != null && _secondType.getKey().length() > 0)
        {
            sb.append('&');
            sb.append(_secondType.getKey());
            sb.append('=');
            sb.append(_secondType.getValue());
        }
        return sb.toString();
    }

    public RangeValidatorDialog()
    {
        createPanel();
    }

    private void createPanel()
    {
        FlexTable panel = new FlexTable();
        final GWTPropertyValidator prop = new GWTPropertyValidator();

        if (_oldProp != null)
            prop.copy(_oldProp);

        prop.setType(GWTPropertyValidator.TYPE_RANGE);
        int row = 0;

        BoundTextBox name = new BoundTextBox("name", prop.getName(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.setName(((TextBox)widget).getText());
            }
        });
        panel.setWidget(row, 0, new HTML("Name"));
        panel.getFlexCellFormatter().setColSpan(row, 1, 2);
        panel.setWidget(row++, 1, name);

        BoundTextBox description = new BoundTextBox("description", prop.getDescription(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.setDescription(((TextBox)widget).getText());
            }
        });
        panel.setWidget(row, 0, new HTML("Description"));
        panel.getFlexCellFormatter().setColSpan(row, 1, 2);
        panel.setWidget(row++, 1, description);

        List rangeValues = new ArrayList();
        rangeValues.add(new Pair("Equals", "~eq"));
        rangeValues.add(new Pair("Does not Equal", "~neq"));
        rangeValues.add(new Pair("Greater than", "~gt"));
        rangeValues.add(new Pair("Greater than or Equals", "~gte"));
        rangeValues.add(new Pair("Less than", "~lt"));
        rangeValues.add(new Pair("Less than or Equals", "~lte"));

        BoundListBox firstRange = new BoundListBox(false, new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                ListBox lb = (ListBox)widget;
                if (lb.getSelectedIndex() != -1)
                    _firstType.setKey(lb.getValue(lb.getSelectedIndex()));
            }
        });
        firstRange.setColumns((Pair[])rangeValues.toArray(new Pair[0]));
        firstRange.selectItem(_firstType.getKey());

        BoundTextBox firstRangeValue = new BoundTextBox("firstRangeValue", _firstType.getValue(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                _firstType.setValue(((TextBox)widget).getText());
            }
        });

        panel.setWidget(row, 0, new HTML("First Criteria"));
        panel.setWidget(row, 1, firstRange);
        panel.setWidget(row++, 2, firstRangeValue);

        panel.setWidget(row++, 1, new HTML("And"));

        BoundListBox lastRange = new BoundListBox(false, new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                ListBox lb = (ListBox)widget;
                if (lb.getSelectedIndex() != -1)
                {
                    String value = lb.getValue(lb.getSelectedIndex());
                    _secondType.setKey(value);
                    if (value != null && value.length() > 0)
                        _secondRangeValue.setEnabled(true);
                    else
                    {
                        _secondRangeValue.setText("");
                        _secondRangeValue.setEnabled(false);
                    }
                }
            }
        });

        rangeValues.add(0, new Pair("<no other filter>", ""));
        lastRange.setColumns((Pair[])rangeValues.toArray(new Pair[0]));
        lastRange.selectItem(_secondType.getKey());

        _secondRangeValue = new BoundTextBox("secondRangeValue", _secondType.getValue(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                _secondType.setValue(((TextBox)widget).getText());
            }
        });
        _secondRangeValue.setEnabled(_secondType.getValue() != null && _secondType.getValue().length() > 0);
        panel.setWidget(row, 0, new HTML("Second Criteria"));
        panel.setWidget(row, 1, lastRange);
        panel.setWidget(row++, 2, _secondRangeValue);

        BoundTextArea errorMessage = new BoundTextArea("errorMessage", prop.getErrorMessage(), new WidgetUpdatable()
        {
            public void update(Widget widget)
            {
                prop.setErrorMessage(((TextArea)widget).getText());
            }
        });
        errorMessage.setCharacterWidth(25);
        errorMessage.setHeight("50px");

        panel.setWidget(row, 0, new HTML("Failure Message"));
        panel.getFlexCellFormatter().setColSpan(row, 1, 2);
        panel.setWidget(row++, 1, errorMessage);

        ImageButton save = new ImageButton("OK");
        save.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                prop.setExpression(getExpression());
                _oldProp.copy(prop);
                getListener().propertyChanged(prop);
                RangeValidatorDialog.this.hide();
            }
        });

        ImageButton cancel = new ImageButton("Cancel");
        cancel.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                RangeValidatorDialog.this.hide();
            }
        });

        HorizontalPanel hp = new HorizontalPanel();
        hp.add(save);
        hp.add(new HTML("&nbsp;"));
        hp.add(cancel);
        panel.setWidget(row++, 1, hp);

        setText("Range Validator");
        setWidget(panel);
    }
}