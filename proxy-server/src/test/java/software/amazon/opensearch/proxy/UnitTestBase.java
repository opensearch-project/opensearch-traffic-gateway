package software.amazon.opensearch.proxy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import software.amazon.logging.log4j.plugins.TestAppender;

public class UnitTestBase {
    protected TestAppender testLoggingAppender;

    @BeforeEach
    void initTestBase() {
        // get reference to test appender for assertions on logged objects
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        testLoggingAppender = (TestAppender) config.getAppender("TestAppender");
    }

    @AfterEach
    void cleanupTestBase() {
        // clear messages
        testLoggingAppender.getMessages().clear();
    }
}
