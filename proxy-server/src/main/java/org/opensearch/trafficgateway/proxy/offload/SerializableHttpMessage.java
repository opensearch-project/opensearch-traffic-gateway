package org.opensearch.trafficgateway.proxy.offload;

import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
public class SerializableHttpMessage {
    static enum RequestType {
        REQUEST,
        RESPONSE
    }

    RequestType requestType;
    String requestId;
    String userId;
    String userToken;
    long timestamp;
    String method;
    String path;
    Map<String, List<String>> queryParams;
    List<Map.Entry<String, String>> headers;
    Integer responseCode;
    String responseReason;
    String body;
}
