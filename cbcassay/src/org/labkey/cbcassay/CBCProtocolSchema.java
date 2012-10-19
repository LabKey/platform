package org.labkey.cbcassay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayResultDetailsAction;
import org.labkey.api.study.assay.AbstractTsvAssayProvider;
import org.labkey.api.study.assay.AssayProtocolSchema;
import org.labkey.api.study.assay.AssayResultTable;
import org.labkey.api.view.ActionURL;

import java.util.HashMap;
import java.util.Map;

/**
 * User: jeckels
 * Date: 10/19/12
 */
public class CBCProtocolSchema extends AssayProtocolSchema
{
    public CBCProtocolSchema(User user, Container container, ExpProtocol protocol, Container targetStudy)
    {
        super(user, container, protocol, targetStudy);
    }

    @Override
    public FilteredTable createDataTable(boolean includeCopiedToStudyColumns)
    {
        AssayResultTable table = new AssayResultTable(this, includeCopiedToStudyColumns);

        ActionURL showDetailsUrl = new ActionURL(AssayResultDetailsAction.class, getContainer());
        showDetailsUrl.addParameter("rowId", getProtocol().getRowId());
        Map<String, String> params = new HashMap<String, String>();
        params.put("dataRowId", AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME);
        table.setDetailsURL(new DetailsURL(showDetailsUrl, params));

        ActionURL updateUrl = new ActionURL(CBCAssayController.UpdateAction.class, null);
        updateUrl.addParameter("rowId", getProtocol().getRowId());
        Map<String, Object> updateParams = new HashMap<String, Object>();
        updateParams.put("dataRowId", FieldKey.fromString(AbstractTsvAssayProvider.ROW_ID_COLUMN_NAME));
        table.setUpdateURL(new DetailsURL(updateUrl, updateParams));

        return table;
    }


}
