/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.api.security;

import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: 10/24/11
 * Time: 10:01 AM
 */
public class NestedGroupsTest extends Assert
{
    private static final String ALL = "All Employees";
    private static final String DIV_A = "Division A";
    private static final String DIV_B = "Division B";
    private static final String DIV_C = "Division C";
    private static final String CODERS = "Coders";
    private static final String TESTERS = "Testers";
    private static final String WRITERS = "Writers";
    private static final String PROJECT_X = "Project X";
    private static final String SITE_GROUP_1 = "TestSiteGroup1";
    private static final String SITE_GROUP_2 = "TestSiteGroup2";
    private static final String CONCURRENCY_TEST_GROUP = "ConcurrencyGroup";

    private static final String ADD_TO_GUESTS = "Can't add a member to the Guests group";
    private static final String ADD_TO_USERS = "Can't add a member to the Users group";
    private static final String[] PROJECT_GROUP_NAMES = new String[] {ALL, DIV_A, DIV_B, DIV_C, CODERS, TESTERS, WRITERS, PROJECT_X, CONCURRENCY_TEST_GROUP};
    private static final String[] SITE_GROUP_NAMES = new String[] {SITE_GROUP_1, SITE_GROUP_2};

    private Container _project;
    private User _testUser;

    @Before
    public void setUp() throws ValidEmail.InvalidEmailException, SecurityManager.UserManagementException
    {
        _project = JunitUtil.getTestContainer().getProject();
        assertNotNull(_project);

        ValidEmail email = new ValidEmail("junit_test_user@test.com");
        SecurityManager.NewUserStatus status = SecurityManager.addUser(email, null);
        _testUser = status.getUser();
        assertNotNull(_testUser);
    }

    @After
    public void cleanup() throws ValidEmail.InvalidEmailException, SecurityManager.UserManagementException
    {
        Container project = JunitUtil.getTestContainer().getProject();

        for (String groupName : PROJECT_GROUP_NAMES)
        {
            Integer groupId = SecurityManager.getGroupId(project, groupName, false);

            if (null != groupId)
                SecurityManager.deleteGroup(groupId);
        }

        for (String groupName : SITE_GROUP_NAMES)
        {
            Integer groupId = SecurityManager.getGroupId(ContainerManager.getRoot(), groupName, false);

            if (null != groupId)
                SecurityManager.deleteGroup(groupId);
        }

        ValidEmail email = new ValidEmail("junit_test_user@test.com");
        User existingUser = UserManager.getUser(email);
        if (null != existingUser)
            UserManager.deleteUser(existingUser.getUserId());
    }

