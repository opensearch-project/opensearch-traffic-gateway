package software.amazon.logging.log4j.plugins;

import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.layout.template.json.resolver.EventResolver;
import org.apache.logging.log4j.layout.template.json.resolver.EventResolverContext;
import org.apache.logging.log4j.layout.template.json.resolver.EventResolverFactory;
import org.apache.logging.log4j.layout.template.json.resolver.MessageResolverFactory;
import org.apache.logging.log4j.layout.template.json.resolver.TemplateResolverConfig;
import org.apache.logging.log4j.layout.template.json.resolver.TemplateResolverFactory;

@Plugin(name = "ObjectMapperEventResolverFactory", category = TemplateResolverFactory.CATEGORY)
public class ObjectMapperEventResolverFactory implements EventResolverFactory {
    private static final ObjectMapperEventResolverFactory INSTANCE = new ObjectMapperEventResolverFactory();

    private ObjectMapperEventResolverFactory() {
    }

    @PluginFactory
    public static ObjectMapperEventResolverFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public String getName() {
        return ObjectMapperEventResolver.getName();
    }

    @Override
    public ObjectMapperEventResolver create(EventResolverContext context, TemplateResolverConfig config) {
        EventResolver internalResolver = MessageResolverFactory.getInstance().create(context, config);
        return new ObjectMapperEventResolver(internalResolver);
    }
}
