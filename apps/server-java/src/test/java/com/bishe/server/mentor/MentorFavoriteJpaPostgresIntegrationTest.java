package com.bishe.server.mentor;

import com.bishe.server.mentor.repository.MentorFavoriteRepository;
import com.bishe.server.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导师收藏仓储在 PostgreSQL 下的最小验证。
 */
@DataJpaTest
@Import(MentorFavoriteRepository.class)
class MentorFavoriteJpaPostgresIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final long STUDENT_USER_ID = 611L;
    private static final long PRIMARY_MENTOR_USER_ID = 612L;
    private static final long SECONDARY_MENTOR_USER_ID = 613L;

    @Autowired
    private MentorFavoriteRepository mentorFavoriteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertUser(STUDENT_USER_ID, "STUDENT", "pg-favorite-student@example.com", "PG Favorite Student");
        insertUser(PRIMARY_MENTOR_USER_ID, "MENTOR", "pg-favorite-mentor-1@example.com", "PG Favorite Mentor One");
        insertUser(SECONDARY_MENTOR_USER_ID, "MENTOR", "pg-favorite-mentor-2@example.com", "PG Favorite Mentor Two");
        jdbcTemplate.update(
                "DELETE FROM mentor_favorites WHERE student_user_id = ? AND mentor_user_id IN (?, ?)",
                STUDENT_USER_ID,
                PRIMARY_MENTOR_USER_ID,
                SECONDARY_MENTOR_USER_ID
        );
    }

    @Test
    void mentorFavoriteRepositoryShouldSupportIdempotentFavoriteQueryAndDeleteOnPostgres() {
        assertThat(mentorFavoriteRepository.isFavorited(STUDENT_USER_ID, PRIMARY_MENTOR_USER_ID)).isFalse();
        assertThat(mentorFavoriteRepository.countFavoritesByStudent(STUDENT_USER_ID)).isZero();

        mentorFavoriteRepository.addFavorite(STUDENT_USER_ID, PRIMARY_MENTOR_USER_ID);
        mentorFavoriteRepository.addFavorite(STUDENT_USER_ID, PRIMARY_MENTOR_USER_ID);
        mentorFavoriteRepository.addFavorite(STUDENT_USER_ID, SECONDARY_MENTOR_USER_ID);

        Integer storedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mentor_favorites WHERE student_user_id = ?",
                Integer.class,
                STUDENT_USER_ID
        );
        assertThat(storedCount).isEqualTo(2);

        assertThat(mentorFavoriteRepository.isFavorited(STUDENT_USER_ID, PRIMARY_MENTOR_USER_ID)).isTrue();
        assertThat(mentorFavoriteRepository.countFavoritesByStudent(STUDENT_USER_ID)).isEqualTo(2);
        assertThat(mentorFavoriteRepository.findFavoriteMentorUserIds(STUDENT_USER_ID))
                .containsExactly(SECONDARY_MENTOR_USER_ID, PRIMARY_MENTOR_USER_ID);
        assertThat(mentorFavoriteRepository.findFavoritedMentorUserIds(
                STUDENT_USER_ID,
                List.of(PRIMARY_MENTOR_USER_ID, PRIMARY_MENTOR_USER_ID, SECONDARY_MENTOR_USER_ID, 9999L)
        )).isEqualTo(Set.of(PRIMARY_MENTOR_USER_ID, SECONDARY_MENTOR_USER_ID));

        assertThat(mentorFavoriteRepository.removeFavorite(STUDENT_USER_ID, PRIMARY_MENTOR_USER_ID)).isTrue();
        assertThat(mentorFavoriteRepository.removeFavorite(STUDENT_USER_ID, PRIMARY_MENTOR_USER_ID)).isFalse();
        assertThat(mentorFavoriteRepository.isFavorited(STUDENT_USER_ID, PRIMARY_MENTOR_USER_ID)).isFalse();
        assertThat(mentorFavoriteRepository.countFavoritesByStudent(STUDENT_USER_ID)).isEqualTo(1);
    }

    private void insertUser(long userId, String role, String email, String displayName) {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?",
                Integer.class,
                userId
        );
        if (existing != null && existing > 0) {
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO users(
                    id,
                    email,
                    password_hash,
                    role,
                    tier,
                    status,
                    display_name,
                    real_name,
                    is_deleted,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, 'FREE', 'ACTIVE', ?, ?, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                email,
                "$2a$10$seed",
                role,
                displayName,
                displayName
        );
    }
}
