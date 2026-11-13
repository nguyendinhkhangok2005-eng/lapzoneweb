package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AiResponseSettings;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface AiResponseSettingsRepository extends JpaRepository<AiResponseSettings, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AiResponseSettings s where s.id = 1")
    Optional<AiResponseSettings> lockCurrent();
}