    @Test
    public void test() throws SQLException, InterruptedException, ValidEmail.InvalidEmailException, SecurityManager.UserManagementException
    {
        final User user = TestContext.get().getUser();

        // Grab the first group (if there is one) in the home project
        Container home = ContainerManager.getHomeContainer();
        List<Group> homeGroups = SecurityManager.getGroups(home, false);
        @Nullable Group homeGroup = homeGroups.size() > 0 ? homeGroups.get(0) : null;

        final Group all = create(ALL);
        Group divA = create(DIV_A);
        final Group divB = create(DIV_B);
        Group divC = create(DIV_C);
        Group coders = create(CODERS);
        Group testers = create(TESTERS);
        Group writers = create(WRITERS);
        Group projectX = create(PROJECT_X);
        final Group cycleTest = create(CONCURRENCY_TEST_GROUP);
        Group siteGroup1 = SecurityManager.createGroup(ContainerManager.getRoot(), SITE_GROUP_1);
        assertTrue(!siteGroup1.isProjectGroup());
        Group siteGroup2 = SecurityManager.createGroup(ContainerManager.getRoot(), SITE_GROUP_2);
        assertTrue(!siteGroup2.isProjectGroup());

        addMember(projectX, user);
        addMember(projectX, _testUser);
        int[] groups = GroupMembershipCache.getGroupMemberships(user.getUserId());
        expected(groups, projectX);
        notExpected(groups, user, all, divB, divC, testers, writers, siteGroup1, coders, divA);

        int[] allGroups = GroupManager.getAllGroupsForPrincipal(user);
        expected(allGroups, projectX, user);
        notExpected(allGroups, divB, divC, testers, writers, siteGroup1, coders, divA, all);

        addMember(all, divA);
        addMember(all, divB);
        addMember(all, divC);
        addMember(all, coders);
        addMember(all, testers);
        addMember(all, writers);

        addMember(coders, user);
        addMember(divA, user);
        addMember(divA, projectX);

        validateGroupMembers(divA, 1, 1, 1, 2, 2, 1);
        validateGroupMembers(divB, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(divC, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(coders, 1, 1, 0, 1, 1, 0);
        validateGroupMembers(testers, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(writers, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(projectX, 2, 2, 0, 2, 2, 0);
        validateGroupMembers(all, 0, 0, 6, 2, 2, 7);

        UserManager.setUserActive(user, _testUser, false);
        _testUser = UserManager.getUser(_testUser.getUserId());  // Refresh the user to update active bit
        assertNotNull(_testUser);
        assertFalse(_testUser.isActive());

        validateGroupMembers(divA, 1, 1, 1, 2, 1, 1);
        validateGroupMembers(divB, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(divC, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(coders, 1, 1, 0, 1, 1, 0);
        validateGroupMembers(testers, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(writers, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(projectX, 2, 1, 0, 2, 1, 0);
        validateGroupMembers(all, 0, 0, 6, 2, 1, 7);

        UserManager.setUserActive(user, _testUser, true);
        _testUser = UserManager.getUser(_testUser.getUserId());  // Refresh the user to update active bit
        assertNotNull(_testUser);
        assertTrue(_testUser.isActive());

        validateGroupMembers(divA, 1, 1, 1, 2, 2, 1);
        validateGroupMembers(divB, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(divC, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(coders, 1, 1, 0, 1, 1, 0);
        validateGroupMembers(testers, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(writers, 0, 0, 0, 0, 0, 0);
        validateGroupMembers(projectX, 2, 2, 0, 2, 2, 0);
        validateGroupMembers(all, 0, 0, 6, 2, 2, 7);

        // TODO: Create another group, add directly to "all", add user to new group, validate
        // TODO: Check permissions

        Group administrators = SecurityManager.getGroup(Group.groupAdministrators);
        Group developers = SecurityManager.getGroup(Group.groupDevelopers);
        Group users = SecurityManager.getGroup(Group.groupUsers);
        Group guests = SecurityManager.getGroup(Group.groupGuests);

        failAddMember(null, user, SecurityManager.NULL_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, null, SecurityManager.NULL_PRINCIPAL_ERROR_MESSAGE);

        failAddMember(testers, testers, SecurityManager.ADD_GROUP_TO_ITSELF_ERROR_MESSAGE);
        failAddMember(administrators, administrators, SecurityManager.ADD_GROUP_TO_ITSELF_ERROR_MESSAGE);
        failAddMember(developers, developers, SecurityManager.ADD_GROUP_TO_ITSELF_ERROR_MESSAGE);

        failAddMember(coders, user, SecurityManager.ALREADY_A_MEMBER_ERROR_MESSAGE);
        failAddMember(all, divA, SecurityManager.ALREADY_A_MEMBER_ERROR_MESSAGE);
        failAddMember(divA, user, SecurityManager.ALREADY_A_MEMBER_ERROR_MESSAGE);

        failAddMember(divA, all, SecurityManager.CIRCULAR_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, all, SecurityManager.CIRCULAR_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, divA, SecurityManager.CIRCULAR_GROUP_ERROR_MESSAGE);

        failAddMember(guests, user, ADD_TO_GUESTS);
        failAddMember(guests, projectX, ADD_TO_GUESTS);
        failAddMember(guests, users, ADD_TO_GUESTS);
        failAddMember(users, user, ADD_TO_USERS);
        failAddMember(users, projectX, ADD_TO_USERS);
        failAddMember(users, guests, ADD_TO_USERS);

        failAddMember(administrators, projectX, SecurityManager.ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(developers, projectX, SecurityManager.ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(administrators, guests, SecurityManager.ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(developers, users, SecurityManager.ADD_TO_SYSTEM_GROUP_ERROR_MESSAGE);

        failAddMember(projectX, administrators, SecurityManager.ADD_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, developers, SecurityManager.ADD_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, users, SecurityManager.ADD_SYSTEM_GROUP_ERROR_MESSAGE);
        failAddMember(projectX, guests, SecurityManager.ADD_SYSTEM_GROUP_ERROR_MESSAGE);

        if (null != homeGroup)
            failAddMember(projectX, homeGroup, SecurityManager.DIFFERENT_PROJECTS_ERROR_MESSAGE);

        // Attempt multiple simultaneous adds of the same member to the same group, see #14795. One of the five threads
        // should succeed and the others should fail, with either an InvalidGroupMemberShipException (which would
        // normally be displayed to the user) or a constraint violation (which is ignored).
        JunitUtil.createRaces(() -> {
            try
            {
                SecurityManager.addMember(divB, user);
            }
            catch (InvalidGroupMembershipException e)
            {
                // This is expected
            }
        }, 5, 1, 60);
        expected(divB, user);
        SecurityManager.deleteMember(divB, user);

        // Test that we protect against concurrent threads making independent group adds that result in a cycle.  We
        // simulate this by adding two groups that result in a cycle, forcing the second add to occur just after the
        // first add, but before the final validation of the first add.
        try
        {
            SecurityManager.addMember(projectX, cycleTest, new Runnable() {
                @Override
                public void run()
                {
                    SecurityManager.addMemberWithoutValidation(cycleTest, all);
                }
            });
            fail("Add member of circular group addition should have throw IllegalStateException");
        }
        catch (InvalidGroupMembershipException e)
        {
            assertEquals(SecurityManager.CIRCULAR_GROUP_ERROR_MESSAGE, e.getMessage());
        }

        notExpected(projectX, cycleTest);
        expected(cycleTest, all);
        SecurityManager.deleteMember(cycleTest, all);
        notExpected(cycleTest, all);
        addMember(projectX, cycleTest);
        addMember(projectX, siteGroup1);
        addMember(siteGroup1, siteGroup2);

        expected(all, divA, divB, divC, coders, testers, writers);
        notExpected(all, cycleTest);

        groups = GroupMembershipCache.getGroupMemberships(user.getUserId());
        expected(groups, projectX, coders, divA);
        notExpected(groups, user, all, divB, divC, testers, writers, siteGroup1);

        allGroups = GroupManager.getAllGroupsForPrincipal(user);
        expected(allGroups, projectX, coders, divA, user, all);
        notExpected(allGroups, divB, divC, testers, writers, siteGroup1);

        List<Group> projectGroups = SecurityManager.getGroups(_project, false);
        String svg = GroupManager.getGroupGraphSvg(projectGroups, user, false);

        // TODO: Assert something about svg... maybe just length of svg?
    }

    private Group create(String name)
    {
        Group group = SecurityManager.createGroup(_project, name);
        assertTrue(group.isProjectGroup());

        return group;
    }

    private void addMember(Group group, UserPrincipal principal)
    {
        try
        {
            SecurityManager.addMember(group, principal);
            expected(group, principal);
        }
        catch (InvalidGroupMembershipException e)
        {
            throw new RuntimeException(e);
        }
    }

    // Adding this principal should fail
    private void failAddMember(@Nullable Group group, @Nullable UserPrincipal principal, String expectedMessage) throws SQLException
    {
        Set<UserPrincipal> members = getMembers(group);

        try
        {
            SecurityManager.addMember(group, principal);
            fail("Expected failure when adding principal \"" + principal + "\" to group \"" + group + "\"");
        }
        catch (InvalidGroupMembershipException e)
        {
            assertEquals(expectedMessage, e.getMessage());
        }

        // Membership should not have changed
        assertEquals(members, getMembers(group));
    }

    private Set<UserPrincipal> getMembers(@Nullable Group group)
    {
        return null != group ? SecurityManager.getGroupMembers(group, MemberType.ALL_GROUPS_AND_USERS) : Collections.emptySet();
    }

    private void expected(int[] actualIds, UserPrincipal... expectedMembers)
    {
        validate(true, actualIds, expectedMembers);
    }

    private void notExpected(int[] actualIds, UserPrincipal... testMembers)
    {
        validate(false, actualIds, testMembers);
    }

    private void expected(Group group, UserPrincipal... expectedMembers)
    {
        validate(true, group, expectedMembers);
    }

    private void notExpected(Group group, UserPrincipal... expectedMembers)
    {
        validate(false, group, expectedMembers);
    }

    private void validate(boolean expected, int[] actualIds, UserPrincipal... testMembers)
    {
        Set<Integer> actual = new HashSet<>(Arrays.asList(ArrayUtils.toObject(actualIds)));

        validate(expected, actual, testMembers);
    }

    private void validate(boolean expected, Set<Integer> actual, UserPrincipal... testMembers)
    {
        for (UserPrincipal member : testMembers)
        {
            if (expected)
                assertTrue("Expected member \"" + member.getName() + "\" (" + member.getUserId() + ") not present in " + actual, actual.contains(member.getUserId()));
            else
                assertFalse("Member \"" + member.getName() + "\" (" + member.getUserId() + ") was found but was not expected in " + actual, actual.contains(member.getUserId()));
        }
    }

    private void validate(boolean expected, Group group, UserPrincipal... members)
    {
        Set<Integer> set = new HashSet<>();

        for (UserPrincipal userPrincipal : getMembers(group))
            set.add(userPrincipal.getUserId());

        validate(expected, set, members);
    }

    private static void validateGroupMembers(Group group, int allUsers, int activeUsers, int groups, int allRecursiveUsers, int activeRecursiveUsers, int recursiveGroups)
    {
        assertEquals(allUsers, SecurityManager.getGroupMembers(group, MemberType.ACTIVE_AND_INACTIVE_USERS).size());
        assertEquals(activeUsers, SecurityManager.getGroupMembers(group, MemberType.ACTIVE_USERS).size());
        assertEquals(groups, SecurityManager.getGroupMembers(group, MemberType.GROUPS).size());
        assertEquals(allUsers + groups, SecurityManager.getGroupMembers(group, MemberType.ALL_GROUPS_AND_USERS).size());

        assertEquals(allRecursiveUsers, SecurityManager.getAllGroupMembers(group, MemberType.ACTIVE_AND_INACTIVE_USERS).size());
        assertEquals(activeRecursiveUsers, SecurityManager.getAllGroupMembers(group, MemberType.ACTIVE_USERS).size());
        assertEquals(recursiveGroups, SecurityManager.getAllGroupMembers(group, MemberType.GROUPS).size());
        assertEquals(allRecursiveUsers + recursiveGroups, SecurityManager.getAllGroupMembers(group, MemberType.ALL_GROUPS_AND_USERS).size());
    }
}
