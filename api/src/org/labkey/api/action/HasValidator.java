package org.labkey.api.action;

import org.springframework.validation.Errors;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Mar 24, 2008
 */
public interface HasValidator
{
    void validateSpring(Errors errors);
}
