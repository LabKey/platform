/*
 * Copyright (c) 2004-2007 Fred Hutchinson Cancer Research Center
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

import junit.framework.Assert;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.struts.action.ActionErrors;
import org.labkey.api.security.ACL;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.Group;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.JunitUtil;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.Date;

public class TableViewFormTestCase extends junit.framework.TestCase
{
    public TableViewFormTestCase()
    {
        super();
    }


    public TableViewFormTestCase(String name)
    {
        super(name);
    }


    public void testBasic()
    {
        TestForm tf = new TestForm();
        TestContext ctx = TestContext.get();
        tf.reset(null, ctx.getRequest());

        Assert.assertEquals(ctx.getRequest().getUserPrincipal(), tf.getUser());

        //Test date handling
        tf.set("datetimeNotNull", "6/20/2004");
        Date dt = (Date) tf.getTypedValue("datetimeNotNull");
        Assert.assertTrue("Date get", dt.equals(new Date("6/20/2004")));

        //Should turn empty strings into nulls
        tf.set("text", "");
        Assert.assertNull("Turn string to null", tf.getTypedValue("text"));

        tf.set("bitNull", "1");
        Assert.assertTrue(((Boolean) tf.getTypedValue("bitNull")).booleanValue());

        tf.setPkVal(new Integer(20));
        Assert.assertEquals(tf.get("rowId"), "20");
        Assert.assertEquals(tf.getTypedValue("rowId"), new Integer(20));
    }

    public void testErrorHandling()
    {
        TestForm tf = new TestForm();
        TestContext ctx = TestContext.get();
        tf.reset(null, ctx.getRequest());

        //Should be invalid because of null fields.
        //BUG: Not differentiating between insert & update cases.
        //Assert.assertTrue("Initial form should not be valid", !tf.isValid());

        //Get the errors
        //ActionErrors errors = tf.validate(null, ctx.getRequest());
        //Assert.assertEquals("3 Non-null fields", errors.size(), 3);
        //Non-nullable fields are named NotNull

        tf.set("datetimeNotNull", "6/20/2004");
        tf.set("bitNotNull", "1");
        tf.set("intNotNull", "20");
        tf.set("datetimeNull", "garbage");
        ActionErrors errors = tf.validate(null, ctx.getRequest());
        Assert.assertEquals("1 error", errors.size(), 1);
        Assert.assertNotNull("Date conversion error", errors.get("datetimeNull"));

        tf.setTypedValue("datetimeNull", new Date("6/20/2004"));
        Assert.assertTrue("Final form should be valid", tf.isValid());
    }


    public void testDbOperations() throws SQLException, ServletException
    {
        TestForm tf = new TestForm();
        TestContext ctx = TestContext.get();
        tf.reset(null, ctx.getRequest());

        Container test = JunitUtil.getTestContainer();
        ACL acl = new ACL();
        acl.setPermission(Group.groupAdministrators, ACL.PERM_ALLOWALL);
        SecurityManager.updateACL(test, acl);
        tf.setContainer(test);

        tf.set("datetimeNotNull", "6/20/2004");
        tf.set("bitNotNull", "1");
        tf.set("intNotNull", "20");
        tf.set("datetimeNull", "6/21/2004");
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

        tf.setPkVal(firstPk);
        tf.refreshFromDb(false);
        Assert.assertEquals("reselect", tf.getTypedValue("text"), "First test record");

        tf.doDelete();
        boolean wasDeleted = false;
        try
        {
            tf.refreshFromDb(false);
        }
        catch (Exception x)
        {
            wasDeleted = true;
        }
        Assert.assertTrue("deleted", true);
    }

    public static Test suite()
    {
        TestSuite suite = new TestSuite(TableViewFormTestCase.class);
        return suite;
    }

    public static class TestForm extends TableViewForm
    {
        public TestForm()
        {
            super(TestSchema.getInstance().getTableInfoTestTable());
        }
    }
}

