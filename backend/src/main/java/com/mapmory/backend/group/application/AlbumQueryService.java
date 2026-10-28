package com.mapmory.backend.group.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.Album;
import com.mapmory.backend.group.domain.AlbumEntry;
import com.mapmory.backend.group.domain.AlbumRepository;
import com.mapmory.backend.group.exception.GroupErrorCode;
import com.mapmory.backend.member.api.MemberQuery;
import com.mapmory.backend.travelrecord.api.RecordView;
import com.mapmory.backend.travelrecord.api.TravelRecordQuery;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앨범 조회(T5). 남의 기록은 여행 기록 창구로, 닉네임은 회원 창구로 읽는다(ADR 0022 결정 5).
 *
 * <p>album_entry.travel_record_id에는 FK가 없고 기록 삭제 연쇄(T7)가 아직 없어서, 지워진 기록을 가리키는 항목이 남을 수 있다.
 * 창구가 돌려주지 않은 기록은 응답에서 뺀다.
 */
@Service
public class AlbumQueryService {

    // 회원 행은 탈퇴해도 남으므로 거의 쓰이지 않는다. 행이 없을 때 화면이 비지 않게 넣는다.
    static final String UNKNOWN_NICKNAME = "알 수 없음";

    private final AlbumRepository albumRepository;
    private final GroupMembership groupMembership;
    private final TravelRecordQuery travelRecordQuery;
    private final MemberQuery memberQuery;

    public AlbumQueryService(AlbumRepository albumRepository, GroupMembership groupMembership,
                             TravelRecordQuery travelRecordQuery, MemberQuery memberQuery) {
        this.albumRepository = albumRepository;
        this.groupMembership = groupMembership;
        this.travelRecordQuery = travelRecordQuery;
        this.memberQuery = memberQuery;
    }

    /**
     * 기록 수와 상관없이 쿼리 5번이다. 멤버 확인, 앨범과 항목, 기록과 사진(창구), 닉네임.
     */
    @Transactional(readOnly = true)
    public AlbumDetail detail(Long memberId, Long groupId, Long albumId) {
        groupMembership.validateMember(groupId, memberId);
        Album album = albumRepository.findByIdAndGroupId(albumId, groupId)
                .orElseThrow(() -> new BusinessException(GroupErrorCode.ALBUM_NOT_FOUND));
        return detailOf(album, memberId);
    }

    /**
     * 이미 읽은 앨범으로 상세를 만든다. 앨범 쓰기가 같은 트랜잭션에서 응답을 만들 때 쓴다. 멤버인지는 부르는 쪽이 확인한다.
     */
    @Transactional(readOnly = true)
    public AlbumDetail detailOf(Album album, Long memberId) {
        Map<Long, RecordView> records = travelRecordQuery.viewsOf(album.travelRecordIds()).stream()
                .collect(Collectors.toMap(RecordView::id, Function.identity()));
        // 여행 순서로 읽히게 기록의 시작일 순이다. 시작일이 같으면 담은 순서다(정렬이 안정적이다).
        List<AlbumEntry> entries = album.getEntries().stream()
                .filter(entry -> records.containsKey(entry.getTravelRecordId()))
                .sorted(Comparator.comparing(entry -> records.get(entry.getTravelRecordId()).startDate()))
                .toList();

        Set<Long> memberIds = new LinkedHashSet<>();
        memberIds.add(album.getOpenerMemberId());
        entries.forEach(entry -> memberIds.add(entry.getMemberId()));
        Map<Long, String> nicknames = memberQuery.nicknamesOf(memberIds);

        List<AlbumDetail.Entry> entryViews = entries.stream()
                .map(entry -> new AlbumDetail.Entry(
                        records.get(entry.getTravelRecordId()),
                        contributor(entry.getMemberId(), nicknames, memberId),
                        entry.getCreatedAt()))
                .toList();
        List<RecordView> shown = entryViews.stream().map(AlbumDetail.Entry::record).toList();

        return new AlbumDetail(
                album.getId(),
                album.getGroupId(),
                album.getName(),
                album.getRegion(),
                shown.stream().map(RecordView::startDate).min(Comparator.naturalOrder()).orElse(null),
                shown.stream().map(AlbumQueryService::lastDay).max(Comparator.naturalOrder()).orElse(null),
                contributor(album.getOpenerMemberId(), nicknames, memberId),
                album.travelRecordIdsOf(memberId).stream().filter(records::containsKey).toList(),
                entryViews
        );
    }

    static AlbumDetail.Contributor contributor(Long memberId, Map<Long, String> nicknames, Long myMemberId) {
        return new AlbumDetail.Contributor(
                memberId,
                nicknames.getOrDefault(memberId, UNKNOWN_NICKNAME),
                memberId.equals(myMemberId));
    }

    // 종료일이 없는 기록(당일 여행)은 시작일에 끝난다.
    private static LocalDate lastDay(RecordView record) {
        return Objects.requireNonNullElse(record.endDate(), record.startDate());
    }
}
