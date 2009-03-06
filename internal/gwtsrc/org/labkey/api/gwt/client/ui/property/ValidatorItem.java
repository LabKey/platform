/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.ImageButton;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.TypePicker;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;

import java.util.List;
import java.util.ArrayList;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class ValidatorItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType> implements ValidatorDialog.UpdateListener
{
    private List<GWTPropertyValidator> _validators = new ArrayList<GWTPropertyValidator>();
    private FlexTable _validatorTable;
    private HTML _noValidatorsLabel = new HTML("<i>This field has no validators</i>");
    private ImageButton _addRangeButton;
    private RegexButton _addRegexButton;
    private boolean _validatorChanged;

    public ValidatorItem(PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
    }

    @Override
    public int addToTable(FlexTable flexTable, int row)
    {
        flexTable.getFlexCellFormatter().setHorizontalAlignment(row, 0, HasHorizontalAlignment.ALIGN_CENTER);
        flexTable.getFlexCellFormatter().setColSpan(row, 0, 2);

        FlowPanel label = new FlowPanel();
        label.add(new InlineHTML("<b>Field Validators</b>"));
        label.add(new HelpPopup("Field Validators",
                        "Field validators ensure that all values entered for a field " +
                        "obey a regular expression and/or fall within a specified range."));
        flexTable.setWidget(row, LABEL_COLUMN, label);

        row++;

        _addRegexButton = new RegexButton("Add New Regular Expression");
        flexTable.setWidget(row++, INPUT_COLUMN, _addRegexButton);
        _addRangeButton = new RangeButton("Add New Range");
        flexTable.setWidget(row++, INPUT_COLUMN, _addRangeButton);

        _validatorTable = new FlexTable();
        int col = 1;

        _validatorTable.getFlexCellFormatter().setWidth(0, col, "150px");
        _validatorTable.setHTML(0, col++, "<i>Name</i>");
        _validatorTable.getFlexCellFormatter().setWidth(0, col, "150px");
        _validatorTable.setHTML(0, col++, "<i>Description</i>");
        _validatorTable.getFlexCellFormatter().setWidth(0, col, "85px");
        _validatorTable.setHTML(0, col++, "<i>Type</i>");

        flexTable.setWidget(row, LABEL_COLUMN, _validatorTable);
        flexTable.getFlexCellFormatter().setColSpan(row, 0, 2);

        _validatorTable.setVisible(false);

        flexTable.setWidget(++row, LABEL_COLUMN, _noValidatorsLabel);
        flexTable.getFlexCellFormatter().setColSpan(row, 0, 2);
        flexTable.getFlexCellFormatter().setHorizontalAlignment(row, 0, HasHorizontalAlignment.ALIGN_CENTER);

        return row;
    }

    @Override
    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        boolean result = _validatorChanged;
        field.setPropertyValidators(_validators);
        _validatorChanged = false;
        return result;
    }

    public void enabledChanged()
    {
        _addRegexButton.setEnabled(isEnabled());
        _addRangeButton.setEnabled(isEnabled());
    }

    @Override
    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        String rangeURI = field.getRangeURI();
        _addRangeButton.setVisible(TypePicker.xsdDateTime.equals(rangeURI) || TypePicker.xsdDouble.equals(rangeURI) || TypePicker.xsdInt.equals(rangeURI));
        _validators = field.getPropertyValidators();
        refreshValidators();
    }

    class RegexButton extends ImageButton
    {
        String _label;
        GWTPropertyValidator _validator;

        RegexButton(String label)
        {
            this(label, null);
        }

        RegexButton(String label, GWTPropertyValidator validator)
        {
            super(label);
            _validator = validator;
        }

        public void onClick(Widget sender)
        {
            RegexValidatorDialog dlg = new RegexValidatorDialog(_validator == null ? new GWTPropertyValidator() : _validator);

            dlg.setListener(ValidatorItem.this);
            dlg.center();
            dlg.show();
        }
    }

    class RangeButton extends ImageButton
    {
        String _label;
        GWTPropertyValidator _validator;

        RangeButton(String label)
        {
            this(label, null);
        }

        RangeButton(String label, GWTPropertyValidator validator)
        {
            super(label);
            _validator = validator;
        }

        public void onClick(Widget sender)
        {
            RangeValidatorDialog dlg = new RangeValidatorDialog(_validator == null ? new GWTPropertyValidator() : _validator);

            dlg.setListener(ValidatorItem.this);
            dlg.center();
            dlg.show();
        }
    }

    private void refreshValidators()
    {
        int row = 1;
        int rowCount = _validatorTable.getRowCount() - 1;

        while (rowCount > 0)
            _validatorTable.removeRow(rowCount--);

        for (final GWTPropertyValidator pv : _validators)
        {
            HorizontalPanel panel = new HorizontalPanel();

            if (_addRangeButton.isEnabled())
            {
                String src = PropertyUtil.getContextPath() + "/_images/partdelete.gif";
                Image i = new Image(src);
                i.addClickListener(new ClickListener(){
                    public void onClick(Widget sender)
                    {
                        _validators.remove(pv);
                        _validatorChanged = true;
                        _propertyPane.copyValuesToPropertyDescriptor();
                        refreshValidators();
                    }
                });

                panel.add(i);
                panel.add(new HTML("&nbsp;"));
                if (pv.getType().equals(GWTPropertyValidator.TYPE_RANGE))
                    panel.add(new RangeButton("...", pv));
                else if (pv.getType().equals(GWTPropertyValidator.TYPE_REGEX))
                    panel.add(new RegexButton("...", pv));
            }

            _validatorTable.setWidget(row, 0, panel);
            _validatorTable.setWidget(row, 1, new HTML(StringUtils.filter(pv.getName(), true)));
            _validatorTable.setWidget(row, 2, new HTML(StringUtils.filter(pv.getDescription(), true)));
            _validatorTable.setWidget(row++, 3, new HTML(pv.getType()));
        }

        _validatorTable.setVisible(!_validators.isEmpty());
        _noValidatorsLabel.setVisible(_validators.isEmpty());
    }

    public void propertyChanged(GWTPropertyValidator validator)
    {
        if (validator.isNew() && validator.getRowId() == 0)
        {
            validator.setNew(false);
            _validators.add(validator);
        }
        _validatorChanged = true;
        refreshValidators();
        _propertyPane.copyValuesToPropertyDescriptor();
    }
}