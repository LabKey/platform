package org.labkey.api.data;

import java.util.List;

public record NameExpressionValidationResult(List<String> errors, List<String> warnings, List<String> previews)
{
    @Override
    public boolean equals(Object obj)
    {
        if (obj == null || obj.getClass() != getClass())
            return false;

        NameExpressionValidationResult other = (NameExpressionValidationResult)obj;

        return arrayEqual(errors, other.errors) && arrayEqual(warnings, other.warnings) && arrayEqual(previews, other.previews);
    }

    private boolean arrayEqual(List<String> a, List<String> b)
    {
        if (a == null && b == null)
            return true;
        else if (a == null || b == null)
            return false;
        return a.equals(b);
    }
}
