package org.opensearch.trafficgateway.proxy.governance;

import lombok.Getter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.XQueryParser;
import org.apache.lucene.search.Query;
import org.opensearch.common.time.DateMathParser;

public class CustomQueryParser extends XQueryParser {
    private static final String DATE_EPOCH = "1970-01-01";
    private static final String DATE_NOW = "now";

    @Getter
    private final DateMathParser dateParser;

    public CustomQueryParser(String f, Analyzer a, DateMathParser dateParser) {
        super(f, a);
        this.dateParser = dateParser;
        this.setDateResolution(Resolution.MILLISECOND);
    }

    @Override
    public Query getFieldQuery(String field, String queryText, boolean quoted) throws ParseException {
        if (quoted) {
            return super.getFieldQuery(field, queryText, quoted);
        }

        if (field != null) {
            if (queryText.length() > 1) {
                if (queryText.charAt(0) == '>') {
                    if (queryText.length() > 2) {
                        if (queryText.charAt(1) == '=') {
                            return super.getRangeQuery(field, queryText.substring(2), DATE_NOW, true, true);
                        }
                    }
                    return super.getRangeQuery(field, queryText.substring(1), DATE_NOW, false, true);
                } else if (queryText.charAt(0) == '<') {
                    if (queryText.length() > 2) {
                        if (queryText.charAt(1) == '=') {
                            return super.getRangeQuery(field, DATE_EPOCH, queryText.substring(2), true, true);
                        }
                    }
                    return super.getRangeQuery(field, DATE_EPOCH, queryText.substring(1), true, false);
                }
            }
        }
        return super.getFieldQuery(field, queryText, quoted);
    }
}
