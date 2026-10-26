package com.mapmory.backend.group.application;

/**
 * 수락 전에 보여 주는 초대. 어느 그룹인지와 내가 이미 멤버인지만 담는다.
 */
public record InvitationPreview(String groupName, int memberCount, boolean alreadyMember) {
}
