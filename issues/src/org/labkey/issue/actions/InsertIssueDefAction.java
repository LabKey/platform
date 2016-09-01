package org.labkey.issue.actions;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.UserSchemaAction;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.view.IssuesListView;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Created by klum on 5/26/2016.
 */
@RequiresPermission(InsertPermission.class)
public class InsertIssueDefAction extends UserSchemaAction
{
    private List<Map<String, Object>> _results;

    public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
    {
        InsertView view = new InsertView(tableForm, errors)
        {
            @NotNull
            @Override
            public LinkedHashSet<ClientDependency> getClientDependencies()
            {
                LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();

                resources.add(ClientDependency.fromPath("issues/createIssueDef.js"));
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

        view.getClientDependencies().add(ClientDependency.fromPath("issues/createRelated.js"));
        view.getDataRegion().setButtonBar(bb);

        return view;
    }

    public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
    {
        _results = doInsertUpdate(tableForm, errors, true);
        return 0 == errors.getErrorCount();
    }

    @Override
    public ActionURL getSuccessURL(QueryUpdateForm form)
    {
        if (form != null && _results != null)
        {
            if (_results.size() == 1)
            {
                String name = String.valueOf(_results.get(0).get("name"));

                IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), name);
                if (issueListDef != null)
                {
                    Domain domain = issueListDef.getDomain(getUser());
                    if (domain != null && getContainer().hasPermission(getUser(), AdminPermission.class))
                    {
                        return new ActionURL(IssuesController.AdminAction.class, getContainer()).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, name);
                    }
                }
            }
        }
        return super.getSuccessURL(form);
    }

    public NavTree appendNavTrail(NavTree root)
    {
        super.appendNavTrail(root);
        root.addChild("Insert " + _table.getName());
        return root;
    }
}
