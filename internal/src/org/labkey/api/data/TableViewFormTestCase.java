/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.security.Group;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

public class TableViewFormTestCase extends Assert
{
    @Test
    public void testBasic()
    {
        TestForm tf = new TestForm();
        tf.setViewContext(HttpView.currentContext());
        TestContext ctx = TestContext.get();

        Assert.assertEquals(ctx.getRequest().getUserPrincipal(), tf.getUser());

        //Test date handling
        tf.set("datetimeNotNull", "2004-06-20");
        Date dt = (Date) tf.getTypedValue("datetimeNotNull");
        Assert.assertTrue("Date get", dt.equals(new Timestamp(DateUtil.parseISODateTime("2004-06-20"))));

        //Should turn empty strings into nulls
        tf.set("text", "");
        Assert.assertNull("Turn string to null", tf.getTypedValue("text"));

        tf.set("bitNull", "1");
        Assert.assertTrue((Boolean) tf.getTypedValue("bitNull"));

        tf.setPkVal(20);
        Assert.assertEquals(tf.get("rowId"), "20");
        Assert.assertEquals(tf.getTypedValue("rowId"), 20);
    }

    @Test
    public void testErrorHandling()
    {
        TestForm tf = new TestForm();
        tf.setViewContext(HttpView.currentContext());

        //Should be invalid because of null fields.
        //BUG: Not differentiating between insert & update cases.
        //Assert.assertTrue("Initial form should not be valid", !tf.isValid());

        //Get the errors
        //ActionErrors errors = tf.validate(null, ctx.getRequest());
        //Assert.assertEquals("3 Non-null fields", errors.size(), 3);
        //Non-nullable fields are named NotNull

        tf.set("datetimeNotNull", "2004-06-20");
        tf.set("bitNotNull", "1");
        tf.set("intNotNull", "20");
        tf.set("datetimeNull", "garbage");

        BindException errors = new NullSafeBindException(tf, "form");
        tf.validateBind(errors);
        Assert.assertEquals("1 error", errors.getErrorCount(), 1);
        Assert.assertEquals("Date conversion error", errors.getFieldErrors("datetimeNull").size(), 1);
        Assert.assertEquals("Date conversion error", errors.getFieldErrors("datetimeNull").get(0).getDefaultMessage(), "Could not convert value: garbage");

        tf.setTypedValue("datetimeNull", new Date("6/20/2004"));
        Assert.assertTrue("Final form should be valid", tf.isValid());
    }


    @Test
    public void testDbOperations() throws SQLException, ServletException
    {
        Container test = JunitUtil.getTestContainer();
        MutableSecurityPolicy policy = new MutableSecurityPolicy(test);
        policy.addRoleAssignment(SecurityManager.getGroup(Group.groupAdministrators), RoleManager.getRole(ProjectAdminRole.class));
        SecurityPolicyManager.savePolicy(policy);

        ViewContext ctx = new ViewContext(HttpView.currentContext());
        ctx.setContainer(test);

        TestForm tf = new TestForm();
        tf.setViewContext(ctx);

        tf.set("datetimeNotNull", "2004-06-20");
        tf.set("bitNotNull", "1");
        tf.set("intNotNull", "20");
        tf.set("datetimeNull", "2004-06-20");
        tf.set("text", "First test record");
        tf.doInsert();

        Assert.assertNotNull(tf.getPkVal());
        Object firstPk = tf.getPkVal();
        Assert.assertEquals(tf.getPkVal(), tf.getTypedValue("rowId"));
        Date createdDate = (Date) tf.getTypedValue("created");

        //Make sure date->string->date comes out right...
        tf.set("datetimeNotNull", tf.get("created"));
        tf.set("text", "Second test record");
        tf.getStrings().remove("rowId");
        tf.doInsert();
        Assert.assertEquals("Date time roundtrip: ", createdDate.getTime(), ((Date) tf.getTypedValue("datetimeNotNull")).getTime());
        tf.doDelete();

        tf.setPkVal(firstPk);
        tf.refreshFromDb();
        Assert.assertEquals("reselect", tf.getTypedValue("text"), "First test record");

        tf.doDelete();
        tf.forceReselect();
        Assert.assertTrue("deleted", 1 == tf.getTypedValues().size());
    }

    public static class TestForm extends TableViewForm
    {
        public TestForm()
        {
            super(TestSchema.getInstance().getTableInfoTestTable());
        }
    }
}

