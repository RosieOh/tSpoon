package com.tspoon.moderation;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * 서비스가 {@link ModerationTarget} 빈을 등록하면 그 종류에 대한 신고·관리 API 가 열린다.
 */
@AutoConfiguration
@EnableConfigurationProperties(ModerationProperties.class)
public class ModerationAutoConfiguration {

    @Bean
    public ReportRepository reportRepository(JdbcTemplate jdbcTemplate) {
        return new ReportRepository(jdbcTemplate);
    }

    @Bean
    public ModerationService moderationService(List<ModerationTarget> targets, ReportRepository reportRepository,
                                               ModerationProperties properties) {
        return new ModerationService(targets, reportRepository, properties);
    }

    @Bean
    public ReportController reportController(ModerationService moderationService) {
        return new ReportController(moderationService);
    }

    @Bean
    public AdminReportController adminReportController(ModerationService moderationService) {
        return new AdminReportController(moderationService);
    }
}
