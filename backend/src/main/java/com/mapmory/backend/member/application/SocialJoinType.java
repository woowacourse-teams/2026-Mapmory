package com.mapmory.backend.member.application;

/**
 * 소셜 로그인으로 어떤 회원이 되었는가.
 *
 * SOCIAL_JOINED    : 새 소셜 회원으로 가입했다.
 * PROMOTED         : 게스트 회원이 소셜 회원으로 승격했다. 게스트로 남긴 기록이 이어진다.
 * EXISTING_ACCOUNT : 같은 소셜 계정으로 가입한 회원이 이미 있어 그 회원으로 들어왔다.
 */
public enum SocialJoinType {
    SOCIAL_JOINED,
    PROMOTED,
    EXISTING_ACCOUNT
}
