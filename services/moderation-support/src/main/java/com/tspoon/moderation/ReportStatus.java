package com.tspoon.moderation;

public enum ReportStatus {
    PENDING,    // 검토 대기 — 자동 숨김 기준에 들어간다
    ACCEPTED,   // 관리자가 숨김을 확정
    DISMISSED   // 관리자가 기각 (숨겨져 있었다면 다시 공개)
}
