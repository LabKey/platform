package org.labkey.api.gwt.client.ui.property;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertyPane;
import org.labkey.api.gwt.client.ui.HelpPopup;

/**
 * User: jeckels
 * Date: Sep 2, 2009
 */
public class ImportAliasesItem<DomainType extends GWTDomain<FieldType>, FieldType extends GWTPropertyDescriptor> extends PropertyPaneItem<DomainType, FieldType>
{
    private TextBox _aliasesTextBox = new TextBox();

    public ImportAliasesItem(PropertyPane<DomainType, FieldType> domainTypeFieldTypePropertyPane)
    {
        super(domainTypeFieldTypePropertyPane);
    }

    public int addToTable(FlexTable flexTable, int row)
    {
        _aliasesTextBox.addChangeHandler(createChangeHandler());
        _aliasesTextBox.addKeyUpHandler(createKeyUpHandler());
        _aliasesTextBox.setWidth("100%");

        FlowPanel labelPanel = new FlowPanel();
        labelPanel.add(new InlineLabel("Import Aliases"));
        labelPanel.add(new HelpPopup("Import Aliases", "<p>A set of alternate field names when importing from external files, in addition to the field's name and label.</p><p>Multiple aliases may be separated by spaces or commas. To specify an alias that contains spaces, use double-quotes (&quot;) around the alias.</p>"));

        flexTable.setWidget(row, LABEL_COLUMN, labelPanel);
        DOM.setElementProperty(_aliasesTextBox.getElement(), "id", "importAliases");
        flexTable.setWidget(row, INPUT_COLUMN, _aliasesTextBox);

        return ++row;
    }

    public boolean copyValuesToPropertyDescriptor(FieldType field)
    {
        if (_aliasesTextBox.isEnabled())
        {
            String aliasString = _aliasesTextBox.getText();
            boolean changed = aliasString.equals(field.getImportAliases());
            field.setImportAliases(aliasString);
            return changed;
        }
        return false;
    }

    public void enabledChanged()
    {
        _aliasesTextBox.setEnabled(isEnabled());
    }

    public void showPropertyDescriptor(DomainType domainType, FieldType pd)
    {
        _aliasesTextBox.setText(pd.getImportAliases());
    }
}
