package org.opensearch.trafficgateway.proxy.governance;

import static io.netty.buffer.Unpooled.copiedBuffer;
import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.trafficgateway.proxy.UnitTestBase;

@ExtendWith(MockitoExtension.class)
public class RejectSearchRegexFieldRuleTest extends UnitTestBase {
    @Test
    void testThatRuleRejectsSearchMatchingRegex() {
        // given
        String fieldName = "foo";
        String fieldRegex = "bar.*";
        String indexRegex = "baz.*";
        String responseMessage = "custom response";
        RejectSearchRegexFieldRule rule =
                new RejectSearchRegexFieldRule(fieldName, fieldRegex, indexRegex, responseMessage);
        String requestBody = "{\"foo\": \"bar qux\"}";
        ByteBuf requestContent = copiedBuffer(requestBody, CharsetUtil.UTF_8);
        FullHttpRequest request =
                new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/baz001/_search", requestContent);

        // when
        GovernanceRuleResult result = rule.evaluate(request);

        // then
        assertThat(result)
                .matches(r -> r.getResultType() == GovernanceRuleResultType.REJECT)
                .matches(r -> r.getGovernanceRuleResponse().status().code() == HttpResponseStatus.BAD_REQUEST.code())
                .matches(r ->
                        r.getGovernanceRuleResponse().status().reasonPhrase().equals(responseMessage));
    }

    @Test
    void testThatComplexRegexRuleWorks() {
        // given
        String indexRegex = "my_index.*";
        String fieldName = "query.bool.must[0].query_string.query";
        String fieldRegex = ".*\"[\\w|\\s]*\\*[\\w|\\s]*\\*[\\w|\\s]*\".*";
        RejectSearchRegexFieldRule rule = new RejectSearchRegexFieldRule(fieldName, fieldRegex, indexRegex);
        // TODO: pretty-format this.
        String requestBody =
                "{\"params\": {\"index\": \"my_index*\", \"body\": {\"sort\": [{\"@timestamp\": {\"order\": \"desc\", \"unmapped_type\": \"boolean\"}}], \"size\": 500, \"version\": true, \"aggs\": {\"2\": {\"date_histogram\": {\"field\": \"@timestamp\", \"calendar_interval\": \"1m\", \"time_zone\": \"America/New_York\", \"min_doc_count\": 1}}}, \"stored_fields\": [\"*\"], \"script_fields\": {}, \"docvalue_fields\": [{\"field\": \"@timestamp\", \"format\": \"date_time\"}, {\"field\": \"endTime\", \"format\": \"date_time\"}, {\"field\": \"kubernetes.annotations.kubectl.kubernetes.io/restartedAt\", \"format\": \"date_time\"}, {\"field\": \"log_time_stamp\", \"format\": \"date_time\"}, {\"field\": \"startTime\", \"format\": \"date_time\"}, {\"field\": \"time\", \"format\": \"date_time\"}], \"_source\": {\"excludes\": []}, \"query\": {\"bool\": {\"must\": [{\"query_string\": {\"query\": \" app_name: \\\"cxp-accountmanagement-domain\\\" AND duration: <46.11 AND msg: \\\"*Inbound*\\\"\", \"analyze_wildcard\": true, \"time_zone\": \"America/New_York\"}}], \"filter\": [{\"range\": {\"@timestamp\": {\"gte\": \"2024-04-10T20:15:52.850Z\", \"lte\": \"2024-04-10T21:15:52.850Z\", \"format\": \"strict_date_optional_time\"}}}], \"should\": [], \"must_not\": []}}, \"highlight\": {\"pre_tags\": [\"@opensearch-dashboards-highlighted-field@\"], \"post_tags\": [\"@/opensearch-dashboards-highlighted-field@\"], \"fields\": {\"*\": {}}, \"fragment_size\": 2147483647}}, \"preference\": 1712780976191}}";
        ByteBuf requestContent = copiedBuffer(requestBody, CharsetUtil.UTF_8);
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.POST, "/_dashboards/internal/search/opensearch", requestContent);

        // when
        GovernanceRuleResult result = rule.evaluate(request);

        // then
        assertThat(result).matches(r -> r.getResultType() == GovernanceRuleResultType.REJECT);
    }
}
