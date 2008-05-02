package org.labkey.core.security;

import org.labkey.api.view.JspView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.AppProps;
import org.labkey.common.util.Pair;
import org.springframework.validation.BindException;

import java.util.List;

/**
 * User: jeckels
* Date: May 1, 2008
*/
public class GroupView extends JspView<GroupView.GroupBean>
{
    public GroupView(String groupName, List<Pair<Integer,String>> members, List<String> messages, boolean globalGroup, BindException errors)
    {
        super("/org/labkey/core/security/group.jsp", new GroupBean(), errors);

        GroupBean bean = getModelBean();

        bean.groupName = groupName;
        bean.members = members;
        bean.messages = messages;
        bean.isGlobalGroup = globalGroup;
        bean.ldapDomain = AppProps.getInstance().getLDAPDomain();
        bean.basePermissionsURL = ActionURL.toPathString("User", "userAccess", getViewContext().getContainer()) + "?userId=";
    }

    public static class GroupBean
    {
        public String groupName;
        public List<Pair<Integer, String>> members;
        public List<String> messages;
        public boolean isGlobalGroup;
        public String ldapDomain;
        public String basePermissionsURL;
    }
}
