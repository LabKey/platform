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

import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.Saveable;
import org.labkey.api.gwt.client.ui.WindowUtil;
import org.labkey.api.gwt.client.ui.HelpPopup;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.util.StringUtils;
import org.labkey.api.gwt.client.DefaultValueType;
import com.google.gwt.user.client.ui.*;
import com.google.gwt.user.client.Window;
import com.google.gwt.http.client.URL;

/**
 * User: jeckels
 * Date: Nov 14, 2008
 */
public class DefaultValueItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private ListBox _defaultValueTypes = new ListBox();
    private Label _currentDefault = new Label();
    private Saveable<GWTDomain> _owner;
    private HelpPopup _helpPopup = new HelpPopup("Default Value Types", "");
    private GWTDomain _domain;
    private HTML _setDefaultValueLink;
    private static final String SET_DEFAULT_DISABLED = "<i>Not supported for file/attachment fields.</i>";
    private static final String SET_DEFAULT_ENABLED = "[<a href=\"#\">set&nbsp;value</a>]";
    private FlexTable _defaultTypeTable;
    private FlexTable _defaultValueTable;

    public DefaultValueItem(Saveable<GWTDomain> owner, PropertyPane<DomainType, FieldType> propertyPane)
    {
        super(propertyPane);
        _owner = owner;
    }

    private boolean supportsDefaultValues()
    {
        return (_domain.getDefaultValueOptions() != null &&
                _domain.getDefaultValueOptions().length > 0);
    }

    private void updateEnabledState(FieldType field)
    {
        if (!supportsDefaultValues())
            return;
        if (_defaultValueTypes.getItemCount() == 0)
        {
            for (DefaultValueType type : _domain.getDefaultValueOptions())
                _defaultValueTypes.addItem(type.getLabel(), type.name());
        }
        if (field.isFileType())
        {
            _defaultValueTypes.setEnabled(false);
            _setDefaultValueLink.setHTML(SET_DEFAULT_DISABLED);
        }
        else
        {
            _defaultValueTypes.setEnabled(true);
            _setDefaultValueLink.setHTML(SET_DEFAULT_ENABLED);
        }
        DefaultValueType defaultType = field.getDefaultValueType();
        if (defaultType == null)
            defaultType = _domain.getDefaultDefaultValueType();
        if (field.isFileType())
            defaultType = DefaultValueType.FIXED_EDITABLE;
        for (int i = 0; i < _defaultValueTypes.getItemCount(); i++)
        {
            String currentItemValue = _defaultValueTypes.getValue(i);
            if (currentItemValue.equals(defaultType.name()))
            {
                _defaultValueTypes.setSelectedIndex(i);
                return;
            }
        }
        _defaultValueTypes.setSelectedIndex(0);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        flexTable.setWidget(row, LABEL_COLUMN, new HTML("Default&nbsp;Type"));

        _setDefaultValueLink = new HTML(SET_DEFAULT_ENABLED);
        _setDefaultValueLink.addClickListener(new ClickListener()
        {
            public void onClick(Widget sender)
            {
                if (!_defaultValueTypes.isEnabled())
                    return;

                if (_owner.isDirty() && Window.confirm("You must save your changes before setting default values.  Save changes?"))
                {
                    _owner.save(new Saveable.SaveListener<GWTDomain>()
                    {
                        public void saveSuccessful(GWTDomain domain, String designerUrl)
                        {
                            String actionURL = PropertyUtil.getRelativeURL("setDefaultValues", "list");
                            String queryString = "returnUrl=" + URL.encodeComponent(designerUrl) + "&domainId=" + domain.getDomainId();
                            final String url = actionURL + "?" + queryString;
                            WindowUtil.setLocation(url);
                        }
                    });
                }
                else
                {
                    String actionURL = PropertyUtil.getRelativeURL("setDefaultValues", "list");
                    String currentURL = PropertyUtil.getCurrentURL();
                    String queryString = "returnUrl=" + URL.encodeComponent(currentURL) + "&domainId=";
                    final String baseURL = actionURL + "?" + queryString;
                    WindowUtil.setLocation(baseURL + _propertyPane.getDomainId());
                }
            }
        });

        _defaultTypeTable = new FlexTable();
        _defaultTypeTable.setWidget(0, 0, _defaultValueTypes);
        _defaultTypeTable.setWidget(0, 1, _helpPopup);
        flexTable.setWidget(row, INPUT_COLUMN, _defaultTypeTable);

        _defaultValueTypes.addClickListener(createClickListener());
        _defaultValueTypes.addKeyboardListener(createKeyboardListener());
        _defaultTypeTable.setWidget(0, 2, _setDefaultValueLink);

        _defaultValueTable = new FlexTable();
        _defaultValueTable.setWidget(0, 0, _currentDefault);
        _defaultValueTable.setWidget(0, 1, _setDefaultValueLink);

        flexTable.setWidget(row + 1, LABEL_COLUMN, new HTML("Default&nbsp;Value"));
        flexTable.setWidget(row + 1, INPUT_COLUMN, _defaultValueTable);

        return row + 2;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        if (_defaultValueTypes.isEnabled())
        {
            String name = _defaultValueTypes.getValue(_defaultValueTypes.getSelectedIndex());
            DefaultValueType newType = DefaultValueType.valueOf(name);
            boolean changed = (newType == field.getDefaultValueType());
            field.setDefaultValueType(DefaultValueType.valueOf(name));
            return changed;
        }
        return false;
    }

    public void enabledChanged()
    {
        _defaultValueTypes.setEnabled(isEnabled());
    }

    public void showPropertyDescriptor(DomainType domain, FieldType field)
    {
        _domain = domain;
        StringBuilder helpString = new StringBuilder();
        if (supportsDefaultValues())
        {
            DefaultValueType[] defaultTypes = _domain.getDefaultValueOptions();
            for (int i = 0; i < defaultTypes.length; i++)
            {
                DefaultValueType type = defaultTypes[i];
                helpString.append("<b>").append(type.getLabel()).append("</b>: ").append(type.getHelpText());
                if (i < defaultTypes.length - 1)
                    helpString.append("<br><br>");
            }
            _currentDefault.setText(field.getDefaultValue());
            _helpPopup.setBody(helpString.toString());
            updateEnabledState(field);
        }
        else
        {
            _defaultTypeTable.clear();
            _defaultTypeTable.setWidget(0, 0, new HTML("<i>Not supported for " +
                    StringUtils.filter(domain.getName()) + "</i>"));
            _defaultValueTable.clear();
            _defaultValueTable.setWidget(0, 0, new HTML("<i>None</i>"));
        }
    }

    @Override
    public void propertyDescriptorChanged(FieldType field)
    {
        updateEnabledState(field);
    }

}
