/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.study.security.permissions;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.NoPermissionsRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.RestrictedReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.study.model.GroupSecurityType;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.studySecurityPolicy.xml.GroupPermission;
import org.labkey.studySecurityPolicy.xml.GroupPermissions;
import org.labkey.studySecurityPolicy.xml.GroupSecurityTypeEnum;
import org.labkey.studySecurityPolicy.xml.PerDatasetPermission;
import org.labkey.studySecurityPolicy.xml.PerDatasetPermissions;
import org.labkey.studySecurityPolicy.xml.SecurityTypeEnum;
import org.labkey.studySecurityPolicy.xml.StudySecurityPolicy;
import org.labkey.studySecurityPolicy.xml.StudySecurityPolicyDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 8/23/13
 * Time: 7:24 PM
 */
public class StudyPermissionExporter
{
    public StudyPermissionExporter()
    {

    }

    public StudySecurityPolicyDocument getStudySecurityPolicyDocument(StudyImpl study)
    {
        SecurityPolicy studyPolicy = SecurityPolicyManager.getPolicy(study);

        StudySecurityPolicyDocument xml = StudySecurityPolicyDocument.Factory.newInstance();

        //security type
        StudySecurityPolicy sp = StudySecurityPolicy.Factory.newInstance();

        sp.setSecurityType(SecurityTypeEnum.Enum.forString(study.getSecurityType().name()));


        //group permissions
        GroupPermissions gp = GroupPermissions.Factory.newInstance();
        List<Group> groups = SecurityManager.getGroups(study.getContainer().getProject(), true);
        for (Group group : groups)
        {
            if (group.getUserId() == Group.groupAdministrators)
                continue;

            GroupPermission p = gp.addNewGroupPermission();
            p.setGroupName(group.getName());
            p.setSecurityType(GroupSecurityTypeEnum.Enum.forString(GroupSecurityType.getTypeForGroup(group, study).name()));
        }

        sp.setGroupPermissions(gp);

        //per dataset permissions
        if (study.getSecurityType().isSupportsPerDatasetPermissions())
        {
            ArrayList<Group> restrictedGroups = new ArrayList<>();
            for (Group g : groups)
            {
                if (g.getUserId() != Group.groupAdministrators && studyPolicy.hasNonInheritedPermission(g, ReadSomePermission.class) &&
                        !studyPolicy.hasNonInheritedPermission(g, ReadPermission.class) &&
                        !studyPolicy.hasNonInheritedPermission(g, UpdatePermission.class))
                    restrictedGroups.add(g);
            }

            if (!restrictedGroups.isEmpty())
            {
                PerDatasetPermissions pdp = PerDatasetPermissions.Factory.newInstance();

                for (Dataset ds : study.getDatasets())
                {
                    SecurityPolicy dsPolicy = SecurityPolicyManager.getPolicy(ds);

                    for (Group g : restrictedGroups)
                    {
                        java.util.List<Role> roles = dsPolicy.getAssignedRoles(g);
                        Role assignedRole = roles.isEmpty() ? null : roles.get(0);

                        boolean writePerm = assignedRole != null && assignedRole.getClass() == EditorRole.class;
                        boolean readPerm = !writePerm && dsPolicy.hasNonInheritedPermission(g, ReadPermission.class);

                        if (study.getSecurityType() == SecurityType.ADVANCED_READ && writePerm)
                            readPerm = true;

                        boolean noPerm = !writePerm && !readPerm && assignedRole == null;

                        String role = null;
                        if (noPerm)
                            role = "NONE";
                        if (readPerm)
                            role = ReaderRole.class.getName();

                        if (study.getSecurityType() == SecurityType.ADVANCED_WRITE)
                        {
                            if (writePerm)
                                role = EditorRole.class.getName();

                            if (assignedRole != null)
                            {
                                role = assignedRole.getClass().getName();
                            }
                        }

                        if (role != null)
                        {
                            PerDatasetPermission pd = pdp.addNewDatasetPermission();
                            pd.setDatasetName(ds.getName());
                            pd.setGroupName(g.getName());
                            pd.setRole(role);
                        }
                    }
                }

                sp.setPerDatasetPermissions(pdp);
            }
        }

        xml.setStudySecurityPolicy(sp);

        return xml;
    }

