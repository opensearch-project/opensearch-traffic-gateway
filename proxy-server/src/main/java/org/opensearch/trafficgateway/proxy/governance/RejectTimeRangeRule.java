package org.opensearch.trafficgateway.proxy.governance;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.handler.codec.http.FullHttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.opensearch.OpenSearchParseException;
import org.opensearch.common.time.DateFormatter;
import org.opensearch.common.time.DateMathParser;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class RejectTimeRangeRule extends BaseSearchGovernanceRule {
    private static final String RANGE_KEYWORD = "range";
    private static final String QUERY_KEYWORD = "query";
    private static final String QUERY_STRING_KEYWORD = "query_string";
    private static final String TIME_ZONE_KEYWORD = "time_zone";
    private static final String DEFAULT_FIELD_KEYWORD = "default_field";
    private static final String DEFAULT_DATE_FORMAT = "strict_date_optional_time||epoch_millis";

    @NonNull
    private String rangeField;

    @NonNull
    private Long maxTimeRangeMs;

    private boolean rejectIfMissing;

    String responseMessage;

    @NonFinal
    private int rangeFound;

    public RejectTimeRangeRule(
            @NonNull String indexRegex,
            @NonNull String rangeField,
            @NonNull String maxTimeRangeMs,
            @NonNull String rejectIfMissing) {
        this(indexRegex, rangeField, maxTimeRangeMs, rejectIfMissing, null);
    }

    public RejectTimeRangeRule(
            @NonNull String indexRegex,
            @NonNull String rangeField,
            @NonNull String maxTimeRangeMs,
            @NonNull String rejectIfMissing,
            String responseMessage) {
        super(indexRegex);
        this.rangeField = rangeField;
        this.maxTimeRangeMs = Long.parseLong(maxTimeRangeMs);
        this.rejectIfMissing = Boolean.parseBoolean(rejectIfMissing);
        this.rangeFound = 0;
        if (responseMessage != null) {
            this.responseMessage = responseMessage;
        } else {
            this.responseMessage = "The query range for field '" + getRangeField()
                    + "' does not exist or is bigger than or equal to the maximum range: '" + getMaxTimeRangeMs() + "'";
        }
    }

    @Override
    public GovernanceRuleResult evaluate(FullHttpRequest request) {
        ParsedSearchRequest searchRequest = tryParseSearchRequest(request);

        if (searchRequest == null || !requestMatchesIndex(searchRequest)) {
            return getPassResult();
        }

        if (!fieldRangeQueryWithinMaxTimeRange(searchRequest.getSearchBody())) {
            return getRejectResultWithMessage(getResponseMessage());
        }

        return getPassResult();
    }

    private boolean fieldRangeQueryWithinMaxTimeRange(JsonNode body) {
        JsonNode queryObject;
        try {
            queryObject = body.get(QUERY_KEYWORD);
        } catch (Exception e) {
            return true;
        }
        this.rangeFound = 0;
        boolean foundValidRange = scanJsonForRangeAndQueryString(queryObject);
        if (foundValidRange) {
            return true;
        } else {
            if (!rejectIfMissing)
                return rangeFound != 1;
            else
                return false;
        }
    }

    private boolean scanJsonForRangeAndQueryString(JsonNode element) {
        if (element == null) {
            return false;
        }
        if (element.isObject()) {
            if (element.has(RANGE_KEYWORD)) {
                JsonNode rangeObject = element.get(RANGE_KEYWORD).get(getRangeField());

                if (rangeObject != null) {
                    this.rangeFound = 1;
                    return formatAndCheckRangeBetween(rangeObject);
                }
            }

            if (element.has(QUERY_STRING_KEYWORD)) {
                JsonNode queryString = element.get(QUERY_STRING_KEYWORD);
                if (queryString != null) {
                    ImmutablePair<Long, String> queryStringRangeBetweenMs = checkQueryString(queryString);
                    if (queryStringRangeBetweenMs != null) {
                        if (queryStringRangeBetweenMs.getRight().equals(getRangeField())) {
                            this.rangeFound = 1;
                        }
                        if (queryStringRangeBetweenMs.getLeft() != -1)
                            return queryStringRangeBetweenMs.getLeft() <= getMaxTimeRangeMs();
                    }
                }
            }

            for (Map.Entry<String, JsonNode> entry : element.properties()) {
                if (scanJsonForRangeAndQueryString(entry.getValue())) {
                    return true;
                }
            }
        } else if (element.isArray()) {
            for (JsonNode jsonElement : element) {
                if (scanJsonForRangeAndQueryString(jsonElement)) {
                    return true;
                }
            }
        }

        return false;
    }

    private ImmutablePair<Long, String> checkQueryString(JsonNode queryString) {
        DateFormatter formatter;
        DateMathParser parser;

        try {
            formatter = DateFormatter.forPattern(DEFAULT_DATE_FORMAT);
            parser = formatter.toDateMathParser();
        } catch (IllegalArgumentException e) {
            return null;
        }

        ZoneId zoneId = ZoneId.systemDefault();

        if (queryString.has(TIME_ZONE_KEYWORD)
                && queryString.get(TIME_ZONE_KEYWORD).isValueNode()) {
            String timeZone = queryString.get(TIME_ZONE_KEYWORD).asText();
            zoneId = getTimeZone(timeZone);
            if (zoneId == null) {
                return null;
            }
        }

        Clock clock = Clock.system(zoneId);
        StandardAnalyzer analyzer;
        CustomQueryParser queryParser;
        Query query;
        String defaultField = "*";
        if (queryString.has(DEFAULT_FIELD_KEYWORD)
                && queryString.get(DEFAULT_FIELD_KEYWORD).isValueNode()) {
            defaultField = queryString.get(DEFAULT_FIELD_KEYWORD).asText();
        }
        try {
            analyzer = new StandardAnalyzer();
            queryParser = new CustomQueryParser(defaultField, analyzer, parser);
            String queryStringQuery = queryString.get(QUERY_KEYWORD).asText();
            query = queryParser.parse(queryStringQuery);
        } catch (ParseException e) {
            return null;
        }

        List<Query> rangeQueries = getRangeQueries(query);

        return checkIfAllTermRangeQueriesWithinMaxRange(rangeQueries, queryParser, clock);
    }

    private ImmutablePair<Long, String> checkIfAllTermRangeQueriesWithinMaxRange(
            List<Query> rangeQueries, CustomQueryParser queryParser, Clock clock) {

        // Find the longest range within the maxTimeRangeMs
        // if any of the ranges in the list is bigger then we return -1
        long longestRange = 0;
        String field = "";
        for (Query query : rangeQueries) {
            TermRangeQuery rangeQuery = null;
            try {
                rangeQuery = (TermRangeQuery) query;
            } catch (ClassCastException e) {
                return null;
            }

            if (!rangeQuery.getField().equals(getRangeField())) {
                return null;
            }
            String lowerBound;
            String upperBound;
            Instant startInstant;
            Instant endInstant;

            field = rangeQuery.getField();
            ImmutablePair<Long, String> invalidRangeWithField = new ImmutablePair<>(-1L, rangeQuery.getField());

            // If they pass in an un-recognized value like * then that means it's an
            // unbounded range
            // so deny the request.
            if (rangeQuery.getLowerTerm() == null || rangeQuery.getUpperTerm() == null) {
                return invalidRangeWithField;
            }

            try {
                lowerBound = new String(rangeQuery.getLowerTerm().bytes, StandardCharsets.UTF_8);
                startInstant = queryParser.getDateParser().parse(lowerBound, () -> Instant.now(clock)
                        .toEpochMilli());
            } catch (OpenSearchParseException e) {
                return invalidRangeWithField;
            }

            try {
                upperBound = new String(rangeQuery.getUpperTerm().bytes, StandardCharsets.UTF_8);
                endInstant = queryParser.getDateParser().parse(upperBound, () -> Instant.now(clock)
                        .toEpochMilli());
            } catch (OpenSearchParseException e) {
                return invalidRangeWithField;
            }

            if (startInstant.isAfter(endInstant)) {
                return invalidRangeWithField;
            }

            long currentRange = ChronoUnit.MILLIS.between(startInstant, endInstant);
            if (currentRange > getMaxTimeRangeMs()) {
                return invalidRangeWithField;
            }

            if (currentRange > longestRange) {
                longestRange = currentRange;
            }
        }

        return new ImmutablePair<>(longestRange, field);
    }

    private List<Query> getRangeQueries(Query query) {
        List<Query> rangeQueries = new ArrayList<>();

        if (query instanceof BooleanQuery) {
            BooleanQuery boolQuery = (BooleanQuery) query;

            for (BooleanClause clause : boolQuery.clauses()) {
                rangeQueries.addAll(getRangeQueries(clause.getQuery()));
            }
        } else if (query instanceof TermRangeQuery) {
            rangeQueries.add(query);
        }

        return rangeQueries;
    }

    private ZoneId getTimeZone(String timeZone) {
        ZoneId zoneId;
        // ISO 8601 UTC offsets
        if (timeZone.startsWith("+") || timeZone.startsWith("-")) {
            try {
                zoneId = ZoneOffset.of(timeZone);
            } catch (DateTimeException e) {
                return null;
            }
        } else { // IANA time zone IDs,
            try {
                zoneId = ZoneId.of(timeZone);
            } catch (DateTimeException e) {
                return null;
            }
        }
        return zoneId;
    }

    private boolean formatAndCheckRangeBetween(JsonNode range)
            throws UnsupportedOperationException, IllegalStateException {

        // Check if format is passed in else default to
        // 'strict_date_optional_time||epoch_millis'
        String format = DEFAULT_DATE_FORMAT;
        if (range.has("format") && range.get("format").isValueNode()) {
            format = range.get("format").asText();
        }

        DateFormatter formatter;
        DateMathParser parser;

        try {
            formatter = DateFormatter.forPattern(format);
            parser = formatter.toDateMathParser();
        } catch (IllegalArgumentException e) {
            return false;
        }

        ZoneId zoneId = ZoneId.systemDefault();

        if (range.has(TIME_ZONE_KEYWORD) && range.get(TIME_ZONE_KEYWORD).isValueNode()) {
            String timeZone = range.get(TIME_ZONE_KEYWORD).asText();
            zoneId = getTimeZone(timeZone);
            if (zoneId == null) {
                return false;
            }
        }

        Clock clock = Clock.system(zoneId);
        String dateNow = LocalDateTime.now(clock).toString();
        String dateEpoch = LocalDateTime.of(1970, Month.JANUARY, 1, 0, 0).toString();

        // Parse the start and end range, if it doesn't exist then set to the current
        // time
        String start = range.has("gte")
                ? range.get("gte").asText()
                : range.has("gt") ? range.get("gt").asText() : null;

        String end = range.has("lte")
                ? range.get("lte").asText()
                : range.has("lt") ? range.get("lt").asText() : null;

        if (start == null && end == null) {
            start = dateEpoch;
            end = dateNow;
        } else if (start == null) {
            start = dateEpoch;
        } else if (end == null) {
            end = dateNow;
        }

        Instant startInstant;
        Instant endInstant;
        try {
            startInstant = parser.parse(start, () -> Instant.now(clock).toEpochMilli(), false, zoneId);
            endInstant = parser.parse(end, () -> Instant.now(clock).toEpochMilli(), false, zoneId);
        } catch (OpenSearchParseException e) {
            return false;
        }
        // If start is after the end then return false
        if (startInstant.isAfter(endInstant)) {
            return false;
        }

        return ChronoUnit.MILLIS.between(startInstant, endInstant) <= getMaxTimeRangeMs();
    }
}
