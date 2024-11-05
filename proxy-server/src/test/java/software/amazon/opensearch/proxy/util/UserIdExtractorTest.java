package software.amazon.opensearch.proxy.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import java.io.IOException;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.xpath.XPathExpressionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xml.sax.SAXException;
import software.amazon.opensearch.proxy.UnitTestBase;

@ExtendWith(MockitoExtension.class)
public class UserIdExtractorTest extends UnitTestBase {
    private static final String VALID_XML_RESOURCE_PATH = "/offload/valid-saml-response.xml";
    private static final String TEST_USER_ID = "user@example.com";

    @Test
    void testThatGetUserIdFromSAMLXMLGetsUserId()
            throws IOException, XMLStreamException, XPathExpressionException, SAXException,
                    ParserConfigurationException {
        // given
        byte[] samlXmlBytes =
                this.getClass().getResourceAsStream(VALID_XML_RESOURCE_PATH).readAllBytes();
        UserIdExtractor extractor = new UserIdExtractor();

        // when
        String userId = extractor.getUserIdFromSAMLXML(samlXmlBytes);

        // then
        assertThat(userId).isEqualTo(TEST_USER_ID);
    }

    @Test
    void testThatGetUserIdFromSAMLResponseGetsUserId() {
        // given
        // SAMLResponse=(valid-saml-response.xml > base64 encode > crlf after every 76
        // characters > url encode)
        String samlBody =
                "SAMLResponse=PHNhbWw6QXNzZXJ0aW9uIHhtbG5zOnNhbWw9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjIuMDph%0D%0Ac3NlcnRpb24iCiAgICBJRD0iXzNmMDE4NGFjLTJjNmQtNDg2NC05Y2QxLWQ2YTJjNjI5YjhmZSIg%0D%0ASXNzdWVJbnN0YW50PSIyMDIzLTA1LTA1VDEyOjM0OjU2WiIgVmVyc2lvbj0iMi4wIj4KICAgIDxz%0D%0AYW1sOklzc3Vlcj4uLi48L3NhbWw6SXNzdWVyPgogICAgPFNpZ25hdHVyZSB4bWxucz0iaHR0cDov%0D%0AL3d3dy53My5vcmcvMjAwMC8wOS94bWxkc2lnIyI%2BCiAgICAgICAgPFNpZ25lZEluZm8%2BCiAgICAg%0D%0AICAgICAgIDxDYW5vbmljYWxpemF0aW9uTWV0aG9kIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5v%0D%0AcmcvMjAwMS8xMC94bWwtZXhjLWMxNG4jIiAvPgogICAgICAgICAgICA8U2lnbmF0dXJlTWV0aG9k%0D%0AIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS8wNC94bWxkc2lnLW1vcmUjcnNhLXNo%0D%0AYTI1NiIgLz4KICAgICAgICAgICAgPFJlZmVyZW5jZSBVUkk9IiNfM2YwMTg0YWMtMmM2ZC00ODY0%0D%0ALTljZDEtZDZhMmM2MjliOGZlIj4KICAgICAgICAgICAgICAgIDxUcmFuc2Zvcm1zPgogICAgICAg%0D%0AICAgICAgICAgICAgIDxUcmFuc2Zvcm0gQWxnb3JpdGhtPSJodHRwOi8vd3d3LnczLm9yZy8yMDAw%0D%0ALzA5L3htbGRzaWcjZW52ZWxvcGVkLXNpZ25hdHVyZSIgLz4KICAgICAgICAgICAgICAgICAgICA8%0D%0AVHJhbnNmb3JtIEFsZ29yaXRobT0iaHR0cDovL3d3dy53My5vcmcvMjAwMS8xMC94bWwtZXhjLWMx%0D%0ANG4jIiAvPgogICAgICAgICAgICAgICAgPC9UcmFuc2Zvcm1zPgogICAgICAgICAgICAgICAgPERp%0D%0AZ2VzdE1ldGhvZCBBbGdvcml0aG09Imh0dHA6Ly93d3cudzMub3JnLzIwMDEvMDQveG1sZW5jI3No%0D%0AYTI1NiIgLz4KICAgICAgICAgICAgICAgIDxEaWdlc3RWYWx1ZT4uLi48L0RpZ2VzdFZhbHVlPgog%0D%0AICAgICAgICAgICA8L1JlZmVyZW5jZT4KICAgICAgICA8L1NpZ25lZEluZm8%2BCiAgICAgICAgPFNp%0D%0AZ25hdHVyZVZhbHVlPi4uLjwvU2lnbmF0dXJlVmFsdWU%2BCiAgICAgICAgPEtleUluZm8%2BCiAgICAg%0D%0AICAgICAgIDxYNTA5RGF0YT4KICAgICAgICAgICAgICAgIDxYNTA5Q2VydGlmaWNhdGU%2BLi4uPC9Y%0D%0ANTA5Q2VydGlmaWNhdGU%2BCiAgICAgICAgICAgIDwvWDUwOURhdGE%2BCiAgICAgICAgPC9LZXlJbmZv%0D%0APgogICAgPC9TaWduYXR1cmU%2BCiAgICA8c2FtbDpTdWJqZWN0PgogICAgICAgIDxzYW1sOk5hbWVJ%0D%0ARCBGb3JtYXQ9InVybjpvYXNpczpuYW1lczp0YzpTQU1MOjEuMTpuYW1laWQtZm9ybWF0OnVuc3Bl%0D%0AY2lmaWVkIj51c2VyQGV4YW1wbGUuY29tPC9zYW1sOk5hbWVJRD4KICAgICAgICA8c2FtbDpTdWJq%0D%0AZWN0Q29uZmlybWF0aW9uIE1ldGhvZD0idXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmNtOmJl%0D%0AYXJlciI%2BCiAgICAgICAgICAgIDxzYW1sOlN1YmplY3RDb25maXJtYXRpb25EYXRhIE5vdE9uT3JB%0D%0AZnRlcj0iMjAyMy0wNS0wNVQxMzozNDo1NloiCiAgICAgICAgICAgICAgICBSZWNpcGllbnQ9Imh0%0D%0AdHBzOi8vc2lnbmluLmF3cy5hbWF6b24uY29tL3NhbWwiIC8%2BCiAgICAgICAgPC9zYW1sOlN1Ympl%0D%0AY3RDb25maXJtYXRpb24%2BCiAgICA8L3NhbWw6U3ViamVjdD4KICAgIDxzYW1sOkNvbmRpdGlvbnMg%0D%0ATm90QmVmb3JlPSIyMDIzLTA1LTA1VDEyOjM0OjU2WiIgTm90T25PckFmdGVyPSIyMDIzLTA1LTA1%0D%0AVDEzOjM0OjU2WiI%2BCiAgICAgICAgPHNhbWw6QXVkaWVuY2VSZXN0cmljdGlvbj4KICAgICAgICAg%0D%0AICAgPHNhbWw6QXVkaWVuY2U%2BdXJuOmFtYXpvbjp3ZWJzZXJ2aWNlczwvc2FtbDpBdWRpZW5jZT4K%0D%0AICAgICAgICA8L3NhbWw6QXVkaWVuY2VSZXN0cmljdGlvbj4KICAgIDwvc2FtbDpDb25kaXRpb25z%0D%0APgogICAgPHNhbWw6QXV0aG5TdGF0ZW1lbnQgQXV0aG5JbnN0YW50PSIyMDIzLTA1LTA1VDEyOjM0%0D%0AOjU2WiIKICAgICAgICBTZXNzaW9uSW5kZXg9Il8zZjAxODRhYy0yYzZkLTQ4NjQtOWNkMS1kNmEy%0D%0AYzYyOWI4ZmUiPgogICAgICAgIDxzYW1sOkF1dGhuQ29udGV4dD4KICAgICAgICAgICAgPHNhbWw6%0D%0AQXV0aG5Db250ZXh0Q2xhc3NSZWY%2BdXJuOm9hc2lzOm5hbWVzOnRjOlNBTUw6Mi4wOmFjOmNsYXNz%0D%0AZXM6dW5zcGVjaWZpZWQ8L3NhbWw6QXV0aG5Db250ZXh0Q2xhc3NSZWY%2BCiAgICAgICAgPC9zYW1s%0D%0AOkF1dGhuQ29udGV4dD4KICAgIDwvc2FtbDpBdXRoblN0YXRlbWVudD4KICAgIDxzYW1sOkF0dHJp%0D%0AYnV0ZVN0YXRlbWVudD4KICAgICAgICA8c2FtbDpBdHRyaWJ1dGUgTmFtZT0iaHR0cHM6Ly9hd3Mu%0D%0AYW1hem9uLmNvbS9TQU1ML0F0dHJpYnV0ZXMvUm9sZVNlc3Npb25OYW1lIj4KICAgICAgICAgICAg%0D%0APHNhbWw6QXR0cmlidXRlVmFsdWU%2BdXNlckBleGFtcGxlLmNvbTwvc2FtbDpBdHRyaWJ1dGVWYWx1%0D%0AZT4KICAgICAgICA8L3NhbWw6QXR0cmlidXRlPgogICAgICAgIDxzYW1sOkF0dHJpYnV0ZSBOYW1l%0D%0APSJodHRwczovL2F3cy5hbWF6b24uY29tL1NBTUwvQXR0cmlidXRlcy9Sb2xlIj4KICAgICAgICAg%0D%0AICAgPHNhbWw6QXR0cmlidXRlVmFsdWU%2BCiAgICAgICAgICAgICAgICBhcm46YXdzOmlhbTo6MTIz%0D%0ANDU2Nzg5MDEyOnJvbGUvRXhhbXBsZVJvbGU8L3NhbWw6QXR0cmlidXRlVmFsdWU%2BCiAgICAgICAg%0D%0APC9zYW1sOkF0dHJpYnV0ZT4KICAgICAgICA8c2FtbDpBdHRyaWJ1dGUgTmFtZT0iaHR0cHM6Ly9h%0D%0Ad3MuYW1hem9uLmNvbS9TQU1ML0F0dHJpYnV0ZXMvU2Vzc2lvbkR1cmF0aW9uIj4KICAgICAgICAg%0D%0AICAgPHNhbWw6QXR0cmlidXRlVmFsdWU%2BMzYwMDwvc2FtbDpBdHRyaWJ1dGVWYWx1ZT4KICAgICAg%0D%0AICA8L3NhbWw6QXR0cmlidXRlPgogICAgPC9zYW1sOkF0dHJpYnV0ZVN0YXRlbWVudD4KPC9zYW1s%0D%0AOkFzc2VydGlvbj4%3D%0D%0A";
        UserIdExtractor extractor = new UserIdExtractor();

        // when
        String userId = extractor.getUserIdFromSAMLResponse(samlBody);

        // then
        assertThat(userId).isEqualTo(TEST_USER_ID);
    }

