package org.labkey.issue.experimental.actions;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.UserSchemaAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.ClientDependency;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashSet;

/**
 * Created by klum on 5/26/2016.
 */
@RequiresPermission(InsertPermission.class)
public class InsertIssueDefAction extends UserSchemaAction
{
    public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
    {
        InsertView view = new InsertView(tableForm, errors)
        {
            @NotNull
            @Override
            public LinkedHashSet<ClientDependency> getClientDependencies()
            {
                LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();

                resources.add(ClientDependency.fromPath("issues/experimental/createIssueDef.js"));
                resources.addAll(super.getClientDependencies());

                return resources;
            }
        };

        ButtonBar bb = new ButtonBar();
        bb.setStyle(ButtonBar.Style.separateButtons);
        String submitGUID = "submit-" + GUID.makeGUID();
        String cancelGUID = "cancel-" + GUID.makeGUID();

        ActionButton btnSubmit = new ActionButton("Submit");
        btnSubmit.setScript("IssueDefUtil.verifyIssueDefName('" + submitGUID + "');");
        btnSubmit.setActionType(ActionButton.Action.SCRIPT);
        btnSubmit.setId(submitGUID);
        ActionButton btnCancel = new ActionButton(getCancelURL(tableForm), "Cancel");
        btnCancel.setId(cancelGUID);

        bb.add(btnSubmit);
        bb.add(btnCancel);

        view.getClientDependencies().add(ClientDependency.fromPath("issues/experimental/createRelated.js"));
        view.getDataRegion().setButtonBar(bb);

        return view;
    }

    public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
    {
        doInsertUpdate(tableForm, errors, true);
        return 0 == errors.getErrorCount();
    }

    public NavTree appendNavTrail(NavTree root)
    {
        super.appendNavTrail(root);
        root.addChild("Insert " + _table.getName());
        return root;
    }
}
