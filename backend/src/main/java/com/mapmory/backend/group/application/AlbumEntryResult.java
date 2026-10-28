package com.mapmory.backend.group.application;

/**
 * 기록 담기 결과. 같은 기록을 다시 담았으면 created가 false다. 컨트롤러가 201과 200을 고르는 데 쓴다.
 */
public record AlbumEntryResult(AlbumDetail detail, boolean created) {
}
