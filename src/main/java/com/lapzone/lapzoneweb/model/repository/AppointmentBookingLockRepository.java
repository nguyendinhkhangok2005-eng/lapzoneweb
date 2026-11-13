package com.lapzone.lapzoneweb.model.repository;

import com.lapzone.lapzoneweb.model.entity.AppointmentBookingLock;
import org.springframework.data.jpa.repository.*;

public interface AppointmentBookingLockRepository extends JpaRepository<AppointmentBookingLock, String> {
    // The MySQL upsert itself obtains the exclusive row lock, even for a new key.
    @Modifying
    @Query(value = "insert into appointment_booking_locks (lock_key) values (?) on duplicate key update lock_key = lock_key", nativeQuery = true)
    void acquire(String key);
}
