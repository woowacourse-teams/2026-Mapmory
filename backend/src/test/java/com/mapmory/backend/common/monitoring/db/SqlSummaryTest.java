package com.mapmory.backend.common.monitoring.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SqlSummaryTest {

    @ParameterizedTest(name = "{0} → {1} {2}")
    @CsvSource(delimiter = '|', value = {
            "select tr1_0.id from travel_record tr1_0 join tag t1_0 on t1_0.id = tr1_0.tag_id where tr1_0.member_id=? | SELECT | travel_record",
            "SELECT * FROM `member` WHERE id = ? FOR UPDATE | SELECT | member",
            "insert into record_media (object_key, travel_record_id) values (?, ?) | INSERT | record_media",
            "insert ignore into tag (name) values (?) | INSERT | tag",
            "update travel_record set title=? where id=? | UPDATE | travel_record",
            "delete from travel_record_tag where travel_record_id=? | DELETE | travel_record_tag",
            "  select count(*) from region | SELECT | region",
            "select 1 | SELECT | UNKNOWN",
            "with recent as (select id from member) select * from recent | OTHER | UNKNOWN",
            "call refresh_statistics() | OTHER | UNKNOWN",
    })
    void SQL의_종류와_첫_테이블만_뽑는다(String sql, String queryType, String table) {
        assertThat(SqlSummary.of(sql)).isEqualTo(new SqlSummary(queryType, table));
    }

    @Test
    void 여러_줄로_나뉜_SQL도_읽는다() {
        assertThat(SqlSummary.of("select\n        m1_0.id\n    from\n        member m1_0"))
                .isEqualTo(new SqlSummary("SELECT", "member"));
    }
}
