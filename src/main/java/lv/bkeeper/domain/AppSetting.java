package lv.bkeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Simple key-value application setting (integration configuration, etc.).
 * Examples: google_drive_folder_id
 */
@Data
@Builder
@Table("app_setting")
public class AppSetting {

    @Id
    private String settingKey;

    private String settingValue;

    private LocalDateTime updatedAt;
}
