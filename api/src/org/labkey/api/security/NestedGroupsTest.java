package org.labkey.api.security;

import org.apache.commons.lang.ArrayUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
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
    private static final String DEVELOPERS = "Developers";
    private static final String TESTERS = "Testers";
    private static final String WRITERS = "Writers";
    private static final String PROJECT_X = "Project X";

    private static final String ADDING_GROUP_TO_ITSELF_MESSAGE = "Can't add a group to itself";
    private static final String ALREADY_A_MEMBER_MESSAGE = "Principal is already a member of this group";
    private static final String CIRCULAR_MESSAGE = "Can't add a group that results in a circular group relation";

    private static final String[] GROUP_NAMES = new String[] {ALL, DIV_A, DIV_B, DIV_C, DEVELOPERS, TESTERS, WRITERS, PROJECT_X};

    private Container _project;

    @Before
    public void setUp()
    {
        cleanup();
        _project = JunitUtil.getTestContainer().getProject();
    }

    @Test
    public void test() throws SQLException
    {
        User user = TestContext.get().getUser();

        Group all = create(ALL);
        Group divA = create(DIV_A);
        Group divB = create(DIV_B);
        Group divC = create(DIV_C);
        Group developers = create(DEVELOPERS);
        Group testers = create(TESTERS);
        Group writers = create(WRITERS);
        Group projectX = create(PROJECT_X);

        addMember(all, divA);
        addMember(all, divB);
        addMember(all, divC);
        addMember(all, developers);
        addMember(all, testers);
        addMember(all, writers);

        addMember(developers, user);
        addMember(divA, user);
        addMember(divA, projectX);
        addMember(projectX, user);

        expected(all, divA, divB, divC, developers, testers, writers);

        int[] groups = GroupMembershipCache.getGroupsForPrincipal(user.getUserId());
        expected(groups, projectX, developers, divA);
        notExpected(groups, user, all, divB, divC, testers, writers);

        int[] allGroups = GroupManager.getAllGroupsForPrincipal(user);
        expected(allGroups, projectX, developers, divA, user, all);
        notExpected(allGroups, divB, divC, testers, writers);

        failAddMember(testers, testers, ADDING_GROUP_TO_ITSELF_MESSAGE);

        failAddMember(developers, user, ALREADY_A_MEMBER_MESSAGE);
        failAddMember(all, divA, ALREADY_A_MEMBER_MESSAGE);
        failAddMember(divA, user, ALREADY_A_MEMBER_MESSAGE);

        failAddMember(divA, all, CIRCULAR_MESSAGE);
        failAddMember(projectX, all, CIRCULAR_MESSAGE);
        failAddMember(projectX, divA, CIRCULAR_MESSAGE);

        // TODO: Create another group, add directly to "all", add user to new group, validate
    }

    private Group create(String name)
    {
        return SecurityManager.createGroup(_project, name);
    }

    private void addMember(Group group, UserPrincipal principal)
    {
        SecurityManager.addMember(group, principal);
    }

    // Adding this principal should fail
    private void failAddMember(Group group, UserPrincipal principal, String expectedMessage) throws SQLException
    {
        // Does the principal already exist in this group?  We'll use this to verify no change below.
        boolean isMember = SecurityManager.getGroupMembers(group, SecurityManager.GroupMemberType.Both).contains(principal);

        try
        {
            SecurityManager.addMember(group, principal);
            assertTrue("Expected failure when adding principal \"" + principal.getName() + "\" to group \"" + group.getName() + "\"", false);
        }
        catch (IllegalStateException e)
        {
            assertEquals(expectedMessage, e.getMessage());
        }

        // We expect no change in membership
        if (isMember)
            expected(group, principal);
        else
            notExpected(group, principal);
    }

    private void expected(int[] actualIds, UserPrincipal... expectedMembers)
    {
        validate(true, actualIds, expectedMembers);
    }

    private void notExpected(int[] actualIds, UserPrincipal... testMembers)
    {
        validate(false, actualIds, testMembers);
    }

    private void expected(Group group, UserPrincipal... expectedMembers) throws SQLException
    {
        validate(true, group, expectedMembers);
    }

    private void notExpected(Group group, UserPrincipal... expectedMembers) throws SQLException
    {
        validate(false, group, expectedMembers);
    }

    private void validate(boolean expected, int[] actualIds, UserPrincipal... testMembers)
    {
        Set<Integer> actual = new HashSet<Integer>(Arrays.asList(ArrayUtils.toObject(actualIds)));

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
        Set<Integer> set = new HashSet<Integer>();

        for (UserPrincipal userPrincipal : SecurityManager.getGroupMembers(group, SecurityManager.GroupMemberType.Both))
            set.add(userPrincipal.getUserId());

        validate(expected, set, members);
    }

    private void cleanup()
    {
        Container project = JunitUtil.getTestContainer().getProject();

        for (String groupName : GROUP_NAMES)
        {
            Integer groupId = SecurityManager.getGroupId(project, groupName, false);

            if (null != groupId)
                SecurityManager.deleteGroup(groupId);
        }
    }
}
