package org.labkey.api.exp;

import java.util.Collection;

/**
 * User: jeckels
 * Date: Dec 7, 2005
 */
public interface Handler<HandledType>
{
    public enum Priority
    {
        LOW, MEDIUM, HIGH;

        public static <H extends Handler<V>, V> H findBestHandler(Collection<H> handlers, V value)
        {
            H bestHandler = null;
            Handler.Priority bestPriority = null;
            for (H handler : handlers)
            {
                Handler.Priority priority = handler.getPriority(value);
                if (priority != null)
                {
                    if (bestPriority == null || bestPriority.compareTo(priority) < 0)
                    {
                        bestHandler = handler;
                        bestPriority = priority;
                    }
                }
            }
            return bestHandler;
        }
    }

    /** @return null if this handler cannot handle the object */ 
    public Priority getPriority(HandledType object);
}
