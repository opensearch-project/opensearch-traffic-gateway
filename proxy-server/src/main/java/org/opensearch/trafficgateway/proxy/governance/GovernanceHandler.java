package org.opensearch.trafficgateway.proxy.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class GovernanceHandler extends ChannelInboundHandlerAdapter {
    private static final String BYPASS_KEY_KEYWORD = "bypassKey";
    private final EmbeddedChannel requestProcessingChannel;
    private final EmbeddedChannel modifiedRequestProcessingChannel;

    private final GovernanceRuleHandler governanceRuleHandler;

    public GovernanceHandler(String bypassKey, boolean disableAllGovernanceRules, GovernanceRule... rules) {
        modifiedRequestProcessingChannel = new EmbeddedChannel(new HttpRequestEncoder());
        governanceRuleHandler = new GovernanceRuleHandler(
                bypassKey, disableAllGovernanceRules, modifiedRequestProcessingChannel, rules);
        requestProcessingChannel = new EmbeddedChannel(
                new HttpRequestDecoder(),
                new HttpResponseEncoder(),
                new HttpObjectAggregator(2147483647),
                governanceRuleHandler);
    }

    static class GovernanceRuleHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

        private final GovernanceRule[] rules;
        private String bypassKey;
        private boolean disableAllGovernanceRules;

        @Getter
        private boolean requestRejected;

        private final EmbeddedChannel modifiedRequestProcessingChannel;

        GovernanceRuleHandler(
                String bypassKey,
                boolean disableAllGovernanceRules,
                EmbeddedChannel modifiedRequestProcessingChannel,
                GovernanceRule... rules) {
            this.rules = rules;
            this.bypassKey = bypassKey;
            this.disableAllGovernanceRules = disableAllGovernanceRules;
            requestRejected = false;
            this.modifiedRequestProcessingChannel = modifiedRequestProcessingChannel;
        }

        protected String getRequestBypassKey(JsonNode jsonBody) {
            if (jsonBody != null && jsonBody.has(BYPASS_KEY_KEYWORD)) {
                JsonNode bypassNode = jsonBody.get(BYPASS_KEY_KEYWORD);
                if (bypassNode.isValueNode()) {
                    return bypassNode.asText();
                }
            }

            return null;
        }

        /**
         * Removes illegal contents from body such as the request bypass key which would
         * cause OpenSearch to fail.
         *
         * @param jsonBody
         * @param msg
         * @return
         */
        protected void reformatRequestBody(JsonNode jsonBody, FullHttpRequest msg) {
            if (jsonBody != null && jsonBody.has(BYPASS_KEY_KEYWORD)) {
                ((ObjectNode) jsonBody).remove(BYPASS_KEY_KEYWORD);
                ByteBuf modifiedContent = Unpooled.copiedBuffer(jsonBody.toString(), CharsetUtil.UTF_8);
                msg.content().clear().writeBytes(modifiedContent);
                msg.headers().set(HttpHeaderNames.CONTENT_LENGTH, msg.content().readableBytes());
            }
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            String requestBody = msg.content().toString(CharsetUtil.UTF_8);
            // if the content is empty string "" or null then the fromJson returns null.
            JsonNode jsonBody;
            try {
                jsonBody = JSON_MAPPER.readTree(requestBody);
            } catch (JsonProcessingException e) {
                jsonBody = null;
            }

            String requestBypassKey = getRequestBypassKey(jsonBody);
            reformatRequestBody(jsonBody, msg);
            modifiedRequestProcessingChannel.writeOutbound(msg.retainedDuplicate());

            if (disableAllGovernanceRules) {
                return;
            }

            if (requestBypassKey != null && requestBypassKey.equals(bypassKey)) {
                return;
            }

            log.debug("Got FullHttpRequest for path: {}", () -> msg.uri());
            for (GovernanceRule rule : rules) {
                log.debug("Evaluating rule: {}", () -> rule.getClass().getSimpleName());
                GovernanceRuleResult ruleResult = rule.evaluate(msg);

                switch (ruleResult.getResultType()) {
                    case PASS:
                        log.atDebug().log("Request passed rule.");
                        continue;
                    case REJECT:
                        FullHttpResponse ruleResponse = ruleResult.getGovernanceRuleResponse();
                        assert ruleResponse != null;
                        log.debug("Request rejected by rule with status: {}", () -> ruleResponse.status());
                        requestRejected = true;
                        ctx.writeAndFlush(ruleResponse).addListener(ChannelFutureListener.CLOSE);
                        return;
                    default:
                        throw new IllegalStateException(
                                "Unknown GovernanceRuleResultType: " + ruleResult.getResultType());
                }
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        log.atDebug().log("Received message. Sending to embedded channel for processing.");
        requestProcessingChannel.writeInbound(((ByteBuf) msg).retainedDuplicate());

        log.debug(
                "Writing outbound messages from embedded channel. {} messages available.",
                () -> requestProcessingChannel.outboundMessages().size());

        for (Object outboundMsg = requestProcessingChannel
                .readOutbound(); outboundMsg != null; outboundMsg = requestProcessingChannel.readOutbound()) {
            ctx.pipeline().write(outboundMsg);
        }
        ctx.pipeline().flush();

        requestProcessingChannel.releaseInbound();

        if (governanceRuleHandler.requestRejected) {
            log.debug("Request rejected by governance handlers, closing channel.");
            // Close channel because there may be a partially sent request sitting on the
            // target server.
            // Otherwise the next request sent would fail.
            ctx.close();
        } else {
            log.debug("Sending request to next handler.");

            // retrieve the full request if partial is retrieved
            if (modifiedRequestProcessingChannel.outboundMessages().isEmpty()) {
                ctx.channel().read();
            }
            // The encoder was splitting the FullHttpRequest into two parts
            // And we were only passing along the request content so we pass it directly
            // from the embedded channel in order
            for (Object modifiedMsg = modifiedRequestProcessingChannel
                    .readOutbound(); modifiedMsg != null; modifiedMsg = modifiedRequestProcessingChannel
                            .readOutbound()) {
                if (modifiedMsg instanceof ByteBuf) {
                    super.channelRead(ctx, modifiedMsg);
                }
            }
        }
    }
}
