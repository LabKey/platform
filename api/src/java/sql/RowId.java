/*
 * @(#)RowId.java	1.5 06/05/28
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.sql;

public interface RowId
{
    boolean equals(Object obj);

    byte[] getBytes();

    String toString();

    int hashCode();
}


