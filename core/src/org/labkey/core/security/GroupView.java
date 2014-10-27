/*
 * Copyright (c) 2008-2014 LabKey Corporation
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

package org.labkey.core.security;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.Group;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.JspView;
import org.springframework.validation.BindException;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * User: jeckels
* Date: May 1, 2008
*/
public class GroupView extends JspView<GroupView.GroupBean>
{
    public GroupView(Group group, Collection<UserPrincipal> members, Map<UserPrincipal, List<UserPrincipal>> redundantMembers, List<String> messages, boolean systemGroup, BindException errors)
    {
        super("/org/labkey/core/security/group.jsp", new GroupBean(), errors);

        GroupBean bean = getModelBean();

        bean.group = group;
        bean.groupName = group.getPath();
        bean.members = members;
        bean.messages = messages;
        bean.isSystemGroup = systemGroup;
        bean.ldapDomain = AuthenticationManager.getLdapDomain();
        bean.redundantMembers = redundantMembers;
    }

    public static class GroupBean
    {
        public Group group;
        public String groupName;
        public Collection<UserPrincipal> members;
        public List<String> messages;
        public boolean isSystemGroup;
        public String ldapDomain;
        public Map<UserPrincipal, List<UserPrincipal>> redundantMembers;

        public String displayRedundancyReasonHTML(UserPrincipal principal)
        {
            StringBuilder sb = new StringBuilder();
            if (redundantMembers.containsKey(principal))
            {
                List<UserPrincipal> path = redundantMembers.get(principal);
                sb.append("This member can be removed because:<BR/>");
                for (int i = path.size()-1; i > 0; i--)
                {
                    sb.append(StringUtils.repeat("&nbsp;&nbsp;&nbsp;", path.size() - i - 1));
                    sb.append(i == path.size() - 1 ? PageFlowUtil.filter(path.get(i)) : "Which");
                    sb.append(" is a member of ");
                    sb.append("<strong>").append(PageFlowUtil.filter(path.get(i-1))).append("</strong>");
                    sb.append("<BR/>");
                }
            }
            return sb.toString();
        }
    }
}
