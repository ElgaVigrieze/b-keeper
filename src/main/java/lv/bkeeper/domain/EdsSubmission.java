package lv.bkeeper.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Tracks every EDS document submission.
 */
@Data
@Builder
@Table("eds_submission")
public class EdsSubmission {

    @Id
    private Long id;

    /**
     * Document type:
     *   PVN          = PVN taksācijas perioda deklarācija
     *   DDZ          = Darba devēja ziņojums
     *   ZINAS        = Ziņas par darba ņēmējiem
     *   GADA_PARSKATS = Gada pārskats
     *   UIN          = Uzņēmuma ienākuma nodoklis
     *   PAZINOTAJS   = Paziņojums par fiziskai personai izmaksātajām summām
     */
    private String documentType;

    private Integer periodYear;
    private Integer periodMonth;  // null for annual documents

    /** DRAFT | READY | SUBMITTED | ACCEPTED | REJECTED */
    private String status;

    private String xmlFilename;    // generated XML file path
    private String edsDocNumber;   // EDS confirmation number after submission
    private LocalDateTime submittedAt;
    private LocalDateTime confirmedAt;

    /** JSON snapshot of key figures at submission time */
    private String totalJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
