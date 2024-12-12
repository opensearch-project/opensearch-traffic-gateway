package org.opensearch.trafficgateway.proxy.util;

import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.util.CharsetUtil;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.w3c.dom.Document;

/*
 * NOTE: Will not work with SigV4.
 */
@Log4j2
public class UserIdExtractor {
    public static final String DEFAULT_SAML_USER_ID_XPATH = "/Assertion/Subject/NameID[text()]";
    public static final String DEFAULT_SAML_TOKEN_COOKIE_NAME = "security_authentication_saml1";

    static final String ACS_PATH = "/_dashboards/_opendistro/_security/saml/acs";

    private static final Decoder BASE64_DECODER = Base64.getDecoder();
    private static final XPath XPATH = XPathFactory.newInstance().newXPath();
    private static final DocumentBuilderFactory DOC_BUILDER_FACTORY = DocumentBuilderFactory.newInstance();

    private final XPathExpression samlUserIdXPath;
    private final String samlTokenCookieName;

    public UserIdExtractor() {
        this(DEFAULT_SAML_USER_ID_XPATH, DEFAULT_SAML_TOKEN_COOKIE_NAME);
    }

    @SneakyThrows
    public UserIdExtractor(String samlUserIdXPath, String samlTokenCookieName) {
        if (samlUserIdXPath == null) {
            this.samlUserIdXPath = null;
        } else {
            this.samlUserIdXPath = XPATH.compile(samlUserIdXPath);
        }
        this.samlTokenCookieName = samlTokenCookieName;
    }

    public String extractUserId(FullHttpRequest request) {
        HttpHeaders requestHeaders = request.headers();
        String authHeader = requestHeaders.get(HttpHeaderNames.AUTHORIZATION);

        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
        String requestBody = request.content().toString(CharsetUtil.UTF_8);

        if (authHeader != null && authHeader.startsWith("Basic ")) {
            return getUserIdFromAuthHeader(authHeader);
        } else if (queryStringDecoder.path().equals(ACS_PATH)) {
            return getUserIdFromSAMLResponse(requestBody);
        }

        return null;
    }

    public String extractUserToken(HttpMessage message) {
        HttpHeaders headers = message.headers();

        return getUserTokenFromHeaders(headers);
    }

    String getUserIdFromAuthHeader(String authHeader) {
        try {
            String authString = authHeader.substring(6);
            String decodedAuthString = new String(BASE64_DECODER.decode(authString), StandardCharsets.UTF_8);
            String username = decodedAuthString.split(":")[0];
            return username;
        } catch (Exception e) {
            log.warn("Error parsing auth header, including entire header value as userId.", e);
            return authHeader;
        }
    }

    String getUserIdFromSAMLResponse(String requestBody) {
        if (!requestBody.startsWith("SAMLResponse=")) {
            return null;
        }

        try {
            String urlEncodedBase64SAMLResponse = requestBody.substring(13);
            String base64SAMLResponse = QueryStringDecoder.decodeComponent(urlEncodedBase64SAMLResponse)
                    .replaceAll("\\R", "");
            byte[] samlResponseBytes = BASE64_DECODER.decode(base64SAMLResponse);
            return getUserIdFromSAMLXML(samlResponseBytes);
        } catch (Exception e) {
            log.warn("Error parsing SAML response, setting userId to null.", e);
            return null;
        }
    }

    @SneakyThrows
    String getUserIdFromSAMLXML(byte[] samlResponseBytes) throws XMLStreamException {
        if (this.samlUserIdXPath == null) {
            return null;
        }

        DocumentBuilder builder = DOC_BUILDER_FACTORY.newDocumentBuilder();
        ByteArrayInputStream samlResponseStream = new ByteArrayInputStream(samlResponseBytes);
        Document document = builder.parse(samlResponseStream);
        return (String) samlUserIdXPath.evaluate(document, XPathConstants.STRING);
    }

    String getUserTokenFromHeaders(HttpHeaders requestHeaders) {
        try {
            List<String> cookieHeaders = requestHeaders.getAll(HttpHeaderNames.COOKIE);
            if (cookieHeaders == null || cookieHeaders.isEmpty()) {
                cookieHeaders = requestHeaders.getAll(HttpHeaderNames.SET_COOKIE);
            }

            if (cookieHeaders == null || cookieHeaders.isEmpty()) {
                return null;
            }

            for (String cookieHeader : cookieHeaders) {
                Set<Cookie> cookies = ServerCookieDecoder.LAX.decode(cookieHeader);
                for (Cookie cookie : cookies) {
                    if (cookie.name().equals(samlTokenCookieName)) {
                        return cookie.value();
                    }
                }
            }

            return null;
        } catch (Exception e) {
            log.warn("Error parsing user token from headers. Setting user token to null.", e);
            return null;
        }
    }
}
