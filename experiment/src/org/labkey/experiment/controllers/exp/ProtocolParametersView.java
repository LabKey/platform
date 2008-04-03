package org.labkey.experiment.controllers.exp;

import org.labkey.api.view.JspView;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;

import java.sql.SQLException;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ProtocolParametersView extends JspView
{
    public ProtocolParametersView(ExpProtocol protocol) throws SQLException
    {
        super("/org/labkey/experiment/Parameters.jsp", ExperimentService.get().getProtocolParameters(protocol.getRowId()));
        setTitle("Protocol Parameters");
    }
}
