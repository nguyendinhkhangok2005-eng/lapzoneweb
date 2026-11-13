package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AiResponseSettingsChange;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiResponseSettingsChangeRepository extends JpaRepository<AiResponseSettingsChange, Long> {
    Page<AiResponseSettingsChange> findAllByOrderByChangedAtDescIdDesc(Pageable page);
}