    public void loadFromXmlFile(StudyImpl study, User u, File file, List<String> errorMsgs) throws IllegalArgumentException
    {
        study = study.createMutable();
        MutableSecurityPolicy policy = new MutableSecurityPolicy(study);
        FileInputStream is = null;
        try
        {
            XmlOptions xmlOptions = new XmlOptions();
            Map<String, String> namespaceMap = new HashMap<>();
            namespaceMap.put("", "http://labkey.org/studySecurityPolicy/xml");
            xmlOptions.setLoadSubstituteNamespaces(namespaceMap);

            is = new FileInputStream(file);
            StudySecurityPolicyDocument spd = StudySecurityPolicyDocument.Factory.parse(is, xmlOptions);
            if (AppProps.getInstance().isDevMode())
            {
                try
                {
                    XmlBeansUtil.validateXmlDocument(spd, null);
                }
                catch (XmlValidationException e)
                {
                    throw new IllegalArgumentException("Invalid XML file: " + e.getDetails());
                }
            }

            StudySecurityPolicy sp = spd.getStudySecurityPolicy();
            SecurityType st = SecurityType.valueOf(sp.getSecurityType().toString());

            // NOTE: we do not allow advanced security policy in a shared study
            Study shared = StudyManager.getInstance().getSharedStudy(study.getContainer());
            if (null != shared && shared.getShareDatasetDefinitions())
            {
                if (study.isDataspaceStudy())
                {
                    errorMsgs.add("Shared studies only support read-only datasets");
                    return;
                }
                else if (st.isSupportsPerDatasetPermissions())
                {
                    errorMsgs.add("Studies with shared datasets do not support per dataset permissions");
                    return;
                }
            }

            study.setSecurityType(st);
            StudyManager.getInstance().updateStudy(u, study);

            for (GroupPermission gp : sp.getGroupPermissions().getGroupPermissionArray())
            {
                //note: groups are currently only allowed at root or project level
                Integer groupId = SecurityManager.getGroupId(study.getContainer().getProject(), gp.getGroupName(), false);
                if (groupId == null)
                {
                    groupId = SecurityManager.getGroupId(null, gp.getGroupName(), false);
                }

                if (groupId == null)
                {
                    errorMsgs.add("Unable to find group with name: " + gp.getGroupName() + ", skipping");
                    continue;
                }

                Group group = SecurityManager.getGroup(groupId);
                GroupSecurityType securityType = GroupSecurityType.valueOf(gp.getSecurityType().toString());

                if (securityType == GroupSecurityType.UPDATE_ALL)
                    policy.addRoleAssignment(group, EditorRole.class);
                else if (securityType == GroupSecurityType.READ_ALL)
                    policy.addRoleAssignment(group, ReaderRole.class);
                else if (securityType == GroupSecurityType.PER_DATASET)
                    policy.addRoleAssignment(group, RestrictedReaderRole.class);
                else if (securityType == GroupSecurityType.NONE)
                    policy.addRoleAssignment(group, NoPermissionsRole.class);
                else
                    throw new IllegalArgumentException("Unexpected permission type: " + securityType.name());
            }

            study.savePolicy(policy, u);

            if (sp.isSetPerDatasetPermissions())
            {
                Map<Dataset, List<PerDatasetPermission>> map = new HashMap<>();
                for (PerDatasetPermission pd : sp.getPerDatasetPermissions().getDatasetPermissionArray())
                {
                    Dataset ds = study.getDatasetByName(pd.getDatasetName());
                    if (ds == null)
                    {
                        errorMsgs.add("Unable to find dataset with name: " + pd.getDatasetName() + ", skipping");
                        continue;
                    }

                    List<PerDatasetPermission> list = map.get(ds);
                    if (list == null)
                        list = new ArrayList<>();

                    list.add(pd);
                    map.put(ds, list);
                }

                for (Dataset ds : study.getDatasets())
                {
                    MutableSecurityPolicy dsPolicy = new MutableSecurityPolicy(ds);
                    List<PerDatasetPermission> list = map.get(ds);
                    if (list != null)
                    {
                        for (PerDatasetPermission pd : list)
                        {
                            //note: groups are currently only allowed at root or project level
                            Integer groupId = SecurityManager.getGroupId(study.getContainer().getProject(), pd.getGroupName(), false);
                            if (groupId == null)
                            {
                                groupId = SecurityManager.getGroupId(null, pd.getGroupName(), false);
                            }

                            if (groupId == null)
                            {
                                errorMsgs.add("Unable to find group with name: " + pd.getGroupName() + ", skipping");
                                continue;
                            }

                            Group group = SecurityManager.getGroup(groupId);

                            String roleClass = pd.getRole();
                            boolean foundRole = false;
                            for (Role role : RoleManager.getAllRoles())
                            {
                                if (role.getClass().getName().equals(roleClass))
                                {
                                    dsPolicy.addRoleAssignment(group, role);
                                    foundRole = true;
                                    break;
                                }
                            }

                            if (!foundRole && !"NONE".equals(roleClass))
                            {
                                errorMsgs.add("Unable to find role: " + roleClass);
                            }
                        }
                    }

                    ds.savePolicy(dsPolicy, u);
                }
            }
        }
        catch (XmlException | IOException e)
        {
            throw new IllegalArgumentException("Unable to read XML file: " + e.getMessage(), e);
        }
        finally
        {
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (IOException e)
                {
                    //ignore
                }
            }
        }
    }
}
