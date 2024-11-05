package software.amazon.opensearch.proxy.governance;

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
import software.amazon.opensearch.proxy.UnitTestBase;

@ExtendWith(MockitoExtension.class)
public class RejectTimeRangeRuleTest extends UnitTestBase {
    @Test
    void testThatTimeRangeRuleWorks() {
        // given
        String indexRegex = ".*(abc123|something).*";
        String rangeField = "@timestamp";
        String maxTimeRangeMs = "172800000";
        String rejectIfMissing = "true";
        RejectTimeRangeRule rule = new RejectTimeRangeRule(indexRegex, rangeField, maxTimeRangeMs, rejectIfMissing);

        String requestBody = "{\"params\":{\"index\":\"my_testabc123_index*\",\"body\":{\"sort\":[{\"@timestamp\":{\"order\":\"desc\",\"unmapped_type\":\"boolean\"}}],\"size\":500,\"version\":true,\"aggs\":{\"2\":{\"date_histogram\":{\"field\":\"@timestamp\",\"calendar_interval\":\"1h\",\"time_zone\":\"America/New_York\",\"min_doc_count\":1}}},\"stored_fields\":[\"*\"],\"script_fields\":{},\"docvalue_fields\":[{\"field\":\"@timestamp\",\"format\":\"date_time\"},{\"field\":\"endTime\",\"format\":\"date_time\"},{\"field\":\"eventEndTime\",\"format\":\"date_time\"},{\"field\":\"eventStartTime\",\"format\":\"date_time\"},{\"field\":\"kubernetes.annotations.kubectl.kubernetes.io/restartedAt\",\"format\":\"date_time\"},{\"field\":\"log_time_stamp\",\"format\":\"date_time\"},{\"field\":\"startTime\",\"format\":\"date_time\"},{\"field\":\"time\",\"format\":\"date_time\"}],\"_source\":{\"excludes\":[]},\"query\":{\"bool\":{\"must\":[],\"filter\":[{\"match_all\":{}},{\"range\":{\"@timestamp\":{\"gte\":\"2024-09-29T17:26:08.123Z\",\"lte\":\"2024-10-02T17:26:08.123Z\",\"format\":\"strict_date_optional_time\"}}}],\"should\":[],\"must_not\":[]}},\"highlight\":{\"pre_tags\":[\"@opensearch-dashboards-highlighted-field@\"],\"post_tags\":[\"@/opensearch-dashboards-highlighted-field@\"],\"fields\":{\"*\":{}},\"fragment_size\":2147483647}},\"preference\":1727889203117}}";
        ByteBuf requestContent = copiedBuffer(requestBody, CharsetUtil.UTF_8);
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST,
                "/_dashboards/internal/search/opensearch",
                requestContent);

        // when
        GovernanceRuleResult result = rule.evaluate(request);

        // then
        assertThat(result)
                .matches(r -> r.getResultType() == GovernanceRuleResultType.REJECT);
    }

}
