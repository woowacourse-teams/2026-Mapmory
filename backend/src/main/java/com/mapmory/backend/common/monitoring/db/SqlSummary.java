package com.mapmory.backend.common.monitoring.db;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 느린 쿼리 로그에 남길 SQL의 종류와 대상 테이블. SQL 원문과 파라미터는 개인정보가 들어갈 수 있어 남기지 않고, 이 두 값만 뽑는다.
 *
 * <p>테이블은 SELECT·DELETE는 첫 {@code FROM}, INSERT는 {@code INTO}, UPDATE는 {@code UPDATE} 바로 뒤의 이름이다.
 * 조인한 다른 테이블은 남기지 않는다. 고칠 쿼리를 찾는 실마리로만 쓴다.
 */
record SqlSummary(String queryType, String table) {

    static final String OTHER = "OTHER";
    static final String UNKNOWN_TABLE = "UNKNOWN";

    private static final Pattern TYPE = Pattern.compile("^\\s*(select|insert|update|delete)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_OR_DELETE_TABLE = Pattern.compile("\\bfrom\\s+`?([A-Za-z0-9_$]+)`?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INSERT_TABLE = Pattern.compile("\\binto\\s+`?([A-Za-z0-9_$]+)`?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_TABLE = Pattern.compile("^\\s*update\\s+`?([A-Za-z0-9_$]+)`?",
            Pattern.CASE_INSENSITIVE);

    static SqlSummary of(String sql) {
        if (sql == null) {
            return new SqlSummary(OTHER, UNKNOWN_TABLE);
        }
        Matcher type = TYPE.matcher(sql);
        if (!type.find()) {
            return new SqlSummary(OTHER, UNKNOWN_TABLE);
        }
        String queryType = type.group(1).toUpperCase(Locale.ROOT);
        Pattern tablePattern = switch (queryType) {
            case "INSERT" -> INSERT_TABLE;
            case "UPDATE" -> UPDATE_TABLE;
            default -> SELECT_OR_DELETE_TABLE;
        };
        Matcher table = tablePattern.matcher(sql);
        return new SqlSummary(queryType, table.find() ? table.group(1) : UNKNOWN_TABLE);
    }
}
