package org.labkey.api.action;

import org.labkey.api.util.ExceptionUtil;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import java.util.List;

/**
 * Avoids the dreaded "NoSuchMessageException: No message found under code 'null' for locale 'en_US'."
 * by checking the parameters of the BindException methods.
 *
 * If nulls are passed as a 'defaultMessage' parameter an error is
 * thrown at render time by Spring making it difficult to track down where
 * the null error message was generated.
 *
 * See 10432.
 */
public class NullSafeBindException extends org.springframework.validation.BindException
{
    public NullSafeBindException(BindingResult bindingResult)
    {
        super(bindingResult);
        checkErrors(bindingResult);
    }

    public NullSafeBindException(Object target, String objectName)
    {
        super(target, objectName);
    }

    @Override
    public void reject(String errorCode)
    {
        if (errorCode == null)
        {
            err("Please supply an errorCode");
            errorCode = SpringActionController.ERROR_GENERIC;
        }
        super.reject(errorCode);
    }

    @Override
    public void reject(String errorCode, String defaultMessage)
    {
        if (errorCode == null && defaultMessage == null)
        {
            err("Please supply an errorCode or a defaultMessage");
            errorCode = SpringActionController.ERROR_GENERIC;
        }
        super.reject(errorCode, defaultMessage);
    }

    @Override
    public void reject(String errorCode, Object[] errorArgs, String defaultMessage)
    {
        if (errorCode == null && defaultMessage == null)
        {
            err("Please supply an errorCode or a defaultMessage");
            errorCode = SpringActionController.ERROR_GENERIC;
        }
        super.reject(errorCode, errorArgs, defaultMessage);
    }

    @Override
    public void rejectValue(String field, String errorCode)
    {
        if (field == null || errorCode == null)
        {
            err("Please supply a field and an errorCode");
            errorCode = SpringActionController.ERROR_GENERIC;
        }
        super.rejectValue(field, errorCode);
    }

    @Override
    public void rejectValue(String field, String errorCode, String defaultMessage)
    {
        if (field == null || (errorCode == null && defaultMessage == null))
        {
            err("Please supply a field and an errorCode or a defaultMessage");
            if (errorCode == null)
                errorCode = SpringActionController.ERROR_GENERIC;
        }
        super.rejectValue(field, errorCode, defaultMessage);
    }

    @Override
    public void rejectValue(String field, String errorCode, Object[] errorArgs, String defaultMessage)
    {
        if (field == null || (errorCode == null && defaultMessage == null))
        {
            err("Please supply a field and an errorCode or a defaultMessage");
            if (errorCode == null)
                errorCode = SpringActionController.ERROR_GENERIC;
        }
        super.rejectValue(field, errorCode, errorArgs, defaultMessage);
    }

    @Override
    public void addError(ObjectError error)
    {
        checkError(error);
        super.addError(error);
    }

    @Override
    public void addAllErrors(Errors errors)
    {
        checkErrors(errors);
        super.addAllErrors(errors);
    }

    private static void err(String message)
    {
        assert false : message;
        ExceptionUtil.logExceptionToMothership(null, new Exception(message));
    }

    private static void checkError(ObjectError error)
    {
        if (error.getCode() == null && error.getDefaultMessage() == null)
            err("Please supply an errorCode or a defaultMessage");
    }

    private static void checkErrors(Errors errors)
    {
        for (ObjectError error : (List<ObjectError>)errors.getAllErrors())
            checkError(error);
    }

}
