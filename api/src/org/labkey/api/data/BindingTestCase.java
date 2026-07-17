package org.labkey.api.data;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.data.RecordFactory.MiniUser;
import org.labkey.api.util.DateUtil;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;

import java.util.Date;
import java.util.Map;

public class BindingTestCase extends Assert
{
    @Test
    public void testBinding()
    {
        Date lastLogin = new Date();

        // Provide all parameters
        Map<String, Object> params = Map.of(
            "firstName", "Fred",
            "lastName", "Flintstone",
            "lastLogin", DateUtil.formatIsoDateLongTime(lastLogin),
            "userId", 1009
        );
        String toString = "MiniUser[FIRSTname=Fred, LASTNAME=Flintstone, lastLogin=" + lastLogin + ", UserId=1009]";
        testRecordBinding(params, toString);
        testFormBinding(params, toString);

        // Provide just the primitive parameter; others are nullable
        params = Map.of(
            "userId", 1009
        );
        toString = "MiniUser[FIRSTname=null, LASTNAME=null, lastLogin=null, UserId=1009]";
        testRecordBinding(params, toString);
        testFormBinding(params, toString);

        // No parameters should fail for record due to "userid" primitive. Ensure a reasonable error message.
        testRecordBinding(Map.of(), "Primitive parameter \"UserId\" is required");
        // No parameters should succeed for form class. UserId simply defaults to 0;
        testFormBinding(Map.of(), "MiniUser[FIRSTname=null, LASTNAME=null, lastLogin=null, UserId=0]");

        // Verify message for conversion error
        params = Map.of("UserId", "abc");
        String errorMessage = "Failed to convert property value of type 'java.lang.String' to required type 'int' for property 'UserId'; Could not convert 'abc' to an integer";
        testRecordBinding(params, errorMessage);
        testFormBinding(params, errorMessage);
    }

    private void testRecordBinding(Map<String, Object> map, String expectedToStringOrError)
    {
        PropertyValues pvs = new MutablePropertyValues(map);
        BindException be = BaseViewAction.bindParametersToRecord(MiniUser.class, pvs, "form");

        if (be.hasErrors())
        {
            validateError(be, expectedToStringOrError);
        }
        else
        {
            validateTarget(be.getTarget(), expectedToStringOrError);
        }
    }

    private void testFormBinding(Map<String, Object> map, String expectedToStringOrError)
    {
        PropertyValues pvs = new MutablePropertyValues(map);
        BindException be = BaseViewAction.defaultBindParameters(new MiniUserForm(), "form", pvs);

        if (be.hasErrors())
        {
            validateError(be, expectedToStringOrError);
        }
        else
        {
            validateTarget(be.getTarget(), expectedToStringOrError);
        }
    }

    private void validateError(BindException be, String expectedErrorMessage)
    {
        ObjectError error = be.getAllErrors().getFirst();
        assertNotNull(error);
        assertEquals(expectedErrorMessage, error.getDefaultMessage());
    }

    private void validateTarget(Object user, String expectedToString)
    {
        assertNotNull(user);
        assertEquals(expectedToString, user.toString());
    }

    // Simple test form
    @SuppressWarnings("unused")
    private static class MiniUserForm
    {
        String _firstName;
        String _lastName;
        Date _lastLogin;
        int _userId;

        public String getFirstName()
        {
            return _firstName;
        }

        public void setFirstName(String firstName)
        {
            _firstName = firstName;
        }

        public String getLastName()
        {
            return _lastName;
        }

        public void setLastName(String lastName)
        {
            _lastName = lastName;
        }

        public Date getLastLogin()
        {
            return _lastLogin;
        }

        public void setLastLogin(Date lastLogin)
        {
            _lastLogin = lastLogin;
        }

        public int getUserId()
        {
            return _userId;
        }

        public void setUserId(int userId)
        {
            _userId = userId;
        }

        @Override
        public String toString()
        {
            // No useful default toString(), so emulate the standard record toString(). That's good enough to verify
            // that the parameters were bound correctly.
            return "MiniUser[FIRSTname=" + _firstName + ", LASTNAME=" + _lastName + ", lastLogin=" + _lastLogin + ", UserId=" + _userId + "]";
        }
    }
}
