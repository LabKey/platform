/*
 * Copyright (c) 2016 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.api.compliance;

import org.labkey.api.data.ColumnInfo;

import java.util.function.Predicate;

/**
 * Convenience sub-interface for filtering ColumnInfos based on some criteria
 * Created by adam on 1/17/2016
 */
public interface ColumnInfoFilter extends Predicate<ColumnInfo>
{
}
