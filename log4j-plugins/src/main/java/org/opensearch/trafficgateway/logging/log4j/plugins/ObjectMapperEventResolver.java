package org.opensearch.trafficgateway.logging.log4j.plugins;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.layout.template.json.resolver.EventResolver;
import org.apache.logging.log4j.layout.template.json.util.JsonWriter;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ObjectMessage;
import org.apache.logging.log4j.message.ReusableObjectMessage;

@AllArgsConstructor
public class ObjectMapperEventResolver implements EventResolver {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESOLVER_NAME = "objectMapper";

    private final EventResolver internalResolver;

    static String getName() {
        return RESOLVER_NAME;
    }

    @Override
    public void resolve(LogEvent value, JsonWriter jsonWriter) {
        Message message = value.getMessage();
        Object object;

        if (message instanceof ObjectMessage) {
            object = ((ObjectMessage) message).getParameter();
        } else if (message instanceof ReusableObjectMessage) {
            object = ((ReusableObjectMessage) message).getParameter();
        } else if (message instanceof MutableLogEvent
                && message.getFormat() == null
                && ((MutableLogEvent) message).getParameterCount() == 1) {
            // log.info(object) copies object to first param of MutableLogEvent and sets
            // null messageFormat
            object = ((MutableLogEvent) message).getParameters()[0];
        } else {
            internalResolver.resolve(value, jsonWriter);
            return;
        }

        try {
            Map<String, Object> mappedObject = MAPPER.convertValue(object, new TypeReference<Map<String, Object>>() {
            });
            Message newMessage = new ObjectMessage(mappedObject);
            LogEvent mappedEvent = new Log4jLogEvent.Builder(value).setMessage(newMessage).build();

            internalResolver.resolve(mappedEvent, jsonWriter);
        } catch (IllegalArgumentException e) {
            internalResolver.resolve(value, jsonWriter);
        }
    }
}
