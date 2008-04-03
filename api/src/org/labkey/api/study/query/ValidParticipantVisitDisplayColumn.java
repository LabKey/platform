package org.labkey.api.study.query;

import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleDisplayColumn;
import org.labkey.api.util.PageFlowUtil;

import java.io.IOException;
import java.io.Writer;

/**
 * User: jeckels
 * Date: Nov 6, 2007
 */
public class ValidParticipantVisitDisplayColumn extends SimpleDisplayColumn
{
    private final PublishRunDataQueryView.ResolverHelper _resolverHelper;
    private boolean _firstMatch = true;
    private boolean _firstNoMatch = true;

    public ValidParticipantVisitDisplayColumn(PublishRunDataQueryView.ResolverHelper resolverHelper)
    {
        _resolverHelper = resolverHelper;
        setCaption("Specimen Match");
    }

    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
    {
        if (_resolverHelper.hasMatch(ctx))
        {
            out.write("Y");
            if (_firstMatch)
            {
                _firstMatch = false;
                out.write(PageFlowUtil.helpPopup("Match", "There is a specimen in the target study that matches this row's participant and visit/date"));
            }
        }
        else
        {
            out.write("N");
            if (_firstNoMatch)
            {
                _firstNoMatch = false;
                out.write(PageFlowUtil.helpPopup("No match", "There are no specimens in the target study that matches this row's participant and visit/date"));
            }
        }
    }
}
