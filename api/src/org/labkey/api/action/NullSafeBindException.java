/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.action;

import org.labkey.api.util.ExceptionUtil;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

/**
 * Avoids the dreaded "NoSuchMessageException: No message found under code 'null' for locale 'en_US'."
 * by checking the parameters of the BindException methods.
 *
 * If nulls are passed as a 'defaultMessage' parameter an error is
 * thrown at render time by Spring making it difficult to track down where
 * the null error message was generated.
 *
 * See issue 10432.
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
        for (ObjectError error : errors.getAllErrors())
            checkError(error);
    }

}
