package lv.bkeeper.repository;

import lv.bkeeper.domain.AppSetting;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AppSettingRepository extends CrudRepository<AppSetting, String> {

    default Optional<String> findValue(String key) {
        return findById(key).map(AppSetting::getSettingValue);
    }

    /**
     * Insert or update a setting.
     * Uses an explicit SQLite upsert because CrudRepository.save() treats a
     * non-null String id as an existing row and would issue a no-op UPDATE.
     */
    @Modifying
    @Query("INSERT INTO app_setting (setting_key, setting_value, updated_at) "
         + "VALUES (:key, :value, :updatedAt) "
         + "ON CONFLICT(setting_key) DO UPDATE SET "
         + "setting_value = excluded.setting_value, updated_at = excluded.updated_at")
    void put(@Param("key") String key,
             @Param("value") String value,
             @Param("updatedAt") LocalDateTime updatedAt);

    default void put(String key, String value) {
        put(key, value, LocalDateTime.now());
    }

    default void remove(String key) {
        deleteById(key);
    }
}
