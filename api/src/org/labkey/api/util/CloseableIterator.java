package org.labkey.api.util;

import java.util.Iterator;
import java.io.Closeable;

/**
 * User: adam
 * Date: Apr 8, 2009
 * Time: 7:54:30 PM
 */
public interface CloseableIterator<K> extends Closeable, Iterator<K>
{
}