    @Test
    void testThatGetUserTokenFromHeadersGetsTokenFromCookieHeader(@Mock HttpHeaders headers) {
        // given
        String samlToken = "mySAMLToken";
        String cookieHeader = "security_authentication=othertoken; security_authentication_saml1=" + samlToken
                + "; other_header=val;";
        when(headers.getAll(eq(HttpHeaderNames.COOKIE))).thenReturn(List.of(cookieHeader));
        UserIdExtractor extractor = new UserIdExtractor();

        // when
        String userToken = extractor.getUserTokenFromHeaders(headers);

        // then
        assertThat(userToken).isEqualTo(samlToken);
    }

    @Test
    void testThatGetUserTokenFromHeadersGetsTokenFromSetCookieHeader(@Mock HttpHeaders headers) {
        // given
        String samlToken = "mySAMLToken";
        List<String> setCookieHeaders = List.of(
                "security_authentication=othertoken; Secure; HttpOnly; Path=/_dashboards",
                "security_authentication_saml2=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Secure; HttpOnly; Path=/_dashboards",
                "security_authentication_saml1=" + samlToken + "; Secure; HttpOnly; Path=/_dashboards",
                "security_authentication_saml3=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Secure; HttpOnly; Path=/_dashboards");
        when(headers.getAll(eq(HttpHeaderNames.SET_COOKIE))).thenReturn(setCookieHeaders);
        when(headers.getAll(not(eq(HttpHeaderNames.SET_COOKIE)))).thenReturn(null);
        UserIdExtractor extractor = new UserIdExtractor();

        // when
        String userToken = extractor.getUserTokenFromHeaders(headers);

        // then
        assertThat(userToken).isEqualTo(samlToken);
    }
}
