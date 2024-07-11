package ru.belgorod.pervomaiskaya6.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.belgorod.pervomaiskaya6.domain.model.UserData;

import java.util.Optional;

@Repository
public interface UserDataRepository extends JpaRepository<UserData, Long> {

    Optional<UserData> findByRoomNumber(Integer roomNumber);

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = "UPDATE user_data SET old_thermal_meter_value = thermal_meter_value, thermal_meter_value = null where true")
    void copyAndClearThermalMeterValue();
}
