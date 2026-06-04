package com.bishe.server.mentor.service;

import com.bishe.server.mentor.repository.MentorRecommendationRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MentorRecommendationMaintenanceServiceTest {

    @Test
    void shouldPurgeOldRunsAndEventsInOneBatch() {
        MentorRecommendationRepository repository = mock(MentorRecommendationRepository.class);
        Instant purgeBefore = Instant.now().minusSeconds(3600);
        when(repository.findRunIdsBefore(purgeBefore, 2)).thenReturn(List.of(31L, 32L));
        when(repository.deleteEventsByRunIds(List.of(31L, 32L))).thenReturn(4L);
        when(repository.deleteRunsByIds(List.of(31L, 32L))).thenReturn(2L);

        MentorRecommendationMaintenanceService service = new MentorRecommendationMaintenanceService(repository);

        MentorRecommendationMaintenanceService.RetentionPurgeResult result = service.purgeRunsBefore(purgeBefore, 2);

        assertThat(result.deletedRunCount()).isEqualTo(2L);
        assertThat(result.deletedEventCount()).isEqualTo(4L);
        assertThat(result.purgeBefore()).isEqualTo(purgeBefore);
        verify(repository).deleteEventsByRunIds(List.of(31L, 32L));
        verify(repository).deleteRunsByIds(List.of(31L, 32L));
    }

    @Test
    void shouldReturnEmptyResultWhenNoExpiredRunsFound() {
        MentorRecommendationRepository repository = mock(MentorRecommendationRepository.class);
        Instant purgeBefore = Instant.now().minusSeconds(3600);
        when(repository.findRunIdsBefore(purgeBefore, 3)).thenReturn(List.of());

        MentorRecommendationMaintenanceService service = new MentorRecommendationMaintenanceService(repository);

        MentorRecommendationMaintenanceService.RetentionPurgeResult result = service.purgeRunsBefore(purgeBefore, 3);

        assertThat(result.deletedRunCount()).isZero();
        assertThat(result.deletedEventCount()).isZero();
        assertThat(result.purgeBefore()).isEqualTo(purgeBefore);
    }
}
