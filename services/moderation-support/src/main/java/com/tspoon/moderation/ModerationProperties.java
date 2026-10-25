package com.tspoon.moderation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param autoHideThreshold 서로 다른 회원의 검토 대기 신고가 이 수에 닿으면 관리자 검토 전이라도 숨긴다
 */
@ConfigurationProperties("tspoon.moderation")
public record ModerationProperties(@DefaultValue("5") int autoHideThreshold) {
}
