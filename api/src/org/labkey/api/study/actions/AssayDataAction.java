package org.labkey.api.study.actions;

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.ACL;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.view.*;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.query.QueryView;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.validation.BindException;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:30:05 PM
*/
@RequiresPermission(ACL.PERM_READ)
public class AssayDataAction extends BaseAssayAction<ProtocolIdForm>
{
    private ExpProtocol _protocol;

    public ModelAndView getView(ProtocolIdForm summaryForm, BindException errors) throws Exception
    {
        _protocol = getProtocol(summaryForm);
        AssayHeaderView headerView = new AssayHeaderView(_protocol, AssayService.get().getProvider(_protocol), false);

        ViewContext context = getViewContext();

        VBox fullView = new VBox();
        fullView.addView(headerView);
        AssayProvider provider = AssayService.get().getProvider(_protocol);

        if (!provider.allowUpload(context.getUser(), context.getContainer(), _protocol))
            fullView.addView(provider.getDisallowedUploadMessageView(context.getUser(), context.getContainer(), _protocol));

        QueryView dataView = provider.createRunDataView(context, _protocol);
        fullView.addView(dataView);
        return fullView;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        NavTree result = super.appendNavTrail(root);
        result.addChild(_protocol.getName(), AssayService.get().getAssayRunsURL(getContainer(), _protocol));
        result.addChild(_protocol.getName() + " Data");
        return result;
    }
}
