package com.mapmory.backend.group.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.domain.Album;
import com.mapmory.backend.group.domain.AlbumEntry;
import com.mapmory.backend.group.domain.AlbumRepository;
import com.mapmory.backend.group.exception.GroupErrorCode;
import com.mapmory.backend.member.api.MemberQuery;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.travelrecord.api.RecordBrief;
import com.mapmory.backend.travelrecord.api.RecordView;
import com.mapmory.backend.travelrecord.api.TravelRecordQuery;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앨범 목록과 상세(T5). 남의 기록은 여행 기록 창구로, 닉네임은 회원 창구로 읽는다(ADR 0022 결정 5).
 * 두 조회 모두 앨범·기록 수와 상관없이 쿼리 수가 일정하다. 기록과 닉네임은 한 번에 묶어 읽는다.
 *
 * <p>album_entry.travel_record_id에는 FK가 없고 기록 삭제 연쇄(T7)가 아직 없어서, 지워진 기록을 가리키는 항목이 남을 수 있다.
 * 창구가 돌려주지 않은 기록은 응답에서 뺀다.
 */
@Service
public class AlbumQueryService {

    // 회원 행은 탈퇴해도 남으므로 거의 쓰이지 않는다. 행이 없을 때 화면이 비지 않게 넣는다.
    static final String UNKNOWN_NICKNAME = "알 수 없음";
    // 기록 목록(TravelRecordService)과 같은 한도다.
    private static final int MAX_PAGE_SIZE = 50;

    private final AlbumRepository albumRepository;
    private final SocialMemberGuard socialMemberGuard;
    private final GroupMembership groupMembership;
    private final TravelRecordQuery travelRecordQuery;
    private final MemberQuery memberQuery;

    public AlbumQueryService(AlbumRepository albumRepository, SocialMemberGuard socialMemberGuard,
                             GroupMembership groupMembership, TravelRecordQuery travelRecordQuery,
                             MemberQuery memberQuery) {
        this.albumRepository = albumRepository;
        this.socialMemberGuard = socialMemberGuard;
        this.groupMembership = groupMembership;
        this.travelRecordQuery = travelRecordQuery;
        this.memberQuery = memberQuery;
    }

    /**
     * 쿼리는 페이지당 일정하다. 소셜 회원 확인, 멤버 확인, 앨범 id 한 페이지(와 전체 수), 앨범과 항목,
     * 한 페이지 모든 기록의 요약(창구 2번), 담은 사람 모두의 닉네임.
     */
    @Transactional(readOnly = true)
    public AlbumPage list(Long memberId, Long groupId, int page, int size) {
        validatePagination(page, size);
        socialMemberGuard.require(memberId);
        groupMembership.validateMember(groupId, memberId);
        Page<Long> albumIds = albumRepository.findIdsByGroupIdOrderByLastAdded(groupId, PageRequest.of(page, size));
        List<Album> albums = findInOrder(albumIds.getContent());

        List<Long> travelRecordIds = albums.stream().flatMap(album -> album.travelRecordIds().stream()).toList();
        Map<Long, RecordBrief> records = briefsOf(travelRecordIds);
        Set<Long> memberIds = albums.stream()
                .flatMap(album -> album.getEntries().stream())
                .filter(entry -> records.containsKey(entry.getTravelRecordId()))
                .map(AlbumEntry::getMemberId)
                .collect(Collectors.toSet());
        Map<Long, String> nicknames = memberIds.isEmpty() ? Map.of() : memberQuery.nicknamesOf(memberIds);

        return new AlbumPage(
                albums.stream().map(album -> toCard(album, records, nicknames, memberId)).toList(),
                albumIds.getNumber(),
                albumIds.getSize(),
                albumIds.getTotalElements(),
                albumIds.getTotalPages(),
                albumIds.hasNext()
        );
    }

    /**
     * 기록 수와 상관없이 쿼리 6번이다. 소셜 회원 확인, 멤버 확인, 앨범과 항목, 기록과 사진(창구 2번), 닉네임.
     */
    @Transactional(readOnly = true)
    public AlbumDetail detail(Long memberId, Long groupId, Long albumId) {
        socialMemberGuard.require(memberId);
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
        List<AlbumEntry> entries = album.getEntries().stream()
                .filter(entry -> records.containsKey(entry.getTravelRecordId()))
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

    private List<Album> findInOrder(List<Long> albumIds) {
        if (albumIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Album> albums = albumRepository.findAllWithEntriesByIdIn(albumIds).stream()
                .collect(Collectors.toMap(Album::getId, Function.identity()));
        return albumIds.stream().map(albums::get).filter(Objects::nonNull).toList();
    }

    private Map<Long, RecordBrief> briefsOf(Collection<Long> travelRecordIds) {
        return travelRecordQuery.briefsOf(travelRecordIds).stream()
                .collect(Collectors.toMap(RecordBrief::id, Function.identity()));
    }

    private static AlbumPage.Card toCard(Album album, Map<Long, RecordBrief> records, Map<Long, String> nicknames,
                                         Long myMemberId) {
        List<AlbumEntry> entries = album.getEntries().stream()
                .filter(entry -> records.containsKey(entry.getTravelRecordId()))
                .toList();
        List<RecordBrief> shown = entries.stream().map(entry -> records.get(entry.getTravelRecordId())).toList();
        return new AlbumPage.Card(
                album.getId(),
                album.getName(),
                album.getRegion(),
                shown.stream().map(RecordBrief::startDate).min(Comparator.naturalOrder()).orElse(null),
                shown.stream().map(RecordBrief::endDate).max(Comparator.naturalOrder()).orElse(null),
                coverOf(album, shown),
                // 한 멤버가 기록을 여럿 담을 수 있어 처음 담은 순서로 한 번씩만 넣는다.
                entries.stream().map(AlbumEntry::getMemberId).distinct()
                        .map(memberId -> contributor(memberId, nicknames, myMemberId)).toList(),
                shown.size(),
                shown.stream().mapToInt(RecordBrief::photoCount).sum(),
                // 정렬과 같은 기준이라 지워진 기록의 항목도 센다.
                album.getEntries().stream().map(AlbumEntry::getCreatedAt).max(Comparator.naturalOrder()).orElse(null)
        );
    }

    private static ExpiringUrl coverOf(Album album, List<RecordBrief> shown) {
        Optional<ExpiringUrl> openerCover = shown.stream()
                .filter(record -> record.memberId().equals(album.getOpenerMemberId()))
                .map(RecordBrief::cover)
                .filter(Objects::nonNull)
                .findFirst();
        return openerCover.or(() -> shown.stream().map(RecordBrief::cover).filter(Objects::nonNull).findFirst())
                .orElse(null);
    }

    private static void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(GroupErrorCode.INVALID_PAGINATION);
        }
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
