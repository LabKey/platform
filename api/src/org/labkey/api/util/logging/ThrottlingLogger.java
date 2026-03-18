package org.labkey.api.util.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheManager;

import java.util.concurrent.atomic.AtomicInteger;

public class ThrottlingLogger extends ExtendedLoggerWrapper
{
    private static final Cache<String, AtomicInteger> THROTTLING_CACHE = CacheManager.getBlockingCache(500, CacheManager.HOUR, "Throttle for Loggers", (_, _) -> new AtomicInteger(0));
    private static final Message DUMMY_MESSAGE = new Message()
    {
        @Override
        public String getFormattedMessage()
        {
            return null;
        }

        @Override
        public Object[] getParameters()
        {
            return null;
        }

        @Override
        public Throwable getThrowable()
        {
            return null;
        }
    };

    public ThrottlingLogger(ExtendedLogger logger)
    {
        super(logger, logger.getName(), new ThrottlingMessageFactory(logger.getMessageFactory(), 20));
    }

    @Override
    public void logMessage(final String fqcn, final Level level, final Marker marker, final Message message, final Throwable t)
    {
        if (message != DUMMY_MESSAGE)
            super.logMessage(fqcn, level, marker, message, t);
    }

    private static class ThrottlingMessageFactory implements MessageFactory
    {
        private final MessageFactory _factory;
        private final int _maxBurst;

        private ThrottlingMessageFactory(MessageFactory factory, int maxBurst)
        {
            _factory = factory;
            _maxBurst = maxBurst;
        }

        @Override
        public Message newMessage(Object message)
        {
            String key = message == null ? "null" : message.toString();
            return shouldLog(key) ? _factory.newMessage(message) : DUMMY_MESSAGE;
        }

        @Override
        public Message newMessage(String message)
        {
            return shouldLog(message) ? _factory.newMessage(message) : DUMMY_MESSAGE;
        }

        @Override
        public Message newMessage(String message, Object... params)
        {
            return shouldLog(message) ? _factory.newMessage(message, params) : DUMMY_MESSAGE;
        }

        private boolean shouldLog(String message)
        {
            AtomicInteger count = THROTTLING_CACHE.get(message);

            if (count.intValue() < _maxBurst)
            {
                count.incrementAndGet();
                return true;
            }

            return false;
        }
    }
}
