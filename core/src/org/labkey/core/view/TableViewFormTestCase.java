/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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

package org.labkey.core.view;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.data.TestSchema;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.TestContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TableViewFormTestCase extends Assert
{
    @Test
    public void testBasic()
    {
        TestForm tf = new TestForm();
        TestContext ctx = TestContext.get();

        Assert.assertEquals(ctx.getRequest().getUserPrincipal(), tf.getUser());

        //Test date handling
        tf.setValueToBind("datetimeNotNull", "2004-06-20");
        Date dt = (Date) tf.getTypedValue("datetimeNotNull");
        Assert.assertEquals("Date get", dt, new Timestamp(DateUtil.parseISODateTime("2004-06-20")));

        //Should turn empty strings into nulls
        tf.setValueToBind("text", "");
        Assert.assertNull("Turn string to null", tf.getTypedValue("text"));

        tf.setValueToBind("bitNull", "1");
        Assert.assertTrue((Boolean) tf.getTypedValue("bitNull"));

        tf.setPkVal(20);
        Assert.assertEquals("20", tf.getAsString("rowId"));
        Assert.assertEquals(20, tf.getTypedValue("rowId"));
    }

    @SuppressWarnings("unused")
    public static class BindBean
    {
        private String[] strArray;
        private Boolean boolValue;

        public String[] getStrArray()
        {
            return strArray;
        }

        public void setStrArray(String[] strArray)
        {
            this.strArray = strArray;
        }

        public Boolean getBoolValue()
        {
            return boolValue;
        }

        public void setBoolValue(Boolean boolValue)
        {
            this.boolValue = boolValue;
        }
    }

    @Test
    public void testArray()
    {
        // actually using a BeanVieForm because SQL Server
        BeanViewForm<BindBean> form;
        MutablePropertyValues mpv;
        Map<String,Object> typed;
        String[] strArray;

        // test comma parsing
        form = new BeanViewForm<>(BindBean.class, null);
        mpv = new MutablePropertyValues();
        mpv.add("strArray", "Option 1, Option 2");
        form.bindParameters(mpv);
        typed = form.getTypedValues();
        assertEquals(1, typed.size());
        assertTrue(typed.get("strArray") instanceof String[]);
        strArray = (String[]) typed.get("strArray");
        assertEquals(2, strArray.length);
        assertEquals("Option 1", strArray[0]);
        assertEquals("Option 2", strArray[1]);

        // test <select multi>
        form = new BeanViewForm<>(BindBean.class, null);
        mpv = new MutablePropertyValues();
        mpv.add("strArray", new String[] {"Option 1", "Option 2"});
        form.bindParameters(mpv);
        typed = form.getTypedValues();
        assertEquals(1, typed.size());
        assertTrue(typed.get("strArray") instanceof String[]);
        strArray = (String[]) typed.get("strArray");
        assertEquals(2, strArray.length);
        assertEquals("Option 1", strArray[0]);
        assertEquals("Option 2", strArray[1]);

        // test <select multi> disambiguate one select
        // this is explicitly an array of size 1, so no parsing
        form = new BeanViewForm<>(BindBean.class, null);
        mpv = new MutablePropertyValues();
        mpv.add("[]strArray", "Option 1, Option 2");
        form.bindParameters(mpv);
        typed = form.getTypedValues();
        assertEquals(1, typed.size());
        assertTrue(typed.get("strArray") instanceof String[]);
        strArray = (String[]) typed.get("strArray");
        assertEquals(1, strArray.length);
        assertEquals("Option 1, Option 2", strArray[0]);
    }


    @Test
    public void testBoolean()
    {
        TestForm form;
        MutablePropertyValues mpv;
        Map<String,Object> typed;

        form = new TestForm();
        mpv = new MutablePropertyValues();
        mpv.add("other", "1");
        form.bindParameters(mpv);
        typed = form.getTypedValues();
        assertFalse(typed.containsKey("bitNull"));

        form = new TestForm();
        mpv = new MutablePropertyValues();
        mpv.add("bitNull", "1");
        form.bindParameters(mpv);
        typed = form.getTypedValues();
        assertEquals(1, typed.size());
        assertTrue(typed.get("bitNull") instanceof Boolean);
        assertTrue((Boolean) typed.get("bitNull"));

        form = new TestForm();
        mpv = new MutablePropertyValues();
        mpv.add("bitNull", "0");
        form.bindParameters(mpv);
        typed = form.getTypedValues();
        assertEquals(1, typed.size());
        assertTrue(typed.get("bitNull") instanceof Boolean);
        assertFalse((Boolean) typed.get("bitNull"));

        form = new TestForm();
        mpv = new MutablePropertyValues();
        mpv.add(SpringActionController.FIELD_MARKER+"bitNull", "1");
        form.bindParameters(mpv);
        typed = form.getTypedValues();
        assertEquals(1, typed.size());
        assertTrue(typed.get("bitNull") instanceof Boolean);
        assertFalse((Boolean) typed.get("bitNull"));

        form = new TestForm();
        mpv = new MutablePropertyValues();
        mpv.add(SpringActionController.FIELD_MARKER+"bitNull", "1");
        mpv.add("bitNull", "1");
        form.bindParameters(mpv);
        typed = form.getTypedValues();
        assertEquals(1, typed.size());
        assertTrue(typed.get("bitNull") instanceof Boolean);
        assertTrue((Boolean) typed.get("bitNull"));
    }



    @Test
    public void testErrorHandling()
    {
        TestForm tf = new TestForm();

        //Should be invalid because of null fields.
        //BUG: Not differentiating between insert & update cases.
        //Assert.assertTrue("Initial form should not be valid", !tf.isValid());

        //Get the errors
        //ActionErrors errors = tf.validate(null, ctx.getRequest());
        //Assert.assertEquals("3 Non-null fields", errors.size(), 3);
        //Non-nullable fields are named NotNull

        tf.setValueToBind("datetimeNotNull", "2004-06-20");
        tf.setValueToBind("bitNotNull", "1");
        tf.setValueToBind("intNotNull", "20");
        tf.setValueToBind("datetimeNull", "garbage");

        BindException errors = new NullSafeBindException(tf, "form");
        tf.validateBind(errors);
        Assert.assertEquals("1 error", 1, errors.getErrorCount());
        Assert.assertEquals("Date conversion error", 1, errors.getFieldErrors("datetimeNull").size());
        Assert.assertEquals("Date conversion error", "Could not convert value: garbage", errors.getFieldErrors("datetimeNull").getFirst().getDefaultMessage());

        tf.setTypedValue("datetimeNull", new Date("6/20/2004"));
        Assert.assertTrue("Final form should be valid", tf.isValid());
    }


    @Test
    public void testDbOperations() throws SQLException
    {
        Container test = JunitUtil.getTestContainer();

        ViewContext ctx = new ViewContext(Objects.requireNonNull(HttpView.currentContext()));
        ctx.setContainer(test);

        TestForm tf = new TestForm();
        tf.setValueToBind("datetimeNotNull", "2004-06-20");
        tf.setValueToBind("bitNotNull", "1");
        tf.setValueToBind("intNotNull", "20");
        tf.setValueToBind("datetimeNull", "2004-06-20");
        tf.setValueToBind("text", "First test record");
        tf.doInsert();

        Assert.assertNotNull(tf.getPkVal());
        Object firstPk = tf.getPkVal();
        Assert.assertEquals(tf.getPkVal(), tf.getTypedValue("rowId"));
        Date createdDate = (Date) tf.getTypedValue("created");

        //Make sure date->string->date comes out right...
        Map<String,Object> copy = new HashMap<>(tf.getValuesToBind());
        copy.remove("rowId");
        copy.put("datetimeNotNull", tf.getAsString("created"));
        copy.put("text", "Second test record");
        tf = new TestForm();
        tf.setValuesToBind(copy);
        tf.doInsert();
        Assert.assertEquals("Date time roundtrip: ", createdDate.getTime(), ((Date) tf.getTypedValue("datetimeNotNull")).getTime());
        tf.doDelete();

        tf.setPkVal(firstPk);
        tf.refreshFromDb();
        Assert.assertEquals("reselect", "First test record", tf.getTypedValue("text"));

        tf.doDelete();
        tf.forceReselect();
        Assert.assertEquals("deleted", 1, tf.getTypedValues().size());
    }

    public static class TestForm extends TableViewForm
    {
        public TestForm()
        {
            super(TestSchema.getInstance().getTableInfoTestTable());
            setViewContext(Objects.requireNonNull(HttpView.currentContext()));
        }
    }
}

